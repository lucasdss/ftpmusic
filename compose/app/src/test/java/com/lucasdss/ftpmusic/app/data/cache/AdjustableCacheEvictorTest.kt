package com.lucasdss.ftpmusic.app.data.cache

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.util.TreeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JVM tests for [AdjustableCacheEvictor]: pin/unpin semantics, pinned-key
 * hiding from the LRU delegate, metadata-flag fallback for cross-process
 * downloads, refresh replay, and every evictor callback. Delegate accounting
 * is observed through the private `currentSize` field of the underlying
 * LeastRecentlyUsedCacheEvictor.
 */
class AdjustableCacheEvictorTest {

    private fun newEvictor(limit: Long = 10_000) = AdjustableCacheEvictor { limit }

    private fun span(key: String, length: Long = 1000) =
        CacheSpan(key, 0, length, System.currentTimeMillis(), File("/tmp/$key"))

    private fun cacheWith(vararg spansByKey: Pair<String, List<CacheSpan>>): Cache {
        val cache = mockk<Cache>(relaxed = true)
        val map = spansByKey.map { (k, spans) -> k to TreeSet<CacheSpan>().apply { addAll(spans) } }.toMap()
        every { cache.getKeys() } returns map.keys
        every { cache.getCachedSpans(any()) } answers { map[firstArg<String>()] ?: TreeSet() }
        val metadata = mockk<ContentMetadata>(relaxed = true)
        every { metadata.get(any(), any<Long>()) } returns 0L
        every { cache.getContentMetadata(any()) } returns metadata
        return cache
    }

    private fun delegateSize(evictor: AdjustableCacheEvictor): Long {
        val delegateField = AdjustableCacheEvictor::class.java.getDeclaredField("delegate")
        delegateField.isAccessible = true
        val delegate = delegateField.get(evictor)
        val sizeField = delegate.javaClass.getDeclaredField("currentSize")
        sizeField.isAccessible = true
        return sizeField.getLong(delegate)
    }

    // ── pin / unpin ─────────────────────────────────────────────────────────

    @Test
    fun `pin hides the key and removes spans from delegate accounting`() {
        val cache = cacheWith("k" to listOf(span("k", 1000)))
        val evictor = newEvictor()
        evictor.onSpanAdded(cache, span("k", 1000))
        assertEquals(1000, delegateSize(evictor))

        evictor.pin("k")

        assertTrue(evictor.isPinned("k"))
        assertEquals(0, delegateSize(evictor))
    }

    @Test
    fun `unpin re-subjects the key to LRU accounting`() {
        val cache = cacheWith("k" to listOf(span("k", 1000)))
        val evictor = newEvictor()
        evictor.onSpanAdded(cache, span("k", 1000)) // attaches cacheRef + accounts
        assertEquals(1000, delegateSize(evictor))
        evictor.pin("k")
        assertTrue(evictor.isPinned("k"))
        assertEquals(0, delegateSize(evictor))

        evictor.unpin("k")

        assertFalse(evictor.isPinned("k"))
        assertEquals(1000, delegateSize(evictor))
    }

    @Test
    fun `pin and unpin work before any cache is attached`() {
        val evictor = newEvictor()
        evictor.pin("k")
        assertTrue(evictor.isPinned("k"))
        evictor.unpin("k")
        assertFalse(evictor.isPinned("k"))
    }

    @Test
    fun `pin of an already-pinned key is a no-op`() {
        val cache = cacheWith("k" to listOf(span("k")))
        val evictor = newEvictor()
        evictor.onSpanAdded(cache, span("k")) // attach cacheRef
        evictor.pin("k")
        evictor.pin("k")
        verify(exactly = 1) { cache.getCachedSpans("k") }
    }

    @Test
    fun `unpin of a non-pinned key is a no-op`() {
        val cache = cacheWith("k" to listOf(span("k")))
        val evictor = newEvictor()
        evictor.unpin("k")
        verify(exactly = 0) { cache.getCachedSpans("k") }
    }

    // ── callbacks hide pinned keys from the delegate ────────────────────────

    @Test
    fun `onSpanAdded ignores spans of pinned keys`() {
        val cache = cacheWith("k" to listOf(span("k")))
        val evictor = newEvictor()
        evictor.pin("k")

        evictor.onSpanAdded(cache, span("k", 1000))

        assertEquals(0, delegateSize(evictor))
    }

