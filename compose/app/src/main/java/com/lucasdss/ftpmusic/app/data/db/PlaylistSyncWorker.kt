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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private val isFlushing = AtomicBoolean(false)
    private var consecutiveFailures = 0
    private var lastCleanupMs = 0L

    fun start() {
        flushJob = scope.launch {
            while (true) {
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    Log.w(TAG, "Max retries reached ($MAX_CONSECUTIVE_FAILURES). Pausing flush loop.")
                    break
                }
                try {
                    flushPending()
                    // Periodic cleanup of old flushed changes
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

    /** Schedule an immediate flush. No-ops if already in progress. */
    fun flushNow() {
        if (!isFlushing.compareAndSet(false, true)) return
        scope.launch {
            try {
                flushPending()
            } catch (
                e: Exception,
            ) {
                Log.w(TAG, "flushNow failed: ${e.message}")
            } finally {
                isFlushing.set(false)
            }
        }
    }

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
            try {
                when (change.changeType) {
                    "rename" -> {
                        val name = extractPayload(change.payload)
                        api.updatePlaylist(params, playlistId = change.playlistId, name = name)
                    }

                    "create" -> {
                        val name = extractPayload(change.payload)
                        val response = api.createPlaylist(params, name = name)
                        val sr = response["subsonic-response"] as? Map<*, *>
                        val pl = sr?.get("playlist") as? Map<*, *>
                        val serverId = pl?.get("id") as? String
                        if (serverId != null && serverId != change.playlistId) {
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
                                    ),
                                ),
                            )
                            // Remap all pending changes that reference the temp ID
                            pendingDao.remapPlaylistId(change.playlistId, serverId)
                        }
                    }

                    "delete" -> {
                        api.deletePlaylist(params, id = change.playlistId)
                        // Remove from local DB after successful server deletion
                        playlistDao.clearEntries(change.playlistId)
                        playlistDao.delete(change.playlistId)
                    }

                    "sync_tracks" -> {
                        // Full track sync: clear server then re-add
                        val entries = playlistDao.getEntries(change.playlistId)
                        val serverList = api.getPlaylist(params, id = change.playlistId)
                        val serverCount = (serverList["songCount"] as? Number)?.toInt() ?: 0
                        val removeIndices = if (serverCount > 0) (0 until serverCount).joinToString(",") else ""
                        val addIds = entries.joinToString(",") { it.trackId }
                        api.updatePlaylist(
                            params,
                            playlistId = change.playlistId,
                            removeIndices = removeIndices,
                            addIds = addIds,
                        )

                        // Update track count in local metadata
                        val updatedMeta = playlistDao.getById(change.playlistId)?.copy(
                            trackCount = entries.size,
                            updatedAt = System.currentTimeMillis(),
                        )
                        if (updatedMeta != null) playlistDao.upsertAll(listOf(updatedMeta))
                    }

                    "add_tracks" -> {
                        val trackIds = change.payload
                        api.updatePlaylist(params, playlistId = change.playlistId, addIds = trackIds)
                    }

                    "remove_tracks" -> {
                        val indices = change.payload
                        api.updatePlaylist(params, playlistId = change.playlistId, removeIndices = indices)
                    }
                }
                pendingDao.markFlushed(change.id)
                updateLastSyncedAt(change.playlistId)
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Timeout flushing ${change.changeType} for ${change.playlistId} — will retry")
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
