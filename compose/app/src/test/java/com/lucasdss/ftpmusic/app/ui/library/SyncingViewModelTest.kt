package com.lucasdss.ftpmusic.app.ui.library

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.SyncStatus
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncingViewModelTest {

    private val metadataSyncWorker: MetadataSyncWorker = mockk(relaxed = true)
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val genreMixDao: GenreMixDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val lyricsCacheDao: LyricsCacheDao = mockk(relaxed = true)
    private val appContext: Context = mockk(relaxed = true)
    private val dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository =
        mockk(relaxed = true)
    private val sharedPrefs: SharedPreferences = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { appContext.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Create a fresh ViewModel for each test. */
    private fun createViewModel(): SyncingViewModel = SyncingViewModel(
        metadataSyncWorker,
        metadataDao,
        genreMixDao,
        playlistDao,
        lyricsCacheDao,
        appContext,
        dailyMixRepository,
    )

    /** Cancel the ViewModel's coroutine scope. Must be called inside runTest { }. */
    private fun cleanup(viewModel: SyncingViewModel) {
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `rebuildOnly regenerates mixes without server sync`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 5
        coEvery { genreMixDao.getTopGenres() } returns emptyList()
        coEvery { dailyMixRepository.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.repository.CustomMix(
                id = 1L,
                name = "Rock Mix",
                filters = com.lucasdss.ftpmusic.app.data.repository.MixFilters(genres = listOf("Rock")),
                autoCache = false,
                isDefault = true,
            ),
        )

        val viewModel = createViewModel()
        viewModel.startSync(rebuildOnly = true)
        advanceUntilIdle()

        assertTrue(viewModel.isDone.value)
        coVerify(exactly = 0) { metadataSyncWorker.syncNowAsync(any()) }
        coVerify(exactly = 1) { dailyMixRepository.generateAll(any(), true, any()) }
        assertEquals(5, viewModel.status.value.albums)
        cleanup(viewModel)
    }

    @Test
    fun `startSync triggers sync when no metadata exists`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 0
        coEvery { trackDao.getStarredIdsByIds(any()) } returns emptyList()
        coEvery { trackDao.getRatedTracksByIds(any()) } returns emptyList()
        // Not running — startSync's wait-for-idle passes immediately.
        // Must set phase="complete" so the collect {} in startSync() exits.
        every { metadataSyncWorker.status } returns MutableStateFlow(SyncStatus(phase = "complete", isRunning = false))

        val viewModel = createViewModel()
        viewModel.startSync()

        testScheduler.runCurrent()

        verify(atLeast = 1) { metadataSyncWorker.syncNowAsync(any()) }
        cleanup(viewModel)
    }

    @Test
    fun `generateDailyMixes delegates to repository after sync`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 5
        coEvery { dailyMixRepository.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.repository.CustomMix(
                id = 1L,
                name = "Rock Mix",
                filters = com.lucasdss.ftpmusic.app.data.repository.MixFilters(genres = listOf("Rock")),
                autoCache = false,
                isDefault = true,
            ),
        )
        // Completed status → collect persists metrics + generates mixes
        every { metadataSyncWorker.status } returns MutableStateFlow(SyncStatus(phase = "complete", isRunning = false))

        val viewModel = createViewModel()
        viewModel.startSync()
        advanceUntilIdle()

        coVerify(atLeast = 1) { dailyMixRepository.generateAll(any(), false, any()) }
        assertTrue(viewModel.isDone.value)
        cleanup(viewModel)
    }

    @Test
    fun `user-triggered sync forces track resync`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 42
        coEvery { trackDao.getTrackPlayCounts(any()) } returns emptyList()
        coEvery { trackDao.getStarredIdsByIds(any()) } returns emptyList()
        coEvery { trackDao.getRatedTracksByIds(any()) } returns emptyList()
        coEvery { genreMixDao.getTopGenres() } returns emptyList()
        val statusFlow = MutableStateFlow(
            SyncStatus(
                albums = 42,
                albumsTotal = 42,
                phase = "complete",
                isRunning = false,
            ),
        )
        every { metadataSyncWorker.status } returns statusFlow

        val viewModel = createViewModel()
        viewModel.startSync(userTriggered = true)
        advanceUntilIdle()

        // Resync Library must force a full track re-fetch
        verify(atLeast = 1) { metadataSyncWorker.syncNowAsync(forceTrackResync = true) }
        assertTrue(viewModel.isDone.value)
        cleanup(viewModel)
    }

    @Test
    fun `user-triggered sync preserves timer elapsedMs across worker emissions`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 42
        coEvery { trackDao.getTrackPlayCounts(any()) } returns emptyList()
        coEvery { trackDao.getStarredIdsByIds(any()) } returns emptyList()
        coEvery { trackDao.getRatedTracksByIds(any()) } returns emptyList()
        coEvery { genreMixDao.getTopGenres() } returns emptyList()
        // Worker status flow — starts running, emits a stale elapsedMs (frozen at 5s)
        // while the timer should be at a higher value.
        val statusFlow = MutableStateFlow(
            SyncStatus(
                albums = 42,
                albumsTotal = 42,
                phase = "tracks",
                isRunning = true,
                elapsedMs = 5000L,
            ),
        )
        every { metadataSyncWorker.status } returns statusFlow
        every { metadataSyncWorker.syncNowAsync(forceTrackResync = true) } returns mockk(relaxed = true)

        val viewModel = createViewModel()
        viewModel.startSync(userTriggered = true)
        testScheduler.runCurrent()

        // Advance virtual time 3 seconds — the timer should have elapsedMs ≈ 3000
        testScheduler.advanceTimeBy(3000L)
        testScheduler.runCurrent()

        // Worker emits a progress update with its own stale elapsedMs (5s frozen)
        statusFlow.value = SyncStatus(
            albums = 42,
            albumsTotal = 42,
            phase = "tracks",
            isRunning = true,
            albumTracksProgress = 10,
            elapsedMs = 5000L, // worker's stale value
        )
        testScheduler.runCurrent()

        // The ViewModel must preserve the timer's elapsedMs (~3000+), NOT the
        // worker's stale 5000L which would jump the timer backward.
        val elapsed = viewModel.status.value.elapsedMs
        assertTrue(
            "elapsedMs must stay close to timer value, not jump to worker's stale value. Got $elapsed",
            elapsed < 5000L,
        )
        cleanup(viewModel)
    }

    @Test
    fun `daily mix phase reports progress for every configured mix`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 5
        coEvery { dailyMixRepository.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.repository.CustomMix(
                id = 1L,
                name = "Rock Mix",
                filters = com.lucasdss.ftpmusic.app.data.repository.MixFilters(genres = listOf("Rock")),
                autoCache = false,
                isDefault = true,
            ),
            com.lucasdss.ftpmusic.app.data.repository.CustomMix(
                id = 2L,
                name = "Jazz Mix",
                filters = com.lucasdss.ftpmusic.app.data.repository.MixFilters(genres = listOf("Jazz")),
                autoCache = false,
                isDefault = true,
            ),
        )
        every { metadataSyncWorker.status } returns MutableStateFlow(SyncStatus(phase = "complete", isRunning = false))

        val viewModel = createViewModel()
        viewModel.startSync()
        advanceUntilIdle()

        coVerify(exactly = 1) { dailyMixRepository.generateAll(any(), false, any()) }
        assertTrue(viewModel.isDone.value)
        cleanup(viewModel)
    }

    @Test
    fun `startSync persists sync metrics to SharedPreferences on completion`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 0
        coEvery { trackDao.getStarredIdsByIds(any()) } returns emptyList()
        coEvery { trackDao.getRatedTracksByIds(any()) } returns emptyList()

        val metricsEditor: SharedPreferences.Editor = mockk(relaxed = true)
        every { sharedPrefs.edit() } returns metricsEditor
        every { metricsEditor.putInt(any(), any()) } returns metricsEditor
        every { metricsEditor.apply() } just Runs

        val statusFlow = MutableStateFlow(
            SyncStatus(
                albums = 5, albumsTotal = 5,
                artists = 3, artistsTotal = 3,
                albumTracksProgress = 100, albumTracksProgressTotal = 100,
                trackCount = 100,
                genres = 10, genresTotal = 10,
                phase = "complete", isRunning = false,
            ),
        )
        every { metadataSyncWorker.status } returns statusFlow

        val viewModel = createViewModel()
        viewModel.startSync(userTriggered = true)
        advanceUntilIdle()

        coVerify(atLeast = 1) { metricsEditor.putInt("sync_albums", any()) }
        coVerify(atLeast = 1) { metricsEditor.putInt("sync_tracks", any()) }
        coVerify(atLeast = 1) { metricsEditor.apply() }
        cleanup(viewModel)
    }

    @Test
    fun `status includes totals during sync`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 0
        coEvery { trackDao.getStarredIdsByIds(any()) } returns emptyList()
        coEvery { trackDao.getRatedTracksByIds(any()) } returns emptyList()
        val statusFlow = MutableStateFlow(
            SyncStatus(
                albums = 5, albumsTotal = 42,
                artists = 3, artistsTotal = 10,
                albumTracksProgress = 0, albumTracksProgressTotal = 500,
                genres = 2, genresTotal = 20,
                phase = "albums", isRunning = false, // not running so wait-for-idle passes
            ),
        )
        every { metadataSyncWorker.status } returns statusFlow

        val viewModel = createViewModel()
        viewModel.startSync()
        testScheduler.runCurrent()

        val s = viewModel.status.value
        assertEquals(5, s.albums)
        assertEquals(42, s.albumsTotal)
        assertEquals(3, s.artists)
        assertEquals(10, s.artistsTotal)
        assertEquals(0, s.albumTracksProgress)
        assertEquals(500, s.albumTracksProgressTotal)
        assertEquals(2, s.genres)
        assertEquals(20, s.genresTotal)
        assertEquals("albums", s.phase)
        cleanup(viewModel)
    }

    @Test
    fun `generateDailyMixes uses local-first repository generation`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 5
        coEvery { dailyMixRepository.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.repository.CustomMix(
                id = 1L,
                name = "Rock Mix",
                filters = com.lucasdss.ftpmusic.app.data.repository.MixFilters(genres = listOf("Rock")),
                autoCache = false,
                isDefault = true,
            ),
        )
        every { metadataSyncWorker.status } returns MutableStateFlow(SyncStatus(phase = "complete", isRunning = false))

        val viewModel = createViewModel()
        viewModel.startSync()
        advanceUntilIdle()

        // Generation is delegated to the repository (local DB pools only)
        coVerify(atLeast = 1) { dailyMixRepository.generateAll(any(), false, any()) }
        cleanup(viewModel)
    }

    // ── Error handling ───────────────────────────────────────────────────

    @Test
    fun `non-user-triggered error sets isError flag`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 0
        val statusFlow = MutableStateFlow(
            SyncStatus(
                phase = "error",
                isRunning = false,
            ),
        )
        every { metadataSyncWorker.status } returns statusFlow
        every { metadataSyncWorker.syncNowAsync(any()) } returns mockk(relaxed = true)

        val viewModel = createViewModel()
        viewModel.startSync(userTriggered = false)
        advanceUntilIdle()

        assertTrue("isError must be true after non-user-triggered error", viewModel.isError.value)
        assertTrue("isDone must be true after error", viewModel.isDone.value)
        cleanup(viewModel)
    }

    @Test
    fun `dailyMixTotal preserved during worker emission merge`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 0
        coEvery { dailyMixRepository.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.repository.CustomMix(
                id = 1L,
                name = "Rock Mix",
                filters = com.lucasdss.ftpmusic.app.data.repository.MixFilters(genres = listOf("Rock")),
                autoCache = false,
                isDefault = true,
            ),
            com.lucasdss.ftpmusic.app.data.repository.CustomMix(
                id = 2L,
                name = "Jazz Mix",
                filters = com.lucasdss.ftpmusic.app.data.repository.MixFilters(genres = listOf("Jazz")),
                autoCache = false,
                isDefault = true,
            ),
        )
        val statusFlow = MutableStateFlow(
            SyncStatus(
                albums = 1,
                albumsTotal = 1,
                phase = "albums",
                isRunning = false,
                dailyMixTotal = 0,
            ),
        )
        every { metadataSyncWorker.status } returns statusFlow
        every { metadataSyncWorker.syncNowAsync(any()) } returns mockk(relaxed = true)

        val viewModel = createViewModel()
        viewModel.startSync(userTriggered = false)
        testScheduler.runCurrent() // process coroutine until first suspension (collect blocks)

        // dailyMixTotal from ViewModel (2) must survive the worker emission (0)
        assertEquals(
            "dailyMixTotal must be preserved after worker emission merge",
            2,
            viewModel.status.value.dailyMixTotal,
        )
        cleanup(viewModel)
    }

    @Test
    fun `user-triggered handles null job from syncNowAsync gracefully`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 42
        coEvery { genreMixDao.getTopGenres() } returns emptyList()
        // isRunning=false so both status.first { } calls resolve immediately
        val flow = MutableStateFlow(SyncStatus(phase = "complete", isRunning = false))
        every { metadataSyncWorker.status } returns flow
        every { metadataSyncWorker.syncNowAsync(forceTrackResync = true) } returns null

        val viewModel = createViewModel()
        viewModel.startSync(userTriggered = true)
        advanceUntilIdle()

        assertFalse("isError must be false when existing sync completes", viewModel.isError.value)
        cleanup(viewModel)
    }

    @Test
    fun `initial status has zero totals to avoid 0-by-1 flash`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 0
        coEvery { genreMixDao.getTopGenres() } returns emptyList()
        val statusFlow = MutableStateFlow(SyncStatus(phase = "albums", isRunning = true))
        every { metadataSyncWorker.status } returns statusFlow
        every { metadataSyncWorker.syncNowAsync(any()) } returns mockk(relaxed = true)

        val viewModel = createViewModel()
        viewModel.startSync(userTriggered = false)
        testScheduler.runCurrent()

        val s = viewModel.status.value
        assertEquals("initial albumsTotal must be 0 not 1", 0, s.albumsTotal)
        assertEquals("initial artistsTotal must be 0 not 1", 0, s.artistsTotal)
        assertEquals("initial genresTotal must be 0 not 1", 0, s.genresTotal)
        cleanup(viewModel)
    }
}
