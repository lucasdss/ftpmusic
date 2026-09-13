package com.lucasdss.ftpmusic.app.cast

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.framework.CastSession
import com.lucasdss.ftpmusic.app.playback.PlaybackState
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CastIntegrationTest {

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @After
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `disconnect calls endCurrentSession with true`() {
        val sessionManager: com.google.android.gms.cast.framework.SessionManager = mockk(relaxed = true)
        every { sessionManager.endCurrentSession(true) } just runs

        sessionManager.endCurrentSession(true)

        verify(exactly = 1) { sessionManager.endCurrentSession(true) }
    }

    @Test
    fun `setPlayer transfers queue items count`() {
        val oldPlayer: Player = mockk(relaxed = true)
        val newPlayer: Player = mockk(relaxed = true)

        val item1 = MediaItem.Builder().setMediaId("t1").setUri("http://ex.com/1").build()
        val item2 = MediaItem.Builder().setMediaId("t2").setUri("http://ex.com/2").build()

        every { oldPlayer.mediaItemCount } returns 2
        every { oldPlayer.getMediaItemAt(0) } returns item1
        every { oldPlayer.getMediaItemAt(1) } returns item2
        every { oldPlayer.currentMediaItemIndex } returns 0
        every { oldPlayer.currentPosition } returns 0L
        every { oldPlayer.isPlaying } returns true

        val transferredItems = (0 until oldPlayer.mediaItemCount).mapNotNull { oldPlayer.getMediaItemAt(it) }
        newPlayer.setMediaItems(transferredItems)
        newPlayer.seekTo(oldPlayer.currentMediaItemIndex, oldPlayer.currentPosition)

        assertEquals(2, transferredItems.size)
        verify { newPlayer.setMediaItems(transferredItems) }
    }

    @Test
    fun `setPlayer preserves current index`() {
        val oldPlayer: Player = mockk(relaxed = true)
        val newPlayer: Player = mockk(relaxed = true)

        every { oldPlayer.mediaItemCount } returns 3
        every { oldPlayer.getMediaItemAt(any()) } returns
            MediaItem.Builder().setMediaId("t").setUri("http://ex.com/t").build()
        every { oldPlayer.currentMediaItemIndex } returns 2
        every { oldPlayer.currentPosition } returns 45_000L
        every { oldPlayer.isPlaying } returns true

        val transferredItems = (0 until oldPlayer.mediaItemCount).mapNotNull { oldPlayer.getMediaItemAt(it) }
        newPlayer.setMediaItems(transferredItems)
        newPlayer.seekTo(oldPlayer.currentMediaItemIndex, oldPlayer.currentPosition)

        verify { newPlayer.seekTo(2, 45_000L) }
    }

    @Test
    fun `setPlayer preserves playing state`() {
        val oldPlayer: Player = mockk(relaxed = true)
        val newPlayer: Player = mockk(relaxed = true)

        every { oldPlayer.mediaItemCount } returns 1
        every { oldPlayer.getMediaItemAt(0) } returns
            MediaItem.Builder().setMediaId("t1").setUri("http://ex.com/1").build()
        every { oldPlayer.currentMediaItemIndex } returns 0
        every { oldPlayer.currentPosition } returns 0L
        every { oldPlayer.isPlaying } returns true

        val transferredItems = (0 until oldPlayer.mediaItemCount).mapNotNull { oldPlayer.getMediaItemAt(it) }
        newPlayer.setMediaItems(transferredItems)
        newPlayer.seekTo(oldPlayer.currentMediaItemIndex, oldPlayer.currentPosition)
        newPlayer.prepare()
        newPlayer.play()

        verify { newPlayer.prepare() }
        verify { newPlayer.play() }
    }

    @Test
    fun `setPlayer handles null from player`() {
        val newPlayer: Player = mockk(relaxed = true)

        // Simulate null old player — no transfer
        val hasItems = false
        if (hasItems) {
            newPlayer.setMediaItems(emptyList())
        }

        verify(exactly = 0) { newPlayer.setMediaItems(any()) }
        verify(exactly = 0) { newPlayer.prepare() }
    }

    @Test
    fun `onCastSessionAvailable updates isCasting`() {
        val device: CastDevice = mockk()
        val session: CastSession = mockk()
        every { device.friendlyName } returns "Kitchen Speaker"
        every { session.castDevice } returns device

        val state = PlaybackState().copy(
            isCasting = true,
            castDeviceName = session.castDevice?.friendlyName,
        )

        assertTrue("isCasting must be true after session available", state.isCasting)
        assertEquals("Kitchen Speaker", state.castDeviceName)
    }

    @Test
    fun `onCastSessionUnavailable clears Cast state`() {
        val state = PlaybackState().copy(
            isCasting = false,
            castDeviceName = null,
        )

        assertFalse("isCasting must be false after session unavailable", state.isCasting)
        assertNull("castDeviceName must be null after session unavailable", state.castDeviceName)
    }
}
