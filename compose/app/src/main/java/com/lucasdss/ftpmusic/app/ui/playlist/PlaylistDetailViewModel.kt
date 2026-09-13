package com.lucasdss.ftpmusic.app.ui.playlist

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.db.CacheQueueDao
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeDao
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorker
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaylistDetailState(
    val playlist: PlaylistEntity? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val isConflicted: Boolean = false,
    val conflictMessage: String? = null,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val api: SubsonicApi,
    private val playlistDao: PlaylistDao,
    private val pendingChangeDao: PendingPlaylistChangeDao,
    private val syncWorker: PlaylistSyncWorker,
    private val trackDao: TrackDao,
    private val playbackManager: PlaybackManager,
    private val cacheService: CacheService,
    private val downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager,
    private val cacheQueueDao: CacheQueueDao,
    private val storage: SecureStorage,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistDetailState())
    val state: StateFlow<PlaylistDetailState> = _state.asStateFlow()

    private val auth = SubsonicAuthHelper()
    private var username: String = ""
    private var password: String = ""

    fun loadPlaylist(playlistId: String) {
        if (playlistId.isBlank()) {
            _state.value = _state.value.copy(error = "Invalid playlist ID", isLoading = false)
            return
        }
        username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
        password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
        viewModelScope.launch {
            if (_state.value.isLoading) return@launch
            _state.value = _state.value.copy(isLoading = true)
            try {
                // Load from local database first
                val cached = playlistDao.getById(playlistId)
                if (cached != null) {
                    _state.value = _state.value.copy(
                        playlist = cached,
                        isConflicted = cached.isConflicted,
                        conflictMessage = cached.conflictMessage,
                    )
                }
                // Load tracks from database
                val entries = playlistDao.getEntries(playlistId)
                if (entries.isNotEmpty()) {
                    val trackIds = entries.map { it.trackId }
                    val trackEntities = trackDao.getTracksByIds(trackIds)
                    val trackMap = trackEntities.associateBy { it.id }
                    val tracks = entries.asSequence().mapNotNull { entry ->
                        trackMap[entry.trackId]?.let { entity ->
                            Track(
                                id = entity.id, title = entity.title,
                                artist = entity.artist, album = null,
                                albumId = entity.albumId, artistId = entity.artistId,
                                duration = entity.durationSeconds, trackNumber = entity.trackNumber,
                                coverArt = entity.coverArtUrl, suffix = entity.suffix,
                                contentType = entity.contentType,
                                userRating = entity.userRating,
                            )
                        }
                    }.distinctBy { it.id }.toList()
                    _state.value = _state.value.copy(tracks = tracks, isLoading = false)
                } else {
                    _state.value = _state.value.copy(isLoading = false)
                }
                // Background refresh from server (updates metadata + entries)
                refreshFromServer(playlistId, cached)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to load playlist")
            }
        }
    }

    private suspend fun refreshFromServer(playlistId: String, cached: PlaylistEntity?) {
        try {
            val authParams = auth.buildAuthParams(username, password)
            val response = api.getPlaylist(authParams, id = playlistId)
            if (!auth.checkResponseStatus(response)) return
            val sr = response["subsonic-response"] as? Map<*, *> ?: return
            val pl = sr["playlist"] as? Map<*, *> ?: return
            val entries = pl["entry"] as? List<*>

            val playlistMeta = cached?.copy(
                name = pl["name"] as? String ?: cached.name,
                trackCount = (pl["songCount"] as? Number)?.toInt() ?: cached.trackCount,
                coverArt = pl["coverArt"] as? String ?: cached.coverArt,
                isConflicted = false,
                conflictMessage = null,
                lastSyncedAt = System.currentTimeMillis(),
            ) ?: PlaylistEntity(
                id = pl["id"] as? String ?: playlistId,
                name = pl["name"] as? String ?: "Playlist",
                owner = pl["owner"] as? String,
                isPublic = (pl["public"] as? Boolean) ?: false,
                trackCount = (pl["songCount"] as? Number)?.toInt() ?: 0,
                coverArt = pl["coverArt"] as? String,
                lastSyncedAt = System.currentTimeMillis(),
            )

            val tracks = entries?.mapNotNull { e ->
                val m = e as? Map<*, *> ?: return@mapNotNull null
                val trackId = m["id"] as? String ?: return@mapNotNull null
                Track(
                    id = trackId, title = m["title"] as? String ?: "Unknown",
                    artist = m["artist"] as? String, album = m["album"] as? String,
                    albumId = m["albumId"] as? String, duration = (m["duration"] as? Number)?.toInt(),
                    trackNumber = (m["track"] as? Number)?.toInt(), coverArt = m["coverArt"] as? String,
                    suffix = m["suffix"] as? String, contentType = m["contentType"] as? String,
                    userRating = (m["userRating"] as? Number)?.toInt(),
                )
            } ?: emptyList()

            // Persist to database
            playlistDao.upsertAll(listOf(playlistMeta))
            playlistDao.clearEntries(playlistId)
            playlistDao.upsertEntries(
                tracks.mapIndexed { idx, t ->
                    PlaylistEntryEntity(playlistId = playlistId, trackId = t.id, position = idx)
                },
            )
            // Batch save track entities
            val trackEntities = tracks.map { t ->
                com.lucasdss.ftpmusic.app.data.db.TrackEntity(
                    id = t.id, title = t.title, artist = t.artist,
                    albumId = t.albumId,
                    durationSeconds = t.duration, trackNumber = t.trackNumber,
                    coverArtUrl = t.coverArt, suffix = t.suffix,
                    contentType = t.contentType,
                )
            }
            if (trackEntities.isNotEmpty()) trackDao.upsertAll(trackEntities)

            // Update UI with fresh server data, clearing any conflict state
            _state.value = _state.value.copy(
                playlist = playlistMeta,
                tracks = tracks.distinctBy { it.id },
                isLoading = false,
                error = null,
                isConflicted = false,
                conflictMessage = null,
            )
        } catch (_: Exception) {
            android.util.Log.w("ftpmusic-playlist", "Background refresh failed for $playlistId")
        }
    }

    /** Sync playlist tracks from server — upserts tracks, optionally downloads. */
    fun syncFromServer() {
        val playlistId = _state.value.playlist?.id ?: return
        viewModelScope.launch {
            if (_state.value.isSyncing) return@launch
            _state.value = _state.value.copy(isSyncing = true)
            try {
                val autoDownload = storage.get("auto_download_playlists")?.toBooleanStrictOrNull() ?: true
                val authParams = auth.buildAuthParams(username, password)
                val response = api.getPlaylist(authParams, id = playlistId)
                if (!auth.checkResponseStatus(response)) {
                    _state.value = _state.value.copy(isSyncing = false, error = auth.getResponseError(response))
                    return@launch
                }
                val sr = response["subsonic-response"] as? Map<*, *>
                val pl = sr?.get("playlist") as? Map<*, *>
                val entries = pl?.get("entry") as? List<*>

                val tracks = mutableListOf<Track>()
                val trackEntities = mutableListOf<com.lucasdss.ftpmusic.app.data.db.TrackEntity>()
                entries?.forEachIndexed { index, e ->
                    val m = e as? Map<*, *> ?: return@forEachIndexed
                    val trackId = m["id"] as? String ?: return@forEachIndexed
                    trackEntities.add(
                        com.lucasdss.ftpmusic.app.data.db.TrackEntity(
                            id = trackId,
                            title = m["title"] as? String ?: "Unknown",
                            artist = m["artist"] as? String,
                            albumId = m["albumId"] as? String,
                            artistId = m["artistId"] as? String,
                            coverArtUrl = m["coverArt"] as? String,
                            durationSeconds = (m["duration"] as? Number)?.toInt(),
                            trackNumber = (m["track"] as? Number)?.toInt(),
                            bitrate = (m["bitRate"] as? Number)?.toInt(),
                            suffix = m["suffix"] as? String,
                            contentType = m["contentType"] as? String,
                            path = m["path"] as? String,
                        ),
                    )
                    tracks.add(
                        Track(
                            id = trackId,
                            title = m["title"] as? String ?: "Unknown",
                            artist = m["artist"] as? String,
                            artistId = m["artistId"] as? String,
                            album = m["album"] as? String,
                            albumId = m["albumId"] as? String,
                            duration = (m["duration"] as? Number)?.toInt(),
                            trackNumber = (m["track"] as? Number)?.toInt(),
                            bitrate = (m["bitRate"] as? Number)?.toInt(),
                            suffix = m["suffix"] as? String,
                            contentType = m["contentType"] as? String,
                            path = m["path"] as? String,
                            coverArt = m["coverArt"] as? String,
                            userRating = (m["userRating"] as? Number)?.toInt(),
                        ),
                    )
                    if (autoDownload) {
                        val streamUrl = buildStreamUrl(trackId)
                        downloadManager.enqueue(trackId, streamUrl, priority = 1)
                    }
                }
                // Batch save track entities
                if (trackEntities.isNotEmpty()) trackDao.upsertAll(trackEntities)
                // Persist entries for offline access
                playlistDao.clearEntries(playlistId)
                val entryEntities = tracks.mapIndexed { index, track ->
                    PlaylistEntryEntity(playlistId = playlistId, trackId = track.id, position = index)
                }
                playlistDao.upsertEntries(entryEntities)
                // Persist updated metadata from server
                val cachedMeta = playlistDao.getById(playlistId)
                val updatedMeta = (cachedMeta ?: PlaylistEntity(id = playlistId, name = "Playlist")).copy(
                    name = pl?.get("name") as? String ?: cachedMeta?.name ?: "Playlist",
                    comment = pl?.get("comment") as? String ?: cachedMeta?.comment,
                    owner = pl?.get("owner") as? String ?: cachedMeta?.owner,
                    trackCount = tracks.size,
                    coverArt = pl?.get("coverArt") as? String ?: cachedMeta?.coverArt,
                    updatedAt = System.currentTimeMillis(),
                    lastSyncedAt = System.currentTimeMillis(),
                )
                playlistDao.upsertAll(listOf(updatedMeta))
                // Update state directly — no redundant server fetch
                _state.value = _state.value.copy(
                    playlist = updatedMeta,
                    tracks = tracks.distinctBy { it.id },
                    isSyncing = false,
                    error = null,
                )
                refreshCacheStatus(tracks.map { it.id })
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSyncing = false, error = "Sync failed: ${e.message}")
            }
        }
    }

    fun playAll() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        val urls = tracks.map { buildStreamUrl(it.id) }
        val playlist = _state.value.playlist
        val started = if (playlist != null) {
            playbackManager.tryStartContext(
                tracks,
                urls,
                sourceType = "playlist",
                sourceId = playlist.id,
                sourceName = playlist.name,
            )
        } else {
            playbackManager.tryStartContext(tracks, urls)
        }
        if (!started) _showOverwriteModal.value = true
    }

    private val _showOverwriteModal = kotlinx.coroutines.flow.MutableStateFlow(false)
    val showOverwriteModal: kotlinx.coroutines.flow.StateFlow<Boolean> = _showOverwriteModal

    fun resolveOverwrite(clearAndPlay: Boolean) {
        _showOverwriteModal.value = false
        playbackManager.resolveOverwrite(clearAndPlay)
    }

    fun shuffle() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        val urls = tracks.map { buildStreamUrl(it.id) }
        val playlist = _state.value.playlist
        val started = if (playlist != null) {
            playbackManager.tryShuffleContext(
                tracks,
                urls,
                sourceType = "playlist",
                sourceId = playlist.id,
                sourceName = playlist.name,
            )
        } else {
            playbackManager.tryShuffleContext(tracks, urls)
        }
        if (!started) _showOverwriteModal.value = true
    }

    fun playTrack(index: Int) {
        val tracks = _state.value.tracks
        if (index < 0 || index >= tracks.size) return
        val urls = tracks.map { buildStreamUrl(it.id) }
        playbackManager.playAlbum(tracks, urls, startIndex = index)
    }

    fun addToQueue() {
        val tracks = _state.value.tracks
        val urls = tracks.map { buildStreamUrl(it.id) }
        tracks.zip(urls).forEach { (track, url) ->
            playbackManager.addToQueue(track, url)
        }
    }

    /** Insert all playlist tracks at the front of the Priority Queue (Play Next). */
    fun playNextAll() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        val urls = tracks.map { buildStreamUrl(it.id) }
        // Reverse so the FIRST track ends up playing next.
        tracks.zip(urls).reversed().forEach { (track, url) ->
            playbackManager.playNext(track, url)
        }
    }

    fun addToQueueTrack(track: Track, url: String) {
        playbackManager.addToQueue(track, url)
    }

    fun playNext(index: Int) {
        val tracks = _state.value.tracks
        if (index < 0 || index >= tracks.size) return
        val track = tracks[index]
        val url = buildStreamUrl(track.id)
        playbackManager.playNext(track, url)
    }

    /** Remove a single track from this playlist (local-first + background sync). */
    fun removeFromPlaylist(trackId: String) {
        val playlist = _state.value.playlist ?: return
        viewModelScope.launch {
            try {
                // Capture position BEFORE state update
                val position = _state.value.tracks.indexOfFirst { it.id == trackId }
                playlistDao.removeEntry(playlist.id, trackId)
                // Update local state
                val updatedTracks = _state.value.tracks.filter { it.id != trackId }
                val updatedMeta = playlist.copy(
                    trackCount = updatedTracks.size,
                    updatedAt = System.currentTimeMillis(),
                )
                playlistDao.upsertAll(listOf(updatedMeta))
                _state.value = _state.value.copy(
                    playlist = updatedMeta,
                    tracks = updatedTracks,
                )
                // Enqueue background sync
                pendingChangeDao.insert(
                    PendingPlaylistChangeEntity(
                        playlistId = playlist.id,
                        changeType = "remove_tracks",
                        payload = position.toString(),
                    ),
                )
                syncWorker.flushNow()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-playlist", "removeFromPlaylist failed: ${e.message}")
            }
        }
    }

    /** Add tracks from this playlist to another playlist. */
    fun addToPlaylist(targetPlaylistId: String, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val existingEntries = playlistDao.getEntries(targetPlaylistId)
                val existingIds = existingEntries.map { it.trackId }.toSet()
                val newTrackIds = trackIds.filter { it !in existingIds }
                if (newTrackIds.isEmpty()) return@launch
                val nextPosition = (existingEntries.maxOfOrNull { it.position } ?: -1) + 1
                val newEntries = newTrackIds.mapIndexed { idx, tid ->
                    PlaylistEntryEntity(playlistId = targetPlaylistId, trackId = tid, position = nextPosition + idx)
                }
                playlistDao.upsertEntries(newEntries)
                val meta = playlistDao.getById(targetPlaylistId)
                if (meta != null) {
                    playlistDao.upsertAll(
                        listOf(
                            meta.copy(
                                trackCount = existingEntries.size + newTrackIds.size,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        ),
                    )
                }
                pendingChangeDao.insert(
                    PendingPlaylistChangeEntity(
                        playlistId = targetPlaylistId,
                        changeType = "add_tracks",
                        payload = trackIds.joinToString(","),
                    ),
                )
                syncWorker.flushNow()
                // Auto-download tracks if setting is enabled
                val autoDownload = storage.get("auto_download_playlists")?.toBooleanStrictOrNull() ?: true
                if (autoDownload) {
                    newTrackIds.forEach { tid ->
                        val streamUrl = buildStreamUrl(tid)
                        downloadManager.enqueue(tid, streamUrl, priority = 1)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-playlist", "addToPlaylist failed: ${e.message}")
            }
        }
    }

    /**
     * Add tracks INTO this playlist (from the "Add Songs" picker). Local-first:
     * dedupes against existing entries, appends at the next position, updates
     * local state immediately, then enqueues a background `add_tracks` sync and
     * optionally auto-downloads (mirrors [addToPlaylist] + [syncFromServer]).
     */
    fun addTracksToThisPlaylist(trackIds: List<String>) {
        val playlist = _state.value.playlist ?: return
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val existingEntries = playlistDao.getEntries(playlist.id)
                val existingIds = existingEntries.map { it.trackId }.toSet()
                val newIds = trackIds.distinct().filter { it !in existingIds }
                if (newIds.isEmpty()) return@launch
                val nextPosition = (existingEntries.maxOfOrNull { it.position } ?: -1) + 1
                playlistDao.upsertEntries(
                    newIds.mapIndexed { idx, tid ->
                        PlaylistEntryEntity(playlistId = playlist.id, trackId = tid, position = nextPosition + idx)
                    },
                )
                // Persist + map track entities so the rows render offline.
                val entityById = trackDao.getTracksByIds(newIds).associateBy { it.id }
                val newTracks = newIds.mapNotNull { entityById[it] }.map { entity ->
                    Track(
                        id = entity.id, title = entity.title,
                        artist = entity.artist, album = null,
                        albumId = entity.albumId, artistId = entity.artistId,
                        duration = entity.durationSeconds, trackNumber = entity.trackNumber,
                        coverArt = entity.coverArtUrl, suffix = entity.suffix,
                        contentType = entity.contentType,
                        userRating = entity.userRating,
                    )
                }
                val updatedTracks = (_state.value.tracks + newTracks).distinctBy { it.id }
                val updatedMeta = playlist.copy(
                    trackCount = updatedTracks.size,
                    updatedAt = System.currentTimeMillis(),
                )
                playlistDao.upsertAll(listOf(updatedMeta))
                _state.value = _state.value.copy(playlist = updatedMeta, tracks = updatedTracks)
                // Enqueue background sync
                pendingChangeDao.insert(
                    PendingPlaylistChangeEntity(
                        playlistId = playlist.id,
                        changeType = "add_tracks",
                        payload = newIds.joinToString(","),
                    ),
                )
                syncWorker.flushNow()
                // Auto-download tracks if setting is enabled (same as addToPlaylist)
                val autoDownload = storage.get("auto_download_playlists")?.toBooleanStrictOrNull() ?: true
                if (autoDownload) {
                    newIds.forEach { tid ->
                        val streamUrl = buildStreamUrl(tid)
                        downloadManager.enqueue(tid, streamUrl, priority = 1)
                    }
                }
                refreshCacheStatus(updatedTracks.map { it.id })
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-playlist", "addTracksToThisPlaylist failed: ${e.message}")
            }
        }
    }

    /** Search the local track catalog for the Add Songs picker. */
    suspend fun searchPickerTracks(query: String): List<com.lucasdss.ftpmusic.app.data.db.TrackEntity> =
        trackDao.searchAllTracks(query)

    /** Recently played tracks shown as Add Songs picker suggestions. */
    suspend fun pickerSuggestions(): List<com.lucasdss.ftpmusic.app.data.db.TrackEntity> =
        trackDao.getRecentlyPlayed(limit = 50)

    /** Move a track from fromIndex to toIndex within this playlist. */
    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val tracks = _state.value.tracks
        val playlist = _state.value.playlist ?: return
        if (fromIndex < 0 || fromIndex >= tracks.size || toIndex < 0 || toIndex >= tracks.size) return
        if (fromIndex == toIndex) return
        viewModelScope.launch {
            try {
                // Reorder in local state
                val mutable = tracks.toMutableList()
                val moved = mutable.removeAt(fromIndex)
                mutable.add(toIndex, moved)
                // Persist new positions
                playlistDao.clearEntries(playlist.id)
                val entries = mutable.mapIndexed { idx, track ->
                    PlaylistEntryEntity(playlistId = playlist.id, trackId = track.id, position = idx)
                }
                playlistDao.upsertEntries(entries)
                _state.value = _state.value.copy(tracks = mutable)
                // Enqueue full track sync to update server order
                val trackIds = mutable.joinToString(",") { it.id }
                pendingChangeDao.insert(
                    PendingPlaylistChangeEntity(
                        playlistId = playlist.id,
                        changeType = "sync_tracks",
                        payload = trackIds,
                    ),
                )
                syncWorker.flushNow()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-playlist", "moveTrack failed: ${e.message}")
            }
        }
    }

    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            downloadTrackInternal(track)
        }
    }

    private suspend fun downloadTrackInternal(track: Track) {
        try {
            if (cacheService.promoteToDownload(track.id)) {
                if (!downloadedTrackIds.contains(track.id)) downloadedTrackIds.add(track.id)
                return
            }
            val streamUrl = buildStreamUrl(track.id)
            downloadManager.enqueue(track.id, streamUrl, priority = 1)
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-playlist", "downloadTrackInternal failed: ${e.message}")
        }
    }

    fun downloadAll() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            tracks.forEach { downloadTrackInternal(it) }
        }
    }

    fun removeLocally() {
        val playlist = _state.value.playlist ?: return
        viewModelScope.launch {
            playlistDao.clearEntries(playlist.id)
            playlistDao.delete(playlist.id)
        }
    }

    fun syncToServer() {
        val playlist = _state.value.playlist ?: return
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            if (_state.value.isSyncing) return@launch
            _state.value = _state.value.copy(isSyncing = true)
            try {
                // Update local metadata
                val updated = playlist.copy(
                    trackCount = tracks.size,
                    updatedAt = System.currentTimeMillis(),
                )
                playlistDao.upsertAll(listOf(updated))
                // Enqueue background track sync
                val trackIds = tracks.joinToString(",") { it.id }
                pendingChangeDao.insert(
                    PendingPlaylistChangeEntity(
                        playlistId = playlist.id,
                        changeType = "sync_tracks",
                        payload = trackIds,
                    ),
                )
                syncWorker.flushNow()
                // Clear conflict state immediately — user sees instant feedback
                _state.value = _state.value.copy(
                    playlist = updated,
                    isSyncing = false,
                    isConflicted = false,
                    conflictMessage = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSyncing = false)
                android.util.Log.w("ftpmusic-playlist", "syncToServer failed: ${e.message}")
            }
        }
    }

    fun buildStreamUrl(trackId: String): String {
        val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        return auth.buildStreamUrl(base, trackId, username, password)
    }

    fun renamePlaylist(newName: String) {
        val playlist = _state.value.playlist ?: return
        if (newName.isBlank()) return
        viewModelScope.launch {
            try {
                // Local-first: update DB immediately
                val updated = playlist.copy(name = newName, updatedAt = System.currentTimeMillis())
                playlistDao.upsertAll(listOf(updated))
                _state.value = _state.value.copy(playlist = updated)
                // Enqueue background sync
                pendingChangeDao.insert(
                    PendingPlaylistChangeEntity(
                        playlistId = playlist.id,
                        changeType = "rename",
                        payload = "name=$newName",
                    ),
                )
                syncWorker.flushNow()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-playlist", "renamePlaylist failed: ${e.message}")
            }
        }
    }

    fun deletePlaylist(onComplete: () -> Unit = {}) {
        val playlist = _state.value.playlist ?: return
        viewModelScope.launch {
            try {
                // Local-first: delete from DB immediately
                playlistDao.clearEntries(playlist.id)
                playlistDao.delete(playlist.id)
                // Enqueue background sync
                pendingChangeDao.insert(
                    PendingPlaylistChangeEntity(
                        playlistId = playlist.id,
                        changeType = "delete",
                        payload = "",
                    ),
                )
                syncWorker.flushNow()
                onComplete()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-playlist", "deletePlaylist failed: ${e.message}")
                onComplete() // navigate back even on error — local delete already happened
            }
        }
    }

    fun copyPlaylist() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            try {
                val params = auth.buildAuthParams(username, password)
                val copyName = "${_state.value.playlist?.name ?: "Playlist"} (copy)"
                val response = api.createPlaylist(
                    params,
                    name = copyName,
                    songIds = tracks.joinToString(",") { it.id },
                )
                if (!auth.checkResponseStatus(response)) {
                    android.util.Log.w(
                        "ftpmusic-playlist",
                        "copyPlaylist server error: ${auth.getResponseError(response)}",
                    )
                    return@launch
                }
                val sr = response["subsonic-response"] as? Map<*, *>
                val pl = sr?.get("playlist") as? Map<*, *>
                val newId = pl?.get("id") as? String
                if (newId != null) {
                    // Persist metadata + entries locally
                    playlistDao.upsertAll(
                        listOf(
                            PlaylistEntity(
                                id = newId,
                                name = copyName,
                                trackCount = tracks.size,
                                coverArt = _state.value.playlist?.coverArt,
                            ),
                        ),
                    )
                    playlistDao.clearEntries(newId)
                    playlistDao.upsertEntries(
                        tracks.mapIndexed { idx, t ->
                            PlaylistEntryEntity(playlistId = newId, trackId = t.id, position = idx)
                        },
                    )
                    // Save track entities for offline access
                    val trackEntities = tracks.mapNotNull { t ->
                        // Track from ViewModel state may be incomplete; save what we have
                        com.lucasdss.ftpmusic.app.data.db.TrackEntity(
                            id = t.id, title = t.title, artist = t.artist,
                            albumId = t.albumId, artistId = t.artistId,
                            durationSeconds = t.duration, trackNumber = t.trackNumber,
                            coverArtUrl = t.coverArt, suffix = t.suffix,
                            contentType = t.contentType,
                        )
                    }
                    if (trackEntities.isNotEmpty()) trackDao.upsertAll(trackEntities)
                    // Enqueue full track sync
                    val trackIds = tracks.joinToString(",") { it.id }
                    pendingChangeDao.insert(
                        PendingPlaylistChangeEntity(
                            playlistId = newId,
                            changeType = "sync_tracks",
                            payload = trackIds,
                        ),
                    )
                    syncWorker.flushNow()
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-playlist", "copyPlaylist failed: ${e.message}")
            }
        }
    }

    fun buildCoverArtUrl(coverArtId: String): String {
        val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        val authParams = auth.buildAuthParams(username, password)
        val params = authParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$base/rest/getCoverArt?id=$coverArtId&$params&size=400"
    }

    fun isCached(trackId: String): Boolean = cachedTrackIds.contains(trackId)
    fun isDownloaded(trackId: String): Boolean = downloadedTrackIds.contains(trackId)
    fun isQueued(trackId: String): Boolean = queuedTrackIds.contains(trackId)

    fun loadAvailablePlaylists() {
        val currentId = _state.value.playlist?.id ?: return
        viewModelScope.launch {
            try {
                val all = playlistDao.getAll()
                _availablePlaylists.value = all.filter { it.id != currentId }
            } catch (_: Exception) {}
        }
    }

    private val cachedTrackIds = mutableStateListOf<String>()
    private val downloadedTrackIds = mutableStateListOf<String>()
    private val queuedTrackIds = mutableStateListOf<String>()
    private var watcherJobs = mutableListOf<kotlinx.coroutines.Job>()

    /** Available playlists for "Add to Playlist" picker (excludes current playlist). */
    private val _availablePlaylists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val availablePlaylists: StateFlow<List<PlaylistEntity>> = _availablePlaylists.asStateFlow()

    companion object {
        /** Maximum per-track Flow watchers to prevent trigger storms on large playlists.
         *  Each track gets 2 watchers (cache + queue), so cap is 400 total collectors. */
        private const val MAX_WATCHER_TRACKS = 200
    }

    private fun refreshCacheStatus(trackIds: List<String>) {
        // Cancel all previous watchers to prevent accumulation
        watcherJobs.forEach { it.cancel() }
        watcherJobs.clear()
        // Initial load: check cache status for all tracks
        viewModelScope.launch {
            try {
                cachedTrackIds.clear()
                downloadedTrackIds.clear()
                trackIds.forEach { id ->
                    val entity = cacheService.getTrackEntity(id)
                    if (entity?.cachedFilePath != null) {
                        cachedTrackIds.add(id)
                        if (entity.isDownloaded) downloadedTrackIds.add(id)
                    }
                }
            } catch (_: Exception) {}
        }.also { watcherJobs.add(it) }
        // Reactive: watch cache status for up to MAX_WATCHER_TRACKS to avoid trigger storms
        val watchIds = trackIds.take(MAX_WATCHER_TRACKS)
        watchIds.forEach { trackId ->
            val job = viewModelScope.launch {
                try {
                    cacheService.watchCacheStatus(trackId).collect { row ->
                        if (row.cached_file_path != null) {
                            if (!cachedTrackIds.contains(trackId)) cachedTrackIds.add(trackId)
                            if (row.is_downloaded &&
                                !downloadedTrackIds.contains(trackId)
                            ) {
                                downloadedTrackIds.add(trackId)
                            }
                            if (!row.is_downloaded) downloadedTrackIds.remove(trackId)
                            queuedTrackIds.remove(trackId)
                        } else {
                            cachedTrackIds.remove(trackId)
                            downloadedTrackIds.remove(trackId)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ftpmusic-playlist", "watchCacheStatus($trackId) failed: ${e.message}")
                }
            }
            watcherJobs.add(job)
        }
        // Reactive: watch download queue status for up to MAX_WATCHER_TRACKS
        watchIds.forEach { trackId ->
            val job = viewModelScope.launch {
                try {
                    cacheQueueDao.watchByTrackId(trackId).collect { items ->
                        val item = items.firstOrNull()
                        if (item != null && item.status in listOf("pending", "processing")) {
                            if (!queuedTrackIds.contains(trackId)) queuedTrackIds.add(trackId)
                        } else {
                            queuedTrackIds.remove(trackId)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ftpmusic-playlist", "watchByTrackId($trackId) failed: ${e.message}")
                }
            }
            watcherJobs.add(job)
        }
    }

    override fun onCleared() {
        super.onCleared()
        watcherJobs.forEach { it.cancel() }
        watcherJobs.clear()
    }
}
