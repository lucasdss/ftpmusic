package com.lucasdss.ftpmusic.app.data.repository

import android.util.Log
import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
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
) {
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

        val entity = PlaylistEntity(
            id = pl["id"] as? String ?: playlistId,
            name = pl["name"] as? String ?: "Playlist",
            comment = pl["comment"] as? String,
            owner = pl["owner"] as? String,
            isPublic = (pl["public"] as? Boolean) ?: false,
            trackCount = (pl["songCount"] as? Number)?.toInt() ?: 0,
            coverArt = pl["coverArt"] as? String,
            lastSyncedAt = System.currentTimeMillis(),
        )
        playlistDao.upsertAll(listOf(entity))

        val autoDownload = storage.get("auto_download_playlists")?.toBooleanStrictOrNull() ?: true
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
    }
}
