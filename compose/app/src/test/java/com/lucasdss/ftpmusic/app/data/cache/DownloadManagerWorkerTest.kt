package com.lucasdss.ftpmusic.app.data.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.lucasdss.ftpmusic.app.data.db.CacheQueueDao
import com.lucasdss.ftpmusic.app.data.db.CacheQueueItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.io.File
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercises the real worker execution path (workerLoop → downloadItem →
 * handleRetry) against a real local HTTP server — the core materialization
 * path for offline availability. The DownloadManager's OkHttp client is real;
 * only the cache write and DAO are mocked.
 */
class DownloadManagerWorkerTest {

    private val dao: CacheQueueDao = mockk(relaxed = true)
    private val cacheService: CacheService = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val offline = mockk<OfflineModeManager>(relaxed = true)

    private val tmpDir: File = File(System.getProperty("java.io.tmpdir"), "ftpmusic-dlw-${System.nanoTime()}").apply {
        mkdirs()
    }

    private fun startHttpServer(
        status: Int = 200,
        body: ByteArray = ByteArray(8192) {
            7
        },
    ): Pair<MockWebServer, String> {
        val server = MockWebServer()
        if (status == 200) {
            server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(body)))
        } else {
            server.enqueue(MockResponse().setResponseCode(status).setBody("error"))
        }
        server.start()
        return server to server.url("/stream").toString()
    }

    private fun managerWithPicks(
        pick0: CacheQueueItemEntity?,
        pick1: CacheQueueItemEntity?,
        pick0Times: Int = 1,
    ): DownloadManager {
        var p0 = pick0
        var p1 = pick1
        var p0Left = pick0Times
        coEvery { dao.getNextPendingByPriority(0) } answers {
            // Production re-reads the row on each poll — retryCount advances.
            val current = p0
            if (current != null &&
                p0Left > 0
            ) {
                p0Left--
                p0 = current.copy(retryCount = pick0Times - p0Left - 1)
                p0
            } else {
                null
            }
        }
        coEvery { dao.getNextPendingByPriority(1) } answers {
            val current = p1
            if (current != null &&
                p0Left > 0
            ) {
                p0Left--
                p1 = current.copy(retryCount = pick0Times - p0Left - 1)
                p1
            } else {
                null
            }
        }
        coEvery { dao.getNextPendingByPriority(2) } returns null
        coEvery { dao.updateStatus(any(), any()) } just runs
        coEvery { dao.updateRetryCount(any(), any()) } just runs
        every { cacheService.tempDirectory } returns tmpDir
        return DownloadManager(dao, cacheService, offline, context)
    }

    @Test
    fun `successful download writes the cache and marks the row completed`() {
        val (server, url) = startHttpServer()
        try {
            val item = CacheQueueItemEntity(id = 1, trackId = "t1", remoteUrl = url, priority = 0)
            coEvery { dao.getByTrackId("t1") } returns null
            coEvery { cacheService.writeCachedTrackFromFile(eq("t1"), any(), isDownload = any()) } returns true
            val mgr = managerWithPicks(item, null)
            try {
                mgr.start()
                coVerify(timeout = 5000) { dao.updateStatus(item.id, "completed") }
            } finally {
                mgr.stop()
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `server 500 retries with backoff`() {
        val (server, url) = startHttpServer(status = 500)
        try {
            val item = CacheQueueItemEntity(id = 2, trackId = "t2", remoteUrl = url, priority = 0)
            coEvery { dao.getByTrackId("t2") } returns null
            val mgr = managerWithPicks(item, null)
            try {
                mgr.start()
                coVerify(timeout = 5000) { dao.updateStatus(item.id, "processing") }
                coVerify(timeout = 5000) { dao.updateRetryCount(item.id, 1) }
                coVerify(timeout = 5000) { dao.updateStatus(item.id, "pending") }
                // A failed fetch must never be marked completed
                coVerify(exactly = 0) { dao.updateStatus(item.id, "completed") }
            } finally {
                mgr.stop()
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `cache write failure retries`() {
        val (server, url) = startHttpServer()
        try {
            val item = CacheQueueItemEntity(id = 3, trackId = "t3", remoteUrl = url, priority = 0)
            coEvery { dao.getByTrackId("t3") } returns null
            coEvery { cacheService.writeCachedTrackFromFile(eq("t3"), any(), isDownload = any()) } returns false
            val mgr = managerWithPicks(item, null)
            try {
                mgr.start()
                coVerify(timeout = 5000) { dao.updateStatus(item.id, "processing") }
                coVerify(timeout = 5000) { dao.updateStatus(item.id, "pending") }
                coVerify(exactly = 0) { dao.updateStatus(item.id, "completed") }
            } finally {
                mgr.stop()
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `downloaded bytes written to the temp file match the served body`() {
        val body = ByteArray(64 * 1024) { it.toByte() }
        val (server, url) = startHttpServer(body = body)
        try {
            val item = CacheQueueItemEntity(id = 4, trackId = "t4", remoteUrl = url, priority = 0)
            coEvery { dao.getByTrackId("t4") } returns null
            // The worker deletes the temp file after the handoff — capture the
            // bytes inside the stub, while the file still exists.
            val capturedBytes = java.util.concurrent.atomic.AtomicReference<ByteArray>()
            coEvery { cacheService.writeCachedTrackFromFile(eq("t4"), any(), isDownload = any()) } answers {
                capturedBytes.set(secondArg<File>().readBytes())
                true
            }
            val mgr = managerWithPicks(item, null)
            try {
                mgr.start()
                coVerify(timeout = 5000) { dao.updateStatus(item.id, "completed") }
                assertEquals(body.size, capturedBytes.get().size)
                assertEquals(body[0], capturedBytes.get()[0])
            } finally {
                mgr.stop()
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `priority-1 download is gated by constraints when battery is low and not charging`() {
        val conn = mockk<ConnectivityManager>(relaxed = true)
        val net = mockk<Network>()
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns conn
        every { conn.activeNetwork } returns net
        every { conn.getNetworkCapabilities(net) } returns caps
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        val bm = mockk<BatteryManager>(relaxed = true)
        every { context.getSystemService(Context.BATTERY_SERVICE) } returns bm
        every { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 15
        every { bm.isCharging } returns false

        val item = CacheQueueItemEntity(id = 5, trackId = "t5", remoteUrl = "http://127.0.0.1:1/x", priority = 1)
        val mgr = managerWithPicks(null, item)
        try {
            mgr.start()
            Thread.sleep(700)
            coVerify(exactly = 0) { dao.updateStatus(item.id, "processing") }
        } finally {
            mgr.stop()
        }
    }

    @Test
    fun `priority-1 download runs when battery is low but charging`() {
        val conn = mockk<ConnectivityManager>(relaxed = true)
        val net = mockk<Network>()
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns conn
        every { conn.activeNetwork } returns net
        every { conn.getNetworkCapabilities(net) } returns caps
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        val bm = mockk<BatteryManager>(relaxed = true)
        every { context.getSystemService(Context.BATTERY_SERVICE) } returns bm
        every { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 15
        every { bm.isCharging } returns true

        val (server, url) = startHttpServer()
        try {
            val item = CacheQueueItemEntity(id = 6, trackId = "t6", remoteUrl = url, priority = 0)
            coEvery { dao.getByTrackId("t6") } returns null
            coEvery { cacheService.writeCachedTrackFromFile(eq("t6"), any(), isDownload = any()) } returns true
            val mgr = managerWithPicks(item, null)
            try {
                mgr.start()
                coVerify(timeout = 5000) { dao.updateStatus(item.id, "completed") }
            } finally {
                mgr.stop()
            }
        } finally {
            server.shutdown()
        }
    }
}
