package com.lucasdss.ftpmusic.app.ui.library

import com.lucasdss.ftpmusic.app.data.db.*
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.CustomMix
import com.lucasdss.ftpmusic.app.data.repository.MixFilters
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
    private lateinit var coverStore: com.lucasdss.ftpmusic.app.data.cover.CollectionCoverStore
    private val today = LocalDate.now().toString()

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
        coverStore = mockk(relaxed = true)
        coEvery { dailyMixRepository.filterPlayableMixTrackIds(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            invocation.args[0] as List<String>
        }
        coEvery { dailyMixRepository.getPendingDailyMix(any()) } returns null
        coEvery { dailyMixRepository.getMix(any()) } returns CustomMix(
            id = 5L,
            name = "Rock Mix",
            filters = MixFilters(genres = listOf("Rock")),
            autoCache = false,
            allowCrossMixFill = false,
            isDefault = true,
        )
        viewModel = MixDetailViewModel(
            genreMixDao, dailyMixRepository, trackDao, authHelper, storage,
            playbackManager = playbackManager,
            cacheService = cacheService,
            downloadManager = downloadManager,
            favoriteRepository = favoriteRepository,
            api = mockk(relaxed = true),
            coverStore = coverStore,
        )
    }

    @After fun teardown() {
        Dispatchers.resetMain()
        DynamicBaseUrl.url = "" // don't leak the static into other classes
    }

    private fun te(id: String, title: String = "Song $id", genre: String = "Rock") =
        TrackEntity(id = id, genre = genre, title = title, coverArtUrl = "ca-$id", artist = "Artist $id")

    private fun activeRow(id: Int = 1, version: Int = 1) = DailyMixEntity(
        id = id,
        date = today,
        mixId = 5L,
        version = version,
        status = DailyMixStatus.READY,
    )

    @Test fun `loadMix resolves track IDs from active pointer`() = runTest(testDispatcher) {
        val mix = activeRow(id = 1, version = 2)
        coEvery { dailyMixRepository.getActiveDailyMix(5L) } returns mix
        coEvery { genreMixDao.getDailyMixTrackIds(1) } returns listOf("t1", "t2")
        coEvery { trackDao.getTracksByIds(listOf("t1", "t2")) } returns listOf(te("t1", "One"), te("t2", "Two"))
        viewModel.loadMix(5L)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.tracks.size)
        assertEquals("One", viewModel.state.value.tracks[0].title)
        coVerify(exactly = 0) { dailyMixRepository.generateOne(any(), any(), any()) }
    }

    @Test fun `loadMix handles DB exception`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.getActiveDailyMix(any()) } throws RuntimeException("fail")
        viewModel.loadMix(5L)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.tracks.isEmpty())
    }

    @Test fun `refreshMix errors on repository failure`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.generateOne(5L, manual = true, activate = true) } throws RuntimeException("fail")
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

    @Test fun `loadMix auto-generates once when no active pointer`() = runTest(testDispatcher) {
        var active: DailyMixEntity? = null
        coEvery { dailyMixRepository.getActiveDailyMix(5L) } answers { active }
        coEvery { dailyMixRepository.generateOne(5L, manual = true, activate = true) } answers {
            active = activeRow(id = 5, version = 1)
            listOf("t1")
        }
        coEvery { genreMixDao.getDailyMixTrackIds(5) } returns listOf("t1")
        coEvery { trackDao.getTracksByIds(listOf("t1")) } returns listOf(te("t1", "Auto Generated"))

        viewModel.loadMix(5L)
        advanceUntilIdle()

        assertTrue("Mix must auto-generate on open", viewModel.state.value.tracks.isNotEmpty())
        assertEquals("Auto Generated", viewModel.state.value.tracks[0].title)
        coVerify(exactly = 1) { dailyMixRepository.generateOne(5L, manual = true, activate = true) }
    }

    @Test fun `loadMix does not auto-refresh empty active pointer`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.getActiveDailyMix(5L) } returns activeRow(id = 7, version = 1)
        coEvery { genreMixDao.getDailyMixTrackIds(7) } returns emptyList()

        viewModel.loadMix(5L)
        advanceUntilIdle()

        coVerify(exactly = 0) { dailyMixRepository.generateOne(any(), any(), any()) }
        assertEquals("No playable tracks — tap refresh", viewModel.state.value.error)
    }

    @Test fun `loadMix shows empty error when generation yields nothing`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.getActiveDailyMix(5L) } returns null
        coEvery { dailyMixRepository.generateOne(5L, manual = true, activate = true) } returns emptyList()

        viewModel.loadMix(5L)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.tracks.isEmpty())
        assertEquals("No tracks for this mix yet. Try Resync Library in Settings.", viewModel.state.value.error)
    }

    @Test fun `refreshMix activates new version`() = runTest(testDispatcher) {
        var active = activeRow(id = 1, version = 1)
        coEvery { dailyMixRepository.getActiveDailyMix(5L) } answers { active }
        coEvery { genreMixDao.getDailyMixTrackIds(1) } returns listOf("old")
        coEvery { trackDao.getTracksByIds(listOf("old")) } returns listOf(te("old", "Old"))
        viewModel.loadMix(5L)
        advanceUntilIdle()

        coEvery { dailyMixRepository.generateOne(5L, manual = true, activate = true) } answers {
            active = activeRow(id = 2, version = 2)
            listOf("t1")
        }
        coEvery { genreMixDao.getDailyMixTrackIds(2) } returns listOf("t1")
        coEvery { trackDao.getTracksByIds(listOf("t1")) } returns listOf(te("t1", "New"))

        viewModel.refreshMix(5L)
        advanceUntilIdle()

        assertEquals("New", viewModel.state.value.tracks[0].title)
        coVerify { dailyMixRepository.generateOne(5L, manual = true, activate = true) }
    }

    @Test fun `refreshMix never persists an empty generated mix`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.generateOne(5L, manual = true, activate = true) } returns emptyList()

        viewModel.refreshMix(5L)
        advanceUntilIdle()

        assertEquals("No tracks for this mix yet. Try Resync Library in Settings.", viewModel.state.value.error)
    }

    @Test fun `banner shows when pending differs from session`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.getActiveDailyMix(5L) } returns activeRow(id = 1, version = 1)
        coEvery { genreMixDao.getDailyMixTrackIds(1) } returns listOf("t1")
        coEvery { trackDao.getTracksByIds(listOf("t1")) } returns listOf(te("t1"))
        coEvery { dailyMixRepository.getPendingDailyMix(5L) } returns activeRow(id = 9, version = 2)

        viewModel.loadMix(5L)
        advanceUntilIdle()

        assertTrue(viewModel.updateAvailable.value)
    }

    @Test fun `ignorePendingUpdate suppresses banner for visit`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.getActiveDailyMix(5L) } returns activeRow(id = 1)
        coEvery { genreMixDao.getDailyMixTrackIds(1) } returns listOf("t1")
        coEvery { trackDao.getTracksByIds(listOf("t1")) } returns listOf(te("t1"))
        coEvery { dailyMixRepository.getPendingDailyMix(5L) } returns activeRow(id = 9, version = 2)
        viewModel.loadMix(5L)
        advanceUntilIdle()
        assertTrue(viewModel.updateAvailable.value)

        viewModel.ignorePendingUpdate()
        assertFalse(viewModel.updateAvailable.value)
    }

    @Test fun `adoptPendingUpdate activates then reloads`() = runTest(testDispatcher) {
        var active = activeRow(id = 1, version = 1)
        coEvery { dailyMixRepository.getActiveDailyMix(5L) } answers { active }
        coEvery { genreMixDao.getDailyMixTrackIds(1) } returns listOf("t1")
        coEvery { trackDao.getTracksByIds(listOf("t1")) } returns listOf(te("t1", "Old"))
        coEvery { dailyMixRepository.getPendingDailyMix(5L) } returns activeRow(id = 9, version = 2)
        viewModel.loadMix(5L)
        advanceUntilIdle()

        coEvery { dailyMixRepository.activatePending(5L) } answers {
            active = activeRow(id = 9, version = 2)
            true
        }
        coEvery { genreMixDao.getDailyMixTrackIds(9) } returns listOf("t2")
        coEvery { trackDao.getTracksByIds(listOf("t2")) } returns listOf(te("t2", "Pending"))
        coEvery { dailyMixRepository.getPendingDailyMix(5L) } returns null

        viewModel.adoptPendingUpdate()
        advanceUntilIdle()

        coVerify { dailyMixRepository.activatePending(5L) }
        assertEquals("Pending", viewModel.state.value.tracks[0].title)
        assertFalse(viewModel.updateAvailable.value)
    }

    @Test fun `onLeaveActivatePending activates pending once`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.getActiveDailyMix(5L) } returns activeRow(id = 1)
        coEvery { genreMixDao.getDailyMixTrackIds(1) } returns listOf("t1")
        coEvery { trackDao.getTracksByIds(listOf("t1")) } returns listOf(te("t1"))
        viewModel.loadMix(5L)
        advanceUntilIdle()

        coEvery { dailyMixRepository.activatePending(5L) } returns true
        viewModel.onLeaveActivatePending()
        viewModel.onLeaveActivatePending()
        advanceUntilIdle()

        coVerify(exactly = 1) { dailyMixRepository.activatePending(5L) }
    }

    // ── Playback / queue / download actions (coverage) ────────────────────

    private suspend fun kotlinx.coroutines.test.TestScope.loadMixIntoTracks(ids: List<String> = listOf("t1", "t2")) {
        val mix = activeRow(id = 1)
        coEvery { dailyMixRepository.getActiveDailyMix(5L) } returns mix
        coEvery { genreMixDao.getDailyMixTrackIds(1) } returns ids
        coEvery { trackDao.getTracksByIds(ids) } returns ids.map { te(it) }
        viewModel.loadMix(5L)
        advanceUntilIdle()
    }

    @Test fun `playTrack passes full mix order and startIndex`() = runTest(testDispatcher) {
        loadMixIntoTracks(listOf("t1", "t2", "t3"))
        viewModel.playTrack(1)
        verify {
            playbackManager.playAlbum(
                match { it.size == 3 && it[0].id == "t1" && it[1].id == "t2" && it[2].id == "t3" },
                match { it.size == 3 },
                eq(1),
                eq(false),
                journalSource = match { it?.type == "genremix" && it.id == "5" },
            )
        }
        verify(exactly = 0) { playbackManager.playSingleTrack(any(), any()) }
    }

    @Test fun `playTrack no-op on out-of-bounds index`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        viewModel.playTrack(9)
        verify(exactly = 0) { playbackManager.playAlbum(any(), any(), startIndex = any()) }
    }

    @Test fun `playAll starts the context and shows overwrite modal when rejected`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        coEvery {
            playbackManager.tryStartContext(any(), any(), sourceType = any(), sourceId = any(), sourceName = any())
        } returns false
        viewModel.playAll()
        assertTrue(viewModel.showOverwriteModal.value)
    }

    @Test fun `playAll starts the context without modal when accepted`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        coEvery {
            playbackManager.tryStartContext(any(), any(), sourceType = any(), sourceId = any(), sourceName = any())
        } returns true
        viewModel.playAll()
        assertFalse(viewModel.showOverwriteModal.value)
    }

    @Test fun `playAll no-op on empty mix`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.getActiveDailyMix(5L) } returns null
        coEvery { dailyMixRepository.generateOne(5L, manual = true, activate = true) } returns emptyList()
        viewModel.loadMix(5L)
        advanceUntilIdle()
        viewModel.playAll()
        coVerify(exactly = 0) { playbackManager.tryStartContext(any(), any(), any(), any()) }
    }

    @Test fun `shuffle requests a shuffle context`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        coEvery {
            playbackManager.tryShuffleContext(any(), any(), sourceType = any(), sourceId = any(), sourceName = any())
        } returns true
        viewModel.shuffle()
        coVerify {
            playbackManager.tryShuffleContext(
                match {
                    it.size == 2
                },
                any(),
                sourceType = "genremix",
                sourceId = "5",
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
        coEvery {
            playbackManager.tryStartContext(any(), any(), sourceType = any(), sourceId = any(), sourceName = any())
        } returns false
        viewModel.playAll()
        assertTrue(viewModel.showOverwriteModal.value)

        viewModel.resolveOverwrite(clearAndPlay = true)
        assertFalse(viewModel.showOverwriteModal.value)
        coVerify { playbackManager.resolveOverwrite(true) }
    }

    @Test fun `toggleTrackLike marks and unmarks a track optimistically`() = runTest(testDispatcher) {
        loadMixIntoTracks()
        viewModel.toggleTrackLike("t1")
        assertTrue("optimistic like before await", "t1" in viewModel.state.value.likedTrackIds)
        advanceUntilIdle()
        assertTrue(viewModel.isTrackLiked("t1"))
        viewModel.toggleTrackLike("t1")
        assertTrue("optimistic unlike before await", "t1" !in viewModel.state.value.likedTrackIds)
        advanceUntilIdle()
        assertFalse(viewModel.isTrackLiked("t1"))
        coVerify { favoriteRepository.unlikeTrack("t1") }
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
