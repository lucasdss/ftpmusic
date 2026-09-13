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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import com.lucasdss.ftpmusic.app.playback.PlaybackViewModel
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import com.lucasdss.ftpmusic.app.playback.QueueRevisionTracker
import com.lucasdss.ftpmusic.app.ui.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Full-screen queue view showing all queued tracks.
 * Currently playing item is highlighted.
 * Supports tap-to-jump, swipe-to-remove, and drag-to-reorder.
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
            QueueProjection.project(
                queuePlayer,
                currentIndex,
                { mediaId ->
                    viewModel.getTrackInfo(mediaId)?.let {
                        QueueTrackMetadata(it.title, it.artist, it.album)
                    }
                },
            ) { index -> flags.getOrElse(index) { false } }
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Queue (${playbackState.queueSize})")
                        if (playbackState.isCasting && playbackState.castDeviceName != null) {
                            Text(
                                "Casting to ${playbackState.castDeviceName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.clearPriorityQueue() },
                        enabled = playbackState.priorityQueueSize > 0,
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear")
                    }
                },
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
            val queueRows = mediaItems.filter { it.isPriority }
            val continueRows = mediaItems.filter { !it.isPriority }
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
                            color = Color(0xFFB040E8),
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
                        )
                    }
                    items(queueRows, key = { if (it.entryId > 0) it.entryId else it.index }) { item ->
                        val rowKey = if (item.entryId > 0) item.entryId else item.index
                        ReorderableItem(state = reorderableState, key = rowKey) { isDragging ->
                            QueueDismissRow(item, isDragging, playbackState.isPlaying, viewModel)
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
                            QueueDismissRow(item, isDragging, playbackState.isPlaying, viewModel)
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
        )
    }
}

@Composable
private fun QueueItemRow(
    item: QueueProjectionItem,
    isPlaying: Boolean = false,
    isDragging: Boolean = false,
    onClick: () -> Unit = {},
) {
    val bgColor = when {
        isDragging -> MaterialTheme.colorScheme.surfaceVariant
        item.isCurrent -> Color(0xFF00C8B4).copy(alpha = 0.08f)
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
        // Drag handle
        Icon(Icons.Default.DragHandle, null, tint = Color(0xFF333333), modifier = Modifier.size(adp(14f)))
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
                    Icon(Icons.Default.MusicNote, null, tint = Color(0xFF555555), modifier = Modifier.size(adp(16f)))
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
                                ).fillMaxHeight(anim).clip(RoundedCornerShape(adp(1f))).background(Color(0xFF00C8B4)),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(adp(10f)))

        // Title + Artist + Star rating
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (item.isCurrent) Color(0xFF00C8B4) else Color.White,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.artist?.let { a ->
                    Text(
                        a,
                        color = Color(0xFF888888),
                        fontSize = asp(12f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Row(modifier = Modifier.padding(start = adp(8f))) {
                    for (i in 1..5) {
                        Icon(
                            imageVector = if (i <=
                                (item.userRating ?: 0)
                            ) {
                                Icons.Default.Star
                            } else {
                                Icons.Default.StarBorder
                            },
                            contentDescription = null,
                            tint = if (i <= (item.userRating ?: 0)) Color(0xFF00C8B4) else Color(0xFF444444),
                            modifier = Modifier.size(adp(11f)),
                        )
                    }
                }
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
