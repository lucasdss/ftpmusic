package com.lucasdss.ftpmusic.app.data.db

import androidx.work.ListenableWorker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Branch coverage extensions for [SyncScheduleWorker] (started-sync success
 * path) and [LyricsCacheEntity] (plain data-class construction).
 */
class SyncScheduleWorkerBranchTest {

    private lateinit var metadataSyncWorker: MetadataSyncWorker
    private lateinit var worker: SyncScheduleWorker

    @Before
    fun setup() {
        metadataSyncWorker = mockk(relaxed = true)
        val context = mockk<android.content.Context>(relaxed = true)
        val workerParams = mockk<androidx.work.WorkerParameters>(relaxed = true)
        worker = spyk(SyncScheduleWorker(context, workerParams, metadataSyncWorker))
    }

    @Test
    fun `doWork returns Success when sync started`() = runBlocking {
        coEvery { metadataSyncWorker.syncNow() } returns true
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `doWork returns Failure when sync throws after max retries`() = runBlocking {
        coEvery { metadataSyncWorker.syncNow() } throws RuntimeException("network error")
        every { worker.runAttemptCount } returns 5
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `doWork returns Retry on early failure`() = runBlocking {
        coEvery { metadataSyncWorker.syncNow() } throws RuntimeException("transient")
        every { worker.runAttemptCount } returns 1
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    // ── LyricsCacheEntity ───────────────────────────────────────────────────

    @Test
    fun `lyricsCacheEntity exposes fields and defaults`() {
        val entity = LyricsCacheEntity(
            trackId = "t1",
            artist = "A",
            title = "T",
            rawJson = "{}",
            syncedLinesJson = "[]",
            unstructuredText = null,
            cacheVersion = 2,
        )
        assertEquals("t1", entity.trackId)
        assertEquals("A", entity.artist)
        assertEquals("{}", entity.rawJson)
        assertEquals("[]", entity.syncedLinesJson)
        assertEquals(2, entity.cacheVersion)
        assertEquals(LyricsCacheEntity.CURRENT_CACHE_VERSION, LyricsCacheEntity.CURRENT_CACHE_VERSION)

        val defaults = LyricsCacheEntity(trackId = "t2", artist = null, title = null)
        assertEquals(2, defaults.cacheVersion)
        assertTrue(defaults.fetchedAt > 0)
    }
}
