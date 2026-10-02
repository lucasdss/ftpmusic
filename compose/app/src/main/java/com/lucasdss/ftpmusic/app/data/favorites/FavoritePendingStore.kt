package com.lucasdss.ftpmusic.app.data.favorites

import java.util.concurrent.ConcurrentHashMap

/** Expected local reaction while a Room write is in flight. */
enum class FavoritePendingKind { Liked, Disliked, Neutral }

/**
 * Presentation overlay so optimistic thumbs survive stale Room Flow emissions
 * between tap and local-first write completion (ADR 0020).
 */
class FavoritePendingStore {
    private val map = ConcurrentHashMap<String, FavoritePendingKind>()

    fun set(id: String, kind: FavoritePendingKind) {
        map[id] = kind
    }

    fun clear(id: String) {
        map.remove(id)
    }

    fun clearAll() {
        map.clear()
    }

    fun get(id: String): FavoritePendingKind? = map[id]

    fun isEmpty(): Boolean = map.isEmpty()

    /**
     * Merge Room liked-id set with pending overlays.
     * Liked pending → add; Disliked/Neutral pending → remove from liked set.
     */
    fun mergeLiked(roomLiked: Set<String>): Set<String> {
        if (map.isEmpty()) return roomLiked
        val out = roomLiked.toMutableSet()
        map.forEach { (id, kind) ->
            when (kind) {
                FavoritePendingKind.Liked -> out.add(id)
                FavoritePendingKind.Disliked, FavoritePendingKind.Neutral -> out.remove(id)
            }
        }
        return out
    }

    /**
     * Merge Room disliked-id set with pending overlays.
     * Disliked pending → add; Liked/Neutral pending → remove from disliked set.
     */
    fun mergeDisliked(roomDisliked: Set<String>): Set<String> {
        if (map.isEmpty()) return roomDisliked
        val out = roomDisliked.toMutableSet()
        map.forEach { (id, kind) ->
            when (kind) {
                FavoritePendingKind.Disliked -> out.add(id)
                FavoritePendingKind.Liked, FavoritePendingKind.Neutral -> out.remove(id)
            }
        }
        return out
    }

    /** Drop pending entries whose Room state already matches expectation. */
    fun reconcile(roomLiked: Set<String>, roomDisliked: Set<String>) {
        if (map.isEmpty()) return
        val done = mutableListOf<String>()
        map.forEach { (id, kind) ->
            val liked = id in roomLiked
            val disliked = id in roomDisliked
            val matches = when (kind) {
                FavoritePendingKind.Liked -> liked && !disliked
                FavoritePendingKind.Disliked -> disliked && !liked
                FavoritePendingKind.Neutral -> !liked && !disliked
            }
            if (matches) done.add(id)
        }
        done.forEach { map.remove(it) }
    }
}
