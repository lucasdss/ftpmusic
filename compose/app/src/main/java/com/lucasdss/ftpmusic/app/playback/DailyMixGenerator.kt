package com.lucasdss.ftpmusic.app.playback

import kotlin.random.Random

/**
 * Generates a Daily Mix that maximizes artist and album diversity before
 * applying weight-based priority. Uses a round-robin approach through artists,
 * preferring unplayed albums within each artist.
 *
 * Weight is a deterministic priority rank, not a probability: within an artist
 * the highest-weight tracks are chosen first, and no track is sampled
 * proportionally. Once an artist's soft cap is reached, a high-weight track
 * does not get extra picks.
 *
 * Rules:
 * - Random target size between 30 and 120
 * - No two consecutive tracks from the same artist
 * - Phase 1: round-robin through artists (1 track per artist per round)
 * - Phase 2: within artist, prefer tracks from unused albums
 * - Phase 3: fill remaining in descending weight order (play count + star/rating)
 * - Per-artist cap is a soft dynamic limit (maxTracksPerArtist), relaxed when
 *   no uncapped alternative remains
 * - Yesterday's tracks get weight penalty
 */
object DailyMixGenerator {

    /** SQLite bind-variable guard: uncapped mix selections can push the
     *  candidate id list past the 999-variable limit on older Android. */
    private const val SQL_CHUNK = 900

    /** Internal bucket for null/blank artists. A sentinel (not the literal
     *  string "unknown") keeps untagged tracks distinct from a real artist
     *  whose name happens to be "unknown" (DM-9). */
    internal const val UNKNOWN_ARTIST_KEY = "\u0000<unknown>"

    internal fun artistKey(artist: String?): String = artist?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN_ARTIST_KEY

    /** Album identity for diversity. A non-null albumId is unique per server;
     *  null albumId tracks are singles, each with its own pseudo-album. */
    internal fun albumKey(track: SongInfo): String = track.albumId ?: "single-${track.id}"

    /**
     * Per-artist cap that cannot, by itself, prevent reaching [targetSize].
     * The previous `targetSize / 10` cap limited a two-artist recipe to 6–24
     * tracks for a 30–120 target (DM-1/DM-12). This cap is a soft diversity
     * limit: the selection loop ignores it when no uncapped alternative remains.
     */
    internal fun maxTracksPerArtist(targetSize: Int, artistCount: Int): Int {
        if (artistCount <= 0) return 0
        return maxOf(2, (targetSize + artistCount - 1) / artistCount + 1)
    }

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
        val primaryIds = primaryPool.mapTo(mutableSetOf()) { it.id }
        // Supplemental is a fallback pool: drop IDs already selectable from the
        // primary pool so Phase 3 cannot waste slots on duplicates.
        val supplementalPool = supplementalTracks.filter { it.id !in primaryIds }

        val targetSize = random.nextInt(30, 121).coerceAtMost(primaryPool.size + supplementalPool.size)

        // DM-3: the weight map must cover BOTH pools. It used to be built from
        // the primary pool only, so every supplemental lookup fell back to 1
        // and the Phase-3 sort was a no-op. Yesterday's penalty applies to
        // supplemental tracks too.
        val weightMap = (primaryPool.asSequence() + supplementalPool.asSequence()).associate { s ->
            var w = (ratings[s.id] ?: 1).coerceIn(1, 5)
            if (s.id in yesterdayTrackIds) w = (w - 1).coerceAtLeast(1)
            s.id to w
        }

        // Organize primary pool by normalized artist key → tracks sorted by weight
        val artistTracks = mutableMapOf<String, MutableList<SongInfo>>()
        for (s in primaryPool) {
            artistTracks.getOrPut(artistKey(s.artist)) { mutableListOf() }.add(s)
        }
        for ((_, tracks) in artistTracks) {
            tracks.sortByDescending { weightMap[it.id] ?: 1 }
        }

        // Soft cap: never let the cap alone keep the mix below target.
        val dynamicMaxPerArtist = maxTracksPerArtist(targetSize, artistTracks.size)

        val result = mutableListOf<SongInfo>()
        val resultIds = mutableSetOf<String>()
        val artistCounts = mutableMapOf<String, Int>()
        val artistRemaining = artistTracks.mapValuesTo(mutableMapOf()) { it.value.size }
        val albumUsage = mutableMapOf<String, Int>() // albumId (or per-single key)

        // Round-robin through artists (shuffled to avoid stable ordering)
        val queue = ArrayDeque(artistTracks.keys.shuffled(random))

        fun hasAlternative(current: String): Boolean = queue.any { other ->
            other != current &&
                (artistCounts[other] ?: 0) < dynamicMaxPerArtist &&
                (artistRemaining[other] ?: 0) > 0
        }

