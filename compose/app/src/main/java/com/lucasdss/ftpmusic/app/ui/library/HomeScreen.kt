package com.lucasdss.ftpmusic.app.ui.library

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.components.AlbumDownloadBadge
import com.lucasdss.ftpmusic.app.ui.components.ArtistAvatar
import com.lucasdss.ftpmusic.app.ui.components.CoverArtImage
import com.lucasdss.ftpmusic.app.ui.components.DownloadDot
import com.lucasdss.ftpmusic.app.ui.components.downloadStatus
import com.lucasdss.ftpmusic.app.ui.player.CastButton

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
    // Active track for EQ animation in Recently Played and Recently Added
    currentTrackId: String? = null,
    currentAlbumId: String? = null,
    isPlaying: Boolean = false,
    // Opens Settings → Server (banner "Fix" action)
    onOpenServerSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAlbumSheet by remember { mutableStateOf<com.lucasdss.ftpmusic.app.data.model.Album?>(null) }

    // Refresh recently played each time Home becomes visible
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshRecentlyPlayed()
            viewModel.loadStats()
            viewModel.loadGenres()
            viewModel.loadDailyMixes()
            viewModel.refreshHomePrefs()
            viewModel.healEmptyLedger()
            viewModel.loadPlaylists()
        }
    }

    Column(Modifier.background(Color(0xFF12121E))) {
        // Server config/reachability warning (stale proxy URL, unreachable server)
        com.lucasdss.ftpmusic.app.ui.components.ServerErrorBanner(
            configWarning = state.configWarning,
            isOffline = viewModel.isOffline(),
            onOpenServerSettings = onOpenServerSettings,
        )
        // Data-first: the full-screen spinner only shows until the first
        // content render; a stalled loader must never leave Home spinning.
        if (state.isLoading && !state.hasLoadedOnce) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00C8B4))
            }
        } else {
            // Hoisted: recomputed only when the playlist list changes, not on
            // every LazyColumn recomposition (scroll/EQ frames).
            val syncedPlaylists = remember(state.playlists) { state.playlists.filter { it.isSynced } }
            LazyColumn {
                // ── Surprise Me hero card ──
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1E1E2E))
                            .border(
                                2.dp,
                                Brush.horizontalGradient(listOf(Color(0xFF00C8B4), Color(0xFFB040E8))),
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { viewModel.playSurpriseMe() },
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Inset image — rounded square (button affordance)
                            Image(
                                painter = painterResource(R.drawable.random),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Surprise Me",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Random music from your library",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

                // ── Daily Mixes ──
                if (state.mixCards.isNotEmpty()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacingL(), vertical = spacingXS()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Daily Mixes",
                                color = Color.White,
                                fontSize = textHeadingM(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { viewModel.refreshAllMixes() },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    "Refresh mixes",
                                    tint = Color(0xFF00C8B4),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(spacingM()),
                            contentPadding = PaddingValues(horizontal = spacingL()),
                            modifier = Modifier.semantics { testTag = "daily_mix_row" },
                        ) {
                            items(state.mixCards, key = { it.id }) { mix ->
                                GenreMixCard(
                                    mix = mix,
                                    onClick = { onMixClick(mix.id) },
                                    modifier = Modifier.semantics { testTag = "daily_mix_card_${mix.id}" },
                                )
                            }
                        }
                    }
                } else if (state.isResyncing || state.isGeneratingMixes) {
                    item {
                        Text(
                            "Building your Daily Mixes…",
                            color = Color(0xFF666666),
                            fontSize = textBodyM(),
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                        )
                    }
                } else {
                    // No mixes exist yet
                    item {
                        Text(
                            "Daily Mixes will appear after sync completes",
                            color = Color(0xFF555555),
                            fontSize = textBodyM(),
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                        )
                    }
                }

                // ── Playlists (synced only) ──
                if (state.showPlaylistsOnHome && syncedPlaylists.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingXS()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Playlists",
                                color = Color.White,
                                fontSize = textHeadingM(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "See all",
                                color = Color(0xFF00C8B4),
                                fontSize = textLabelM(),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { onPlaylistsClick() },
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(spacingM()),
                            contentPadding = PaddingValues(horizontal = spacingL()),
                            modifier = Modifier.semantics { testTag = "home_playlists_row" },
                        ) {
                            items(syncedPlaylists, key = { it.id }) { pl ->
                                HomePlaylistCard(
                                    playlist = pl,
                                    montageCovers = state.playlistMontages[pl.id].orEmpty(),
                                    onClick = { onPlaylistClick(pl.id) },
                                )
                            }
                        }
                    }
                }

                // ── Favorite Artists ──
                if (state.showFavArtistsSection && state.starredArtists.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingXS()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Favorite Artists",
                                color = Color.White,
                                fontSize = textHeadingM(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Default.ThumbUp,
                                null,
                                tint = Color(0xFF00C8B4),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = spacingL()),
                            modifier = Modifier.semantics { testTag = "home_fav_artists_row" },
                        ) {
                            items(state.starredArtists, key = { it.id }) { artist ->
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
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        artist.name,
                                        color = Color.White,
                                        fontSize = textLabelM(),
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
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingXS()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Favorite Albums",
                                color = Color.White,
                                fontSize = textHeadingM(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Default.ThumbUp,
                                null,
                                tint = Color(0xFF00C8B4),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(spacingM()),
                            contentPadding = PaddingValues(horizontal = spacingL()),
                            modifier = Modifier.semantics { testTag = "home_fav_albums_row" },
                        ) {
                            items(state.starredAlbums, key = { it.id }) { album ->
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
                                            size = 300,
                                        )
                                        if (url != null) {
                                            CoverArtImage(
                                                url = url,
                                                contentDescription = album.name,
                                                modifier = Modifier.fillMaxSize(),
                                                fallbackArtist = album.artist,
                                                fallbackAlbum = album.name,
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
                                    Spacer(Modifier.height(6.dp))
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
                                        color = Color(0xFF555555),
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
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingXS()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Favorite Radio",
                                color = Color.White,
                                fontSize = textHeadingM(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Default.Bookmark,
                                null,
                                tint = Color(0xFF00C8B4),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = spacingL()),
                            modifier = Modifier.semantics { testTag = "home_fav_radio_row" },
                        ) {
                            items(state.bookmarkedRadio, key = { it.stationId }) { station ->
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
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacingL(), vertical = spacingXS()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Tuned In",
                                color = Color.White,
                                fontSize = textHeadingM(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                tint = Color(0xFF555555),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    item {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(spacingS()),
                            verticalArrangement = Arrangement.spacedBy(spacingS()),
                        ) {
                            val genreColors = listOf(
                                Color(0xFF00C8B4),
                                Color(0xFFB040E8),
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
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = color, modifier = Modifier.size(adp(10f)))
                                    Spacer(Modifier.width(4.dp))
                                    Text(genre, color = color, fontSize = textBodyM(), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // ── Recently Added albums ──
                if (state.randomAlbums.isNotEmpty()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacingL(), vertical = spacingXS()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Recently Added",
                                color = Color.White,
                                fontSize = textHeadingM(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Default.Refresh,
                                null,
                                tint = Color(0xFF555555),
                                modifier = Modifier.size(18.dp),
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                tint = Color(0xFF555555),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(spacingM()),
                            contentPadding = PaddingValues(horizontal = spacingL()),
                        ) {
                            items(state.randomAlbums.take(10), key = { it.id }) { album ->
                                val isActive = currentAlbumId != null && album.id == currentAlbumId && isPlaying
                                AlbumCardDesign(
                                    album,
                                    isActive = isActive,
                                    downloadStatus =
                                        state.downloadStatusByAlbumId[album.id] ?: "none",
                                    onClick = { onAlbumClick(album.id) },
                                    onLongClick = { showAlbumSheet = album },
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
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

@Composable
private fun SectionHeaderHome(title: String) {
    Text(
        title,
        color = Color.White,
        fontSize = textHeadingM(),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AlbumCardDesign(
    album: Album,
    isActive: Boolean = false,
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
                size = 300,
            )
            if (url != null) {
                CoverArtImage(
                    url = url,
                    contentDescription = album.name,
                    modifier = Modifier.fillMaxSize(),
                    fallbackArtist = album.artist,
                    fallbackAlbum = album.name,
                )
            } else {
                Icon(Icons.Default.Album, null, tint = Color(0xFF444444), modifier = Modifier.size(36.dp))
            }
            // EQ overlay on active album
            if (isActive) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedEqBars()
                }
            }
            AlbumDownloadBadge(downloadStatus, Modifier.align(Alignment.BottomEnd).padding(4.dp))
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
                tint = if (i <= rating) Color(0xFF00C8B4) else Color(0xFF444444),
                modifier = Modifier.size(iconMicro()),
            )
        }
    }
}

@Composable
private fun GenreMixCard(
    mix: com.lucasdss.ftpmusic.app.ui.library.MixCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(albumCardWidth())
            .clip(RoundedCornerShape(cornerM()))
            .clickable { onClick() },
    ) {
        // Single primary cover on Home LazyRow — 4-tile montage binds 4×
        // resolver + Coil and stalls horizontal fling (HOME_SCROLL_PERF).
        Box(
            Modifier
                .width(albumCardWidth())
                .aspectRatio(1f)
                .clip(RoundedCornerShape(cornerM()))
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            val primaryId = mix.coverArts.firstOrNull()
            if (primaryId != null) {
                val url = rememberPreferredCoverArt(
                    coverArtId = primaryId,
                    artist = null,
                    album = null,
                    size = 300,
                )
                if (url != null) {
                    CoverArtImage(
                        url = url,
                        contentDescription = mix.name,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = Color(0xFF444444), modifier = Modifier.size(36.dp))
                }
            } else {
                Icon(Icons.Default.MusicNote, null, tint = Color(0xFF444444), modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            mix.name,
            color = Color.White,
            fontSize = textBodyM(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
private fun HomePlaylistCard(playlist: PlaylistView, montageCovers: List<String>, onClick: () -> Unit) {
    Column(Modifier.width(albumCardWidth()).clickable { onClick() }) {
        Box(
            Modifier
                .width(albumCardWidth())
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            val primaryId = montageCovers.firstOrNull()
            if (primaryId != null) {
                val url = rememberPreferredCoverArt(
                    coverArtId = primaryId,
                    artist = null,
                    album = null,
                    size = 300,
                )
                if (url != null) {
                    CoverArtImage(
                        url = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.QueueMusic, null, tint = Color(0xFF444444), modifier = Modifier.size(32.dp))
                }
            } else {
                Icon(Icons.Default.QueueMusic, null, tint = Color(0xFF444444), modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF00C8B4), modifier = Modifier.size(10.dp))
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
            color = Color(0xFF555555),
            fontSize = textLabelM(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Compact pill card for the Home Favorite Radio row: 40dp icon tile, name,
 *  teal "Live" badge. Tapping plays the station. */
@Composable
private fun HomeRadioPill(name: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(cornerM()))
            .background(Color(0xFF1C1C2E))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(cornerM()))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.SettingsInputAntenna, null, tint = Color(0xFF00C8B4), modifier = Modifier.size(20.dp))
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
                Icon(Icons.Default.SettingsInputAntenna, null, tint = Color(0xFF00C8B4), modifier = Modifier.size(9.dp))
                Spacer(Modifier.width(4.dp))
                Text("Live", color = Color(0xFF555555), fontSize = 10.sp)
            }
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
                    .fillMaxHeight(anim)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF00C8B4)),
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: TrackEntity,
    albumCoverArtId: String?,
    isActive: Boolean = false,
    onTrackClick: ((TrackEntity) -> Unit)? = null,
) {
    val eqAnimation = rememberInfiniteTransition(label = "eq")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = 10.dp).then(
            if (onTrackClick !=
                null
            ) {
                Modifier.clickable { onTrackClick(track) }
            } else {
                Modifier
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album cover art
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
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF1E1E1E)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = Color(0xFF555555), modifier = Modifier.size(iconSmall()))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isActive) Color(0xFF00C8B4) else Color.White,
                fontSize = textHeadingS(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            track.artist?.let {
                Text(
                    it,
                    color = Color(0xFF888888),
                    fontSize = textBodyM(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // EQ bars for active track
        if (isActive) {
            Row(Modifier.width(16.dp).height(16.dp).padding(end = spacingS()), verticalAlignment = Alignment.Bottom) {
                val delays = listOf(0, 150, 300)
                val heights = listOf(0.4f, 0.7f, 1.0f)
                for (i in 0..2) {
                    val anim by eqAnimation.animateFloat(
                        initialValue = heights[i] * 0.4f,
                        targetValue = heights[i],
                        animationSpec = infiniteRepeatable(
                            animation = tween(400 + delays[i], easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "eq$i",
                    )
                    Box(
                        Modifier.width(
                            3.dp,
                        ).fillMaxHeight(anim).clip(RoundedCornerShape(1.dp)).background(Color(0xFF00C8B4)),
                    )
                    if (i < 2) Spacer(Modifier.width(2.dp))
                }
            }
        }
        val ds = downloadStatus(track.isDownloaded, false, track.cachedFilePath != null)
        if (ds != "none") {
            DownloadDot(ds)
            Spacer(Modifier.width(6.dp))
        }
        track.durationSeconds?.let { secs ->
            val mins = secs / 60
            val sec = secs % 60
            Text("$mins:${sec.toString().padStart(2, '0')}", color = Color(0xFF888888), fontSize = textBodyM())
        }
    }
}

private data class Quadruple(val first: ImageVector, val second: String, val third: String)
