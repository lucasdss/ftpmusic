package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.data.db.QueueJournalDao
import com.lucasdss.ftpmusic.app.data.model.Track
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueJournalTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)

    private val mockAppContext: android.app.Application = mockk(relaxed = true)
    private val mockPlayer: Player = mockk(relaxed = true)
    private val queueManager: QueueManager = QueueManager(mockk())
    private val mockPersistenceManager: QueuePersistenceManager = mockk(relaxed = true)
    private val mockOfflineModeManager: com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager =
        mockk(relaxed = true)
    private val mockDownloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)
    private val queueJournalDao: QueueJournalDao = mockk(relaxed = true)

    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns Uri.EMPTY
        PlayerHolder.player = mockPlayer
        playbackManager = PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            queueJournalDao,
            testScope,
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
        PlayerHolder.player = null
    }

    // ── playAlbum journaling ──────────────────────────────────────────────

    @Test
    fun `playAlbum with source journals queue`() = runTest {
        val tracks = listOf(
            Track("t1", "Song 1", artist = "Artist A", albumId = "al-1", duration = 200),
            Track("t2", "Song 2", artist = "Artist A", albumId = "al-1", duration = 180),
        )
        val urls = tracks.map { "http://server/rest/stream?id=${it.id}" }

        playbackManager.playAlbum(tracks, urls, sourceType = "album", sourceId = "al-1", sourceName = "Test Album")
        advanceUntilIdle()
        coVerify { queueJournalDao.upsert(any()) }
    }

    @Test
    fun `playAlbum without source does not journal`() = runTest {
        val tracks = listOf(
            Track("t1", "Song 1", artist = "Artist A", albumId = "al-1", duration = 200),
        )
        val urls = tracks.map { "http://server/rest/stream?id=${it.id}" }

        playbackManager.playAlbum(tracks, urls)
        advanceUntilIdle()
        coVerify(exactly = 0) { queueJournalDao.upsert(any()) }
    }

    // ── shuffleAlbum journaling ───────────────────────────────────────────

    @Test
    fun `shuffleAlbum with same sourceId upserts same entry`() = runTest {
        val tracks = listOf(
            Track("t1", "Song 1", artist = "Artist A", albumId = "al-1", duration = 200),
            Track("t2", "Song 2", artist = "Artist A", albumId = "al-1", duration = 180),
        )
        val urls = tracks.map { "http://server/rest/stream?id=${it.id}" }

        // Play album first
        playbackManager.playAlbum(
            tracks,
            urls,
            sourceType = "album",
            sourceId = "al-1",
            sourceName = "Test Album",
        )
        advanceUntilIdle()
        // Then shuffle same album — should upsert, not insert duplicate
        val shuffledUrls = tracks.map { "http://server/rest/stream?id=${it.id}" }
        playbackManager.shuffleAlbum(
            tracks,
            shuffledUrls,
            sourceType = "album",
            sourceId = "al-1",
            sourceName = "Test Album",
        )
        advanceUntilIdle()
        coVerify(atLeast = 2) { queueJournalDao.upsert(match { it.sourceType == "album" && it.sourceId == "al-1" }) }
    }

    @Test
    fun `shuffleAlbum without source does not journal`() = runTest {
        val tracks = listOf(
            Track("t1", "Song 1", artist = "Artist A", albumId = "al-1", duration = 200),
        )
        val urls = tracks.map { "http://server/rest/stream?id=${it.id}" }

        playbackManager.shuffleAlbum(tracks, urls)
        advanceUntilIdle()
        coVerify(exactly = 0) { queueJournalDao.upsert(any()) }
    }

    // ── addToQueue does NOT journal ───────────────────────────────────────

    @Test
    fun `addToQueue does not journal`() = runTest {
        val track = Track("t1", "Song 1", artist = "Artist A", duration = 200)
        val url = "http://server/rest/stream?id=t1"

        playbackManager.addToQueue(track, url)
        advanceUntilIdle()
        coVerify(exactly = 0) { queueJournalDao.upsert(any()) }
    }

    // ── playStream does NOT journal ───────────────────────────────────────

    @Test
    fun `playStream does not journal`() = runTest {
        playbackManager.playStream("http://radio.example.com/stream", "Radio Station")
        advanceUntilIdle()
        coVerify(exactly = 0) { queueJournalDao.upsert(any()) }
    }

    // ── playSingleTrack journaling ────────────────────────────────────────

    @Test
    fun `playSingleTrack with source journals`() = runTest {
        val track = Track("t1", "Song 1", artist = "Artist A", duration = 200)
        val url = "http://server/rest/stream?id=t1"

        playbackManager.playSingleTrack(track, url, sourceType = "album", sourceId = "al-1", sourceName = "Test Album")
        advanceUntilIdle()
        coVerify { queueJournalDao.upsert(any()) }
    }

    @Test
    fun `playSingleTrack without source does not journal`() = runTest {
        val track = Track("t1", "Song 1", artist = "Artist A", duration = 200)
        val url = "http://server/rest/stream?id=t1"

        playbackManager.playSingleTrack(track, url)
        advanceUntilIdle()
        coVerify(exactly = 0) { queueJournalDao.upsert(any()) }
    }

    // ── journaling is best-effort ─────────────────────────────────────────

    @Test
    fun `journaling is best-effort — exceptions are swallowed`() = runTest {
        val track = Track("t1", "Song 1", artist = "Artist A", duration = 200)
        val url = "http://server/rest/stream?id=t1"

        // Make upsert throw — journalQueue must catch it silently
        val throwingDao: QueueJournalDao = mockk(relaxed = true)
        coEvery { throwingDao.upsert(any()) } throws RuntimeException("Simulated DB failure")

        val pm = PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            throwingDao,
            testScope,
        )

        // This must NOT throw
        pm.playAlbum(
            listOf(track),
            listOf(url),
            sourceType = "album",
            sourceId = "al-1",
            sourceName = "Broken DB",
        )
        advanceUntilIdle()

        // Verify that upsert was attempted (threw) but no crash propagated
        coVerify { throwingDao.upsert(any()) }

        // Cleanup
        pm.destroy()
    }
}
