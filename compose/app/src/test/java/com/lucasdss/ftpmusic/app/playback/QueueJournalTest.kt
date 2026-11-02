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

    @Test
    fun `playAlbum journalSource journals the tapped context without clearing priority`() = runTest {
        val queued = Track("q1", "Queued", artist = "Queue Artist", duration = 120)
        playbackManager.playNext(queued, "http://server/rest/stream?id=q1")
        val priorityBefore = playbackManager.priorityQueueSize
        assertTrue("test must seed a priority queue", priorityBefore > 0)

        val tracks = listOf(
            Track("t1", "Song 1", artist = "Artist A", albumId = "al-1", duration = 200),
            Track("t2", "Song 2", artist = "Artist A", albumId = "al-1", duration = 180),
        )
        val urls = tracks.map { "http://server/rest/stream?id=${it.id}" }

        playbackManager.playAlbum(
            tracks,
            urls,
            startIndex = 1,
            journalSource = PlaybackManager.JournalSource("album", "al-1", "Test Album"),
        )
        advanceUntilIdle()

        assertEquals(
            "CP-12: journal-only source must not clear Priority",
            priorityBefore,
            playbackManager.priorityQueueSize,
        )
        coVerify {
            queueJournalDao.upsert(
                match {
                    it.sourceType == "album" &&
                        it.sourceId == "al-1" &&
                        it.sourceName == "Test Album" &&
                        it.trackIdsJson.contains("t1") &&
                        it.trackIdsJson.contains("t2") &&
                        it.position == 1
                },
            )
        }
    }

    @Test
    fun `playAlbum legacy sourceType still clears priority and journals`() = runTest {
        val queued = Track("q1", "Queued", artist = "Queue Artist", duration = 120)
        playbackManager.playNext(queued, "http://server/rest/stream?id=q1")
        assertTrue(playbackManager.priorityQueueSize > 0)

        val tracks = listOf(
            Track("t1", "Song 1", artist = "Artist A", albumId = "al-1", duration = 200),
        )
        val urls = tracks.map { "http://server/rest/stream?id=${it.id}" }

        playbackManager.playAlbum(
            tracks,
            urls,
            sourceType = "album",
            sourceId = "al-1",
            sourceName = "Test Album",
        )
        advanceUntilIdle()

        assertEquals("legacy sourceType keeps its overwrite semantics", 0, playbackManager.priorityQueueSize)
        coVerify {
            queueJournalDao.upsert(match { it.sourceType == "album" && it.sourceId == "al-1" })
        }
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

    @Test
    fun `playSingleTrack journalSource journals search context and keeps priority`() = runTest {
        val queued = Track("q1", "Queued", artist = "Queue Artist", duration = 120)
        playbackManager.playNext(queued, "http://server/rest/stream?id=q1")
        val priorityBefore = playbackManager.priorityQueueSize
        assertTrue(priorityBefore > 0)

        val track = Track("t1", "Song 1", artist = "Artist A", duration = 200)
        val url = "http://server/rest/stream?id=t1"

        playbackManager.playSingleTrack(
            track,
            url,
            journalSource = PlaybackManager.JournalSource("search", "search", "Search"),
        )
        advanceUntilIdle()

        assertEquals("single-track starts keep Priority", priorityBefore, playbackManager.priorityQueueSize)
        coVerify {
            queueJournalDao.upsert(
                match {
                    it.sourceType == "search" &&
                        it.sourceId == "search" &&
                        it.trackIdsJson.contains("t1")
                },
            )
        }
    }

    @Test
    fun `repeated search taps journal under the same key instead of a new key per tap`() = runTest {
        val first = Track("s1", "First", duration = 100)
        val second = Track("s2", "Second", duration = 100)

        playbackManager.playSingleTrack(
            first,
            "http://server/rest/stream?id=s1",
            journalSource = PlaybackManager.JournalSource("search", "search", "Search"),
        )
        playbackManager.playSingleTrack(
            second,
            "http://server/rest/stream?id=s2",
            journalSource = PlaybackManager.JournalSource("search", "search", "Search"),
        )
        advanceUntilIdle()

        coVerify(exactly = 2) {
            queueJournalDao.upsert(match { it.sourceType == "search" && it.sourceId == "search" })
        }
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
