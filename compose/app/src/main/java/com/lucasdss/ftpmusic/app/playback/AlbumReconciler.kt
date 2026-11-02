package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog
import com.lucasdss.ftpmusic.app.data.repository.AlbumPresence
import com.lucasdss.ftpmusic.app.data.repository.AlbumRepository
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * N11-3 purge guard outcome. [NONE] is the only value that allows the purge.
 * Kept pure so the guard cannot drift from its test.
 */
internal enum class AlbumPurgeBlock {
    NONE,
    DOWNLOADED,
    USER_INTENT,
}

internal fun albumPurgeBlock(
    downloadedTracks: Int,
    starred: Boolean,
    disliked: Boolean,
    pendingUnstar: Boolean,
): AlbumPurgeBlock = when {
    downloadedTracks > 0 -> AlbumPurgeBlock.DOWNLOADED
    starred || disliked || pendingUnstar -> AlbumPurgeBlock.USER_INTENT
    else -> AlbumPurgeBlock.NONE
}

/**
 * N11-3 — reconcile a confirmed server-side album removal.
 *
 * Trigger is the second same-album `REMOTE_MISSING` (N11-2), never the first: a
 * single 404 can come from a transient server/proxy fault. One authenticated
 * `getAlbum` decides; only an explicit Subsonic `code=70` counts as ABSENT.
 *
 * The purge is deliberately narrow (policy B):
 * - an album with any pinned download is never purged (the files would remain
 *   on disk with no library row);
 * - a starred, disliked or pending-unstar album is user intent and is skipped;
 * - only after those guards are the cached album rows removed, orphaned album
 *   tracks pruned, the ledger pruned and the densified corpus reconciled.
 *
 * The album's entries are also removed from the Dual queue and the queue is
 * persisted so a restore does not reinsert them.
 */
@Singleton
class AlbumReconciler @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val trackDao: TrackDao,
    private val metadataDao: CachedMetadataDao,
    private val dualQueue: DualQueueManager,
    private val playbackManager: PlaybackManager,
) {
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastCheckMs = ConcurrentHashMap<String, Long>()

    /** @return true when the album was actually purged. */
    suspend fun reconcileAfterConfirmedMissing(albumId: String): Boolean = withContext(Dispatchers.IO) {
        if (albumId.isBlank()) return@withContext false
        if (!inFlight.add(albumId)) return@withContext false
        try {
            val now = System.currentTimeMillis()
            val last = lastCheckMs[albumId] ?: 0L
            // A cluster of failures can retrigger the same album (shuffled
            // queue). One verification per minute is enough; a transient
            // misconfiguration is re-checked on the next play session.
            if (now - last < RECONCILE_COOLDOWN_MS) return@withContext false
            lastCheckMs[albumId] = now

            val username = SubsonicCredentials.username
            val password = SubsonicCredentials.password
            if (username.isEmpty() || password.isEmpty()) {
                DiagnosticLog.w(
                    "ftpmusic-reconcile",
                    "reconcile albumId=$albumId presence=unknown reason=no_credentials",
                )
                return@withContext false
            }
            val presence = withTimeoutOrNull(VERIFY_TIMEOUT_MS) {
                albumRepository.albumPresence(albumId, username, password)
            } ?: AlbumPresence.UNKNOWN
            if (presence != AlbumPresence.ABSENT) {
                DiagnosticLog.w(
                    "ftpmusic-reconcile",
                    "reconcile albumId=$albumId presence=$presence removed=false",
                )
                return@withContext false
            }

            val downloaded = trackDao.countDownloadedTracksInAlbum(albumId)
            val starred = metadataDao.isAlbumStarred(albumId)
            val disliked = metadataDao.isAlbumDisliked(albumId)
            val pendingUnstar = metadataDao.isAlbumPendingUnstar(albumId)
            when (albumPurgeBlock(downloaded, starred, disliked, pendingUnstar)) {
                AlbumPurgeBlock.DOWNLOADED -> {
                    DiagnosticLog.w(
                        "ftpmusic-reconcile",
                        "reconcile albumId=$albumId removed=false reason=downloaded_present " +
                            "downloaded=$downloaded",
                    )
                    return@withContext false
                }

                AlbumPurgeBlock.USER_INTENT -> {
                    DiagnosticLog.w(
                        "ftpmusic-reconcile",
                        "reconcile albumId=$albumId removed=false reason=album_intent " +
                            "starred=$starred disliked=$disliked pendingUnstar=$pendingUnstar",
                    )
                    return@withContext false
                }

                AlbumPurgeBlock.NONE -> Unit
            }

            val cachedKept = trackDao.countCachedTracksInAlbum(albumId)
            val albumTracks = runCatching { metadataDao.getAlbumTracks(albumId).size }.getOrDefault(0)
            // R5 (audit): a cancelled service scope between the album delete and the
            // orphan prune would leave `cached_album_tracks` rows pointing at a
            // deleted album. The mutation sequence is short and must complete or
            // not start; cancellation is observed after it.
            var orphanedTracks = 0
            var corpus = 0
            var queueEntries = 0
            withContext(NonCancellable) {
                metadataDao.deleteAlbumsByIds(listOf(albumId))
                orphanedTracks = metadataDao.deleteOrphanedAlbumTracks()
                metadataDao.pruneAlbumLedger()
                corpus = trackDao.reconcileSearchCorpusAgainstCatalog()
                queueEntries = dualQueue.removeAllByAlbumId(albumId)
                if (queueEntries > 0) {
                    playbackManager.persistCurrentQueue()
                }
            }
            DiagnosticLog.w(
                "ftpmusic-reconcile",
                "reconcile albumId=$albumId removed=true albumTracks=$albumTracks " +
                    "orphanedTracks=$orphanedTracks corpus=$corpus " +
                    "queueEntries=$queueEntries cachedKept=$cachedKept",
            )
            return@withContext true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.w(
                "ftpmusic-reconcile",
                "reconcile albumId=$albumId removed=false reason=error err=${e.javaClass.simpleName}",
                e,
            )
            return@withContext false
        } finally {
            inFlight.remove(albumId)
        }
    }

    private companion object {
        const val RECONCILE_COOLDOWN_MS = 60_000L
        const val VERIFY_TIMEOUT_MS = 8_000L
    }
}
