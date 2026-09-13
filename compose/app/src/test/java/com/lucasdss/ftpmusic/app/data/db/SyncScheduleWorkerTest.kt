package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import androidx.work.ListenableWorker
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SyncScheduleWorkerTest {

    private lateinit var metadataSyncWorker: MetadataSyncWorker
    private lateinit var worker: SyncScheduleWorker

    @Before
    fun setup() {
        metadataSyncWorker = mockk(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        // Reflect to create WorkerParameters mock
        val workerParams = mockk<androidx.work.WorkerParameters>(relaxed = true)
        worker = spyk(SyncScheduleWorker(context, workerParams, metadataSyncWorker))
    }

    @Test
    fun `doWork calls syncNow on MetadataSyncWorker`() = runBlocking {
        val result = worker.doWork()
        coVerify { metadataSyncWorker.syncNow() }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork returns Success when sync succeeds`() = runBlocking {
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork returns Failure when sync throws after max retries`() = runBlocking {
        coEvery { metadataSyncWorker.syncNow() } throws RuntimeException("network error")
        // Simulate runAttemptCount > 3 (already exceeded)
        every { worker.runAttemptCount } returns 5
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `doWork returns Retry on first failure`() = runBlocking {
        coEvery { metadataSyncWorker.syncNow() } throws RuntimeException("transient")
        every { worker.runAttemptCount } returns 1
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `doWork skips syncNow when metadata already present`() = runBlocking {
        coEvery { metadataSyncWorker.hasMetadata() } returns true

        val result = worker.doWork()

        coVerify(exactly = 0) { metadataSyncWorker.syncNow() }
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork still syncs when library is empty`() = runBlocking {
        coEvery { metadataSyncWorker.hasMetadata() } returns false

        worker.doWork()

        coVerify { metadataSyncWorker.syncNow() }
    }
}
