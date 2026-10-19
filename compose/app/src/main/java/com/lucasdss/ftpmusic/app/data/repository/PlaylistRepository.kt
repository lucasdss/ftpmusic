package com.lucasdss.ftpmusic.app.data.repository

import android.util.Log
import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverKind
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverStore
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeDao
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorker
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.ui.library.PlaylistView
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PlaylistRepository @Inject constructor(
    private val api: SubsonicApi,
    private val playlistDao: PlaylistDao,
    private val pendingChangeDao: PendingPlaylistChangeDao,
    private val syncWorker: PlaylistSyncWorker,
    private val trackDao: TrackDao,
    private val storage: SecureStorage,
    private val downloadManager: DownloadManager,
    private val coverStore: CollectionCoverStore,
) {
    companion object {
        const val COVER_PREFIX = "pl"
    }
    private val auth = SubsonicAuthHelper()

    private suspend fun authParams(): Map<String, String> {
        val user = storage.get(SecureStorage.KEY_USERNAME) ?: ""
        val pass = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
        return auth.buildAuthParams(user, pass)
    }

    /**
     * Create a new playlist locally and enqueue sync to server.
     * Returns the generated temp ID for immediate UI feedback.
     */
    suspend fun createPlaylist(name: String): String {
        val tempId = "new-${System.currentTimeMillis()}"
        val entity = PlaylistEntity(
            id = tempId,
            name = name,
            trackCount = 0,
            coverArt = null,
            updatedAt = System.currentTimeMillis(),
        )
        playlistDao.upsertAll(listOf(entity))
        pendingChangeDao.insert(
            PendingPlaylistChangeEntity(
                playlistId = tempId,
                changeType = "create",
                payload = "name=$name",
            ),
        )
        syncWorker.flushNow()
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playlist",
            "create playlistId=$tempId",
        )
        return tempId
    }

    /**
     * Import a playlist from the server — saves metadata, entries and track
     * entities locally, and (when `auto_download_playlists` is enabled) enqueues
     * the track downloads so the playlist is ready to play offline. Local-first:
     * the user manually imports/resyncs; Navidrome is only called for listing
     * (getPlaylists) and syncing (getPlaylist).
     */
    suspend fun importPlaylist(playlistId: String) {
        val params = authParams()
        val response = api.getPlaylist(params, id = playlistId)
        if (!auth.checkResponseStatus(response)) return
        val sr = response["subsonic-response"] as? Map<*, *> ?: return
        val pl = sr["playlist"] as? Map<*, *> ?: return

        val existing = playlistDao.getById(playlistId)
        val songCount = (pl["songCount"] as? Number)?.toInt() ?: 0
        val entries = pl["entry"] as? List<*>
        val entryEntities = mutableListOf<PlaylistEntryEntity>()
        val trackEntities = mutableListOf<TrackEntity>()
        entries?.forEachIndexed { index, e ->
            val m = e as? Map<*, *> ?: return@forEachIndexed
            val tid = m["id"] as? String ?: return@forEachIndexed
            entryEntities.add(PlaylistEntryEntity(playlistId = playlistId, trackId = tid, position = index))
            trackEntities.add(
                TrackEntity(
                    id = tid, title = m["title"] as? String ?: tid,
                    artist = m["artist"] as? String,
                    albumId = m["albumId"] as? String,
                    artistId = m["artistId"] as? String,
                    durationSeconds = (m["duration"] as? Number)?.toInt(),
                    trackNumber = (m["track"] as? Number)?.toInt(),
                    discNumber = (m["discNumber"] as? Number)?.toInt(),
                    bitrate = (m["bitRate"] as? Number)?.toInt(),
                    suffix = m["suffix"] as? String,
                    contentType = m["contentType"] as? String,
                    coverArtUrl = m["coverArt"] as? String,
                    path = m["path"] as? String,
                ),
            )
        }
        // Malformed payload (songCount>0 but no parseable entries) — do not wipe local rows.
        if (entryEntities.isEmpty() && songCount > 0) {
            Log.w(
                "ftpmusic-playlist",
                "import abort — songCount=$songCount but entry missing/malformed playlistId=$playlistId",
            )
            return
        }

        val entity = PlaylistEntity(
            id = pl["id"] as? String ?: playlistId,
            name = pl["name"] as? String ?: "Playlist",
            comment = pl["comment"] as? String,
            owner = pl["owner"] as? String,
            isPublic = (pl["public"] as? Boolean) ?: false,
            trackCount = songCount,
            coverArt = pl["coverArt"] as? String,
            lastSyncedAt = System.currentTimeMillis(),
            fixedCoverKind = existing?.fixedCoverKind,
            fixedCoverValue = existing?.fixedCoverValue,
        )
        playlistDao.upsertAll(listOf(entity))

        val autoDownload = storage.get(SecureStorage.KEY_AUTO_DOWNLOAD_PLAYLISTS)?.toBooleanStrictOrNull() ?: true
        // Local-first: once imported, the tracks must be downloaded so the
        // playlist is always ready to play (respects the auto-download toggle,
        // mirroring PlaylistDetailViewModel.syncFromServer).
        if (autoDownload) {
            for (tid in entryEntities.map { it.trackId }) {
                downloadManager.enqueue(tid, buildStreamUrl(tid), priority = 1)
            }
        }
        if (trackEntities.isNotEmpty()) trackDao.upsertTracksPreserveCache(trackEntities)
        if (entryEntities.isNotEmpty()) {
            playlistDao.replaceEntries(playlistId, entryEntities)
        } else {
            // True empty playlist (songCount==0).
            playlistDao.clearEntries(playlistId)
        }
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playlist",
            "import playlistId=$playlistId tracks=${entryEntities.size} autoDownload=$autoDownload",
        )
    }

    private fun buildStreamUrl(trackId: String): String {
        val base = DynamicBaseUrl.url.trimEnd('/')
        val username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
        val password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
        return auth.buildStreamUrl(base, trackId, username, password)
    }

    /**
     * ADR-0139 / 1A: refresh already-imported playlists during metadata sync.
     * Inventory via getPlaylists; intersect with local IDs; skip pending outbound
     * and conflicted playlists. Silent refresh — no auto-download.
     */
    suspend fun refreshImportedPlaylists() {
        val params = authParams()
        val response = try {
            api.getPlaylists(params)
        } catch (e: Exception) {
            Log.w("ftpmusic-playlist", "refreshImportedPlaylists inventory failed: ${e.message}")
            return
        }
        if (!auth.checkResponseStatus(response)) return
        val sr = response["subsonic-response"] as? Map<*, *> ?: return
        val pls = sr["playlists"] as? Map<*, *> ?: return
        val list = pls["playlist"] as? List<*> ?: return
        val serverIds = list.mapNotNull { p ->
            val m = p as? Map<*, *> ?: return@mapNotNull null
            m["id"] as? String
        }.toSet()
        if (serverIds.isEmpty()) return

        val local = try {
            playlistDao.getAll()
        } catch (e: Exception) {
            Log.w("ftpmusic-playlist", "refreshImportedPlaylists local list failed: ${e.message}")
            return
        }
        for (cached in local) {
            if (cached.id.startsWith("new-")) continue
            if (cached.id !in serverIds) continue
            if (cached.isConflicted) continue
            val pending = try {
                pendingChangeDao.pendingCountForPlaylist(cached.id)
            } catch (e: Exception) {
                Log.w("ftpmusic-playlist", "pending check failed for ${cached.id}: ${e.message}")
                continue
            }
            if (pending > 0) continue
            try {
                refreshImportedPlaylistSilent(cached.id, cached)
            } catch (e: Exception) {
                Log.w("ftpmusic-playlist", "refreshImported ${cached.id} failed: ${e.message}")
            }
        }
    }

    /** Persist server playlist meta + entries without enqueueing downloads. */
    private suspend fun refreshImportedPlaylistSilent(playlistId: String, cached: PlaylistEntity) {
        val params = authParams()
        val response = api.getPlaylist(params, id = playlistId)
        if (!auth.checkResponseStatus(response)) return
        val sr = response["subsonic-response"] as? Map<*, *> ?: return
        val pl = sr["playlist"] as? Map<*, *> ?: return
        val songCount = (pl["songCount"] as? Number)?.toInt()
        val entries = pl["entry"] as? List<*>

        // ADR-0139 hardening: re-check outbound pending / conflict after network
        // round-trip so a local edit during fetch is not overwritten.
        val latest = playlistDao.getById(playlistId) ?: cached
        if (latest.isConflicted) {
            Log.d("ftpmusic-playlist", "refreshImported abort — conflicted playlistId=$playlistId")
            return
        }
        val pendingAfter = try {
            pendingChangeDao.pendingCountForPlaylist(playlistId)
        } catch (e: Exception) {
            Log.w("ftpmusic-playlist", "pending re-check failed for $playlistId: ${e.message}")
            return
        }
        if (pendingAfter > 0) {
            Log.d("ftpmusic-playlist", "refreshImported abort — pending outbound playlistId=$playlistId")
            return
        }

        val entryEntities = mutableListOf<PlaylistEntryEntity>()
        val trackEntities = mutableListOf<TrackEntity>()
        entries?.forEachIndexed { index, e ->
            val m = e as? Map<*, *> ?: return@forEachIndexed
            val tid = m["id"] as? String ?: return@forEachIndexed
            entryEntities.add(PlaylistEntryEntity(playlistId = playlistId, trackId = tid, position = index))
            trackEntities.add(
                TrackEntity(
                    id = tid,
                    title = m["title"] as? String ?: tid,
                    artist = m["artist"] as? String,
                    albumId = m["albumId"] as? String,
                    artistId = m["artistId"] as? String,
                    durationSeconds = (m["duration"] as? Number)?.toInt(),
                    trackNumber = (m["track"] as? Number)?.toInt(),
                    discNumber = (m["discNumber"] as? Number)?.toInt(),
                    bitrate = (m["bitRate"] as? Number)?.toInt(),
                    suffix = m["suffix"] as? String,
                    contentType = m["contentType"] as? String,
                    coverArtUrl = m["coverArt"] as? String,
                    path = m["path"] as? String,
                ),
            )
        }
        val effectiveSongCount = songCount ?: latest.trackCount
        // Malformed: claimed tracks but nothing parseable — keep local entries.
        if (entryEntities.isEmpty() && effectiveSongCount > 0) {
            Log.w(
                "ftpmusic-playlist",
                "refreshImported abort — songCount=$effectiveSongCount but entry missing/malformed " +
                    "playlistId=$playlistId",
            )
            return
        }

        val playlistMeta = latest.copy(
            name = pl["name"] as? String ?: latest.name,
            comment = pl["comment"] as? String ?: latest.comment,
            owner = pl["owner"] as? String ?: latest.owner,
            isPublic = (pl["public"] as? Boolean) ?: latest.isPublic,
            trackCount = songCount ?: latest.trackCount,
            coverArt = pl["coverArt"] as? String ?: latest.coverArt,
            lastSyncedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        playlistDao.upsertAll(listOf(playlistMeta))

        if (trackEntities.isNotEmpty()) trackDao.upsertTracksPreserveCache(trackEntities)
        if (entryEntities.isNotEmpty()) {
            playlistDao.replaceEntries(playlistId, entryEntities)
        } else {
            // True empty playlist (songCount==0).
            playlistDao.clearEntries(playlistId)
        }
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playlist",
            "refreshImported playlistId=$playlistId tracks=${entryEntities.size}",
        )
    }

    /**
     * Fetch all server playlists and return only those not yet imported.
     */
    suspend fun loadServerPlaylists(): List<PlaylistView> {
        val params = authParams()
        val response = api.getPlaylists(params)
        if (!auth.checkResponseStatus(response)) return emptyList()
        val sr = response["subsonic-response"] as? Map<*, *> ?: return emptyList()
        val pls = sr["playlists"] as? Map<*, *> ?: return emptyList()
        val list = pls["playlist"] as? List<*> ?: return emptyList()
        val localIds = playlistDao.getAll().map { it.id }.toSet()
        return list.mapNotNull { p ->
            val m = p as? Map<*, *> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            if (id in localIds) return@mapNotNull null
            PlaylistView(
                id = id,
                name = m["name"] as? String ?: "",
                trackCount = (m["songCount"] as? Number)?.toInt() ?: 0,
            )
        }
    }

    /**
     * Add tracks to an existing playlist and enqueue sync.
     * Deduplicates tracks already present.
     */
    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        playlistDao.addTracksToPlaylist(playlistId, trackIds)
        pendingChangeDao.insert(
            PendingPlaylistChangeEntity(
                playlistId = playlistId,
                changeType = "add_tracks",
                payload = trackIds.joinToString(","),
            ),
        )
        syncWorker.flushNow()
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playlist",
            "addToPlaylist playlistId=$playlistId count=${trackIds.size}",
        )
    }

    /**
     * Local-first create + add, then await sync. Returns the server playlist id
     * on success, or null when offline / sync failed (local playlist still kept).
     */
    suspend fun createPlaylistWithTracksSynced(name: String, trackIds: List<String>): String? {
        val tempId = "new-${System.currentTimeMillis()}"
        playlistDao.upsertAll(
            listOf(
                PlaylistEntity(
                    id = tempId,
                    name = name,
                    trackCount = trackIds.size,
                    coverArt = null,
                    updatedAt = System.currentTimeMillis(),
                ),
            ),
        )
        pendingChangeDao.insert(
            PendingPlaylistChangeEntity(
                playlistId = tempId,
                changeType = "create",
                payload = "name=$name",
            ),
        )
        if (trackIds.isNotEmpty()) {
            playlistDao.addTracksToPlaylist(tempId, trackIds)
            pendingChangeDao.insert(
                PendingPlaylistChangeEntity(
                    playlistId = tempId,
                    changeType = "add_tracks",
                    payload = trackIds.joinToString(","),
                ),
            )
        }
        val synced = syncWorker.flushNowAndAwait()
        val serverId = if (synced) {
            syncWorker.consumeIdRemap(tempId)
                ?: playlistDao.getById(tempId)?.id?.takeUnless { it.startsWith("new-") }
        } else {
            null
        }
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playlist",
            "createPlaylistWithTracksSynced tempId=$tempId serverId=$serverId synced=$synced",
        )
        // Only return server id when the full create+add flush succeeded (audit P0).
        return if (synced && serverId != null) serverId else null
    }

    /** Mark playlist public/private on server and mirror [PlaylistEntity.isPublic]. */
    suspend fun setPlaylistPublic(playlistId: String, isPublic: Boolean): Boolean {
        return try {
            val params = authParams()
            val response = api.updatePlaylist(
                params,
                playlistId = playlistId,
                publicFlag = if (isPublic) "true" else "false",
            )
            if (!auth.checkResponseStatus(response)) return false
            playlistDao.getById(playlistId)?.let {
                playlistDao.upsertAll(listOf(it.copy(isPublic = isPublic)))
            }
            true
        } catch (e: Exception) {
            Log.w("ftpmusic-playlist", "setPlaylistPublic failed: ${e.message}")
            false
        }
    }

    /** Pin a local-only fixed cover (library navidrome id or gallery relative path). */
    suspend fun setFixedCover(playlistId: String, kind: String, value: String) {
        if (!CollectionCoverKind.isValid(kind) || value.isBlank()) return
        val old = playlistDao.getById(playlistId) ?: return
        if (old.fixedCoverKind == CollectionCoverKind.LOCAL &&
            old.fixedCoverValue != null &&
            old.fixedCoverValue != value
        ) {
            coverStore.deleteRelative(old.fixedCoverValue)
        }
        playlistDao.updateFixedCover(
            id = playlistId,
            kind = kind,
            value = value,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun clearFixedCover(playlistId: String) {
        val old = playlistDao.getById(playlistId) ?: return
        if (old.fixedCoverKind == CollectionCoverKind.LOCAL) {
            coverStore.deleteRelative(old.fixedCoverValue)
        }
        coverStore.deleteForPrefix(COVER_PREFIX, playlistId)
        playlistDao.updateFixedCover(
            id = playlistId,
            kind = null,
            value = null,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Persist a cropped bitmap as LOCAL fixed cover. Recycles [bitmap].
     * @return relative path or null on failure.
     */
    suspend fun setFixedCoverFromCroppedBitmap(
        playlistId: String,
        bitmap: android.graphics.Bitmap,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val relative = coverStore.importCroppedBitmap(bitmap, COVER_PREFIX, playlistId)
                ?: return@withContext null
            setFixedCover(playlistId, CollectionCoverKind.LOCAL, relative)
            relative
        } finally {
            bitmap.recycle()
        }
    }

    /** Drop local cover files when a playlist is removed. */
    suspend fun onPlaylistDeleted(playlistId: String) {
        val old = playlistDao.getById(playlistId)
        if (old?.fixedCoverKind == CollectionCoverKind.LOCAL) {
            coverStore.deleteRelative(old.fixedCoverValue)
        }
        coverStore.deleteForPrefix(COVER_PREFIX, playlistId)
    }
}
