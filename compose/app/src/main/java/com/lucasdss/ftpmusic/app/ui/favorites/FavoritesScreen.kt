package com.lucasdss.ftpmusic.app.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.components.ArtistAvatar
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun load() {
        viewModelScope.launch {
            try {
                val tracks = trackDao.getStarred(50)
                val albums = metadataDao.getStarredAlbums(50)
                val artists = metadataDao.getStarredArtists(50)
                val radio = radioFavoriteDao.getAll()
                _state.value = FavoritesState(
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
                    radio = radio,
                    showFavArtistsSection =
                        storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ARTISTS)?.toBooleanStrictOrNull() ?: true,
                    showFavAlbumsSection =
                        storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ALBUMS)?.toBooleanStrictOrNull() ?: true,
                    showFavRadioSection =
                        storage.get(SecureStorage.KEY_HOME_SHOW_FAV_RADIO)?.toBooleanStrictOrNull() ?: true,
                )
            } catch (_: Exception) {}
        }
    }

    /** Reactive favorites: Room Flow observation so the tab updates live when
     *  favorites change from any screen — no resume dependency. */
    fun observe() {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.flow.combine(
                    trackDao.getStarredFlow(50),
                    metadataDao.getStarredAlbumsFlow(50),
                    metadataDao.getStarredArtistsFlow(50),
                    radioFavoriteDao.getAllFlow(),
                ) { tracks, albums, artists, radio ->
                    _state.value = _state.value.copy(
                        tracks = tracks,
                        albums = albums,
                        artists = artists,
                        radio = radio,
                    )
                }.collect { }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "observe stopped", e)
            }
        }
    }

    init {
        observe()
    }

    fun unstarTrack(trackId: String) {
        // Optimistic: remove immediately, restore on failure
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

    /** Remove a liked album (optimistic, rollback on failure). */
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

    /** Remove a liked artist (optimistic, rollback on failure). */
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

    /** Remove a bookmarked radio station (optimistic, rollback on failure). */
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
}

