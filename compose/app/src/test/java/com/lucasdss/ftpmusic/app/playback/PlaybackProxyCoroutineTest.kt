package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.cache.CacheService
import io.mockk.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Verifies PlaybackProxy cache check doesn't use runBlocking on the Netty event loop.
 * Instead, it must use CoroutineScope(Dispatchers.IO).launch so the event loop stays responsive.
 *
 * Detection strategy: if runBlocking is used, getCachedPath executes on the Netty event loop
 * thread. With Dispatchers.IO, it runs on a DefaultDispatcher-worker thread.
 */
class PlaybackProxyCoroutineTest {

    private lateinit var cacheService: CacheService
    private lateinit var tmpDir: File

    @Before
    fun setup() {
        tmpDir = File(System.getProperty("java.io.tmpdir"), "proxy-coroutine-test-${System.nanoTime()}")
        tmpDir.mkdirs()
        cacheService = mockk(relaxed = true)
        every { cacheService.cacheDirectory } returns tmpDir
    }

    @After
    fun teardown() {
        tmpDir.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun `cache check dispatches to IO thread, not event loop thread`() {
        // Given: record the thread that getCachedPath executes on
        val getCachedPathThreads = ConcurrentLinkedQueue<String>()

        val testFile = File(tmpDir, "track-1.cache")
        testFile.writeBytes("cached audio bytes".toByteArray())

        coEvery { cacheService.getCachedPath("track-1") } answers {
            getCachedPathThreads.add(Thread.currentThread().name)
            testFile.absolutePath
        }

        // When: proxy starts and handles a request for the cached track
        val proxy = PlaybackProxy(cacheService)
        proxy.start(0) // random port

        try {
            // Wait for proxy to be ready
            var attempts = 0
            while (!proxy.isReady && attempts < 50) {
                Thread.sleep(100)
                attempts++
            }
            assertTrue("Proxy should have started within 5s", proxy.isReady)

            // Discover the actual bound port via reflection
            val channelField = PlaybackProxy::class.java.getDeclaredField("channel")
            channelField.isAccessible = true
            val channel = channelField.get(proxy) as? io.netty.channel.Channel
            val port = (channel?.localAddress() as? java.net.InetSocketAddress)?.port
                ?: throw AssertionError("Could not determine proxy port")

            // Send HTTP GET for the cached track
            val url = URL(
                "http://127.0.0.1:$port/stream" +
                    "?id=track-1" +
                    "&url=http%3A%2F%2Fexample.com%2Fstream.mp3",
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            val responseBody = connection.inputStream.readBytes()

            // Then: response is 200 OK with the cached content
            assertEquals("HTTP status", 200, responseCode)
            assertArrayEquals("Response body should match cached file", testFile.readBytes(), responseBody)

            // Then: getCachedPath must NOT have run on a Netty event loop thread
            val threadName = getCachedPathThreads.poll()
            assertNotNull("getCachedPath should have been called", threadName)
            assertFalse(
                "getCachedPath ran on Netty event loop thread '$threadName' — " +
                    "runBlocking is still in use. Expected IO dispatcher thread.",
                threadName!!.contains("nioEventLoop", ignoreCase = true) ||
                    (threadName.contains("main") && !threadName.contains("DefaultDispatcher")),
            )

            // Verify cacheService.getCachedPath was called for the correct track
            coVerify(exactly = 1) { cacheService.getCachedPath("track-1") }
        } finally {
            proxy.stop()
        }
    }
}
