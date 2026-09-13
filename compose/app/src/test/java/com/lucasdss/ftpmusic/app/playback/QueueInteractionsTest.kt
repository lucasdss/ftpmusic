package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.Player
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QueueInteractionsTest {

    private val mockAppContext: android.app.Application = mockk(relaxed = true)
    private val queueManager = QueueManager(mockk())
    private lateinit var mockPlayer: Player
    private val mockPersistenceManager: QueuePersistenceManager = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns Uri.EMPTY
        mockPlayer = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
        PlayerHolder.player = null
    }

    // --- QueueManager.playFromIndex tests ---

    @Test
    fun `playFromIndex delegates to player seekToDefaultPosition`() {
        PlayerHolder.player = mockPlayer
        every { mockPlayer.mediaItemCount } returns 5
        every { mockPlayer.playWhenReady = any() } just Runs

        queueManager.playFromIndex(2)

        verify { mockPlayer.seekToDefaultPosition(2) }
        verify { mockPlayer.playWhenReady = true }
    }

    @Test
    fun `playFromIndex ignores null player`() {
        PlayerHolder.player = null

        queueManager.playFromIndex(0)

        // No NPE thrown — test passes
    }

    @Test
    fun `playFromIndex ignores out of bounds index`() {
        PlayerHolder.player = mockPlayer
        every { mockPlayer.mediaItemCount } returns 3

        queueManager.playFromIndex(-1)
        queueManager.playFromIndex(3)

        verify(exactly = 0) { mockPlayer.seekToDefaultPosition(any()) }
    }

    // --- PlaybackManager.playQueueItem tests ---

    @Test
    fun `playQueueItem delegates to QueueManager playFromIndex`() {
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

        playbackManager.playQueueItem(3)

        verify { mockQueueManager.playFromIndex(3) }
    }
}
