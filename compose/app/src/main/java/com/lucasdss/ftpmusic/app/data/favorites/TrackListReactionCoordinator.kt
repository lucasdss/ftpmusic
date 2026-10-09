package com.lucasdss.ftpmusic.app.data.favorites

import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Optimistic like/dislike for browse track-list windows (ADR-0020 / ADR-0104).
 * Each ViewModel owns an instance; not a singleton.
 */
class TrackListReactionCoordinator(private val favoriteRepository: FavoriteRepository) {
    data class Sets(val liked: Set<String> = emptySet(), val disliked: Set<String> = emptySet())

    private val pending = FavoritePendingStore()
    private val mutexById = ConcurrentHashMap<String, Mutex>()

    private fun mutex(trackId: String): Mutex = mutexById.getOrPut(trackId) { Mutex() }

    /** Merge Room entity flags with pending overlays. */
    fun setsFromEntities(entities: List<TrackEntity>): Sets {
        val roomLiked = mutableSetOf<String>()
        val roomDisliked = mutableSetOf<String>()
        for (entity in entities) {
            if (entity.starredAt != null) roomLiked.add(entity.id)
            if (entity.isDisliked) roomDisliked.add(entity.id)
        }
        return mergeRoom(roomLiked, roomDisliked)
    }

    fun mergeRoom(roomLiked: Set<String>, roomDisliked: Set<String>): Sets {
        pending.reconcile(roomLiked, roomDisliked)
        return Sets(
            liked = pending.mergeLiked(roomLiked),
            disliked = pending.mergeDisliked(roomDisliked),
        )
    }

    fun toggleLike(trackId: String, current: Sets, scope: CoroutineScope, publish: (Sets) -> Unit) {
        val isLiked = trackId in current.liked
        if (isLiked) {
            pending.set(trackId, FavoritePendingKind.Neutral)
            publish(Sets(liked = current.liked - trackId, disliked = current.disliked))
            scope.launch {
                mutex(trackId).withLock {
                    try {
                        favoriteRepository.unlikeTrack(trackId)
                    } catch (_: Exception) {
                        pending.clear(trackId)
                        publish(current)
                    }
                }
            }
        } else {
            pending.set(trackId, FavoritePendingKind.Liked)
            publish(
                Sets(
                    liked = current.liked + trackId,
                    disliked = current.disliked - trackId,
                ),
            )
            scope.launch {
                mutex(trackId).withLock {
                    try {
                        favoriteRepository.likeTrack(trackId)
                    } catch (_: Exception) {
                        pending.clear(trackId)
                        publish(current)
                    }
                }
            }
        }
    }

    fun toggleDislike(trackId: String, current: Sets, scope: CoroutineScope, publish: (Sets) -> Unit) {
        val isDisliked = trackId in current.disliked
        if (isDisliked) {
            pending.set(trackId, FavoritePendingKind.Neutral)
            publish(Sets(liked = current.liked, disliked = current.disliked - trackId))
            scope.launch {
                mutex(trackId).withLock {
                    try {
                        favoriteRepository.clearDislikeTrack(trackId)
                    } catch (_: Exception) {
                        pending.clear(trackId)
                        publish(current)
                    }
                }
            }
        } else {
            pending.set(trackId, FavoritePendingKind.Disliked)
            publish(
                Sets(
                    liked = current.liked - trackId,
                    disliked = current.disliked + trackId,
                ),
            )
            scope.launch {
                mutex(trackId).withLock {
                    try {
                        favoriteRepository.dislikeTrack(trackId)
                    } catch (_: Exception) {
                        pending.clear(trackId)
                        publish(current)
                    }
                }
            }
        }
    }
}
