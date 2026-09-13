package com.lucasdss.ftpmusic.app.ui.album

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.CacheQueueDao
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeDao
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorker
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.MusicBrainzService
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.AlbumRepository
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import com.lucasdss.ftpmusic.app.ui.library.PlaylistView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlbumDetailState(
    val album: com.lucasdss.ftpmusic.app.data.model.Album? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val likedTrackIds: Set<String> = emptySet(),
    val dislikedTrackIds: Set<String> = emptySet(),
    val trackRatings: Map<String, Int> = emptyMap(),
    val publicRating: Double? = null,
    val publicRatingVotes: Int? = null,
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val repository: AlbumRepository,
    private val storage: SecureStorage,
    private val playbackManager: PlaybackManager,
    private val cacheService: CacheService,
    private val downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager,
    private val cacheQueueDao: CacheQueueDao,
    private val playlistDao: PlaylistDao,
    private val pendingChangeDao: PendingPlaylistChangeDao,
    private val syncWorker: PlaylistSyncWorker,
    private val playlistRepo: PlaylistRepository,
    private val metadataDao: CachedMetadataDao,
    private val favoriteRepository: FavoriteRepository,
    private val api: SubsonicApi,
    private val authBuilder: SubsonicAuthHelper = SubsonicAuthHelper(),
    private val musicBrainzService: MusicBrainzService,
    private val offlineModeManager: OfflineModeManager,
    private val trackDao: TrackDao,
) : ViewModel() {

    private val _state = MutableStateFlow(AlbumDetailState())
    val state: StateFlow<AlbumDetailState> = _state.asStateFlow()

    private val _showOverwriteModal = MutableStateFlow(false)
    val showOverwriteModal: StateFlow<Boolean> = _showOverwriteModal.asStateFlow()

    private val auth = SubsonicAuthHelper()
    private var username: String = ""
    private var password: String = ""

    fun playTrack(index: Int) {
        val tracks = _state.value.tracks
        if (index < 0 || index >= tracks.size) return
        val urls = tracks.map { buildStreamUrl(it.id) }
        playbackManager.playAlbum(tracks, urls, startIndex = index)
    }

    fun playAll() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        val urls = tracks.map { buildStreamUrl(it.id) }
        val album = _state.value.album
        val started = playbackManager.tryStartContext(
            tracks,
            urls,
            sourceType = album?.let { "album" },
            sourceId = album?.id,
            sourceName = album?.name ?: "this album",
        )
        if (!started) _showOverwriteModal.value = true
    }

    fun resolveOverwrite(clearAndPlay: Boolean) {
        _showOverwriteModal.value = false
        playbackManager.resolveOverwrite(clearAndPlay)
    }

    fun shuffle() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        val urls = tracks.map { buildStreamUrl(it.id) }
        val album = _state.value.album
        val started = playbackManager.tryShuffleContext(
            tracks,
            urls,
            sourceType = album?.let { "album" },
            sourceId = album?.id,
            sourceName = album?.name ?: "this album",
        )
        if (!started) _showOverwriteModal.value = true
    }

    private fun playAlbumWithoutJournal(tracks: List<Track>, urls: List<String>) {
        playbackManager.playAlbum(tracks, urls)
    }

    fun addToQueue() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        val urls = tracks.map { buildStreamUrl(it.id) }
        tracks.zip(urls).forEach { (track, url) ->
            playbackManager.addToQueue(track, url)
        }
    }

    fun playNextAll() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        // Add in reverse order so first track ends up playing next
        tracks.zip(tracks.map { buildStreamUrl(it.id) }).reversed().forEach { (track, url) ->
            playbackManager.playNext(track, url)
        }
    }

    fun addToQueueTrack(track: Track, url: String) {
        playbackManager.addToQueue(track, url)
    }

    fun playNextTrack(track: Track, url: String) {
        playbackManager.playNext(track, url)
    }

    fun playNext(index: Int) {
        val tracks = _state.value.tracks
        if (index < 0 || index >= tracks.size) return
        val track = tracks[index]
        val url = buildStreamUrl(track.id)
        playbackManager.playNext(track, url)
    }

    fun loadAlbum(albumId: String) {
        username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
        password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
        loadAlbum(albumId, username, password)
    }

    fun loadAlbum(albumId: String, username: String, password: String) {
        this.username = username
        this.password = password
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                // Load cached metadata + tracks first for instant display
                val cachedTracks = metadataDao.getAlbumTracks(albumId)
                if (cachedTracks.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        tracks = cachedTracks.map { t ->
                            Track(
                                id = t.id, title = t.title, artist = t.artist, albumId = t.albumId,
                                artistId = t.artistId, duration = t.duration,
                                trackNumber = t.trackNumber, coverArt = t.coverArt,
                                suffix = t.suffix, contentType = t.contentType,
                            )
                        },
                        isLoading = false,
                    )
                }
                // Offline: show cached data only, no API call
                if (offlineModeManager.isOffline.value) {
                    _state.value = _state.value.copy(isLoading = false)
                    if (cachedTracks.isEmpty()) {
                        _state.value = _state.value.copy(error = "Album not cached offline")
                    }
                    return@launch
                }
                // Fetch from API for fresh data
                val result = repository.getAlbum(albumId, username, password)
                val tracks = result?.tracks ?: emptyList()
                _state.value = _state.value.copy(
                    album = result?.album,
                    tracks = tracks,
                    isLoading = false,
                )
                // Lazy: load cached public rating, then fetch from MusicBrainz if missing
                loadPublicRating(albumId, result?.album)
                // Cache tracks for next visit — atomic replace prevents partial data
                // if the process dies mid-write (clear + insert in one transaction).
                if (tracks.isNotEmpty()) {
                    metadataDao.replaceAlbumTracks(
                        albumId,
                        tracks.map { t ->
                            com.lucasdss.ftpmusic.app.data.db.CachedAlbumTrackEntity(
                                id = t.id,
                                albumId = albumId,
                                title = t.title,
                                artist = t.artist,
                                duration = t.duration,
                                trackNumber = t.trackNumber,
                                coverArt = t.coverArt,
                                suffix = t.suffix,
                                contentType = t.contentType,
                            )
                        },
                    )
                }
                refreshCacheStatus(tracks.map { it.id })
                startCacheWarming(tracks)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    /** Load cached public rating, then lazy-fetch from MusicBrainz if missing. */
    private fun loadPublicRating(albumId: String, album: com.lucasdss.ftpmusic.app.data.model.Album?) {
        viewModelScope.launch {
            try {
                // Check cached rating first
                val cachedRating = metadataDao.getAlbumById(albumId)?.publicRating
                if (cachedRating != null) {
                    _state.value = _state.value.copy(publicRating = cachedRating)
                    return@launch
                }
                // Fetch from MusicBrainz if we have artist + album names
                val artistName = album?.artist ?: return@launch
                val albumName = album?.name ?: return@launch
                val (rating, mbid) = musicBrainzService.fetchAlbumRating(artistName, albumName)
                if (rating.value != null) {
                    metadataDao.setAlbumPublicRating(albumId, rating.value, rating.votes, mbid)
                    _state.value = _state.value.copy(publicRating = rating.value, publicRatingVotes = rating.votes)
                }
            } catch (_: Exception) {
                // Network/parse failure — retry next open
            }
        }
    }

    /** Cache warming: enqueue album tracks for background download. */
    private fun startCacheWarming(tracks: List<Track>) {
        viewModelScope.launch {
            val toDownload = tracks.filter { !isCached(it.id) }
            toDownload.forEach { track ->
                try {
                    val streamUrl = buildStreamUrl(track.id)
                    downloadManager.enqueue(track.id, streamUrl, priority = 2)
                    android.util.Log.d("ftpmusic-warm", "[WARM] enqueued: ${track.id} — ${track.title}")
                } catch (e: Exception) {
                    android.util.Log.w("ftpmusic-album", "cacheWarming enqueue failed: ${e.message}")
                }
            }
        }
    }

    fun buildStreamUrl(trackId: String): String {
        val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        return auth.buildStreamUrl(base, trackId, username, password)
    }

    fun buildCoverArtUrl(coverArtId: String): String {
        val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        val authParams = auth.buildAuthParams(username, password)
        val params = authParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$base/rest/getCoverArt?id=$coverArtId&$params&size=400"
    }

    fun buildSmallCoverArtUrl(coverArtId: String): String {
        val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        val authParams = auth.buildAuthParams(username, password)
        val params = authParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$base/rest/getCoverArt?id=$coverArtId&$params&size=100"
    }

    fun isCached(trackId: String): Boolean = cachedTrackIds.contains(trackId)

    fun isDownloaded(trackId: String): Boolean = downloadedTrackIds.contains(trackId)

    /** Download a single track (priority=1 → permanent, never evicted).
     * Expects to be called within an existing coroutine context. */
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
            android.util.Log.w("ftpmusic-album", "downloadTrack enqueue failed: ${e.message}")
        }
    }

    /** Download all album tracks (skip already downloaded). */
    fun downloadAlbum() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            tracks.forEach { downloadTrackInternal(it) }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val local = playlistDao.getAll()
                _playlists.value = local.map {
                    PlaylistView(id = it.id, name = it.name, trackCount = it.trackCount, coverArt = it.coverArt)
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-album", "loadPlaylists failed: ${e.message}")
            }
        }
    }

    fun addToPlaylist(playlistId: String, trackIds: List<String>) {
        viewModelScope.launch {
            try {
                playlistRepo.addToPlaylist(playlistId, trackIds)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-album", "addToPlaylist failed: ${e.message}")
            }
        }
    }

    fun createPlaylist(name: String, onCreated: (String?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val tempId = playlistRepo.createPlaylist(name)
                loadPlaylists()
                onCreated(tempId)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-album", "createPlaylist failed: ${e.message}")
                onCreated(null)
            }
        }
    }

    fun importPlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                playlistRepo.importPlaylist(playlistId)
                loadPlaylists()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-album", "importPlaylist failed: ${e.message}")
            }
        }
    }

    private val _serverPlaylists = MutableStateFlow<List<PlaylistView>>(emptyList())
    val serverPlaylists: StateFlow<List<PlaylistView>> = _serverPlaylists.asStateFlow()

    fun loadServerPlaylists() {
        viewModelScope.launch {
            try {
                _serverPlaylists.value = playlistRepo.loadServerPlaylists()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-album", "loadServerPlaylists failed: ${e.message}")
            }
        }
    }

    private val cachedTrackIds = mutableStateListOf<String>()
    private val downloadedTrackIds = mutableStateListOf<String>()
    private val queuedTrackIds = mutableStateListOf<String>()
    private var watcherParentJob: kotlinx.coroutines.Job? = null
    private val _playlists = MutableStateFlow<List<PlaylistView>>(emptyList())
    val playlists: StateFlow<List<PlaylistView>> = _playlists.asStateFlow()

    init {
        // Reactively update UI when a track is cached or downloaded
        viewModelScope.launch {
            cacheService.cacheEventFlow.collect { (trackId, isDownload) ->
                if (!cachedTrackIds.contains(trackId)) {
                    cachedTrackIds.add(trackId)
                }
                if (isDownload && !downloadedTrackIds.contains(trackId)) {
                    downloadedTrackIds.add(trackId)
                }
            }
        }
    }

    private fun refreshCacheStatus(trackIds: List<String>) {
        // Cancel all previous watchers atomically via parent Job — prevents
        // race between old flow cancellation and new flow registration
        watcherParentJob?.cancel()
        watcherParentJob = kotlinx.coroutines.Job(viewModelScope.coroutineContext[Job])
        val parent = watcherParentJob!!
        // Initial load: batch query cache status for all tracks (avoid N+1)
        viewModelScope.launch(parent) {
            try {
                applyTrackStatus(cacheService.getTracksByIds(trackIds))
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-album", "refreshCacheStatus batch: ${e.message}")
            }
        }
        // Reactive: ONE flow over all tracks (Room re-runs a single SELECT on
        // invalidation — previously one flow per track re-ran ~2×N queries on
        // every cache progress write).
        viewModelScope.launch(parent) {
            try {
                trackDao.watchTracksByIds(trackIds).collect { entities ->
                    applyTrackStatus(entities)
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-album", "watchTracksByIds: ${e.message}")
            }
        }
        // Reactive: ONE flow over the download queue for all tracks
        viewModelScope.launch(parent) {
            try {
                cacheQueueDao.watchByTrackIds(trackIds).collect { items ->
                    val queued = items.filter { it.status in listOf("pending", "processing") }
                        .map { it.trackId }.toSet()
                    Snapshot.withMutableSnapshot {
                        queuedTrackIds.clear()
                        queuedTrackIds.addAll(queued)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-album", "watchByTrackIds: ${e.message}")
            }
        }
    }

    /** Apply a batch of track entities to the cache/like/dislike snapshots
     *  atomically — avoids per-item recomposition flicker. */
    private fun applyTrackStatus(entities: List<com.lucasdss.ftpmusic.app.data.db.TrackEntity>) {
        val newCached = mutableListOf<String>()
        val newDownloaded = mutableListOf<String>()
        val newLiked = mutableListOf<String>()
        val newDisliked = mutableListOf<String>()
        for (entity in entities) {
            if (entity.cachedFilePath != null) {
                newCached.add(entity.id)
                if (entity.isDownloaded) newDownloaded.add(entity.id)
            }
            if (entity.starredAt != null) newLiked.add(entity.id)
            if (entity.isDisliked) newDisliked.add(entity.id)
        }
        Snapshot.withMutableSnapshot {
            cachedTrackIds.clear()
            cachedTrackIds.addAll(newCached)
            downloadedTrackIds.clear()
            downloadedTrackIds.addAll(newDownloaded)
            _state.value = _state.value.copy(
                likedTrackIds = newLiked.toSet(),
                dislikedTrackIds = newDisliked.toSet(),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        watcherParentJob?.cancel()
    }

    fun isQueued(trackId: String): Boolean = queuedTrackIds.contains(trackId)

    fun isTrackLiked(trackId: String): Boolean = _state.value.likedTrackIds.contains(trackId)

    fun isTrackDisliked(trackId: String): Boolean = _state.value.dislikedTrackIds.contains(trackId)

    fun getTrackRating(trackId: String): Int = _state.value.trackRatings[trackId] ?: 0

    /** Computes overall album download status for the hero badge. */
    fun getAlbumDownloadStatus(): String {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return "none"
        val allDownloaded = tracks.all { isDownloaded(it.id) }
        val anyCached = tracks.any { isCached(it.id) }
        return when {
            allDownloaded -> "downloaded"
            anyCached -> "cached"
            else -> "none"
        }
    }

    /** Rates the album itself via setRating API. */
    fun rateAlbum(albumId: String, rating: Int) {
        viewModelScope.launch {
            try {
                val authParams = auth.buildAuthParams(username, password)
                api.setRating(authParams, id = albumId, rating = rating)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-album", "rateAlbum failed: ${e.message}")
            }
        }
    }

    private var toggleLikeJob: kotlinx.coroutines.Job? = null

    fun toggleTrackLike(trackId: String) {
        val current = _state.value.likedTrackIds
        val disliked = _state.value.dislikedTrackIds
        if (trackId in current) {
            // Optimistic unlike
            _state.value = _state.value.copy(likedTrackIds = current - trackId)
            toggleLikeJob?.cancel()
            toggleLikeJob = viewModelScope.launch {
                try {
                    favoriteRepository.unlikeTrack(trackId)
                } catch (
                    _: Exception,
                ) {
                    _state.value = _state.value.copy(likedTrackIds = current)
                }
            }
        } else {
            // Optimistic like — mutual exclusion: clear dislike
            _state.value = _state.value.copy(likedTrackIds = current + trackId, dislikedTrackIds = disliked - trackId)
            toggleLikeJob?.cancel()
            toggleLikeJob = viewModelScope.launch {
                try {
                    favoriteRepository.likeTrack(trackId)
                } catch (_: Exception) {
                    _state.value = _state.value.copy(likedTrackIds = current, dislikedTrackIds = disliked)
                }
            }
        }
    }

    /** Toggle thumbs-down (local dislike). Mutual exclusion: disliking clears like (star). */
    fun toggleTrackDislike(trackId: String) {
        val current = _state.value.dislikedTrackIds
        val liked = _state.value.likedTrackIds
        if (trackId in current) {
            _state.value = _state.value.copy(dislikedTrackIds = current - trackId)
            toggleLikeJob?.cancel()
            toggleLikeJob = viewModelScope.launch {
                try {
                    favoriteRepository.clearDislikeTrack(trackId)
                } catch (
                    _: Exception,
                ) {
                    _state.value = _state.value.copy(dislikedTrackIds = current)
                }
            }
        } else {
            _state.value = _state.value.copy(dislikedTrackIds = current + trackId, likedTrackIds = liked - trackId)
            toggleLikeJob?.cancel()
            toggleLikeJob = viewModelScope.launch {
                try {
                    favoriteRepository.dislikeTrack(trackId)
                } catch (_: Exception) {
                    _state.value = _state.value.copy(dislikedTrackIds = current, likedTrackIds = liked)
                }
            }
        }
    }

    fun rateTrack(trackId: String, rating: Int) {
        val clamped = rating.coerceIn(0, 5)
        val current = _state.value.trackRatings
        _state.value = _state.value.copy(trackRatings = current + (trackId to clamped))
        viewModelScope.launch {
            try {
                // Local-first, then best-effort server sync (parity with Now Playing)
                trackDao.setRating(trackId, clamped)
                val authParams = auth.buildAuthParams(username, password)
                api.setRating(authParams, id = trackId, rating = clamped)
            } catch (_: Exception) {
                _state.value = _state.value.copy(trackRatings = current)
            }
        }
    }
}
