package com.lucasdss.ftpmusic.app.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.data.db.AlbumEntity
import com.lucasdss.ftpmusic.app.data.db.ArtistEntity
import com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.favorites.FavoritePendingKind
import com.lucasdss.ftpmusic.app.data.favorites.FavoritePendingStore
import com.lucasdss.ftpmusic.app.data.favorites.FavoritesPaging
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.SurfaceChip
import com.lucasdss.ftpmusic.app.ui.components.ArtistAvatar
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChip
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChipRow
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class FavoritesMode { LIKED, DISLIKED }

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val favoriteRepository: FavoriteRepository,
    private val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao,
    private val radioFavoriteDao: com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao,
    private val storage: SecureStorage,
) : ViewModel() {
    private val _state = MutableStateFlow(FavoritesState())
    val state: StateFlow<FavoritesState> = _state.asStateFlow()

    /** How many rows currently held per entity (liked / disliked). */
    private var loadedLikedTracks = FavoritesPaging.PAGE_SIZE
    private var loadedLikedAlbums = FavoritesPaging.PAGE_SIZE
    private var loadedLikedArtists = FavoritesPaging.PAGE_SIZE
    private var loadedDislikedTracks = FavoritesPaging.PAGE_SIZE
    private var loadedDislikedAlbums = FavoritesPaging.PAGE_SIZE
    private var loadedDislikedArtists = FavoritesPaging.PAGE_SIZE

    private val windowMutex = Mutex()
    private val trackPending = FavoritePendingStore()
    private val albumPending = FavoritePendingStore()
    private val artistPending = FavoritePendingStore()

    fun setMode(mode: FavoritesMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun load() {
        viewModelScope.launch {
            try {
                windowMutex.withLock {
                    refreshLikedWindow(forceMinPage = true)
                    refreshDislikedWindow(forceMinPage = true)
                    _state.value = _state.value.copy(
                        radio = radioFavoriteDao.getAll(),
                        showFavArtistsSection =
                            storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ARTISTS)?.toBooleanStrictOrNull() ?: true,
                        showFavAlbumsSection =
                            storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ALBUMS)?.toBooleanStrictOrNull() ?: true,
                        showFavRadioSection =
                            storage.get(SecureStorage.KEY_HOME_SHOW_FAV_RADIO)?.toBooleanStrictOrNull() ?: true,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "load failed", e)
            }
        }
    }

    /**
     * Room page-0 Flow invalidation → refresh the **already-loaded window**
     * (limit=loadedCount, offset=0) so appends survive and order stays correct.
     */
    fun observe() {
        viewModelScope.launch {
            try {
                val page = FavoritesPaging.PAGE_SIZE
                val liked = kotlinx.coroutines.flow.combine(
                    trackDao.getStarredFlow(page, 0),
                    metadataDao.getStarredAlbumsFlow(page, 0),
                    metadataDao.getStarredArtistsFlow(page, 0),
                    radioFavoriteDao.getAllFlow(),
                ) { tracks, albums, artists, radio ->
                    LikedBundle(tracks, albums, artists, radio)
                }
                val disliked = kotlinx.coroutines.flow.combine(
                    trackDao.getDislikedFlow(page, 0),
                    metadataDao.getDislikedAlbumsFlow(page, 0),
                    metadataDao.getDislikedArtistsFlow(page, 0),
                ) { tracks, albums, artists ->
                    DislikedBundle(tracks, albums, artists)
                }
                liked.combine(disliked) { _, _ ->
                    windowMutex.withLock {
                        refreshLikedWindow(forceMinPage = false)
                        refreshDislikedWindow(forceMinPage = false)
                        _state.value = _state.value.copy(radio = radioFavoriteDao.getAll())
                    }
                }.collect { }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "observe stopped", e)
            }
        }
    }

    /** Append next page for the active mode (Liked or Disliked). */
    fun loadMore() {
        if (_state.value.mode == FavoritesMode.LIKED) {
            loadMoreLiked()
        } else {
            loadMoreDisliked()
        }
    }

    private fun loadMoreLiked() {
        if (!_state.value.hasMoreLiked) return
        viewModelScope.launch {
            try {
                windowMutex.withLock {
                    if (!_state.value.hasMoreLiked) return@withLock
                    val page = FavoritesPaging.PAGE_SIZE
                    val moreTracks = if (_state.value.hasMoreLikedTracks) {
                        trackDao.getStarred(page, loadedLikedTracks)
                    } else {
                        emptyList()
                    }
                    val moreAlbums = if (_state.value.hasMoreLikedAlbums) {
                        metadataDao.getStarredAlbums(page, loadedLikedAlbums)
                    } else {
                        emptyList()
                    }
                    val moreArtists = if (_state.value.hasMoreLikedArtists) {
                        metadataDao.getStarredArtists(page, loadedLikedArtists)
                    } else {
                        emptyList()
                    }
                    if (moreTracks.isNotEmpty()) loadedLikedTracks += moreTracks.size
                    if (moreAlbums.isNotEmpty()) loadedLikedAlbums += moreAlbums.size
                    if (moreArtists.isNotEmpty()) loadedLikedArtists += moreArtists.size
                    val s = _state.value
                    val tracks =
                        applyTrackPendingLiked(
                            s.tracks + moreTracks.filter {
                                it.id !in s.tracks.map { t -> t.id }.toSet()
                            },
                        )
                    val albums =
                        applyAlbumPendingLiked(
                            s.albums + moreAlbums.filter {
                                it.id !in s.albums.map { a -> a.id }.toSet()
                            },
                        )
                    val artists =
                        applyArtistPendingLiked(
                            s.artists + moreArtists.filter {
                                it.id !in s.artists.map { a -> a.id }.toSet()
                            },
                        )
                    _state.value = s.copy(
                        tracks = tracks,
                        albums = albums,
                        artists = artists,
                        hasMoreLikedTracks = moreTracks.size >= page,
                        hasMoreLikedAlbums = moreAlbums.size >= page,
                        hasMoreLikedArtists = moreArtists.size >= page,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "loadMoreLiked failed", e)
            }
        }
    }

    private fun loadMoreDisliked() {
        if (!_state.value.hasMoreDisliked) return
        viewModelScope.launch {
            try {
                windowMutex.withLock {
                    if (!_state.value.hasMoreDisliked) return@withLock
                    val page = FavoritesPaging.PAGE_SIZE
                    val moreTracks = if (_state.value.hasMoreDislikedTracks) {
                        trackDao.getDisliked(page, loadedDislikedTracks)
                    } else {
                        emptyList()
                    }
                    val moreAlbums = if (_state.value.hasMoreDislikedAlbums) {
                        metadataDao.getDislikedAlbums(page, loadedDislikedAlbums)
                    } else {
                        emptyList()
                    }
                    val moreArtists = if (_state.value.hasMoreDislikedArtists) {
                        metadataDao.getDislikedArtists(page, loadedDislikedArtists)
                    } else {
                        emptyList()
                    }
                    if (moreTracks.isNotEmpty()) loadedDislikedTracks += moreTracks.size
                    if (moreAlbums.isNotEmpty()) loadedDislikedAlbums += moreAlbums.size
                    if (moreArtists.isNotEmpty()) loadedDislikedArtists += moreArtists.size
                    val s = _state.value
                    val tracks = applyTrackPendingDisliked(
                        s.dislikedTracks + moreTracks.filter { it.id !in s.dislikedTracks.map { t -> t.id }.toSet() },
                    )
                    val albums = applyAlbumPendingDisliked(
                        s.dislikedAlbums + moreAlbums.filter { it.id !in s.dislikedAlbums.map { a -> a.id }.toSet() },
                    )
                    val artists = applyArtistPendingDisliked(
                        s.dislikedArtists + moreArtists.filter {
                            it.id !in s.dislikedArtists.map { a -> a.id }.toSet()
                        },
                    )
                    _state.value = s.copy(
                        dislikedTracks = tracks,
                        dislikedAlbums = albums,
                        dislikedArtists = artists,
                        hasMoreDislikedTracks = moreTracks.size >= page,
                        hasMoreDislikedAlbums = moreAlbums.size >= page,
                        hasMoreDislikedArtists = moreArtists.size >= page,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "loadMoreDisliked failed", e)
            }
        }
    }

    private suspend fun refreshLikedWindow(forceMinPage: Boolean) {
        val page = FavoritesPaging.PAGE_SIZE
        if (forceMinPage) {
            loadedLikedTracks = maxOf(loadedLikedTracks, page)
            loadedLikedAlbums = maxOf(loadedLikedAlbums, page)
            loadedLikedArtists = maxOf(loadedLikedArtists, page)
        } else {
            // Preserve at least what UI already shows.
            loadedLikedTracks = maxOf(loadedLikedTracks, _state.value.tracks.size, page)
            loadedLikedAlbums = maxOf(loadedLikedAlbums, _state.value.albums.size, page)
            loadedLikedArtists = maxOf(loadedLikedArtists, _state.value.artists.size, page)
        }
        val tracks = trackDao.getStarred(loadedLikedTracks, 0)
        val albums = metadataDao.getStarredAlbums(loadedLikedAlbums, 0)
        val artists = metadataDao.getStarredArtists(loadedLikedArtists, 0)
        trackPending.reconcile(tracks.map { it.id }.toSet(), emptySet())
        albumPending.reconcile(albums.map { it.id }.toSet(), emptySet())
        artistPending.reconcile(artists.map { it.id }.toSet(), emptySet())
        // Probe one more row past the loaded window.
        val probeTracks = trackDao.getStarred(1, tracks.size)
        val probeAlbums = metadataDao.getStarredAlbums(1, albums.size)
        val probeArtists = metadataDao.getStarredArtists(1, artists.size)
        loadedLikedTracks = tracks.size.coerceAtLeast(page)
        loadedLikedAlbums = albums.size.coerceAtLeast(page)
        loadedLikedArtists = artists.size.coerceAtLeast(page)
        _state.value = _state.value.copy(
            tracks = applyTrackPendingLiked(tracks),
            albums = applyAlbumPendingLiked(albums),
            artists = applyArtistPendingLiked(artists),
            hasMoreLikedTracks = probeTracks.isNotEmpty(),
            hasMoreLikedAlbums = probeAlbums.isNotEmpty(),
            hasMoreLikedArtists = probeArtists.isNotEmpty(),
        )
    }

    private suspend fun refreshDislikedWindow(forceMinPage: Boolean) {
        val page = FavoritesPaging.PAGE_SIZE
        if (forceMinPage) {
            loadedDislikedTracks = maxOf(loadedDislikedTracks, page)
            loadedDislikedAlbums = maxOf(loadedDislikedAlbums, page)
            loadedDislikedArtists = maxOf(loadedDislikedArtists, page)
        } else {
            loadedDislikedTracks = maxOf(loadedDislikedTracks, _state.value.dislikedTracks.size, page)
            loadedDislikedAlbums = maxOf(loadedDislikedAlbums, _state.value.dislikedAlbums.size, page)
            loadedDislikedArtists = maxOf(loadedDislikedArtists, _state.value.dislikedArtists.size, page)
        }
        val tracks = trackDao.getDisliked(loadedDislikedTracks, 0)
        val albums = metadataDao.getDislikedAlbums(loadedDislikedAlbums, 0)
        val artists = metadataDao.getDislikedArtists(loadedDislikedArtists, 0)
        // Neutral pending = clear dislike (removed from disliked list)
        trackPending.reconcile(emptySet(), tracks.map { it.id }.toSet())
        albumPending.reconcile(emptySet(), albums.map { it.id }.toSet())
        artistPending.reconcile(emptySet(), artists.map { it.id }.toSet())
        val probeTracks = trackDao.getDisliked(1, tracks.size)
        val probeAlbums = metadataDao.getDislikedAlbums(1, albums.size)
        val probeArtists = metadataDao.getDislikedArtists(1, artists.size)
        loadedDislikedTracks = tracks.size.coerceAtLeast(page)
        loadedDislikedAlbums = albums.size.coerceAtLeast(page)
        loadedDislikedArtists = artists.size.coerceAtLeast(page)
        _state.value = _state.value.copy(
            dislikedTracks = applyTrackPendingDisliked(tracks),
            dislikedAlbums = applyAlbumPendingDisliked(albums),
            dislikedArtists = applyArtistPendingDisliked(artists),
            hasMoreDislikedTracks = probeTracks.isNotEmpty(),
            hasMoreDislikedAlbums = probeAlbums.isNotEmpty(),
            hasMoreDislikedArtists = probeArtists.isNotEmpty(),
        )
    }

    private fun applyTrackPendingLiked(room: List<TrackEntity>): List<TrackEntity> =
        room.filter { trackPending.get(it.id) != FavoritePendingKind.Neutral }

    private fun applyAlbumPendingLiked(room: List<AlbumEntity>): List<AlbumEntity> =
        room.filter { albumPending.get(it.id) != FavoritePendingKind.Neutral }

    private fun applyArtistPendingLiked(room: List<ArtistEntity>): List<ArtistEntity> =
        room.filter { artistPending.get(it.id) != FavoritePendingKind.Neutral }

    private fun applyTrackPendingDisliked(room: List<TrackEntity>): List<TrackEntity> =
        room.filter { trackPending.get(it.id) != FavoritePendingKind.Neutral }

    private fun applyAlbumPendingDisliked(room: List<AlbumEntity>): List<AlbumEntity> =
        room.filter { albumPending.get(it.id) != FavoritePendingKind.Neutral }

    private fun applyArtistPendingDisliked(room: List<ArtistEntity>): List<ArtistEntity> =
        room.filter { artistPending.get(it.id) != FavoritePendingKind.Neutral }

    private data class LikedBundle(
        val tracks: List<TrackEntity>,
        val albums: List<AlbumEntity>,
        val artists: List<ArtistEntity>,
        val radio: List<RadioFavoriteEntity>,
    )

    private data class DislikedBundle(
        val tracks: List<TrackEntity>,
        val albums: List<AlbumEntity>,
        val artists: List<ArtistEntity>,
    )

    init {
        observe()
    }

    fun unstarTrack(trackId: String) {
        val removed = _state.value.tracks.firstOrNull { it.id == trackId }
        trackPending.set(trackId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(tracks = _state.value.tracks.filter { it.id != trackId })
        viewModelScope.launch {
            try {
                favoriteRepository.unstarTrack(trackId)
            } catch (_: Exception) {
                trackPending.clear(trackId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        tracks = (_state.value.tracks + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }

    fun unlikeAlbum(albumId: String) {
        val removed = _state.value.albums.firstOrNull { it.id == albumId }
        albumPending.set(albumId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(albums = _state.value.albums.filter { it.id != albumId })
        viewModelScope.launch {
            try {
                favoriteRepository.unlikeAlbum(albumId)
            } catch (_: Exception) {
                albumPending.clear(albumId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        albums = (_state.value.albums + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }

    fun unlikeArtist(artistId: String) {
        val removed = _state.value.artists.firstOrNull { it.id == artistId }
        artistPending.set(artistId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(artists = _state.value.artists.filter { it.id != artistId })
        viewModelScope.launch {
            try {
                favoriteRepository.unlikeArtist(artistId)
            } catch (_: Exception) {
                artistPending.clear(artistId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        artists = (_state.value.artists + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }

    fun unbookmarkRadio(stationId: String) {
        val previous = _state.value.radio
        _state.value = _state.value.copy(radio = previous.filter { it.stationId != stationId })
        viewModelScope.launch {
            try {
                favoriteRepository.unbookmarkRadio(stationId)
            } catch (_: Exception) {
                _state.value = _state.value.copy(radio = previous)
            }
        }
    }

    fun clearDislikeTrack(trackId: String) {
        val removed = _state.value.dislikedTracks.firstOrNull { it.id == trackId }
        trackPending.set(trackId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(dislikedTracks = _state.value.dislikedTracks.filter { it.id != trackId })
        viewModelScope.launch {
            try {
                favoriteRepository.clearDislikeTrack(trackId)
            } catch (_: Exception) {
                trackPending.clear(trackId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        dislikedTracks = (_state.value.dislikedTracks + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }

    fun clearDislikeAlbum(albumId: String) {
        val removed = _state.value.dislikedAlbums.firstOrNull { it.id == albumId }
        albumPending.set(albumId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(dislikedAlbums = _state.value.dislikedAlbums.filter { it.id != albumId })
        viewModelScope.launch {
            try {
                favoriteRepository.clearDislikeAlbum(albumId)
            } catch (_: Exception) {
                albumPending.clear(albumId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        dislikedAlbums = (_state.value.dislikedAlbums + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }

    fun clearDislikeArtist(artistId: String) {
        val removed = _state.value.dislikedArtists.firstOrNull { it.id == artistId }
        artistPending.set(artistId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(dislikedArtists = _state.value.dislikedArtists.filter { it.id != artistId })
        viewModelScope.launch {
            try {
                favoriteRepository.clearDislikeArtist(artistId)
            } catch (_: Exception) {
                artistPending.clear(artistId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        dislikedArtists = (_state.value.dislikedArtists + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }
}

data class FavoritesState(
    val mode: FavoritesMode = FavoritesMode.LIKED,
    val tracks: List<TrackEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<ArtistEntity> = emptyList(),
    val radio: List<RadioFavoriteEntity> = emptyList(),
    val dislikedTracks: List<TrackEntity> = emptyList(),
    val dislikedAlbums: List<AlbumEntity> = emptyList(),
    val dislikedArtists: List<ArtistEntity> = emptyList(),
    val hasMoreLikedTracks: Boolean = false,
    val hasMoreLikedAlbums: Boolean = false,
    val hasMoreLikedArtists: Boolean = false,
    val hasMoreDislikedTracks: Boolean = false,
    val hasMoreDislikedAlbums: Boolean = false,
    val hasMoreDislikedArtists: Boolean = false,
    val showFavArtistsSection: Boolean = true,
    val showFavAlbumsSection: Boolean = true,
    val showFavRadioSection: Boolean = true,
) {
    val hasMoreLiked: Boolean
        get() = hasMoreLikedTracks || hasMoreLikedAlbums || hasMoreLikedArtists
    val hasMoreDisliked: Boolean
        get() = hasMoreDislikedTracks || hasMoreDislikedAlbums || hasMoreDislikedArtists
}

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onTrackClick: (TrackEntity) -> Unit = {},
    onAlbumClick: (AlbumEntity) -> Unit = {},
    onArtistClick: (ArtistEntity) -> Unit = {},
    onRadioStationClick: (RadioFavoriteEntity) -> Unit = {},
    currentTrackId: String? = null,
    currentAlbumId: String? = null,
    isPlaying: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.load()
        }
    }

    val dislikedHasAnything = state.dislikedTracks.isNotEmpty() ||
        (state.showFavArtistsSection && state.dislikedArtists.isNotEmpty()) ||
        (state.showFavAlbumsSection && state.dislikedAlbums.isNotEmpty())

    val likedHasAnything = state.tracks.isNotEmpty() ||
        (state.showFavArtistsSection && state.artists.isNotEmpty()) ||
        (state.showFavAlbumsSection && state.albums.isNotEmpty()) ||
        (state.showFavRadioSection && state.radio.isNotEmpty())

    val hasAnything = if (state.mode == FavoritesMode.LIKED) likedHasAnything else dislikedHasAnything

    Column(Modifier.fillMaxSize().background(Background)) {
        FavoritesModeChips(
            selected = state.mode,
            onSelect = viewModel::setMode,
        )

        if (!hasAnything) {
            Box(Modifier.fillMaxSize().testTag("favorites_empty"), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (state.mode == FavoritesMode.LIKED) {
                            Icons.Outlined.ThumbUp
                        } else {
                            Icons.Outlined.ThumbDown
                        },
                        null,
                        tint = Color(0xFF2A2A3E),
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (state.mode == FavoritesMode.LIKED) "No favorites yet" else "No dislikes yet",
                        color = Color(0xFF888888),
                        fontSize = textBodyM(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.mode == FavoritesMode.LIKED) {
                            "Like any track, album, or artist, or bookmark a radio station to add it here"
                        } else {
                            "Thumbs-down any track, album, or artist to keep it out of Daily Mixes"
                        },
                        color = Color(0xFF666666),
                        fontSize = textLabelM(),
                        modifier = Modifier.padding(horizontal = spacingXL()),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else if (state.mode == FavoritesMode.LIKED) {
            LikedFavoritesList(
                state = state,
                viewModel = viewModel,
                onTrackClick = onTrackClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onRadioStationClick = onRadioStationClick,
                currentTrackId = currentTrackId,
                currentAlbumId = currentAlbumId,
                isPlaying = isPlaying,
            )
        } else {
            DislikedFavoritesList(
                state = state,
                viewModel = viewModel,
                onTrackClick = onTrackClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                currentTrackId = currentTrackId,
                currentAlbumId = currentAlbumId,
                isPlaying = isPlaying,
            )
        }
    }
}

@Composable
private fun FavoritesModeChips(selected: FavoritesMode, onSelect: (FavoritesMode) -> Unit) {
    SegmentedChipRow(Modifier.testTag("favorites_mode_chips")) {
        SegmentedChip(
            label = "Liked",
            selected = selected == FavoritesMode.LIKED,
            onClick = { onSelect(FavoritesMode.LIKED) },
            modifier = Modifier.weight(1f).testTag("favorites_mode_liked"),
        )
        SegmentedChip(
            label = "Disliked",
            selected = selected == FavoritesMode.DISLIKED,
            onClick = { onSelect(FavoritesMode.DISLIKED) },
            modifier = Modifier.weight(1f).testTag("favorites_mode_disliked"),
        )
    }
}

@Composable
private fun LikedFavoritesList(
    state: FavoritesState,
    viewModel: FavoritesViewModel,
    onTrackClick: (TrackEntity) -> Unit,
    onAlbumClick: (AlbumEntity) -> Unit,
    onArtistClick: (ArtistEntity) -> Unit,
    onRadioStationClick: (RadioFavoriteEntity) -> Unit,
    currentTrackId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.hasMoreLiked) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && last >= total - 3
        }.collect { nearEnd ->
            if (nearEnd && state.hasMoreLiked) viewModel.loadMore()
        }
    }
    LazyColumn(Modifier.testTag("favorites_liked_list"), state = listState) {
        if (state.tracks.isNotEmpty()) {
            item { FavoriteSectionHeader("Tracks", Icons.Filled.ThumbUp) }
            items(state.tracks, key = { "liked_t_${it.id}" }) { track ->
                TrackFavoriteRow(
                    track = track,
                    isActive = currentTrackId != null && track.id == currentTrackId,
                    actionIcon = Icons.Filled.ThumbUp,
                    actionTint = BrandTeal,
                    actionCd = "Unlike",
                    onRowClick = { onTrackClick(track) },
                    onAction = { viewModel.unstarTrack(track.id) },
                )
            }
        }
        if (state.showFavArtistsSection && state.artists.isNotEmpty()) {
            item { FavoriteSectionHeader("Artists", Icons.Filled.ThumbUp) }
            items(state.artists, key = { "liked_ar_${it.id}" }) { artist ->
                ArtistFavoriteRow(
                    artist = artist,
                    actionIcon = Icons.Filled.ThumbUp,
                    actionTint = BrandTeal,
                    actionCd = "Unlike artist",
                    onRowClick = { onArtistClick(artist) },
                    onAction = { viewModel.unlikeArtist(artist.id) },
                )
            }
        }
        if (state.showFavAlbumsSection && state.albums.isNotEmpty()) {
            item { FavoriteSectionHeader("Albums", Icons.Filled.ThumbUp) }
            items(state.albums, key = { "liked_al_${it.id}" }) { album ->
                AlbumFavoriteRow(
                    album = album,
                    isActive = currentAlbumId != null && album.id == currentAlbumId && isPlaying,
                    actionIcon = Icons.Filled.ThumbUp,
                    actionTint = BrandTeal,
                    actionCd = "Unlike album",
                    onRowClick = { onAlbumClick(album) },
                    onAction = { viewModel.unlikeAlbum(album.id) },
                )
            }
        }
        if (state.showFavRadioSection && state.radio.isNotEmpty()) {
            item { FavoriteSectionHeader("Radio", Icons.Filled.Bookmark) }
            items(state.radio, key = { "liked_r_${it.stationId}" }) { station ->
                RadioFavoriteRow(
                    station = station,
                    onRowClick = { onRadioStationClick(station) },
                    onUnbookmark = { viewModel.unbookmarkRadio(station.stationId) },
                )
            }
        }
        if (state.hasMoreLiked) {
            item {
                TextButton(
                    onClick = { viewModel.loadMore() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("favorites_load_more_liked"),
                ) {
                    Text("Load more", color = BrandTeal)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DislikedFavoritesList(
    state: FavoritesState,
    viewModel: FavoritesViewModel,
    onTrackClick: (TrackEntity) -> Unit,
    onAlbumClick: (AlbumEntity) -> Unit,
    onArtistClick: (ArtistEntity) -> Unit,
    currentTrackId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
) {
    val dislikeTint = Color(0xFFE84040)
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.hasMoreDisliked) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && last >= total - 3
        }.collect { nearEnd ->
            if (nearEnd && state.hasMoreDisliked) viewModel.loadMore()
        }
    }
    LazyColumn(Modifier.testTag("favorites_disliked_list"), state = listState) {
        if (state.dislikedTracks.isNotEmpty()) {
            item { FavoriteSectionHeader("Tracks", Icons.Filled.ThumbDown, tint = dislikeTint) }
            items(state.dislikedTracks, key = { "dis_t_${it.id}" }) { track ->
                TrackFavoriteRow(
                    track = track,
                    isActive = currentTrackId != null && track.id == currentTrackId,
                    actionIcon = Icons.Filled.ThumbDown,
                    actionTint = dislikeTint,
                    actionCd = "Remove dislike",
                    onRowClick = { onTrackClick(track) },
                    onAction = { viewModel.clearDislikeTrack(track.id) },
                )
            }
        }
        if (state.showFavArtistsSection && state.dislikedArtists.isNotEmpty()) {
            item { FavoriteSectionHeader("Artists", Icons.Filled.ThumbDown, tint = dislikeTint) }
            items(state.dislikedArtists, key = { "dis_ar_${it.id}" }) { artist ->
                ArtistFavoriteRow(
                    artist = artist,
                    actionIcon = Icons.Filled.ThumbDown,
                    actionTint = dislikeTint,
                    actionCd = "Remove artist dislike",
                    onRowClick = { onArtistClick(artist) },
                    onAction = { viewModel.clearDislikeArtist(artist.id) },
                )
            }
        }
        if (state.showFavAlbumsSection && state.dislikedAlbums.isNotEmpty()) {
            item { FavoriteSectionHeader("Albums", Icons.Filled.ThumbDown, tint = dislikeTint) }
            items(state.dislikedAlbums, key = { "dis_al_${it.id}" }) { album ->
                AlbumFavoriteRow(
                    album = album,
                    isActive = currentAlbumId != null && album.id == currentAlbumId && isPlaying,
                    actionIcon = Icons.Filled.ThumbDown,
                    actionTint = dislikeTint,
                    actionCd = "Remove album dislike",
                    onRowClick = { onAlbumClick(album) },
                    onAction = { viewModel.clearDislikeAlbum(album.id) },
                )
            }
        }
        if (state.hasMoreDisliked) {
            item {
                TextButton(
                    onClick = { viewModel.loadMore() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("favorites_load_more_disliked"),
                ) {
                    Text("Load more", color = BrandTeal)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TrackFavoriteRow(
    track: TrackEntity,
    isActive: Boolean,
    actionIcon: ImageVector,
    actionTint: Color,
    actionCd: String,
    onRowClick: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onRowClick)
            .padding(horizontal = spacingL(), vertical = 10.dp)
            .testTag("fav_track_${track.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val trackCoverUrl = rememberCoverArtUrl(track.coverArtUrl, 200)
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            if (trackCoverUrl != null) {
                AsyncImage(
                    model = trackCoverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = NavUnselected,
                    modifier = Modifier.size(iconSmall()),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FittingText(
                text = track.title,
                color = if (isActive) BrandTeal else Color.White,
                fontSize = textHeadingS(),
                minFontSize = textMicro(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
            track.artist?.let {
                FittingText(
                    text = it,
                    color = Color(0xFF888888),
                    fontSize = textLabelM(),
                    minFontSize = textMicro(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val ds = com.lucasdss.ftpmusic.app.ui.components.downloadStatus(
            track.isDownloaded,
            false,
            track.cachedFilePath != null,
        )
        if (ds != "none") {
            com.lucasdss.ftpmusic.app.ui.components.DownloadDot(ds)
            Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            actionIcon,
            contentDescription = actionCd,
            tint = actionTint,
            modifier = Modifier.size(16.dp).clickable(onClick = onAction),
        )
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.padding(horizontal = spacingL()),
    )
}

@Composable
private fun ArtistFavoriteRow(
    artist: ArtistEntity,
    actionIcon: ImageVector,
    actionTint: Color,
    actionCd: String,
    onRowClick: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onRowClick)
            .padding(horizontal = spacingL(), vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistAvatar(artistName = artist.name, coverArtId = artist.coverArtUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FittingText(
                text = artist.name,
                color = Color.White,
                fontSize = textBodyM(),
                minFontSize = textMicro(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Artist", color = Color(0xFF888888), fontSize = textLabelM())
        }
        Icon(
            actionIcon,
            contentDescription = actionCd,
            tint = actionTint,
            modifier = Modifier.size(16.dp).clickable(onClick = onAction),
        )
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.padding(horizontal = spacingL()),
    )
}

@Composable
private fun AlbumFavoriteRow(
    album: AlbumEntity,
    isActive: Boolean,
    actionIcon: ImageVector,
    actionTint: Color,
    actionCd: String,
    onRowClick: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onRowClick)
            .padding(horizontal = spacingL(), vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            val url = rememberCoverArtUrl(album.coverArtUrl, 200)
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.Album,
                    null,
                    tint = NavUnselected,
                    modifier = Modifier.size(iconSmall()),
                )
            }
            if (isActive) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        listOf(0.4f, 0.7f, 1.0f).forEachIndexed { i, h ->
                            Box(
                                Modifier.width(2.dp).height((16 * h).dp)
                                    .clip(RoundedCornerShape(1.dp)).background(BrandTeal),
                            )
                            if (i < 2) Spacer(Modifier.width(2.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FittingText(
                text = album.name,
                color = if (isActive) BrandTeal else Color.White,
                fontSize = textBodyM(),
                minFontSize = textMicro(),
                modifier = Modifier.fillMaxWidth(),
            )
            FittingText(
                text = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
                color = Color(0xFF888888),
                fontSize = textLabelM(),
                minFontSize = textMicro(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Icon(
            actionIcon,
            contentDescription = actionCd,
            tint = actionTint,
            modifier = Modifier.size(16.dp).clickable(onClick = onAction),
        )
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.padding(horizontal = spacingL()),
    )
}

@Composable
private fun RadioFavoriteRow(station: RadioFavoriteEntity, onRowClick: () -> Unit, onUnbookmark: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onRowClick)
            .padding(horizontal = spacingL(), vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.SettingsInputAntenna,
                null,
                tint = BrandTeal,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FittingText(
                text = station.name,
                color = Color.White,
                fontSize = textBodyM(),
                minFontSize = textMicro(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SettingsInputAntenna,
                    null,
                    tint = BrandTeal,
                    modifier = Modifier.size(10.dp),
                )
                Spacer(Modifier.width(4.dp))
                FittingText(
                    text = station.streamUrl,
                    color = Color(0xFF888888),
                    fontSize = textLabelM(),
                    minFontSize = textMicro(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Icon(
            Icons.Filled.Bookmark,
            contentDescription = "Unbookmark station",
            tint = BrandTeal,
            modifier = Modifier.size(16.dp).clickable(onClick = onUnbookmark),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(BrandTeal.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                null,
                tint = BrandTeal,
                modifier = Modifier.size(14.dp),
            )
        }
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.padding(horizontal = spacingL()),
    )
}

/** Teal uppercase section header with a favorite icon — design's SectionHead. */
@Composable
private fun FavoriteSectionHeader(label: String, icon: ImageVector, tint: Color = BrandTeal) {
    Row(
        Modifier.padding(start = spacingL(), end = spacingL(), top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label.uppercase(),
            color = tint,
            fontSize = textLabelM(),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}
