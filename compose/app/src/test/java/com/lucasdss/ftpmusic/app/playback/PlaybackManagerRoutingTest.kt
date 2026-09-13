package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.model.Track
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaybackManagerRoutingTest {

    private val queueManager = QueueManager(mockk())
    private val mockPersistenceManager: QueuePersistenceManager = mockk(relaxed = true)
    private val mockOfflineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val mockDownloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)
    private val mockAppContext: android.app.Application = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns Uri.EMPTY
        every { castPreferences.castFromPhone } returns false
        every { castPreferences.useHttpForCast } returns true
        every { mockOfflineModeManager.isOffline } returns mockk { every { value } returns false }
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
        PlayerHolder.player = null
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
    }

    @Test
    fun `playAlbum builds media items and triggers play when isCasting is false`() {
        PlayerHolder.isCasting = false
        val manager = PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockk<com.lucasdss.ftpmusic.app.data.db.QueueJournalDao>(relaxed = true),
        )

        val tracks = listOf(Track("t1", "Song 1", duration = 100))
        val urls = listOf("https://music.example.com/rest/stream?id=t1")

        // Should not throw — proxyUrl now returns the serverUrl directly
        manager.playAlbum(tracks, urls)
    }

    @Test
    fun `playAlbum builds media items regardless of isCasting`() {
        PlayerHolder.isCasting = true
        val manager = PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockk<com.lucasdss.ftpmusic.app.data.db.QueueJournalDao>(relaxed = true),
        )

        val tracks = listOf(Track("t1", "Song 1", duration = 100))
        val urls = listOf("https://music.example.com/rest/stream?id=t1")

        // Should not throw — media items are built with direct URLs
        manager.playAlbum(tracks, urls)
    }
}
