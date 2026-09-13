package com.lucasdss.ftpmusic.app.ui.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.components.DownloadDot
import com.lucasdss.ftpmusic.app.ui.components.FavoriteThumbButton
import com.lucasdss.ftpmusic.app.ui.components.downloadStatus
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String = "",
    onAlbumClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(artistId) { viewModel.loadArtist(artistId) }

    // Fetch + display artist art (iTunes fallback cached on disk)
    val context = LocalContext.current
    var artistArtUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.name) {
        if (state.name.isNotEmpty()) {
            CoverArtFallbackService(context).fetchArtistArt(state.name).collect { url ->
                artistArtUrl = url
            }
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showArtistSheet by remember { mutableStateOf(false) }
    var showTrackSheet by remember { mutableStateOf<Int?>(null) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var playlistTrackIds by remember { mutableStateOf(emptyList<String>()) }

    Box(Modifier.fillMaxSize().background(Color(0xFF12121E))) {
        Column(Modifier.fillMaxSize()) {
            // ═══ Hero section — artist art with gradient overlay (Album pattern) ═══
            // Half the Album hero height (260/2 = 130dp) — compact header so more
            // track/album rows are visible below.
            Box(Modifier.fillMaxWidth().height(adp(130f))) {
                if (artistArtUrl != null) {
                    AsyncImage(
                        model = artistArtUrl,
                        contentDescription = state.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1E1E1E)), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            tint = Color(0xFF444444),
                            modifier = Modifier.size(iconLarge()),
                        )
                    }
                }
                // Gradient overlay
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF12121E).copy(alpha = 0.3f), Color(0xFF12121E).copy(alpha = 0.98f)),
                        ),
                    ),
                )
                // Back button
                Box(
                    Modifier.padding(top = spacingL(), start = spacingL()).size(36.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        "Back",
                        tint = Color.White,
                        modifier = Modifier.size(iconSmall()),
                    )
                }
                // Artist name at bottom
                Column(
                    Modifier.align(Alignment.BottomStart).padding(horizontal = spacingXL(), vertical = spacingL()),
                ) {
                    Text(
                        state.name.ifEmpty {
                            "Artist"
                        },
                        color = Color.White,
                        fontSize = textDisplay(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${state.albums.size} albums", color = Color(0xFF888888), fontSize = textLabelM())
                        if (state.albums.isNotEmpty() && state.tracks.isNotEmpty()) {
                            Text(" · ", color = Color(0xFF444444), fontSize = textLabelM())
                        }
                        if (state.tracks.isNotEmpty()) {
                            Text("${state.tracks.size} tracks", color = Color(0xFF888888), fontSize = textLabelM())
                        }
                    }
                }
            }
            // Content below hero — weight(1f) fills remaining space so the
            // inner tab Box(weight 1f) + LazyColumn/Grid get bounded height.
            Column(Modifier.weight(1f)) {
                // Public rating (MusicBrainz) — ABOVE Play/Shuffle (parity with Album)
                state.publicRating?.let { rating ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "★ %.1f".format(rating),
                            color = Color(0xFFFFC107),
                            fontSize = textBodyM(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        state.publicRatingVotes?.let { votes ->
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "($votes votes · MusicBrainz)",
                                color = Color(0xFF666666),
                                fontSize = textLabelM(),
                            )
                        }
                    }
                }
                // Play controls row — consistent Box pattern (Album reference)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Play — gradient
                    Box(
                        Modifier.weight(1f).height(adp(42f)).clip(RoundedCornerShape(cornerM()))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF00C8B4), Color(0xFFB040E8)),
                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(
                                        Float.POSITIVE_INFINITY,
                                        Float.POSITIVE_INFINITY,
                                    ),
                                ),
                            )
                            .clickable(enabled = state.tracks.isNotEmpty()) { viewModel.playAll() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PlayArrow,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(knobSize()),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Play All",
                                color = Color.White,
                                fontSize = textBodyM(),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    // Shuffle — dark with gradient border (consistent across screens)
                    Box(
                        Modifier.weight(1f).height(adp(42f)).clip(RoundedCornerShape(cornerM()))
                            .background(Color(0xFF252538))
                            .border(
                                1.dp,
                                Brush.horizontalGradient(listOf(Color(0xFF00C8B4), Color(0xFFB040E8))),
                                RoundedCornerShape(cornerM()),
                            )
                            .clickable(enabled = state.tracks.isNotEmpty()) { viewModel.shuffle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Shuffle,
                                null,
                                tint = Color(0xFFCCCCCC),
                                modifier = Modifier.size(knobSize()),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Shuffle",
                                color = Color(0xFFCCCCCC),
                                fontSize = textBodyM(),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    // ⋮ button — artist action sheet
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(cornerM()))
                            .background(Color(0xFF252538))
                            .clickable(enabled = state.tracks.isNotEmpty()) { showArtistSheet = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            null,
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                // Similar artists (last.fm)
                if (state.similarArtists.isNotEmpty()) {
                    Text(
                        "Similar Artists",
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = spacingL(), vertical = 2.dp),
                    )
                    LazyRow(
                        Modifier.fillMaxWidth().padding(horizontal = spacingL()),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(state.similarArtists) { _, sa ->
                            Column(
                                Modifier.clickable { /* navigate to similar artist — optional */ }.width(64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF1E1E2E)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        sa.name.take(1).uppercase(),
                                        color = Color(0xFF00C8B4),
                                        fontSize = textHeadingL(),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    sa.name,
                                    color = Color.White,
                                    fontSize = textLabelM(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                // Tab row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF12121E),
                    contentColor = Color(0xFF00C8B4),
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Top Tracks") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Albums") },
                    )
                }
                Spacer(Modifier.height(0.dp))
                // Tab content — weight(1f) bounds the inner LazyColumn/Grid
                Box(Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        TracksTab(state = state, viewModel = viewModel, onTrackMenu = { showTrackSheet = it })
                    } else {
                        AlbumsTab(state = state, viewModel = viewModel, onAlbumClick = onAlbumClick)
                    }
                }
            }

            // ═══ Overwrite Protection (Ask mode) ═══
            val showOverwrite by viewModel.showOverwriteModal.collectAsStateWithLifecycle()
            if (showOverwrite) {
                android.app.AlertDialog.Builder(context).apply {
                    setTitle("Tracks in your queue")
                    setMessage(
                        "You have tracks in your Priority Queue. Do you want to clear them and play this artist, or keep them?",
                    )
                    setNegativeButton("Keep Queue") { _, _ -> viewModel.resolveOverwrite(false) }
                    setPositiveButton("Clear & Play") { _, _ -> viewModel.resolveOverwrite(true) }
                    setOnCancelListener { viewModel.resolveOverwrite(false) }
                    show()
                }
            }

            // ═══ Artist Action Sheet ═══
            if (showArtistSheet) {
                ArtistActionSheet(
                    trackCount = state.tracks.size,
                    onPlayNextAll = {
                        viewModel.playNextAll()
                        android.widget.Toast.makeText(
                            context,
                            "Playing next",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        showArtistSheet = false
                    },
                    onAddAllToQueue = {
                        viewModel.addAllToQueue()
                        android.widget.Toast.makeText(
                            context,
                            "${state.tracks.size} tracks added to queue",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        showArtistSheet = false
                    },
                    onAddAllToPlaylist = {
                        playlistTrackIds = state.tracks.map { it.id }
                        showPlaylistPicker = true
                        showArtistSheet = false
                    },
                    onDownloadAll = {
                        viewModel.downloadAll()
                        showArtistSheet = false
                    },
                    onDismiss = { showArtistSheet = false },
                )
            }

            // ═══ Track Action Sheet ═══
            showTrackSheet?.let { idx ->
                val track = state.tracks.getOrNull(idx) ?: return@let
                ArtistTrackActionSheet(
                    track = track,
                    onAddToQueue = {
                        val url = viewModel.buildStreamUrl(track.id)
                        viewModel.addToQueueTrack(track, url)
                        android.widget.Toast.makeText(
                            context,
                            "Added to queue",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        showTrackSheet = null
                    },
                    onPlayNext = {
                        val url = viewModel.buildStreamUrl(track.id)
                        viewModel.playNextTrack(track, url)
                        android.widget.Toast.makeText(
                            context,
                            "Playing next",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        showTrackSheet = null
                    },
                    onAddToPlaylist = {
                        playlistTrackIds = listOf(track.id)
                        showPlaylistPicker = true
                        showTrackSheet = null
                    },
                    onDownload = {
                        viewModel.downloadTrack(track)
                        showTrackSheet = null
                    },
                    onDismiss = { showTrackSheet = null },
                )
            }

            // ═══ Playlist Picker ═══
            if (showPlaylistPicker) {
                var showCreatePlaylist by remember { mutableStateOf(false) }
                val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                LaunchedEffect(showPlaylistPicker) { viewModel.loadPlaylists() }
                ModalBottomSheet(
                    onDismissRequest = {
                        showPlaylistPicker = false
                        playlistTrackIds = emptyList()
                    },
                    containerColor = Color(0xFF1C1C2E),
                    shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
                ) {
                    Column(Modifier.padding(bottom = spacing3XL())) {
                        Box(
                            Modifier.fillMaxWidth().padding(top = spacingS()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.width(
                                    32.dp,
                                ).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)),
                            )
                        }
                        Text(
                            "Add to Playlist",
                            color = Color.White,
                            fontSize = textHeadingM(),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                        )
                        if (playlists.isEmpty()) {
                            Text(
                                "No playlists yet",
                                color = Color(0xFF888888),
                                fontSize = textBodyM(),
                                modifier = Modifier.padding(horizontal = spacingL()),
                            )
                        } else {
                            playlists.forEach { pl ->
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.04f),
                                    modifier = Modifier.padding(horizontal = spacingL()),
                                )
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        viewModel.addToPlaylist(pl.id, playlistTrackIds)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Added to ${pl.name}",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                        showPlaylistPicker = false
                                        playlistTrackIds = emptyList()
                                    }.padding(horizontal = spacingL(), vertical = spacingM()),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic,
                                        null,
                                        tint = Color(0xFF444444),
                                        modifier = Modifier.size(iconSmall()),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        pl.name,
                                        color = Color.White,
                                        fontSize = textBodyM(),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${pl.trackCount} tracks",
                                        color = Color(0xFF888888),
                                        fontSize = textLabelM(),
                                    )
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
private fun TracksTab(state: ArtistDetailState, viewModel: ArtistDetailViewModel, onTrackMenu: (Int) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = spacingM()),
        contentPadding = PaddingValues(vertical = spacingS()),
    ) {
        itemsIndexed(state.tracks) { index, track ->
            TrackRow(
                track = track, index = index,
                onClick = { viewModel.playTrack(index) },
                onLongClick = { onTrackMenu(index) },
                isLiked = viewModel.isTrackLiked(track.id),
                isDisliked = viewModel.isTrackDisliked(track.id),
                rating = viewModel.getTrackRating(track.id),
                onToggleLike = { viewModel.toggleTrackLike(track.id) },
                onToggleDislike = { viewModel.toggleTrackDislike(track.id) },
                onRate = { viewModel.rateTrack(track.id, it) },
            )
        }
        // Load-more trigger: when near the end, fetch the next page
        if (state.hasMoreTracks) {
            item(key = "load-more") {
                LaunchedEffect(state.tracks.size) { viewModel.loadMoreTracks() }
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isLoadingMoreTracks) {
                        CircularProgressIndicator(
                            color = Color(0xFF00C8B4),
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Loading more...", color = Color(0xFF666666), fontSize = textLabelM())
                    }
                }
            }
        } else if (state.tracks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Text("No tracks found", color = Color(0xFF666666), fontSize = textBodyM())
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isLiked: Boolean,
    isDisliked: Boolean,
    rating: Int,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onRate: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}",
            color = Color(0xFF666666),
            fontSize = textBodyM(),
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = Color.White,
                fontSize = textBodyM(),
                fontWeight = FontWeight.Medium,
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
            // Star rating (parity with Album detail)
            Row {
                for (i in 1..5) {
                    Icon(
                        if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                        null,
                        tint = if (i <= rating) Color(0xFF00C8B4) else Color(0xFF444444),
                        modifier = Modifier.size(11.dp).clickable { onRate(i) },
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // ThumbsUp (like == star)
        Icon(
            if (isLiked) Icons.Filled.ThumbUp else Icons.Filled.ThumbUp,
            contentDescription = if (isLiked) "Unlike" else "Like",
            tint = if (isLiked) Color(0xFF00C8B4) else Color(0xFF444444),
            modifier = Modifier.size(knobSize()).clickable { onToggleLike() },
        )
        Spacer(Modifier.width(6.dp))
        // ThumbsDown (dislike — local)
        Icon(
            if (isDisliked) Icons.Filled.ThumbDown else Icons.Filled.ThumbDown,
            contentDescription = if (isDisliked) "Remove dislike" else "Dislike",
            tint = if (isDisliked) Color(0xFFE84040) else Color(0xFF444444),
            modifier = Modifier.size(knobSize()).clickable { onToggleDislike() },
        )
        Spacer(Modifier.width(6.dp))
        track.duration?.let { d ->
            Text(
                formatDuration(d),
                color = Color(0xFF666666),
                fontSize = textLabelM(),
            )
        }
        Spacer(Modifier.width(4.dp))
        // ⋮ menu
        Icon(
            Icons.Default.MoreVert,
            null,
            tint = Color(0xFF444444),
            modifier = Modifier.size(knobSize()).clickable { onLongClick() },
        )
    }
}

// ─── Artist Action Sheet ───
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistActionSheet(
    trackCount: Int,
    onPlayNextAll: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onAddAllToPlaylist: () -> Unit,
    onDownloadAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C2E),
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(bottom = spacing3XL())) {
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Artist · $trackCount tracks", color = Color(0xFF888888), fontSize = textBodyM())
            }
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(horizontal = spacingL()),
            )
            ArtistSheetAction(
                "Play Next",
                "Insert $trackCount tracks after current",
                Icons.Default.ArrowUpward,
                Color(0xFF00C8B4),
                onPlayNextAll,
            )
            ArtistSheetAction(
                "Add to Queue",
                "Append $trackCount tracks",
                Icons.AutoMirrored.Filled.QueueMusic,
                Color(0xFF5B8DEE),
                onAddAllToQueue,
            )
            ArtistSheetAction(
                "Add to Playlist",
                "Save artist tracks to a playlist",
                Icons.AutoMirrored.Filled.PlaylistAdd,
                Color(0xFFB040E8),
                onAddAllToPlaylist,
            )
            ArtistSheetAction(
                "Download All",
                "Save all tracks offline",
                Icons.Default.Download,
                Color(0xFF888888),
                onDownloadAll,
            )
        }
    }
}

