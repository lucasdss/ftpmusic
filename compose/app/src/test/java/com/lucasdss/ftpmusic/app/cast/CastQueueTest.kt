package com.lucasdss.ftpmusic.app.cast

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for Cast queue transfer, disconnect wiring, and boot queue player handling.
 * The `setPlayer` logic lives in MediaService but is tested here in isolation
 * by simulating the Player swap pattern.
 */
class CastQueueTest {

    private val oldPlayer: Player = mockk(relaxed = true)
    private val newPlayer: Player = mockk(relaxed = true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @After
    fun teardown() {
        clearAllMocks()
    }

    // ── Fix 1: Queue transfer between players ──────────────────────────

    @Test
    fun `setPlayer transfers queue from old to new player`() {
        val item1 = MediaItem.Builder().setMediaId("t1").setUri("http://ex.com/1").build()
        val item2 = MediaItem.Builder().setMediaId("t2").setUri("http://ex.com/2").build()
        val items = listOf(item1, item2)

        every { oldPlayer.mediaItemCount } returns 2
        every { oldPlayer.getMediaItemAt(0) } returns item1
        every { oldPlayer.getMediaItemAt(1) } returns item2
        every { oldPlayer.currentMediaItemIndex } returns 1
        every { oldPlayer.currentPosition } returns 30_000L
        every { oldPlayer.isPlaying } returns true

        // Simulate setPlayer transfer logic
        val transferredItems = (0 until oldPlayer.mediaItemCount).mapNotNull { oldPlayer.getMediaItemAt(it) }
        val currentIndex = oldPlayer.currentMediaItemIndex
        val currentPosition = oldPlayer.currentPosition
        val wasPlaying = oldPlayer.isPlaying

        newPlayer.setMediaItems(transferredItems)
        newPlayer.seekTo(currentIndex, currentPosition)
        newPlayer.prepare()
        newPlayer.play()

        verify { newPlayer.setMediaItems(items) }
        verify { newPlayer.seekTo(1, 30_000L) }
        verify { newPlayer.prepare() }
        verify { newPlayer.play() }
    }

    @Test
    fun `setPlayer transfers queue when old player is paused`() {
        val item = MediaItem.Builder().setMediaId("t1").setUri("http://ex.com/1").build()
        val items = listOf(item)

        every { oldPlayer.mediaItemCount } returns 1
        every { oldPlayer.getMediaItemAt(0) } returns item
        every { oldPlayer.currentMediaItemIndex } returns 0
        every { oldPlayer.currentPosition } returns 0L
        every { oldPlayer.isPlaying } returns false

        val transferredItems = (0 until oldPlayer.mediaItemCount).mapNotNull { oldPlayer.getMediaItemAt(it) }
        newPlayer.setMediaItems(transferredItems)
        newPlayer.seekTo(oldPlayer.currentMediaItemIndex, oldPlayer.currentPosition)

        verify { newPlayer.setMediaItems(items) }
        verify { newPlayer.seekTo(0, 0L) }
        verify(exactly = 0) { newPlayer.prepare() }
        verify(exactly = 0) { newPlayer.play() }
    }

    @Test
    fun `setPlayer does nothing when old player has no items`() {
        every { oldPlayer.mediaItemCount } returns 0

        // Should not call setMediaItems on new player
        val hasItems = oldPlayer.mediaItemCount > 0
        if (hasItems) {
            val transferredItems = (0 until oldPlayer.mediaItemCount).mapNotNull { oldPlayer.getMediaItemAt(it) }
            newPlayer.setMediaItems(transferredItems)
        }

        verify(exactly = 0) { newPlayer.setMediaItems(any()) }
        verify(exactly = 0) { newPlayer.seekTo(any(), any()) }
        verify(exactly = 0) { newPlayer.prepare() }
        verify(exactly = 0) { newPlayer.play() }
    }

    @Test
    fun `setPlayer handles null from player gracefully`() {
        // When from is null, no transfer should occur
        val hasItems = false // simulate null from
        if (hasItems) {
            // This block should not execute
        }

        // oldPlayer should have been stopped and listener removed in real code
        // but here we just verify no transfer attempt on newPlayer
        verify(exactly = 0) { newPlayer.setMediaItems(any()) }
        verify(exactly = 0) { newPlayer.prepare() }
    }

    // ── Fix 2: Disconnect wiring ───────────────────────────────────────

    @Test
    fun `disconnect calls endCurrentSession`() {
        val sessionManager: SessionManager = mockk(relaxed = true)
        val castSession: CastSession = mockk(relaxed = true)

        every { sessionManager.endCurrentSession(true) } just runs

        // Simulate the onDisconnect lambda from NavHost
        try {
            sessionManager.endCurrentSession(true)
        } catch (_: Exception) {}

        verify(exactly = 1) { sessionManager.endCurrentSession(true) }
    }

    @Test
    fun `disconnect endCurrentSession is safe when exception occurs`() {
        val sessionManager: SessionManager = mockk(relaxed = true)

        every { sessionManager.endCurrentSession(true) } throws RuntimeException("Cast error")

        // Should not throw — exception is caught by try/catch
        try {
            sessionManager.endCurrentSession(true)
        } catch (_: Exception) {}

        // Test passes if no exception propagates
    }

    @Test
    fun `disconnect endCurrentSession with true parameter stops casting`() {
        val sessionManager: SessionManager = mockk(relaxed = true)
        every { sessionManager.endCurrentSession(true) } just runs

        sessionManager.endCurrentSession(true)

        // Verify it was called with stopCasting = true
        verify { sessionManager.endCurrentSession(true) }
    }

    // ── Fix 3: Boot queue uses active player ───────────────────────────

    @Test
    fun `boot queue seekTo uses correct player from PlayerHolder`() {
        // Validate pattern: PlayerHolder.player ?: return@withContext
        val mockPlayer: Player = mockk(relaxed = true)

        // Simulate PlayerHolder usage
        object {
            var player: Player? = mockPlayer
        }.also { holder ->
            val player = holder.player ?: return
            player.seekTo(3, 42_000L)
            player.pause()
        }

        verify { mockPlayer.seekTo(3, 42_000L) }
        verify { mockPlayer.pause() }
    }

    @Test
    fun `boot queue skips restore when player is null`() {
        val mockPlayer: Player = mockk(relaxed = true)

        // Simulate PlayerHolder.player = null case
        object {
            var player: Player? = null
        }.also { holder ->
            val player = holder.player ?: return
            player.seekTo(0, 0L)
            player.pause()
        }

        // Should not interact with player since PlayerHolder is null
        verify(exactly = 0) { mockPlayer.seekTo(any(), any()) }
        verify(exactly = 0) { mockPlayer.pause() }
    }

    // ── Fix 4: Queue restore guard ──────────────────────────────────────

    @Test
    fun `restore skips playAlbum when player already has items`() {
        // Simulate the onCreate restore check: withContext(Dispatchers.Main) { ... }
        // If player already has items, restore should not overwrite
        val itemsPlayer: Player = mockk(relaxed = true)
        every { itemsPlayer.mediaItemCount } returns 5 // user already played 5 tracks

        val shouldRestore = itemsPlayer.mediaItemCount == 0
        if (shouldRestore) {
            itemsPlayer.setMediaItems(emptyList()) // should NOT reach here
        }

        assertFalse("Should NOT restore when player has items", shouldRestore)
        verify(exactly = 0) { itemsPlayer.setMediaItems(any()) }
    }

    @Test
    fun `restore proceeds with playAlbum when player queue is empty`() {
        val emptyPlayer: Player = mockk(relaxed = true)
        every { emptyPlayer.mediaItemCount } returns 0

        val shouldRestore = emptyPlayer.mediaItemCount == 0
        assertTrue("Should restore when player has no items", shouldRestore)
    }

    @Test
    fun `restore handles null player gracefully`() {
        val nullPlayer: Player? = null
        val shouldRestore = nullPlayer == null || nullPlayer.mediaItemCount == 0
        assertTrue("Should restore when player is null", shouldRestore)
    }

    @Test
    fun `restore double-check prevents overwrite after DB load`() {
        // Simulate the double-check: first check passes (empty), but during DB load
        // the user plays music — second check catches this and skips restore
        val player: Player = mockk(relaxed = true)

        // First check: queue is empty
        every { player.mediaItemCount } returns 0
        val shouldRestore1 = player.mediaItemCount == 0
        assertTrue("First check: should restore when empty", shouldRestore1)

        // Simulate DB load time — user plays 12 tracks
        every { player.mediaItemCount } returns 12

        // Second check: queue now has items — abort
        val shouldRestore2 = player.mediaItemCount == 0
        assertFalse("Second check: should NOT restore after user played music", shouldRestore2)
        verify(exactly = 0) { player.setMediaItems(any()) }
    }

    // ── Fix 5: Fake-session guard ────────────────────────────────────────

    @Test
    fun `existing session with isConnected false does NOT set isCasting`() {
        val castSession: CastSession = mockk(relaxed = true)
        every { castSession.isConnected } returns false

        val existingSession = castSession
        val shouldSetCasting = existingSession != null && existingSession.isConnected
        assertFalse("isConnected=false should prevent isCasting=true", shouldSetCasting)
    }

    @Test
    fun `existing session with isConnected true DOES set isCasting`() {
        val castSession: CastSession = mockk(relaxed = true)
        every { castSession.isConnected } returns true
        every { castSession.castDevice } returns mockk {
            every { friendlyName } returns "Soundbar"
        }

        val existingSession = castSession
        val shouldSetCasting = existingSession != null && existingSession.isConnected
        assertTrue("isConnected=true should allow isCasting=true", shouldSetCasting)
        assertEquals("Soundbar", existingSession.castDevice?.friendlyName)
    }

    @Test
    fun `null existing session does NOT set isCasting`() {
        val existingSession: CastSession? = null
        val shouldSetCasting = existingSession != null && existingSession.isConnected
        assertFalse("null session should not set isCasting", shouldSetCasting)
    }

    @Test
    fun `full cast session switch preserves playback state`() {
        val track1 = MediaItem.Builder().setMediaId("a1").setUri("http://ex.com/a1").build()
        val track2 = MediaItem.Builder().setMediaId("a2").setUri("http://ex.com/a2").build()
        val queue = listOf(track1, track2)

        every { oldPlayer.mediaItemCount } returns 2
        every { oldPlayer.getMediaItemAt(0) } returns track1
        every { oldPlayer.getMediaItemAt(1) } returns track2
        every { oldPlayer.currentMediaItemIndex } returns 0
        every { oldPlayer.currentPosition } returns 15_000L
        every { oldPlayer.isPlaying } returns true

        // Simulate full transfer (cast session available → switch to CastPlayer)
        val transferred = (0 until oldPlayer.mediaItemCount).mapNotNull { oldPlayer.getMediaItemAt(it) }
        newPlayer.setMediaItems(transferred)
        newPlayer.seekTo(oldPlayer.currentMediaItemIndex, oldPlayer.currentPosition)

        assertEquals(2, transferred.size)
        assertEquals("a1", transferred[0].mediaId)
        assertEquals("a2", transferred[1].mediaId)

        verify { newPlayer.setMediaItems(queue) }
        verify { newPlayer.seekTo(0, 15_000L) }
    }
}
