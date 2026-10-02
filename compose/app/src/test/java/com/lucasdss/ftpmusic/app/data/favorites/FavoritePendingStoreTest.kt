package com.lucasdss.ftpmusic.app.data.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritePendingStoreTest {

    @Test
    fun mergeLiked_addsPendingLiked_removesNeutralAndDisliked() {
        val store = FavoritePendingStore()
        store.set("a", FavoritePendingKind.Liked)
        store.set("b", FavoritePendingKind.Neutral)
        store.set("c", FavoritePendingKind.Disliked)
        val merged = store.mergeLiked(setOf("b", "c", "d"))
        assertEquals(setOf("a", "d"), merged)
    }

    @Test
    fun mergeDisliked_addsPendingDisliked_removesLikedAndNeutral() {
        val store = FavoritePendingStore()
        store.set("a", FavoritePendingKind.Disliked)
        store.set("b", FavoritePendingKind.Liked)
        store.set("c", FavoritePendingKind.Neutral)
        val merged = store.mergeDisliked(setOf("b", "c", "d"))
        assertEquals(setOf("a", "d"), merged)
    }

    @Test
    fun reconcile_clearsWhenRoomMatches() {
        val store = FavoritePendingStore()
        store.set("liked", FavoritePendingKind.Liked)
        store.set("disliked", FavoritePendingKind.Disliked)
        store.set("neutral", FavoritePendingKind.Neutral)
        store.set("stale", FavoritePendingKind.Liked)
        store.reconcile(
            roomLiked = setOf("liked"),
            roomDisliked = setOf("disliked"),
        )
        assertNull(store.get("liked"))
        assertNull(store.get("disliked"))
        assertNull(store.get("neutral"))
        assertEquals(FavoritePendingKind.Liked, store.get("stale"))
    }

    @Test
    fun pendingSurvivesStaleRoom_untilCatchUp() {
        val store = FavoritePendingStore()
        store.set("t1", FavoritePendingKind.Liked)
        // Stale emit still without t1
        var liked = store.mergeLiked(emptySet())
        assertTrue(liked.contains("t1"))
        store.reconcile(emptySet(), emptySet())
        assertEquals(FavoritePendingKind.Liked, store.get("t1"))
        // Room catches up
        store.reconcile(setOf("t1"), emptySet())
        assertNull(store.get("t1"))
        liked = store.mergeLiked(setOf("t1"))
        assertEquals(setOf("t1"), liked)
    }

    @Test
    fun rollbackClearsPending() {
        val store = FavoritePendingStore()
        store.set("x", FavoritePendingKind.Liked)
        store.clear("x")
        assertTrue(store.isEmpty())
        assertFalse(store.mergeLiked(emptySet()).contains("x"))
    }

    @Test
    fun neutralSurvivesLikedOnlyReconcileUntilBothSidesMatch() {
        val store = FavoritePendingStore()
        store.set("ghost", FavoritePendingKind.Neutral)
        // Liked-window refresh that passes empty liked + leftover disliked must keep Neutral
        // (premature clear caused ghost unstar rows).
        store.reconcile(roomLiked = emptySet(), roomDisliked = setOf("ghost"))
        assertEquals(FavoritePendingKind.Neutral, store.get("ghost"))
        // Both sides match Neutral (!liked && !disliked) → clear
        store.reconcile(roomLiked = emptySet(), roomDisliked = emptySet())
        assertNull(store.get("ghost"))
    }
}
