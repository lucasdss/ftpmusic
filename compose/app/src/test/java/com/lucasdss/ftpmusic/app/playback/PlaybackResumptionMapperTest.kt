package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.data.model.Track
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackResumptionMapperTest {

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns (firstArg() as String)
            uri
        }
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `shouldSkipQueueRestore when casting or items present`() {
        assertTrue(PlaybackResumptionMapper.shouldSkipQueueRestore(1, false))
        assertTrue(PlaybackResumptionMapper.shouldSkipQueueRestore(0, true))
        assertFalse(PlaybackResumptionMapper.shouldSkipQueueRestore(0, false))
    }

    @Test
    fun `toMediaItemsWithStartPosition returns null for empty queue`() {
        val saved = SavedQueueState(tracks = emptyList(), urls = emptyList(), currentIndex = 0, positionMs = 0)
        assertNull(PlaybackResumptionMapper.toMediaItemsWithStartPosition(saved))
    }

    @Test
    fun `toMediaItemsWithStartPosition builds local metadata and clamps index`() {
        val tracks = listOf(
            Track(id = "t1", title = "One", artist = "A", album = "Alb", coverArt = "c1", duration = 100),
            Track(id = "t2", title = "Two", artist = "B", album = "Alb", duration = 200),
        )
        val saved = SavedQueueState(
            tracks = tracks,
            urls = listOf("https://stream/1", "https://stream/2"),
            currentIndex = 99,
            positionMs = 12_000L,
            repeatMode = Player.REPEAT_MODE_ONE,
            shuffleEnabled = true,
        )
        val playlist = PlaybackResumptionMapper.toMediaItemsWithStartPosition(saved) {
            if (it.coverArt != null) Uri.parse("https://art/${it.coverArt}") else null
        }
        assertNotNull(playlist)
        assertEquals(2, playlist!!.mediaItems.size)
        assertEquals(1, playlist.startIndex) // clamped to lastIndex
        assertEquals(12_000L, playlist.startPositionMs)
        assertEquals("t1", playlist.mediaItems[0].mediaId)
        assertEquals("One", playlist.mediaItems[0].mediaMetadata.title.toString())
        assertEquals("A", playlist.mediaItems[0].mediaMetadata.artist.toString())
        assertTrue(playlist.mediaItems[0].localConfiguration?.uri?.toString()?.contains("stream/1") == true)
    }

    @Test
    fun `applyTransportExtras sets repeat and shuffle`() {
        val player = mockk<Player>(relaxed = true)
        PlaybackResumptionMapper.applyTransportExtras(player, Player.REPEAT_MODE_ALL, true)
        verify { player.repeatMode = Player.REPEAT_MODE_ALL }
        verify { player.shuffleModeEnabled = true }
    }
}