data class FavoritesState(
    val tracks: List<TrackEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<ArtistEntity> = emptyList(),
    val radio: List<RadioFavoriteEntity> = emptyList(),
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

    val hasAnything = state.tracks.isNotEmpty() ||
        (state.showFavArtistsSection && state.artists.isNotEmpty()) ||
        (state.showFavAlbumsSection && state.albums.isNotEmpty()) ||
        (state.showFavRadioSection && state.radio.isNotEmpty())

    Column(Modifier.fillMaxSize().background(Color(0xFF12121E))) {
        if (!hasAnything) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.FavoriteBorder, null, tint = Color(0xFF2A2A3E), modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No favorites yet", color = Color(0xFF888888), fontSize = textBodyM())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Like any track, album, or artist, or bookmark a radio station to add it here",
                        color = Color(0xFF666666),
                        fontSize = textLabelM(),
                        modifier = Modifier.padding(horizontal = spacingXL()),
                    )
                }
            }
        } else {
            LazyColumn {
                // ── Tracks ──
                if (state.tracks.isNotEmpty()) {
                    item { FavoriteSectionHeader("Tracks", Icons.Filled.ThumbUp) }
                    items(state.tracks, key = { it.id }) { track ->
                        val isActive = currentTrackId != null && track.id == currentTrackId
                        Row(
                            Modifier.fillMaxWidth().clickable { onTrackClick(track) }
                                .padding(horizontal = spacingL(), vertical = 10.dp),
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
                                        tint = Color(0xFF555555),
                                        modifier = Modifier.size(iconSmall()),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    color = if (isActive) Color(0xFF00C8B4) else Color.White,
                                    fontSize = textBodyM(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                track.artist?.let {
                                    Text(
                                        it,
                                        color = Color(0xFF888888),
                                        fontSize = textLabelM(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
                                Icons.Filled.ThumbUp,
                                contentDescription = "Unlike",
                                tint = Color(0xFF00C8B4),
                                modifier = Modifier.size(16.dp).clickable { viewModel.unstarTrack(track.id) },
                            )
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = spacingL()),
                        )
                    }
                }

                // ── Artists ──
                if (state.showFavArtistsSection && state.artists.isNotEmpty()) {
                    item { FavoriteSectionHeader("Artists", Icons.Filled.ThumbUp) }
                    items(state.artists, key = { it.id }) { artist ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onArtistClick(artist) }
                                .padding(horizontal = spacingL(), vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtistAvatar(artistName = artist.name, coverArtId = artist.coverArtUrl, size = 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    artist.name,
                                    color = Color.White,
                                    fontSize = textBodyM(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("Artist", color = Color(0xFF888888), fontSize = textLabelM())
                            }
                            Icon(
                                Icons.Filled.ThumbUp,
                                contentDescription = "Unlike artist",
                                tint = Color(0xFF00C8B4),
                                modifier = Modifier.size(16.dp).clickable { viewModel.unlikeArtist(artist.id) },
                            )
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = spacingL()),
                        )
                    }
                }

                // ── Albums ──
                if (state.showFavAlbumsSection && state.albums.isNotEmpty()) {
                    item { FavoriteSectionHeader("Albums", Icons.Filled.ThumbUp) }
                    items(state.albums, key = { it.id }) { album ->
                        val isActive = currentAlbumId != null && album.id == currentAlbumId && isPlaying
                        Row(
                            Modifier.fillMaxWidth().clickable { onAlbumClick(album) }
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
                                        tint = Color(0xFF555555),
                                        modifier = Modifier.size(iconSmall()),
                                    )
                                }
                                if (isActive) {
                                    Box(
                                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        // Mini EQ — static 3-bar indicator (animation would burn battery in a list)
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            listOf(0.4f, 0.7f, 1.0f).forEachIndexed { i, h ->
                                                Box(
                                                    Modifier.width(2.dp).height((16 * h).dp)
                                                        .clip(RoundedCornerShape(1.dp)).background(Color(0xFF00C8B4)),
                                                )
                                                if (i < 2) Spacer(Modifier.width(2.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    album.name,
                                    color = if (isActive) Color(0xFF00C8B4) else Color.White,
                                    fontSize = textBodyM(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
                                    color = Color(0xFF888888),
                                    fontSize = textLabelM(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.Filled.ThumbUp,
                                contentDescription = "Unlike album",
                                tint = Color(0xFF00C8B4),
                                modifier = Modifier.size(16.dp).clickable { viewModel.unlikeAlbum(album.id) },
                            )
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = spacingL()),
                        )
                    }
                }

                // ── Radio ──
                if (state.showFavRadioSection && state.radio.isNotEmpty()) {
                    item { FavoriteSectionHeader("Radio", Icons.Filled.Bookmark) }
                    items(state.radio, key = { it.stationId }) { station ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onRadioStationClick(station) }
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
                                    tint = Color(0xFF00C8B4),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    station.name,
                                    color = Color.White,
                                    fontSize = textBodyM(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.SettingsInputAntenna,
                                        null,
                                        tint = Color(0xFF00C8B4),
                                        modifier = Modifier.size(10.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        station.streamUrl,
                                        color = Color(0xFF888888),
                                        fontSize = textLabelM(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = "Unbookmark station",
                                tint = Color(0xFF00C8B4),
                                modifier = Modifier.size(16.dp).clickable {
                                    viewModel.unbookmarkRadio(station.stationId)
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(
                                    32.dp,
                                ).clip(CircleShape).background(Color(0xFF00C8B4).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    null,
                                    tint = Color(0xFF00C8B4),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = spacingL()),
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/** Teal uppercase section header with a favorite icon — design's SectionHead. */
@Composable
private fun FavoriteSectionHeader(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.padding(start = spacingL(), end = spacingL(), top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color(0xFF00C8B4), modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label.uppercase(),
            color = Color(0xFF00C8B4),
            fontSize = textLabelM(),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}
