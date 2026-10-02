package com.lucasdss.ftpmusic.app.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import com.lucasdss.ftpmusic.app.playback.PlaybackViewModel
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import com.lucasdss.ftpmusic.app.playback.QueueRevisionTracker
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.SurfaceElevated
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Full-screen queue view for unit/instrumentation tests and legacy callers.
 *
 * **Production UI** is the in-player bottom sheet in [PlayerBar] (`queue_sheet`).
 * Keep section semantics (Queue / Continue Playing / Autoplay) and brand tokens
 * aligned with that sheet so tests do not drift from shipping chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onBack: () -> Unit, viewModel: PlaybackViewModel = hiltViewModel()) {
    val playbackState by viewModel.state.collectAsState()
    val queueRevision by QueueRevisionTracker.revision.collectAsState()
    // During Cast, PlayerHolder.player exposes the receiver's sliding window.
    // ExoPlayer remains the full canonical queue mirror used by this screen.
    val castPlayer = PlayerHolder.player
    val queuePlayer = PlayerHolder.exoPlayer ?: castPlayer
    val listState = rememberLazyListState()
    val queueCount = queuePlayer?.mediaItemCount ?: playbackState.queueSize
    val currentIndex = queuePlayer?.currentMediaItemIndex ?: 0
    val mediaItems = remember(queueRevision, queuePlayer, queueCount, currentIndex) {
        if (queueCount > 0 && queuePlayer != null) {
            val flags = viewModel.isPriorityFlags()
            val autoplay = viewModel.isAutoplayFlags()
            QueueProjection.project(
                queuePlayer,
                currentIndex,
                { mediaId ->
                    viewModel.getTrackInfo(mediaId)?.let {
                        QueueTrackMetadata(it.title, it.artist, it.album)
                    }
                },
                { index -> flags.getOrElse(index) { false } },
                { index -> autoplay.getOrElse(index) { false } },
            )
        } else {
            emptyList()
        }
    }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromIdx = mediaItems.firstOrNull { it.entryId == from.key || it.index == from.key }?.index
                ?: return@rememberReorderableLazyListState
            val toIdx = mediaItems.firstOrNull { it.entryId == to.key || it.index == to.key }?.index
                ?: return@rememberReorderableLazyListState
            viewModel.moveQueueItem(fromIdx, toIdx)
        },
    )

    Scaffold(
        containerColor = SurfaceElevated,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Queue (${playbackState.queueSize})", color = Color.White)
                        if (playbackState.isCasting && playbackState.castDeviceName != null) {
                            Text(
                                "Casting to ${playbackState.castDeviceName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandTeal,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.clearPriorityQueue() },
                        enabled = playbackState.priorityQueueSize > 0,
                        colors = ButtonDefaults.textButtonColors(contentColor = BrandTeal),
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceElevated,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = BrandTeal,
                ),
            )
        },
    ) { padding ->
        if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        null,
                        modifier = Modifier.size(miniPlayerHeight()),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Queue is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            var continuousPlayOn by remember {
                mutableStateOf(viewModel.isContinuousPlayEnabled())
            }
            val queueRows = mediaItems.filter { it.isPriority }
            val continueRows = mediaItems.filter { !it.isPriority && !it.isAutoplay }
            val autoplayRows = mediaItems.filter { !it.isPriority && it.isAutoplay }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (queueRows.isNotEmpty()) {
                    item(key = "hdr-queue") {
                        Text(
                            "Queue · ${queueRows.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = BrandPurple,
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
                        )
                    }
                    items(queueRows, key = { if (it.entryId > 0) it.entryId else it.index }) { item ->
                        val rowKey = if (item.entryId > 0) item.entryId else item.index
                        ReorderableItem(state = reorderableState, key = rowKey) { isDragging ->
                            QueueDismissRow(
                                item = item,
                                isDragging = isDragging,
                                isPlaying = playbackState.isPlaying,
                                viewModel = viewModel,
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = { viewModel.beginQueueReorder(item.entryId, item.index) },
                                    onDragStopped = { viewModel.commitQueueReorder() },
                                ),
                            )
                        }
                    }
                }
                if (continueRows.isNotEmpty()) {
                    item(key = "hdr-continue") {
                        Text(
                            if (playbackState.contextSource != null) {
                                "Continue Playing · ${playbackState.contextSource} · ${continueRows.size}"
                            } else {
                                "Continue Playing · ${continueRows.size}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
                        )
                    }
                    items(continueRows, key = { if (it.entryId > 0) it.entryId else it.index }) { item ->
                        val rowKey = if (item.entryId > 0) item.entryId else item.index
                        ReorderableItem(state = reorderableState, key = rowKey) { isDragging ->
                            QueueDismissRow(
                                item = item,
                                isDragging = isDragging,
                                isPlaying = playbackState.isPlaying,
                                viewModel = viewModel,
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = { viewModel.beginQueueReorder(item.entryId, item.index) },
                                    onDragStopped = { viewModel.commitQueueReorder() },
                                ),
                            )
                        }
                    }
                }
                item(key = "hdr-autoplay") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacingL(), vertical = spacingS()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (autoplayRows.isNotEmpty()) {
                                "Autoplay · ${autoplayRows.size}"
                            } else {
                                "Autoplay · journal when queue ends"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = BrandTeal,
                            modifier = Modifier.weight(1f),
                        )
                        if (autoplayRows.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearAutoplayQueue() }) {
                                Text("Clear", color = BrandTeal)
                            }
                        }
                        Switch(
                            checked = continuousPlayOn,
                            onCheckedChange = {
                                continuousPlayOn = it
                                viewModel.setContinuousPlayEnabled(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandTeal,
                            ),
                        )
                    }
                }
                if (autoplayRows.isNotEmpty()) {
                    items(autoplayRows, key = { if (it.entryId > 0) it.entryId else it.index }) { item ->
                        val rowKey = if (item.entryId > 0) item.entryId else item.index
                        ReorderableItem(state = reorderableState, key = rowKey) { isDragging ->
                            QueueDismissRow(
                                item = item,
                                isDragging = isDragging,
                                isPlaying = playbackState.isPlaying,
                                viewModel = viewModel,
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = { viewModel.beginQueueReorder(item.entryId, item.index) },
                                    onDragStopped = { viewModel.commitQueueReorder() },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueDismissRow(
    item: QueueProjectionItem,
    isDragging: Boolean,
    isPlaying: Boolean,
    viewModel: PlaybackViewModel,
    dragHandleModifier: Modifier = Modifier,
) {
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    viewModel.removeFromQueue(item.index)
                    true
                } else {
                    false
                }
            },
        ),
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Red),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    "Remove",
                    tint = Color.White,
                    modifier = Modifier.padding(end = spacingXL()),
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        QueueItemRow(
            item = item,
            isPlaying = isPlaying,
            isDragging = isDragging,
            onClick = { viewModel.playQueueItem(item.index) },
            dragHandleModifier = dragHandleModifier,
        )
    }
}

