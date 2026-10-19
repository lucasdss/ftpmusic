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
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.clear()
        coEvery { dao.getByTrackId("tr-3") } returns null
        coEvery { dao.insertIgnore(any()) } returns 1

        manager.enqueue("tr-3", "http://z", priority = 1)

        coVerify {
            dao.insertIgnore(match { it.trackId == "tr-3" && it.priority == 1 && it.isDownload })
        }
        val snap = com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.snapshot(
            context,
            offline = false,
            reachable = true,
        )
        assertTrue(snap.contains("result=inserted"))
        assertTrue(snap.contains("trackId=tr-3"))
        assertFalse(snap.contains("http://z"))
    }

    @Test
    fun `enqueue does not resurrect failed items for mix priority`() = runTest {
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.clear()
        coEvery { dao.getByTrackId("tr-4") } returns CacheQueueItemEntity(
            id = 3,
            trackId = "tr-4",
            remoteUrl = "http://a",
            status = "failed",
            priority = 2,
        )

        manager.enqueue("tr-4", "http://a", priority = 2)

        // Prefetch/mix (pri 0/2): failed stays terminal — no restore thrash.
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
        coVerify(exactly = 0) { dao.updateStatus(3, "pending") }
        val snap = com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.snapshot(
            context,
            offline = false,
            reachable = true,
        )
        assertTrue(snap.contains("result=failed_terminal"))
        assertFalse(snap.contains("http://a"))
        assertTrue(manager.isTerminalFailed("tr-4"))
    }

    @Test
    fun `enqueue resurrects failed item on user download priority`() = runTest {
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.clear()
        coEvery { dao.getByTrackId("tr-retry") } returns CacheQueueItemEntity(
            id = 33,
            trackId = "tr-retry",
            remoteUrl = "http://a",
            status = "failed",
            priority = 2,
            isDownload = false,
            retryCount = 3,
        )

        manager.enqueue("tr-retry", "http://a", priority = 1)

        coVerify { dao.updateStatus(33, "pending") }
        coVerify { dao.updateRetryCount(33, 0) }
        coVerify { dao.updatePriority(33, 1) }
        coVerify { dao.markAsDownload(33) }
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
        val snap = com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.snapshot(
            context,
            offline = false,
            reachable = true,
        )
        assertTrue(snap.contains("result=retry_user"))
    }

    @Test
    fun `enqueue play-queue pri1 does not resurrect failed terminal`() = runTest {
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.clear()
        coEvery { dao.getByTrackId("tr-pq") } returns CacheQueueItemEntity(
            id = 34,
            trackId = "tr-pq",
            remoteUrl = "http://a",
            status = DownloadManager.STATUS_FAILED_CT,
            priority = 1,
            isDownload = true,
            retryCount = 0,
        )

        manager.enqueue("tr-pq", "http://a", priority = 1, isDownload = false)

        coVerify(exactly = 0) { dao.updateStatus(34, "pending") }
        coVerify(exactly = 0) { dao.markAsDownload(any()) }
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
        val snap = com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.snapshot(
            context,
            offline = false,
            reachable = true,
        )
        assertTrue(snap.contains("result=failed_terminal"))
        assertTrue(manager.isTerminalFailed("tr-pq"))
    }

    @Test
    fun `enqueue play-queue requeues retry-exhausted failed`() = runTest {
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.clear()
        coEvery { dao.getByTrackId("tr-tq") } returns CacheQueueItemEntity(
            id = 35,
            trackId = "tr-tq",
            remoteUrl = "http://a",
            status = DownloadManager.STATUS_FAILED,
            priority = 1,
            isDownload = false,
            retryCount = DownloadManager.MAX_RETRIES,
        )

        manager.enqueue("tr-tq", "http://a", priority = 1, isDownload = false)

        coVerify { dao.updateStatus(35, "pending") }
        coVerify { dao.updateRetryCount(35, 0) }
        coVerify(exactly = 0) { dao.markAsDownload(any()) }
        val snap = com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.snapshot(
            context,
            offline = false,
            reachable = true,
        )
        assertTrue(snap.contains("result=retry_transient"))
        assertFalse(manager.isTerminalFailed("tr-tq"))
    }

    @Test
    fun `enqueue play-queue keeps legacy failed with low retry sticky`() = runTest {
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.clear()
        coEvery { dao.getByTrackId("tr-leg") } returns CacheQueueItemEntity(
            id = 36,
            trackId = "tr-leg",
            remoteUrl = "http://a",
            status = DownloadManager.STATUS_FAILED,
            priority = 1,
            isDownload = false,
            retryCount = 0,
        )

        manager.enqueue("tr-leg", "http://a", priority = 1, isDownload = false)

        coVerify(exactly = 0) { dao.updateStatus(36, "pending") }
        assertTrue(manager.isTerminalFailed("tr-leg"))
    }

    @Test
    fun `enqueue play-queue remainder insert does not set isDownload`() = runTest {
        coEvery { dao.getByTrackId("tr-rem") } returns null
        coEvery { dao.insertIgnore(any()) } returns 1

        manager.enqueue("tr-rem", "http://r", priority = 1, isDownload = false)

        coVerify {
            dao.insertIgnore(
                match { it.trackId == "tr-rem" && it.priority == 1 && !it.isDownload },
            )
        }
    }

    @Test
    fun `summarizeSubsonicErrorText extracts code and message without paths`() {
        val (code, msg) = manager.summarizeSubsonicErrorText(
            """{"subsonic-response":{"status":"failed","error":{"code":70,"message":"Song not found"}}}""",
        )
        assertEquals("70", code)
        assertEquals("Song not found", msg)
        val (c2, m2) = manager.summarizeSubsonicErrorText(
            """{"error":{"code":40,"message":"Wrong username or password for http://evil.example/x"}}""",
        )
        assertEquals("40", c2)
        assertTrue(m2.contains("<url>"))
        assertFalse(m2.contains("evil.example"))
    }

    @Test
    fun `isTerminalFailed false when row missing or pending`() = runTest {
        coEvery { dao.getByTrackId("gone") } returns null
        coEvery { dao.getByTrackId("pend") } returns CacheQueueItemEntity(
            id = 9,
            trackId = "pend",
            remoteUrl = "http://x",
            status = "pending",
            priority = 2,
        )
        assertFalse(manager.isTerminalFailed("gone"))
        assertFalse(manager.isTerminalFailed("pend"))
    }

    @Test
    fun `enqueue reuses completed item by resetting status`() = runTest {
        coEvery { dao.getByTrackId("tr-5") } returns CacheQueueItemEntity(
            id = 4,
            trackId = "tr-5",
            remoteUrl = "http://b",
            status = "completed",
            priority = 2,
            isDownload = false,
        )
        coEvery { cacheService.isPlayableCached("tr-5") } returns false
        coEvery { cacheService.promoteToDownload("tr-5") } returns false

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
        coEvery { cacheService.isPlayableCached("tr-7") } returns true
        coEvery { cacheService.promoteToDownload("tr-7") } returns true

        manager.enqueue("tr-7", "http://d", priority = 1)

        coVerify { cacheService.promoteToDownload("tr-7") }
        coVerify { dao.markAsDownload(6) }
        coVerify(exactly = 0) { dao.updateStatus(any(), "pending") }
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
    }

    @Test
    fun `enqueue download for already completed download is idempotent`() = runTest {
        coEvery { dao.getByTrackId("tr-7b") } returns CacheQueueItemEntity(
            id = 66,
            trackId = "tr-7b",
            remoteUrl = "http://d",
            status = "completed",
            priority = 1,
            isDownload = true,
        )
        coEvery { cacheService.isPlayableCached("tr-7b") } returns true

        manager.enqueue("tr-7b", "http://d", priority = 1)

        coVerify(exactly = 0) { cacheService.promoteToDownload(any()) }
        coVerify(exactly = 0) { dao.markAsDownload(any()) }
        coVerify(exactly = 0) { dao.updateStatus(any(), "pending") }
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
    fun `worker skips priority-0 on cellular LOCAL_ONLY even if wifiOrEthernet stale true`() {
        val offline = mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true)
        every { offline.isOfflineEnabled() } returns false
        every { offline.isQueueEnabled() } returns false
        val cm = mockk<android.net.ConnectivityManager>(relaxed = true)
        val net = mockk<android.net.Network>(relaxed = true)
        val caps = mockk<android.net.NetworkCapabilities>(relaxed = true)
        every { context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) } returns cm
        every { cm.activeNetwork } returns net
        every { cm.getNetworkCapabilities(net) } returns caps
        every { caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        NetworkPolicyState.resetForTests(
            policy = CellularMediaPolicy.LOCAL_ONLY,
            wifiOrEthernet = true,
        )
        com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder.resetForTests(true)
        val mgr = DownloadManager(dao, cacheService, offline, context)
        every { cacheService.tempDirectory } returns File(System.getProperty("java.io.tmpdir"))
        coEvery { dao.getNextPendingByPriority(0) } returns CacheQueueItemEntity(
            id = 97,
            trackId = "cell-urgent",
            remoteUrl = "http://x",
            priority = 0,
        )

        try {
            mgr.start()
            Thread.sleep(700)
        } finally {
            mgr.stop()
            NetworkPolicyState.resetForTests()
        }

        coVerify(exactly = 0) { dao.updateStatus(any(), "processing") }
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
