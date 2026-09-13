@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_IS_NOT_ENABLED")

package com.lucasdss.ftpmusic.app.data.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.lucasdss.ftpmusic.app.data.db.CacheQueueDao
import com.lucasdss.ftpmusic.app.data.db.CacheQueueItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import okio.Buffer

@Singleton
class DownloadManager @Inject constructor(
    private val cacheQueueDao: CacheQueueDao,
    private val cacheService: CacheService,
    private val offlineModeManager: OfflineModeManager,
    @ApplicationContext private val context: Context,
) {
    companion object {
        /** Allow downloads on mobile data. Default: true. Toggled from Settings. */
        @Volatile var allowMobileData: Boolean = true
    }
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private val downloadSemaphore = Semaphore(2)
    private val connectivity by lazy { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }

    private val downloadClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectionPool(okhttp3.ConnectionPool(2, 30, TimeUnit.SECONDS))
            .build()
    }

    fun start() {
        if (isRunning) return
        // stop() cancels the scope permanently — recreate it so restart works
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        isRunning = true
        // Reset stuck "processing" items to "pending" from previous unclean stop
        scope.launch {
            try {
                cacheQueueDao.resetProcessingToPending()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-download", "Failed: " + e.message)
            }
            sweepStaleTempFiles()
        }
        scope.launch { workerLoop() }
    }

    /** Delete *.tmp files older than 1 hour left behind by crashed downloads. */
    private fun sweepStaleTempFiles() {
        try {
            val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
            cacheService.tempDirectory.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".tmp") && file.lastModified() < cutoff) {
                    android.util.Log.d("ftpmusic-download", "[sweep] deleting stale temp file ${file.name}")
                    file.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-download", "Sweep failed: " + e.message)
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
    }

    /** Enqueue with dedup. Priority: 0=play-queue (urgent), 1=download, 2=cache-warming.
     *
     *  Atomicity: the insert uses INSERT OR IGNORE on the unique track_id index,
     *  so two concurrent enqueues for the same track can never produce two rows
     *  (which previously caused two workers to write the same temp file).
     *
     *  Completed rows that are still stored in the cache are never reset to
     *  "pending" — re-enqueueing a cached track (e.g. the play-queue prefetch
     *  firing on every track advance) must not trigger a re-download. */
    suspend fun enqueue(trackId: String, url: String, priority: Int = 2) {
        val isDownload = priority == 1
        val existing = cacheQueueDao.getByTrackId(trackId)
        if (existing != null && existing.status in listOf("pending", "processing")) {
            // Upgrade priority if new request is higher
            if (priority < existing.priority) {
                cacheQueueDao.updatePriority(existing.id, priority)
            }
            // Upgrade to download even when priority stays (e.g. play-queue item at 0)
            if (isDownload && !existing.isDownload) {
                cacheQueueDao.markAsDownload(existing.id)
            }
            return
        }
        // Re-enqueue failed/completed items by resetting status
        if (existing != null) {
            // Completed as auto-cache and now requested as download → promote
            // in place, no re-download needed
            if (isDownload && existing.status == "completed" &&
                cacheService.promoteToDownload(trackId)
            ) {
                cacheQueueDao.markAsDownload(existing.id)
                return
            }
            // Completed and still on disk → no-op. The play-queue prefetch fires
            // on every track advance; without this guard every cached queue track
            // would be fully re-downloaded on each advance (cache thrash).
            if (existing.status == "completed" && !isDownload &&
                cacheService.isStoredInCache(trackId)
            ) {
                if (priority < existing.priority) {
                    cacheQueueDao.updatePriority(existing.id, priority)
                }
                return
            }
            // "failed" is terminal (the worker gave up after its retry cap).
            // Do NOT resurrect it: resetting it to pending here turns every
            // re-enqueue (play-queue prefetch, cache warming, playlist sync)
            // into an infinite download-retry loop that pegs CPU/disk and
            // starves the rest of the app. Only an explicit new request
            // (enqueue after the row was cleared) re-attempts a download.
            if (existing.status == "failed") return
            cacheQueueDao.updateStatus(existing.id, "pending")
            if (priority < existing.priority) {
                cacheQueueDao.updatePriority(existing.id, priority)
            }
            if (isDownload && !existing.isDownload) {
                cacheQueueDao.markAsDownload(existing.id)
            }
            return
        }
        val inserted = cacheQueueDao.insertIgnore(
            CacheQueueItemEntity(
                trackId = trackId,
                remoteUrl = url,
                priority = priority,
                isDownload = isDownload,
            ),
        )
        if (inserted == -1L) {
            // A concurrent enqueue won the race — apply our upgrades to that row.
            cacheQueueDao.getByTrackId(trackId)?.let { winner ->
                if (priority < winner.priority) cacheQueueDao.updatePriority(winner.id, priority)
                if (isDownload && !winner.isDownload) cacheQueueDao.markAsDownload(winner.id)
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun workerLoop() {
        while (isRunning) {
            // Priority 0 (play-queue urgent window) always first — but offline
            // mode blocks it too: no network bytes may leave the device while
            // the user has offline toggled on (local-first contract).
            var item = if (offlineModeManager.isOfflineEnabled()) {
                null
            } else {
                cacheQueueDao.getNextPendingByPriority(0)
            }
            // If no play-queue items and constraints satisfied, try 1 then 2
            if (item == null && checkConstraints()) {
                item = cacheQueueDao.getNextPendingByPriority(1)
                    ?: cacheQueueDao.getNextPendingByPriority(2)
            }
            if (item == null) {
                delay(1000)
                continue
            }

            cacheQueueDao.updateStatus(item.id, "processing")
            // Capture the value: the loop variable is reassigned on the next
            // iteration — launching with `item!!` would let a fast loop hand
            // coroutines the WRONG row (stale or advanced item).
            val itemToDownload = item
            scope.launch {
                downloadSemaphore.acquire()
                try {
                    downloadItem(itemToDownload)
                } finally {
                    downloadSemaphore.release()
                }
            }
        }
    }

    private fun checkConstraints(): Boolean {
        // Software offline mode blocks all downloads — local-first contract.
        if (offlineModeManager.isOfflineEnabled()) return false
        try {
            val net = connectivity.activeNetwork ?: return false
            val caps = connectivity.getNetworkCapabilities(net) ?: return false
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
            // Wi-Fi or Ethernet required, unless mobile data is allowed
            if (!allowMobileData && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                return false
            }
        } catch (_: Exception) {
            return false
        }
        // Battery check
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level < 20 && !bm.isCharging) return false
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-download", "Failed: " + e.message)
        }
        return true
    }

    private suspend fun downloadItem(item: CacheQueueItemEntity) {
        // Row id in the temp name: even though track_id is unique, two retries
        // of the same row must never share a file mid-write.
        val tmpFile = java.io.File(cacheService.tempDirectory, "${item.trackId}.${item.id}.tmp")
        try {
            val request = okhttp3.Request.Builder().url(item.remoteUrl).build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    handleRetry(item)
                    return
                }
                val body = response.body
                if (body == null) {
                    // Don't leave the item stuck in "processing"
                    handleRetry(item)
                    return
                }
                val totalLen = body.contentLength()
                cacheQueueDao.updateProgress(item.id, 0, totalLen)

                var downloaded = 0L
                java.io.FileOutputStream(tmpFile).use { out ->
                    val source = body.source()
                    val buf = Buffer()
                    while (source.read(buf, 8192) != -1L) {
                        val chunk = buf.readByteArray()
                        out.write(chunk)
                        downloaded += chunk.size
                        if (downloaded % (256 * 1024) < 8192) {
                            cacheQueueDao.updateProgress(item.id, downloaded, totalLen)
                        }
                    }
                }
                cacheQueueDao.updateProgress(item.id, downloaded, totalLen)
                // Re-read isDownload — user may have tapped Download while transfer was in flight
                val isDownloadNow = item.isDownload ||
                    (cacheQueueDao.getByTrackId(item.trackId)?.isDownload ?: false)
                val success = cacheService.writeCachedTrackFromFile(
                    item.trackId,
                    tmpFile,
                    isDownload = isDownloadNow,
                )
                if (success) {
                    cacheQueueDao.updateStatus(item.id, "completed")
                    android.util.Log.d("ftpmusic-download", "[worker] completed: ${item.trackId} (${downloaded}B)")
                } else {
                    android.util.Log.w(
                        "ftpmusic-download",
                        "[worker] cache write failed for ${item.trackId} — will retry",
                    )
                    handleRetry(item)
                }
            }
        } catch (e: Exception) {
            handleRetry(item)
        } finally {
            // writeCachedTrackFromFile consumes the file on success — anything
            // left behind here is a failed/partial download
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    private suspend fun handleRetry(item: CacheQueueItemEntity) {
        val nextRetry = item.retryCount + 1
        cacheQueueDao.updateRetryCount(item.id, nextRetry)
        if (nextRetry < 3) {
            // Exponential backoff: 1s, 2s, 4s max
            if (nextRetry > 0) {
                delay(minOf(1000L * (1 shl (nextRetry - 1)), 8000L))
            }
            // Reset to pending — worker loop picks up on next poll (1s)
            // Retry count prevents infinite loops
            cacheQueueDao.updateStatus(item.id, "pending")
        } else {
            cacheQueueDao.updateStatus(item.id, "failed")
        }
    }
}
