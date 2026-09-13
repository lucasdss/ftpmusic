package com.lucasdss.ftpmusic.app.playback

import kotlin.random.Random

/**
 * Generates a Daily Mix that maximizes artist and album diversity before
 * applying weight-based selection. Uses a round-robin approach through artists,
 * preferring unplayed albums within each artist.
 *
 * Rules:
 * - Random target size between 30 and 120
 * - No two consecutive tracks from the same artist
 * - Phase 1: round-robin through artists (1 track per artist per round)
 * - Phase 2: within artist, prefer tracks from unused albums
 * - Phase 3: fill remaining with highest-weight tracks (play count + star/rating)
 * - Max 4 tracks per artist (maxPerArtist)
 * - Yesterday's tracks get weight penalty
 */
object DailyMixGenerator {

    /** SQLite bind-variable guard: uncapped mix selections can push the
     *  candidate id list past the 999-variable limit on older Android. */
    private const val SQL_CHUNK = 900

    /**
     * Determines whether the Daily Mix should be regenerated.
     *
     * Rules:
     * - Manual refresh always regenerates.
     * - Usage-based: 24h passed AND >10% of tracks listened → regenerate.
     * - Hard max: 48h since last generation → regenerate regardless.
     */
    fun shouldRegenerate(
        createdAtMs: Long,
        totalTracks: Int,
        listenedTracks: Int,
        nowMs: Long = System.currentTimeMillis(),
        manual: Boolean = false,
    ): Boolean {
        if (manual) return true
        val hoursSinceCreation = (nowMs - createdAtMs) / (60 * 60 * 1000L)
        if (hoursSinceCreation >= 48) return true
        if (hoursSinceCreation >= 24 && totalTracks > 0) {
            val listenRatio = listenedTracks.toFloat() / totalTracks.toFloat()
            if (listenRatio >= 0.10f) return true
        }
        return false
    }

    fun generate(
        songs: List<SongInfo>,
        ratings: Map<String, Int> = emptyMap(),
        supplementalTracks: List<SongInfo> = emptyList(),
        yesterdayTrackIds: Set<String> = emptySet(),
        previouslyListenedTrackIds: Set<String> = emptySet(),
        random: kotlin.random.Random = Random,
    ): List<SongInfo> {
        if (songs.isEmpty()) return emptyList()

        // Anti-repetition: exclude previously listened tracks unless necessary.
        // Build a candidate pool of unlistened songs first.
        val unlistenedSongs = songs.filter { it.id !in previouslyListenedTrackIds }
        val primaryPool = if (unlistenedSongs.size >= 30) unlistenedSongs else songs

        val targetSize = random.nextInt(30, 121).coerceAtMost(primaryPool.size + supplementalTracks.size)
        // Dynamic per-artist cap: proportional to mix size (min 2, max ~12 for 120-track mix)
        val dynamicMaxPerArtist = maxOf(2, targetSize / 10)

        // Build weight map from the primary pool for tiebreaking (includes yesterday penalty)
        val weightMap = primaryPool.associate { s ->
            var w = (ratings[s.id] ?: 1).coerceIn(1, 5)
            if (s.id in yesterdayTrackIds) w = (w - 1).coerceAtLeast(1)
            s.id to w
        }

        // Organize primary pool by artist → list of tracks sorted by weight
        val artistTracks = mutableMapOf<String, MutableList<SongInfo>>()
        for (s in primaryPool) {
            val artist = s.artist ?: "unknown"
            artistTracks.getOrPut(artist) { mutableListOf() }.add(s)
        }
        for ((_, tracks) in artistTracks) {
            tracks.sortByDescending { weightMap[it.id] ?: 1 }
        }

        val result = mutableListOf<SongInfo>()
        val resultIds = mutableSetOf<String>()
        val artistCounts = mutableMapOf<String, Int>()
        val albumUsage = mutableMapOf<String, Int>() // "$artist:$albumId"

        // Round-robin through artists (shuffled to avoid stable ordering)
        val queue = ArrayDeque(artistTracks.keys.shuffled(random))

        var consecutiveSkips = 0
        while (result.size < targetSize && queue.isNotEmpty() && consecutiveSkips < queue.size * 2) {
            val artist = queue.removeFirst()
            val tracks = artistTracks[artist] ?: continue
            val count = artistCounts[artist] ?: 0
            if (count >= dynamicMaxPerArtist) continue

            val lastArtist = result.lastOrNull()?.artist
            if (artist == lastArtist) {
                queue.addLast(artist)
                consecutiveSkips++
                continue
            }
            consecutiveSkips = 0

            // Pick best track: prefer unused album, then highest weight.
            // Un-albumed tracks get a UNIQUE per-track key ("single-<id>") so they
            // are treated like individual singles — the old shared "artist:null"
            // key grouped all un-albumed tracks as one pseudo-album, penalizing
            // all but the first (albumCount built up on the shared key).
            val pick = tracks
                .filter { it.id !in resultIds }
                .minByOrNull { track ->
                    val albumKey = "$artist:${track.albumId ?: "single-${track.id}"}"
                    val albumCount = albumUsage[albumKey] ?: 0
                    // Lower = better: prefer 0-album tracks, then higher weight
                    albumCount * 1000 - (weightMap[track.id] ?: 1)
                }

            if (pick != null) {
                result.add(pick)
                resultIds.add(pick.id)
                artistCounts[artist] = count + 1
                val ak = "$artist:${pick.albumId ?: "single-${pick.id}"}"
                albumUsage[ak] = (albumUsage[ak] ?: 0) + 1
                queue.addLast(artist) // re-enter for next round
            }
        }

        // Phase 3: fill remaining from supplemental
        if (result.size < targetSize) {
            val lastArtist = result.lastOrNull()?.artist
            supplementalTracks
                .filter { it.artist != lastArtist && it.id !in resultIds }
                .sortedByDescending { weightMap[it.id] ?: 1 }
                .forEach { s ->
                    if (result.size >= targetSize) return@forEach
                    val a = s.artist ?: "unknown"
                    if ((artistCounts[a] ?: 0) >= dynamicMaxPerArtist) return@forEach
                    result.add(s)
                    resultIds.add(s.id)
                    artistCounts[a] = (artistCounts[a] ?: 0) + 1
                }
        }

        return result.take(targetSize)
    }

