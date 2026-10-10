package com.lucasdss.ftpmusic.app.data.cache

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.SimpleCache
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
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
class CacheServiceTest {

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val waveformRepo: WaveformRepository = mockk(relaxed = true)
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "ftpmusic-test-${System.nanoTime()}")
    private val evictor = AdjustableCacheEvictor { 100L * 1024 * 1024 }

    @Suppress("DEPRECATION")
    private val simpleCache = SimpleCache(tempDir.apply { mkdirs() }, evictor)
    private val service = CacheService(trackDao, waveformRepo, tempDir, simpleCache, evictor)

    @After
    fun tearDown() {
        try {
            simpleCache.release()
        } catch (_: Exception) {}
        tempDir.deleteRecursively()
        File(tempDir.parentFile, "${tempDir.name}_tmp").deleteRecursively()
        File(tempDir.parentFile, "${tempDir.name}_legacy").deleteRecursively()
    }

    @Test
    fun `writeCachedTrack stores content in unified cache and updates entity`() = runTest {
        val trackId = "tr-1"
        val data = fakeAudioBytes()

        coEvery { trackDao.getTrack(trackId) } returns null
        coEvery { trackDao.upsert(any()) } just Runs

        service.writeCachedTrack(trackId, data)

        assertTrue(service.isStoredInCache(trackId))
        val path = service.getCachedPath(trackId)
        assertNotNull(path)
        assertArrayEquals(data, File(path!!).readBytes())
        coVerify { trackDao.upsert(match { it.id == trackId && it.isAutoCached && !it.isDownloaded }) }
    }

    @Test
    fun `getCachedPath returns null for missing track`() = runTest {
        coEvery { trackDao.getTrack("missing") } returns null
        assertNull(service.getCachedPath("missing"))
    }

    @Test
    fun `getTotalBytes starts at zero`() {
        assertEquals(0, service.getTotalBytes())
    }

    @Test
    fun `writeCachedTrackFromFile moves temp file into cache`() = runTest {
        val trackId = "tr-2"
        val tempFile = File(service.tempDirectory, "$trackId.tmp")
        tempFile.writeBytes(fakeAudioBytes())

        coEvery { trackDao.getTrack(trackId) } returns null
        coEvery { trackDao.upsert(any()) } just Runs

        val ok = service.writeCachedTrackFromFile(trackId, tempFile, isDownload = false)

        assertTrue(ok)
        assertFalse(tempFile.exists())
        val path = service.getCachedPath(trackId)
        assertNotNull(path)
        assertEquals(fakeAudioBytes().size.toLong(), File(path!!).length())
    }

    @Test
    fun `writeCachedTrackFromFile tracks as autoCache for proxy writes`() = runTest {
        val trackId = "tr-3"
        val tempFile = File(service.tempDirectory, "$trackId.tmp")
        tempFile.writeBytes(fakeAudioBytes())

        coEvery { trackDao.getTrack(trackId) } returns null
        coEvery { trackDao.upsert(any()) } just Runs

        service.writeCachedTrackFromFile(trackId, tempFile, isDownload = false)

        assertEquals(fakeAudioBytes().size.toLong(), service.getAutoCacheBytes())
        assertEquals(0, service.getDownloadBytes())
    }

    @Test
    fun `writeCachedTrackFromFile counts downloads separately and pins them`() = runTest {
        val trackId = "dl-1"
        val tempFile = File(service.tempDirectory, "$trackId.tmp")
        tempFile.writeBytes(fakeAudioBytes(5000))

        coEvery { trackDao.getTrack(trackId) } returns null
        coEvery { trackDao.upsert(any()) } just Runs

        service.writeCachedTrackFromFile(trackId, tempFile, isDownload = true)

        assertEquals(5000L, service.getDownloadBytes())
        assertEquals(0, service.getAutoCacheBytes())
        assertTrue(evictor.isPinned(trackId))
    }

    @Test
    fun `getCachedPath returns null when content is not in the unified cache`() = runTest {
        val trackId = "tr-5"
        // Room row exists but SimpleCache has no content (e.g. evicted)
        val entity = TrackEntity(id = trackId, title = "Test", cachedFilePath = "/nonexistent/$trackId.cache")
        coEvery { trackDao.getTrack(trackId) } returns entity

        assertNull(service.getCachedPath(trackId))
    }

    @Test
    fun `clearAutoCache removes auto-cached content but keeps downloads`() = runTest {
        val autoId = "auto-1"
        val dlId = "dl-1"
        coEvery { trackDao.getTrack(any()) } returns null
        coEvery { trackDao.upsert(any()) } just Runs
        service.writeCachedTrack(autoId, fakeAudioBytes(), isDownload = false)
        service.writeCachedTrack(dlId, fakeAudioBytes(5000), isDownload = true)

        val autoEntity =
            TrackEntity(
                id = autoId,
                title = "Auto",
                cachedFilePath = service.getCachedPath(autoId),
                isAutoCached = true,
            )
        val dlEntity =
            TrackEntity(id = dlId, title = "DL", cachedFilePath = service.getCachedPath(dlId), isDownloaded = true)
        coEvery { trackDao.getCachedPaginated(0, Int.MAX_VALUE) } returns listOf(autoEntity, dlEntity)
        coEvery { trackDao.update(any()) } just Runs

        service.clearAutoCache()

        assertFalse(service.isStoredInCache(autoId))
        assertTrue(service.isStoredInCache(dlId))
        coVerify { waveformRepo.deleteByTrackIds(listOf(autoId)) }
    }

    @Test
    fun `clearDownloads removes downloaded content`() = runTest {
        val dlId = "dl-2"
        coEvery { trackDao.getTrack(any()) } returns null
        coEvery { trackDao.upsert(any()) } just Runs
        service.writeCachedTrack(dlId, fakeAudioBytes(), isDownload = true)

        val dlEntity =
            TrackEntity(id = dlId, title = "DL", cachedFilePath = service.getCachedPath(dlId), isDownloaded = true)
        coEvery { trackDao.getCachedPaginated(0, Int.MAX_VALUE) } returns listOf(dlEntity)
        coEvery { trackDao.update(any()) } just Runs

        service.clearDownloads()

        assertFalse(service.isStoredInCache(dlId))
        assertEquals(0, service.getDownloadBytes())
        coVerify { waveformRepo.deleteByTrackIds(listOf(dlId)) }
        coVerify { trackDao.update(match { it.id == dlId && !it.isDownloaded && it.cachedFilePath == null }) }
    }

    @Test
    fun `writeCachedTrackFromFile preserves existing metadata`() = runTest {
        val trackId = "tr-preserve"
        val tempFile = File(service.tempDirectory, "$trackId.tmp")
        tempFile.writeBytes(fakeAudioBytes())

        // Simulate ScrobbleService already created a row with real title
        val existing = TrackEntity(id = trackId, title = "Real Song Name", artist = "Real Artist")
        coEvery { trackDao.getTrack(trackId) } returns existing
        coEvery { trackDao.upsert(any()) } just Runs

        service.writeCachedTrackFromFile(trackId, tempFile, isDownload = false)

        val slot = slot<TrackEntity>()
        coVerify { trackDao.upsert(capture(slot)) }
        assertEquals("Real Song Name", slot.captured.title)
        assertEquals("Real Artist", slot.captured.artist)
        assertTrue(slot.captured.isAutoCached)
    }

    @Test
    fun `writeCachedTrackFromFile rejects subsonic error json payloads`() = runTest {
        val trackId = "tr-poison"
        val tempFile = File(service.tempDirectory, "$trackId.tmp")
        tempFile.writeBytes(subsonicErrorJsonBytes())

        val ok = service.writeCachedTrackFromFile(trackId, tempFile, isDownload = false)

        assertFalse(ok)
        assertFalse(tempFile.exists())
        assertFalse(service.isStoredInCache(trackId))
    }

    @Test
    fun `writeCachedTrackFromFile rejects zero-byte files`() = runTest {
        val trackId = "tr-empty"
        val tempFile = File(service.tempDirectory, "$trackId.tmp")
        tempFile.writeBytes(ByteArray(0))

        val ok = service.writeCachedTrackFromFile(trackId, tempFile, isDownload = false)

        assertFalse(ok)
        assertFalse(tempFile.exists())
        assertFalse(service.isStoredInCache(trackId))
    }

    @Test
    fun `promoteToDownload pins cached track without re-download`() = runTest {
        val trackId = "tr-promote"
        coEvery { trackDao.getTrack(trackId) } returns null
        coEvery { trackDao.upsert(any()) } just Runs
        service.writeCachedTrack(trackId, fakeAudioBytes(), isDownload = false)

        val entity =
            TrackEntity(id = trackId, title = "T", cachedFilePath = service.getCachedPath(trackId), isAutoCached = true)
        coEvery { trackDao.getTrack(trackId) } returns entity
        coEvery { trackDao.update(any()) } just Runs

        assertTrue(service.promoteToDownload(trackId))

        assertTrue(evictor.isPinned(trackId))
        assertEquals(fakeAudioBytes().size.toLong(), service.getDownloadBytes())
        coVerify { trackDao.update(match { it.isDownloaded && !it.isAutoCached }) }
    }

    @Test
    fun `initialize clears stale rows and pins downloads, without re-running`() = runTest {
        val dlId = "dl-init"
        coEvery { trackDao.getTrack(any()) } returns null
        coEvery { trackDao.upsert(any()) } just Runs
        service.writeCachedTrack(dlId, fakeAudioBytes(5000), isDownload = true)

        val downloadedEntity = TrackEntity(
            id = dlId,
            title = "Init DL",
            cachedFilePath = service.getCachedPath(dlId),
            cacheSizeBytes = 500,
            isDownloaded = true,
        )
        val staleEntity = TrackEntity(
            id = "gone",
            title = "Stale",
            cachedFilePath = "/nonexistent/gone.cache",
            cacheSizeBytes = 100,
            isAutoCached = true,
        )
        coEvery { trackDao.getCachedPaginated(any(), any()) } returnsMany listOf(
            listOf(downloadedEntity, staleEntity),
            emptyList(),
        )
        coEvery { trackDao.update(any()) } just Runs
        coEvery { trackDao.getTrack("gone") } returns staleEntity.copy()

        service.initialize()

        assertTrue(evictor.isPinned(dlId))
        coVerify { trackDao.update(match { it.id == "gone" && it.cachedFilePath == null && !it.isAutoCached }) }
        coVerify(exactly = 2) { trackDao.getCachedPaginated(any(), any()) }

        service.initialize()
        // Still only 2 total calls — second initialize returned early
        coVerify(exactly = 2) { trackDao.getCachedPaginated(any(), any()) }
    }

    @Test
    fun `relocateLegacyFiles parks legacy files and leaves SimpleCache files alone`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "ftpmusic-legacy-${System.nanoTime()}").apply { mkdirs() }
        try {
            File(dir, "track-1.cache").writeText("legacy-audio")
            File(dir, "stale.tmp").writeText("partial")
            File(dir, "2.0.12345.v3.exo").writeText("span")
            File(dir, "cached_content_index.exi").writeText("index")
            File(dir, "abc.uid").writeText("")

            CacheService.relocateLegacyFiles(dir)

            val legacyDir = CacheService.legacyDirectory(dir)
            assertTrue(File(legacyDir, "track-1.cache").exists())
            assertFalse(File(dir, "track-1.cache").exists())
            // Incomplete legacy temp files are deleted, not parked
            assertFalse(File(dir, "stale.tmp").exists())
            assertFalse(File(legacyDir, "stale.tmp").exists())
            // SimpleCache-owned files stay in place
            assertTrue(File(dir, "2.0.12345.v3.exo").exists())
            assertTrue(File(dir, "cached_content_index.exi").exists())
            assertTrue(File(dir, "abc.uid").exists())
        } finally {
            CacheService.legacyDirectory(dir).deleteRecursively()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `initialize imports legacy files and pins downloads recorded in Room`() = runTest {
        val legacyDir = CacheService.legacyDirectory(tempDir).apply { mkdirs() }
        File(legacyDir, "legacy-dl.cache").writeBytes(fakeAudioBytes())
        File(legacyDir, "legacy-auto.cache").writeBytes(fakeAudioBytes())

        coEvery { trackDao.getTrack("legacy-dl") } returns
            TrackEntity(id = "legacy-dl", title = "DL", isDownloaded = true)
        coEvery { trackDao.getTrack("legacy-auto") } returns null
        coEvery { trackDao.upsert(any()) } just Runs
        coEvery { trackDao.getCachedPaginated(any(), any()) } returns emptyList()

        service.initialize()

        assertTrue(service.isStoredInCache("legacy-dl"))
        assertTrue(service.isStoredInCache("legacy-auto"))
        assertEquals(fakeAudioBytes().size.toLong(), File(service.getCachedPath("legacy-dl")!!).length())
        // Pin status restored from the Room isDownloaded flag
        assertTrue(evictor.isPinned("legacy-dl"))
        assertFalse(evictor.isPinned("legacy-auto"))
        // Parking directory removed after import
        assertFalse(legacyDir.exists())
    }

    @Test
    fun `removeCached removes content via SimpleCache and clears Room metadata`() = runTest {
        val trackId = "tr-remove"
        coEvery { trackDao.getTrack(trackId) } returns null
        coEvery { trackDao.upsert(any()) } just Runs
        service.writeCachedTrack(trackId, fakeAudioBytes(), isDownload = false)
        assertTrue(service.isStoredInCache(trackId))

        val entity =
            TrackEntity(
                id = trackId,
                title = "T",
                cachedFilePath = service.getCachedPath(trackId),
                isDownloaded = false,
            )
        coEvery { trackDao.getTrack(trackId) } returns entity
        coEvery { trackDao.update(any()) } just Runs

        service.removeCached(trackId)

        assertFalse(service.isStoredInCache(trackId))
        assertFalse(evictor.isPinned(trackId))
        coVerify {
            trackDao.update(
                match {
                    it.id == trackId && it.cachedFilePath == null && !it.isDownloaded &&
                        !it.isAutoCached
                },
            )
        }
    }

    @Test
    fun `removeCached refuses to delete a pinned download (downloads are sacred)`() = runTest {
        val trackId = "tr-download"
        coEvery { trackDao.getTrack(trackId) } returns null
        coEvery { trackDao.upsert(any()) } just Runs
        service.writeCachedTrack(trackId, fakeAudioBytes(), isDownload = true)
        assertTrue(service.isStoredInCache(trackId))
        assertTrue(evictor.isPinned(trackId))

        val entity =
            TrackEntity(id = trackId, title = "T", cachedFilePath = service.getCachedPath(trackId), isDownloaded = true)
        coEvery { trackDao.getTrack(trackId) } returns entity
        coEvery { trackDao.update(any()) } just Runs

        service.removeCached(trackId)

        // Content, pin and Room metadata all survive — only user-initiated
        // deletion (clearDownloads) may remove a download.
        assertTrue(service.isStoredInCache(trackId))
        assertTrue(evictor.isPinned(trackId))
        coVerify(exactly = 0) { trackDao.update(any()) }
    }

    @Test
    fun `hasPlayableSpan true for audio false for poison`() = runTest {
        plantSpan("tr-ok", fakeAudioBytes())
        assertTrue(service.hasPlayableSpan("tr-ok"))
        assertTrue(service.isPlayableCached("tr-ok"))

        val poison = ByteArray(AudioCacheValidation.MIN_CACHED_AUDIO_BYTES.toInt())
        subsonicErrorJsonBytes().copyInto(poison)
        plantSpan("tr-poison", poison)
        assertTrue(service.isStoredInCache("tr-poison"))
        assertFalse(service.hasPlayableSpan("tr-poison"))
        coEvery { trackDao.getTrack("tr-poison") } returns
            TrackEntity(id = "tr-poison", title = "P", isDownloaded = true, cachedFilePath = "/x")
        coEvery { trackDao.update(any()) } just Runs
        assertFalse(service.isPlayableCached("tr-poison"))
        assertFalse(service.isStoredInCache("tr-poison"))
        coVerify {
            trackDao.update(match { !it.isDownloaded && it.cachedFilePath == null })
        }
    }

    @Test
    fun `isPlayableCached keeps unknown magic pin`() = runTest {
        val unknown = ByteArray(5000) { 0x11 }
        plantSpan("tr-unk", unknown)
        assertTrue(service.isStoredInCache("tr-unk"))
        assertFalse(service.hasPlayableSpan("tr-unk"))
        coEvery { trackDao.getTrack("tr-unk") } returns
            TrackEntity(id = "tr-unk", title = "U", isDownloaded = true, cachedFilePath = "/u")
        assertFalse(service.isPlayableCached("tr-unk"))
        assertTrue(service.isStoredInCache("tr-unk"))
        coVerify(exactly = 0) { trackDao.update(any()) }
    }

    @OptIn(UnstableApi::class)
    private fun plantSpan(trackId: String, data: ByteArray) {
        val length = data.size.toLong()
        val probe = simpleCache.startReadWriteNonBlocking(trackId, 0, length)!!
        if (probe.isHoleSpan) simpleCache.releaseHoleSpan(probe)
        simpleCache.removeResource(trackId)
        val hole = simpleCache.startReadWriteNonBlocking(trackId, 0, length)!!
        val mutations = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(mutations, length)
        simpleCache.applyContentMetadataMutations(trackId, mutations)
        val cacheFile = simpleCache.startFile(trackId, 0, length)
        cacheFile.writeBytes(data)
        simpleCache.commitFile(cacheFile, length)
        simpleCache.releaseHoleSpan(hole)
    }
}
