package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import androidx.work.ListenableWorker
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SyncScheduleWorkerTest {

    private lateinit var metadataSyncWorker: MetadataSyncWorker
    private lateinit var worker: SyncScheduleWorker

    @Before
    fun setup() {
        ReachabilityStateHolder.onApiSuccess()
        metadataSyncWorker = mockk(relaxed = true)
        every { metadataSyncWorker.lastFullSyncMs() } returns System.currentTimeMillis() // recent → DELTA
        every { metadataSyncWorker.syncNow(any(), any()) } returns true
        val context = mockk<Context>(relaxed = true)
        val workerParams = mockk<androidx.work.WorkerParameters>(relaxed = true)
        worker = spyk(SyncScheduleWorker(context, workerParams, metadataSyncWorker))
    }

    @After
    fun tearDown() {
        ReachabilityStateHolder.onApiSuccess()
    }

    @Test
    fun `doWork calls syncNow DELTA when last full sync recent`() = runBlocking {
        val recent = System.currentTimeMillis()
        every { metadataSyncWorker.lastFullSyncMs() } returns recent
        val result = worker.doWork()
        verify {
            metadataSyncWorker.syncNow(
                forceTrackResync = false,
                mode = LibrarySyncMode.DELTA,
            )
        }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork calls syncNow FULL when last full sync never`() = runBlocking {
        every { metadataSyncWorker.lastFullSyncMs() } returns 0L
        worker.doWork()
        verify {
            metadataSyncWorker.syncNow(
                forceTrackResync = false,
                mode = LibrarySyncMode.FULL,
            )
        }
    }

    @Test
    fun `doWork calls syncNow FULL when last full sync older than 7 days`() = runBlocking {
        val eightDaysAgo = System.currentTimeMillis() - MetadataSyncWorker.FULL_SYNC_INTERVAL_MS - 1000
        every { metadataSyncWorker.lastFullSyncMs() } returns eightDaysAgo
        worker.doWork()
        verify {
            metadataSyncWorker.syncNow(
                forceTrackResync = false,
                mode = LibrarySyncMode.FULL,
            )
        }
    }

    @Test
    fun `doWork returns Success when sync succeeds`() = runBlocking {
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork returns Failure when sync throws after max retries`() = runBlocking {
        every { metadataSyncWorker.syncNow(any(), any()) } throws RuntimeException("network error")
        every { worker.runAttemptCount } returns 5
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `doWork returns Retry on first failure`() = runBlocking {
        every { metadataSyncWorker.syncNow(any(), any()) } throws RuntimeException("transient")
        every { worker.runAttemptCount } returns 1
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `doWork still syncs when library is populated`() = runBlocking {
        coEvery { metadataSyncWorker.hasMetadata() } returns true
        worker.doWork()
        verify { metadataSyncWorker.syncNow(any(), any()) }
    }

    @Test
    fun `doWork retries when server unreachable`() = runBlocking {
        ReachabilityStateHolder.onApiFailure()
        val result = worker.doWork()
        verify(exactly = 0) { metadataSyncWorker.syncNow(any(), any()) }
        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
