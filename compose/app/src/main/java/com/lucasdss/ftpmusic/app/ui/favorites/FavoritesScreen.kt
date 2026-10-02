package com.lucasdss.ftpmusic.app.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    /** Items loaded beyond the Flow-backed first page (liked). */
    private var likedAppendedOffset = FavoritesPaging.PAGE_SIZE
    private var dislikedAppendedOffset = FavoritesPaging.PAGE_SIZE
    private var loadingMoreLiked = false
    private var loadingMoreDisliked = false

    fun setMode(mode: FavoritesMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun load() {
        viewModelScope.launch {
            try {
                val page = FavoritesPaging.PAGE_SIZE
                _state.value = _state.value.copy(
                    tracks = trackDao.getStarred(page, 0),
                    albums = metadataDao.getStarredAlbums(page, 0),
                    artists = metadataDao.getStarredArtists(page, 0),
                    radio = radioFavoriteDao.getAll(),
                    dislikedTracks = trackDao.getDisliked(page, 0),
                    dislikedAlbums = metadataDao.getDislikedAlbums(page, 0),
                    dislikedArtists = metadataDao.getDislikedArtists(page, 0),
                    hasMoreLiked = true,
                    hasMoreDisliked = true,
                    showFavArtistsSection =
                        storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ARTISTS)?.toBooleanStrictOrNull() ?: true,
                    showFavAlbumsSection =
                        storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ALBUMS)?.toBooleanStrictOrNull() ?: true,
                    showFavRadioSection =
                        storage.get(SecureStorage.KEY_HOME_SHOW_FAV_RADIO)?.toBooleanStrictOrNull() ?: true,
                )
                likedAppendedOffset = FavoritesPaging.PAGE_SIZE
                dislikedAppendedOffset = FavoritesPaging.PAGE_SIZE
            } catch (_: Exception) {}
        }
    }

    /** Reactive first page — Room Flow so the tab updates live. Appended pages
     *  survive until the next first-page emit resets offsets (user re-taps Load more). */
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
                liked.combine(disliked) { l, d ->
                    likedAppendedOffset = FavoritesPaging.PAGE_SIZE
                    dislikedAppendedOffset = FavoritesPaging.PAGE_SIZE
                    _state.value = _state.value.copy(
                        tracks = l.tracks,
                        albums = l.albums,
                        artists = l.artists,
                        radio = l.radio,
                        dislikedTracks = d.tracks,
                        dislikedAlbums = d.albums,
                        dislikedArtists = d.artists,
                        hasMoreLiked = l.tracks.size >= page ||
                            l.albums.size >= page ||
                            l.artists.size >= page,
                        hasMoreDisliked = d.tracks.size >= page ||
                            d.albums.size >= page ||
                            d.artists.size >= page,
                    )
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
        if (loadingMoreLiked || !_state.value.hasMoreLiked) return
        loadingMoreLiked = true
        viewModelScope.launch {
            try {
                val page = FavoritesPaging.PAGE_SIZE
                val offset = likedAppendedOffset
                val moreTracks = trackDao.getStarred(page, offset)
                val moreAlbums = metadataDao.getStarredAlbums(page, offset)
                val moreArtists = metadataDao.getStarredArtists(page, offset)
                val existingTrackIds = _state.value.tracks.map { it.id }.toSet()
                val existingAlbumIds = _state.value.albums.map { it.id }.toSet()
                val existingArtistIds = _state.value.artists.map { it.id }.toSet()
                likedAppendedOffset = offset + page
                _state.value = _state.value.copy(
                    tracks = _state.value.tracks + moreTracks.filter { it.id !in existingTrackIds },
                    albums = _state.value.albums + moreAlbums.filter { it.id !in existingAlbumIds },
                    artists = _state.value.artists + moreArtists.filter { it.id !in existingArtistIds },
                    hasMoreLiked = moreTracks.size >= page ||
                        moreAlbums.size >= page ||
                        moreArtists.size >= page,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "loadMoreLiked failed", e)
            } finally {
                loadingMoreLiked = false
            }
        }
    }

    private fun loadMoreDisliked() {
        if (loadingMoreDisliked || !_state.value.hasMoreDisliked) return
        loadingMoreDisliked = true
        viewModelScope.launch {
            try {
                val page = FavoritesPaging.PAGE_SIZE
                val offset = dislikedAppendedOffset
                val moreTracks = trackDao.getDisliked(page, offset)
                val moreAlbums = metadataDao.getDislikedAlbums(page, offset)
                val moreArtists = metadataDao.getDislikedArtists(page, offset)
                val existingTrackIds = _state.value.dislikedTracks.map { it.id }.toSet()
                val existingAlbumIds = _state.value.dislikedAlbums.map { it.id }.toSet()
                val existingArtistIds = _state.value.dislikedArtists.map { it.id }.toSet()
                dislikedAppendedOffset = offset + page
                _state.value = _state.value.copy(
                    dislikedTracks = _state.value.dislikedTracks +
                        moreTracks.filter { it.id !in existingTrackIds },
                    dislikedAlbums = _state.value.dislikedAlbums +
                        moreAlbums.filter { it.id !in existingAlbumIds },
                    dislikedArtists = _state.value.dislikedArtists +
                        moreArtists.filter { it.id !in existingArtistIds },
                    hasMoreDisliked = moreTracks.size >= page ||
                        moreAlbums.size >= page ||
                        moreArtists.size >= page,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "loadMoreDisliked failed", e)
            } finally {
                loadingMoreDisliked = false
            }
        }
    }

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
        val previousTracks = _state.value.tracks
        _state.value = _state.value.copy(tracks = previousTracks.filter { it.id != trackId })
        viewModelScope.launch {
            try {
                favoriteRepository.unstarTrack(trackId)
            } catch (_: Exception) {
                _state.value = _state.value.copy(tracks = previousTracks)
            }
        }
    }

    fun unlikeAlbum(albumId: String) {
        val previous = _state.value.albums
        _state.value = _state.value.copy(albums = previous.filter { it.id != albumId })
        viewModelScope.launch {
            try {
                favoriteRepository.unlikeAlbum(albumId)
            } catch (_: Exception) {
                _state.value = _state.value.copy(albums = previous)
            }
        }
    }

    fun unlikeArtist(artistId: String) {
        val previous = _state.value.artists
        _state.value = _state.value.copy(artists = previous.filter { it.id != artistId })
        viewModelScope.launch {
            try {
                favoriteRepository.unlikeArtist(artistId)
            } catch (_: Exception) {
                _state.value = _state.value.copy(artists = previous)
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
        val previous = _state.value.dislikedTracks
        _state.value = _state.value.copy(dislikedTracks = previous.filter { it.id != trackId })
        viewModelScope.launch {
            try {
                favoriteRepository.clearDislikeTrack(trackId)
            } catch (_: Exception) {
                _state.value = _state.value.copy(dislikedTracks = previous)
            }
        }
    }

    fun clearDislikeAlbum(albumId: String) {
        val previous = _state.value.dislikedAlbums
        _state.value = _state.value.copy(dislikedAlbums = previous.filter { it.id != albumId })
        viewModelScope.launch {
            try {
                favoriteRepository.clearDislikeAlbum(albumId)
            } catch (_: Exception) {
                _state.value = _state.value.copy(dislikedAlbums = previous)
            }
        }
    }

    fun clearDislikeArtist(artistId: String) {
        val previous = _state.value.dislikedArtists
        _state.value = _state.value.copy(dislikedArtists = previous.filter { it.id != artistId })
        viewModelScope.launch {
            try {
                favoriteRepository.clearDislikeArtist(artistId)
            } catch (_: Exception) {
                _state.value = _state.value.copy(dislikedArtists = previous)
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
    val hasMoreLiked: Boolean = false,
    val hasMoreDisliked: Boolean = false,
    val showFavArtistsSection: Boolean = true,
    val showFavAlbumsSection: Boolean = true,
    val showFavRadioSection: Boolean = true,
)

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
    LazyColumn(Modifier.testTag("favorites_liked_list")) {
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
    LazyColumn(Modifier.testTag("favorites_disliked_list")) {
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
