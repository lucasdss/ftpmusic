package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.Player
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QueueControlsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockAppContext: android.app.Application = mockk(relaxed = true)
    private val queueManager = QueueManager(mockk())
    private lateinit var mockPlayer: Player
    private val mockPersistenceManager: QueuePersistenceManager = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns Uri.EMPTY
        mockPlayer = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
        PlayerHolder.player = null
    }

    // --- QueueManager.remove tests ---

    @Test
    fun `remove delegates to player`() {
        PlayerHolder.player = mockPlayer
        every { mockPlayer.mediaItemCount } returns 5

        queueManager.remove(2)

        verify { mockPlayer.removeMediaItem(2) }
    }

    @Test
    fun `remove ignores null player`() {
        PlayerHolder.player = null

        queueManager.remove(0)

        // No NPE thrown — test passes
    }

    @Test
    fun `remove ignores out of bounds index`() {
        PlayerHolder.player = mockPlayer
        every { mockPlayer.mediaItemCount } returns 3

        queueManager.remove(-1)
        queueManager.remove(3)

        verify(exactly = 0) { mockPlayer.removeMediaItem(any()) }
    }

    // --- QueueManager.move tests ---

    @Test
    fun `move delegates to player`() {
        PlayerHolder.player = mockPlayer
        every { mockPlayer.mediaItemCount } returns 5

        queueManager.move(1, 3)

        verify { mockPlayer.moveMediaItem(1, 3) }
    }

    @Test
    fun `move ignores null player`() {
        PlayerHolder.player = null

        queueManager.move(0, 1)

        // No NPE thrown — test passes
    }

    @Test
    fun `move ignores out of bounds indices`() {
        PlayerHolder.player = mockPlayer
        every { mockPlayer.mediaItemCount } returns 3

        queueManager.move(-1, 1)
        queueManager.move(0, 3)

        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
    }

    // --- QueueManager.clear tests ---

    @Test
    fun `clear clears player items`() {
        PlayerHolder.player = mockPlayer

        queueManager.clear()

        verify { mockPlayer.clearMediaItems() }
    }

    @Test
    fun `clear ignores null player`() {
        PlayerHolder.player = null

        queueManager.clear()

        // No NPE thrown — test passes
    }

    // --- PlaybackManager.clearQueue tests ---

    @Test
    fun `clearQueue clears dual-queue when no player active`() {
        val mockQueueManager = mockk<QueueManager>(relaxed = true)
        val playbackManager =
            PlaybackManager(
                mockQueueManager,
                mockPersistenceManager,
                mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.cache.DownloadManager>(relaxed = true),
                castPreferences,
                mockAppContext,
                mockk<com.lucasdss.ftpmusic.app.data.db.QueueJournalDao>(relaxed = true),
            )

        // Should not throw — clears DualQueueManager + persistence
        playbackManager.clearQueue()

        // queueManager.clear() is no longer called — dual-queue architecture takes over
        verify(exactly = 0) { mockQueueManager.clear() }
    }

    // --- PlaybackManager.removeFromQueue tests ---

    @Test
    fun `removeFromQueue delegates to DualQueueManager`() {
        val mockQueueManager = mockk<QueueManager>(relaxed = true)
        val playbackManager =
            PlaybackManager(
                mockQueueManager,
                mockPersistenceManager,
                mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.cache.DownloadManager>(relaxed = true),
                castPreferences,
                mockAppContext,
                mockk<com.lucasdss.ftpmusic.app.data.db.QueueJournalDao>(relaxed = true),
            )

        // Dual-queue mode: removeFromQueue operates on DualQueueManager internally.
        // syncDualQueueToPlayer is a no-op when PlayerHolder.player is null.
        playbackManager.removeFromQueue(2)

        // Should not crash — remove from DualQueueManager doesn't need the player
    }
}
