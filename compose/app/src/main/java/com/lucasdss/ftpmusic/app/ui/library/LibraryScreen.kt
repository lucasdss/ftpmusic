package com.lucasdss.ftpmusic.app.ui.library

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.components.AlbumDownloadBadge
import com.lucasdss.ftpmusic.app.ui.components.ArtistAvatar
import com.lucasdss.ftpmusic.app.ui.components.CoverArtImage
import com.lucasdss.ftpmusic.app.ui.components.FavoriteThumbButton
import com.lucasdss.ftpmusic.app.ui.player.CastButton
import com.lucasdss.ftpmusic.app.ui.playlist.AddSongsPickerContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class LibraryTab(val label: String) {
    ALBUMS(
        "Albums",
    ),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    RADIO("Radio"),
}

/** Create-playlist sheet steps: name → created (+ Add Songs) → track picker. */
private enum class CreateSheetStep { NAME, CREATED, PICKER }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryContent(
    viewModel: LibraryViewModel = hiltViewModel(),
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onRadioStationClick: (RadioStation) -> Unit = {},
    currentAlbumId: String? = null,
    isPlaying: Boolean = false,
    initialTab: String = "albums",
    onOpenServerSettings: () -> Unit = {},
) {
    val shell by viewModel.libraryShellUi.collectAsStateWithLifecycle()
    // Full LibraryState is collected only in Playlists/Radio/sheets — Albums/Artists
    // use albumTabUi / artistTabUi so Home/mix ticks do not invalidate those grids.
    val initialLibraryTab = runCatching { LibraryTab.valueOf(initialTab.uppercase()) }.getOrDefault(LibraryTab.ALBUMS)
    var selectedTab by rememberSaveable(initialLibraryTab) { mutableStateOf(initialLibraryTab) }
    var searchQuery by rememberSaveable(selectedTab) { mutableStateOf("") }
    var showCreatePlaylistSheet by remember { mutableStateOf(false) }
    var showAddFromServerSheet by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var createStep by remember { mutableStateOf(CreateSheetStep.NAME) }
    var menuPlaylist by remember { mutableStateOf<PlaylistView?>(null) }
    var showRemovePlaylistConfirm by remember { mutableStateOf(false) }
    // The playlist targeted by the confirm dialog — captured when the menu item
    // is tapped, because menuPlaylist is cleared to close the menu.
    var pendingRemovePlaylist by remember { mutableStateOf<PlaylistView?>(null) }

    val context = LocalContext.current
    val coverArtFallback = remember { CoverArtFallbackService.getInstance(context) }

    Column(Modifier.background(Color(0xFF12121E))) {
        // Server config/reachability warning (stale proxy URL, unreachable server)
        com.lucasdss.ftpmusic.app.ui.components.ServerErrorBanner(
            configWarning = shell.configWarning,
            isOffline = viewModel.isOffline(),
            onOpenServerSettings = onOpenServerSettings,
        )
        // Refresh favorites (thumbs/bookmarks) whenever Library becomes visible
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                viewModel.loadFavorites()
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingS()),
            horizontalArrangement = Arrangement.spacedBy(spacingXS()),
        ) {
            LibraryTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                Box(
                    Modifier.weight(
                        1f,
                    ).clip(
                        RoundedCornerShape(10.dp),
                    ).background(if (selected) Color(0xFF2A2A2A) else Color(0xFF1C1C1C)).clickable {
                        selectedTab =
                            tab
                    }.padding(vertical = spacingS()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tab.label,
                        color = if (selected) Color.White else Color(0xFF666666),
                        fontSize = textBodyM(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        // Contextual search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search ${selectedTab.label}…", color = Color(0xFF666666)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF555555)) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    Icon(
                        Icons.Default.Close,
                        "Clear",
                        tint = Color(0xFF555555),
                        modifier = Modifier.clickable {
                            searchQuery = ""
                        },
                    )
                }
            } else {
                null
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color(0xFF1C1C1C),
                unfocusedContainerColor = Color(0xFF1C1C1C),
                cursorColor = Color(0xFF00C8B4),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingXS()),
            shape = RoundedCornerShape(cornerM()), singleLine = true,
        )
        // Data-first: spinner only until the first content render — a stalled
        // loader must never leave the screen spinning forever.
        if (shell.isLoading && !shell.hasLoadedOnce) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00C8B4))
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    LibraryTab.ALBUMS -> {
                        val albumTab by viewModel.albumTabUi.collectAsStateWithLifecycle()
                        LaunchedEffect(selectedTab) { viewModel.loadAlphaAlbums() }
                        LaunchedEffect(searchQuery) {
                            if (searchQuery.length >= 2) {
                                kotlinx.coroutines.delay(300)
                                viewModel.searchAlbums(searchQuery)
                            } else {
                                viewModel.clearAlbumSearch()
                            }
                        }
                        val displayedAlbums = albumTab.albumSearchResults ?: albumTab.albums
                        if (displayedAlbums.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No albums found", color = Color(0xFF666666), fontSize = textBodyM())
                            }
                        } else {
                            val gridState = rememberLazyGridState()
                            // No near-end loadMore: Albums tab loads the full alpha
                            // catalog via loadAlphaAlbums (see LIBRARY_SCROLL_PERF).
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.padding(horizontal = spacingM()),
                                contentPadding = PaddingValues(vertical = spacingS()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(displayedAlbums, key = { it.id }, contentType = { "album" }) { album ->
                                    val isActive = currentAlbumId != null && album.id == currentAlbumId && isPlaying
                                    Column(Modifier.clickable { onAlbumClick(album.id) }) {
                                        Box(
                                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(cornerM())),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            val url =
                                                rememberPreferredCoverArt(
                                                    album.coverArt,
                                                    album.artist,
                                                    album.name,
                                                    size = 300,
                                                    fallbackService = coverArtFallback,
                                                )
                                            if (url != null) {
                                                CoverArtImage(
                                                    url = url,
                                                    contentDescription = album.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    fallbackArtist = album.artist,
                                                    fallbackAlbum = album.name,
                                                    fallbackService = coverArtFallback,
                                                    decodeSize = 160.dp,
                                                )
                                            } else {
                                                Box(
                                                    Modifier.fillMaxSize().background(Color(0xFF1E1E1E)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        Icons.Default.Album,
                                                        null,
                                                        tint = Color(0xFF444444),
                                                        modifier = Modifier.size(40.dp),
                                                    )
                                                }
                                            }
                                            if (isActive) {
                                                Box(
                                                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    AnimatedEqBars()
                                                }
                                            }
                                            AlbumDownloadBadge(
                                                "none",
                                                Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                            )
                                            Row(
                                                Modifier.align(Alignment.TopStart).padding(6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                FavoriteThumbButton(
                                                    icon = Icons.Filled.ThumbUp,
                                                    active = album.id in albumTab.likedAlbumIds,
                                                    contentDescription = if (album.id in albumTab.likedAlbumIds) {
                                                        "Unlike album"
                                                    } else {
                                                        "Like album"
                                                    },
                                                    onClick = { viewModel.toggleAlbumLike(album.id) },
                                                )
                                                FavoriteThumbButton(
                                                    icon = Icons.Filled.ThumbDown,
                                                    active = album.id in albumTab.dislikedAlbumIds,
                                                    activeTint = Color(0xFFE84040),
                                                    contentDescription = if (album.id in albumTab.dislikedAlbumIds) {
                                                        "Remove dislike"
                                                    } else {
                                                        "Dislike album"
                                                    },
                                                    onClick = { viewModel.toggleAlbumDislike(album.id) },
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        if (album.rating != null && album.rating!! > 0) {
                                            StarRating(album.rating!!)
                                            Spacer(Modifier.height(4.dp))
                                        }
                                        Text(
                                            album.name,
                                            color = Color.White,
                                            fontSize = textBodyM(),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        album.artist?.let {
                                            Text(
                                                it,
                                                color = Color(0xFF888888),
                                                fontSize = textLabelM(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LibraryTab.ARTISTS -> {
                        val artistTab by viewModel.artistTabUi.collectAsStateWithLifecycle()
                        LaunchedEffect(selectedTab) { viewModel.loadArtists() }
                        LaunchedEffect(searchQuery) {
                            if (searchQuery.length >= 2) {
                                kotlinx.coroutines.delay(300)
                                viewModel.searchArtists(searchQuery)
                            } else {
                                viewModel.clearArtistSearch()
                            }
                        }
                        val displayedArtists = artistTab.artistSearchResults ?: artistTab.artists
                        if (displayedArtists.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No artists found", color = Color(0xFF666666), fontSize = textBodyM())
                            }
                        } else {
                            LazyColumn {
                                items(displayedArtists, key = { it.id }, contentType = { "artist" }) { artist ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            onArtistClick(artist.id)
                                        }.padding(horizontal = spacingL(), vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ArtistAvatar(
                                            artistName = artist.name,
                                            coverArtId = artist.coverArt,
                                            size = 52.dp,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                artist.name,
                                                color = Color.White,
                                                fontSize = textHeadingS(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            artist.albumCount?.let {
                                                Text("$it albums", color = Color(0xFF888888), fontSize = textLabelM())
                                            }
                                        }
                                        FavoriteThumbButton(
                                            icon = Icons.Filled.ThumbUp,
                                            active = artist.id in artistTab.likedArtistIds,
                                            contentDescription = if (artist.id in artistTab.likedArtistIds) {
                                                "Unlike artist"
                                            } else {
                                                "Like artist"
                                            },
                                            onClick = { viewModel.toggleArtistLike(artist.id) },
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        FavoriteThumbButton(
                                            icon = Icons.Filled.ThumbDown,
                                            active = artist.id in artistTab.dislikedArtistIds,
                                            activeTint = Color(0xFFE84040),
                                            contentDescription = if (artist.id in artistTab.dislikedArtistIds) {
                                                "Remove dislike"
                                            } else {
                                                "Dislike artist"
                                            },
                                            onClick = { viewModel.toggleArtistDislike(artist.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LibraryTab.PLAYLISTS -> {
                        val state by viewModel.state.collectAsStateWithLifecycle()
                        LaunchedEffect(selectedTab) { viewModel.loadPlaylists() }
                        val filteredPlaylists = if (searchQuery.isEmpty()) {
                            state.playlists
                        } else {
                            state.playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                        if (filteredPlaylists.isEmpty() && !state.isLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.QueueMusic,
                                        null,
                                        tint = Color(0xFF333333),
                                        modifier = Modifier.size(iconLarge()),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text("No playlists yet", color = Color(0xFF888888), fontSize = textBodyM())
                                }
                            }
                        } else {
                            LazyColumn {
                                items(filteredPlaylists) { pl ->
                                    Box {
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = { onPlaylistClick(pl.id) },
                                                    onLongClick = { menuPlaylist = pl },
                                                )
                                                .padding(horizontal = spacingL(), vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                Modifier.size(
                                                    52.dp,
                                                ).clip(RoundedCornerShape(cornerM())).background(Color(0xFF1E1E1E)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (pl.coverArt != null) {
                                                    val url = rememberCoverArtUrl(pl.coverArt, 120)
                                                    if (url !=
                                                        null
                                                    ) {
                                                        AsyncImage(
                                                            model = url,
                                                            contentDescription = null,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop,
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Default.QueueMusic,
                                                            null,
                                                            tint = Color(0xFF555555),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    }
                                                } else {
                                                    Icon(
                                                        Icons.Default.QueueMusic,
                                                        null,
                                                        tint = Color(0xFF555555),
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    pl.name,
                                                    color = Color.White,
                                                    fontSize = textHeadingS(),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "${pl.trackCount} tracks",
                                                        color = Color(0xFF888888),
                                                        fontSize = textLabelM(),
                                                    )
                                                    if (pl.isSynced) {
                                                        Spacer(Modifier.width(8.dp))
                                                        Surface(
                                                            color = Color(0xFF00C8B4).copy(alpha = 0.12f),
                                                            shape = RoundedCornerShape(4.dp),
                                                        ) {
                                                            Text(
                                                                "Synced",
                                                                modifier = Modifier.padding(
                                                                    horizontal = 5.dp,
                                                                    vertical = 1.dp,
                                                                ),
                                                                color = Color(0xFF00C8B4),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                            )
                                                        }
                                                    }
                                                    if (pl.isDownloaded) {
                                                        Spacer(Modifier.width(8.dp))
                                                        Surface(
                                                            color = Color(0xFFB040E8).copy(alpha = 0.12f),
                                                            shape = RoundedCornerShape(4.dp),
                                                        ) {
                                                            Text(
                                                                "Downloaded",
                                                                modifier = Modifier.padding(
                                                                    horizontal = 5.dp,
                                                                    vertical = 1.dp,
                                                                ),
                                                                color = Color(0xFFB040E8),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                null,
                                                tint = Color(0xFF444444),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        // Long-press menu
                                        DropdownMenu(
                                            expanded = menuPlaylist?.id == pl.id,
                                            onDismissRequest = { menuPlaylist = null },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Remove Locally") },
                                                onClick = {
                                                    pendingRemovePlaylist = pl
                                                    menuPlaylist = null
                                                    showRemovePlaylistConfirm = true
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFE84040))
                                                },
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        color = Color(0xFF1C1C2E),
                                        modifier = Modifier.padding(horizontal = spacingL()),
                                    )
                                }
                            }
                        }
                        // Create playlist FAB
                        Box(Modifier.fillMaxSize()) {
                            FloatingActionButton(
                                onClick = {
                                    viewModel.clearCreatedPlaylist()
                                    createStep = CreateSheetStep.NAME
                                    showCreatePlaylistSheet = true
                                },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                containerColor = Color(0xFF00C8B4),
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.White)
                            }
                        }
                    }

                    LibraryTab.RADIO -> {
                        val state by viewModel.state.collectAsStateWithLifecycle()
                        LaunchedEffect(selectedTab) { viewModel.loadRadioStations() }
                        val filteredRadio = if (searchQuery.isEmpty()) {
                            state.radioStations
                        } else {
                            state.radioStations.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                        if (filteredRadio.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.SettingsInputAntenna,
                                        null,
                                        tint = Color(0xFF333333),
                                        modifier = Modifier.size(iconLarge()),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text("No radio stations found", color = Color(0xFF888888), fontSize = textBodyM())
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "via getInternetRadioStations",
                                        color = Color(0xFF666666),
                                        fontSize = textLabelM(),
                                    )
                                }
                            }
                        } else {
                            LazyColumn {
                                item {
                                    Row(
                                        Modifier.padding(horizontal = spacingL(), vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.SettingsInputAntenna,
                                            null,
                                            tint = Color(0xFF00C8B4),
                                            modifier = Modifier.size(iconMicro()),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Internet Radio", color = Color(0xFF888888), fontSize = textLabelM())
                                    }
                                    HorizontalDivider(
                                        color = Color(0xFF1C1C2E),
                                        modifier = Modifier.padding(horizontal = spacingL()),
                                    )
                                }
                                items(filteredRadio) { station ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            onRadioStationClick(station)
                                        }.padding(horizontal = spacingL(), vertical = spacingM()),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier.size(
                                                iconLarge(),
                                            ).clip(RoundedCornerShape(cornerM())).background(Color(0xFF1E1E1E)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.SettingsInputAntenna,
                                                null,
                                                tint = Color(0xFF00C8B4),
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                station.name,
                                                color = Color.White,
                                                fontSize = textHeadingS(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                station.streamUrl,
                                                color = Color(0xFF888888),
                                                fontSize = textLabelM(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        // v43: Bookmark ribbon (local-only favorite)
                                        Icon(
                                            if (station.id in
                                                state.bookmarkedStationIds
                                            ) {
                                                Icons.Filled.Bookmark
                                            } else {
                                                Icons.Outlined.BookmarkBorder
                                            },
                                            if (station.id in
                                                state.bookmarkedStationIds
                                            ) {
                                                "Unbookmark station"
                                            } else {
                                                "Bookmark station"
                                            },
                                            tint = if (station.id in
                                                state.bookmarkedStationIds
                                            ) {
                                                Color(0xFF00C8B4)
                                            } else {
                                                Color(0xFF555555)
                                            },
                                            modifier = Modifier.size(18.dp).clickable {
                                                viewModel.toggleRadioBookmark(station)
                                            },
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Box(
                                            Modifier.size(
                                                36.dp,
                                            ).clip(CircleShape).background(Color(0xFF00C8B4).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                null,
                                                tint = Color(0xFF00C8B4),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        color = Color(0xFF1C1C2E),
                                        modifier = Modifier.padding(horizontal = spacingL()),
                                    )
                                }
                            }
                        }
                    }
                }
            } // close Box(Modifier.weight(1f))
        }
    }

    // ── Remove from device confirmation ──
    if (showRemovePlaylistConfirm) {
        AlertDialog(
            onDismissRequest = {
                pendingRemovePlaylist = null
                showRemovePlaylistConfirm = false
            },
            title = { Text("Remove from Device") },
            text = {
                Text("Remove \"${pendingRemovePlaylist?.name ?: ""}\" from this device? It stays on the server.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemovePlaylist?.let { viewModel.removePlaylistLocally(it.id) }
                    pendingRemovePlaylist = null
                    showRemovePlaylistConfirm = false
                }) {
                    Text("Remove", color = Color(0xFFE84040))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRemovePlaylist = null
                    showRemovePlaylistConfirm = false
                }) { Text("Cancel") }
            },
        )
    }

    if (showCreatePlaylistSheet) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        // Switch to the "created" step once the playlist exists
        LaunchedEffect(state.playlistCreated) {
            if (state.playlistCreated && showCreatePlaylistSheet && createStep == CreateSheetStep.NAME) {
                createStep = CreateSheetStep.CREATED
            }
        }
        ModalBottomSheet(
            onDismissRequest = {
                if (!state.isCreatingPlaylist) {
                    if (createStep == CreateSheetStep.PICKER) {
                        createStep = CreateSheetStep.CREATED
                    } else {
                        viewModel.clearCreatePlaylistError()
                        viewModel.clearCreatedPlaylist()
                        showCreatePlaylistSheet = false
                        newPlaylistName = ""
                        createStep = CreateSheetStep.NAME
                    }
                }
            },
            containerColor = Color(0xFF1C1C2E),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            when (createStep) {
                CreateSheetStep.NAME -> Column(Modifier.padding(bottom = 32.dp)) {
                    // Handle
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.width(
                                32.dp,
                            ).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)),
                        )
                    }
                    // Import from Server row
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { showAddFromServerSheet = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            null,
                            tint = Color(0xFF00C8B4),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Import from Server",
                            color = Color(0xFF00C8B4),
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = Color(0xFF444444),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    HorizontalDivider(color = Color(0xFF2A2A3E), modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(12.dp))
                    // Title
                    Text(
                        "Create Playlist",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    // Input
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = {
                            newPlaylistName = it
                            viewModel.clearCreatePlaylistError()
                        },
                        singleLine = true,
                        placeholder = { Text("Playlist name…", color = Color(0xFF666666)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00C8B4),
                            unfocusedBorderColor = Color(0xFF333344),
                            focusedContainerColor = Color(0xFF252538),
                            unfocusedContainerColor = Color(0xFF252538),
                            cursorColor = Color(0xFF00C8B4),
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                    )
                    // Error
                    if (state.createPlaylistError != null) {
                        Text(
                            state.createPlaylistError!!,
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Buttons: Cancel + Create & Sync
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                if (!state.isCreatingPlaylist) {
                                    viewModel.clearCreatePlaylistError()
                                    viewModel.clearCreatedPlaylist()
                                    showCreatePlaylistSheet = false
                                    newPlaylistName = ""
                                    createStep = CreateSheetStep.NAME
                                }
                            },
                            enabled = !state.isCreatingPlaylist,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252538)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(
                                "Cancel",
                                color = if (!state.isCreatingPlaylist) Color(0xFF888888) else Color(0xFF444444),
                            )
                        }
                        Button(
                            onClick = {
                                if (newPlaylistName.isNotBlank() && !state.isCreatingPlaylist) {
                                    viewModel.createPlaylist(newPlaylistName.trim())
                                }
                            },
                            enabled = newPlaylistName.isNotBlank() && !state.isCreatingPlaylist,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF00C8B4), Color(0xFFB040E8))),
                                    RoundedCornerShape(12.dp),
                                ),
                        ) {
                            if (state.isCreatingPlaylist) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Syncing…",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            } else {
                                Text(
                                    "Create & Sync",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    // API footer
                    Text(
                        "Synced via createPlaylist + savePlayQueue",
                        color = Color(0xFF666666),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }

                CreateSheetStep.CREATED -> Column(Modifier.padding(bottom = 32.dp)) {
                    // Handle
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.width(
                                32.dp,
                            ).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)),
                        )
                    }
                    Spacer(Modifier.height(spacingL()))
                    Text(
                        "Playlist Created",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    Text(
                        "\"${state.createdPlaylist?.name ?: ""}\" is ready. Add songs now or finish later.",
                        color = Color(0xFF888888),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                viewModel.clearCreatedPlaylist()
                                showCreatePlaylistSheet = false
                                newPlaylistName = ""
                                createStep = CreateSheetStep.NAME
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252538)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("Done", color = Color(0xFF888888)) }
                        Button(
                            onClick = { createStep = CreateSheetStep.PICKER },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF00C8B4), Color(0xFFB040E8))),
                                    RoundedCornerShape(12.dp),
                                ),
                        ) { Text("Add Songs", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }

                CreateSheetStep.PICKER -> AddSongsPickerContent(
                    existingTrackIds = emptySet(),
                    search = viewModel::searchPickerTracks,
                    suggestions = viewModel::pickerSuggestions,
                    onAdd = { trackIds ->
                        viewModel.addTracksToNewPlaylist(state.createdPlaylist?.id ?: "", trackIds)
                        android.widget.Toast.makeText(
                            context,
                            "Added ${trackIds.size} song${if (trackIds.size == 1) "" else "s"}",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        createStep = CreateSheetStep.CREATED
                    },
                    onDismiss = { createStep = CreateSheetStep.CREATED },
                )
            }
        }
    }

    // Add from Server sub-sheet
    if (showAddFromServerSheet) {
        val serverPlaylists by viewModel.serverPlaylists.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.loadServerPlaylists() }
        ModalBottomSheet(
            onDismissRequest = { showAddFromServerSheet = false },
            containerColor = Color(0xFF1C1C2E),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
                }
                Text(
                    "Add from Server",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
                if (serverPlaylists.isEmpty()) {
                    Text(
                        "No playlists found on server",
                        color = Color(0xFF888888),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                } else {
                    LazyColumn {
                        serverPlaylists.forEach { pl ->
                            item {
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        viewModel.importPlaylist(pl.id)
                                        showAddFromServerSheet =
                                            false
                                    }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        null,
                                        tint = Color(0xFF00C8B4),
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        pl.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("${pl.trackCount} tracks", color = Color(0xFF888888), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StarRating(rating: Int) {
    Row {
        for (i in 1..5) {
            Icon(
                imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (i <= rating) Color(0xFF00C8B4) else Color(0xFF444444),
                modifier = Modifier.size(iconMicro()),
            )
        }
    }
}

@Composable
private fun AnimatedEqBars() {
    val eqAnimation = rememberInfiniteTransition(label = "eqAlbum")
    Row(
        Modifier.width(24.dp).height(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val delays = listOf(0, 150, 300)
        val heights = listOf(0.5f, 0.7f, 1.0f)
        for (i in 0..2) {
            val anim by eqAnimation.animateFloat(
                initialValue = heights[i] * 0.3f,
                targetValue = heights[i],
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + delays[i], easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "eqAlbum$i",
            )
            Box(
                Modifier
                    .width(4.dp)
                    .height((16 * anim).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF00C8B4)),
            )
        }
    }
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onRadioStationClick: (RadioStation) -> Unit = {},
    currentAlbumId: String? = null,
    isPlaying: Boolean = false,
    initialTab: String = "albums",
    onOpenServerSettings: () -> Unit = {},
) = LibraryContent(
    viewModel,
    onArtistClick,
    onAlbumClick,
    onPlaylistClick,
    onRadioStationClick,
    currentAlbumId,
    isPlaying,
    initialTab,
    onOpenServerSettings,
)
