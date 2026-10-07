package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
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
    fun `shouldBackgroundAutoplay false on API 37 plus`() {
        assertTrue(PlaybackResumptionMapper.shouldBackgroundAutoplay(36))
        assertFalse(PlaybackResumptionMapper.shouldBackgroundAutoplay(37))
        assertFalse(PlaybackResumptionMapper.shouldBackgroundAutoplay(38))
    }

    @Test
    fun `shouldWaitForRestoreOnGetSession only when empty and in flight`() {
        assertTrue(PlaybackResumptionMapper.shouldWaitForRestoreOnGetSession(true, true))
        assertFalse(PlaybackResumptionMapper.shouldWaitForRestoreOnGetSession(false, true))
        assertFalse(PlaybackResumptionMapper.shouldWaitForRestoreOnGetSession(true, false))
    }

    @Test
    fun `migrateStreamUrl unwraps legacy proxy and pipe`() {
        val remote = "https://music.example.com/rest/stream?id=t1&u=x"
        val encoded = java.net.URLEncoder.encode(remote, "UTF-8")
        val legacy = "http://127.0.0.1:9000/stream?id=t1&url=$encoded"
        assertEquals(remote, PlaybackResumptionMapper.migrateStreamUrl("t1", legacy))
        assertEquals(remote, PlaybackResumptionMapper.migrateStreamUrl("t1", "$remote|castIgnore"))
        // proxy without url= query stays as-is (no unwrap)
        val noUrl = "http://127.0.0.1:9000/stream?id=t1"
        assertEquals(noUrl, PlaybackResumptionMapper.migrateStreamUrl("t1", noUrl))
    }

    @Test
    fun `toMediaItems allows blank url without setUri`() {
        val saved = SavedQueueState(
            tracks = listOf(Track(id = "t1", title = "NoUrl")),
            urls = listOf(""),
            currentIndex = 0,
            positionMs = 0L,
        )
        val playlist = PlaybackResumptionMapper.toMediaItemsWithStartPosition(saved)
        assertNotNull(playlist)
        assertEquals("t1", playlist!!.mediaItems[0].mediaId)
        assertNull(playlist.mediaItems[0].localConfiguration)
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
    fun `fromPlayer snapshots seated playlist`() {
        val player = mockk<Player>(relaxed = true)
        every { player.mediaItemCount } returns 2
        every { player.currentMediaItemIndex } returns 1
        every { player.currentPosition } returns 3_000L
        every { player.getMediaItemAt(0) } returns MediaItem.Builder().setMediaId("a").build()
        every { player.getMediaItemAt(1) } returns MediaItem.Builder().setMediaId("b").build()
        val snap = PlaybackResumptionMapper.fromPlayer(player)
        assertNotNull(snap)
        assertEquals(2, snap!!.mediaItems.size)
        assertEquals(1, snap.startIndex)
        assertEquals(3_000L, snap.startPositionMs)
        assertEquals("b", snap.mediaItems[1].mediaId)
    }

    @Test
    fun `fromPlayer null when empty`() {
        val player = mockk<Player>(relaxed = true)
        every { player.mediaItemCount } returns 0
        assertNull(PlaybackResumptionMapper.fromPlayer(player))
    }

    @Test
    fun `applyTransportExtras sets repeat and shuffle`() {
        val player = mockk<Player>(relaxed = true)
        PlaybackResumptionMapper.applyTransportExtras(player, Player.REPEAT_MODE_ALL, true)
        verify { player.repeatMode = Player.REPEAT_MODE_ALL }
        verify { player.shuffleModeEnabled = true }
    }
}
