package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.gms.cast.MediaQueueItem
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for mapping a Cast receiver's currentItemId to the local (full)
 * queue index — the fix for the wrong-track-on-disconnect regression.
 */
class CastQueueSyncHelperTest {

    private fun playerWith(vararg ids: String): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.mediaItemCount } returns ids.size
        ids.forEachIndexed { i, id ->
            every { player.getMediaItemAt(i) } returns MediaItem.Builder().setMediaId(id)
                .build().withQueueEntryId(i + 1)
        }
        return player
    }

    @Test
    fun `maps receiver itemId to the local index via queueEntryId`() {
        val player = playerWith("track-a", "track-b", "track-c")

        assertEquals(0, resolveLocalIndexFromReceiverId(player, 1))
        assertEquals(1, resolveLocalIndexFromReceiverId(player, 2))
        assertEquals(2, resolveLocalIndexFromReceiverId(player, 3))
    }

    @Test
    fun `returns null for invalid item id`() {
        val player = playerWith("track-a")

        assertNull(resolveLocalIndexFromReceiverId(player, MediaQueueItem.INVALID_ITEM_ID))
    }

    @Test
    fun `falls back to mediaId hashCode for unstamped items`() {
        val player = mockk<Player>(relaxed = true)
        val raw = MediaItem.Builder().setMediaId("legacy").build()
        every { player.mediaItemCount } returns 1
        every { player.getMediaItemAt(0) } returns raw
        assertEquals(0, resolveLocalIndexFromReceiverId(player, "legacy".hashCode()))
    }

    @Test
    fun `returns null when the item id is not in the queue`() {
        val player = playerWith("track-a", "track-b")

        assertNull(resolveLocalIndexFromReceiverId(player, "ghost-track".hashCode()))
    }

    @Test
    fun `maps correctly even when the receiver trimmed earlier tracks`() {
        // Receiver trimmed tracks 0-2; current item "track-d" should map to
        // the FULL local queue index 3, not the receiver's index 0.
        val player = playerWith("track-a", "track-b", "track-c", "track-d")

        assertEquals(3, resolveLocalIndexFromReceiverId(player, 4))
    }
}
