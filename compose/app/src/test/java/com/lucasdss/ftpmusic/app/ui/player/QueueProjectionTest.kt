package com.lucasdss.ftpmusic.app.ui.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.playback.withQueueEntryId
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueProjectionTest {
    @Test
    fun `projects player metadata extras and current item`() {
        val item = MediaItem.Builder()
            .setMediaId("track")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Title")
                    .setArtist("Artist")
                    .setAlbumTitle("Album")
                    .setExtras(Bundle())
                    .build(),
            )
            .build()

        val result = QueueProjection.project(player(item), currentIndex = 0) { null }.single()

        assertEquals("track", result.id)
        assertEquals("Title", result.title)
        assertEquals("Artist", result.artist)
        assertEquals("Album", result.album)
        assertNull(result.coverArtUrl)
        assertEquals(0L, result.durationMs)
        assertNull(result.userRating)
        assertTrue(result.isCurrent)
        assertEquals(0, result.entryId)
        assertFalse(result.isPriority)
    }

    @Test
    fun `dup mediaId rows keep distinct entryIds`() {
        val a = MediaItem.Builder().setMediaId("same").build().withQueueEntryId(1)
        val b = MediaItem.Builder().setMediaId("same").build().withQueueEntryId(2)
        val flags = listOf(true, false)
        val result = QueueProjection.project(player(a, b), currentIndex = 0, { null }) { flags[it] }
        assertEquals(listOf(1, 2), result.map { it.entryId })
        assertEquals(listOf(true, false), result.map { it.isPriority })
        assertEquals("same", result[0].id)
        assertEquals("same", result[1].id)
    }

    @Test
    fun `falls back to cached metadata and safe defaults`() {
        val unrated = Bundle().apply { putInt("userRating", 0) }
        val item = MediaItem.Builder()
            .setMediaMetadata(MediaMetadata.Builder().setExtras(unrated).build())
            .build()

        val result = QueueProjection.project(player(item), currentIndex = 3) {
            QueueTrackMetadata("Cached", "Cached Artist", "Cached Album")
        }.single()

        assertEquals("item-0", result.id)
        assertEquals("Cached", result.title)
        assertEquals("Cached Artist", result.artist)
        assertEquals("Cached Album", result.album)
        assertNull(result.userRating)
        assertFalse(result.isCurrent)
    }

    @Test
    fun `unknown metadata and empty player produce valid projection`() {
        val item = MediaItem.Builder().setMediaId("unknown").build()

        val unknown = QueueProjection.project(player(item), currentIndex = -1) { null }.single()
        val empty = QueueProjection.project(player(), currentIndex = 0) { null }

        assertEquals("Unknown", unknown.title)
        assertNull(unknown.artist)
        assertNull(unknown.album)
        assertTrue(empty.isEmpty())
    }

    private fun player(vararg items: MediaItem): Player = mockk {
        every { mediaItemCount } returns items.size
        items.forEachIndexed { index, item ->
            every { getMediaItemAt(index) } returns item
        }
    }
}
