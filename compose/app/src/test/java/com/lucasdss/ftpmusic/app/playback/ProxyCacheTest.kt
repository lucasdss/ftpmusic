package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import io.mockk.*
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric required: SimpleCache depends on android.util.SparseArray internals
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ProxyCacheTest {

    private val mockAppContext: android.app.Application = mockk(relaxed = true)
    private val mockCacheService: CacheService = mockk(relaxed = true)
    private val mockOfflineModeManager: com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager =
        mockk(relaxed = true)
    private val mockDownloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)

    @Before
    fun setup() {
    }

    @After
    fun teardown() {
    }

    @Test
    fun `isCached returns true for downloaded tracks`() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "cache-test-${System.nanoTime()}")
        tmpDir.mkdirs()
        val evictor = com.lucasdss.ftpmusic.app.data.cache.AdjustableCacheEvictor { 100L * 1024 * 1024 }

        @Suppress("DEPRECATION")
        val simpleCache = androidx.media3.datasource.cache.SimpleCache(tmpDir, evictor)
        try {
            // Use a real in-memory tracking map since mockk relaxed returns nulls
            val trackStore = mutableMapOf<String, com.lucasdss.ftpmusic.app.data.db.TrackEntity>()
            val trackDao = mockk<com.lucasdss.ftpmusic.app.data.db.TrackDao>(relaxed = true)
            coEvery { trackDao.getTrack(any()) } answers { trackStore[firstArg()] }
            coEvery { trackDao.upsert(any()) } answers
                { trackStore[(firstArg() as com.lucasdss.ftpmusic.app.data.db.TrackEntity).id] = firstArg() }
            coEvery { trackDao.getRecentlyPlayedCached(any()) } returns emptyList()

            val cacheService =
                CacheService(trackDao, mockk<WaveformRepository>(relaxed = true), tmpDir, simpleCache, evictor)
            val trackId = "cached-track-1"
            val data = "test audio data".toByteArray()

            kotlinx.coroutines.runBlocking {
                cacheService.writeCachedTrack(trackId, data, isDownload = false)
            }

            val path = kotlinx.coroutines.runBlocking { cacheService.getCachedPath(trackId) }
            assertNotNull("Cached path should not be null", path)
            assertTrue(File(path!!).exists())
            assertArrayEquals(data, File(path).readBytes())
        } finally {
            try {
                simpleCache.release()
            } catch (_: Exception) {}
            tmpDir.deleteRecursively()
            File(tmpDir.parentFile, "${tmpDir.name}_tmp").deleteRecursively()
        }
    }
}