// ─── Artist Track Action Sheet ───
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistTrackActionSheet(
    track: Track,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C2E),
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(bottom = spacing3XL())) {
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val coverUrl = rememberCoverArtUrl(track.coverArt, 120)
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier.size(iconLarge()).clip(RoundedCornerShape(cornerS())),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier.size(iconLarge()).clip(RoundedCornerShape(cornerS())).background(Color(0xFF1E1E1E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = Color(0xFF555555), modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        track.title,
                        color = Color.White,
                        fontSize = textHeadingS(),
                        fontWeight = FontWeight.SemiBold,
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
            }
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(horizontal = spacingL()),
            )
            ArtistSheetAction(
                "Play Next",
                "Insert at top of Priority Queue",
                Icons.Default.ArrowUpward,
                Color(0xFF00C8B4),
                onPlayNext,
            )
            ArtistSheetAction(
                "Add to Queue",
                "Append to Priority Queue",
                Icons.AutoMirrored.Filled.QueueMusic,
                Color(0xFF5B8DEE),
                onAddToQueue,
            )
            ArtistSheetAction(
                "Add to Playlist",
                "Save to an existing playlist",
                Icons.AutoMirrored.Filled.PlaylistAdd,
                Color(0xFFB040E8),
                onAddToPlaylist,
            )
            ArtistSheetAction(
                "Download",
                "Save for offline playback",
                Icons.Default.Download,
                Color(0xFF888888),
                onDownload,
            )
        }
    }
}

