package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.util.Log
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background worker that flushes pending playlist changes to the Subsonic server.
 *
 * Implements the CONTEXT.md local-first design: mutations write to
 * pending_playlist_changes immediately, this worker flushes to server.
 */
class PlaylistSyncWorker(
    private val context: Context,
    private val pendingDao: PendingPlaylistChangeDao,
    private val playlistDao: PlaylistDao,
    private val api: SubsonicApi,
    private val authHelper: SubsonicAuthHelper,
    private val offlineModeManager: OfflineModeManager,
    /** Coroutine scope. Uses Dispatchers.Main by default so tests can
     *  override via Dispatchers.setMain(testDispatcher). */
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    companion object {
        private const val TAG = "ftpmusic-sync"
        private const val FLUSH_INTERVAL_MS = 15_000L
        private const val MAX_CONSECUTIVE_FAILURES = 10
        private const val CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private var flushJob: Job? = null
    private val flushMutex = Mutex()
    private var consecutiveFailures = 0
    private var lastCleanupMs = 0L

    /** temp playlist id → server id after a successful create flush (ADR-0076). */
    private val idRemaps = ConcurrentHashMap<String, String>()

    fun start() {
        flushJob = scope.launch {
            while (true) {
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    Log.w(TAG, "Max retries reached ($MAX_CONSECUTIVE_FAILURES). Pausing flush loop.")
                    break
                }
                try {
                    flushMutex.withLock { flushPending() }
                    cleanupFlushedIfNeeded()
                } catch (e: Exception) {
                    Log.w(TAG, "Flush cycle failed: ${e.message}", e)
                }
                val delayMs = calculateBackoff(consecutiveFailures)
                delay(delayMs)
            }
        }
    }

    fun stop() {
        flushJob?.cancel()
    }

    /** Schedule an immediate flush on the worker scope. */
    fun flushNow() {
        scope.launch {
            try {
                flushMutex.withLock { flushPending() }
            } catch (e: Exception) {
                Log.w(TAG, "flushNow failed: ${e.message}")
                com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
                    "ftpmusic-playlist",
                    "flushNow failed",
                    e,
                )
            }
        }
    }

    /**
     * Flush pending changes and wait. Returns true when the queue is empty after
     * the flush (sync succeeded or nothing to do). False when offline or pending remain.
     */
    suspend fun flushNowAndAwait(): Boolean {
        return try {
            flushMutex.withLock {
                flushPending()
                if (offlineModeManager.isOfflineEnabled()) return@withLock false
                pendingDao.getPending().isEmpty()
            }
        } catch (e: Exception) {
            Log.w(TAG, "flushNowAndAwait failed: ${e.message}")
            false
        }
    }

    /** Consume a temp→server remap produced by the last create flush. */
    fun consumeIdRemap(tempId: String): String? = idRemaps.remove(tempId)

    private suspend fun flushPending() {
        // Software offline mode: hold all pending changes locally; they are
        // flushed when the user toggles offline off or the process restarts.
        if (offlineModeManager.isOfflineEnabled()) return
        val pending = pendingDao.getPending()
        if (pending.isEmpty()) {
            consecutiveFailures = 0 // no work = success
            return
        }

        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isEmpty() || password.isEmpty()) return

        val params = authHelper.buildAuthParams(username, password)
        var anyNetworkFailure = false

        for (change in pending) {
            // Resolve temp→server remaps from earlier creates in this same flush pass.
            val playlistId = idRemaps[change.playlistId] ?: change.playlistId
            try {
                when (change.changeType) {
                    "rename" -> {
                        val name = extractPayload(change.payload)
                        val response = api.updatePlaylist(params, playlistId = playlistId, name = name)
                        if (!authHelper.checkResponseStatus(response)) {
                            Log.w(TAG, "rename failed status for $playlistId")
                            anyNetworkFailure = true
                            continue
                        }
                    }

                    "create" -> {
                        val name = extractPayload(change.payload)
                        val response = api.createPlaylist(params, name = name)
                        if (!authHelper.checkResponseStatus(response)) {
                            Log.w(TAG, "create failed status for ${change.playlistId}")
                            anyNetworkFailure = true
                            continue
                        }
                        val sr = response["subsonic-response"] as? Map<*, *>
                        val pl = sr?.get("playlist") as? Map<*, *>
                        val serverId = pl?.get("id") as? String
                        if (serverId != null && serverId != change.playlistId) {
                            // Preserve local fixed cover across temp→server id remap.
                            val prior = playlistDao.getById(change.playlistId)
                            // Replace temp local ID with server-assigned ID
                            playlistDao.delete(change.playlistId)
                            // Re-point entries written under the temp ID (tracks added
                            // before this flush) so they are not orphaned.
                            playlistDao.remapEntries(change.playlistId, serverId)
                            val entryCount = playlistDao.getEntries(serverId).size
                            playlistDao.upsertAll(
                                listOf(
                                    PlaylistEntity(
                                        id = serverId,
                                        name = pl["name"] as? String ?: name,
                                        trackCount = entryCount.coerceAtLeast(
                                            (pl["songCount"] as? Number)?.toInt() ?: 0,
                                        ),
                                        coverArt = pl["coverArt"] as? String,
                                        fixedCoverKind = prior?.fixedCoverKind,
                                        fixedCoverValue = prior?.fixedCoverValue,
                                    ),
                                ),
                            )
                            // Remap all pending changes that reference the temp ID
                            pendingDao.remapPlaylistId(change.playlistId, serverId)
                            idRemaps[change.playlistId] = serverId
                        } else if (serverId != null) {
                            idRemaps[change.playlistId] = serverId
                        }
                    }

                    "delete" -> {
                        val response = api.deletePlaylist(params, id = playlistId)
                        if (!authHelper.checkResponseStatus(response)) {
                            Log.w(TAG, "delete failed status for $playlistId")
                            anyNetworkFailure = true
                            continue
                        }
                        // Remove from local DB after successful server deletion
                        playlistDao.clearEntries(playlistId)
                        playlistDao.delete(playlistId)
                    }

                    "sync_tracks" -> {
                        // Full track sync: clear server then re-add
                        val entries = playlistDao.getEntries(playlistId)
                        val serverList = api.getPlaylist(params, id = playlistId)
                        if (!authHelper.checkResponseStatus(serverList)) {
                            Log.w(TAG, "sync_tracks getPlaylist failed status for $playlistId")
                            anyNetworkFailure = true
                            continue
                        }
                        val serverCount = (serverList["songCount"] as? Number)?.toInt() ?: 0
                        val removeIndices = if (serverCount > 0) (0 until serverCount).joinToString(",") else ""
                        val addIds = entries.joinToString(",") { it.trackId }
                        val response = api.updatePlaylist(
                            params,
                            playlistId = playlistId,
                            removeIndices = removeIndices,
                            addIds = addIds,
                        )
                        if (!authHelper.checkResponseStatus(response)) {
                            Log.w(TAG, "sync_tracks update failed status for $playlistId")
                            anyNetworkFailure = true
                            continue
                        }

                        // Update track count in local metadata
                        val updatedMeta = playlistDao.getById(playlistId)?.copy(
                            trackCount = entries.size,
                            updatedAt = System.currentTimeMillis(),
                        )
                        if (updatedMeta != null) playlistDao.upsertAll(listOf(updatedMeta))
                    }

                    "add_tracks" -> {
                        val trackIds = change.payload
                        val response = api.updatePlaylist(params, playlistId = playlistId, addIds = trackIds)
                        if (!authHelper.checkResponseStatus(response)) {
                            Log.w(TAG, "add_tracks failed status for $playlistId")
                            anyNetworkFailure = true
                            continue
                        }
                    }

                    "remove_tracks" -> {
                        val indices = change.payload
                        val response =
                            api.updatePlaylist(params, playlistId = playlistId, removeIndices = indices)
                        if (!authHelper.checkResponseStatus(response)) {
                            Log.w(TAG, "remove_tracks failed status for $playlistId")
                            anyNetworkFailure = true
                            continue
                        }
                    }
                }
                pendingDao.markFlushed(change.id)
                updateLastSyncedAt(idRemaps[change.playlistId] ?: playlistId)
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Timeout flushing ${change.changeType} for ${change.playlistId} — will retry")
                com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
                    "ftpmusic-playlist",
                    "flush timeout change=${change.changeType} playlistId=${change.playlistId}",
                )
                anyNetworkFailure = true
            } catch (e: UnknownHostException) {
                Log.w(TAG, "DNS resolution failed for ${change.changeType} — will retry")
                anyNetworkFailure = true
            } catch (e: IOException) {
                Log.w(
                    TAG,
                    "Network error flushing ${change.changeType} for ${change.playlistId}: ${e.message} — will retry",
                )
                anyNetworkFailure = true
            } catch (e: Exception) {
                // Permanent error (server rejection, parse error) — mark flushed with conflict
                Log.w(TAG, "Failed to flush ${change.changeType} for ${change.playlistId}: ${e.message}")
                com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
                    "ftpmusic-playlist",
                    "flush fail change=${change.changeType} playlistId=${change.playlistId}",
                    e,
                )
                pendingDao.markFlushed(change.id)
                val current = playlistDao.getById(change.playlistId)
                if (current != null) {
                    playlistDao.upsertAll(
                        listOf(
                            current.copy(
                                isConflicted = true,
                                conflictMessage = "Server rejected ${change.changeType}: ${e.message}",
                            ),
                        ),
                    )
                }
            }
        }
        // Update backoff counter
        if (anyNetworkFailure) consecutiveFailures++ else consecutiveFailures = 0

        // Track playlist sync stats when changes were flushed
        if (!anyNetworkFailure && pending.isNotEmpty()) {
            val playlistCount = playlistDao.count()
            val prefs = context.getSharedPreferences(MetadataSyncWorker.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("last_playlist_sync_ms", System.currentTimeMillis())
                .putInt("playlist_count", playlistCount)
                .apply()
        }
    }

    private fun calculateBackoff(failures: Int): Long = when {
        failures == 0 -> FLUSH_INTERVAL_MS

        failures <= 3 -> 60_000L

        // 1 minute
        failures <= 6 -> 5 * 60_000L

        // 5 minutes
        else -> 30 * 60_000L // 30 minutes
    }

    private suspend fun cleanupFlushedIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastCleanupMs < CLEANUP_INTERVAL_MS) return
        lastCleanupMs = now
        try {
            val cutoff = now - 7 * 24 * 60 * 60 * 1000L // 7 days
            pendingDao.deleteFlushedOlderThan(cutoff)
            Log.d(TAG, "Cleaned up flushed pending changes older than 7 days")
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup failed: ${e.message}")
        }
    }

    private fun extractPayload(payload: String): String {
        val parts = payload.split("=", limit = 2)
        return if (parts.size == 2) parts[1] else payload
    }

    private suspend fun updateLastSyncedAt(playlistId: String) {
        val existing = playlistDao.getById(playlistId) ?: return
        playlistDao.upsertAll(listOf(existing.copy(lastSyncedAt = System.currentTimeMillis())))
    }
}
