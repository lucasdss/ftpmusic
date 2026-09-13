package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.cache.CacheService
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Socket-level tests for the local proxy (Cast tier 3): binds the real Netty
 * server on an ephemeral port and issues real HTTP requests. Validates the
 * F14 Range rewrite end-to-end (bounded ranges, suffix ranges, 416, 404) —
 * the previous implementation over-served bounded ranges with a lying
 * Content-Range header.
 */
class PlaybackProxySocketTest {

    private lateinit var proxy: PlaybackProxy
    private lateinit var cacheService: CacheService
    private lateinit var audioFile: File

    @Before
    fun setup() {
        cacheService = mockk(relaxed = true)
        audioFile = File.createTempFile("proxy-test", ".mp3").apply {
            writeBytes(ByteArray(1000) { it.toByte() })
        }
        proxy = PlaybackProxy(cacheService)
        proxy.start(0) // port 0 → OS-assigned ephemeral
    }

    @After
    fun teardown() {
        proxy.stop()
        audioFile.delete()
    }

    private fun baseUrl(): String {
        val addr = proxy.channelAddress() ?: error("proxy not bound")
        return "http://127.0.0.1:${addr.port}"
    }

    private fun get(path: String, range: String? = null): Triple<Int, Map<String, String?>, ByteArray> {
        val conn = URL(baseUrl() + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        if (range != null) conn.setRequestProperty("Range", range)
        val code = conn.responseCode
        // getHeaderField is case-insensitive and skips consumed headers;
        // Content-Length comes from getContentLength.
        val headers = mapOf(
            "Content-Length" to (if (conn.contentLength >= 0) conn.contentLength.toString() else null),
            "Content-Range" to conn.getHeaderField("Content-Range"),
            "Content-Type" to conn.getHeaderField("Content-Type"),
        )
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.readBytes() ?: ByteArray(0)
        conn.disconnect()
        return Triple(code, headers, body)
    }

    @Test
    fun `open-ended range returns 206 with full remainder`() {
        coEvery { cacheService.getCachedPath("t1") } returns audioFile.absolutePath
        coEvery { cacheService.getTrackEntity("t1") } returns null

        val (code, headers, body) = get("/stream?id=t1", "bytes=100-")

        assertEquals(206, code)
        assertEquals("900", headers["Content-Length"])
        assertEquals("bytes 100-999/1000", headers["Content-Range"])
        assertEquals(900, body.size)
        assertEquals(100.toByte(), body[0])
    }

    @Test
    fun `bounded range returns exactly the requested window`() {
        coEvery { cacheService.getCachedPath("t1") } returns audioFile.absolutePath
        coEvery { cacheService.getTrackEntity("t1") } returns null

        val (code, headers, body) = get("/stream?id=t1", "bytes=200-249")

        assertEquals(206, code)
        assertEquals("50", headers["Content-Length"])
        assertEquals("bytes 200-249/1000", headers["Content-Range"])
        assertEquals(50, body.size)
        assertEquals(200.toByte(), body[0])
        assertEquals(249.toByte(), body[49])
    }

    @Test
    fun `suffix range returns the last N bytes`() {
        coEvery { cacheService.getCachedPath("t1") } returns audioFile.absolutePath
        coEvery { cacheService.getTrackEntity("t1") } returns null

        val (code, headers, body) = get("/stream?id=t1", "bytes=-64")

        assertEquals(206, code)
        assertEquals("64", headers["Content-Length"])
        assertEquals("bytes 936-999/1000", headers["Content-Range"])
        assertEquals(64, body.size)
        assertEquals(936.toByte(), body[0])
    }

    @Test
    fun `full file without range returns 200`() {
        coEvery { cacheService.getCachedPath("t1") } returns audioFile.absolutePath
        coEvery { cacheService.getTrackEntity("t1") } returns null

        val (code, headers, body) = get("/stream?id=t1")

        assertEquals(200, code)
        assertEquals("1000", headers["Content-Length"])
        assertEquals(1000, body.size)
    }

    @Test
    fun `range beyond EOF returns 416 with content-range asterisk`() {
        coEvery { cacheService.getCachedPath("t1") } returns audioFile.absolutePath

        val (code, headers, _) = get("/stream?id=t1", "bytes=5000-")

        assertEquals(416, code)
        assertEquals("bytes */1000", headers["Content-Range"])
    }

    @Test
    fun `garbage range returns 416`() {
        coEvery { cacheService.getCachedPath("t1") } returns audioFile.absolutePath

        val (code, _, _) = get("/stream?id=t1", "bytes=abc-def")

        assertEquals(416, code)
    }

    @Test
    fun `cache miss returns 404 for the Cast tier-1 fallback`() {
        coEvery { cacheService.getCachedPath("missing") } returns null

        val (code, _, _) = get("/stream?id=missing")

        assertEquals(404, code)
    }

    @Test
    fun `missing track id returns 400`() {
        val (code, _, _) = get("/stream?noid=1")
        assertEquals(400, code)
    }

    @Test
    fun `restart after stop rebinds and serves again`() {
        coEvery { cacheService.getCachedPath("t1") } returns audioFile.absolutePath
        proxy.stop()
        assertTrue(!proxy.isReady)
        proxy.start(0)
        assertTrue(proxy.isReady)

        val (code, _, body) = get("/stream?id=t1")
        assertEquals(200, code)
        assertEquals(1000, body.size)
    }

    // ── Content-type resolution + lifecycle helpers ───────────────────────

    @Test
    fun `content type comes from room metadata when available`() = runTest {
        val audio = File.createTempFile("ctype", ".bin").apply {
            writeBytes(
                ByteArray(4) { 0x49.toByte() }.let {
                    ByteArray(4) {
                        0x49.toByte()
                    } + ByteArray(8)
                },
            )
        } // ID3-ish
        coEvery { cacheService.getCachedPath("ct1") } returns audio.absolutePath
        coEvery { cacheService.getTrackEntity("ct1") } returns
            com.lucasdss.ftpmusic.app.data.db.TrackEntity(id = "ct1", title = "T", contentType = "audio/flac")
        audio.deleteOnExit()

        val (code, headers, _) = get("/stream?id=ct1")
        assertEquals(200, code)
        assertEquals("audio/flac", headers["Content-Type"])
        audio.delete()
    }

    @Test
    fun `magic byte sniffing detects flac when metadata is absent`() = runTest {
        val audio = File.createTempFile("magic", ".bin")
        audio.writeBytes(byteArrayOf(0x66, 0x4C, 0x61, 0x43) + ByteArray(8))
        coEvery { cacheService.getCachedPath("magic1") } returns audio.absolutePath
        coEvery { cacheService.getTrackEntity("magic1") } returns null
        audio.deleteOnExit()

        val (code, headers, _) = get("/stream?id=magic1")
        assertEquals(200, code)
        assertEquals("audio/flac", headers["Content-Type"])
        audio.delete()
    }

    @Test
    fun `unknown file falls back to audio mpeg`() = runTest {
        val audio = File.createTempFile("unk", ".bin")
        audio.writeBytes(ByteArray(16) { 0x0 })
        coEvery { cacheService.getCachedPath("unk1") } returns audio.absolutePath
        coEvery { cacheService.getTrackEntity("unk1") } returns null
        audio.deleteOnExit()

        val (code, headers, _) = get("/stream?id=unk1")
        assertEquals(200, code)
        assertEquals("audio/mpeg", headers["Content-Type"])
        audio.delete()
    }

    @Test
    fun `forceRestart rebinds and serves`() {
        coEvery { cacheService.getCachedPath("t1") } returns audioFile.absolutePath
        proxy.forceRestart()
        assertTrue(proxy.isReady)
        val (code, _, _) = get("/stream?id=t1")
        assertEquals(200, code)
    }

    @Test
    fun `restartIfNeeded no-ops while active`() {
        assertTrue(proxy.isReady)
        proxy.restartIfNeeded()
        assertTrue(proxy.isReady)
    }

    @Test
    fun `restartIfNeeded rebinds after stop`() {
        proxy.stop()
        assertTrue(!proxy.isReady)
        proxy.restartIfNeeded()
        assertTrue(proxy.isReady)
    }

    @Test
    fun `empty range marker returns 416`() {
        coEvery { cacheService.getCachedPath("t1") } returns audioFile.absolutePath
        val (code, _, _) = get("/stream?id=t1", "bytes=-")
        assertEquals(416, code)
    }
}