@Composable
private fun QueueItemRow(
    item: QueueProjectionItem,
    isPlaying: Boolean = false,
    isDragging: Boolean = false,
    onClick: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
) {
    val bgColor = when {
        isDragging -> MaterialTheme.colorScheme.surfaceVariant
        item.isCurrent -> BrandTeal.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = adp(10f), horizontal = spacingL()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag handle — calvin onDragStopped commits one Exo moveMediaItem
        Icon(
            Icons.Default.DragHandle,
            null,
            tint = Color(0xFF333333),
            modifier = Modifier
                .size(adp(14f))
                .then(dragHandleModifier),
        )
        Spacer(Modifier.width(adp(10f)))

        // Cover art with EQ overlay
        Box(Modifier.size(adp(40f)).clip(RoundedCornerShape(adp(8f))), contentAlignment = Alignment.Center) {
            if (item.coverArtUrl != null) {
                AsyncImage(
                    model = item.coverArtUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF1E1E1E)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = NavUnselected, modifier = Modifier.size(adp(16f)))
                }
            }
            if (item.isCurrent && isPlaying) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.width(16.dp).height(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(adp(2f)),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        val eqAnim = rememberInfiniteTransition(label = "eq_qs_${item.id}")
                        listOf(0.4f, 0.7f, 1.0f).forEachIndexed { j, h ->
                            val anim by eqAnim.animateFloat(
                                initialValue = h * 0.3f,
                                targetValue = h,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(400 + j * 150, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                                label = "bar$j",
                            )
                            Box(
                                Modifier.width(
                                    adp(3f),
                                ).fillMaxHeight(anim).clip(RoundedCornerShape(adp(1f))).background(BrandTeal),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(adp(10f)))

        // Title + Artist
        Column(Modifier.weight(1f)) {
            FittingText(
                text = item.title,
                fontSize = textHeadingS(),
                minFontSize = textMicro(),
                fontWeight = FontWeight.Medium,
                color = if (item.isCurrent) BrandTeal else Color.White,
                modifier = Modifier.fillMaxWidth(),
            )
            item.artist?.let { a ->
                FittingText(
                    text = a,
                    color = Color(0xFF888888),
                    fontSize = textLabelM(),
                    minFontSize = textMicro(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Duration
        if (item.durationMs > 0) {
            val seconds = (item.durationMs / 1000) % 60
            val minutes = (item.durationMs / 1000) / 60
            Text(
                "$minutes:${seconds.toString().padStart(2, '0')}",
                color = Color(0xFF666666),
                fontSize = asp(12f),
                fontFamily = interFontFamily(),
            )
        }

        Spacer(Modifier.width(adp(8f)))

        // Remove button (tappable via surface onClick but also shown here)
        Icon(
            Icons.Default.Close,
            "Remove",
            tint = Color(0xFF444444),
            modifier = Modifier.size(adp(12f)),
        )
    }
}
