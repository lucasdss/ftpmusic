package com.lucasdss.ftpmusic.app.ui.library

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.components.AlbumDownloadBadge
import com.lucasdss.ftpmusic.app.ui.components.ArtistAvatar
import com.lucasdss.ftpmusic.app.ui.components.CollectionCoverArt
import com.lucasdss.ftpmusic.app.ui.components.rememberCollectionCoverEditController
import com.lucasdss.ftpmusic.app.ui.components.CoverArtImage
import com.lucasdss.ftpmusic.app.ui.components.ScrollAwareEqBars
import com.lucasdss.ftpmusic.app.ui.components.SongListRow
import com.lucasdss.ftpmusic.app.ui.components.downloadStatus
import com.lucasdss.ftpmusic.app.ui.components.formatSongDuration

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onAlbumClick: (String) -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    onMixClick: (Long) -> Unit = {},
    onPlaylistsClick: () -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onRadioStationClick: (com.lucasdss.ftpmusic.app.ui.library.RadioStation) -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onRecentlyAddedClick: () -> Unit = {},
    // Active track for EQ animation in Recently Played and Recently Added
    currentTrackId: String? = null,
    currentAlbumId: String? = null,
    isPlaying: Boolean = false,
    // Opens Settings → Server (banner "Fix" action)
    onOpenServerSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coverToast by viewModel.coverToast.collectAsStateWithLifecycle()
    var showAlbumSheet by remember { mutableStateOf<com.lucasdss.ftpmusic.app.data.model.Album?>(null) }
    var coverEditTarget by rememberSaveable(stateSaver = HomeCoverEditTargetSaver) {
        mutableStateOf<HomeCoverEditTarget?>(null)
    }
    val context = LocalContext.current
    LaunchedEffect(coverToast) {
        coverToast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearCoverToast()
        }
    }
    coverEditTarget?.let { target ->
        val coverEdit = rememberCollectionCoverEditController(
            title = target.title,
            hasFixedCover = target.hasFixedCover,
            onLibraryCover = { id ->
                when (target) {
                    is HomeCoverEditTarget.Mix -> viewModel.setMixFixedCoverFromLibrary(target.id, id)
                    is HomeCoverEditTarget.Playlist -> viewModel.setPlaylistFixedCoverFromLibrary(target.id, id)
                }
                coverEditTarget = null
            },
            onCroppedBitmap = { bmp ->
                when (target) {
                    is HomeCoverEditTarget.Mix -> viewModel.setMixFixedCoverFromCropped(target.id, bmp)
                    is HomeCoverEditTarget.Playlist -> viewModel.setPlaylistFixedCoverFromCropped(target.id, bmp)
                }
                coverEditTarget = null
            },
            onClear = {
                when (target) {
                    is HomeCoverEditTarget.Mix -> viewModel.clearMixFixedCover(target.id)
                    is HomeCoverEditTarget.Playlist -> viewModel.clearPlaylistFixedCover(target.id)
                }
                coverEditTarget = null
            },
            onLoadFailed = {
                viewModel.notifyCoverCropLoadFailed()
                coverEditTarget = null
            },
            onExportFailed = {
                viewModel.notifyCoverCropExportFailed()
                coverEditTarget = null
            },
            onIdleDismiss = { coverEditTarget = null },
        )
        LaunchedEffect(target) { coverEdit.openSheet() }
    }

    // ADR-0109 amend: TTL-check Recently Added on resume (with other Home refresh).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onHomeResume()
        }
    }

    // ADR-0114: opaque Background rides under overlay AppHeader so Settings/Cast
    // stay readable on reveal (parity with Library/Favorites). Header clearance is
    // Column pad via clip-host; Lazy top pad is local spacing only. ADR-0143 mini
    // bottom pad kept on Lazy so idle mini reveal does not clip list end.
    val headerPad = LocalAppHeaderContentPadding.current
    val headerOffset = LocalAppHeaderOffsetPx.current
    val headerPadPx = with(LocalDensity.current) { headerPad.toPx() }
    Column(
        Modifier.appHeaderRidingClipHost(headerPad),
    ) {
        // Server issues: AppHeader IssueStatusIcon + sheet (ADR-0120).
        // Data-first: the full-screen spinner only shows until the first
        // content render; a stalled loader must never leave Home spinning.
        if (state.isLoading && !state.hasLoadedOnce) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandTeal)
            }
        } else {
            // Hoisted: recomputed only when the playlist list changes, not on
            // every LazyColumn recomposition (scroll/EQ frames).
            val syncedPlaylists = remember(state.playlists) { state.playlists.filter { it.isSynced } }
            val recentAlbums = remember(state.randomAlbums) { state.randomAlbums.take(10) }
            // Compose 1.9+ viewport-relative prefetch (Pass 5 / ADR-0107).
            val listState = rememberLazyListState(
                cacheWindow = LazyLayoutCacheWindow(
                    aheadFraction = 0.5f,
                    behindFraction = 0.2f,
                ),
            )
            val scrollInProgress = listState.isScrollInProgress
            val cardDecode = albumCardWidth()
            val miniBottomPad = LocalMiniPlayerBottomContentPadding.current
            Column(
                Modifier
                    .fillMaxSize()
                    .appHeaderRidingContent(
                        offsetPx = { headerOffset.offsetPx() },
                        maxOffsetPx = headerPadPx,
                    )
                    .background(Background),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_scroll"),
                    contentPadding = PaddingValues(top = spacingS(), bottom = miniBottomPad),
                ) {
                    // ── Surprise Me hero card ──
                    item(contentType = "hero") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacingL(), vertical = spacingM())
                                .height(100.dp)
                                .clip(RoundedCornerShape(cornerM()))
                                .background(Color(0xFF1E1E2E))
                                .border(
                                    2.dp,
                                    Brush.horizontalGradient(listOf(BrandTeal, BrandPurple)),
                                    RoundedCornerShape(cornerM()),
                                )
                                .clickable { viewModel.playSurpriseMe() },
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(spacingS()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Inset image — rounded square (button affordance)
                                Image(
                                    painter = painterResource(R.drawable.random),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(cornerM())),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(spacingL()))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Surprise Me",
                                        color = Color.White,
                                        fontSize = textHeadingL(),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(spacingXS()))
                                    Text(
                                        "Random music from your library",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = textLabelL(),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // ── Daily Mixes ──
                    if (state.mixCards.isNotEmpty()) {
                        item(contentType = "section_header") {
                            HomeSectionHeader(
                                title = "Daily Mixes",
                                leadingIcon = Icons.Default.AutoAwesome,
                                trailing = HomeSectionTrailing.Refresh(
                                    onClick = { viewModel.refreshAllMixes() },
                                    contentDescription = "Refresh mixes",
                                ),
                            )
                        }
                        item(contentType = "lazy_row_mix") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(spacingM()),
                                contentPadding = PaddingValues(horizontal = spacingL()),
                                modifier = Modifier
                                    .padding(bottom = spacingM())
                                    .semantics { testTag = "daily_mix_row" },
                            ) {
                                items(state.mixCards, key = { it.id }, contentType = { "mix" }) { mix ->
                                    GenreMixCard(
                                        mix = mix,
                                        decodeSize = cardDecode,
                                        onClick = { onMixClick(mix.id) },
                                        onLongClickCover = {
                                            coverEditTarget = HomeCoverEditTarget.Mix(
                                                id = mix.id,
                                                title = mix.name,
                                                hasFixedCover = mix.fixedCoverKind != null,
                                            )
                                        },
                                        modifier = Modifier.semantics { testTag = "daily_mix_card_${mix.id}" },
                                    )
                                }
                            }
                        }
                    } else if (state.isResyncing || state.isGeneratingMixes) {
                        item(contentType = "placeholder") {
                            Text(
                                "Building your Daily Mixes…",
                                color = Color(0xFF666666),
                                fontSize = textBodyM(),
                                modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                            )
                        }
                    } else {
                        // No mixes exist yet
                        item(contentType = "placeholder") {
                            Text(
                                "Daily Mixes will appear after sync completes",
                                color = NavUnselected,
                                fontSize = textBodyM(),
                                modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                            )
                        }
                    }

                    // ── Playlists (synced only) ──
                    if (state.showPlaylistsOnHome && syncedPlaylists.isNotEmpty()) {
                        item(contentType = "section_header") {
                            HomeSectionHeader(
                                title = "Playlists",
                                leadingIcon = Icons.AutoMirrored.Filled.QueueMusic,
                                trailing = HomeSectionTrailing.SeeAll(
                                    onClick = onPlaylistsClick,
                                    contentDescription = "See all playlists",
                                ),
                            )
                        }
                        item(contentType = "lazy_row_playlist") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(spacingM()),
                                contentPadding = PaddingValues(horizontal = spacingL()),
                                modifier = Modifier
                                    .padding(bottom = spacingM())
                                    .semantics { testTag = "home_playlists_row" },
                            ) {
                                items(syncedPlaylists, key = { it.id }, contentType = { "playlist" }) { pl ->
                                    HomePlaylistCard(
                                        playlist = pl,
                                        montageCovers = state.playlistMontages[pl.id].orEmpty(),
                                        decodeSize = cardDecode,
                                        onClick = { onPlaylistClick(pl.id) },
                                        onLongClickCover = {
                                            coverEditTarget = HomeCoverEditTarget.Playlist(
                                                id = pl.id,
                                                title = pl.name,
                                                hasFixedCover = pl.fixedCoverKind != null,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ── Favorite Artists ──
                    if (state.showFavArtistsSection && state.starredArtists.isNotEmpty()) {
                        item(contentType = "section_header") {
                            HomeSectionHeader(
                                title = "Favorite Artists",
                                leadingIcon = Icons.Default.ThumbUp,
                                trailing = HomeSectionTrailing.SeeAll(
                                    onClick = onFavoritesClick,
                                    contentDescription = "See all favorite artists",
                                ),
                            )
                        }
                        item(contentType = "lazy_row_artist") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(spacingM()),
                                contentPadding = PaddingValues(horizontal = spacingL()),
                                modifier = Modifier
                                    .padding(bottom = spacingM())
                                    .semantics { testTag = "home_fav_artists_row" },
                            ) {
                                items(state.starredArtists, key = { it.id }, contentType = { "artist" }) { artist ->
                                    Column(
                                        Modifier.width(albumCardWidth()).clickable { onArtistClick(artist.id) },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        // Avatar sized like the Album/Daily Mix art box (user request)
                                        ArtistAvatar(
                                            artistName = artist.name,
                                            coverArtId = artist.coverArtUrl,
                                            size = albumCardWidth(),
                                        )
                                        Spacer(Modifier.height(spacingBelowArt()))
                                        Text(
                                            artist.name,
                                            color = Color.White,
                                            fontSize = textBodyM(),
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Favorite Albums ──
                    if (state.showFavAlbumsSection && state.starredAlbums.isNotEmpty()) {
                        item(contentType = "section_header") {
                            HomeSectionHeader(
                                title = "Favorite Albums",
                                leadingIcon = Icons.Default.ThumbUp,
                                trailing = HomeSectionTrailing.SeeAll(
                                    onClick = onFavoritesClick,
                                    contentDescription = "See all favorite albums",
                                ),
                            )
                        }
                        item(contentType = "lazy_row_album") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(spacingM()),
                                contentPadding = PaddingValues(horizontal = spacingL()),
                                modifier = Modifier
                                    .padding(bottom = spacingM())
                                    .semantics { testTag = "home_fav_albums_row" },
                            ) {
                                items(state.starredAlbums, key = { it.id }, contentType = { "album" }) { album ->
                                    Column(
                                        Modifier.width(albumCardWidth()).clickable { onAlbumClick(album.id) },
                                    ) {
                                        Box(
                                            Modifier.width(albumCardWidth()).aspectRatio(1f)
                                                .clip(RoundedCornerShape(cornerM())).background(Color(0xFF1E1E1E)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            val url = rememberPreferredCoverArt(
                                                coverArtId = album.coverArtUrl,
                                                artist = album.artist,
                                                album = album.name,
                                                size = with(LocalDensity.current) {
                                                    cardDecode.roundToPx().coerceAtLeast(1)
                                                },
                                            )
                                            if (url != null) {
                                                CoverArtImage(
                                                    url = url,
                                                    contentDescription = album.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    fallbackArtist = album.artist,
                                                    fallbackAlbum = album.name,
                                                    decodeSize = cardDecode,
                                                    allowFallbackFetch = !scrollInProgress,
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Album,
                                                    null,
                                                    tint = Color(0xFF444444),
                                                    modifier = Modifier.size(36.dp),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(spacingBelowArt()))
                                        Text(
                                            album.name,
                                            color = Color.White,
                                            fontSize = textBodyM(),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            album.artist ?: "Unknown artist",
                                            color = NavUnselected,
                                            fontSize = textLabelM(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Favorite Radio ──
                    if (state.showFavRadioSection && state.bookmarkedRadio.isNotEmpty()) {
                        item(contentType = "section_header") {
                            HomeSectionHeader(
                                title = "Favorite Radio",
                                leadingIcon = Icons.Default.Bookmark,
                                trailing = HomeSectionTrailing.SeeAll(
                                    onClick = onFavoritesClick,
                                    contentDescription = "See all favorite radio",
                                ),
                            )
                        }
                        item(contentType = "lazy_row_radio") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(spacingM()),
                                contentPadding = PaddingValues(horizontal = spacingL()),
                                modifier = Modifier
                                    .padding(bottom = spacingM())
                                    .semantics { testTag = "home_fav_radio_row" },
                            ) {
                                items(state.bookmarkedRadio, key = {
                                    it.stationId
                                }, contentType = { "radio" }) { station ->
                                    HomeRadioPill(
                                        name = station.name,
                                        onClick = {
                                            onRadioStationClick(
                                                RadioStation(
                                                    id = station.stationId,
                                                    name = station.name,
                                                    streamUrl = station.streamUrl,
                                                    homePageUrl = station.homePageUrl,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ── Tuned In genres ──
                    if (state.genres.isNotEmpty()) {
                        item(contentType = "section_header") {
                            HomeSectionHeader(
                                title = "Tuned In",
                                leadingIcon = Icons.Default.Tune,
                            )
                        }
                        item(contentType = "tuned_in") {
                            FlowRow(
                                modifier = Modifier.padding(
                                    start = spacingL(),
                                    end = spacingL(),
                                    top = spacingXS(),
                                    bottom = spacingM(),
                                ),
                                horizontalArrangement = Arrangement.spacedBy(spacingS()),
                                verticalArrangement = Arrangement.spacedBy(spacingS()),
                            ) {
                                val genreColors = listOf(
                                    BrandTeal,
                                    BrandPurple,
                                    Color(0xFF5B8DEE),
                                    Color(0xFFE84090),
                                    Color(0xFFF0A040),
                                )
                                for ((i, genre) in state.genres.withIndex()) {
                                    val color = genreColors[i % genreColors.size]
                                    Row(
                                        Modifier
                                            .border(1.dp, color.copy(alpha = 0.33f), RoundedCornerShape(50))
                                            .clip(RoundedCornerShape(50))
                                            .background(color.copy(alpha = 0.13f))
                                            .clickable { onGenreClick(genre) }
                                            .padding(horizontal = spacingS(), vertical = spacingXS()),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            null,
                                            tint = color,
                                            modifier = Modifier.size(adp(10f)),
                                        )
                                        Spacer(Modifier.width(spacingXS()))
                                        Text(
                                            genre,
                                            color = color,
                                            fontSize = textBodyM(),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Recently Added albums ──
                    if (recentAlbums.isNotEmpty()) {
                        item(contentType = "section_header") {
                            HomeSectionHeader(
                                title = "Recently Added",
                                leadingIcon = Icons.Default.NewReleases,
                                trailing = HomeSectionTrailing.SeeAll(
                                    onClick = onRecentlyAddedClick,
                                    contentDescription = "See all recently added",
                                ),
                            )
                        }
                        item(contentType = "row_recent") {
                            // Fixed ≤10 cards — plain Row avoids nested LazyRow Subcompose tax (Pass 7).
                            Row(
                                Modifier
                                    .padding(bottom = spacingM())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = spacingL()),
                                horizontalArrangement = Arrangement.spacedBy(spacingM()),
                            ) {
                                recentAlbums.forEach { album ->
                                    key(album.id) {
                                        val isActive =
                                            currentAlbumId != null && album.id == currentAlbumId && isPlaying
                                        AlbumCardDesign(
                                            album,
                                            isActive = isActive,
                                            scrollInProgress = scrollInProgress,
                                            decodeSize = cardDecode,
                                            downloadStatus =
                                                state.downloadStatusByAlbumId[album.id] ?: "none",
                                            onClick = { onAlbumClick(album.id) },
                                            onLongClick = { showAlbumSheet = album },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(contentType = "spacer") { Spacer(Modifier.height(spacingL())) }
                }
            } // riding content Column
        }
    }

    // Long-press album action sheet
    showAlbumSheet?.let { album ->
        com.lucasdss.ftpmusic.app.ui.components.AlbumCardActionSheet(
            albumName = album.name,
            artistName = album.artist,
            trackCount = album.songCount ?: 0,
            onPlay = {
                showAlbumSheet = null
                onAlbumClick(album.id)
            },
            onShuffle = {
                showAlbumSheet = null
                onAlbumClick(album.id)
            },
            onPlayNext = {
                showAlbumSheet = null
                viewModel.albumAction(album.id, "playnext")
            },
            onAddToQueue = {
                showAlbumSheet = null
                viewModel.albumAction(album.id, "queue")
            },
            onAddToPlaylist = {
                showAlbumSheet = null
                onAlbumClick(album.id)
            },
            onDownload = {
                showAlbumSheet = null
                viewModel.albumAction(album.id, "download")
            },
            onDismiss = { showAlbumSheet = null },
        )
    }

    // ── Overwrite Protection (Ask mode — Surprise Me) ──
    val overwriteContext = LocalContext.current
    val showOverwrite by viewModel.showOverwriteModal.collectAsStateWithLifecycle()
    if (showOverwrite) {
        android.app.AlertDialog.Builder(overwriteContext).apply {
            setTitle("Tracks in your queue")
            setMessage(
                "You have tracks in your Priority Queue. Do you want to clear them and play Surprise Me, or keep them?",
            )
            setNegativeButton("Keep Queue") { _, _ -> viewModel.resolveOverwrite(false) }
            setPositiveButton("Clear & Play") { _, _ -> viewModel.resolveOverwrite(true) }
            setOnCancelListener { viewModel.resolveOverwrite(false) }
            show()
        }
    }
}

/** Trailing Home section action — only when wired to a real destination/effect. */
private sealed class HomeSectionTrailing {
    abstract val onClick: () -> Unit
    abstract val contentDescription: String
    abstract val icon: ImageVector

    data class Refresh(override val onClick: () -> Unit, override val contentDescription: String = "Refresh mixes") :
        HomeSectionTrailing() {
        override val icon: ImageVector = Icons.Default.Refresh
    }

    data class SeeAll(override val onClick: () -> Unit, override val contentDescription: String) :
        HomeSectionTrailing() {
        override val icon: ImageVector = Icons.Default.ChevronRight
    }
}

/**
 * Standard Home shelf header: leading category glyph tight to title;
 * optional trailing IconButton (Refresh / See-all chevron). ADR 0089.
 */
@Composable
private fun HomeSectionHeader(
    title: String,
    leadingIcon: ImageVector,
    trailing: HomeSectionTrailing? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(
                start = spacingL(),
                end = spacingL(),
                top = spacing2XL(),
                bottom = spacingXS(),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            leadingIcon,
            contentDescription = null,
            tint = BrandTeal,
            modifier = Modifier.size(adp(16f)),
        )
        Spacer(Modifier.width(spacingXS()))
        Text(
            title,
            color = Color.White,
            fontSize = textHeadingM(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            IconButton(
                onClick = trailing.onClick,
                modifier = Modifier.size(adp(40f)),
            ) {
                Icon(
                    trailing.icon,
                    contentDescription = trailing.contentDescription,
                    tint = BrandTeal,
                    modifier = Modifier.size(adp(18f)),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AlbumCardDesign(
    album: Album,
    isActive: Boolean = false,
    scrollInProgress: Boolean = false,
    decodeSize: androidx.compose.ui.unit.Dp = albumCardWidth(),
    downloadStatus: String = "none",
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Column(Modifier.width(albumCardWidth()).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .width(albumCardWidth())
                .aspectRatio(1f)
                .clip(RoundedCornerShape(cornerM()))
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            val url = rememberPreferredCoverArt(
                coverArtId = album.coverArt,
                artist = album.artist,
                album = album.name,
                size = with(LocalDensity.current) { decodeSize.roundToPx().coerceAtLeast(1) },
            )
            if (url != null) {
                CoverArtImage(
                    url = url,
                    contentDescription = album.name,
                    modifier = Modifier.fillMaxSize(),
                    fallbackArtist = album.artist,
                    fallbackAlbum = album.name,
                    decodeSize = decodeSize,
                    allowFallbackFetch = !scrollInProgress,
                )
            } else {
                Icon(Icons.Default.Album, null, tint = Color(0xFF444444), modifier = Modifier.size(36.dp))
            }
            // EQ overlay on active album — pause infinite anim while flinging (ADR-0102)
            if (isActive) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    ScrollAwareEqBars(scrollInProgress = scrollInProgress)
                }
            }
            AlbumDownloadBadge(downloadStatus, Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Spacer(Modifier.height(spacingBelowArt()))
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
            Text(it, color = Color(0xFF888888), fontSize = textLabelM(), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                tint = if (i <= rating) BrandTeal else Color(0xFF444444),
                modifier = Modifier.size(iconMicro()),
            )
        }
    }
}

/**
 * Cover edit targets on Home — playlists + Daily Mixes only (albums intentionally
 * absent; album long-press opens [AlbumCardActionSheet]). ADR-0150.
 */
private sealed class HomeCoverEditTarget {
    abstract val title: String
    abstract val hasFixedCover: Boolean

    data class Mix(
        val id: Long,
        override val title: String,
        override val hasFixedCover: Boolean,
    ) : HomeCoverEditTarget()

    data class Playlist(
        val id: String,
        override val title: String,
        override val hasFixedCover: Boolean,
    ) : HomeCoverEditTarget()
}

private val HomeCoverEditTargetSaver = listSaver<HomeCoverEditTarget?, Any>(
    save = { target ->
        when (target) {
            is HomeCoverEditTarget.Mix -> listOf("mix", target.id, target.title, target.hasFixedCover)
            is HomeCoverEditTarget.Playlist -> listOf("pl", target.id, target.title, target.hasFixedCover)
            null -> emptyList()
        }
    },
    restore = { list ->
        if (list.isEmpty()) return@listSaver null
        when (list[0] as String) {
            "mix" -> HomeCoverEditTarget.Mix(
                id = (list[1] as Number).toLong(),
                title = list[2] as String,
                hasFixedCover = list[3] as Boolean,
            )
            "pl" -> HomeCoverEditTarget.Playlist(
                id = list[1] as String,
                title = list[2] as String,
                hasFixedCover = list[3] as Boolean,
            )
            else -> null
        }
    },
)

@Composable
private fun GenreMixCard(
    mix: com.lucasdss.ftpmusic.app.ui.library.MixCard,
    onClick: () -> Unit,
    onLongClickCover: () -> Unit = {},
    modifier: Modifier = Modifier,
    decodeSize: androidx.compose.ui.unit.Dp = albumCardWidth(),
) {
    // Clip art Box only — Column clip cut title first glyph against cornerM arc.
    Column(modifier.width(albumCardWidth())) {
        // Single primary cover on Home LazyRow — 4-tile montage binds 4×
        // resolver + Coil and stalls horizontal fling (HOME_SCROLL_PERF).
        CollectionCoverArt(
            name = mix.name,
            fixedCoverKind = mix.fixedCoverKind,
            fixedCoverValue = mix.fixedCoverValue,
            derivedCoverArtIds = mix.coverArts,
            primaryArtist = mix.primaryArtist,
            primaryAlbum = mix.primaryAlbum,
            decodeSize = decodeSize,
            contentDescription = "${mix.name} cover. Long-press to edit.",
            modifier = Modifier
                .width(albumCardWidth())
                .aspectRatio(1f)
                .clip(RoundedCornerShape(cornerM()))
                .combinedClickable(onClick = onClick, onLongClick = onLongClickCover),
        )
        Spacer(Modifier.height(spacingBelowArt()))
        Text(
            mix.name,
            color = Color.White,
            fontSize = textBodyM(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

/** Detail-screen 2×2 tile helper (Home LazyRow uses a single primary cover). */
@Composable
internal fun GenreMixCoverImage(coverArtId: String, modifier: Modifier) {
    val url = rememberPreferredCoverArt(
        coverArtId = coverArtId,
        artist = null,
        album = null,
        size = 150,
    )
    if (url != null) {
        CoverArtImage(
            url = url,
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(Color(0xFF1A1A1A)))
    }
}

/** Square playlist card for the Home Playlists row — same art size as the
 *  Daily Mix cards (albumCardWidth()): single primary cover (fling-cheap),
 *  teal synced check, track count. */
@Composable
private fun HomePlaylistCard(
    playlist: PlaylistView,
    montageCovers: List<String>,
    onClick: () -> Unit,
    onLongClickCover: () -> Unit = {},
    decodeSize: androidx.compose.ui.unit.Dp = albumCardWidth(),
) {
    Column(Modifier.width(albumCardWidth())) {
        CollectionCoverArt(
            name = playlist.name,
            fixedCoverKind = playlist.fixedCoverKind,
            fixedCoverValue = playlist.fixedCoverValue,
            derivedCoverArtIds = montageCovers,
            serverCoverArtId = playlist.coverArt,
            primaryArtist = playlist.primaryArtist,
            primaryAlbum = playlist.primaryAlbum,
            decodeSize = decodeSize,
            contentDescription = "${playlist.name} cover. Long-press to edit.",
            modifier = Modifier
                .width(albumCardWidth())
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClickCover),
        )
        Spacer(Modifier.height(spacingBelowArt()))
        Column(Modifier.clickable(onClick = onClick)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = BrandTeal, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    playlist.name,
                    color = Color.White,
                    fontSize = textBodyM(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"}",
                color = NavUnselected,
                fontSize = textLabelM(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Compact pill card for the Home Favorite Radio row: 40dp icon tile, name,
 *  teal "Live" badge. Tapping plays the station. */
@Composable
private fun HomeRadioPill(name: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(cornerM()))
            .background(Surface)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(cornerM()))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.SettingsInputAntenna, null, tint = BrandTeal, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(88.dp)) {
            Text(
                name,
                color = Color.White,
                fontSize = textBodyM(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsInputAntenna, null, tint = BrandTeal, modifier = Modifier.size(9.dp))
                Spacer(Modifier.width(4.dp))
                Text("Live", color = NavUnselected, fontSize = textMicro())
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: TrackEntity,
    albumCoverArtId: String?,
    isActive: Boolean = false,
    isLiked: Boolean = false,
    isDisliked: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    onToggleDislike: (() -> Unit)? = null,
    onTrackClick: ((TrackEntity) -> Unit)? = null,
) {
    val ds = downloadStatus(track.isDownloaded, false, track.cachedFilePath != null)
    SongListRow(
        title = track.title,
        subtitle = track.artist,
        isActive = isActive,
        downloadStatus = ds,
        durationLabel = track.durationSeconds?.let { formatSongDuration(it) },
        isLiked = if (onToggleLike != null) isLiked else null,
        isDisliked = if (onToggleDislike != null) isDisliked else null,
        onLike = onToggleLike,
        onDislike = onToggleDislike,
        onClick = onTrackClick?.let { handler -> { handler(track) } },
        modifier = Modifier.padding(horizontal = spacingL(), vertical = 10.dp),
        leadingContent = {
            Box(
                Modifier.size(iconLarge()).clip(RoundedCornerShape(cornerS())),
                contentAlignment = Alignment.Center,
            ) {
                val resolvedUrl = rememberPreferredCoverArt(
                    coverArtId = albumCoverArtId,
                    artist = track.artist,
                    album = null,
                    size = 120,
                )
                if (resolvedUrl != null) {
                    CoverArtImage(
                        url = resolvedUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        decodeSize = iconLarge(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1E1E1E)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, tint = NavUnselected, modifier = Modifier.size(iconSmall()))
                    }
                }
            }
        },
    )
}

private data class Quadruple(val first: ImageVector, val second: String, val third: String)