@Composable
private fun ArtistSheetAction(label: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = spacingL(), vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = textBodyM(), fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF888888), fontSize = textLabelM())
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = spacingL()))
}

@Composable
private fun AlbumsTab(state: ArtistDetailState, viewModel: ArtistDetailViewModel, onAlbumClick: (String) -> Unit) {
    val albums = state.albums
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No albums found", color = Color(0xFF666666), fontSize = textBodyM())
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = spacingM()),
        contentPadding = PaddingValues(vertical = spacingS()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums) { album ->
            Column(Modifier.clickable { onAlbumClick(album.id) }) {
                // Cover art
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(cornerM())),
                    contentAlignment = Alignment.Center,
                ) {
                    val coverUrl = rememberCoverArtUrl(album.coverArt, 300)
                    if (coverUrl != null) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = album.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
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
                    // v43: Like/Dislike thumbs — top-left corner (Library card parity)
                    Row(
                        Modifier.align(Alignment.TopStart).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FavoriteThumbButton(
                            icon = Icons.Filled.ThumbUp,
                            active = album.id in state.likedAlbumIds,
                            contentDescription = if (album.id in state.likedAlbumIds) "Unlike album" else "Like album",
                            onClick = { viewModel.toggleAlbumLike(album.id) },
                        )
                        FavoriteThumbButton(
                            icon = Icons.Filled.ThumbDown,
                            active = album.id in state.dislikedAlbumIds,
                            activeTint = Color(0xFFE84040),
                            contentDescription = if (album.id in
                                state.dislikedAlbumIds
                            ) {
                                "Remove dislike"
                            } else {
                                "Dislike album"
                            },
                            onClick = { viewModel.toggleAlbumDislike(album.id) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Album name
                Text(
                    album.name,
                    color = Color.White,
                    fontSize = textBodyM(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Year or track count
                val subtitle = album.year?.toString() ?: album.songCount?.let { "$it tracks" } ?: ""
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
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

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
