package com.lucasdss.ftpmusic.app.data.cache

import com.lucasdss.ftpmusic.app.data.db.CacheQueueDao
import com.lucasdss.ftpmusic.app.data.db.CacheQueueItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Plain-JVM tests for [DownloadViewModel]: state aggregation from the queue
 * flow and the four action methods.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelTest {

    private val cacheQueueDao: CacheQueueDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        // viewModelScope runs on Main; bind it to the test scheduler.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = DownloadViewModel(cacheQueueDao)

    @Test
    fun `state aggregates item counts by status`() = runTest {
        coEvery { cacheQueueDao.getAllFlow() } returns flowOf(
            listOf(
                CacheQueueItemEntity(id = 1, trackId = "t1", remoteUrl = "http://a", status = "pending", priority = 0),
                CacheQueueItemEntity(
                    id = 2,
                    trackId = "t2",
                    remoteUrl = "http://b",
                    status = "completed",
                    priority = 1,
                ),
                CacheQueueItemEntity(id = 3, trackId = "t3", remoteUrl = "http://c", status = "failed", priority = 2),
                CacheQueueItemEntity(
                    id = 4,
                    trackId = "t4",
                    remoteUrl = "http://d",
                    status = "processing",
                    priority = 3,
                ),
            ),
        )
        val viewModel = newViewModel()

        // Unconfined Main runs the init collect synchronously
        val state = viewModel.state.value
        assertEquals(4, state.items.size)
        assertEquals(1, state.pendingCount)
        assertEquals(1, state.completedCount)
        assertEquals(1, state.failedCount)
        assertEquals("pending", state.items[0].status)
        assertEquals(3, state.items[3].priority)
    }

    @Test
    fun `state defaults to empty before the first emission`() {
        val viewModel = newViewModel()
        assertEquals(0, viewModel.state.value.items.size)
        assertEquals(0, viewModel.state.value.pendingCount)
    }

    @Test
    fun `cancelDownload marks the item cancelled`() = runTest {
        coEvery { cacheQueueDao.getAllFlow() } returns flowOf(emptyList())
        val viewModel = newViewModel()
        viewModel.cancelDownload(5)
        coVerify { cacheQueueDao.updateStatus(5, "cancelled") }
    }

    @Test
    fun `retryDownload marks the item pending`() = runTest {
        coEvery { cacheQueueDao.getAllFlow() } returns flowOf(emptyList())
        val viewModel = newViewModel()
        viewModel.retryDownload(7)
        coVerify { cacheQueueDao.updateStatus(7, "pending") }
    }

    @Test
    fun `clearCompleted deletes completed and cancelled rows`() = runTest {
        coEvery { cacheQueueDao.getAllFlow() } returns flowOf(emptyList())
        val viewModel = newViewModel()
        viewModel.clearCompleted()
        coVerify { cacheQueueDao.deleteByStatus("completed") }
        coVerify { cacheQueueDao.deleteByStatus("cancelled") }
    }

    @Test
    fun `clearAll deletes every row`() = runTest {
        coEvery { cacheQueueDao.getAllFlow() } returns flowOf(emptyList())
        val viewModel = newViewModel()
        viewModel.clearAll()
        coVerify { cacheQueueDao.deleteAll() }
    }
}
