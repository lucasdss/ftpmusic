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
        val entity = PlaylistEntity(
            id = pl["id"] as? String ?: playlistId,
            name = pl["name"] as? String ?: "Playlist",
            comment = pl["comment"] as? String,
            owner = pl["owner"] as? String,
            isPublic = (pl["public"] as? Boolean) ?: false,
            trackCount = (pl["songCount"] as? Number)?.toInt() ?: 0,
            coverArt = pl["coverArt"] as? String,
            lastSyncedAt = System.currentTimeMillis(),
            fixedCoverKind = existing?.fixedCoverKind,
            fixedCoverValue = existing?.fixedCoverValue,
        )
        playlistDao.upsertAll(listOf(entity))

        val autoDownload = storage.get(SecureStorage.KEY_AUTO_DOWNLOAD_PLAYLISTS)?.toBooleanStrictOrNull() ?: true
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
                    bitrate = (m["bitRate"] as? Number)?.toInt(),
                    suffix = m["suffix"] as? String,
                    contentType = m["contentType"] as? String,
                    coverArtUrl = m["coverArt"] as? String,
                    path = m["path"] as? String,
                ),
            )
            // Local-first: once imported, the tracks must be downloaded so the
            // playlist is always ready to play (respects the auto-download toggle,
            // mirroring PlaylistDetailViewModel.syncFromServer).
            if (autoDownload) {
                val streamUrl = buildStreamUrl(tid)
                downloadManager.enqueue(tid, streamUrl, priority = 1)
            }
        }
        if (trackEntities.isNotEmpty()) trackDao.upsertAll(trackEntities)
        if (entryEntities.isNotEmpty()) {
            playlistDao.replaceEntries(playlistId, entryEntities)
        } else {
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

    /** Drop local cover files when a playlist is removed. */
    suspend fun onPlaylistDeleted(playlistId: String) {
        val old = playlistDao.getById(playlistId)
        if (old?.fixedCoverKind == CollectionCoverKind.LOCAL) {
            coverStore.deleteRelative(old.fixedCoverValue)
        }
        coverStore.deleteForPrefix(COVER_PREFIX, playlistId)
    }
}
