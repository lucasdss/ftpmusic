package com.lucasdss.ftpmusic.app.ui.album

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.components.DownloadDot
import com.lucasdss.ftpmusic.app.ui.components.downloadStatus
import com.lucasdss.ftpmusic.app.ui.library.PlaylistView
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl
import com.lucasdss.ftpmusic.app.ui.library.rememberPreferredCoverArt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onTrackClick: (Track) -> Unit = {},
    onPlayAll: (List<Track>) -> Unit = {},
    onShuffle: (List<Track>) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onBack: () -> Unit = {},
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTrackSheet by remember { mutableStateOf<Int?>(null) }
    var showAlbumSheet by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var playlistTrackIds by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(albumId) { viewModel.loadAlbum(albumId) }

    val context = LocalContext.current
    val fallbackService = remember { CoverArtFallbackService.getInstance(context) }
    val artUrl = rememberPreferredCoverArt(
        coverArtId = state.album?.coverArt,
        artist = state.album?.artist ?: "",
        album = state.album?.name ?: "",
        size = 600,
        fallbackService = fallbackService,
    ) ?: state.album?.coverArt?.let { viewModel.buildCoverArtUrl(it) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize().background(Color(0xFF12121E)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00C8B4))
        }
        return
    }

    if (state.error != null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF12121E)), contentAlignment = Alignment.Center) {
            Text("Error: ${state.error}", color = Color.White)
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF12121E))) {
        LazyColumn {
            // ═══ Hero section — full-width art with gradient overlay ═══
            item {
                Box(Modifier.fillMaxWidth().height(heroHeaderHeight())) {
                    if (artUrl != null) {
                        AsyncImage(
                            model = artUrl,
                            contentDescription = state.album?.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(0xFF1E1E1E)), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Album,
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
                    // Download badge top-right
                    val albumStatus = viewModel.getAlbumDownloadStatus()
                    if (albumStatus != "none") {
                        Row(
                            Modifier.align(Alignment.TopEnd).padding(top = spacingL(), end = spacingL())
                                .clip(RoundedCornerShape(50)).background(
                                    if (albumStatus == "downloaded") {
                                        Color(0xFFB040E8).copy(alpha = 0.85f)
                                    } else {
                                        Color(0xFF1E1E30).copy(alpha = 0.85f)
                                    },
                                ).padding(horizontal = 10.dp, vertical = spacingXS()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (albumStatus == "downloaded") Icons.Default.CheckCircle else Icons.Default.Check,
                                null,
                                tint = if (albumStatus == "downloaded") Color.White else Color(0xFFAAAAAA),
                                modifier = Modifier.size(iconMicro()),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (albumStatus == "downloaded") "Downloaded" else "Partial",
                                color = if (albumStatus == "downloaded") Color.White else Color(0xFFAAAAAA),
                                fontSize = textLabelM(),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    // Album info at bottom
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(horizontal = spacingXL(), vertical = spacingL()),
                    ) {
                        Text(
                            state.album?.name ?: "",
                            color = Color.White,
                            fontSize = textDisplay(),
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.album?.artist?.let {
                            Text(
                                it,
                                color = Color(0xFF00C8B4),
                                fontSize = textHeadingS(),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Row(
                            Modifier.padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            state.album?.year?.let { Text("$it", color = Color(0xFF888888), fontSize = textLabelM()) }
                            if (state.album?.year != null && state.tracks.isNotEmpty()) {
                                Text(" · ", color = Color(0xFF444444), fontSize = textLabelM())
                            }
                            Text("${state.tracks.size} tracks", color = Color(0xFF888888), fontSize = textLabelM())
                            state.album?.let { alb ->
                                alb.rating?.let { r ->
                                    if (r > 0) {
                                        Text(" · ", color = Color(0xFF444444), fontSize = textLabelM())
                                        Row {
                                            for (i in 1..5) {
                                                Icon(
                                                    if (i <= r) Icons.Default.Star else Icons.Default.StarBorder,
                                                    null,
                                                    tint = if (i <= r) Color(0xFF00C8B4) else Color(0xFF444444),
                                                    modifier = Modifier.size(iconMicro())
                                                        .clickable { viewModel.rateAlbum(albumId, i) },
                                                )
                                            }

                                            // ── Overwrite Protection ──
                                            val showOverwrite by
                                                viewModel.showOverwriteModal.collectAsStateWithLifecycle()
                                            if (showOverwrite) {
                                                android.app.AlertDialog.Builder(context).apply {
                                                    setTitle("Tracks in your queue")
                                                    setMessage(
                                                        "You have tracks in your Priority Queue. Do you want to clear them and play this album, or keep them?",
                                                    )
                                                    setNegativeButton("Keep Queue") { _, _ ->
                                                        viewModel.resolveOverwrite(false)
                                                    }
                                                    setPositiveButton("Clear & Play") { _, _ ->
                                                        viewModel.resolveOverwrite(true)
                                                    }
                                                    setOnCancelListener { viewModel.resolveOverwrite(false) }
                                                    show()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Public rating (MusicBrainz)
            item {
                state.publicRating?.let { pr ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = spacingXL(), vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "★ %.1f".format(pr),
                            color = Color(0xFFFFC107),
                            fontSize = textLabelM(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        state.publicRatingVotes?.let { votes ->
                            Spacer(Modifier.width(4.dp))
                            Text("($votes votes · MusicBrainz)", color = Color(0xFF666666), fontSize = textLabelM())
                        }
                    }
                }
            }

            // ═══ Action row: Play | Shuffle | ⋮ ═══
            if (state.tracks.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = spacingXL(), vertical = spacingL()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Play button — gradient
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
                                .clickable { viewModel.playAll() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(knobSize()),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Play", color = Color.White, fontSize = textBodyM(), fontWeight = FontWeight.Bold)
                            }
                        }
                        // Shuffle button — dark with gradient border (consistent across screens)
                        Box(
                            Modifier.weight(1f).height(adp(42f)).clip(RoundedCornerShape(cornerM()))
                                .background(Color(0xFF252538))
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(listOf(Color(0xFF00C8B4), Color(0xFFB040E8))),
                                    RoundedCornerShape(cornerM()),
                                )
                                .clickable { viewModel.shuffle() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    null,
                                    tint = Color(0xFFCCCCCC),
                                    modifier = Modifier.size(knobSize()),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Shuffle",
                                    color = Color(0xFFCCCCCC),
                                    fontSize = textBodyM(),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        // ⋮ button
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(cornerM()))
                                .background(Color(0xFF252538)).clickable { showAlbumSheet = true },
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
                }
            }

            // ═══ Track list ═══
            itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track ->
                val isActive = currentTrackId != null && track.id == currentTrackId && isPlaying
                val isCached = viewModel.isCached(track.id)
                val isDownloaded = viewModel.isDownloaded(track.id)
                val isQueued = viewModel.isQueued(track.id)
                val ds = downloadStatus(isDownloaded, isQueued, isCached)

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacingXL(), vertical = spacingM()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Track number + info — tap to play, long-press for menu
                    Row(
                        Modifier.weight(1f).combinedClickable(
                            onClick = { viewModel.playTrack(index) },
                            onLongClick = { showTrackSheet = index },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Track number / EQ
                        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                            if (isActive) {
                                AnimatedEqBars()
                            } else {
                                Text(
                                    "${index + 1}",
                                    color = Color(0xFF666666),
                                    fontSize = textLabelM(),
                                    fontFamily = interFontFamily(),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        // Info
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                color = if (isActive) Color(0xFF00C8B4) else Color.White,
                                fontSize = textHeadingS(),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Row {
                                for (i in 1..5) {
                                    val r = viewModel.getTrackRating(track.id)
                                    Icon(
                                        if (i <= r) Icons.Default.Star else Icons.Default.StarBorder,
                                        null,
                                        tint = if (i <= r) Color(0xFF00C8B4) else Color(0xFF444444),
                                        modifier = Modifier.size(11.dp).clickable { viewModel.rateTrack(track.id, i) },
                                    )
                                }
                            }
                        }
                    } // end playable area
                    Spacer(Modifier.width(8.dp))
                    // Right-side icons — independent tap targets
                    DownloadDot(ds)
                    Spacer(Modifier.width(6.dp))
                    // ThumbsUp (like == star)
                    val isLiked = viewModel.isTrackLiked(track.id)
                    Icon(
                        Icons.Filled.ThumbUp,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) Color(0xFF00C8B4) else Color(0xFF444444),
                        modifier = Modifier.size(knobSize()).clickable { viewModel.toggleTrackLike(track.id) },
                    )
                    Spacer(Modifier.width(6.dp))
                    // ThumbsDown (dislike — local)
                    val isDisliked = viewModel.isTrackDisliked(track.id)
                    Icon(
                        Icons.Filled.ThumbDown,
                        contentDescription = if (isDisliked) "Remove dislike" else "Dislike",
                        tint = if (isDisliked) Color(0xFFE84040) else Color(0xFF444444),
                        modifier = Modifier.size(knobSize()).clickable { viewModel.toggleTrackDislike(track.id) },
                    )
                    Spacer(Modifier.width(6.dp))
                    // Duration
                    Text(
                        track.formattedDuration,
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                        fontFamily = interFontFamily(),
                    )
                    Spacer(Modifier.width(4.dp))
                    // ⋮
                    Icon(
                        Icons.Default.MoreVert,
                        null,
                        tint = Color(0xFF444444),
                        modifier = Modifier.size(knobSize()).clickable { showTrackSheet = index },
                    )
                }
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier.padding(horizontal = spacingXL()),
                )
            }
        }
    }

    // ═══ Track Action Sheet ═══
    showTrackSheet?.let { idx ->
        val track = state.tracks.getOrNull(idx) ?: return@let
        TrackActionSheet(
            track = track,
            onAddToQueue = {
                val url = viewModel.buildStreamUrl(track.id)
                viewModel.addToQueueTrack(track, url)
                android.widget.Toast.makeText(context, "Added to queue", android.widget.Toast.LENGTH_SHORT).show()
                showTrackSheet = null
            },
            onPlayNext = {
                val url = viewModel.buildStreamUrl(track.id)
                viewModel.playNextTrack(track, url)
                android.widget.Toast.makeText(context, "Playing next", android.widget.Toast.LENGTH_SHORT).show()
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

    // ═══ Album Action Sheet ═══
    if (showAlbumSheet) {
        AlbumActionSheet(
            album = state.album,
            trackCount = state.tracks.size,
            onPlayAlbum = {
                viewModel.playAll()
                showAlbumSheet = false
            },
            onPlayNextAll = {
                viewModel.playNextAll()
                showAlbumSheet = false
            },
            onAddAllToQueue = {
                viewModel.addToQueue()
                android.widget.Toast.makeText(
                    context,
                    "${state.tracks.size} tracks added to queue",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                showAlbumSheet = false
            },
            onAddAllToPlaylist = {
                playlistTrackIds = state.tracks.map { it.id }
                showPlaylistPicker = true
                showAlbumSheet = false
            },
            onDownloadAll = {
                viewModel.downloadAlbum()
                showAlbumSheet = false
            },
            onDismiss = { showAlbumSheet = false },
        )
    }

    // ═══ Playlist Picker ═══
    if (showPlaylistPicker) {
        var showCreatePlaylist by remember { mutableStateOf(false) }
        var showServerPlaylists by remember { mutableStateOf(false) }
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        LaunchedEffect(showPlaylistPicker) { viewModel.loadPlaylists() }
        PlaylistPickerSheet(
            playlists = playlists,
            onAdd = { plId ->
                viewModel.addToPlaylist(plId, playlistTrackIds)
                showPlaylistPicker = false
                playlistTrackIds = emptyList()
            },
            onCreateNew = { showCreatePlaylist = true },
            onAddFromServer = { showServerPlaylists = true },
            onDismiss = {
                showPlaylistPicker = false
                playlistTrackIds = emptyList()
            },
        )
        // ── Create Playlist dialog ──
        if (showCreatePlaylist) {
            CreatePlaylistDialog(
                onCreate = { name ->
                    viewModel.createPlaylist(name) { newId ->
                        if (newId != null && playlistTrackIds.isNotEmpty()) {
                            viewModel.addToPlaylist(newId, playlistTrackIds)
                        }
                        showCreatePlaylist = false
                        playlistTrackIds = emptyList()
                    }
                },
                onDismiss = { showCreatePlaylist = false },
            )
        }
        // ── Add from Server sheet ──
        if (showServerPlaylists) {
            AddFromServerSheet(
                onImport = { id, name ->
                    viewModel.importPlaylist(id)
                    showServerPlaylists = false
                },
                onDismiss = { showServerPlaylists = false },
            )
        }
    }
}

// ─── Animated EQ bars for active track ───
@Composable
private fun AnimatedEqBars() {
    val transition = rememberInfiniteTransition(label = "eq")
    val bars = listOf(
        transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            "b1",
        ),
        transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(400, easing = FastOutSlowInEasing, delayMillis = 100), RepeatMode.Reverse),
            "b2",
        ),
        transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(550, easing = FastOutSlowInEasing, delayMillis = 200), RepeatMode.Reverse),
            "b3",
        ),
    )
    Row(
        Modifier.width(12.dp).height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { anim ->
            Box(
                Modifier.weight(1f).fillMaxHeight(fraction = 0.35f + anim.value * 0.65f)
                    .clip(RoundedCornerShape(1.dp)).background(Color(0xFF00C8B4)),
            )
        }
    }
}

// ─── Track Action Sheet ───
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackActionSheet(
    track: Track,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit = {},
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
            // Drag handle
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
            }
            // Header
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
                    val subtitle = buildString {
                        track.artist?.let { append(it) }
                        if (track.album != null) {
                            if (isNotEmpty()) append(" · ")
                            append(track.album)
                        }
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
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
            // Actions — Play Next first per design spec (App.tsx:317)
            SheetAction(
                "Play Next",
                "Insert at top of Priority Queue",
                Icons.Default.ArrowUpward,
                Color(0xFF00C8B4),
                onPlayNext,
            )
            SheetAction(
                "Add to Queue",
                "Append to Priority Queue",
                Icons.Default.QueueMusic,
                Color(0xFF5B8DEE),
                onAddToQueue,
            )
            SheetAction(
                "Add to Playlist",
                "Save to an existing playlist",
                Icons.AutoMirrored.Filled.PlaylistAdd,
                Color(0xFFB040E8),
                onAddToPlaylist,
            )
            SheetAction("Download", "Save for offline playback", Icons.Default.Download, Color(0xFF888888), onDownload)
        }
    }
}

// ─── Album Action Sheet ───
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumActionSheet(
    album: com.lucasdss.ftpmusic.app.data.model.Album?,
    trackCount: Int,
    onPlayAlbum: () -> Unit,
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
                val coverUrl = rememberCoverArtUrl(album?.coverArt, 120)
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
                        Icon(Icons.Default.Album, null, tint = Color(0xFF555555), modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        album?.name ?: "",
                        color = Color.White,
                        fontSize = textHeadingS(),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${album?.artist ?: ""} · $trackCount tracks",
                        color = Color(0xFF888888),
                        fontSize = textBodyM(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(horizontal = spacingL()),
            )
            // Per design spec (App.tsx:341-348): Play Album, Play Next, Add to Queue, Add to Playlist, Download
            SheetAction("Play Album", "Resets context queue", Icons.Default.PlayArrow, Color.White, onPlayAlbum)
            SheetAction(
                "Play Next",
                "Insert $trackCount tracks after current",
                Icons.Default.ArrowUpward,
                Color(0xFF00C8B4),
                onPlayNextAll,
            )
            SheetAction(
                "Add to Queue",
                "Append $trackCount tracks",
                Icons.Default.QueueMusic,
                Color(0xFF5B8DEE),
                onAddAllToQueue,
            )
            SheetAction(
                "Add to Playlist",
                "Save album to a playlist",
                Icons.AutoMirrored.Filled.PlaylistAdd,
                Color(0xFFB040E8),
                onAddAllToPlaylist,
            )
            SheetAction(
                "Download Album",
                "Save all tracks offline",
                Icons.Default.Download,
                Color(0xFF888888),
                onDownloadAll,
            )
        }
    }
}

// ─── Playlist Picker Sheet ───
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    playlists: List<PlaylistView>,
    onAdd: (String) -> Unit,
    onCreateNew: () -> Unit,
    onAddFromServer: () -> Unit,
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
            Text(
                "Add to Playlist",
                color = Color.White,
                fontSize = textHeadingM(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
            )
            // New Playlist row
            Row(
                Modifier.fillMaxWidth().clickable {
                    onCreateNew()
                }.padding(horizontal = spacingL(), vertical = spacingM())
                    .clip(
                        RoundedCornerShape(cornerM()),
                    ).border(1.dp, Color(0xFF00C8B4).copy(alpha = 0.3f), RoundedCornerShape(cornerM()))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, null, tint = Color(0xFF00C8B4), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "New Playlist…",
                    color = Color(0xFF00C8B4),
                    fontSize = textBodyM(),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // Add from Server
            Row(
                Modifier.fillMaxWidth().clickable {
                    onAddFromServer()
                }.padding(horizontal = spacingL(), vertical = spacingM()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.CloudDownload, null, tint = Color(0xFF00C8B4), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Add from Server", color = Color.White, fontSize = textBodyM(), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
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
                            onAdd(pl.id)
                        }.padding(horizontal = spacingL(), vertical = spacingM()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.QueueMusic,
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
                        Text("${pl.trackCount} tracks", color = Color(0xFF888888), fontSize = textLabelM())
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C2E),
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(horizontal = spacingXL(), vertical = spacingM())) {
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.width(
                        adp(32f),
                    ).height(adp(4f)).clip(RoundedCornerShape(adp(2f))).background(Color(0xFF444444)),
                )
            }
            Spacer(Modifier.height(spacingL()))
            Text("Create Playlist", color = Color.White, fontSize = textHeadingM(), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(spacingL()))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name\u2026", color = Color(0xFF666666)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00C8B4),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color(0xFF252538),
                    unfocusedContainerColor = Color(0xFF252538),
                    cursorColor = Color(0xFF00C8B4),
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(cornerM()),
                singleLine = true,
            )
            Spacer(Modifier.height(spacingL()))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingM())) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252538)),
                    modifier = Modifier.weight(1f).height(adp(48f)),
                ) {
                    Text("Cancel", color = Color(0xFF999999), fontSize = textBodyM(), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        syncing = true
                        onCreate(name.trim())
                    },
                    enabled = name.isNotBlank() && !syncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.weight(1f).height(adp(48f)).background(
                        Brush.linearGradient(listOf(Color(0xFF00C8B4), Color(0xFFB040E8))),
                        RoundedCornerShape(cornerM()),
                    ),
                ) {
                    Text(
                        if (syncing) "Syncing\u2026" else "Create & Sync",
                        color = Color.White,
                        fontSize = textBodyM(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(spacingS()))
            Text(
                "Synced via createPlaylist",
                color = Color(0xFF666666),
                fontSize = textLabelM(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFromServerSheet(onImport: (String, String) -> Unit, onDismiss: () -> Unit) {
    val viewModel: AlbumDetailViewModel = hiltViewModel()
    val serverPlaylists by viewModel.serverPlaylists.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadServerPlaylists() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C2E),
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(bottom = spacing3XL())) {
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.width(
                        adp(32f),
                    ).height(adp(4f)).clip(RoundedCornerShape(adp(2f))).background(Color(0xFF444444)),
                )
            }
            Text(
                "Add from Server",
                color = Color.White,
                fontSize = textHeadingM(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
            )
            Spacer(Modifier.height(spacingS()))
            if (serverPlaylists.isEmpty()) {
                Text(
                    "All playlists already imported",
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(horizontal = spacingL(), vertical = spacing3XL()),
                )
            } else {
                LazyColumn {
                    serverPlaylists.forEach { pl ->
                        item {
                            Row(
                                Modifier.fillMaxWidth().clickable { onImport(pl.id, pl.name) }
                                    .padding(horizontal = spacingL(), vertical = spacingM()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    null,
                                    tint = Color(0xFF00C8B4),
                                    modifier = Modifier.size(iconSmall()),
                                )
                                Spacer(Modifier.width(spacingM()))
                                Text(
                                    pl.name,
                                    color = Color.White,
                                    fontSize = textBodyM(),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
