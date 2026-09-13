package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.data.model.Track
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class QueueAutosaveTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockAppContext: android.app.Application = mockk(relaxed = true)
    private val mockPlayer: Player = mockk(relaxed = true)
    private val mockPersistenceManager: QueuePersistenceManager = mockk(relaxed = true)
    private val queueManager = QueueManager(mockk())
    private val castPreferences: CastPreferences = mockk(relaxed = true)
    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns Uri.EMPTY
        PlayerHolder.player = mockPlayer
        playbackManager =
            PlaybackManager(
                queueManager,
                mockPersistenceManager,
                mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.cache.DownloadManager>(relaxed = true),
                castPreferences,
                mockAppContext,
                mockk<com.lucasdss.ftpmusic.app.data.db.QueueJournalDao>(relaxed = true),
            )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        playbackManager.destroy()
        unmockkStatic(Uri::class)
        PlayerHolder.player = null
    }

    @Test
    fun `playAlbum triggers save`() = runTest {
        val tracks = listOf(
            Track("t1", "Song 1", artist = "Artist A", duration = 200, coverArt = "ca-1"),
            Track("t2", "Song 2", artist = "Artist A", duration = 180),
        )
        val urls = tracks.map { "http://server/rest/stream?id=${it.id}" }

        playbackManager.playAlbum(tracks, urls)

        coVerify(timeout = 2000) {
            mockPersistenceManager.save(tracks, any(), 0, 0L, any(), any(), any(), any())
        }
    }

    @Test
    fun `shuffleAlbum triggers save`() = runTest {
        val tracks = listOf(
            Track("t1", "S1", duration = 100),
            Track("t2", "S2", duration = 100),
        )
        val urls = tracks.map { "http://server/rest/stream?id=${it.id}" }

        playbackManager.shuffleAlbum(tracks, urls)

        coVerify(timeout = 2000) {
            mockPersistenceManager.save(tracks, any(), 0, 0L, any(), any(), any(), any())
        }
    }

    @Test
    fun `playSingleTrack triggers save`() = runTest {
        val track = Track(
            "t-solo",
            "Solo Track",
            artist = "Solo Artist",
            album = "Solo Album",
            duration = 250,
            coverArt = "ca-solo",
        )
        val url = "http://server/rest/stream?id=t-solo"

        playbackManager.playSingleTrack(track, url)

        coVerify(timeout = 2000) {
            mockPersistenceManager.save(listOf(track), any(), 0, 0L, any(), any(), any(), any())
        }
    }

    @Test
    fun `addToQueue triggers save`() = runTest {
        // Add a track first so the queue is non-empty and player reports state
        val track = Track("t-add", "Add Me", duration = 150, coverArt = "ca-add")
        val url = "http://server/rest/stream?id=t-add"
        every { mockPlayer.mediaItemCount } returns 0
        every { mockPlayer.currentMediaItemIndex } returns 0

        playbackManager.addToQueue(track, url)

        // addToQueue builds current state from player, then appends the new track
        coVerify(timeout = 2000) {
            mockPersistenceManager.save(any(), any(), 0, 0L, any(), any(), any(), any())
        }
    }

    @Test
    fun `clearQueue triggers clear on persistence manager`() = runTest {
        playbackManager.clearQueue()

        coVerify(timeout = 2000) {
            mockPersistenceManager.clear()
        }
    }
}
