package com.lucasdss.ftpmusic.app.data.cache

import android.content.Context
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Plain-JVM branch coverage for [CoverArtFallbackService] with a mock-injected
 * OkHttpClient (no real network): in-memory/disk cache hits, all-services-fail
 * flow completion, image-size heuristics, Navidrome URL building, singleton
 * access and legacy-file early return. org.json is a stub on the plain JVM, so
 * the iTunes/MusicBrainz JSON parse success paths are not exercised here.
 */
class CoverArtFallbackServiceBranchTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private lateinit var cacheDir: File
    private lateinit var service: CoverArtFallbackService

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("covers")
        every { context.applicationContext } returns context
        every { context.cacheDir } returns tempFolder.root
        service = CoverArtFallbackService(context)
    }

    @After
    fun tearDown() {
        unmockkObject(SubsonicCredentials)
        unmockkObject(DynamicBaseUrl)
        com.lucasdss.ftpmusic.app.di.ServerConfigState.value = com.lucasdss.ftpmusic.app.di.ServerConfig()
    }

    /** Replace the private OkHttpClient so no real network is touched. */
    private fun injectMockClient(target: CoverArtFallbackService = service): OkHttpClient =
        injectMockClientWithBody(target, """{"results":[]}""")

    private fun injectMockClientWithBody(target: CoverArtFallbackService = service, json: String): OkHttpClient {
        val client = mockk<OkHttpClient>()
        val call = mockk<okhttp3.Call>(relaxed = true)
        val response = mockk<okhttp3.Response>(relaxed = true)
        val body = mockk<okhttp3.ResponseBody>(relaxed = true)
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response
        every { body.string() } returns json
        every { response.body } returns body
        val field = CoverArtFallbackService::class.java.getDeclaredField("client")
        field.isAccessible = true
        field.set(target, client)
        return client
    }

    /** Tiny loopback HTTP server (raw socket — no jdk.httpserver on Android's test classpath). */
    private fun startImageServer(): Pair<java.net.ServerSocket, Int> {
        val socket = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        Thread {
            try {
                while (!socket.isClosed) {
                    val client = socket.accept()
                    val body = JPEG_MAGIC
                    val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: image/jpeg\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    client.getOutputStream().use { out ->
                        out.write(header.toByteArray())
                        out.write(body)
                    }
                    client.close()
                }
            } catch (_: Exception) {
                // socket closed
            }
        }.apply { isDaemon = true }.start()
        return socket to socket.localPort
    }

    private fun urlCacheOf(target: CoverArtFallbackService): MutableMap<String, String> {
        val field = CoverArtFallbackService::class.java.getDeclaredField("urlCache")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(target) as MutableMap<String, String>
    }

    /** Write bytes that pass the image magic-byte check on the plain JVM. */
    private fun writeFakeImage(file: File) {
        file.parentFile?.mkdirs()
        file.writeBytes(JPEG_MAGIC)
    }

    private companion object {
        val JPEG_MAGIC = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x01,
        )
    }

    // ── fetchArt cache paths ────────────────────────────────────────────────

    @Test
    fun `fetchArt emits the in-memory cached url`() = runTest {
        urlCacheOf(service)["artist|album"] = "http://cached/art.jpg"
        val url = service.fetchArt("Artist", "Album").first()
        assertEquals("http://cached/art.jpg", url)
    }

    @Test
    fun `fetchArt emits file url from the disk cache`() = runTest {
        val cacheKey = "artist|album".lowercase()
        val cachedFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")
        writeFakeImage(cachedFile)

        val url = service.fetchArt("Artist", "Album").first()

        assertEquals("file://${cachedFile.absolutePath}", url)
    }

    @Test
    fun `fetchArt completes without emission when both services fail`() = runTest {
        injectMockClient()
        val result = service.fetchArt("Artist", "Album").firstOrNull()
        assertNull(result)
    }

    @Test
    fun `fetchArt skips disk write when the best url is a local file`() = runTest {
        // Cover the bestUrl.startsWith("file://") guard: a file:// url from the
        // disk cache path is re-emitted without attempting a download.
        val cacheKey = "artist|album".lowercase()
        val cachedFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")
        writeFakeImage(cachedFile)
        val url = service.fetchArt("Artist", "Album").first()
        assertTrue(url!!.startsWith("file://"))
    }

    // ── fetchArtistArt paths ────────────────────────────────────────────────

    @Test
    fun `fetchArtistArt without coverArtId emits null when services fail`() = runTest {
        injectMockClient()
        val result = service.fetchArtistArt("NoSuchArtist").first()
        assertNull(result)
    }

    @Test
    fun `fetchArtistArt with blank coverArtId skips navidrome`() = runTest {
        injectMockClient()
        val result = service.fetchArtistArt("Artist", coverArtId = "").first()
        assertNull(result)
    }

    // ── buildNavidromeCoverArtUrl ───────────────────────────────────────────

    @Test
    fun `navidrome url is null without credentials`() {
        com.lucasdss.ftpmusic.app.di.ServerConfigState.value = com.lucasdss.ftpmusic.app.di.ServerConfig()
        val method = CoverArtFallbackService::class.java.getDeclaredMethod(
            "buildNavidromeCoverArtUrl",
            String::class.java,
        )
        method.isAccessible = true
        assertNull(method.invoke(service, "ca-1"))
    }

    @Test
    fun `navidrome url embeds the cover id and auth params`() {
        com.lucasdss.ftpmusic.app.di.ServerConfigState.value = com.lucasdss.ftpmusic.app.di.ServerConfig(
            "https://music.example.com",
            "user",
            "pass",
        )

        val method = CoverArtFallbackService::class.java.getDeclaredMethod(
            "buildNavidromeCoverArtUrl",
            String::class.java,
        )
        method.isAccessible = true
        val url = method.invoke(service, "ca-1") as String

        assertTrue(url.startsWith("https://music.example.com/rest/getCoverArt?"))
        assertTrue(url.contains("id=ca-1"))
        assertTrue(url.contains("size=300"))
        assertTrue(url.contains("u=user"))
        assertTrue(url.contains("t=")) // token
        assertTrue(url.contains("s=")) // salt
        assertTrue(url.contains("v=1.16.1"))
        assertTrue(url.contains("f=json"))
    }

    // ── getImageSize heuristics ─────────────────────────────────────────────

    @Test
    fun `getImageSize returns 360k for 600x600 urls and 0 otherwise`() {
        val method = CoverArtFallbackService::class.java.getDeclaredMethod("getImageSize", String::class.java)
        method.isAccessible = true
        assertEquals(360_000, method.invoke(service, "http://x/600x600bb.jpg"))
        assertEquals(0, method.invoke(service, "http://x/100x100bb.jpg"))
        assertEquals(0, method.invoke(service, "https://musicbrainz.org/cover"))
    }

    // ── cacheNavidromeArt early return ──────────────────────────────────────

    @Test
    fun `cacheNavidromeArt skips when the file already exists`() {
        val cachedFile = File(cacheDir, "navidrome|${"ca-1".hashCode()}.jpg")
        writeFakeImage(cachedFile)
        val client = injectMockClient()

        service.cacheNavidromeArt("ca-1", "http://image")

        // No network call made for an existing file
        io.mockk.verify(exactly = 0) { client.newCall(any()) }
    }

    // ── singleton access ────────────────────────────────────────────────────

    @Test
    fun `getInstance returns the same singleton and resets after`() {
        val a = CoverArtFallbackService.getInstance(context)
        val b = CoverArtFallbackService.getInstance(context)
        assertSame(a, b)
        // Reset the process-wide singleton so later tests (e.g.
        // DownloadManagerWorkerTest) construct their own instance bound to
        // THEIR context — otherwise they inherit this test's temp cacheDir.
        val field = CoverArtFallbackService::class.java.getDeclaredField("fallbackInstance")
        field.isAccessible = true
        field.set(null, null)
    }

    // ── quota helpers not covered elsewhere ─────────────────────────────────

    @Test
    fun `getCacheSizeBytes returns zero for empty cache`() {
        assertEquals(0L, service.getCacheSizeBytes())
    }

    @Test
    fun `evictIfNeeded no-ops when the cache directory is missing`() {
        // cacheDir already exists from setUp — delete it to force the null path
        cacheDir.deleteRecursively()
        service.evictIfNeeded() // no crash
        assertEquals(0L, service.getCacheSizeBytes())
    }

    @Test
    fun `getCacheSizeBytes sums file lengths`() {
        File(cacheDir, "a.jpg").writeText("ab")
        File(cacheDir, "b.jpg").writeText("cde")
        assertEquals(5L, service.getCacheSizeBytes())
    }
    // ── getImageSize local file path ────────────────────────────────────────

    @Test
    fun `getImageSize handles local file urls`() {
        val file = File(cacheDir, "art.jpg")
        file.writeText("img")
        val method = CoverArtFallbackService::class.java.getDeclaredMethod("getImageSize", String::class.java)
        method.isAccessible = true
        // BitmapFactory is a stub on the plain JVM → decodeFile returns null → 0
        assertEquals(0, method.invoke(service, "file://${file.absolutePath}"))
    }

    // ── evictIfNeeded over-quota loop ───────────────────────────────────────

    @Test
    fun `evictIfNeeded evicts oldest files until under the target`() {
        service.maxCacheBytes = 300
        val old = File(cacheDir, "old.jpg").apply { writeText("a".repeat(200)) }
        old.setLastModified(System.currentTimeMillis() - 60_000)
        val fresh = File(cacheDir, "fresh.jpg").apply { writeText("b".repeat(200)) }
        fresh.setLastModified(System.currentTimeMillis())

        service.evictIfNeeded()

        assertFalse("oldest file must be evicted", old.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `clearCache removes files and the in-memory url cache`() {
        val file = File(cacheDir, "art.jpg").apply { writeText("img") }
        urlCacheOf(service)["artist|album"] = "http://x"

        service.clearCache()

        assertFalse(file.exists())
        assertTrue(urlCacheOf(service).isEmpty())
    }

    // ── fetchArtistArt disk-cache hit ───────────────────────────────────────

    @Test
    fun `fetchArtistArt emits the cached file url`() = runTest {
        val cacheKey = "artist|iron maiden".lowercase()
        val cachedFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")
        writeFakeImage(cachedFile)

        val url = service.fetchArtistArt("Iron Maiden").first()

        assertEquals("file://${cachedFile.absolutePath}", url)
    }

    // ── cacheNavidromeArt launch path (bounded, no poll loop) ───────────────

    @Test
    fun `cacheNavidromeArt writes the image when the body is present`() {
        val client = mockk<OkHttpClient>()
        val call = mockk<okhttp3.Call>(relaxed = true)
        val response = mockk<okhttp3.Response>(relaxed = true)
        val body = mockk<okhttp3.ResponseBody>(relaxed = true)
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response
        every { body.bytes() } returns JPEG_MAGIC
        every { response.header("Content-Type") } returns "image/jpeg"
        every { response.body } returns body
        val field = CoverArtFallbackService::class.java.getDeclaredField("client")
        field.isAccessible = true
        field.set(service, client)

        service.cacheNavidromeArt("ca-new", "http://image/art.jpg")
        Thread.sleep(300)

        val cachedFile = File(cacheDir, "navidrome|${"ca-new".hashCode()}.jpg")
        assertTrue("image must be written by the coroutine", cachedFile.exists())
        assertFalse("temp file must not survive the atomic rename", File(cacheDir, cachedFile.name + ".tmp").exists())
    }

    @Test
    fun `cacheNavidromeArt rejects non-image content types`() {
        val client = mockk<OkHttpClient>()
        val call = mockk<okhttp3.Call>(relaxed = true)
        val response = mockk<okhttp3.Response>(relaxed = true)
        val body = mockk<okhttp3.ResponseBody>(relaxed = true)
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response
        every { response.body } returns body
        every { response.header("Content-Type") } returns "application/json"
        every { body.bytes() } returns """{"subsonic-response":{"status":"failed"}}""".toByteArray()
        val field = CoverArtFallbackService::class.java.getDeclaredField("client")
        field.isAccessible = true
        field.set(service, client)

        service.cacheNavidromeArt("ca-json", "http://image/art.jpg")
        Thread.sleep(300)

        assertFalse(
            "JSON error bodies must never be cached as images",
            File(cacheDir, "navidrome|${"ca-json".hashCode()}.jpg").exists(),
        )
    }

    @Test
    fun `fetchArt evicts a corrupt disk cache file and falls through`() = runTest {
        injectMockClient()
        val cacheKey = "artist|album".lowercase()
        val cachedFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")
        cachedFile.parentFile?.mkdirs()
        cachedFile.writeText("corrupt")

        val url = service.fetchArt("Artist", "Album").firstOrNull()

        assertNull(url)
        assertFalse("corrupt cache entry must be evicted", cachedFile.exists())
    }

    @Test
    fun `fetchArtistArt evicts a corrupt cache file and falls through`() = runTest {
        injectMockClient()
        val cacheKey = "artist|bad artist".lowercase()
        val cachedFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")
        cachedFile.parentFile?.mkdirs()
        cachedFile.writeText("corrupt")

        val url = service.fetchArtistArt("Bad Artist").firstOrNull()

        assertNull(url)
        assertFalse("corrupt cache entry must be evicted", cachedFile.exists())
    }

    @Test
    fun `fetchArtistArt falls through when the navidrome download fails`() = runTest {
        injectMockClient()
        // Connection-refused server: buildNavidromeCoverArtUrl succeeds, the
        // download fails, and the flow falls through to the (mocked) iTunes
        // search which returns no results.
        com.lucasdss.ftpmusic.app.di.ServerConfigState.value = com.lucasdss.ftpmusic.app.di.ServerConfig(
            "http://127.0.0.1:1",
            "user",
            "pass",
        )

        val url = service.fetchArtistArt("Some Artist", coverArtId = "ar-1").firstOrNull()

        assertNull(url)
    }

    @Test
    fun `cacheNavidromeArt no-ops when the response has no body`() {
        val client = mockk<OkHttpClient>()
        val call = mockk<okhttp3.Call>(relaxed = true)
        val response = mockk<okhttp3.Response>(relaxed = true)
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response
        every { response.body } returns null
        val field = CoverArtFallbackService::class.java.getDeclaredField("client")
        field.isAccessible = true
        field.set(service, client)

        service.cacheNavidromeArt("ca-null-body", "http://image/art.jpg")
        Thread.sleep(200)

        assertFalse(File(cacheDir, "navidrome|${"ca-null-body".hashCode()}.jpg").exists())
    }

    @Test
    fun `cacheNavidromeArt swallows download failures`() {
        val client = mockk<OkHttpClient>()
        val call = mockk<okhttp3.Call>(relaxed = true)
        every { client.newCall(any()) } returns call
        every { call.execute() } throws java.io.IOException("boom")
        val field = CoverArtFallbackService::class.java.getDeclaredField("client")
        field.isAccessible = true
        field.set(service, client)

        service.cacheNavidromeArt("ca-fail", "http://image/art.jpg")
        Thread.sleep(200)

        assertFalse(File(cacheDir, "navidrome|${"ca-fail".hashCode()}.jpg").exists())
    }

    @Test
    fun `cacheNavidromeArt rejects undecodable bytes without a content type`() {
        val client = mockk<OkHttpClient>()
        val call = mockk<okhttp3.Call>(relaxed = true)
        val response = mockk<okhttp3.Response>(relaxed = true)
        val body = mockk<okhttp3.ResponseBody>(relaxed = true)
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response
        every { response.body } returns body
        every { response.header("Content-Type") } returns null
        every { body.bytes() } returns "not-an-image".toByteArray()
        val field = CoverArtFallbackService::class.java.getDeclaredField("client")
        field.isAccessible = true
        field.set(service, client)

        service.cacheNavidromeArt("ca-garbage", "http://image/art.jpg")
        Thread.sleep(200)

        assertFalse(
            "non-image bytes must never be cached",
            File(cacheDir, "navidrome|${"ca-garbage".hashCode()}.jpg").exists(),
        )
        assertFalse(File(cacheDir, "navidrome|${"ca-garbage".hashCode()}.jpg.tmp").exists())
    }

    @Test
    fun `cleanOrphanedNavidromeArt skips an empty active set`() {
        val file = File(cacheDir, "navidrome|${"ca-1".hashCode()}.jpg")
        file.writeText("keep")
        file.setLastModified(System.currentTimeMillis() - 10 * 60_000L)

        service.cleanOrphanedNavidromeArt(emptySet())

        assertTrue("empty active set must never sweep the cache", file.exists())
    }

    @Test
    fun `cleanOrphanedNavidromeArt skips recently written files`() {
        val fresh = File(cacheDir, "navidrome|${"ca-fresh".hashCode()}.jpg")
        fresh.writeText("fresh")

        service.cleanOrphanedNavidromeArt(setOf("ca-1"))

        assertTrue("in-flight writes must not be raced by the sweep", fresh.exists())
    }

    @Test
    fun `cleanOrphanedNavidromeArt keeps active and non-navidrome files`() {
        File(cacheDir, "navidrome|${"ca-1".hashCode()}.jpg").writeText("active")
        val orphan = File(cacheDir, "navidrome|${"ca-old".hashCode()}.jpg")
        orphan.writeText("orphan")
        orphan.setLastModified(System.currentTimeMillis() - 10 * 60_000L)
        File(cacheDir, "artist|123.jpg").writeText("keep")

        service.cleanOrphanedNavidromeArt(setOf("ca-1"))

        assertTrue(File(cacheDir, "navidrome|${"ca-1".hashCode()}.jpg").exists())
        assertFalse(File(cacheDir, "navidrome|${"ca-old".hashCode()}.jpg").exists())
        assertTrue(File(cacheDir, "artist|123.jpg").exists())
    }

    // ── download success paths (loopback HTTP server) ───────────────────────

    @Test
    fun `fetchArt emits and caches the itunes result`() = runTest {
        val (server, port) = startImageServer()
        try {
            injectMockClientWithBody(
                json = """{"results":[{"artworkUrl100":"http://127.0.0.1:$port/art_100x100bb.jpg"}]}""",
            )

            val urls = service.fetchArt("Artist", "Album").toList()

            assertTrue("expected at least one emitted url", urls.isNotEmpty())
            assertTrue(urls.first()!!.contains("600x600bb"))
            val cachedFile = File(cacheDir, "${"artist|album".hashCode()}.jpg")
            assertTrue("downloaded art must be cached", cachedFile.exists())
        } finally {
            server.close()
        }
    }

    @Test
    fun `fetchArtistArt caches the itunes result on download success`() = runTest {
        val (server, port) = startImageServer()
        try {
            injectMockClientWithBody(
                json = """{"results":[{"artworkUrl100":"http://127.0.0.1:$port/art_100x100bb.jpg"}]}""",
            )

            val url = service.fetchArtistArt("ITunes Artist").first()

            assertNotNull(url)
            assertTrue(url!!.startsWith("file://"))
        } finally {
            server.close()
        }
    }

    @Test
    fun `fetchArtistArt downloads and caches navidrome art`() = runTest {
        val (server, port) = startImageServer()
        try {
            com.lucasdss.ftpmusic.app.di.ServerConfigState.value = com.lucasdss.ftpmusic.app.di.ServerConfig(
                "http://127.0.0.1:$port",
                "user",
                "pass",
            )

            val url = service.fetchArtistArt("Server Artist", coverArtId = "ar-1").first()

            assertNotNull(url)
            assertTrue(url!!.startsWith("file://"))
        } finally {
            server.close()
        }
    }
}
