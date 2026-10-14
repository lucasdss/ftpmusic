package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import androidx.work.ListenableWorker
import com.lucasdss.ftpmusic.app.data.update.AppUpdateProbe
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SyncScheduleWorkerTest {

    private lateinit var metadataSyncWorker: MetadataSyncWorker
    private lateinit var appUpdateProbe: AppUpdateProbe
    private lateinit var worker: SyncScheduleWorker
    private lateinit var statusFlow: MutableStateFlow<SyncStatus>

    @Before
    fun setup() {
        ReachabilityStateHolder.onApiSuccess()
        metadataSyncWorker = mockk(relaxed = true)
        appUpdateProbe = mockk(relaxed = true)
        coEvery { appUpdateProbe.probe(any()) } returns Unit
        statusFlow = MutableStateFlow(SyncStatus(phase = "complete"))
        every { metadataSyncWorker.status } returns statusFlow
        every { metadataSyncWorker.lastFullSyncMs() } returns System.currentTimeMillis() // recent → DELTA
        every { metadataSyncWorker.syncNowAsync(any(), any()) } answers {
            statusFlow.value = SyncStatus(phase = "complete")
            Job().apply { complete() }
        }
        val context = mockk<Context>(relaxed = true)
        val workerParams = mockk<androidx.work.WorkerParameters>(relaxed = true)
        worker = spyk(SyncScheduleWorker(context, workerParams, metadataSyncWorker, appUpdateProbe))
    }

    @After
    fun tearDown() {
        ReachabilityStateHolder.onApiSuccess()
    }

    @Test
    fun `doWork calls syncNowAsync DELTA when last full sync recent`() = runBlocking {
        val recent = System.currentTimeMillis()
        every { metadataSyncWorker.lastFullSyncMs() } returns recent
        val result = worker.doWork()
        verify {
            metadataSyncWorker.syncNowAsync(
                forceTrackResync = false,
                mode = LibrarySyncMode.DELTA,
            )
        }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork calls syncNowAsync FULL when last full sync never`() = runBlocking {
        every { metadataSyncWorker.lastFullSyncMs() } returns 0L
        worker.doWork()
        verify {
            metadataSyncWorker.syncNowAsync(
                forceTrackResync = false,
                mode = LibrarySyncMode.FULL,
            )
        }
    }

    @Test
    fun `doWork calls syncNowAsync FULL when last full sync older than 7 days`() = runBlocking {
        val eightDaysAgo = System.currentTimeMillis() - MetadataSyncWorker.FULL_SYNC_INTERVAL_MS - 1000
        every { metadataSyncWorker.lastFullSyncMs() } returns eightDaysAgo
        worker.doWork()
        verify {
            metadataSyncWorker.syncNowAsync(
                forceTrackResync = false,
                mode = LibrarySyncMode.FULL,
            )
        }
    }

    @Test
    fun `doWork returns Success when sync succeeds`() = runBlocking {
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        coVerify { appUpdateProbe.probe(any()) }
    }

    @Test
    fun `doWork still Success when update probe throws`() = runBlocking {
        coEvery { appUpdateProbe.probe(any()) } throws RuntimeException("play boom")
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork propagates CancellationException from update probe`() = runBlocking {
        coEvery { appUpdateProbe.probe(any()) } throws CancellationException("cancelled")
        try {
            worker.doWork()
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test
    fun `doWork skips probe when sync skipped already_running`() = runBlocking {
        every { metadataSyncWorker.syncNowAsync(any(), any()) } returns null
        every { metadataSyncWorker.lastSkipReason() } returns "already_running"
        worker.doWork()
        coVerify(exactly = 0) { appUpdateProbe.probe(any()) }
    }

    @Test
    fun `doWork returns Success when sync skipped already_running`() = runBlocking {
        every { metadataSyncWorker.syncNowAsync(any(), any()) } returns null
        every { metadataSyncWorker.lastSkipReason() } returns "already_running"
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork returns Success when sync skipped for cooldown`() = runBlocking {
        every { metadataSyncWorker.syncNowAsync(any(), any()) } returns null
        every { metadataSyncWorker.lastSkipReason() } returns "cooldown"
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork returns Retry when sync skipped for cellular_policy`() = runBlocking {
        every { metadataSyncWorker.syncNowAsync(any(), any()) } returns null
        every { metadataSyncWorker.lastSkipReason() } returns "cellular_policy"
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `doWork awaits Job before returning`() = runBlocking {
        val deferredJob = Job()
        every { metadataSyncWorker.syncNowAsync(any(), any()) } answers {
            statusFlow.value = SyncStatus(phase = "complete")
            deferredJob
        }
        val resultDeferred = async { worker.doWork() }
        assertFalse(resultDeferred.isCompleted)
        deferredJob.complete()
        val result = resultDeferred.await()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork returns Retry when sync ends in error phase`() = runBlocking {
        every { metadataSyncWorker.syncNowAsync(any(), any()) } answers {
            statusFlow.value = SyncStatus(phase = "error", isRunning = false)
            Job().apply { complete() }
        }
        every { worker.runAttemptCount } returns 1
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `doWork returns Failure when error phase after max retries`() = runBlocking {
        every { metadataSyncWorker.syncNowAsync(any(), any()) } answers {
            statusFlow.value = SyncStatus(phase = "error", isRunning = false)
            Job().apply { complete() }
        }
        every { worker.runAttemptCount } returns 5
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `doWork returns Failure when sync throws after max retries`() = runBlocking {
        every { metadataSyncWorker.syncNowAsync(any(), any()) } throws RuntimeException("network error")
        every { worker.runAttemptCount } returns 5
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `doWork returns Retry on first failure`() = runBlocking {
        every { metadataSyncWorker.syncNowAsync(any(), any()) } throws RuntimeException("transient")
        every { worker.runAttemptCount } returns 1
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `doWork still syncs when library is populated`() = runBlocking {
        coEvery { metadataSyncWorker.hasMetadata() } returns true
        worker.doWork()
        verify { metadataSyncWorker.syncNowAsync(any(), any()) }
    }

    @Test
    fun `doWork retries when server unreachable`() = runBlocking {
        ReachabilityStateHolder.onApiFailure()
        val result = worker.doWork()
        verify(exactly = 0) { metadataSyncWorker.syncNowAsync(any(), any()) }
        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
