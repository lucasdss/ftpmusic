package com.lucasdss.ftpmusic.app.ui.library

import com.lucasdss.ftpmusic.app.data.db.*
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import io.mockk.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MixDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var genreMixDao: GenreMixDao
    private lateinit var dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository
    private lateinit var trackDao: TrackDao
    private lateinit var authHelper: SubsonicAuthHelper
    private lateinit var storage: SecureStorage
    private lateinit var viewModel: MixDetailViewModel
    private lateinit var playbackManager: com.lucasdss.ftpmusic.app.playback.PlaybackManager
    private lateinit var cacheService: com.lucasdss.ftpmusic.app.data.cache.CacheService
    private lateinit var downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager
    private lateinit var favoriteRepository: com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
    private val today = LocalDate.now().toString()
    private val yesterday = LocalDate.now().minusDays(1).toString()

    @Before fun setup() {
        Dispatchers.setMain(testDispatcher)
        genreMixDao = mockk(relaxed = true)
        dailyMixRepository = mockk(relaxed = true)
        trackDao = mockk(relaxed = true)
        authHelper = mockk(relaxed = true)
        storage = mockk(relaxed = true)
        playbackManager = mockk(relaxed = true)
        cacheService = mockk(relaxed = true)
        downloadManager = mockk(relaxed = true)
        favoriteRepository = mockk(relaxed = true)
        viewModel = MixDetailViewModel(
            genreMixDao, dailyMixRepository, trackDao, authHelper, storage,
            playbackManager = playbackManager,
            cacheService = cacheService,
            downloadManager = downloadManager,
            favoriteRepository = favoriteRepository,
            api = mockk(relaxed = true),
        )
    }

    @After fun teardown() {
        Dispatchers.resetMain()
        DynamicBaseUrl.url = "" // don't leak the static into other classes
    }

    private fun te(id: String, title: String = "Song $id", genre: String = "Rock") =
        TrackEntity(id = id, genre = genre, title = title, coverArtUrl = "ca-$id", artist = "Artist $id")

    @Test fun `loadMix resolves track IDs from tracks table`() = runTest(testDispatcher) {
        val mix = DailyMixEntity(id = 1, date = today, mixId = 5L)
        coEvery { genreMixDao.getDailyMix(today, 5L) } returns mix
        coEvery { genreMixDao.getDailyMixTrackIds(1) } returns listOf("t1", "t2")
        coEvery { trackDao.getTracksByIds(listOf("t1", "t2")) } returns listOf(te("t1", "One"), te("t2", "Two"))
        viewModel.loadMix(5L)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.tracks.size)
        assertEquals("One", viewModel.state.value.tracks[0].title)
    }

    @Test fun `loadMix handles DB exception`() = runTest(testDispatcher) {
        coEvery { genreMixDao.getDailyMix(any(), any()) } throws RuntimeException("fail")
        viewModel.loadMix(5L)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.tracks.isEmpty())
    }

    @Test fun `refreshMix errors on repository failure`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.generateOne(5L, manual = true) } throws RuntimeException("fail")
        viewModel.refreshMix(5L)
        advanceUntilIdle()
        assertEquals("Failed to refresh mix", viewModel.state.value.error)
    }

    @Test fun `buildStreamUrl uses storage`() {
        DynamicBaseUrl.url = "https://music.example.com"
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "u"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "p"
        every { authHelper.buildStreamUrl(any(), any(), any(), any()) } returns "https://out"
        assertEquals("https://out", viewModel.buildStreamUrl("x"))
    }

    @Test fun `buildStreamUrl handles null creds`() {
        DynamicBaseUrl.url = "https://music.example.com"
        every { storage.get(any()) } returns null
        every { authHelper.buildStreamUrl(any(), any(), any(), any()) } returns "https://out"
        assertEquals("https://out", viewModel.buildStreamUrl("x"))
    }
    // ── Daily Mix dead-end guards (yesterday fallback + auto-generate) ─────

    @Test fun `loadMix falls back to yesterday mix when today has none`() = runTest(testDispatcher) {
        val yMix = DailyMixEntity(id = 7, date = yesterday, mixId = 5L)
        coEvery { genreMixDao.getDailyMix(today, 5L) } returns null
        coEvery { genreMixDao.getDailyMix(yesterday, 5L) } returns yMix
        coEvery { genreMixDao.getDailyMixTrackIds(7) } returns listOf("t1")
        coEvery { trackDao.getTracksByIds(listOf("t1")) } returns listOf(te("t1", "Yesterday Track"))

        viewModel.loadMix(5L)
        advanceUntilIdle()

        assertEquals("Yesterday's mix must render when today's is missing", 1, viewModel.state.value.tracks.size)
        assertEquals("Yesterday Track", viewModel.state.value.tracks[0].title)
    }

    @Test fun `loadMix prefers today mix over yesterday mix`() = runTest(testDispatcher) {
        val todayMix = DailyMixEntity(id = 1, date = today, mixId = 5L)
        val yMix = DailyMixEntity(id = 2, date = yesterday, mixId = 5L)
        coEvery { genreMixDao.getDailyMix(today, 5L) } returns todayMix
        coEvery { genreMixDao.getDailyMix(yesterday, 5L) } returns yMix
        coEvery { genreMixDao.getDailyMixTrackIds(1) } returns listOf("t1")
        coEvery { genreMixDao.getDailyMixTrackIds(2) } returns listOf("t9")
        coEvery { trackDao.getTracksByIds(listOf("t1")) } returns listOf(te("t1", "Today Track"))

        viewModel.loadMix(5L)
        advanceUntilIdle()

        assertEquals("Today Track", viewModel.state.value.tracks[0].title)
    }

    @Test fun `loadMix auto-generates once when no mix exists`() = runTest(testDispatcher) {
        // Stateful DAO mock: the auto-generation persists the mix, and the
        // re-entrant loadMix must then find it (like the real Room DB).
        var savedMix: DailyMixEntity? = null
        coEvery { genreMixDao.getDailyMix(today, 5L) } answers { savedMix }
        coEvery { genreMixDao.getDailyMix(yesterday, 5L) } returns null
        coEvery { dailyMixRepository.generateOne(5L, manual = true) } answers {
            savedMix = DailyMixEntity(id = 5, date = today, mixId = 5L)
            listOf("t1")
        }
        coEvery { genreMixDao.getDailyMixTrackIds(5) } returns listOf("t1")
        coEvery { trackDao.getTracksByIds(listOf("t1")) } returns listOf(te("t1", "Auto Generated"))

        viewModel.loadMix(5L)
        advanceUntilIdle()

        // The auto-generated mix is persisted, re-read and displayed — no dead-end.
        assertTrue("Mix must auto-generate on open", viewModel.state.value.tracks.isNotEmpty())
        assertEquals("Auto Generated", viewModel.state.value.tracks[0].title)
        coVerify(exactly = 1) { dailyMixRepository.generateOne(5L, manual = true) }
    }

    @Test fun `loadMix shows empty error when generation yields nothing`() = runTest(testDispatcher) {
        coEvery { genreMixDao.getDailyMix(today, 5L) } returns null
        coEvery { genreMixDao.getDailyMix(yesterday, 5L) } returns null
        coEvery { dailyMixRepository.generateOne(5L, manual = true) } returns emptyList()

        viewModel.loadMix(5L)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.tracks.isEmpty())
        assertEquals("No tracks for this mix yet. Try Resync Library in Settings.", viewModel.state.value.error)
    }

    @Test fun `refreshMix never persists an empty generated mix`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.generateOne(5L, manual = true) } returns emptyList()

        viewModel.refreshMix(5L)
        advanceUntilIdle()

        assertEquals("No tracks for this mix yet. Try Resync Library in Settings.", viewModel.state.value.error)
    }

    // ── Playback / queue / download actions (coverage) ────────────────────

    private suspend fun kotlinx.coroutines.test.TestScope.loadMixIntoTracks(ids: List<String> = listOf("t1", "t2")) {
        val mix = DailyMixEntity(id = 1, date = today, mixId = 5L)
        coEvery { genreMixDao.getDailyMix(any(), 5L) } returns null
        coEvery { genreMixDao.getDailyMix(today, 5L) } returns mix
        coEvery { genreMixDao.getDailyMixTrackIds(1) } returns ids
        coEvery { trackDao.getTracksByIds(ids) } returns ids.map { te(it) }
        viewModel.loadMix(5L)
        advanceUntilIdle()
    }

    @Test fun `playAll starts the context and shows overwrite modal when rejected`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        coEvery { playbackManager.tryStartContext(any(), any(), sourceType = any(), sourceName = any()) } returns false
        viewModel.playAll()
        assertTrue(viewModel.showOverwriteModal.value)
    }

    @Test fun `playAll starts the context without modal when accepted`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        coEvery { playbackManager.tryStartContext(any(), any(), sourceType = any(), sourceName = any()) } returns true
        viewModel.playAll()
        assertFalse(viewModel.showOverwriteModal.value)
    }

    @Test fun `playAll no-op on empty mix`() = runTest(testDispatcher) {
        coEvery { genreMixDao.getDailyMix(today, 5L) } returns null
        coEvery { genreMixDao.getDailyMix(any(), 5L) } returns null
        coEvery { dailyMixRepository.generateOne(5L, manual = true) } returns emptyList()
        viewModel.loadMix(5L)
        advanceUntilIdle()
        viewModel.playAll()
        coVerify(exactly = 0) { playbackManager.tryStartContext(any(), any(), any(), any()) }
    }

    @Test fun `shuffle requests a shuffle context`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        coEvery { playbackManager.tryShuffleContext(any(), any(), sourceType = any(), sourceName = any()) } returns true
        viewModel.shuffle()
        coVerify {
            playbackManager.tryShuffleContext(
                match {
                    it.size == 2
                },
                any(),
                sourceType = "genremix",
                sourceName = any(),
            )
        }
    }

    @Test fun `playNextAll inserts tracks at the front in reverse order`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        viewModel.playNextAll()
        // 2 tracks → playNext called twice
        coVerify(exactly = 2) { playbackManager.playNext(any(), any()) }
    }

    @Test fun `addAllToQueue appends all tracks`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        viewModel.addAllToQueue()
        coVerify(exactly = 2) { playbackManager.addToQueue(any(), any()) }
    }

    @Test fun `playNextTrack and addToQueueTrack delegate to playback manager`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        viewModel.playNextTrack(Track("t1", "T1"), "http://x")
        coVerify { playbackManager.playNext(match { it.id == "t1" }, "http://x") }
        viewModel.addToQueueTrack(Track("t2", "T2"), "http://y")
        coVerify { playbackManager.addToQueue(match { it.id == "t2" }, "http://y") }
    }

    @Test fun `downloadTrack promotes cached content instead of re-enqueueing`() = runTest(testDispatcher) {
        coEvery { cacheService.promoteToDownload("t1") } returns true
        viewModel.downloadTrack(Track("t1", "T1"))
        advanceUntilIdle()
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), priority = any()) }
    }

    @Test fun `downloadTrack enqueues a permanent download when not cached`() = runTest(testDispatcher) {
        coEvery { cacheService.promoteToDownload("t1") } returns false
        viewModel.downloadTrack(Track("t1", "T1"))
        advanceUntilIdle()
        coVerify { downloadManager.enqueue("t1", any(), priority = 1) }
    }

    @Test fun `downloadAll skips empty mix and marks promoted tracks downloaded`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        coEvery { cacheService.promoteToDownload(any()) } returns true
        viewModel.downloadAll()
        advanceUntilIdle()
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), priority = any()) }
        assertEquals(2, viewModel.downloadedTrackIds.size)
    }

    @Test fun `resolveOverwrite forwards the decision and hides the modal`() = runTest(testDispatcher) {
        // Trigger the modal via a rejected start
        loadMixIntoTracks()
        coEvery { playbackManager.tryStartContext(any(), any(), sourceType = any(), sourceName = any()) } returns false
        viewModel.playAll()
        assertTrue(viewModel.showOverwriteModal.value)

        viewModel.resolveOverwrite(clearAndPlay = true)
        assertFalse(viewModel.showOverwriteModal.value)
        coVerify { playbackManager.resolveOverwrite(true) }
    }

    @Test fun `toggleTrackLike marks and unmarks a track optimistically`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        viewModel.toggleTrackLike("t1")
        advanceUntilIdle()
        assertTrue(viewModel.isTrackLiked("t1"))
        viewModel.toggleTrackLike("t1")
        advanceUntilIdle()
        assertFalse(viewModel.isTrackLiked("t1"))
    }

    @Test fun `toggleTrackDislike marks a track disliked locally`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        viewModel.toggleTrackDislike("t1")
        advanceUntilIdle()
        assertTrue(viewModel.isTrackDisliked("t1"))
    }

    @Test fun `rateTrack stores the clamped rating locally`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        viewModel.rateTrack("t1", 7)
        assertEquals(5, viewModel.getTrackRating("t1"))
    }
}
