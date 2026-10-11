package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Service-safe like/dislike for the current (or any) track — ADR-0020 semantics.
 * Used by [PlaybackViewModel] (in-app) and [MediaSessionCallback] (shade / lock / Auto).
 */
@Singleton
class CurrentTrackReactionController @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val trackDao: TrackDao,
) {
    data class Snapshot(val trackId: String, val isStarred: Boolean, val isDisliked: Boolean, val trackRating: Int = 0)

    private val _reactions = MutableStateFlow<Snapshot?>(null)
    val reactions: StateFlow<Snapshot?> = _reactions.asStateFlow()

    private val mutexById = ConcurrentHashMap<String, Mutex>()

    private fun mutex(trackId: String): Mutex = mutexById.getOrPut(trackId) { Mutex() }

    /** Read Room without touching [_reactions] (toggle path — preserve optimistic publish). */
    private suspend fun readSnapshot(trackId: String): Snapshot {
        val entity = try {
            trackDao.getTrack(trackId)
        } catch (_: Exception) {
            null
        }
        return Snapshot(
            trackId = trackId,
            isStarred = entity?.starredAt != null,
            isDisliked = entity?.isDisliked == true,
            trackRating = entity?.userRating ?: 0,
        )
    }

    /** Seed reactions from Room on track change / rating probes. */
    suspend fun load(trackId: String): Snapshot = readSnapshot(trackId).also { _reactions.value = it }

    /**
     * Toggle like for [trackId]. When [wasStarred] is non-null, uses that as the
     * pre-toggle state (optimistic UI); otherwise reads Room.
     * Returns a computed snapshot after a successful repo call (does not re-read
     * Room — mocks / eventual Room writers stay consistent with ADR-0020).
     * Emits to [reactions] once at the end only.
     */
    suspend fun toggleLike(trackId: String, wasStarred: Boolean? = null): Snapshot = mutex(trackId).withLock {
        val current = readSnapshot(trackId)
        val starred = wasStarred ?: current.isStarred
        if (starred) {
            favoriteRepository.unlikeTrack(trackId)
            Snapshot(
                trackId = trackId,
                isStarred = false,
                isDisliked = current.isDisliked,
                trackRating = current.trackRating,
            )
        } else {
            favoriteRepository.likeTrack(trackId)
            Snapshot(
                trackId = trackId,
                isStarred = true,
                isDisliked = false,
                trackRating = current.trackRating,
            )
        }.also { _reactions.value = it }
    }

    /**
     * Toggle local-only dislike. When [wasDisliked] is non-null, uses that as the
     * pre-toggle state; otherwise reads Room.
     * Emits to [reactions] once at the end only.
     */
    suspend fun toggleDislike(trackId: String, wasDisliked: Boolean? = null): Snapshot = mutex(trackId).withLock {
        val current = readSnapshot(trackId)
        val disliked = wasDisliked ?: current.isDisliked
        if (disliked) {
            favoriteRepository.clearDislikeTrack(trackId)
            Snapshot(
                trackId = trackId,
                isStarred = current.isStarred,
                isDisliked = false,
                trackRating = current.trackRating,
            )
        } else {
            favoriteRepository.dislikeTrack(trackId)
            Snapshot(
                trackId = trackId,
                isStarred = false,
                isDisliked = true,
                trackRating = current.trackRating,
            )
        }.also { _reactions.value = it }
    }

    /** Publish a snapshot without hitting Room (optimistic UI / VM sync). */
    fun publish(snapshot: Snapshot) {
        _reactions.value = snapshot
    }

    fun clear() {
        _reactions.value = null
    }
}
