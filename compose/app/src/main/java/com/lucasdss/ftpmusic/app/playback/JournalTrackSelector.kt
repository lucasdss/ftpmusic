package com.lucasdss.ftpmusic.app.playback

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lucasdss.ftpmusic.app.data.db.QueueJournalEntity
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import kotlin.math.min
import kotlin.random.Random

/**
 * Scores journal candidates so Continuous Play follows the user's taste and
 * the source queue position instead of sampling purely by journal recency.
 *
 * Pure by design: the caller hydrates [TrackEntity] rows (chunked Room reads)
 * and passes them as [Signals], so this object stays unit-testable.
 */
object JournalTrackSelector {
    /** Bound for Room hydration; a 500-entry journal can hold 50k track ids. */
    const val DEFAULT_POOL_CAP = 800

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val TWO_DAYS_MS = 2L * DAY_MS
    private const val SEVEN_DAYS_MS = 7L * DAY_MS
    private const val POSITION_AFTER_CURRENT = 2.0
    private const val POSITION_BEFORE_CURRENT = 0.4
    private const val POSITION_AFTER_SAVED = 1.4
    private const val POSITION_BEFORE_SAVED = 0.75
    private const val ARTIST_REPEAT_FACTOR = 0.55
    private const val ALBUM_REPEAT_FACTOR = 0.75

    /** Context + ledgers used to score candidates. */
    data class Signals(
        val currentTrackId: String? = null,
        val currentArtistId: String? = null,
        val currentAlbumId: String? = null,
        val currentGenre: String? = null,
        val excludedIds: Set<String> = emptySet(),
        val dislikedTrackIds: Set<String> = emptySet(),
        val dislikedAlbumIds: Set<String> = emptySet(),
        val dislikedArtistIds: Set<String> = emptySet(),
        val nowMs: Long = 0L,
    )

    /** A unique journal track plus the entry/index that produced it. */
    data class Candidate(val id: String, val entryIndex: Int, val indexInEntry: Int, val entryPosition: Int)

    data class ParsedJournal(val entryCount: Int, val candidates: List<Candidate>, val truncated: Boolean = false)

