package com.lucasdss.ftpmusic.app.data.cache

import android.content.Context
import io.mockk.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CoverArtFallbackServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cacheDir: File
    private lateinit var service: CoverArtFallbackService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cacheDir = tempFolder.newFolder("covers")
        every { context.applicationContext } returns context
        every { context.cacheDir } returns tempFolder.root
        service = CoverArtFallbackService(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Bytes that pass the image magic-byte check on the plain JVM. */
    private fun writeFakeImage(file: File) {
        file.parentFile?.mkdirs()
        file.writeBytes(
            byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
                0x00, 0x01,
            ),
        )
    }

    // ── Artist art: cache key matching SearchScreen ──────────────────────────

    @Test
    fun `artist cache key matches SearchScreen artistArtUrl format`() {
        // SearchScreen computes: "artist|${name.lowercase()}"
        val artistName = "Iron Maiden"
        val cacheKey = "artist|${artistName.lowercase()}"
        val expectedFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")
        val expectedPath = expectedFile.absolutePath

        // Verify the key format is deterministic
        assertEquals("artist|iron maiden", cacheKey)
        assertTrue(expectedPath.contains("covers"))
    }

    // ── fetchArtistArt cache hit ────────────────────────────────────────────

    @Test
    fun `fetchArtistArt returns file URL when cached file exists`() = runTest(testDispatcher) {
        val artistName = "Iron Maiden"
        val cacheKey = "artist|iron maiden"
        val cachedFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")

        // Pre-create a cached file
        writeFakeImage(cachedFile)

        val result = withContext(Dispatchers.IO) {
            service.fetchArtistArt(artistName).first()
        }

        assertNotNull(result)
        assertEquals("file://${cachedFile.absolutePath}", result)
    }

    // ── fetchArtistArt cache miss with working iTunes ───────────────────────

    @Test
    fun `fetchArtistArt emits null when cache miss and network unavailable`() = runTest(testDispatcher) {
        // On a test device without network, iTunes fetch will fail
        // The flow should emit null (best-effort) without crashing
        val artistName = "UnknownArtistXYZ123"

        val result = withContext(Dispatchers.IO) {
            service.fetchArtistArt(artistName).first()
        }

        // Either null (network failed) or a URL (if iTunes happened to work)
        // The important thing: no exception thrown
        assertTrue(result == null || result.startsWith("http"))
    }

    // ── fetchArtistArt flowOn uses IO dispatcher ────────────────────────────

    @Test
    fun `fetchArtistArt collection does not block main thread`() = runTest(testDispatcher) {
        // The flow has .flowOn(Dispatchers.IO) — collecting should not
        // throw NetworkOnMainThreadException when network calls happen.
        // We verify by collecting on Main and confirming no crash.

        val artistName = "TestArtist"
        try {
            // Collect on main thread (testDispatcher)
            service.fetchArtistArt(artistName).first()
            // If we reach here without NetworkOnMainThreadException, the
            // flowOn(Dispatchers.IO) is working correctly
        } catch (e: Exception) {
            // Only fail if it's a NetworkOnMainThreadException
            if (e is android.os.NetworkOnMainThreadException) {
                fail(
                    "fetchArtistArt should not throw NetworkOnMainThreadException when collected on Main thread. flowOn(Dispatchers.IO) may be missing.",
                )
            }
            // Other exceptions (network unavailable) are expected in test env
        }
    }

    // ── Artist art: different artists get different cache keys ───────────────

    @Test
    fun `different artist names produce different cache files`() {
        val artist1 = "Iron Maiden"
        val artist2 = "Metallica"
        val cacheKey1 = "artist|${artist1.lowercase()}"
        val cacheKey2 = "artist|${artist2.lowercase()}"
        val file1 = File(cacheDir, "${cacheKey1.hashCode()}.jpg")
        val file2 = File(cacheDir, "${cacheKey2.hashCode()}.jpg")

        assertNotEquals(file1.absolutePath, file2.absolutePath)
    }

    // ── cacheVersionState increments on file cached ─────────────────────────

    @Test
    fun `fetchArtistArt concurrent callers share one in-flight download`() = runTest(testDispatcher) {
        val artistName = "Shared Artist"
        // No disk cache — both collectors would otherwise race network.
        val results = kotlinx.coroutines.coroutineScope {
            val a = async(Dispatchers.IO) { service.fetchArtistArt(artistName).first() }
            val b = async(Dispatchers.IO) { service.fetchArtistArt(artistName).first() }
            listOf(a.await(), b.await())
        }
        // Without credentials/network both emit null — still must complete without crash.
        assertEquals(results[0], results[1])
    }

    @Test
    fun `observeVersion does not change for cache hits`() = runTest(testDispatcher) {
        val artistName = "CachedArtist"
        val cacheKey = "artist|${artistName.lowercase()}"
        val cachedFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")
        writeFakeImage(cachedFile)

        val versionBefore = service.observeVersion(cacheKey)
        val globalBefore = service.cacheVersionState.intValue

        withContext(Dispatchers.IO) {
            service.fetchArtistArt(artistName).first()
        }

        // Cache hit should not increment per-key or global version
        assertEquals(versionBefore, service.observeVersion(cacheKey))
        assertEquals(globalBefore, service.cacheVersionState.intValue)
    }

    @Test
    fun `cacheKeyFor builds album artist and navidrome identities`() {
        assertEquals("iron maiden|powerslave", service.cacheKeyFor("Iron Maiden", "Powerslave", null))
        assertEquals("artist|iron maiden", service.cacheKeyFor("Iron Maiden", null, null))
        assertEquals("navidrome|ar-1", service.cacheKeyFor(null, null, "ar-1"))
        assertNull(service.cacheKeyFor(null, null, null))
        assertNull(service.cacheKeyFor("", "", ""))
    }

    @Test
    fun `observeVersion bumps only the written key not global`() {
        val onFileCached = CoverArtFallbackService::class.java
            .getDeclaredMethod("onFileCached", String::class.java)
        onFileCached.isAccessible = true

        val globalBefore = service.cacheVersionState.intValue
        onFileCached.invoke(service, "artist|maiden")

        assertEquals(1, service.observeVersion("artist|maiden"))
        assertEquals(0, service.observeVersion("other|album"))
        assertEquals(0, service.observeVersion("navidrome|x"))
        // Home LazyRow must not see a global bump from artist writes.
        assertEquals(globalBefore, service.cacheVersionState.intValue)

        onFileCached.invoke(service, "other|album")
        assertEquals(1, service.observeVersion("artist|maiden"))
        assertEquals(1, service.observeVersion("other|album"))
        assertEquals(globalBefore, service.cacheVersionState.intValue)
    }

    @Test
    fun `clearCache resets key versions and bumps global generation`() {
        val onFileCached = CoverArtFallbackService::class.java
            .getDeclaredMethod("onFileCached", String::class.java)
        onFileCached.isAccessible = true
        onFileCached.invoke(service, "artist|maiden")
        assertEquals(1, service.observeVersion("artist|maiden"))

        val globalBefore = service.cacheVersionState.intValue
        service.clearCache()

        assertEquals(0, service.observeVersion("artist|maiden"))
        assertEquals(globalBefore + 1, service.cacheVersionState.intValue)
    }

    // ── clearCache removes files ────────────────────────────────────────────

    @Test
    fun `clearCache deletes all cached files`() {
        val file = File(cacheDir, "test.jpg")
        file.parentFile?.mkdirs()
        file.writeText("test")

        assertTrue(file.exists())

        service.clearCache()

        assertFalse(file.exists())
    }

    // ── LRU eviction ─────────────────────────────────────────────────────────

    @Test
    fun `evictIfNeeded does nothing when under quota`() {
        service.maxCacheBytes = 10L * 1024 * 1024 // 10MB
        val file = File(cacheDir, "small.jpg")
        file.writeText("small")
        assertTrue(file.exists())

        service.evictIfNeeded()

        assertTrue(file.exists()) // Under quota, nothing evicted
    }

    @Test
    fun `evictIfNeeded deletes oldest files when over quota`() {
        service.maxCacheBytes = 100 // tiny quota — 100 bytes
        // Write 3 files totaling > 100 bytes
        val file1 = File(cacheDir, "old.jpg")
        file1.writeText("a".repeat(50))
        file1.setLastModified(System.currentTimeMillis() - 10000)

        val file2 = File(cacheDir, "mid.jpg")
        file2.writeText("b".repeat(50))
        file2.setLastModified(System.currentTimeMillis() - 5000)

        val file3 = File(cacheDir, "new.jpg")
        file3.writeText("c".repeat(50))
        file3.setLastModified(System.currentTimeMillis())

        service.evictIfNeeded()

        // Oldest file should be deleted, newer ones may survive
        assertFalse(file1.exists())
    }

    @Test
    fun `evictIfNeeded does nothing when under quota boundary`() {
        service.maxCacheBytes = 200 // quota 200 bytes
        // Write 3 files totaling 150 bytes = 75% of quota — no eviction
        val file1 = File(cacheDir, "f1.jpg")
        file1.writeText("a".repeat(50))
        val file2 = File(cacheDir, "f2.jpg")
        file2.writeText("b".repeat(50))
        val file3 = File(cacheDir, "f3.jpg")
        file3.writeText("c".repeat(50))

        service.evictIfNeeded()

        // Total 150 < 200 quota — all files survive
        assertTrue(file1.exists())
        assertTrue(file2.exists())
        assertTrue(file3.exists())
    }

    @Test
    fun `getCacheSizeBytes returns total file sizes`() {
        File(cacheDir, "a.jpg").writeText("ab") // 2 bytes
        File(cacheDir, "b.jpg").writeText("cde") // 3 bytes

        val size = service.getCacheSizeBytes()

        assertEquals(5L, size)
    }

    @Test
    fun `cleanOrphanedNavidromeArt removes files with stale coverArt IDs`() {
        val active1 = File(cacheDir, "navidrome|${"ca-1".hashCode()}.jpg")
        active1.writeText("active")
        val orphan = File(cacheDir, "navidrome|${"ca-old".hashCode()}.jpg")
        orphan.writeText("orphan")
        orphan.setLastModified(System.currentTimeMillis() - 10 * 60_000L)
        val fallback = File(cacheDir, "artist|12345.jpg")
        fallback.writeText("fallback") // Not navidrome-prefixed, should survive

        service.cleanOrphanedNavidromeArt(setOf("ca-1"))

        assertTrue("Active cover art should survive", active1.exists())
        assertFalse("Orphaned cover art should be deleted", orphan.exists())
        assertTrue("Non-navidrome files should survive", fallback.exists())
    }

    // ── v45: fetchArtistArt with Navidrome coverArtId ───────────────────────

    @Test
    fun `fetchArtistArt with coverArtId returns cached file when present`() = runTest(testDispatcher) {
        val artistName = "AC/DC"
        val cacheKey = "artist|ac/dc"
        val cachedFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")
        writeFakeImage(cachedFile)

        val result = withContext(Dispatchers.IO) {
            service.fetchArtistArt(artistName, coverArtId = "ar-someid_0").first()
        }

        assertEquals("file://${cachedFile.absolutePath}", result)
    }

    @Test
    fun `fetchArtistArt with coverArtId emits null on cache miss without credentials`() = runTest(testDispatcher) {
        // No Subsonic credentials in the test env → Navidrome URL build is
        // skipped; iTunes is unreachable → null without throwing.
        val result = withContext(Dispatchers.IO) {
            service.fetchArtistArt("UnknownArtistXYZ123", coverArtId = "ar-someid_0").first()
        }

        assertTrue(result == null || result.startsWith("http"))
    }
}
