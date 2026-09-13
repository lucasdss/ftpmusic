package com.lucasdss.ftpmusic.app.data.cache

import androidx.media3.common.C
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
import java.io.File
import java.util.TreeSet
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Plain-JVM branch coverage for [CacheService]'s lifecycle and bulk methods
 * that the primary mock test does not reach: legacy relocation/import,
 * initialize reconciliation, clearAutoCache/clearDownloads, byte accounting,
 * the span content-length guard, and download-pinning failure tolerance.
 */
class CacheServiceBranchTest {

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val waveformRepo: WaveformRepository = mockk(relaxed = true)
    private val audioCache: SimpleCache = mockk(relaxed = true)
    private val evictor = AdjustableCacheEvictor { 100L * 1024 * 1024 }
    private lateinit var tempDir: File
    private lateinit var service: CacheService

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "ftpmusic-branch-${System.nanoTime()}").apply { mkdirs() }
        service = CacheService(trackDao, waveformRepo, tempDir, audioCache, evictor)
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
        File(tempDir.parentFile, "${tempDir.name}_tmp").deleteRecursively()
        File(tempDir.parentFile, "${tempDir.name}_legacy").deleteRecursively()
    }

    private fun cachedSpan(file: File = File("/fake/span.1"), length: Long = 100L) =
        CacheSpan("t1", 0L, length, 0L, file)

    private fun holeSpan() = CacheSpan("t1", 0L, 10L)

    private fun spansOf(vararg spans: CacheSpan) = TreeSet<CacheSpan>().apply { addAll(spans) }

    private fun metadataReturning(flag: Long): ContentMetadata {
        val meta = mockk<ContentMetadata>(relaxed = true)
        every { meta.get(any(), any<Long>()) } returns flag
        return meta
    }

    // ── relocateLegacyFiles (companion) ─────────────────────────────────────

    @Test
    fun `relocateLegacyFiles parks legacy files and removes tmp files`() {
        val dir = File(tempDir, "cache").apply { mkdirs() }
        File(dir, "span.exo").writeBytes(ByteArray(1))
        File(dir, "dir.uid").writeBytes(ByteArray(1))
        File(dir, "cached_content_index.x").writeBytes(ByteArray(1))
        File(dir, "partial.tmp").writeBytes(ByteArray(1))
        File(dir, "track1.cache").writeBytes(ByteArray(2))
        File(dir, "track2.cache").writeBytes(ByteArray(3))
        File(dir, "other.bin").writeBytes(ByteArray(4))

        CacheService.relocateLegacyFiles(dir)

        val legacy = File(dir.parentFile, "${dir.name}_legacy")
        assertTrue(legacy.isDirectory)
        assertTrue(File(legacy, "track1.cache").exists())
        assertTrue(File(legacy, "track2.cache").exists())
        assertTrue(File(legacy, "other.bin").exists())
        assertFalse(File(dir, "partial.tmp").exists())
        assertTrue(File(dir, "span.exo").exists()) // SimpleCache-owned files untouched
        assertTrue(File(dir, "dir.uid").exists())
    }

    @Test
    fun `relocateLegacyFiles no-ops on a missing directory`() {
        CacheService.relocateLegacyFiles(File(tempDir, "does-not-exist"))
        CacheService.relocateLegacyFiles(File(tempDir, "also-missing"))
    }

    // ── initialize reconciliation ───────────────────────────────────────────

    @Test
    fun `initialize pins downloaded tracks and clears stale rows`() = runTest {
        val cached = TrackEntity(id = "t1", title = "T1", isDownloaded = true, cachedFilePath = "/x")
        val stale = TrackEntity(id = "t2", title = "T2", isDownloaded = false)
        coEvery { trackDao.getCachedPaginated(0, 100) } returns listOf(cached, stale)
        coEvery { trackDao.getCachedPaginated(100, 100) } returns emptyList()
        coEvery { audioCache.getCachedSpans("t1") } returns spansOf(cachedSpan())
        coEvery { audioCache.getCachedSpans("t2") } returns TreeSet()
        coEvery { trackDao.getTrack("t2") } returns stale
        coEvery { trackDao.update(any()) } just runs

        service.initialize()

        assertTrue(evictor.isPinned("t1"))
        coVerify {
            trackDao.update(
                match {
                    it.id == "t2" && it.cachedFilePath == null && !it.isDownloaded &&
                        !it.isAutoCached
                },
            )
        }
    }

    @Test
    fun `initialize is idempotent across calls`() = runTest {
        coEvery { trackDao.getCachedPaginated(0, 100) } returns emptyList()
        service.initialize()
        service.initialize()
        coVerify(exactly = 1) { trackDao.getCachedPaginated(0, 100) }
    }

    @Test
    fun `initialize skips rows whose track vanished`() = runTest {
        val stale = TrackEntity(id = "gone", title = "G")
        coEvery { trackDao.getCachedPaginated(0, 100) } returns listOf(stale)
        coEvery { trackDao.getCachedPaginated(100, 100) } returns emptyList()
        coEvery { audioCache.getCachedSpans("gone") } returns TreeSet()
        coEvery { trackDao.getTrack("gone") } returns null

        service.initialize()

        coVerify(exactly = 0) { trackDao.update(any()) }
    }

    @Test
    fun `initialize imports parked legacy files`() = runTest {
        val legacyDir = File(tempDir.parentFile, "${tempDir.name}_legacy").apply { mkdirs() }
        File(legacyDir, "trackA.cache").writeBytes(ByteArray(5))
        coEvery { trackDao.getCachedPaginated(0, 100) } returns emptyList()
        coEvery { trackDao.getTrack(any()) } returns null
        coEvery { audioCache.startReadWriteNonBlocking(any(), any(), any()) } returns holeSpan()
        coEvery { audioCache.startFile(any(), any(), any()) } returns File(tempDir, "trackA.span")
        coEvery { audioCache.getCachedSpans(any()) } returns spansOf(cachedSpan(File(tempDir, "trackA.span")))
        coEvery { audioCache.getContentMetadata(any()) } returns mockk(relaxed = true)
        coEvery { trackDao.upsert(any()) } just runs

        service.initialize()

        assertFalse("legacy parking dir must be dropped after import", legacyDir.exists())
        coVerify { trackDao.upsert(match { it.id == "trackA" }) }
    }

    // ── clearAutoCache / clearDownloads ─────────────────────────────────────

    @Test
    fun `clearAutoCache removes auto-cached rows and unpinned keys`() = runTest {
        val auto = TrackEntity(id = "a1", title = "A", isDownloaded = false, cachedFilePath = "/x")
        val dl = TrackEntity(id = "d1", title = "D", isDownloaded = true, cachedFilePath = "/y")
        coEvery { trackDao.getCachedPaginated(0, Int.MAX_VALUE) } returns listOf(auto, dl)
        coEvery { trackDao.update(any()) } just runs
        coEvery { waveformRepo.deleteByTrackIds(any()) } just runs
        coEvery { audioCache.keys } returns linkedSetOf("a1", "d1")
        coEvery { audioCache.getContentMetadata("a1") } returns metadataReturning(0L)
        coEvery { audioCache.getContentMetadata("d1") } returns metadataReturning(1L)

        service.clearAutoCache()

        coVerify { audioCache.removeResource("a1") }
        coVerify(exactly = 0) { audioCache.removeResource("d1") } // pinned download untouched
        coVerify { waveformRepo.deleteByTrackIds(listOf("a1")) }
    }

    @Test
    fun `clearAutoCache tolerates cache failures`() = runTest {
        coEvery { trackDao.getCachedPaginated(0, Int.MAX_VALUE) } returns emptyList()
        coEvery { audioCache.keys } throws RuntimeException("boom")
        service.clearAutoCache()
    }

    @Test
    fun `clearDownloads removes downloaded tracks and unpins them`() = runTest {
        val dl = TrackEntity(id = "d1", title = "D", isDownloaded = true, cachedFilePath = "/x")
        coEvery { trackDao.getCachedPaginated(0, Int.MAX_VALUE) } returns listOf(dl)
        coEvery { trackDao.update(any()) } just runs
        coEvery { waveformRepo.deleteByTrackIds(any()) } just runs
        evictor.pin("d1")

        service.clearDownloads()

        coVerify { audioCache.removeResource("d1") }
        assertFalse("download must be unpinned", evictor.isPinned("d1"))
        coVerify { waveformRepo.deleteByTrackIds(listOf("d1")) }
    }

    // ── byte accounting ─────────────────────────────────────────────────────

    @Test
    fun `getDownloadBytes sums downloaded keys and getAutoCacheBytes subtracts`() {
        coEvery { audioCache.keys } returns linkedSetOf("d1", "a1")
        coEvery { audioCache.getContentMetadata("d1") } returns metadataReturning(1L)
        coEvery { audioCache.getContentMetadata("a1") } returns metadataReturning(0L)
        coEvery { audioCache.getCachedBytes("d1", 0, C.LENGTH_UNSET.toLong()) } returns 300L
        coEvery { audioCache.cacheSpace } returns 500L

        assertEquals(300L, service.getDownloadBytes())
        assertEquals(200L, service.getAutoCacheBytes())
    }

    @Test
    fun `getDownloadBytes returns 0 when cache throws`() {
        coEvery { audioCache.keys } throws RuntimeException("boom")
        assertEquals(0L, service.getDownloadBytes())
    }

    @Test
    fun `getTotalBytes returns 0 when cache throws`() {
        coEvery { audioCache.cacheSpace } throws RuntimeException("boom")
        assertEquals(0L, service.getTotalBytes())
    }

    // ── span content-length guard ───────────────────────────────────────────

    @Test
    fun `isStoredInCache rejects spans shorter than the content length`() {
        val span = CacheSpan("t1", 0L, 100L, 0L, File("/fake/span"))
        coEvery { audioCache.getCachedSpans("t1") } returns spansOf(span)
        val meta = mockk<ContentMetadata>(relaxed = true)
        every { meta.get(ContentMetadata.KEY_CONTENT_LENGTH, any<Long>()) } returns 200L
        coEvery { audioCache.getContentMetadata("t1") } returns meta

        assertFalse(service.isStoredInCache("t1"))
    }

    // ── markDownloaded failure tolerance ────────────────────────────────────

    @Test
    fun `promoteToDownload pins even when the metadata write fails`() = runTest {
        val auto = TrackEntity(id = "a1", title = "A", isAutoCached = true)
        coEvery { trackDao.getTrack("a1") } returns auto
        coEvery { audioCache.getCachedSpans("a1") } returns spansOf(cachedSpan())
        coEvery { audioCache.getContentMetadata("a1") } returns mockk(relaxed = true)
        coEvery {
            audioCache.applyContentMetadataMutations(
                any(),
                any<androidx.media3.datasource.cache.ContentMetadataMutations>(),
            )
        } throws RuntimeException("boom")
        coEvery { trackDao.update(any()) } just runs

        assertTrue(service.promoteToDownload("a1"))
        assertTrue("pin must survive metadata failure", evictor.isPinned("a1"))
    }

    // ── writeCachedTrack temp-file failure ──────────────────────────────────

    @Test
    fun `writeCachedTrack aborts when the temp write fails`() = runTest {
        // Make the temp directory path collide with an existing FILE so the
        // FileOutputStream constructor throws → no cache writes happen.
        val blocker = File(tempDir, "collision_tmp").apply { writeBytes(ByteArray(1)) }
        val brokenService = CacheService(trackDao, waveformRepo, File(tempDir, "collision"), audioCache, evictor)

        brokenService.writeCachedTrack("t1", ByteArray(5))

        coVerify(exactly = 0) { trackDao.upsert(any()) }
        assertFalse(File(blocker, "t1.tmp").exists())
    }