    data class SongInfo(
        val id: String,
        val title: String,
        val artist: String?,
        val albumId: String?,
        val duration: Int?,
        val trackNumber: Int?,
        val coverArt: String?,
    )

    /**
     * Builds a combined weight map (trackId → weight 1–5) from play counts
     * and user ratings. Log-scale compresses large play counts; ratings add a bonus.
     *
     * Formula: weight = clamp(1, log₂(playCount) + ⌊rating / 2⌋, 5)
     * Unplayed tracks get weight 1. Rated/starred tracks get +1 to +2 bonus.
     */
    suspend fun buildWeightMap(
        songIds: List<String>,
        trackDao: com.lucasdss.ftpmusic.app.data.db.TrackDao,
    ): Map<String, Int> {
        // Play count → log-scale weight (1–5)
        val playCounts = songIds.chunked(SQL_CHUNK)
            .flatMap { trackDao.getTrackPlayCounts(it) }
            .associate { it.id to it.playCount }
        // Ratings → bonus (0–2), filtered to candidate pool only
        val rated = songIds.chunked(SQL_CHUNK)
            .flatMap { trackDao.getRatedTracksByIds(it) }
            .associate { it.id to it.userRating }
        // Starred tracks as rating=5 → bonus=2, filtered to candidate pool only
        val starredIds = songIds.chunked(SQL_CHUNK)
            .flatMap { trackDao.getStarredIdsByIds(it) }
            .associate { it.id to it.userRating }
        // Star takes priority over rating if both exist (starring = explicit preference)
        val allRatings = rated + starredIds

        val log2 = { n: Int -> if (n <= 1) 0 else (32 - Integer.numberOfLeadingZeros(n - 1)) }
        // log2 computes ceil(log₂(n)): 1→0, 2→1, 3-4→2, 5-8→3, 9-16→4, 17+→5
        return songIds.associateWith { id ->
            val playWeight = log2(playCounts[id] ?: 0)
            val ratingBonus = (allRatings[id] ?: 0) / 2
            (playWeight + ratingBonus).coerceIn(1, 5)
        }
    }
}
