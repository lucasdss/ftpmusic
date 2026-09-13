package com.lucasdss.ftpmusic.app.data.cache

import com.lucasdss.ftpmusic.app.data.db.CacheQueueDao
import com.lucasdss.ftpmusic.app.data.db.CacheQueueItemEntity
import io.mockk.*
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DownloadManagerTest {

    private val dao: CacheQueueDao = mockk(relaxed = true)
    private val cacheService: CacheService = mockk(relaxed = true)
    private val context: android.content.Context = mockk(relaxed = true)
    private val manager =
        DownloadManager(
            dao,
            cacheService,
            mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
            context,
        )

    @Test
    fun `enqueue skips duplicate pending items`() = runTest {
        coEvery { dao.getByTrackId("tr-1") } returns CacheQueueItemEntity(
            id = 1,
            trackId = "tr-1",
            remoteUrl = "http://x",
            status = "pending",
        )

        manager.enqueue("tr-1", "http://x")

        // Should NOT insert duplicate
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
    }

    @Test
    fun `enqueue skips duplicate processing items`() = runTest {
        coEvery { dao.getByTrackId("tr-2") } returns CacheQueueItemEntity(
            id = 2,
            trackId = "tr-2",
            remoteUrl = "http://y",
            status = "processing",
        )

        manager.enqueue("tr-2", "http://y")

        coVerify(exactly = 0) { dao.insertIgnore(any()) }
    }

    @Test
    fun `enqueue inserts when no existing item`() = runTest {
        coEvery { dao.getByTrackId("tr-3") } returns null
        coEvery { dao.insertIgnore(any()) } returns 1

        manager.enqueue("tr-3", "http://z", priority = 1)

        coVerify { dao.insertIgnore(match { it.trackId == "tr-3" && it.priority == 1 }) }
    }

    @Test
    fun `enqueue does not resurrect failed items`() = runTest {
        coEvery { dao.getByTrackId("tr-4") } returns CacheQueueItemEntity(
            id = 3,
            trackId = "tr-4",
            remoteUrl = "http://a",
            status = "failed",
            priority = 2,
        )

        manager.enqueue("tr-4", "http://a")

        // "failed" is terminal — resurrecting it to pending would create an
        // infinite download-retry loop (every re-enqueue re-attempts).
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
        coVerify(exactly = 0) { dao.updateStatus(3, "pending") }
    }

    @Test
    fun `enqueue reuses completed item by resetting status`() = runTest {
        coEvery { dao.getByTrackId("tr-5") } returns CacheQueueItemEntity(
            id = 4,
            trackId = "tr-5",
            remoteUrl = "http://b",
            status = "completed",
            priority = 2,
        )

        manager.enqueue("tr-5", "http://b", priority = 1)

        coVerify(exactly = 0) { dao.insertIgnore(any()) }
        coVerify { dao.updateStatus(4, "pending") }
        coVerify { dao.updatePriority(4, 1) }
    }

    @Test
    fun `resetProcessingToPending resets stuck items`() = runTest {
        coEvery { dao.resetProcessingToPending() } just Runs

        dao.resetProcessingToPending()

        coVerify { dao.resetProcessingToPending() }
    }

    @Test
    fun `allowMobileData flag defaults to true`() {
        assertTrue(DownloadManager.allowMobileData)
    }

    @Test
    fun `allowMobileData can be toggled`() {
        DownloadManager.allowMobileData = false
        assertFalse(DownloadManager.allowMobileData)
        DownloadManager.allowMobileData = true // restore
        assertTrue(DownloadManager.allowMobileData)
    }

    @Test
    fun `enqueue download upgrades isDownload flag on pending play-queue item`() = runTest {
        coEvery { dao.getByTrackId("tr-6") } returns CacheQueueItemEntity(
            id = 5,
            trackId = "tr-6",
            remoteUrl = "http://c",
            status = "pending",
            priority = 0,
            isDownload = false,
        )

        manager.enqueue("tr-6", "http://c", priority = 1)

        coVerify { dao.markAsDownload(5) }
        // Priority 0 already outranks 1 — must NOT be downgraded
        coVerify(exactly = 0) { dao.updatePriority(any(), any()) }
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
    }

    @Test
    fun `enqueue download for completed auto-cache item promotes without re-download`() = runTest {
        coEvery { dao.getByTrackId("tr-7") } returns CacheQueueItemEntity(
            id = 6,
            trackId = "tr-7",
            remoteUrl = "http://d",
            status = "completed",
            priority = 2,
            isDownload = false,
        )
        coEvery { cacheService.promoteToDownload("tr-7") } returns true

        manager.enqueue("tr-7", "http://d", priority = 1)

        coVerify { cacheService.promoteToDownload("tr-7") }
        coVerify { dao.markAsDownload(6) }
        coVerify(exactly = 0) { dao.updateStatus(any(), "pending") }
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
    }

    @Test
    fun `enqueue download re-enqueues completed item when promote fails`() = runTest {
        coEvery { dao.getByTrackId("tr-8") } returns CacheQueueItemEntity(
            id = 7,
            trackId = "tr-8",
            remoteUrl = "http://e",
            status = "completed",
            priority = 2,
            isDownload = false,
        )
        coEvery { cacheService.promoteToDownload("tr-8") } returns false // content evicted

        manager.enqueue("tr-8", "http://e", priority = 1)

        coVerify { dao.updateStatus(7, "pending") }
        coVerify { dao.updatePriority(7, 1) }
        coVerify { dao.markAsDownload(7) }
    }

    @Test
    fun `enqueue inserts new download with isDownload flag`() = runTest {
        coEvery { dao.getByTrackId("tr-9") } returns null
        coEvery { dao.insertIgnore(any()) } returns 1

        manager.enqueue("tr-9", "http://f", priority = 1)

        coVerify { dao.insertIgnore(match { it.trackId == "tr-9" && it.priority == 1 && it.isDownload }) }
    }

    @Test
    fun `start after stop recreates cancelled scope`() {
        coEvery { dao.getNextPendingByPriority(any()) } returns null
        val mgr =
            DownloadManager(
                dao,
                cacheService,
                mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
                context,
            )
        try {
            mgr.start()
            coVerify(timeout = 2000) { dao.resetProcessingToPending() }
            mgr.stop()
            clearMocks(dao, answers = false)

            mgr.start() // must not launch on the dead, cancelled scope

            coVerify(timeout = 2000) { dao.resetProcessingToPending() }
        } finally {
            mgr.stop()
        }
    }

    @Test
    fun `start sweeps stale tmp files older than one hour`() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "ftpmusic-dl-tmp-${System.nanoTime()}").apply {
            mkdirs()
        }
        val mgr =
            DownloadManager(
                dao,
                cacheService,
                mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
                context,
            )
        try {
            val stale = File(tmpDir, "stale.tmp").apply { writeText("x") }
            stale.setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000L)
            val fresh = File(tmpDir, "fresh.tmp").apply { writeText("y") }
            every { cacheService.tempDirectory } returns tmpDir
            coEvery { dao.getNextPendingByPriority(any()) } returns null

            mgr.start()

            val deadline = System.currentTimeMillis() + 3000
            while (stale.exists() && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertFalse(stale.exists())
            assertTrue(fresh.exists()) // recent temp files untouched
        } finally {
            mgr.stop()
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `worker skips even urgent priority-0 items while offline mode is on`() {
        val offline = mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true)
        every { offline.isOfflineEnabled() } returns true
        val mgr = DownloadManager(dao, cacheService, offline, context)
        every { cacheService.tempDirectory } returns File(System.getProperty("java.io.tmpdir"))
        coEvery { dao.getNextPendingByPriority(0) } returns CacheQueueItemEntity(
            id = 99,
            trackId = "urgent",
            remoteUrl = "http://x",
            priority = 0,
        )

        try {
            mgr.start()
            // Give the worker loop a chance to (incorrectly) pick the item
            Thread.sleep(700)
        } finally {
            mgr.stop()
        }

        coVerify(exactly = 0) { dao.updateStatus(any(), "processing") }
        coVerify(exactly = 0) { dao.updateStatus(any(), any()) }
    }

    @Test
    fun `worker processes urgent priority-0 items when online`() {
        val offline = mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true)
        every { offline.isOfflineEnabled() } returns false
        val mgr = DownloadManager(dao, cacheService, offline, context)
        val item = CacheQueueItemEntity(id = 98, trackId = "urgent", remoteUrl = "http://x", priority = 0)
        every { cacheService.tempDirectory } returns File(System.getProperty("java.io.tmpdir"))
        // Pick the item once, then idle — avoids respawning download coroutines
        // (each would do a real DNS call) across the rest of the test.
        var pick = true
        coEvery { dao.getNextPendingByPriority(0) } answers {
            if (pick) {
                pick = false
                item
            } else {
                null
            }
        }
        coEvery { dao.updateStatus(any(), any()) } just Runs

        try {
            mgr.start()
            coVerify(timeout = 2000) { dao.updateStatus(item.id, "processing") }
        } finally {
            mgr.stop()
        }
    }
}
