package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.cache.CacheService
import io.mockk.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Verifies slimmed-down proxy behavior for Cast tier 3:
 * - Cache hit → serves file (200)
 * - Cache miss → returns 404
 * - No remote fetch executor
 */
class PlaybackProxyThreadPoolTest {

    private lateinit var cacheService: CacheService
    private lateinit var tmpDir: File

    @Before
    fun setup() {
        tmpDir = File(System.getProperty("java.io.tmpdir"), "proxy-tp-test-${System.nanoTime()}")
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
    fun `cache miss returns 404 instead of remote fetch`() {
        // Given: cacheService returns null (cache miss)
        coEvery { cacheService.getCachedPath(any()) } returns null

        val proxy = PlaybackProxy(cacheService)
        proxy.start(0)

        try {
            // Wait for proxy readiness
            var attempts = 0
            while (!proxy.isReady && attempts < 50) {
                Thread.sleep(100)
                attempts++
            }
            assertTrue("Proxy should have started", proxy.isReady)

            val channelField = PlaybackProxy::class.java.getDeclaredField("channel")
            channelField.isAccessible = true
            val channel = channelField.get(proxy) as? io.netty.channel.Channel
            val port = (channel?.localAddress() as? java.net.InetSocketAddress)?.port
                ?: throw AssertionError("Could not determine proxy port")

            // When: send a request that will be a cache miss
            val url = URL(
                "http://127.0.0.1:$port/stream" +
                    "?id=miss-1",
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            // Then: should get 404 for cache miss
            val code = conn.responseCode
            assertEquals("Cache miss should return 404", 404, code)
        } finally {
            proxy.stop()
        }
    }

    @Test
    fun `cache hit serves file`() {
        // Given: cacheService returns a valid path
        val testFile = File(tmpDir, "cached-track.mp3")
        testFile.writeBytes(ByteArray(1024) { 0x42.toByte() }) // 1KB of dummy data
        coEvery { cacheService.getCachedPath("hit-1") } returns testFile.absolutePath

        val proxy = PlaybackProxy(cacheService)
        proxy.start(0)

        try {
            var attempts = 0
            while (!proxy.isReady && attempts < 50) {
                Thread.sleep(100)
                attempts++
            }
            assertTrue("Proxy should have started", proxy.isReady)

            val channelField = PlaybackProxy::class.java.getDeclaredField("channel")
            channelField.isAccessible = true
            val channel = channelField.get(proxy) as? io.netty.channel.Channel
            val port = (channel?.localAddress() as? java.net.InetSocketAddress)?.port
                ?: throw AssertionError("Could not determine proxy port")

            // When: send a cache hit request
            val url = URL("http://127.0.0.1:$port/stream?id=hit-1")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            // Then: should get 200
            val code = conn.responseCode
            assertEquals("Cache hit should return 200", 200, code)
            val body = conn.inputStream.readBytes()
            assertEquals("Should serve 1KB file", 1024, body.size)
        } finally {
            proxy.stop()
        }
    }
}
