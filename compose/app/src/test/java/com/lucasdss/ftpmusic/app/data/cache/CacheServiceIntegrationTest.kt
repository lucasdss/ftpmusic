package com.lucasdss.ftpmusic.app.data.cache

import androidx.media3.datasource.cache.SimpleCache
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import io.mockk.*
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric required: SimpleCache depends on android.util internals
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class CacheServiceIntegrationTest {

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "ftpmusic-test-int-${System.nanoTime()}")

    // 1000-byte quota so small writes trigger LRU eviction
    private val evictor = AdjustableCacheEvictor { 1000L }

    @Suppress("DEPRECATION")
    private val simpleCache = SimpleCache(tempDir.apply { mkdirs() }, evictor)
    private val service =
        CacheService(trackDao, mockk<WaveformRepository>(relaxed = true), tempDir, simpleCache, evictor)

    @After
    fun tearDown() {
        try {
            simpleCache.release()
        } catch (_: Exception) {}
        tempDir.deleteRecursively()
        File(tempDir.parentFile, "${tempDir.name}_tmp").deleteRecursively()
    }

    @Test
    fun `LRU eviction triggered when quota exceeded`() = runTest {
        coEvery { trackDao.getTrack(any()) } returns null
        coEvery { trackDao.upsert(any()) } just Runs

        val data = ByteArray(800)
        service.writeCachedTrack("tr-1", data)
        service.writeCachedTrack("tr-2", data)

        // 1600B > 1000B quota — oldest (tr-1) evicted, newest kept
        assertFalse(service.isStoredInCache("tr-1"))
        assertTrue(service.isStoredInCache("tr-2"))
        assertTrue(service.getTotalBytes() <= 1000L)
    }

    @Test
    fun `downloads are never evicted`() = runTest {
        coEvery { trackDao.getTrack(any()) } returns null
        coEvery { trackDao.upsert(any()) } just Runs

        val data = ByteArray(800)
        service.writeCachedTrack("dl-1", data, isDownload = true)
        service.writeCachedTrack("tr-2", data)
        service.writeCachedTrack("tr-3", data)

        // Pinned download survives even though total exceeds quota
        assertTrue(service.isStoredInCache("dl-1"))
        // Auto-cache LRU still applies to unpinned entries
        assertFalse(service.isStoredInCache("tr-2"))
        assertTrue(service.isStoredInCache("tr-3"))
    }

    @Test
    fun `getDownloadBytes tracks download bytes from unified cache`() = runTest {
        coEvery { trackDao.getTrack(any()) } returns null
        coEvery { trackDao.upsert(any()) } just Runs

        val data = ByteArray(500)
        service.writeCachedTrack("dl-1", data, isDownload = true)
        assertEquals(500, service.getDownloadBytes())
        assertEquals(0, service.getAutoCacheBytes())
    }

    @Test
    fun `evictor refresh applies a smaller limit immediately`() = runTest {
        coEvery { trackDao.getTrack(any()) } returns null
        coEvery { trackDao.upsert(any()) } just Runs

        var quota = 1000L
        val localDir = File(System.getProperty("java.io.tmpdir"), "ftpmusic-test-refresh-${System.nanoTime()}")
        val localEvictor = AdjustableCacheEvictor { quota }

        @Suppress("DEPRECATION")
        val localCache = SimpleCache(localDir.apply { mkdirs() }, localEvictor)
        val localService =
            CacheService(trackDao, mockk<WaveformRepository>(relaxed = true), localDir, localCache, localEvictor)
        try {
            localService.writeCachedTrack("a", ByteArray(400))
            localService.writeCachedTrack("b", ByteArray(400))
            assertTrue(localService.isStoredInCache("a"))
            assertTrue(localService.isStoredInCache("b"))

            quota = 500L
            localEvictor.refresh()

            // Oldest entry evicted to satisfy the new limit
            assertFalse(localService.isStoredInCache("a"))
            assertTrue(localService.isStoredInCache("b"))
        } finally {
            try {
                localCache.release()
            } catch (_: Exception) {}
            localDir.deleteRecursively()
            File(localDir.parentFile, "${localDir.name}_tmp").deleteRecursively()
        }
    }
}