    @Test
    fun `onSpanAdded accounts spans of unpinned keys`() {
        val cache = cacheWith()
        val evictor = newEvictor()

        evictor.onSpanAdded(cache, span("u", 500))

        assertEquals(500, delegateSize(evictor))
    }

    @Test
    fun `onSpanRemoved ignores pinned keys and removes unpinned`() {
        val cache = cacheWith("k" to listOf(span("k")), "u" to listOf(span("u")))
        val evictor = newEvictor()
        evictor.pin("k")
        evictor.onSpanAdded(cache, span("k", 1000))
        evictor.onSpanAdded(cache, span("u", 1000))
        assertEquals(1000, delegateSize(evictor))

        evictor.onSpanRemoved(cache, span("k", 1000))
        evictor.onSpanRemoved(cache, span("u", 1000))

        assertEquals(0, delegateSize(evictor))
    }

    @Test
    fun `onStartFile and onSpanTouched ignore pinned keys`() {
        val cache = cacheWith("k" to listOf(span("k")))
        val evictor = newEvictor()
        evictor.pin("k")

        evictor.onStartFile(cache, "k", 0, 100)
        evictor.onSpanTouched(cache, span("k", 100), span("k", 200))

        assertEquals(0, delegateSize(evictor))
    }

    // ── metadata-flag fallback (cross-process downloads) ───────────────────

    @Test
    fun `isPinnedInternal falls back to the download metadata flag`() {
        val cache = mockk<Cache>(relaxed = true)
        val metadata = mockk<ContentMetadata>(relaxed = true)
        every { metadata.get(any(), any<Long>()) } returns 1L
        every { cache.getContentMetadata("dl-key") } returns metadata
        val evictor = newEvictor()

        // onSpanAdded consults isPinnedInternal → flagged as downloaded → hidden
        evictor.onSpanAdded(cache, span("dl-key", 1000))

        assertEquals(0, delegateSize(evictor))
        assertTrue(evictor.isPinned("dl-key")) // flag cached into the pin set
    }

    @Test
    fun `metadata flag read failure is treated as unpinned`() {
        val cache = mockk<Cache>(relaxed = true)
        every { cache.getContentMetadata("bad-key") } throws RuntimeException("metadata broken")
        val evictor = newEvictor()

        evictor.onSpanAdded(cache, span("bad-key", 1000))

        assertEquals(1000, delegateSize(evictor))
    }

    // ── refresh ─────────────────────────────────────────────────────────────

    @Test
    fun `refresh before cache attach just recreates the delegate`() {
        val evictor = newEvictor(limit = 5000)
        evictor.refresh()
        assertEquals(0, delegateSize(evictor))
    }

    @Test
    fun `refresh replays only unpinned spans into the new delegate`() {
        val pinnedSpan = span("k1", 2000)
        val unpinnedSpan = span("k2", 3000)
        val cache = cacheWith("k1" to listOf(pinnedSpan), "k2" to listOf(unpinnedSpan))
        val evictor = newEvictor()
        evictor.pin("k1") // cacheRef null → records the pin
        evictor.onSpanAdded(cache, pinnedSpan) // attaches cacheRef, pinned → hidden
        evictor.onSpanAdded(cache, unpinnedSpan)
        assertEquals(3000, delegateSize(evictor))

        evictor.refresh()

        // New delegate replays k2 only
        assertEquals(3000, delegateSize(evictor))
    }

    @Test
    fun `refresh keeps unpinned spans when the limit is unchanged`() {
        val cache = cacheWith("u" to listOf(span("u", 8000)))
        val evictor = newEvictor(limit = 10_000)
        evictor.onSpanAdded(cache, span("u", 8000))
        assertEquals(8000, delegateSize(evictor))

        evictor.refresh()

        assertEquals(8000, delegateSize(evictor))
    }

    @Test
    fun `requiresCacheSpanTouches returns true`() {
        assertTrue(newEvictor().requiresCacheSpanTouches())
    }

    @Test
    fun `onCacheInitialized does not crash`() {
        val evictor = newEvictor()
        evictor.onCacheInitialized()
    }

    @Test
    fun `unpin of a non-pinned key with an attached cache is a no-op`() {
        val cache = cacheWith("k" to listOf(span("k")))
        val evictor = newEvictor()
        evictor.onSpanAdded(cache, span("k")) // attach cacheRef
        evictor.unpin("other-key")
        verify(exactly = 0) { cache.getCachedSpans("other-key") }
    }
}