        while (result.size < targetSize && queue.isNotEmpty()) {
            val artist = queue.removeFirst()
            val tracks = artistTracks[artist] ?: continue
            if ((artistRemaining[artist] ?: 0) <= 0) continue
            val count = artistCounts[artist] ?: 0

            // Cap is soft: only enforce it while an uncapped alternative exists.
            if (count >= dynamicMaxPerArtist && hasAlternative(artist)) continue

            // Adjacency is soft too: when every alternative is exhausted/capped,
            // allow the same artist rather than collapsing to one track (DM-1).
            val lastArtist = result.lastOrNull()?.let { artistKey(it.artist) }
            if (artist == lastArtist && hasAlternative(artist)) {
                queue.addLast(artist)
                continue
            }

            // Pick best track: prefer unused album, then highest weight.
            // Un-albumed tracks get a UNIQUE per-track key ("single-<id>") so they
            // are treated like individual singles — the old shared "artist:null"
            // key grouped all un-albumed tracks as one pseudo-album, penalizing
            // all but the first (albumCount built up on the shared key).
            val pick = tracks
                .filter { it.id !in resultIds }
                .minByOrNull { track ->
                    val key = albumKey(track)
                    val albumCount = albumUsage[key] ?: 0
                    // Lower = better: prefer 0-album tracks, then higher weight
                    albumCount * 1000 - (weightMap[track.id] ?: 1)
                }

            if (pick != null) {
                result.add(pick)
                resultIds.add(pick.id)
                artistCounts[artist] = count + 1
                artistRemaining[artist] = (artistRemaining[artist] ?: 1) - 1
                val key = albumKey(pick)
                albumUsage[key] = (albumUsage[key] ?: 0) + 1
                queue.addLast(artist) // re-enter for next round
            }
        }

        // Phase 3: fill remaining from supplemental, with the same soft cap,
        // adjacency and album-diversity rules. Fresh supplemental tracks are
        // preferred; familiar ones remain available to the hard-fill fallback so
        // a small fresh pool cannot strand the mix below target (DM-3/DM-2).
        if (result.size < targetSize) {
            val freshSupplemental = supplementalPool.filter { it.id !in previouslyListenedTrackIds }
            val supplementalByArtist = freshSupplemental
                .filter { it.id !in resultIds }
                .groupBy { artistKey(it.artist) }
                .mapValues { (_, tracks) ->
                    tracks.sortedByDescending { weightMap[it.id] ?: 1 }.toMutableList()
                }
            val suppQueue = ArrayDeque(supplementalByArtist.keys.shuffled(random))

            fun hasSupplementalAlternative(current: String): Boolean = suppQueue.any { other ->
                other != current &&
                    (artistCounts[other] ?: 0) < dynamicMaxPerArtist &&
                    supplementalByArtist[other]?.isNotEmpty() == true
            }

            while (result.size < targetSize && suppQueue.isNotEmpty()) {
                val artist = suppQueue.removeFirst()
                val tracks = supplementalByArtist[artist] ?: continue
                if (tracks.isEmpty()) continue
                if ((artistCounts[artist] ?: 0) >= dynamicMaxPerArtist &&
                    hasSupplementalAlternative(artist)
                ) {
                    continue
                }
                val lastArtist = result.lastOrNull()?.let { artistKey(it.artist) }
                if (artist == lastArtist && hasSupplementalAlternative(artist)) {
                    suppQueue.addLast(artist)
                    continue
                }

                // Prefer a different album from the last pick when possible;
                // otherwise fall back to any track (soft diversity).
                val lastAlbum = result.lastOrNull()?.let { albumKey(it) }
                val differentAlbum = tracks.filter { albumKey(it) != lastAlbum }
                val pick = (differentAlbum.ifEmpty { tracks }).minByOrNull { track ->
                    val key = albumKey(track)
                    (albumUsage[key] ?: 0) * 1000 - (weightMap[track.id] ?: 1)
                } ?: continue
                tracks.remove(pick)
                result.add(pick)
                resultIds.add(pick.id)
                artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
                val key = albumKey(pick)
                albumUsage[key] = (albumUsage[key] ?: 0) + 1
                if (tracks.isNotEmpty()) suppQueue.addLast(artist)
            }
        }

        // Hard fill: if small alternative pools exhausted before the target, the
        // soft cap must not leave the mix short. Fresh tracks get one adjacency-
        // aware pass; familiar tracks are then allowed so the mix still reaches
        // the requested size.
        if (result.size < targetSize) {
            val leftover = (primaryPool.asSequence() + supplementalPool.asSequence())
                .filter { it.id !in resultIds }
                .sortedByDescending { weightMap[it.id] ?: 1 }
                .toList()
            val freshLeftover = leftover.filter { it.id !in previouslyListenedTrackIds }
            // Prefer a different artist AND album, then a different artist, then
            // stop so the familiar pass can fill. Recompute after each append so
            // the comparison is against the actual tail, not a static snapshot.
            while (result.size < targetSize) {
                val lastArtist = result.lastOrNull()?.let { artistKey(it.artist) }
                val lastAlbum = result.lastOrNull()?.let { albumKey(it) }
                val next = freshLeftover.firstOrNull {
                    it.id !in resultIds &&
                        artistKey(it.artist) != lastArtist &&
                        albumKey(it) != lastAlbum
                } ?: freshLeftover.firstOrNull {
                    it.id !in resultIds && artistKey(it.artist) != lastArtist
                } ?: break
                result.add(next)
                resultIds.add(next.id)
            }
            if (result.size < targetSize) {
                for (track in leftover) {
                    if (result.size >= targetSize) break
                    if (track.id in resultIds) continue
                    result.add(track)
                    resultIds.add(track.id)
                }
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
