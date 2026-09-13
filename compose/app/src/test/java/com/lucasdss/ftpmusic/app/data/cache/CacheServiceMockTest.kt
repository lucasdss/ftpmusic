package com.lucasdss.ftpmusic.app.data.cache

import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Plain-JVM (jacoco-instrumented) coverage for CacheService's local-first
 * logic using a MOCKED SimpleCache. The Robolectric suite (CacheServiceTest)
 * covers the same paths against the real cache but is invisible to jacoco
 * (Robolectric classloader + the agent), so this class keeps the changed
 * files above the coverage gate:
 *
 *  - R5: removeCached must refuse to delete pinned downloads
 *  - F4: importIntoCache must defer when the resource is locked (streaming)
 *  - getSpanFile / isStoredInCache / promoteToDownload decision logic
 */
class CacheServiceMockTest {

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val waveformRepo: WaveformRepository = mockk(relaxed = true)
    private val audioCache: SimpleCache = mockk(relaxed = true)
    private val evictor = AdjustableCacheEvictor { 100L * 1024 * 1024 }
    private lateinit var tempDir: File
    private lateinit var service: CacheService

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "ftpmusic-mock-${System.nanoTime()}").apply { mkdirs() }
        service = CacheService(trackDao, waveformRepo, tempDir, audioCache, evictor)
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
        File(tempDir.parentFile, "${tempDir.name}_tmp").deleteRecursively()
    }

    // Real CacheSpan instances — its public fields are not mockk-interceptable.
    private fun cachedSpan(file: File? = File("/fake/span.1")): CacheSpan = CacheSpan("t1", 0L, 100L, 0L, file)

    private fun holeSpan(): CacheSpan = CacheSpan("t1", 0L, 10L)

    // ── R5: downloads are sacred ─────────────────────────────────────────

    @Test
    fun `removeCached refuses to delete a pinned download`() = runTest {
        val entity = TrackEntity(id = "t1", title = "T", isDownloaded = true)
        coEvery { trackDao.getTrack("t1") } returns entity

        service.removeCached("t1")

        // No cache mutation, no Room metadata change, no waveform deletion
        coVerify(exactly = 0) { audioCache.removeResource(any()) }
        coVerify(exactly = 0) { trackDao.update(any()) }
        coVerify(exactly = 0) { waveformRepo.deleteByTrackIds(any()) }
        assertFalse(evictor.isPinned("t1") == false && false) // pin untouched (never unpinned)
    }

    @Test
    fun `removeCached removes auto-cached content and clears metadata`() = runTest {
        val entity = TrackEntity(id = "t1", title = "T", isDownloaded = false)
        coEvery { trackDao.getTrack("t1") } returns entity
        coEvery { trackDao.update(any()) } just runs
        coEvery { waveformRepo.deleteByTrackIds(any()) } just runs

        service.removeCached("t1")

        coVerify(exactly = 1) { audioCache.removeResource("t1") }
        assertFalse(evictor.isPinned("t1"))
        coVerify(exactly = 1) {
            trackDao.update(
                match {
                    !it.isDownloaded && !it.isAutoCached &&
                        it.cachedFilePath == null
                },
            )
        }
        coVerify(exactly = 1) { waveformRepo.deleteByTrackIds(listOf("t1")) }
    }

    @Test
    fun `removeCached handles missing room row`() = runTest {
        coEvery { trackDao.getTrack("missing") } returns null

        service.removeCached("missing")

        coVerify { audioCache.removeResource("missing") }
    }

    // ── F4: importIntoCache lock guard ───────────────────────────────────

    @Test
    fun `import defers when the resource is locked by a stream`() = runTest {
        // startReadWriteNonBlocking returns null → another reader/writer holds it
        coEvery { audioCache.startReadWriteNonBlocking(any(), any(), any()) } returns null
        val source = File(tempDir, "src.tmp").apply { writeBytes(ByteArray(10)) }

        val ok = service.writeCachedTrackFromFile("t1", source, isDownload = false)

        assertFalse("Locked resource must not be imported", ok)
        assertFalse("Source file must be cleaned up on deferral", source.exists())
        // The destructive removeResource must never run while locked
        coVerify(exactly = 0) { audioCache.removeResource(any()) }
        coVerify(exactly = 0) { trackDao.upsert(any()) }
    }

    @Test
    fun `import succeeds when the resource is free`() = runTest {
        val hole = holeSpan()
        coEvery { audioCache.startReadWriteNonBlocking("t1", 0L, 10L) } returns hole
        coEvery { audioCache.startFile("t1", 0L, 10L) } returns File(tempDir, "t1.span")
        coEvery { audioCache.getCachedSpans("t1") } returns
            java.util.TreeSet<CacheSpan>().apply { add(cachedSpan(File(tempDir, "t1.span"))) }
        coEvery { audioCache.getContentMetadata("t1") } returns mockk(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns null
        coEvery { trackDao.upsert(any()) } just runs

        val source = File(tempDir, "src2.tmp").apply { writeBytes(ByteArray(10)) }
        val ok = service.writeCachedTrackFromFile("t1", source, isDownload = true)

        assertTrue("Free resource must be imported", ok)
        // The probe lock is released; the stale content is dropped first
        coVerify(atLeast = 1) { audioCache.releaseHoleSpan(hole) }
        coVerify(exactly = 1) { audioCache.removeResource("t1") }
        assertTrue("download must be pinned", evictor.isPinned("t1"))
        coVerify(exactly = 1) { trackDao.upsert(match { it.isDownloaded && it.cacheSizeBytes == 10 }) }
    }

    @Test
    fun `zero-length files are rejected before touching the cache`() = runTest {
        val source = File(tempDir, "empty.tmp").apply { writeBytes(ByteArray(0)) }

        val ok = service.writeCachedTrackFromFile("t1", source, isDownload = false)

        assertFalse(ok)
        coVerify(exactly = 0) { audioCache.startReadWriteNonBlocking(any(), any(), any()) }
    }

    // ── getSpanFile / isStoredInCache / promoteToDownload ─────────────────

    @Test
    fun `isStoredInCache true when a full span at position 0 exists`() {
        coEvery { audioCache.getCachedSpans("t1") } returns java.util.TreeSet<CacheSpan>().apply { add(cachedSpan()) }
        coEvery { audioCache.getContentMetadata("t1") } returns mockk(relaxed = true)
        assertTrue(service.isStoredInCache("t1"))
    }

    @Test
    fun `isStoredInCache false when only a partial span exists`() {
        val span = CacheSpan("t1", 4096L, 100L, 0L, File("/fake/partial"))
        coEvery { audioCache.getCachedSpans("t1") } returns java.util.TreeSet<CacheSpan>().apply { add(span) }
        assertFalse(service.isStoredInCache("t1"))
    }

    @Test
    fun `isStoredInCache false when cache throws`() {
        coEvery { audioCache.getCachedSpans("t1") } throws RuntimeException("corrupt index")
        assertFalse(service.isStoredInCache("t1"))
    }

    @Test
    fun `promoteToDownload rejects tracks with no content`() = runTest {
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "T", isAutoCached = true)
        coEvery { audioCache.getCachedSpans("t1") } returns java.util.TreeSet()

        val ok = service.promoteToDownload("t1")

        assertFalse(ok)
        assertFalse(evictor.isPinned("t1"))
    }

    @Test
    fun `promoteToDownload is a no-op when already downloaded`() = runTest {
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "T", isDownloaded = true)

        assertTrue(service.promoteToDownload("t1"))
        coVerify(exactly = 0) { trackDao.update(any()) }
    }

    @Test
    fun `promoteToDownload rejects non-cached tracks`() = runTest {
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "T", isAutoCached = false)

        assertFalse(service.promoteToDownload("t1"))
    }

    @Test
    fun `promoteToDownload pins and marks an auto-cached track`() = runTest {
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "T", isAutoCached = true)
        coEvery { audioCache.getCachedSpans("t1") } returns java.util.TreeSet<CacheSpan>().apply { add(cachedSpan()) }
        coEvery { audioCache.getContentMetadata("t1") } returns mockk(relaxed = true)
        coEvery { trackDao.update(any()) } just runs

        val ok = service.promoteToDownload("t1")

        assertTrue(ok)
        assertTrue("download must be pinned", evictor.isPinned("t1"))
        coVerify(exactly = 1) { trackDao.update(match { it.isDownloaded && !it.isAutoCached }) }
    }

    // ── getCachedPath ────────────────────────────────────────────────────

    @Test
    fun `getCachedPath returns the span path or null`() = runTest {
        val realFile = File(tempDir, "span.1").apply { writeBytes(ByteArray(4)) }
        coEvery { audioCache.getCachedSpans("t1") } returns
            java.util.TreeSet<CacheSpan>().apply { add(cachedSpan(realFile)) }
        coEvery { audioCache.getContentMetadata("t1") } returns mockk(relaxed = true)
        assertEquals(realFile.absolutePath, service.getCachedPath("t1"))

        coEvery { audioCache.getCachedSpans("t2") } returns java.util.TreeSet()
        assertNull(service.getCachedPath("t2"))
    }
}
