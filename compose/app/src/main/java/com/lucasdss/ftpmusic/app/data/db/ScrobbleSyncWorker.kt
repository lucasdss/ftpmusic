package com.lucasdss.ftpmusic.app.data.db

import android.util.Log
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drains pending honest [listen_events] to Navidrome via Subsonic scrobble
 * with historical `time` (ADR-0166). Mirrors [PlaylistSyncWorker] backoff.
 */
class ScrobbleSyncWorker(
    private val listenEventDao: ListenEventDao,
    private val api: SubsonicApi,
    private val storage: SecureStorage,
    private val offlineModeManager: OfflineModeManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    companion object {
        private const val TAG = "ftpmusic-scrobble-sync"
        private const val BUSY_INTERVAL_MS = 15_000L
        internal const val IDLE_INTERVAL_MS = 5 * 60_000L
        internal const val BATCH_SIZE = 20
        internal const val MAX_ATTEMPTS = 8
    }

    private val authHelper = SubsonicAuthHelper()
    private var flushJob: Job? = null
    private val flushMutex = Mutex()
    private var consecutiveFailures = 0

    fun start() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            while (true) {
                var pendingEmpty = true
                try {
                    flushMutex.withLock {
                        flushPending()
                        pendingEmpty = listenEventDao
                            .getPendingScrobbles(limit = 1, maxAttempts = MAX_ATTEMPTS)
                            .isEmpty()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Flush cycle failed: ${e.message}", e)
                }
                delay(backoffMs(consecutiveFailures, pendingEmpty))
            }
        }
    }

    fun stop() {
        flushJob?.cancel()
        flushJob = null
    }

    /** Schedule an immediate drain (offline-off / reachability restored). */
    fun flushNow() {
        scope.launch {
            try {
                flushMutex.withLock { flushPending() }
            } catch (e: Exception) {
                Log.w(TAG, "flushNow failed: ${e.message}")
                DiagnosticLog.w(TAG, "flushNow failed: ${e.message}")
            }
        }
    }

    suspend fun flushNowAndAwait(): Boolean {
        return try {
            flushMutex.withLock {
                flushPending()
                if (offlineModeManager.isQueueEnabled()) return@withLock false
                listenEventDao.getPendingScrobbles(limit = 1, maxAttempts = MAX_ATTEMPTS).isEmpty()
            }
        } catch (e: Exception) {
            Log.w(TAG, "flushNowAndAwait failed: ${e.message}")
            false
        }
    }

    private suspend fun flushPending() {
        if (offlineModeManager.isQueueEnabled()) {
            consecutiveFailures = 0
            return
        }
        val username = storage.get(SecureStorage.KEY_USERNAME).orEmpty()
        val password = storage.get(SecureStorage.KEY_PASSWORD).orEmpty()
        if (username.isEmpty() || password.isEmpty()) {
            consecutiveFailures = 0
            return
        }
        val pending = listenEventDao.getPendingScrobbles(
            limit = BATCH_SIZE,
            maxAttempts = MAX_ATTEMPTS,
        )
        if (pending.isEmpty()) {
            consecutiveFailures = 0
            return
        }

        val params = authHelper.buildAuthParams(username, password)
        var anyNetworkFailure = false
        for (event in pending) {
            try {
                val response = api.scrobble(
                    params,
                    id = event.trackId,
                    submission = true,
                    time = event.listenedAt,
                )
                if (!authHelper.checkResponseStatus(response)) {
                    listenEventDao.bumpSyncAttempts(event.id)
                    anyNetworkFailure = true
                    DiagnosticLog.w(
                        TAG,
                        "scrobble status failed id=${event.id} track=${event.trackId}",
                    )
                    continue
                }
                listenEventDao.markSynced(event.id, System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listenEventDao.bumpSyncAttempts(event.id)
                anyNetworkFailure = true
                Log.w(TAG, "scrobble drain fail id=${event.id}: ${e.message}")
                if (isTransientNetwork(e)) {
                    break
                }
            }
        }
        consecutiveFailures = if (anyNetworkFailure) consecutiveFailures + 1 else 0
    }

    private fun isTransientNetwork(e: Exception): Boolean =
        e is IOException || e is SocketTimeoutException || e is UnknownHostException ||
            e.cause is IOException

    internal fun backoffMs(failures: Int, pendingEmpty: Boolean): Long {
        if (pendingEmpty && failures == 0) return IDLE_INTERVAL_MS
        val capped = failures.coerceAtMost(5)
        return BUSY_INTERVAL_MS * (1L shl capped).coerceAtMost(16)
    }
}
