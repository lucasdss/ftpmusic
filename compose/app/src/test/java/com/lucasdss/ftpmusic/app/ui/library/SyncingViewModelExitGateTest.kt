package com.lucasdss.ftpmusic.app.ui.library

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.cache.NetworkPolicyHolder
import com.lucasdss.ftpmusic.app.data.cache.NetworkPolicyState
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.LibrarySyncMode
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.SyncStatus
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncingViewModelExitGateTest {

    private val metadataSyncWorker: MetadataSyncWorker = mockk(relaxed = true)
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val genreMixDao: GenreMixDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val lyricsCacheDao: LyricsCacheDao = mockk(relaxed = true)
    private val appContext: Context = mockk(relaxed = true)
    private val dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository =
        mockk(relaxed = true)
    private val networkPolicyHolder: NetworkPolicyHolder = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { appContext.getSharedPreferences(any(), any()) } returns mockk(relaxed = true) {
            every { edit() } returns mockk(relaxed = true)
        }
        NetworkPolicyState.resetForTests()
        every { networkPolicyHolder.shouldWarnFirstLoginOnCellular() } returns false
        coEvery { metadataDao.albumCount() } returns 0
        coEvery { dailyMixRepository.getAll() } returns emptyList()
        every { metadataSyncWorker.syncNowAsync(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every {
            metadataSyncWorker.syncNowAsync(
                mode = LibrarySyncMode.FULL,
                allowCellularOverride = any(),
                bypassCooldown = any(),
            )
        } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = SyncingViewModel(
        metadataSyncWorker,
        metadataDao,
        trackDao,
        genreMixDao,
        playlistDao,
        lyricsCacheDao,
        appContext,
        dailyMixRepository,
        networkPolicyHolder,
    )

    private fun runningFlow(runId: Long = 1L) = MutableStateFlow(
        SyncStatus(phase = "albums", isRunning = true, runId = runId, drainComplete = false),
    )

    @Test
    fun `B0-1 soft-stop does not auto-complete or generate mixes`() = runTest(testDispatcher) {
        val flow = runningFlow(7L)
        every { metadataSyncWorker.status } returns flow

        val vm = createVm()
        vm.startSync()
        runCurrent()
        advanceTimeBy(50)
        runCurrent()
        flow.value = SyncStatus(
            phase = "complete",
            isRunning = false,
            runId = 7L,
            drainComplete = false,
            albums = 3,
            albumsTotal = 3,
        )
        runCurrent()
        advanceTimeBy(50)
        runCurrent()

        assertTrue(vm.isPartial.value)
        assertFalse(vm.isDone.value)
        coVerify(exactly = 0) { dailyMixRepository.generateAll(any(), any(), any()) }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `B0-2 stale complete with wrong runId does not exit`() = runTest(testDispatcher) {
        val flow = runningFlow(2L)
        every { metadataSyncWorker.status } returns flow

        val vm = createVm()
        vm.startSync()
        runCurrent()
        advanceTimeBy(50)
        runCurrent()
        flow.value = SyncStatus(phase = "complete", isRunning = false, runId = 1L, drainComplete = true)
        runCurrent()
        advanceTimeBy(50)
        runCurrent()

        assertFalse(vm.isDone.value)
        assertFalse(vm.isPartial.value)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `B0-3 first-login null syncNowAsync sets error not success`() = runTest(testDispatcher) {
        val flow = MutableStateFlow(SyncStatus(phase = "idle", isRunning = false, runId = 0L))
        every { metadataSyncWorker.status } returns flow
        every { metadataSyncWorker.syncNowAsync(any(), any(), any(), any()) } returns null

        val vm = createVm()
        vm.startSync()
        runCurrent()
        advanceTimeBy(50)
        runCurrent()

        assertTrue(vm.isDone.value)
        assertTrue(vm.isError.value)
        assertFalse(vm.isPartial.value)
        coVerify(exactly = 0) { dailyMixRepository.generateAll(any(), any(), any()) }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `B0-4 cellular cancel is paused not success`() = runTest(testDispatcher) {
        val vm = createVm()
        vm.cancelCellularSync()
        assertTrue(vm.isCancelled.value)
        assertFalse(vm.isDone.value)
        assertFalse(vm.isError.value)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `B0-5 continueIncomplete skips mixes and marks incomplete phase`() = runTest(testDispatcher) {
        val vm = createVm()
        vm.continueIncomplete()
        assertTrue(vm.isDone.value)
        assertFalse(vm.isError.value)
        assertTrue(vm.status.value.phase == "incomplete_continue")
        coVerify(exactly = 0) { dailyMixRepository.generateAll(any(), any(), any()) }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `B0-7 null syncNowAsync adopts already-running instead of Sync failed`() = runTest(testDispatcher) {
        val flow = runningFlow(9L)
        every { metadataSyncWorker.status } returns flow
        every { metadataSyncWorker.syncNowAsync(any(), any(), any(), any()) } returns null
        every {
            metadataSyncWorker.syncNowAsync(
                mode = LibrarySyncMode.FULL,
                allowCellularOverride = any(),
                bypassCooldown = any(),
            )
        } returns null

        val vm = createVm()
        vm.startSync()
        runCurrent()
        advanceTimeBy(50)
        runCurrent()

        assertFalse(vm.isError.value)
        assertFalse(vm.isDone.value)
        assertTrue(vm.status.value.isRunning)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `B0-6 drain complete finishes success path`() = runTest(testDispatcher) {
        coEvery { dailyMixRepository.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.repository.CustomMix(
                id = 1L,
                name = "Rock Mix",
                filters = com.lucasdss.ftpmusic.app.data.repository.MixFilters(genres = listOf("Rock")),
                autoCache = false,
                isDefault = true,
            ),
        )
        val flow = runningFlow(5L)
        every { metadataSyncWorker.status } returns flow

        val vm = createVm()
        vm.startSync()
        runCurrent()
        advanceTimeBy(50)
        runCurrent()
        flow.value = SyncStatus(
            phase = "complete",
            isRunning = false,
            runId = 5L,
            drainComplete = true,
            albums = 10,
            albumsTotal = 10,
        )
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(vm.isDone.value)
        assertFalse(vm.isPartial.value)
        assertFalse(vm.isError.value)
        vm.viewModelScope.cancel()
    }
}
