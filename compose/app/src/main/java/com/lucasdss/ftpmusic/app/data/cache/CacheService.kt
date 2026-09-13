package com.lucasdss.ftpmusic.app.data.cache

import androidx.media3.common.C
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.SimpleCache
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache metadata service backed by the unified [SimpleCache].
 *
 * File storage is fully delegated to [SimpleCache] (shared with ExoPlayer,
 * DownloadManager and the Cast proxy). Room keeps track metadata
 * (title/artist/downloaded flags) and powers UI badges via [watchCacheStatus].
 *
 * Downloads are pinned in the [AdjustableCacheEvictor] so they are never
 * LRU-evicted; auto-cached tracks are normal LRU entries.
 */
@Singleton
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CacheService @Inject constructor(
    private val trackDao: TrackDao,
    private val waveformRepo: WaveformRepository,
    private val cacheDir: File,
    private val audioCache: SimpleCache,
    private val evictor: AdjustableCacheEvictor,
) {
    /** Directory owned by the unified [SimpleCache]. */
    val cacheDirectory: File get() = cacheDir

    /** Scratch directory for in-flight downloads — must live OUTSIDE the
     *  SimpleCache root, which deletes unrecognized files during its scan. */
    val tempDirectory: File by lazy {
        File(cacheDir.parentFile ?: cacheDir, "${cacheDir.name}_tmp").apply { mkdirs() }
    }

    /** Emits trackId and isDownload flag when a track is successfully cached or downloaded.
     *  UI collectors receive instant feedback without polling Room. */
    val cacheEventFlow = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 64)

    companion object {
        const val KB = 1024L
        const val MB = 1024L * KB
        const val GB = 1024L * MB

        /** Sibling directory where legacy (pre-SimpleCache) files are parked until import. */
        fun legacyDirectory(cacheDir: File): File = File(cacheDir.parentFile ?: cacheDir, "${cacheDir.name}_legacy")

        /**
         * Moves legacy complete files (old CacheService layout: `<trackId>.cache`) out of
         * [cacheDir] into [legacyDirectory]. MUST run before [SimpleCache] is constructed
         * on [cacheDir] — SimpleCache's initial scan deletes files it can't parse as
         * spans, which would wipe existing users' downloads on upgrade.
         * Stale legacy `.tmp` files (incomplete downloads) are deleted outright.
         */
        @JvmStatic
        fun relocateLegacyFiles(cacheDir: File) {
            val files = cacheDir.listFiles() ?: return
            var legacyDir: File? = null
            for (file in files) {
                if (!file.isFile) continue
                val name = file.name
                // SimpleCache-owned files: span files, uid marker, legacy flat index
                if (name.endsWith(".exo") || name.endsWith(".uid") ||
                    name.startsWith("cached_content_index")
                ) {
                    continue
                }
                if (name.endsWith(".tmp")) {
                    file.delete()
                    continue
                }
                val dir = legacyDir ?: legacyDirectory(cacheDir).apply { mkdirs() }
                    .also { legacyDir = it }
                val dest = File(dir, name)
                if (!file.renameTo(dest)) {
                    // Cross-filesystem fallback
                    try {
                        file.copyTo(dest, overwrite = true)
                        file.delete()
                    } catch (e: Exception) {
                        android.util.Log.w("ftpmusic-cache", "[legacy] failed to relocate $name: ${e.message}")
                        dest.delete()
                    }
                }
            }
            legacyDir?.let {
                android.util.Log.i(
                    "ftpmusic-cache",
                    "[legacy] parked ${it.listFiles()?.size ?: 0} legacy cache file(s) for import",
                )
            }
        }
    }

    private var initialized = false

    /** Reconcile Room metadata with the unified cache state on startup:
     *  pin downloaded tracks so they're never evicted, and clear rows whose
     *  content no longer exists in the cache. */
    suspend fun initialize(batchSize: Int = 100) {
        if (initialized) return
        importLegacyFiles()
        val staleIds = mutableListOf<String>()
        var offset = 0
        while (true) {
            val batch = trackDao.getCachedPaginated(offset, batchSize)
            if (batch.isEmpty()) break
            for (track in batch) {
                if (isStoredInCache(track.id)) {
                    if (track.isDownloaded) evictor.pin(track.id)
                } else {
                    staleIds.add(track.id)
                }
            }
            offset += batch.size
        }
        // Clear stale rows in a second pass — avoids offset drift from mid-iteration deletions
        for (trackId in staleIds) {
            trackDao.getTrack(trackId)?.let { existing ->
                trackDao.update(
                    existing.copy(
                        cachedFilePath = null,
                        cacheSizeBytes = null,
                        isAutoCached = false,
                        isDownloaded = false,
                    ),
                )
            }
        }
        initialized = true
    }

    /** Import legacy (pre-SimpleCache) files parked by [relocateLegacyFiles] into the
     *  unified cache, preserving each track's pin status from Room, then drop the
     *  parking directory. */
    private suspend fun importLegacyFiles() {
        val legacyDir = legacyDirectory(cacheDir)
        if (!legacyDir.isDirectory) return
        legacyDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val trackId = file.name.removeSuffix(".cache")
            if (trackId.isEmpty()) {
                file.delete()
                return@forEach
            }
            val isDownload = try {
                trackDao.getTrack(trackId)?.isDownloaded == true
            } catch (_: Exception) {
                false
            }
            try {
                val ok = writeCachedTrackFromFile(trackId, file, isDownload = isDownload)
                android.util.Log.d(
                    "ftpmusic-cache",
                    "[legacy] import $trackId isDownload=$isDownload ok=$ok",
                )
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-cache", "[legacy] import failed for $trackId: ${e.message}")
            }
        }
        legacyDir.deleteRecursively()
    }

    /** Write a cached track from a temp file into the unified SimpleCache.
     *  Returns true if the file was successfully cached. */
    suspend fun writeCachedTrackFromFile(trackId: String, sourceFile: File, isDownload: Boolean = false): Boolean {
        val size = sourceFile.length()
        if (size == 0L) {
            android.util.Log.w("ftpmusic-cache", "[writeCached] zero-length file for $trackId — deleting")
            sourceFile.delete()
            return false
        }
        if (!importIntoCache(trackId, sourceFile, size)) return false
        if (isDownload) markDownloaded(trackId)

        val spanPath = getSpanFile(trackId)?.absolutePath
        val existing = trackDao.getTrack(trackId)
        val entity = if (existing != null) {
            existing.copy(
                cachedFilePath = spanPath,
                cacheSizeBytes = size.toInt(),
                cachedAt = System.currentTimeMillis(),
                isDownloaded = isDownload || existing.isDownloaded,
                isAutoCached = !isDownload && !existing.isDownloaded,
            )
        } else {
            TrackEntity(
                id = trackId,
                title = trackId,
                cachedFilePath = spanPath,
                cacheSizeBytes = size.toInt(),
                cachedAt = System.currentTimeMillis(),
                isDownloaded = isDownload,
                isAutoCached = !isDownload,
            )
        }
        trackDao.upsert(entity)

        android.util.Log.d("ftpmusic-cache", "[writeCached] $trackId ${size}B isDownload=$isDownload")
        cacheEventFlow.tryEmit(Pair(trackId, isDownload))
        return true
    }

    /** Write track data from memory (test/proxy path). */
    suspend fun writeCachedTrack(trackId: String, data: ByteArray, isDownload: Boolean = false) {
        val tmp = File(tempDirectory, "$trackId.tmp")
        try {
            FileOutputStream(tmp).use { out -> out.write(data) }
        } catch (_: Exception) {
            tmp.delete()
            return
        }
        writeCachedTrackFromFile(trackId, tmp, isDownload)
    }

    /** Moves [sourceFile] into the SimpleCache as a single full span for [trackId]. */
    private fun importIntoCache(trackId: String, sourceFile: File, length: Long): Boolean {
        var holeSpan: CacheSpan? = null
        return try {
            // Never contend with another writer. SimpleCache has no
            // isResourceLocked(), so probe with a non-blocking acquisition.
            // A cached result means position 0 already has data and carries no
            // write lock; a hole result carries the lock and must be released.
            // Null means another writer owns the resource, so defer the import.
            val probe = audioCache.startReadWriteNonBlocking(trackId, 0, length)
            if (probe == null) {
                android.util.Log.w("ftpmusic-cache", "[import] $trackId is locked (streaming?) — deferring import")
                sourceFile.delete()
                return false
            }
            // A partial resource may already cover position 0. In that case
            // SimpleCache returns a cached span, not a lock-bearing hole span;
            // releaseHoleSpan(cachedSpan) throws IllegalStateException.
            if (probe.isHoleSpan) {
                audioCache.releaseHoleSpan(probe)
            }
            // Drop stale/partial spans so we get a hole covering the full resource
            audioCache.removeResource(trackId)
            val span = audioCache.startReadWriteNonBlocking(trackId, 0, length)
            if (span == null || span.isCached) {
                // Another writer holds the lock (or content re-appeared) — abort
                android.util.Log.w("ftpmusic-cache", "[import] cache busy for $trackId — skipping")
                sourceFile.delete()
                return span?.isCached == true
            }
            holeSpan = span
            val mutations = ContentMetadataMutations()
            ContentMetadataMutations.setContentLength(mutations, length)
            audioCache.applyContentMetadataMutations(trackId, mutations)

            val cacheFile = audioCache.startFile(trackId, 0, length)
            if (!sourceFile.renameTo(cacheFile)) {
                // Cross-filesystem fallback
                sourceFile.copyTo(cacheFile, overwrite = true)
                sourceFile.delete()
            }
            audioCache.commitFile(cacheFile, length)
            true
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-cache", "[import] failed for $trackId: ${e::class.simpleName}: ${e.message}")
            sourceFile.delete()
            false
        } finally {
            holeSpan?.let {
                try {
                    audioCache.releaseHoleSpan(it)
                } catch (_: Exception) {}
            }
        }
    }

    private fun markDownloaded(trackId: String) {
        try {
            val mutations = ContentMetadataMutations()
                .set(AdjustableCacheEvictor.METADATA_IS_DOWNLOADED, 1L)
            audioCache.applyContentMetadataMutations(trackId, mutations)
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-cache", "[markDownloaded] $trackId: ${e.message}")
        }
        evictor.pin(trackId)
    }

    /** True if the full resource for [trackId] is present in the unified cache. */
    fun isStoredInCache(trackId: String): Boolean = getSpanFile(trackId) != null

    /** Returns the on-disk span file for a fully cached track, or null. */
    private fun getSpanFile(trackId: String): File? {
        return try {
            val spans = audioCache.getCachedSpans(trackId)
            val span = spans.firstOrNull { it.isCached && it.position == 0L && it.file != null }
                ?: return null
            val contentLength = ContentMetadata.getContentLength(audioCache.getContentMetadata(trackId))
            if (contentLength != C.LENGTH_UNSET.toLong() && span.length < contentLength) return null
            span.file
        } catch (_: Exception) {
            null
        }
    }

    /** Path to the cached audio file (served by the Cast proxy), or null if not fully cached. */
    suspend fun getCachedPath(trackId: String): String? {
        val file = getSpanFile(trackId) ?: return null
        return if (file.exists()) file.absolutePath else null
    }

    suspend fun getTrackEntity(trackId: String): TrackEntity? = trackDao.getTrack(trackId)

    suspend fun getTracksByIds(trackIds: List<String>): List<TrackEntity> = trackDao.getTracksByIds(trackIds)

    fun watchCacheStatus(trackId: String) = trackDao.watchCacheStatus(trackId)

    /** Total bytes stored in the unified cache. */
    fun getTotalBytes(): Long = try {
        audioCache.cacheSpace
    } catch (_: Exception) {
        0L
    }

    /** Bytes held by permanent downloads (pinned entries). */
    fun getDownloadBytes(): Long = try {
        audioCache.keys.sumOf { key ->
            val downloaded = audioCache.getContentMetadata(key)
                .get(AdjustableCacheEvictor.METADATA_IS_DOWNLOADED, 0L) != 0L
            if (downloaded) audioCache.getCachedBytes(key, 0, C.LENGTH_UNSET.toLong()) else 0L
        }
    } catch (_: Exception) {
        0L
    }

    /** Bytes held by LRU-evictable auto-cached entries. */
    fun getAutoCacheBytes(): Long = (getTotalBytes() - getDownloadBytes()).coerceAtLeast(0L)

    suspend fun clearAutoCache() {
        val cached = trackDao.getCachedPaginated(0, Int.MAX_VALUE)
        cached.filter { !it.isDownloaded }.forEach {
            try {
                audioCache.removeResource(it.id)
            } catch (_: Exception) {}
            trackDao.update(it.copy(cachedFilePath = null, cacheSizeBytes = null, isAutoCached = false))
        }
        waveformRepo.deleteByTrackIds(cached.filter { !it.isDownloaded }.map { it.id })
        // Also drop unpinned cache entries that have no Room row (e.g. stream spans)
        try {
            audioCache.keys.toList().forEach { key ->
                val downloaded = audioCache.getContentMetadata(key)
                    .get(AdjustableCacheEvictor.METADATA_IS_DOWNLOADED, 0L) != 0L
                if (!downloaded) audioCache.removeResource(key)
            }
        } catch (_: Exception) {}
    }

    suspend fun clearDownloads() {
        val cached = trackDao.getCachedPaginated(0, Int.MAX_VALUE)
        cached.filter { it.isDownloaded }.forEach {
            try {
                audioCache.removeResource(it.id)
            } catch (_: Exception) {}
            evictor.unpin(it.id)
            trackDao.update(it.copy(cachedFilePath = null, cacheSizeBytes = null, isDownloaded = false))
        }
        waveformRepo.deleteByTrackIds(cached.filter { it.isDownloaded }.map { it.id })
    }

    /** Remove a track's cached content through SimpleCache (keeps the span index
     *  consistent — never delete span files directly) and clear Room metadata.
     *
     *  Downloads are sacred: a pinned download is NEVER removed through this
     *  path. The only caller is the playback error auto-skip (MediaService
     *  onPlayerError); user-initiated deletions go through [clearDownloads].
     *  This guard enforces the local-first contract that downloaded tracks can
     *  only be deleted on explicit user request. */
    suspend fun removeCached(trackId: String) {
        val existing = try {
            trackDao.getTrack(trackId)
        } catch (_: Exception) {
            null
        }
        if (existing?.isDownloaded == true) {
            android.util.Log.i("ftpmusic-cache", "[removeCached] $trackId is a pinned download — refusing to delete")
            return
        }
        try {
            audioCache.removeResource(trackId)
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-cache", "[removeCached] $trackId: ${e.message}")
        }
        evictor.unpin(trackId)
        trackDao.getTrack(trackId)?.let {
            trackDao.update(
                it.copy(
                    cachedFilePath = null,
                    cacheSizeBytes = null,
                    isAutoCached = false,
                    isDownloaded = false,
                ),
            )
        }
        waveformRepo.deleteByTrackIds(listOf(trackId))
    }

    /** Promote an auto-cached track to permanent download (no re-download needed). */
    suspend fun promoteToDownload(trackId: String): Boolean {
        val existing = trackDao.getTrack(trackId) ?: return false
        if (existing.isDownloaded) return true // already downloaded
        if (!existing.isAutoCached) return false // nothing to promote from
        if (!isStoredInCache(trackId)) return false // content already evicted

        markDownloaded(trackId)
        trackDao.update(
            existing.copy(
                isDownloaded = true,
                isAutoCached = false,
            ),
        )
        return true
    }
}
