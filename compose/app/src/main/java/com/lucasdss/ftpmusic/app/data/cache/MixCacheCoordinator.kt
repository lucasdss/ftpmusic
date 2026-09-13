package com.lucasdss.ftpmusic.app.data.cache

import com.lucasdss.ftpmusic.app.data.db.CustomMixCacheTrackEntity
import com.lucasdss.ftpmusic.app.data.db.CustomMixDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-Cache Mix ownership (v47). A mix that has `auto_cache = true` asks the
 * DownloadManager (priority 2 → constrained + LRU-evictable) to keep its
 * generated tracks cached. Ownership rows in `custom_mix_cache_tracks` record
 * which mix asked for which track so eviction can skip tracks that:
 *  - another auto-cache mix still owns, or
 *  - the user pinned as an explicit download.
 *
 * Tracks evicted by quota pressure are pruned from ownership on the next touch
 * and re-enqueued when the mix regenerates.
 */
@Singleton
class MixCacheCoordinator @Inject constructor(
    private val customMixDao: CustomMixDao,
    private val trackDao: TrackDao,
    private val cacheService: CacheService,
    private val downloadManager: DownloadManager,
    private val authHelper: SubsonicAuthHelper,
    private val storage: SecureStorage,
) {
    /** A generation added tracks: remember them, enqueue the missing ones. */
    suspend fun onGenerated(mix: CustomMixEntity, trackIds: List<String>) {
        if (!mix.autoCache || trackIds.isEmpty()) return
        pruneStaleOwnership(mix.id)
        val desired = trackIds.toSet()
        // Ownership = "this mix wants this track cached", not "this mix
        // downloaded it". Recording cached-by-others tracks keeps the
        // other-owner set accurate so deleting another mix cannot evict a
        // track this mix still wants.
        customMixDao.upsertCacheTracks(desired.map { CustomMixCacheTrackEntity(customMixId = mix.id, trackId = it) })
        val missing = desired.filter { !cacheService.isStoredInCache(it) }
        enqueue(missing)
    }

    /**
     * Desired set changed (source edit, auto-cache turned on, or generation
     * produced a new tracklist). Evicts exclusives no longer desired; when the
     * mix still has auto-cache on, replaces ownership with [desiredTrackIds]
     * and enqueues anything not already cached.
     */
    suspend fun onSourceChanged(mixId: Long, desiredTrackIds: List<String>) {
        val mix = customMixDao.getById(mixId) ?: return
        if (!mix.autoCache) {
            evictExclusive(mixId)
            customMixDao.deleteAllCacheTracks(mixId)
            return
        }
        val desired = desiredTrackIds.toSet()
        evictOwnedNotIn(mixId, desired)
        customMixDao.deleteAllCacheTracks(mixId)
        if (desired.isEmpty()) return
        customMixDao.upsertCacheTracks(desired.map { CustomMixCacheTrackEntity(customMixId = mixId, trackId = it) })
        val missing = desired.filter { !cacheService.isStoredInCache(it) }
        enqueue(missing)
    }

    /** Auto-cache turned off: drop ownership and evict exclusives. */
    suspend fun onDisabled(mixId: Long) {
        evictExclusive(mixId)
        customMixDao.deleteAllCacheTracks(mixId)
    }

    /** Mix deleted: evict exclusively-owned cached tracks (downloads survive). */
    suspend fun onDeleted(mixId: Long) {
        evictExclusive(mixId)
        customMixDao.deleteAllCacheTracks(mixId)
    }

    /** Drop ownership rows whose cache files were evicted by quota pressure. */
    private suspend fun pruneStaleOwnership(mixId: Long) {
        val stale = customMixDao.getOwnedTrackIds(mixId).filter { !cacheService.isStoredInCache(it) }
        if (stale.isNotEmpty()) customMixDao.deleteCacheTracks(mixId, stale)
    }

    private suspend fun evictExclusive(mixId: Long) {
        val owned = customMixDao.getOwnedTrackIds(mixId)
        if (owned.isEmpty()) return
        evict(mixId, owned)
    }

    private suspend fun evictOwnedNotIn(mixId: Long, desired: Set<String>) {
        val owned = customMixDao.getOwnedTrackIds(mixId)
        if (owned.isEmpty()) return
        evict(mixId, owned.filter { it !in desired })
    }

    private suspend fun evict(mixId: Long, candidates: List<String>) {
        if (candidates.isEmpty()) return
        val otherOwned = customMixDao.getTrackIdsOwnedByOtherMixes(mixId).toSet()
        candidates.filter { it !in otherOwned }.forEach { trackId ->
            if (trackDao.getTrack(trackId)?.isDownloaded == true) return@forEach
            cacheService.removeCached(trackId)
        }
    }

    private suspend fun enqueue(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        // Match every other call site: the runtime-resolved server URL (ADR
        // 0033) rather than the persisted copy, which can be stale or missing.
        val serverUrl = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        if (serverUrl.isBlank()) return
        val username = storage.get(SecureStorage.KEY_USERNAME) ?: return
        val password = storage.get(SecureStorage.KEY_PASSWORD) ?: return
        trackIds.forEach { trackId ->
            downloadManager.enqueue(
                trackId,
                authHelper.buildStreamUrl(serverUrl, trackId, username, password),
                priority = 2,
            )
        }
    }
}
