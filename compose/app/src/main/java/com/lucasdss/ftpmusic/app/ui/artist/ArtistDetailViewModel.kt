package com.lucasdss.ftpmusic.app.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.LastFmService
import com.lucasdss.ftpmusic.app.data.network.MusicBrainzService
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import com.lucasdss.ftpmusic.app.ui.library.PlaylistView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArtistDetailState(
    val artistId: String = "",
    val name: String = "",
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val likedAlbumIds: Set<String> = emptySet(),
    val dislikedAlbumIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isLoadingMoreTracks: Boolean = false,
    val hasMoreTracks: Boolean = true,
    val publicRating: Double? = null,
    val publicRatingVotes: Int? = null,
    val similarArtists: List<com.lucasdss.ftpmusic.app.data.network.LastFmService.SimilarArtist> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val api: SubsonicApi,
    private val storage: SecureStorage,
    private val playbackManager: PlaybackManager,
    private val metadataDao: CachedMetadataDao,
    private val trackDao: TrackDao,
    private val offlineModeManager: OfflineModeManager,
    private val musicBrainzService: MusicBrainzService,
    private val lastFmService: LastFmService,
    private val cacheService: com.lucasdss.ftpmusic.app.data.cache.CacheService,
    private val downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager,
    private val playlistDao: com.lucasdss.ftpmusic.app.data.db.PlaylistDao,
    private val playlistRepo: com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository,
    private val favoriteRepository: com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ArtistDetailState())
    val state: StateFlow<ArtistDetailState> = _state.asStateFlow()
    private val auth = SubsonicAuthHelper()
    private var username: String = ""
    private var password: String = ""

    init {
        // Reactive album reactions: Room flows keep the Albums-tab thumbs in
        // sync with every screen (Library / Album detail) without reloads.
        viewModelScope.launch {
            try {
                kotlinx.coroutines.flow.combine(
                    metadataDao.getStarredAlbumIdsFlow(),
                    metadataDao.getDislikedAlbumIdsFlow(),
                ) { liked, disliked -> liked.toSet() to disliked.toSet() }
                    .collect { (liked, disliked) ->
                        _state.value = _state.value.copy(
                            likedAlbumIds = liked,
                            dislikedAlbumIds = disliked,
                        )
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Reactions unavailable — thumbs just render inactive.
            }
        }
    }

    companion object {
        private const val TRACKS_PAGE_SIZE = 50
    }

    fun loadArtist(artistId: String) {
        username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
        password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // Local-first: read artist name + albums from the cached_albums table.
                // Both the artist list count and this page now come from the same
                // source, so counts are always consistent.
                val cachedArtist = metadataDao.getArtistById(artistId)
                // Always merge all three queries: by artist_id, by artist name text,
                // and by track-level artist_id. Some albums may have a NULL or
                // DIFFERENT artist_id (e.g., compilations, or Navidrome attributing
                // the album to "Original Soundtrack" while tracks are by the artist).
                // Merging all three guarantees all albums appear regardless of
                // attribution variance.
                val byId = metadataDao.getAlbumsByArtistId(artistId)
                val byName = if (cachedArtist !=
                    null
                ) {
                    metadataDao.getAlbumsByArtistName(cachedArtist.name)
                } else {
                    emptyList()
                }
                val byTrackArtist = metadataDao.getAlbumsByTrackArtistId(artistId)
                val cachedAlbums = (byId + byName + byTrackArtist).distinctBy { it.id }
                val localAlbums = cachedAlbums.map { it.toAlbum() }

                // Load first page of tracks (cursor-based, local-first)
                val firstPage = trackDao.getTracksByArtistId(artistId, TRACKS_PAGE_SIZE)
                val localTracks = firstPage.map { it.toTrack() }

                if (localAlbums.isNotEmpty() || cachedArtist != null || localTracks.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        artistId = artistId,
                        name = cachedArtist?.name ?: localAlbums.firstOrNull()?.artist ?: "Unknown",
                        albums = localAlbums,
                        tracks = localTracks,
                        hasMoreTracks = firstPage.size >= TRACKS_PAGE_SIZE,
                        publicRating = cachedArtist?.publicRating,
                        publicRatingVotes = cachedArtist?.publicRatingVotes,
                        similarArtists = lastFmService.parseStoredJson(cachedArtist?.similarArtistsJson),
                        isLoading = false,
                    )
                    loadReactions(firstPage.map { it.id })
                    // Lazy: fetch public rating from MusicBrainz if not yet cached
                    if (cachedArtist?.publicRating == null && !offlineModeManager.isOffline.value) {
                        fetchPublicRating(artistId, cachedArtist?.name)
                    }
                    // Lazy: fetch similar artists from last.fm if not yet cached
                    if (cachedArtist?.similarArtistsJson == null && !offlineModeManager.isOffline.value) {
                        fetchSimilarArtists(artistId, cachedArtist?.name ?: localAlbums.firstOrNull()?.artist)
                    }
                    return@launch
                }

                // Nothing cached for this artist — fall back to the live API only if online.
                if (!offlineModeManager.isOffline.value) {
                    val authParams = auth.buildAuthParams(username, password)
                    val response = api.getArtist(id = artistId, auth = authParams)
                    val sr = response["subsonic-response"] as? Map<*, *>
                    val artistData = sr?.get("artist") as? Map<*, *>
                    val name = artistData?.get("name") as? String ?: "Unknown"
                    val albumsData = artistData?.get("album") as? List<*>
                    val albums = albumsData?.mapNotNull { a ->
                        val m = a as? Map<*, *> ?: return@mapNotNull null
                        Album(
                            id = m["id"] as? String ?: return@mapNotNull null,
                            name = m["name"] as? String ?: return@mapNotNull null,
                            artist = m["artist"] as? String,
                            year = (m["year"] as? Number)?.toInt(),
                            coverArt = m["coverArt"] as? String,
                        )
                    } ?: emptyList()
                    _state.value =
                        _state.value.copy(
                            artistId = artistId,
                            name = name,
                            albums = albums,
                            tracks = emptyList(),
                            hasMoreTracks = false,
                            isLoading = false,
                        )
                } else {
                    _state.value = _state.value.copy(
                        artistId = artistId,
                        name = cachedArtist?.name ?: "Unknown",
                        albums = emptyList(),
                        tracks = emptyList(),
                        hasMoreTracks = false,
                        isLoading = false,
                        error = "No cached albums for this artist",
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    /** Lazy-fetch the artist's public rating from MusicBrainz and persist it. */
    private fun fetchPublicRating(artistId: String, artistName: String?) {
        if (artistName.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val (rating, mbid) = musicBrainzService.fetchArtistRating(artistName)
                if (rating.value != null) {
                    metadataDao.setArtistPublicRating(artistId, rating.value, rating.votes, mbid)
                    _state.value = _state.value.copy(
                        publicRating = rating.value,
                        publicRatingVotes = rating.votes,
                    )
                }
            } catch (_: Exception) {
                // Network/parsing failure — leave rating null, retry next open
            }
        }
    }

    /** Lazy-fetch similar artists from last.fm and persist as JSON. */
    private fun fetchSimilarArtists(artistId: String, artistName: String?) {
        if (artistName.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val similar = lastFmService.fetchSimilarArtists(artistName)
                if (similar.isNotEmpty()) {
                    metadataDao.setArtistSimilarArtists(artistId, lastFmService.toStoredJson(similar))
                    _state.value = _state.value.copy(similarArtists = similar)
                }
            } catch (_: Exception) {
                // Network/parsing failure — retry next open
            }
        }
    }

    /** Cursor-based pagination: load the next page of tracks for the artist. */
    fun loadMoreTracks() {
        val state = _state.value
        if (state.isLoadingMoreTracks || !state.hasMoreTracks) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMoreTracks = true)
            try {
                val cursor = _state.value.tracks.lastOrNull()?.title
                val newEntities = if (cursor == null) {
                    trackDao.getTracksByArtistId(state.artistId, TRACKS_PAGE_SIZE)
                } else {
                    trackDao.getTracksByArtistIdAfter(state.artistId, cursor, TRACKS_PAGE_SIZE)
                }
                val newTracks = newEntities.map { it.toTrack() }
                _state.value = _state.value.copy(
                    tracks = _state.value.tracks + newTracks,
                    hasMoreTracks = newEntities.size >= TRACKS_PAGE_SIZE,
                    isLoadingMoreTracks = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoadingMoreTracks = false)
            }
        }
    }

    private fun com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity.toAlbum(): Album = Album(
        id = id,
        name = name,
        artist = artist,
        year = year,
        coverArt = coverArt,
        songCount = songCount,
        duration = duration,
    )

    private fun TrackEntity.toTrack(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        albumId = albumId,
        artistId = artistId,
        duration = durationSeconds,
        coverArt = coverArtUrl,
    )

    fun buildStreamUrl(trackId: String): String {
        val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        return auth.buildStreamUrl(base, trackId, username, password)
    }

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
        val state = _state.value
        val started = playbackManager.tryStartContext(
            tracks,
            urls,
            sourceType = "artist",
            sourceId = state.artistId,
            sourceName = state.name,
        )
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
        val state = _state.value
        val started = playbackManager.tryShuffleContext(
            tracks,
            urls,
            sourceType = "artist",
            sourceId = state.artistId,
            sourceName = state.name,
        )
        if (!started) _showOverwriteModal.value = true
    }

    // ── Queue actions (parity with Album detail) ───────────────────────

    /** Insert all artist top tracks at the front of the Priority Queue (Play Next). */
    fun playNextAll() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        // Reverse so the FIRST track ends up playing next.
        tracks.zip(tracks.map { buildStreamUrl(it.id) }).reversed().forEach { (track, url) ->
            playbackManager.playNext(track, url)
        }
    }

    /** Append all artist top tracks to the Priority Queue (Add to Queue). */
    fun addAllToQueue() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        tracks.zip(tracks.map { buildStreamUrl(it.id) }).forEach { (track, url) ->
            playbackManager.addToQueue(track, url)
        }
    }

    /** Insert a single track at the front of the Priority Queue. */
    fun playNextTrack(track: Track, url: String) {
        playbackManager.playNext(track, url)
    }

    /** Append a single track to the Priority Queue. */
    fun addToQueueTrack(track: Track, url: String) {
        playbackManager.addToQueue(track, url)
    }

    /** Download a single track (priority=1 → permanent, never evicted). */
    fun downloadTrack(track: Track) {
        viewModelScope.launch { downloadTrackInternal(track) }
    }

    private suspend fun downloadTrackInternal(track: Track) {
        try {
            if (cacheService.promoteToDownload(track.id)) {
                if (!_downloadedTrackIds.contains(track.id)) _downloadedTrackIds.add(track.id)
                return
            }
            val streamUrl = buildStreamUrl(track.id)
            downloadManager.enqueue(track.id, streamUrl, priority = 1)
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-artist", "downloadTrack enqueue failed: ${e.message}")
        }
    }

    /** Download all artist top tracks (skip already downloaded). */
    fun downloadAll() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            tracks.forEach { downloadTrackInternal(it) }
        }
    }

    fun isDownloaded(trackId: String): Boolean = _downloadedTrackIds.contains(trackId)

    private val _downloadedTrackIds = androidx.compose.runtime.mutableStateListOf<String>()
    val downloadedTrackIds: List<String> get() = _downloadedTrackIds.toList()

    // ── Like / rating (parity with Album detail) ───────────────────────

    private val _likedTrackIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds = _likedTrackIds.asStateFlow()

    private val _dislikedTrackIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val dislikedTrackIds = _dislikedTrackIds.asStateFlow()

    private val _trackRatings = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Int>>(emptyMap())
    val trackRatings = _trackRatings.asStateFlow()

    fun isTrackLiked(trackId: String): Boolean = _likedTrackIds.value.contains(trackId)

    fun isTrackDisliked(trackId: String): Boolean = _dislikedTrackIds.value.contains(trackId)

    fun getTrackRating(trackId: String): Int = _trackRatings.value[trackId] ?: 0

    /** Load persisted like (starred_at) + dislike flags for the given track ids. */
    private fun loadReactions(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val entities = trackDao.getTracksByIds(trackIds)
                _likedTrackIds.value = entities.filter { it.starredAt != null }.map { it.id }.toSet()
                _dislikedTrackIds.value = entities.filter { it.isDisliked }.map { it.id }.toSet()
            } catch (_: Exception) {}
        }
    }

    fun toggleTrackLike(trackId: String) {
        val isLiked = _likedTrackIds.value.contains(trackId)
        viewModelScope.launch {
            try {
                if (isLiked) {
                    favoriteRepository.unlikeTrack(trackId)
                    _likedTrackIds.value = _likedTrackIds.value - trackId
                } else {
                    favoriteRepository.likeTrack(trackId)
                    _likedTrackIds.value = _likedTrackIds.value + trackId
                    // Mutual exclusion: liking clears dislike
                    _dislikedTrackIds.value = _dislikedTrackIds.value - trackId
                }
            } catch (_: Exception) {
                // leave state unchanged on failure
            }
        }
    }

    /** Toggle thumbs-down (local dislike). Mutual exclusion: disliking clears like. */
    fun toggleTrackDislike(trackId: String) {
        val isDisliked = _dislikedTrackIds.value.contains(trackId)
        viewModelScope.launch {
            try {
                if (isDisliked) {
                    favoriteRepository.clearDislikeTrack(trackId)
                    _dislikedTrackIds.value = _dislikedTrackIds.value - trackId
                } else {
                    favoriteRepository.dislikeTrack(trackId)
                    _dislikedTrackIds.value = _dislikedTrackIds.value + trackId
                    // Mutual exclusion: disliking clears like (star)
                    _likedTrackIds.value = _likedTrackIds.value - trackId
                }
            } catch (_: Exception) {
                // leave state unchanged on failure
            }
        }
    }

    fun rateTrack(trackId: String, rating: Int) {
        val clamped = rating.coerceIn(0, 5)
        _trackRatings.value = _trackRatings.value + (trackId to clamped)
        viewModelScope.launch {
            try {
                // Local-first, then best-effort server sync (parity with Now Playing)
                trackDao.setRating(trackId, clamped)
                val authParams = auth.buildAuthParams(username, password)
                api.setRating(params = authParams, id = trackId, rating = clamped)
            } catch (_: Exception) {
            }
        }
    }

    // ── Album favorites (thumbs, like == server star) ───────────────────

    /** Toggle album like (star). Optimistic with rollback; mutual exclusion:
     *  liking clears dislike. Room flow refreshes the id sets on success. */
    fun toggleAlbumLike(albumId: String) {
        val liked = _state.value.likedAlbumIds.contains(albumId)
        val previous = _state.value
        _state.value = _state.value.copy(
            likedAlbumIds = if (liked) previous.likedAlbumIds - albumId else previous.likedAlbumIds + albumId,
            dislikedAlbumIds = previous.dislikedAlbumIds - albumId,
        )
        viewModelScope.launch {
            try {
                if (liked) {
                    favoriteRepository.unlikeAlbum(albumId)
                } else {
                    favoriteRepository.likeAlbum(albumId)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-artist", "toggleAlbumLike failed — rolled back", e)
                _state.value = previous
            }
        }
    }

    /** Toggle album dislike (local-only). Optimistic with rollback; mutual
     *  exclusion: disliking clears like (star). */
    fun toggleAlbumDislike(albumId: String) {
        val disliked = _state.value.dislikedAlbumIds.contains(albumId)
        val previous = _state.value
        _state.value = _state.value.copy(
            dislikedAlbumIds = if (disliked) {
                previous.dislikedAlbumIds - albumId
            } else {
                previous.dislikedAlbumIds +
                    albumId
            },
            likedAlbumIds = previous.likedAlbumIds - albumId,
        )
        viewModelScope.launch {
            try {
                if (disliked) {
                    favoriteRepository.clearDislikeAlbum(albumId)
                } else {
                    favoriteRepository.dislikeAlbum(albumId)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-artist", "toggleAlbumDislike failed — rolled back", e)
                _state.value = previous
            }
        }
    }

    // ── Playlist picker support (parity with Album detail) ─────────────

    private val _playlists = MutableStateFlow<List<PlaylistView>>(
        emptyList(),
    )
    val playlists = _playlists.asStateFlow()

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val local = playlistDao.getAll()
                _playlists.value = local.map {
                    com.lucasdss.ftpmusic.app.ui.library.PlaylistView(
                        id = it.id,
                        name = it.name,
                        trackCount = it.trackCount,
                        coverArt = it.coverArt,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-artist", "loadPlaylists failed: ${e.message}")
            }
        }
    }

    fun addToPlaylist(playlistId: String, trackIds: List<String>) {
        viewModelScope.launch {
            try {
                playlistRepo.addToPlaylist(playlistId, trackIds)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-artist", "addToPlaylist failed: ${e.message}")
            }
        }
    }
}