// ── writeCachedTrackFromFile entity-state combinations ────────────────────

    @Test
    fun `writeCachedTrackFromFile keeps download state when new write is not a download`() = runTest {
        val existing = TrackEntity(id = "t1", title = "T", isDownloaded = true, isAutoCached = false)
        coEvery { trackDao.getTrack("t1") } returns existing
        coEvery { audioCache.startReadWriteNonBlocking(any(), any(), any()) } returns holeSpan()
        coEvery { audioCache.startFile(any(), any(), any()) } returns File(tempDir, "t1.span")
        coEvery { audioCache.getCachedSpans("t1") } returns spansOf(cachedSpan(File(tempDir, "t1.span")))
        coEvery { audioCache.getContentMetadata(any()) } returns mockk(relaxed = true)
        coEvery { trackDao.upsert(any()) } just runs
        val source = File(tempDir, "src-dl.tmp").apply { writeBytes(ByteArray(10)) }

        val ok = service.writeCachedTrackFromFile("t1", source, isDownload = false)

        assertTrue(ok)
        coVerify { trackDao.upsert(match { it.isDownloaded && !it.isAutoCached }) }
    }

    @Test
    fun `writeCachedTrackFromFile marks auto-cached when nothing was downloaded before`() = runTest {
        val existing = TrackEntity(id = "t1", title = "T", isDownloaded = false, isAutoCached = false)
        coEvery { trackDao.getTrack("t1") } returns existing
        coEvery { audioCache.startReadWriteNonBlocking(any(), any(), any()) } returns holeSpan()
        coEvery { audioCache.startFile(any(), any(), any()) } returns File(tempDir, "t1.span")
        coEvery { audioCache.getCachedSpans("t1") } returns spansOf(cachedSpan(File(tempDir, "t1.span")))
        coEvery { audioCache.getContentMetadata(any()) } returns mockk(relaxed = true)
        coEvery { trackDao.upsert(any()) } just runs
        val source = File(tempDir, "src-auto.tmp").apply { writeBytes(ByteArray(10)) }

        service.writeCachedTrackFromFile("t1", source, isDownload = false)

        coVerify { trackDao.upsert(match { !it.isDownloaded && it.isAutoCached }) }
    }

    // ── importIntoCache lock/busy/exception paths ───────────────────────────

    @Test
    fun `import aborts when the write lock is taken after the probe`() = runTest {
        val probe = holeSpan()
        coEvery { audioCache.startReadWriteNonBlocking(any(), any(), any()) } returnsMany listOf(probe, null)
        val source = File(tempDir, "busy.tmp").apply { writeBytes(ByteArray(10)) }

        val ok = service.writeCachedTrackFromFile("t1", source, isDownload = false)

        assertFalse(ok)
        assertFalse(source.exists())
        coVerify(exactly = 0) { trackDao.upsert(any()) }
    }

    @Test
    fun `import returns true when content reappeared as cached`() = runTest {
        val probe = holeSpan()
        coEvery { audioCache.startReadWriteNonBlocking(any(), any(), any()) } returnsMany listOf(probe, cachedSpan())
        val source = File(tempDir, "reappeared.tmp").apply { writeBytes(ByteArray(10)) }

        val ok = service.writeCachedTrackFromFile("t1", source, isDownload = false)

        assertTrue("content already cached counts as success", ok)
        coVerify { audioCache.releaseHoleSpan(any()) }
    }

    @Test
    fun `import replaces partial cached span without releasing it as a hole`() = runTest {
        val partial = cachedSpan(File(tempDir, "partial.span"))
        val writeHole = holeSpan()
        coEvery { audioCache.startReadWriteNonBlocking(any(), any(), any()) } returnsMany listOf(partial, writeHole)
        coEvery { audioCache.startFile(any(), any(), any()) } returns File(tempDir, "complete.span")
        coEvery { audioCache.getCachedSpans("t1") } returns spansOf(cachedSpan(File(tempDir, "complete.span")))
        coEvery { audioCache.getContentMetadata(any()) } returns mockk(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns null
        coEvery { trackDao.upsert(any()) } just runs
        val source = File(tempDir, "partial-replacement.tmp").apply { writeBytes(ByteArray(10)) }

        val ok = service.writeCachedTrackFromFile("t1", source, isDownload = false)

        assertTrue("partial cache must be replaceable by completed download", ok)
        coVerify(exactly = 1) { audioCache.releaseHoleSpan(writeHole) }
        coVerify(exactly = 0) { audioCache.releaseHoleSpan(partial) }
        coVerify { audioCache.removeResource("t1") }
    }

    @Test
    fun `import swallows cache exceptions and deletes the source`() = runTest {
        coEvery { audioCache.startReadWriteNonBlocking(any(), any(), any()) } throws RuntimeException("cache broken")
        val source = File(tempDir, "crash.tmp").apply { writeBytes(ByteArray(10)) }

        val ok = service.writeCachedTrackFromFile("t1", source, isDownload = false)

        assertFalse(ok)
        assertFalse(source.exists())
    }

    @Test
    fun `import tolerates hole-span release failure in finally`() = runTest {
        val probe = holeSpan()
        coEvery { audioCache.startReadWriteNonBlocking(any(), any(), any()) } returnsMany listOf(probe, holeSpan())
        // First release (probe) succeeds; the finally-block release throws
        var releaseCount = 0
        coEvery { audioCache.releaseHoleSpan(any()) } answers {
            releaseCount++
            if (releaseCount == 2) throw RuntimeException("release broken")
        }
        coEvery { audioCache.startFile(any(), any(), any()) } returns File(tempDir, "t1.span")
        coEvery { audioCache.getCachedSpans("t1") } returns spansOf(cachedSpan(File(tempDir, "t1.span")))
        coEvery { audioCache.getContentMetadata(any()) } returns mockk(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns null
        coEvery { trackDao.upsert(any()) } just runs
        val source = File(tempDir, "release.tmp").apply { writeBytes(ByteArray(10)) }

        val ok = service.writeCachedTrackFromFile("t1", source, isDownload = false)

        assertTrue(ok)
    }

    // ── getSpanFile predicate / getCachedPath ───────────────────────────────

    @Test
    fun `isStoredInCache rejects cached spans without a file`() {
        val span = CacheSpan("t1", 0L, 100L, 0L, null) // isCached but file == null
        coEvery { audioCache.getCachedSpans("t1") } returns spansOf(span)
        assertFalse(service.isStoredInCache("t1"))
    }

    @Test
    fun `getCachedPath returns null when the span file is missing on disk`() = runTest {
        val missing = File(tempDir, "no-such-span")
        coEvery { audioCache.getCachedSpans("t1") } returns spansOf(cachedSpan(missing))
        coEvery { audioCache.getContentMetadata("t1") } returns mockk(relaxed = true)
        assertNull(service.getCachedPath("t1"))
    }

    // ── legacy import details ───────────────────────────────────────────────

    @Test
    fun `relocateLegacyFiles no-ops when nothing to relocate`() {
        val dir = File(tempDir, "cache-empty").apply { mkdirs() }
        File(dir, "span.exo").writeBytes(ByteArray(1))
        CacheService.relocateLegacyFiles(dir)
        assertFalse(File(dir.parentFile, "cache-empty_legacy").exists())
    }

    @Test
    fun `initialize skips legacy import when the parking path is not a directory`() = runTest {
        val legacyFile = File(tempDir.parentFile, "${tempDir.name}_legacy").apply { writeBytes(ByteArray(1)) }
        coEvery { trackDao.getCachedPaginated(0, 100) } returns emptyList()
        service.initialize()
        coVerify(exactly = 0) { trackDao.upsert(any()) }
        legacyFile.delete()
    }

    @Test
    fun `initialize imports a legacy download as pinned`() = runTest {
        val legacyDir = File(tempDir.parentFile, "${tempDir.name}_legacy").apply { mkdirs() }
        File(legacyDir, "trackD.cache").writeBytes(ByteArray(5))
        coEvery { trackDao.getCachedPaginated(0, 100) } returns emptyList()
        coEvery { trackDao.getTrack(any()) } returns TrackEntity(id = "trackD", title = "D", isDownloaded = true)
        coEvery { audioCache.startReadWriteNonBlocking(any(), any(), any()) } returns holeSpan()
        coEvery { audioCache.startFile(any(), any(), any()) } returns File(tempDir, "trackD.span")
        coEvery { audioCache.getCachedSpans(any()) } returns spansOf(cachedSpan(File(tempDir, "trackD.span")))
        coEvery { audioCache.getContentMetadata(any()) } returns mockk(relaxed = true)
        coEvery { trackDao.upsert(any()) } just runs

        service.initialize()

        assertTrue("legacy download must be pinned", evictor.isPinned("trackD"))
        legacyDir.deleteRecursively()
    }

    // ── promoteToDownload edge ──────────────────────────────────────────────

    @Test
    fun `promoteToDownload returns false when the track row is missing`() = runTest {
        coEvery { trackDao.getTrack("ghost") } returns null
        assertFalse(service.promoteToDownload("ghost"))
    }

    @Test
    fun `isStoredInCache ignores hole spans`() {
        val hole = CacheSpan("t1", 0L, 10L) // hole span → isCached == false
        coEvery { audioCache.getCachedSpans("t1") } returns spansOf(hole)
        assertFalse(service.isStoredInCache("t1"))
    }

    @Test
    fun `isStoredInCache skips the length check when content length is unset`() {
        val span = CacheSpan("t1", 0L, 100L, 0L, File("/fake/span"))
        coEvery { audioCache.getCachedSpans("t1") } returns spansOf(span)
        val meta = mockk<ContentMetadata>(relaxed = true)
        every { meta.get(ContentMetadata.KEY_CONTENT_LENGTH, any<Long>()) } returns C.LENGTH_UNSET.toLong()
        coEvery { audioCache.getContentMetadata("t1") } returns meta
        assertTrue(service.isStoredInCache("t1"))
    }
}
