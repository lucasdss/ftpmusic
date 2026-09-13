@file:Suppress("ForEachOnRange")

package com.lucasdss.ftpmusic.app.ui.playlist

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Sync
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.ui.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit = {},
    onTrackClick: (Track) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(playlistId) { viewModel.loadPlaylist(playlistId) }
    val context = LocalContext.current

    var menuTrackIndex by remember { mutableIntStateOf(-1) }
    var showPlaylistMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistPicker by remember { mutableStateOf(false) }
    var showAddSongsSheet by remember { mutableStateOf(false) }
    val availablePlaylists by viewModel.availablePlaylists.collectAsStateWithLifecycle()

    val bgBrush = Brush.verticalGradient(
        listOf(Color(0xFF0D0D0D), Color(0xFF1A1A2E)),
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { showPlaylistMenu = true },
                        ),
                    ) {
                        Text(
                            state.playlist?.name ?: "Playlist",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = textHeadingM(),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (state.playlist != null) {
                            Text(
                                "${state.playlist?.trackCount ?: state.tracks.size} tracks",
                                fontSize = textLabelM(),
                                color = Color(0xFF888888),
                            )
                        }
                        // Playlist action menu
                        DropdownMenu(expanded = showPlaylistMenu, onDismissRequest = { showPlaylistMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    showPlaylistMenu = false
                                    showRenameDialog =
                                        true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                onClick = {
                                    showPlaylistMenu = false
                                    viewModel.copyPlaylist()
                                },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Sync from server") },
                                onClick = {
                                    showPlaylistMenu = false
                                    viewModel.syncFromServer()
                                },
                                leadingIcon = { Icon(Icons.Default.Sync, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showPlaylistMenu = false
                                    showDeleteConfirm =
                                        true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFE84040)) },
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize().background(bgBrush))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))

            if (state.isLoading && state.playlist == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00C8B4))
                }
            } else if (state.error != null && state.playlist == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: ${state.error}", color = Color(0xFF888888))
                        Spacer(Modifier.height(spacingS()))
                        TextButton(onClick = { viewModel.loadPlaylist(playlistId) }) {
                            Text("Retry", color = Color(0xFF00C8B4))
                        }
                    }
                }
            } else {
                // Conflict banner
                if (state.isConflicted) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        color = Color(0x44FF8C00),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("⚠", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Sync Conflict",
                                    color = Color(0xFFFF8C00),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                                state.conflictMessage?.let {
                                    Text(it, color = Color(0x88FFFFFF), fontSize = 11.sp)
                                }
                            }
                            TextButton(onClick = { viewModel.syncToServer() }) {
                                Text("Retry Sync", color = Color(0xFF00C8B4), fontSize = 12.sp)
                            }
                        }
                    }
                }
                LazyColumn {
                    // Cover art header
                    if (state.playlist?.coverArt != null) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().height(coverArtSize()),
                                contentAlignment = Alignment.Center,
                            ) {
                                val coverUrl = remember(state.playlist?.coverArt) {
                                    state.playlist?.coverArt?.let { viewModel.buildCoverArtUrl(it) }
                                }
                                if (coverUrl != null) {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(adp(160f)).clip(RoundedCornerShape(cornerS())),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            Spacer(Modifier.height(spacingS()))
                        }
                    }

                    // Action buttons
                    if (state.tracks.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.padding(horizontal = spacingL()).padding(top = spacingS()),
                                horizontalArrangement = Arrangement.spacedBy(spacingS()),
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
                                        Spacer(Modifier.width(spacingS()))
                                        Text(
                                            "Play",
                                            color = Color.White,
                                            fontSize = textBodyM(),
                                            fontWeight = FontWeight.Bold,
                                        )
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
                                        Spacer(Modifier.width(spacingS()))
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
                                    Modifier.size(adp(40f)).clip(RoundedCornerShape(cornerM()))
                                        .background(Color(0xFF252538)).clickable { showPlaylistSheet = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        null,
                                        tint = Color(0xFFAAAAAA),
                                        modifier = Modifier.size(knobSize()),
                                    )
                                }
                            }
                            Spacer(Modifier.height(spacingS()))
                            // "+ Add Songs" — YouTube Music-style entry into the picker
                            Box(
                                Modifier.fillMaxWidth().padding(horizontal = spacingL())
                                    .height(adp(42f)).clip(RoundedCornerShape(cornerM()))
                                    .background(Color(0xFF252538))
                                    .border(
                                        1.dp,
                                        Brush.horizontalGradient(listOf(Color(0xFF00C8B4), Color(0xFFB040E8))),
                                        RoundedCornerShape(cornerM()),
                                    )
                                    .clickable { showAddSongsSheet = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistAdd,
                                        null,
                                        tint = Color(0xFF00C8B4),
                                        modifier = Modifier.size(knobSize()),
                                    )
                                    Spacer(Modifier.width(spacingS()))
                                    Text(
                                        "Add Songs",
                                        color = Color(0xFFCCCCCC),
                                        fontSize = textBodyM(),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            Spacer(Modifier.height(spacingS()))
                        }
                    }

                    // Divider
                    item { HorizontalDivider() }

                    // Track list
                    if (state.tracks.isEmpty() && !state.isLoading) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(spacing3XL()),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic,
                                        null,
                                        tint = Color(0xFF333333),
                                        modifier = Modifier.size(iconLarge()),
                                    )
                                    Spacer(Modifier.height(spacingS()))
                                    Text(
                                        "No tracks in this playlist",
                                        color = Color(0xFF888888),
                                        fontSize = textBodyM(),
                                    )
                                    Spacer(Modifier.height(spacingL()))
                                    Box(
                                        Modifier.height(adp(42f)).clip(RoundedCornerShape(cornerM()))
                                            .background(
                                                Brush.linearGradient(listOf(Color(0xFF00C8B4), Color(0xFFB040E8))),
                                            )
                                            .clickable { showAddSongsSheet = true }
                                            .padding(horizontal = spacingXL()),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(knobSize()),
                                            )
                                            Spacer(Modifier.width(spacingS()))
                                            Text(
                                                "Add Songs",
                                                color = Color.White,
                                                fontSize = textBodyM(),
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, track ->
                        val isCached = viewModel.isCached(track.id)
                        val isDownloaded = viewModel.isDownloaded(track.id)
                        val isQueued = viewModel.isQueued(track.id)
                        val showCheck = isDownloaded || isQueued || isCached
                        val isActive = currentTrackId != null && track.id == currentTrackId && isPlaying

                        Box(Modifier.background(Color.Black.copy(alpha = 0.15f))) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = {
                                    Text(
                                        "${index + 1}. ${track.title}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isActive) Color(0xFF00C8B4) else Color.White,
                                    )
                                },
                                leadingContent = if (isActive) {
                                    { AnimatedEqBars() }
                                } else {
                                    null
                                },
                                supportingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        track.artist?.let {
                                            Text(
                                                it,
                                                color = Color(0xFF888888),
                                                fontSize = textLabelL(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                        }
                                        track.duration?.let { d ->
                                            Text(
                                                " · ${d / 60}:${(d % 60).toString().padStart(2, '0')}",
                                                color = Color(0xFF666666),
                                                fontSize = textLabelM(),
                                            )
                                        }
                                        if (track.userRating != null && track.userRating!! > 0) {
                                            Spacer(Modifier.width(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                (1..5).forEach { i ->
                                                    Icon(
                                                        imageVector = if (i <=
                                                            track.userRating!!
                                                        ) {
                                                            Icons.Filled.Star
                                                        } else {
                                                            Icons.Default.StarBorder
                                                        },
                                                        contentDescription = null,
                                                        tint = if (i <=
                                                            track.userRating!!
                                                        ) {
                                                            Color(0xFF00C8B4)
                                                        } else {
                                                            Color(0xFF444444)
                                                        },
                                                        modifier = Modifier.size(11.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                trailingContent = {
                                    if (showCheck) {
                                        Icon(
                                            imageVector = when {
                                                isDownloaded -> Icons.Default.CheckCircle
                                                isQueued -> Icons.Default.HourglassEmpty
                                                else -> Icons.Default.Check
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                isDownloaded -> Color(0xFFB040E8)
                                                else -> Color(0xFF888888)
                                            },
                                            modifier = Modifier.size(iconSmall()),
                                        )
                                    }
                                },
                                modifier = Modifier.combinedClickable(
                                    onClick = { viewModel.playTrack(index) },
                                    onLongClick = { menuTrackIndex = index },
                                ),
                            )
                        }

                        // Long-press menu
                        Box {
                            DropdownMenu(
                                expanded = menuTrackIndex == index,
                                onDismissRequest = { menuTrackIndex = -1 },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Play Next") },
                                    onClick = {
                                        viewModel.playNext(index)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Playing next",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                        menuTrackIndex = -1
                                    },
                                    leadingIcon = { Icon(Icons.Default.SkipNext, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to Queue") },
                                    onClick = {
                                        val url = viewModel.buildStreamUrl(track.id)
                                        viewModel.addToQueueTrack(track, url)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Added to queue",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                        menuTrackIndex = -1
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Download") },
                                    onClick = {
                                        viewModel.downloadTrack(track)
                                        menuTrackIndex = -1
                                    },
                                    leadingIcon = { Icon(Icons.Default.Download, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Move Up") },
                                    onClick = {
                                        viewModel.moveTrack(index, index - 1)
                                        menuTrackIndex = -1
                                    },
                                    enabled = index > 0,
                                    leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Move Down") },
                                    onClick = {
                                        viewModel.moveTrack(index, index + 1)
                                        menuTrackIndex = -1
                                    },
                                    enabled = index < state.tracks.lastIndex,
                                    leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) },
                                )
                                HorizontalDivider()
                                if (!track.albumId.isNullOrEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Go to Album") },
                                        onClick = {
                                            onNavigateToAlbum(track.albumId!!)
                                            menuTrackIndex = -1
                                        },
                                        leadingIcon = { Icon(Icons.Default.Album, null) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist") },
                                    onClick = {
                                        viewModel.loadAvailablePlaylists()
                                        showAddToPlaylistPicker = true
                                        menuTrackIndex = -1
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove from Playlist") },
                                    onClick = {
                                        viewModel.removeFromPlaylist(track.id)
                                        menuTrackIndex = -1
                                    },
                                    leadingIcon = { Icon(Icons.Default.Remove, null, tint = Color(0xFFE84040)) },
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(adp(80f))) }
                }
            }
        }
    }

    // ── Overwrite Protection (Ask mode) ──
    val showOverwrite by viewModel.showOverwriteModal.collectAsStateWithLifecycle()
    if (showOverwrite) {
        android.app.AlertDialog.Builder(context).apply {
            setTitle("Tracks in your queue")
            setMessage(
                "You have tracks in your Priority Queue. Do you want to clear them and play this playlist, or keep them?",
            )
            setNegativeButton("Keep Queue") { _, _ -> viewModel.resolveOverwrite(false) }
            setPositiveButton("Clear & Play") { _, _ -> viewModel.resolveOverwrite(true) }
            setOnCancelListener { viewModel.resolveOverwrite(false) }
            show()
        }
    }

    // ── Rename dialog ──
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(state.playlist?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("Playlist name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renamePlaylist(newName)
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
        )
    }

    // ── Delete confirmation ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Playlist") },
            text = { Text("Delete \"${state.playlist?.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist { onBack() }
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = Color(0xFFE84040))
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    // ── Playlist action sheet (⋮ menu) ──
    if (showPlaylistSheet) {
        PlaylistActionSheet(
            onAddSongs = {
                showPlaylistSheet = false
                showAddSongsSheet = true
            },
            onPlayNextAll = {
                viewModel.playNextAll()
                android.widget.Toast.makeText(context, "Playing next", android.widget.Toast.LENGTH_SHORT).show()
                showPlaylistSheet =
                    false
            },
            onAddToQueue = {
                viewModel.addToQueue()
                android.widget.Toast.makeText(context, "Added to queue", android.widget.Toast.LENGTH_SHORT).show()
                showPlaylistSheet =
                    false
            },
            onAddAllToPlaylist = {
                viewModel.loadAvailablePlaylists()
                showAddToPlaylistPicker = true
                showPlaylistSheet =
                    false
            },
            onDownloadAll = {
                viewModel.downloadAll()
                showPlaylistSheet = false
            },
            onSyncFromServer = {
                viewModel.syncFromServer()
                showPlaylistSheet = false
            },
            onSyncToServer = {
                viewModel.syncToServer()
                showPlaylistSheet = false
            },
            onRename = {
                showPlaylistSheet = false
                showRenameDialog = true
            },
            onCopy = {
                viewModel.copyPlaylist()
                showPlaylistSheet = false
            },
            onDelete = {
                showPlaylistSheet = false
                showDeleteConfirm = true
            },
            onRemoveLocal = {
                viewModel.removeLocally()
                showPlaylistSheet = false
                onBack()
            },
            onDismiss = { showPlaylistSheet = false },
        )
    }

    // ── Add to Playlist picker ──
    if (showAddToPlaylistPicker) {
        var selectedTrackIds by remember { mutableStateOf(setOf<String>()) }
        LaunchedEffect(showAddToPlaylistPicker) {
            selectedTrackIds = if (menuTrackIndex >= 0) {
                setOf(state.tracks.getOrElse(menuTrackIndex) { state.tracks.firstOrNull() }?.id ?: "")
            } else {
                emptySet()
            }
        }
        AlertDialog(
            onDismissRequest = { showAddToPlaylistPicker = false },
            title = { Text("Add to Playlist") },
            text = {
                if (availablePlaylists.isEmpty()) {
                    Text("No other playlists available", color = Color(0xFF888888))
                } else {
                    LazyColumn {
                        itemsIndexed(availablePlaylists) { _, pl ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        pl.name,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = { Text("${pl.trackCount} tracks", color = Color(0xFF888888)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    viewModel.addToPlaylist(pl.id, selectedTrackIds.toList())
                                    showAddToPlaylistPicker = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToPlaylistPicker = false }) { Text("Cancel") }
            },
        )
    }

    // ── Add Songs picker (add tracks INTO this playlist) ──
    if (showAddSongsSheet) {
        AddSongsSheet(
            existingTrackIds = state.tracks.map { it.id }.toSet(),
            search = viewModel::searchPickerTracks,
            suggestions = viewModel::pickerSuggestions,
            onAdd = { trackIds ->
                viewModel.addTracksToThisPlaylist(trackIds)
                showAddSongsSheet = false
            },
            onDismiss = { showAddSongsSheet = false },
        )
    }
}

@Composable
private fun RowScope.ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(iconSmall()))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistActionSheet(
    onAddSongs: () -> Unit,
    onPlayNextAll: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddAllToPlaylist: () -> Unit,
    onDownloadAll: () -> Unit,
    onSyncFromServer: () -> Unit,
    onSyncToServer: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRemoveLocal: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            Spacer(Modifier.height(spacingL()))
            PlaylistSheetAction(
                "Add Songs",
                "Add tracks to this playlist",
                Icons.Default.Add,
                Color(0xFF00C8B4),
                onAddSongs,
            )
            PlaylistSheetAction(
                "Play Next",
                "Insert all tracks after current",
                Icons.Default.ArrowUpward,
                Color(0xFF00C8B4),
                onPlayNextAll,
            )
            PlaylistSheetAction(
                "Add to Queue",
                "Add all tracks to play queue",
                Icons.AutoMirrored.Filled.QueueMusic,
                Color(0xFFB040E8),
                onAddToQueue,
            )
            PlaylistSheetAction(
                "Add to Playlist",
                "Add all tracks to another playlist",
                Icons.AutoMirrored.Filled.PlaylistAdd,
                Color(0xFF5B8DEE),
                onAddAllToPlaylist,
            )
            PlaylistSheetAction(
                "Download All",
                "Save all tracks for offline listening",
                Icons.Default.Download,
                Color(0xFF00C8B4),
                onDownloadAll,
            )
            PlaylistSheetAction(
                "Sync from Server",
                "Download tracks for offline",
                Icons.Outlined.Sync,
                Color(0xFF00C8B4),
                onSyncFromServer,
            )
            PlaylistSheetAction(
                "Sync to Server",
                "Push local tracks to server",
                Icons.Default.CloudUpload,
                Color(0xFF5B8DEE),
                onSyncToServer,
            )
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(horizontal = spacingL()),
            )
            PlaylistSheetAction("Rename", "Change playlist name", Icons.Default.Edit, Color(0xFFCCCCCC), onRename)
            PlaylistSheetAction("Copy", "Duplicate this playlist", Icons.Default.ContentCopy, Color(0xFFCCCCCC), onCopy)
            PlaylistSheetAction(
                "Remove Locally",
                "Delete from library, keep on server",
                Icons.Default.DeleteSweep,
                Color(0xFFE84040),
                onRemoveLocal,
            )
            PlaylistSheetAction(
                "Delete Playlist",
                "Remove permanently from server",
                Icons.Default.Delete,
                Color(0xFFE84040),
                onDelete,
            )
        }
    }
}

@Composable
private fun PlaylistSheetAction(label: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = spacingL(), vertical = adp(14f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(adp(40f)).clip(CircleShape).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(adp(18f)))
        }
        Spacer(Modifier.width(spacingM()))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = textBodyM(), fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF888888), fontSize = textLabelM())
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = spacingL()))
}

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