    /**
     * Parses newest-first [entries] into a de-duplicated, bounded pool.
     * [entryCap] should mirror `PlaybackManager.journalCap` (10..500);
     * [poolCap] bounds Room hydration regardless of how many ids entries hold.
     * [ParsedJournal.truncated] reports that [poolCap] cut the id pool, which
     * callers should surface in diagnostics.
     *
     * Scores are relative to the effective entry cap (newest ~5x oldest), not
     * absolute wall-clock age.
     */
    fun parse(
        entries: List<QueueJournalEntity>,
        entryCap: Int = entries.size,
        poolCap: Int = DEFAULT_POOL_CAP,
    ): ParsedJournal {
        if (entries.isEmpty() || entryCap <= 0 || poolCap <= 0) return ParsedJournal(0, emptyList())
        val capped = entries.take(entryCap)
        val gson = Gson()
        val type = object : TypeToken<List<String>>() {}.type
        val seen = HashSet<String>(min(poolCap, 256))
        val candidates = ArrayList<Candidate>(min(poolCap, 256))
        var truncated = false
        outer@ for ((entryIndex, entry) in capped.withIndex()) {
            val ids: List<String> = try {
                gson.fromJson(entry.trackIdsJson, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            for ((indexInEntry, id) in ids.withIndex()) {
                if (id.isBlank() || !seen.add(id)) continue
                candidates.add(Candidate(id, entryIndex, indexInEntry, entry.position.coerceAtLeast(0)))
                if (candidates.size >= poolCap) {
                    truncated = true
                    break@outer
                }
            }
        }
        return ParsedJournal(capped.size, candidates, truncated)
    }

    /**
     * Weighted sample without replacement. The returned order is the pick
     * order (highest score first under a zero-random test double).
     *
     * Hard filters: [Signals.excludedIds], track/album/artist dislike ledgers.
     * Soft signals: journal recency, play count/rating/star, artist/album/genre
     * affinity, recent-play penalty, and source position. Artist/album repeats
     * are down-weighted rather than hard-capped so the result can always fill
     * `min(maxTracks, eligible pool size)`.
     */
    fun select(
        parsed: ParsedJournal,
        hydrated: Map<String, TrackEntity>,
        signals: Signals = Signals(),
        maxTracks: Int = 10,
        random: Random = Random.Default,
    ): List<String> {
        if (maxTracks <= 0 || parsed.candidates.isEmpty()) return emptyList()

        val current = signals.currentTrackId?.takeIf { it.isNotBlank() }
        val currentCandidate = current?.let { id -> parsed.candidates.firstOrNull { it.id == id } }
        val currentEntryIndex = currentCandidate?.entryIndex
        val currentIndexInEntry = currentCandidate?.indexInEntry

        val scored = parsed.candidates.mapNotNull { candidate ->
            val entity = hydrated[candidate.id]
            if (isExcluded(candidate.id, entity, signals)) return@mapNotNull null
            Scored(
                id = candidate.id,
                entity = entity,
                baseScore = baseScore(
                    candidate = candidate,
                    entryCount = parsed.entryCount,
                    entity = entity,
                    signals = signals,
                    currentEntryIndex = currentEntryIndex,
                    currentIndexInEntry = currentIndexInEntry,
                ),
            )
        }.toMutableList()
        if (scored.isEmpty()) return emptyList()

        val picked = ArrayList<String>(min(maxTracks, scored.size))
        val artistCounts = HashMap<String, Int>()
        val albumCounts = HashMap<String, Int>()
        while (picked.size < maxTracks && scored.isNotEmpty()) {
            scored.sortByDescending { it.currentScore(artistCounts, albumCounts) }
            val total = scored.sumOf { it.currentScore(artistCounts, albumCounts) }
            var target = random.nextDouble() * total
            var index = 0
            while (index < scored.lastIndex && target >= scored[index].currentScore(artistCounts, albumCounts)) {
                target -= scored[index].currentScore(artistCounts, albumCounts)
                index++
            }
            val chosen = scored.removeAt(index)
            picked.add(chosen.id)
            chosen.entity?.artistId?.let { artistCounts[it] = (artistCounts[it] ?: 0) + 1 }
            chosen.entity?.albumId?.let { albumCounts[it] = (albumCounts[it] ?: 0) + 1 }
        }
        return picked
    }

    /**
     * Back-compat overload for callers/tests without hydrated metadata.
     * Uses entry recency + position only and no taste/context signals.
     */
    fun select(entries: List<QueueJournalEntity>, currentTrackIds: Set<String>, maxTracks: Int = 10): List<String> =
        select(
            parsed = parse(entries),
            hydrated = emptyMap(),
            signals = Signals(excludedIds = currentTrackIds),
            maxTracks = maxTracks,
        )

    private data class Scored(val id: String, val entity: TrackEntity?, val baseScore: Double) {
        fun currentScore(artistCounts: Map<String, Int>, albumCounts: Map<String, Int>): Double {
            if (entity == null) return baseScore
            var score = baseScore
            repeat(entity.artistId?.let { artistCounts[it] } ?: 0) { score *= ARTIST_REPEAT_FACTOR }
            repeat(entity.albumId?.let { albumCounts[it] } ?: 0) { score *= ALBUM_REPEAT_FACTOR }
            return score
        }
    }

    private fun isExcluded(id: String, entity: TrackEntity?, signals: Signals): Boolean = id in signals.excludedIds ||
        id in signals.dislikedTrackIds ||
        entity?.isDisliked == true ||
        (entity?.albumId != null && entity.albumId in signals.dislikedAlbumIds) ||
        (entity?.artistId != null && entity.artistId in signals.dislikedArtistIds)

    private fun baseScore(
        candidate: Candidate,
        entryCount: Int,
        entity: TrackEntity?,
        signals: Signals,
        currentEntryIndex: Int?,
        currentIndexInEntry: Int?,
    ): Double = entryRecency(candidate.entryIndex, entryCount) *
        taste(entity) *
        context(entity, signals) *
        recentPenalty(entity, signals.nowMs) *
        positionFactor(candidate, currentEntryIndex, currentIndexInEntry)

    private fun entryRecency(entryIndex: Int, entryCount: Int): Double {
        if (entryCount <= 1) return 1.0
        val newestBonus = (entryCount - 1 - entryIndex).toDouble() / (entryCount - 1).toDouble()
        return 1.0 + 4.0 * newestBonus
    }

    private fun taste(entity: TrackEntity?): Double {
        if (entity == null) return 1.0
        val plays = min(entity.playCount, 40) * 0.08
        val rating = (entity.userRating ?: 0).coerceIn(0, 5) / 5.0 * 2.0
        val starred = if (entity.starredAt != null) 1.0 else 0.0
        return 1.0 + plays + rating + starred
    }

    private fun context(entity: TrackEntity?, signals: Signals): Double {
        if (entity == null) return 1.0
        var score = 1.0
        if (!signals.currentArtistId.isNullOrBlank() && entity.artistId == signals.currentArtistId) score += 1.5
        if (!signals.currentAlbumId.isNullOrBlank() && entity.albumId == signals.currentAlbumId) score += 1.0
        if (!signals.currentGenre.isNullOrBlank() && entity.genre == signals.currentGenre) score += 0.75
        return score
    }

    private fun recentPenalty(entity: TrackEntity?, nowMs: Long): Double {
        val lastPlayed = entity?.lastPlayedAt ?: return 1.0
        if (nowMs <= 0L) return 1.0
        val age = (nowMs - lastPlayed).coerceAtLeast(0L)
        return when {
            age < TWO_DAYS_MS -> 0.35
            age < SEVEN_DAYS_MS -> 0.65
            else -> 1.0
        }
    }

    private fun positionFactor(candidate: Candidate, currentEntryIndex: Int?, currentIndexInEntry: Int?): Double {
        if (currentEntryIndex != null && currentIndexInEntry != null && candidate.entryIndex == currentEntryIndex) {
            return when {
                candidate.indexInEntry > currentIndexInEntry -> POSITION_AFTER_CURRENT
                candidate.indexInEntry == currentIndexInEntry -> 1.0
                else -> POSITION_BEFORE_CURRENT
            }
        }
        // No live cursor in this entry: fall back to the saved start index (CP-9).
        return when {
            candidate.indexInEntry > candidate.entryPosition -> POSITION_AFTER_SAVED
            candidate.indexInEntry == candidate.entryPosition -> 1.0
            else -> POSITION_BEFORE_SAVED
        }
    }
}
