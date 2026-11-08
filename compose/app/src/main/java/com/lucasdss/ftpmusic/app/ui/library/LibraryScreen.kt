package com.lucasdss.ftpmusic.app.ui.library

import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
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
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.components.AlbumDownloadBadge
import com.lucasdss.ftpmusic.app.ui.components.ArtistAvatar
import com.lucasdss.ftpmusic.app.ui.components.CollectionCoverArt
import com.lucasdss.ftpmusic.app.ui.components.CollectionCoverSubject
import com.lucasdss.ftpmusic.app.ui.components.CoverArtImage
import com.lucasdss.ftpmusic.app.ui.components.FavoriteThumbButton
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import com.lucasdss.ftpmusic.app.ui.components.RadioBookmarkIcon
import com.lucasdss.ftpmusic.app.ui.components.ScrollAwareEqBars
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChip
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChipRow
import com.lucasdss.ftpmusic.app.ui.components.rememberCollectionCoverEditController
import com.lucasdss.ftpmusic.app.ui.player.CastButton
import com.lucasdss.ftpmusic.app.ui.playlist.AddSongsPickerContent
import kotlinx.coroutines.flow.distinctUntilChanged
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
    var showRenamePlaylistDialog by remember { mutableStateOf(false) }
    var renamePlaylistTarget by remember { mutableStateOf<PlaylistView?>(null) }
    var renamePlaylistDraft by remember { mutableStateOf("") }
    var showDeletePlaylistConfirm by remember { mutableStateOf(false) }
    var pendingDeletePlaylist by remember { mutableStateOf<PlaylistView?>(null) }
    var coverEditPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    val playlistsForCover by viewModel.state.collectAsStateWithLifecycle()
    // Admin radio CRUD (shared server catalog)
    var showRadioEditorSheet by remember { mutableStateOf(false) }
    var editingRadioStation by remember { mutableStateOf<RadioStation?>(null) }
    var radioName by remember { mutableStateOf("") }
    var radioStreamUrl by remember { mutableStateOf("") }
    var radioHomepageUrl by remember { mutableStateOf("") }
    var menuRadioStation by remember { mutableStateOf<RadioStation?>(null) }
    var pendingDeleteRadio by remember { mutableStateOf<RadioStation?>(null) }
    var showDeleteRadioConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coverArtFallback = remember { CoverArtFallbackService.getInstance(context) }
    val coverToast by viewModel.coverToast.collectAsStateWithLifecycle()
    LaunchedEffect(coverToast) {
        coverToast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearCoverToast()
        }
    }
    val coverEditPlaylist = coverEditPlaylistId?.let { id ->
        playlistsForCover.playlists.find { it.id == id }
    }
    coverEditPlaylist?.let { pl ->
        val coverEdit = rememberCollectionCoverEditController(
            title = pl.name,
            hasFixedCover = pl.fixedCoverKind != null,
            subject = CollectionCoverSubject.PLAYLIST,
            collectionName = pl.name,
            onLibraryCover = { id ->
                viewModel.setPlaylistFixedCoverFromLibrary(pl.id, id)
                coverEditPlaylistId = null
            },
            onCroppedBitmap = { bmp ->
                viewModel.setPlaylistFixedCoverFromCropped(pl.id, bmp)
                coverEditPlaylistId = null
            },
            onSavedCover = { path ->
                viewModel.setPlaylistFixedCoverFromSaved(pl.id, path)
                coverEditPlaylistId = null
            },
            onBundledCover = { path ->
                viewModel.setPlaylistFixedCoverFromBundled(pl.id, path)
                coverEditPlaylistId = null
            },
            onSavedRemoved = { coverEditPlaylistId = null },
            onClear = {
                viewModel.clearPlaylistFixedCover(pl.id)
                coverEditPlaylistId = null
            },
            onLoadFailed = {
                viewModel.notifyCoverCropLoadFailed()
                coverEditPlaylistId = null
            },
            onExportFailed = {
                viewModel.notifyCoverCropExportFailed()
                coverEditPlaylistId = null
            },
            onIdleDismiss = { coverEditPlaylistId = null },
        )
        LaunchedEffect(pl.id) { coverEdit.openSheet() }
    }

    // Chrome (chips + search) sits above Lazy — pad Column, not only list content
    // (ADR-0107 / post-1.8.0 hard-fix). Lazy top pad is local spacing only.
    // ADR-0114: chrome + list ride via appHeaderRidingContent (fixed over-measure +
    // graphicsLayer -offsetPx) so collapse leaves no mid/bottom Background strip.
    // FABs must be siblings of the riding Column (inside clipHost Box, outside riding):
    // BottomEnd inside riding sits in the clipped overhang at offset=0.
    val headerPad = LocalAppHeaderContentPadding.current
    val headerOffset = LocalAppHeaderOffsetPx.current
    val headerPadPx = with(LocalDensity.current) { headerPad.toPx() }
    val shellReady = !(shell.isLoading && !shell.hasLoadedOnce)
    Box(
        Modifier.appHeaderRidingClipHost(headerPad),
    ) {
        // Refresh favorites (thumbs/bookmarks) whenever Library becomes visible
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                viewModel.loadFavorites()
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .appHeaderRidingContent(
                    offsetPx = { headerOffset.offsetPx() },
                    maxOffsetPx = headerPadPx,
                )
                .background(Background),
        ) {
            // Server issues: AppHeader IssueStatusIcon + sheet (ADR-0120).
            SegmentedChipRow {
                LibraryTab.entries.forEach { tab ->
                    SegmentedChip(
                        label = tab.label,
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Contextual search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search ${selectedTab.label}…", color = Color(0xFF666666)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = NavUnselected) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        Icon(
                            Icons.Default.Close,
                            "Clear",
                            tint = NavUnselected,
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
                    cursorColor = BrandTeal,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingXS()),
                shape = RoundedCornerShape(cornerM()), singleLine = true,
            )
            // Data-first: spinner only until the first content render — a stalled
            // loader must never leave the screen spinning forever.
            if (shell.isLoading && !shell.hasLoadedOnce) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandTeal)
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
                                val gridState = rememberLazyGridState(
                                    cacheWindow = androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow(
                                        aheadFraction = 0.5f,
                                        behindFraction = 0.2f,
                                    ),
                                )
                                val scrollInProgress = gridState.isScrollInProgress
                                // Windowed alpha browse — near-end append (ADR-0107 Pass 6).
                                val searching = albumTab.albumSearchResults != null
                                LaunchedEffect(gridState, searching) {
                                    if (searching) return@LaunchedEffect
                                    snapshotFlow {
                                        val info = gridState.layoutInfo
                                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        val total = info.totalItemsCount
                                        total > 0 && last >= total - 4
                                    }
                                        .distinctUntilChanged()
                                        .collect { nearEnd ->
                                            if (nearEnd) viewModel.loadMoreAlphaAlbums()
                                        }
                                }
                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier
                                        .padding(horizontal = spacingM())
                                        .testTag("library_albums_grid"),
                                    contentPadding = PaddingValues(
                                        top = spacingS(),
                                        bottom = spacingS(),
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(gridGapH()),
                                    verticalArrangement = Arrangement.spacedBy(gridGapV()),
                                ) {
                                    items(displayedAlbums, key = { it.id }, contentType = { "album" }) { album ->
                                        val isActive = currentAlbumId != null && album.id == currentAlbumId && isPlaying
                                        Column(Modifier.clickable { onAlbumClick(album.id) }) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(cornerM())),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                val url =
                                                    rememberPreferredCoverArt(
                                                        album.coverArt,
                                                        album.artist,
                                                        album.name,
                                                        size = 160,
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
                                                        allowFallbackFetch = !scrollInProgress,
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
                                                        Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.45f)),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        ScrollAwareEqBars(scrollInProgress = scrollInProgress)
                                                    }
                                                }
                                                AlbumDownloadBadge(
                                                    "none",
                                                    Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                                )
                                            }
                                            Spacer(Modifier.height(spacingBelowArt()))
                                            Row(
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
                                            Spacer(Modifier.height(4.dp))
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
                                val artistListState = rememberLazyListState(
                                    cacheWindow = androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow(
                                        aheadFraction = 0.5f,
                                        behindFraction = 0.2f,
                                    ),
                                )
                                LazyColumn(
                                    state = artistListState,
                                    contentPadding = PaddingValues(top = spacingS()),
                                ) {
                                    items(displayedArtists, key = { it.id }, contentType = { "artist" }) { artist ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable {
                                                onArtistClick(artist.id)
                                            }.padding(horizontal = spacingL(), vertical = spacingM()),
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
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                artist.albumCount?.let {
                                                    Text(
                                                        "$it albums",
                                                        color = Color(0xFF888888),
                                                        fontSize = textLabelM(),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
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
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Add a playlist with +",
                                            color = Color(0xFF666666),
                                            fontSize = textLabelM(),
                                        )
                                    }
                                }
                            } else {
                                val playlistListState = rememberLazyListState(
                                    cacheWindow = androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow(
                                        aheadFraction = 0.5f,
                                        behindFraction = 0.2f,
                                    ),
                                )
                                LazyColumn(
                                    state = playlistListState,
                                    contentPadding = PaddingValues(
                                        top = spacingS(),
                                        bottom = 88.dp,
                                    ),
                                ) {
                                    items(filteredPlaylists, key = { it.id }, contentType = { "playlist" }) { pl ->
                                        Box {
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = spacingL(), vertical = spacingM()),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                CollectionCoverArt(
                                                    name = pl.name,
                                                    fixedCoverKind = pl.fixedCoverKind,
                                                    fixedCoverValue = pl.fixedCoverValue,
                                                    derivedCoverArtIds = listOfNotNull(pl.coverArt),
                                                    serverCoverArtId = pl.coverArt,
                                                    primaryArtist = pl.primaryArtist,
                                                    primaryAlbum = pl.primaryAlbum,
                                                    decodeSize = 52.dp,
                                                    contentDescription = "${pl.name} cover. Long-press to edit.",
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(RoundedCornerShape(cornerM()))
                                                        .combinedClickable(
                                                            onClick = { onPlaylistClick(pl.id) },
                                                            onLongClick = { coverEditPlaylistId = pl.id },
                                                        )
                                                        .semantics { testTag = "library_playlist_cover_${pl.id}" },
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Row(
                                                    Modifier
                                                        .weight(1f)
                                                        .combinedClickable(
                                                            onClick = { onPlaylistClick(pl.id) },
                                                            onLongClick = { menuPlaylist = pl },
                                                        ),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(
                                                            pl.name,
                                                            color = Color.White,
                                                            fontSize = textHeadingS(),
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                "${pl.trackCount} tracks",
                                                                color = Color(0xFF888888),
                                                                fontSize = textLabelM(),
                                                            )
                                                            when (pl.syncBadge) {
                                                                PlaylistSyncBadge.SYNCED -> {
                                                                    Spacer(Modifier.width(8.dp))
                                                                    Surface(
                                                                        color = BrandTeal.copy(alpha = 0.12f),
                                                                        shape = RoundedCornerShape(4.dp),
                                                                    ) {
                                                                        Text(
                                                                            "Synced",
                                                                            modifier = Modifier.padding(
                                                                                horizontal = 5.dp,
                                                                                vertical = 1.dp,
                                                                            ),
                                                                            color = BrandTeal,
                                                                            fontSize = textMicro(),
                                                                            fontWeight = FontWeight.SemiBold,
                                                                        )
                                                                    }
                                                                }
                                                                PlaylistSyncBadge.PENDING -> {
                                                                    Spacer(Modifier.width(8.dp))
                                                                    Surface(
                                                                        color = Color(0xFFFFA726).copy(alpha = 0.12f),
                                                                        shape = RoundedCornerShape(4.dp),
                                                                    ) {
                                                                        Text(
                                                                            "Pending",
                                                                            modifier = Modifier.padding(
                                                                                horizontal = 5.dp,
                                                                                vertical = 1.dp,
                                                                            ),
                                                                            color = Color(0xFFFFA726),
                                                                            fontSize = textMicro(),
                                                                            fontWeight = FontWeight.SemiBold,
                                                                        )
                                                                    }
                                                                }
                                                                PlaylistSyncBadge.CONFLICT -> {
                                                                    Spacer(Modifier.width(8.dp))
                                                                    Surface(
                                                                        color = Color(0xFFE84040).copy(alpha = 0.12f),
                                                                        shape = RoundedCornerShape(4.dp),
                                                                    ) {
                                                                        Text(
                                                                            "Conflict",
                                                                            modifier = Modifier.padding(
                                                                                horizontal = 5.dp,
                                                                                vertical = 1.dp,
                                                                            ),
                                                                            color = Color(0xFFE84040),
                                                                            fontSize = textMicro(),
                                                                            fontWeight = FontWeight.SemiBold,
                                                                        )
                                                                    }
                                                                }
                                                                PlaylistSyncBadge.NONE -> Unit
                                                            }
                                                            when (pl.downloadBadge) {
                                                                PlaylistDownloadBadge.FULL -> {
                                                                    Spacer(Modifier.width(8.dp))
                                                                    Surface(
                                                                        color = BrandPurple.copy(alpha = 0.12f),
                                                                        shape = RoundedCornerShape(4.dp),
                                                                    ) {
                                                                        Text(
                                                                            "Downloaded",
                                                                            modifier = Modifier.padding(
                                                                                horizontal = 5.dp,
                                                                                vertical = 1.dp,
                                                                            ),
                                                                            color = BrandPurple,
                                                                            fontSize = textMicro(),
                                                                            fontWeight = FontWeight.SemiBold,
                                                                        )
                                                                    }
                                                                }
                                                                PlaylistDownloadBadge.PARTIAL -> {
                                                                    Spacer(Modifier.width(8.dp))
                                                                    Surface(
                                                                        color = BrandPurple.copy(alpha = 0.08f),
                                                                        shape = RoundedCornerShape(4.dp),
                                                                    ) {
                                                                        Text(
                                                                            "Partial",
                                                                            modifier = Modifier.padding(
                                                                                horizontal = 5.dp,
                                                                                vertical = 1.dp,
                                                                            ),
                                                                            color = BrandPurple,
                                                                            fontSize = textMicro(),
                                                                            fontWeight = FontWeight.SemiBold,
                                                                        )
                                                                    }
                                                                }
                                                                PlaylistDownloadBadge.NONE -> Unit
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
                                            }
                                            // Long-press / overflow manage menu (cover LP stays cover-edit)
                                            DropdownMenu(
                                                expanded = menuPlaylist?.id == pl.id,
                                                onDismissRequest = { menuPlaylist = null },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Rename") },
                                                    onClick = {
                                                        renamePlaylistTarget = pl
                                                        renamePlaylistDraft = pl.name
                                                        menuPlaylist = null
                                                        showRenamePlaylistDialog = true
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Sync from Server") },
                                                    onClick = {
                                                        viewModel.syncPlaylistFromServer(pl.id)
                                                        menuPlaylist = null
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.CloudDownload, null) },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete Playlist") },
                                                    onClick = {
                                                        pendingDeletePlaylist = pl
                                                        menuPlaylist = null
                                                        showDeletePlaylistConfirm = true
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFE84040))
                                                    },
                                                )
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
                                            color = Surface,
                                            modifier = Modifier.padding(horizontal = spacingL()),
                                        )
                                    }
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
                            Box(Modifier.fillMaxSize()) {
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
                                            Text(
                                                "No radio stations found",
                                                color = Color(0xFF888888),
                                                fontSize = textBodyM(),
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                if (state.isAdmin) {
                                                    "Add a station with +"
                                                } else {
                                                    "via getInternetRadioStations"
                                                },
                                                color = Color(0xFF666666),
                                                fontSize = textLabelM(),
                                            )
                                        }
                                    }
                                } else {
                                    val radioListState = rememberLazyListState(
                                        cacheWindow = androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow(
                                            aheadFraction = 0.5f,
                                            behindFraction = 0.2f,
                                        ),
                                    )
                                    LazyColumn(
                                        state = radioListState,
                                        contentPadding = PaddingValues(
                                            top = spacingS(),
                                            bottom = if (state.isAdmin) 88.dp else spacingS(),
                                        ),
                                    ) {
                                        item {
                                            Row(
                                                Modifier.padding(horizontal = spacingL(), vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    Icons.Default.SettingsInputAntenna,
                                                    null,
                                                    tint = BrandTeal,
                                                    modifier = Modifier.size(iconMicro()),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "Internet Radio",
                                                    color = Color(0xFF888888),
                                                    fontSize = textLabelM(),
                                                )
                                            }
                                            HorizontalDivider(
                                                color = Surface,
                                                modifier = Modifier.padding(horizontal = spacingL()),
                                            )
                                        }
                                        items(filteredRadio, key = { it.id }, contentType = { "radio" }) { station ->
                                            Row(
                                                Modifier.fillMaxWidth()
                                                    .combinedClickable(
                                                        onClick = { onRadioStationClick(station) },
                                                        onLongClick = {
                                                            if (state.isAdmin) menuRadioStation = station
                                                        },
                                                    )
                                                    .padding(horizontal = spacingL(), vertical = spacingM()),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(
                                                    Modifier.size(iconLarge())
                                                        .clip(RoundedCornerShape(cornerM()))
                                                        .background(Color(0xFF1E1E1E)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        Icons.Default.SettingsInputAntenna,
                                                        null,
                                                        tint = BrandTeal,
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        station.name,
                                                        color = Color.White,
                                                        fontSize = textHeadingS(),
                                                        fontWeight = FontWeight.Medium,
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
                                                RadioBookmarkIcon(
                                                    bookmarked = station.id in state.bookmarkedStationIds,
                                                    onClick = { viewModel.toggleRadioBookmark(station) },
                                                    inactiveTint = NavUnselected,
                                                )
                                                if (state.isAdmin) {
                                                    Spacer(Modifier.width(4.dp))
                                                    Box {
                                                        IconButton(
                                                            onClick = { menuRadioStation = station },
                                                            modifier = Modifier.size(36.dp),
                                                        ) {
                                                            Icon(
                                                                Icons.Default.MoreVert,
                                                                contentDescription = "Station options",
                                                                tint = Color(0xFF888888),
                                                                modifier = Modifier.size(20.dp),
                                                            )
                                                        }
                                                        DropdownMenu(
                                                            expanded = menuRadioStation?.id == station.id,
                                                            onDismissRequest = { menuRadioStation = null },
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("Edit") },
                                                                onClick = {
                                                                    editingRadioStation = station
                                                                    radioName = station.name
                                                                    radioStreamUrl = station.streamUrl
                                                                    radioHomepageUrl = station.homePageUrl.orEmpty()
                                                                    viewModel.clearRadioMutationError()
                                                                    viewModel.clearRadioMutationSuccess()
                                                                    showRadioEditorSheet = true
                                                                    menuRadioStation = null
                                                                },
                                                                leadingIcon = {
                                                                    Icon(Icons.Default.Edit, null)
                                                                },
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("Delete from Server") },
                                                                onClick = {
                                                                    pendingDeleteRadio = station
                                                                    menuRadioStation = null
                                                                    showDeleteRadioConfirm = true
                                                                },
                                                                leadingIcon = {
                                                                    Icon(
                                                                        Icons.Default.Delete,
                                                                        null,
                                                                        tint = Color(0xFFE84040),
                                                                    )
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Box(
                                                    Modifier.size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(BrandTeal.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        Icons.Filled.PlayArrow,
                                                        null,
                                                        tint = BrandTeal,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                            HorizontalDivider(
                                                color = Surface,
                                                modifier = Modifier.padding(horizontal = spacingL()),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } // close Box(Modifier.weight(1f))
            }
        } // chrome + list appHeaderRidingContent wrap

        // Viewport-static FABs: outside riding so ADR-0114 overhang clip cannot hide them.
        if (shellReady && selectedTab == LibraryTab.PLAYLISTS) {
            FloatingActionButton(
                onClick = {
                    viewModel.clearCreatedPlaylist()
                    createStep = CreateSheetStep.NAME
                    showCreatePlaylistSheet = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .semantics { testTag = "library_create_playlist_fab" },
                containerColor = BrandTeal,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create playlist",
                    tint = Color.White,
                )
            }
        }
        if (shellReady && selectedTab == LibraryTab.RADIO && playlistsForCover.isAdmin) {
            FloatingActionButton(
                onClick = {
                    editingRadioStation = null
                    radioName = ""
                    radioStreamUrl = ""
                    radioHomepageUrl = ""
                    viewModel.clearRadioMutationError()
                    viewModel.clearRadioMutationSuccess()
                    showRadioEditorSheet = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .semantics { testTag = "library_add_radio_fab" },
                containerColor = BrandTeal,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add radio station",
                    tint = Color.White,
                )
            }
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

    if (showRenamePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showRenamePlaylistDialog = false
                renamePlaylistTarget = null
            },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = renamePlaylistDraft,
                    onValueChange = { renamePlaylistDraft = it },
                    singleLine = true,
                    placeholder = { Text("Playlist name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renamePlaylistTarget?.let { viewModel.renamePlaylistLocal(it.id, renamePlaylistDraft) }
                        showRenamePlaylistDialog = false
                        renamePlaylistTarget = null
                    },
                    enabled = renamePlaylistDraft.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenamePlaylistDialog = false
                    renamePlaylistTarget = null
                }) { Text("Cancel") }
            },
        )
    }

    if (showDeletePlaylistConfirm) {
        AlertDialog(
            onDismissRequest = {
                pendingDeletePlaylist = null
                showDeletePlaylistConfirm = false
            },
            title = { Text("Delete Playlist") },
            text = {
                Text("Delete \"${pendingDeletePlaylist?.name ?: ""}\"? This removes it from the server too.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeletePlaylist?.let { viewModel.deletePlaylistFromLibrary(it.id) }
                    pendingDeletePlaylist = null
                    showDeletePlaylistConfirm = false
                }) {
                    Text("Delete", color = Color(0xFFE84040))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDeletePlaylist = null
                    showDeletePlaylistConfirm = false
                }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteRadioConfirm) {
        val mutating by viewModel.state.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = {
                if (!mutating.isMutatingRadio) {
                    pendingDeleteRadio = null
                    showDeleteRadioConfirm = false
                }
            },
            title = { Text("Delete radio station") },
            text = {
                Text(
                    "Delete \"${pendingDeleteRadio?.name ?: ""}\" from the server? " +
                        "This removes it for all users.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = pendingDeleteRadio?.id ?: return@TextButton
                        viewModel.deleteRadioStation(id)
                    },
                    enabled = !mutating.isMutatingRadio,
                ) {
                    Text("Delete", color = Color(0xFFE84040))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDeleteRadio = null
                        showDeleteRadioConfirm = false
                    },
                    enabled = !mutating.isMutatingRadio,
                ) { Text("Cancel") }
            },
        )
        LaunchedEffect(mutating.radioMutationSuccess) {
            if (mutating.radioMutationSuccess && showDeleteRadioConfirm) {
                pendingDeleteRadio = null
                showDeleteRadioConfirm = false
                viewModel.clearRadioMutationSuccess()
            }
        }
        LaunchedEffect(mutating.radioMutationError) {
            mutating.radioMutationError?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.clearRadioMutationError()
            }
        }
    }

    if (showRadioEditorSheet) {
        val radioState by viewModel.state.collectAsStateWithLifecycle()
        val isEdit = editingRadioStation != null
        LaunchedEffect(radioState.radioMutationSuccess) {
            if (radioState.radioMutationSuccess && showRadioEditorSheet) {
                showRadioEditorSheet = false
                editingRadioStation = null
                radioName = ""
                radioStreamUrl = ""
                radioHomepageUrl = ""
                viewModel.clearRadioMutationSuccess()
            }
        }
        ModalBottomSheet(
            onDismissRequest = {
                if (!radioState.isMutatingRadio) {
                    viewModel.clearRadioMutationError()
                    showRadioEditorSheet = false
                    editingRadioStation = null
                }
            },
            containerColor = Surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.width(32.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)),
                    )
                }
                Text(
                    if (isEdit) "Edit Radio Station" else "Add Radio Station",
                    color = Color.White,
                    fontSize = textHeadingS(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                OutlinedTextField(
                    value = radioName,
                    onValueChange = {
                        radioName = it
                        viewModel.clearRadioMutationError()
                    },
                    singleLine = true,
                    placeholder = { Text("Station name…", color = Color(0xFF666666)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = Color(0xFF333344),
                        focusedContainerColor = Color(0xFF252538),
                        unfocusedContainerColor = Color(0xFF252538),
                        cursorColor = BrandTeal,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = radioStreamUrl,
                    onValueChange = {
                        radioStreamUrl = it
                        viewModel.clearRadioMutationError()
                    },
                    singleLine = true,
                    placeholder = { Text("Stream URL…", color = Color(0xFF666666)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = Color(0xFF333344),
                        focusedContainerColor = Color(0xFF252538),
                        unfocusedContainerColor = Color(0xFF252538),
                        cursorColor = BrandTeal,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = radioHomepageUrl,
                    onValueChange = { radioHomepageUrl = it },
                    singleLine = true,
                    placeholder = { Text("Homepage URL (optional)…", color = Color(0xFF666666)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = Color(0xFF333344),
                        focusedContainerColor = Color(0xFF252538),
                        unfocusedContainerColor = Color(0xFF252538),
                        cursorColor = BrandTeal,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                if (radioState.radioMutationError != null) {
                    FittingText(
                        text = radioState.radioMutationError!!,
                        color = Color(0xFFFF5252),
                        fontSize = textLabelM(),
                        minFontSize = textMicro(),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            if (!radioState.isMutatingRadio) {
                                viewModel.clearRadioMutationError()
                                showRadioEditorSheet = false
                                editingRadioStation = null
                            }
                        },
                        enabled = !radioState.isMutatingRadio,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252538)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("Cancel", color = Color(0xFF888888))
                    }
                    val canSave = radioName.isNotBlank() &&
                        radioStreamUrl.isNotBlank() &&
                        !radioState.isMutatingRadio
                    Button(
                        onClick = {
                            if (!canSave) return@Button
                            val homepage = radioHomepageUrl.trim().takeIf { it.isNotEmpty() }
                            val existing = editingRadioStation
                            if (existing != null) {
                                viewModel.updateRadioStation(
                                    id = existing.id,
                                    name = radioName.trim(),
                                    streamUrl = radioStreamUrl.trim(),
                                    homepageUrl = homepage,
                                )
                            } else {
                                viewModel.createRadioStation(
                                    name = radioName.trim(),
                                    streamUrl = radioStreamUrl.trim(),
                                    homepageUrl = homepage,
                                )
                            }
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                            .background(
                                Brush.linearGradient(listOf(BrandTeal, BrandPurple)),
                                RoundedCornerShape(12.dp),
                            ),
                    ) {
                        if (radioState.isMutatingRadio) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                if (isEdit) "Save" else "Add",
                                color = Color.White,
                                fontSize = textLabelL(),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Text(
                    "Synced via create/updateInternetRadioStation (admin)",
                    color = Color(0xFF666666),
                    fontSize = textLabelS(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
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
            containerColor = Surface,
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
                            tint = BrandTeal,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        FittingText(
                            text = "Import from Server",
                            color = BrandTeal,
                            fontSize = textBodyM(),
                            minFontSize = textMicro(),
                            modifier = Modifier.weight(1f),
                            fillMaxWidth = false,
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
                        fontSize = textHeadingS(),
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
                            focusedBorderColor = BrandTeal,
                            unfocusedBorderColor = Color(0xFF333344),
                            focusedContainerColor = Color(0xFF252538),
                            unfocusedContainerColor = Color(0xFF252538),
                            cursorColor = BrandTeal,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                    )
                    // Error
                    if (state.createPlaylistError != null) {
                        FittingText(
                            text = state.createPlaylistError!!,
                            color = Color(0xFFFF5252),
                            fontSize = textLabelM(),
                            minFontSize = textMicro(),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
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
                                    Brush.linearGradient(listOf(BrandTeal, BrandPurple)),
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
                                        fontSize = textLabelL(),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            } else {
                                Text(
                                    "Create & Sync",
                                    color = Color.White,
                                    fontSize = textLabelL(),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    // API footer
                    Text(
                        "Synced via createPlaylist + savePlayQueue",
                        color = Color(0xFF666666),
                        fontSize = textLabelS(),
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
                        fontSize = textHeadingS(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    Text(
                        "\"${state.createdPlaylist?.name ?: ""}\" is ready. Add songs now or finish later.",
                        color = Color(0xFF888888),
                        fontSize = textLabelL(),
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
                                    Brush.linearGradient(listOf(BrandTeal, BrandPurple)),
                                    RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Text(
                                "Add Songs",
                                color = Color.White,
                                fontSize = textLabelL(),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
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
            containerColor = Surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
                }
                Text(
                    "Add from Server",
                    color = Color.White,
                    fontSize = textHeadingS(),
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
                                        tint = BrandTeal,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        pl.name,
                                        color = Color.White,
                                        fontSize = textBodyM(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${pl.trackCount} tracks", color = Color(0xFF888888), fontSize = textLabelM())
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
