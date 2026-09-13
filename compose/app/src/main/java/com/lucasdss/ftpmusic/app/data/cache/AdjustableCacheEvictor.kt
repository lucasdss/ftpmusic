package com.lucasdss.ftpmusic.app.data.cache

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor

/**
 * LRU cache evictor with a user-adjustable size limit and support for
 * pinning keys (downloaded tracks) so they are never evicted.
 *
 * Pinned keys are hidden from the underlying [LeastRecentlyUsedCacheEvictor],
 * so their spans neither count against the limit nor get LRU-evicted.
 *
 * Locking: SimpleCache invokes evictor callbacks while holding its own monitor.
 * Methods that mutate the cache ([refresh], [pin], [unpin]) acquire the cache
 * monitor first, then the internal lock — the same order as callbacks — to
 * avoid lock-order inversion.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AdjustableCacheEvictor(private val maxBytesProvider: () -> Long) : CacheEvictor {

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 2000L * 1024 * 1024 // 2 GB

        /** Content metadata flag marking a resource as a permanent download. */
        const val METADATA_IS_DOWNLOADED = "isDownloaded"
    }

    private val lock = Any()
    private var delegate = LeastRecentlyUsedCacheEvictor(maxBytesProvider())
    private val pinnedKeys = mutableSetOf<String>()

    @Volatile
    private var cacheRef: Cache? = null

    /** Re-reads the max size from [maxBytesProvider] and applies it, evicting immediately if needed. */
    fun refresh() {
        val cache = cacheRef
        if (cache == null) {
            synchronized(lock) { delegate = LeastRecentlyUsedCacheEvictor(maxBytesProvider()) }
            return
        }
        synchronized(cache) {
            synchronized(lock) {
                delegate = LeastRecentlyUsedCacheEvictor(maxBytesProvider())
                // Replay existing unpinned spans so the new delegate has correct
                // size accounting; this also evicts if the new limit is smaller.
                for (key in cache.keys.toList()) {
                    if (isPinnedInternal(cache, key)) continue
                    for (span in cache.getCachedSpans(key).toList()) {
                        delegate.onSpanAdded(cache, span)
                    }
                }
            }
        }
    }

    /** Excludes [key] from LRU eviction (used for downloaded tracks). */
    fun pin(key: String) {
        val cache = cacheRef
        if (cache == null) {
            synchronized(lock) { pinnedKeys.add(key) }
            return
        }
        synchronized(cache) {
            synchronized(lock) {
                if (!pinnedKeys.add(key)) return
                for (span in cache.getCachedSpans(key)) {
                    delegate.onSpanRemoved(cache, span)
                }
            }
        }
    }

    /** Re-subjects [key] to LRU eviction. */
    fun unpin(key: String) {
        val cache = cacheRef
        if (cache == null) {
            synchronized(lock) { pinnedKeys.remove(key) }
            return
        }
        synchronized(cache) {
            synchronized(lock) {
                if (!pinnedKeys.remove(key)) return
                for (span in cache.getCachedSpans(key)) {
                    delegate.onSpanAdded(cache, span)
                }
            }
        }
    }

    fun isPinned(key: String): Boolean = synchronized(lock) { key in pinnedKeys }

    /**
     * Pinned check used from evictor callbacks. Falls back to the persisted
     * [METADATA_IS_DOWNLOADED] content metadata flag so downloads written in a
     * previous process are protected during SimpleCache's initial scan, before
     * [CacheService] has had a chance to call [pin].
     */
    private fun isPinnedInternal(cache: Cache, key: String): Boolean {
        if (key in pinnedKeys) return true
        val downloaded = try {
            cache.getContentMetadata(key).get(METADATA_IS_DOWNLOADED, 0L) != 0L
        } catch (_: Exception) {
            false
        }
        if (downloaded) pinnedKeys.add(key)
        return downloaded
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() {
        synchronized(lock) { delegate.onCacheInitialized() }
    }

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        cacheRef = cache
        synchronized(lock) {
            if (!isPinnedInternal(cache, key)) delegate.onStartFile(cache, key, position, length)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        cacheRef = cache
        synchronized(lock) {
            if (!isPinnedInternal(cache, span.key)) delegate.onSpanAdded(cache, span)
        }
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        synchronized(lock) {
            if (!isPinnedInternal(cache, span.key)) delegate.onSpanRemoved(cache, span)
        }
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        synchronized(lock) {
            if (!isPinnedInternal(cache, oldSpan.key)) delegate.onSpanTouched(cache, oldSpan, newSpan)
        }
    }
}
