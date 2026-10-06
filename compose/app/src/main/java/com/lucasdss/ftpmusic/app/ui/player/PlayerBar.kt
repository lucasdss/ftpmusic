package com.lucasdss.ftpmusic.app.ui.player

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.SurfaceElevated
import com.lucasdss.ftpmusic.app.ui.components.DownloadDot
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import com.lucasdss.ftpmusic.app.ui.components.InteractiveStarRating
import com.lucasdss.ftpmusic.app.ui.player.CastButton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Unified player UI — adapts between mini (bottom bar) and full (now-playing screen).
 *
 * Mini mode: cover art, title/artist, thin progress (display only — no seek),
 * prev/play/pause/next. Full mode: gradient album art background, large cover,
 * seek/waveform, transport, cast volume, in-player queue sheet + lyrics overlay.
 *
 * Cast picker lives in [CastButton] (header) + [CastDevicePickerDialog] (NavHost).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerBar(
    state: PlayerBarState,
    // High-frequency progress (hoisted out of PlayerBarState: position ticks
    // every 200ms — keeping them in the state object would break parameter
    // equality and recompose the whole player tree 5x/sec. Only the seek-bar
    // region reads these two.)
    position: Long = 0L,
    duration: Long = 0L,
    // Callbacks
    onPlayPause: () -> Unit = {},
    onSkipPrev: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onRepeatToggle: () -> Unit = {},
    onShuffleToggle: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onToggleDislike: () -> Unit = {},
    onRate: (Int) -> Unit = {},
    onArtistClick: () -> Unit = {},
    onAlbumClick: () -> Unit = {},
    onBack: () -> Unit = {},
    onClick: () -> Unit = {},
    onClearQueue: () -> Unit = {},
    onClearAutoplayQueue: () -> Unit = {},
    onContinuousPlayChange: (Boolean) -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onPlayQueueItem: (Int) -> Unit = {},
    onSleepTimer: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onSongInfo: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    onCast: () -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onBeginQueueReorder: (entryId: Int, fromIndex: Int) -> Unit = { _, _ -> },
    onCommitQueueReorder: () -> Unit = {},
    onShareQueue: () -> Unit = {},
    onSaveQueueAsPlaylist: (String) -> Unit = {},
    onPlayHistoryTrack: (String) -> Unit = {},
    onRemoveFromQueueBatch: (Set<Int>) -> Unit = {},
    onQueueSheetOpened: () -> Unit = {},
    defaultQueuePlaylistName: String = "Queue",
    modifier: Modifier = Modifier,
) {
    with(state) {
        if (!expanded) {
            PlayerMiniBar(
                state = state,
                position = position,
                duration = duration,
                onPlayPause = onPlayPause,
                onSkipPrev = onSkipPrev,
                onSkipNext = onSkipNext,
                onClick = onClick,
                modifier = modifier,
            )
        } else {
            // ═══ FULL SCREEN (NOW PLAYING) — design v4: no tabs, overlays + swipe ════════
            // Hoisted UI state: the sheet/lyrics overlays and the queue drag-follow
            // animation live here so sub-composables stay stateless and skippable.
            var showQueue by remember(state.title, state.artist) { mutableStateOf(false) }
            var showLyrics by remember(state.title, state.artist) { mutableStateOf(false) }
            // Queue sheet drag-follow: 0f = closed, 1f = fully open. The sheet
            // translates with the finger during the swipe and springs to an anchor
            // on release — the boolean above only gates composition + dismiss.
            val queueOffset = remember(state.title, state.artist) { Animatable(0f) }
            val queueScope = rememberCoroutineScope()
            // Density/config reads hoisted out of the remembers (composable reads
            // are illegal inside remember's calculation lambda).
            val density = LocalDensity.current
            val screenHeightDp = LocalConfiguration.current.screenHeightDp
            val queueSheetHeightPx = remember(density, screenHeightDp) {
                with(density) { screenHeightDp.dp.toPx() }
            }
            Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D14))) {
                // ── Swipe-down-to-minimize (drag-to-dismiss) ──
                var dismissOffset by remember { mutableFloatStateOf(0f) }
                val dismissAnim = remember { Animatable(0f) }
                val dismissScope = rememberCoroutineScope()
                val dismissThresholdPx = with(density) { adp(48f).toPx() }
                val maxDismissPx = with(density) { adp(200f).toPx() }

                // Everything (background + content + overlays) — dragging down
                // translates the screen; release past the threshold minimizes.
                // Disabled while the queue sheet / lyrics overlay is open.
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            translationY = if (dismissAnim.isRunning) dismissAnim.value else dismissOffset
                        }
                        .pointerInput(showQueue, showLyrics) {
                            if (!showQueue && !showLyrics) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (dismissOffset > dismissThresholdPx) onBack()
                                        dismissScope.launch {
                                            dismissAnim.snapTo(dismissOffset)
                                            dismissOffset = 0f
                                            dismissAnim.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                                            )
                                        }
                                    },
                                    onDragCancel = {
                                        dismissScope.launch {
                                            dismissAnim.snapTo(dismissOffset)
                                            dismissOffset = 0f
                                            dismissAnim.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                                            )
                                        }
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        // dragAmount > 0 = finger moving down
                                        if (dragAmount > 0) {
                                            dismissOffset = (dismissOffset + dragAmount).coerceIn(0f, maxDismissPx)
                                        }
                                    },
                                )
                            }
                        },
                ) {
                    if (coverArtUrl != null) {
                        AsyncImage(
                            model = coverArtUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(1.2f)
                                .blur(radius = adp(40f)),
                            contentScale = ContentScale.Crop,
                            alpha = 0.15f,
                        )
                    }
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0x990D0D14),
                                    0.55f to Color(0xEB0D0D14),
                                    1.0f to Color(0xFF0D0D14),
                                ),
                            ),
                        ),
                    )
                    Column(Modifier.fillMaxSize()) {
                        PlayerFullHeader(
                            state = state,
                            onBack = onBack,
                            onArtistClick = onArtistClick,
                            onAlbumClick = onAlbumClick,
                            onSleepTimer = onSleepTimer,
                        )
                        Spacer(Modifier.height(spacingXS()))

                        // ── Main body — fixed layout, no scrolling (design v4) ──
                        PlayerMainBody(
                            state = state,
                            position = position,
                            duration = duration,
                            onPlayPause = onPlayPause,
                            onSkipPrev = onSkipPrev,
                            onSkipNext = onSkipNext,
                            onSeek = onSeek,
                            onVolumeChange = onVolumeChange,
                            onRepeatToggle = onRepeatToggle,
                            onShuffleToggle = onShuffleToggle,
                            onToggleLike = onToggleLike,
                            onToggleDislike = onToggleDislike,
                            onRate = onRate,
                            onShowLyrics = { showLyrics = true },
                        )
                    }

                    PlayerQueuePanel(
                        state = state,
                        duration = duration,
                        showQueue = showQueue,
                        queueOffset = queueOffset,
                        queueScope = queueScope,
                        queueSheetHeightPx = queueSheetHeightPx,
                        onShowQueueChange = { open ->
                            showQueue = open
                            if (open) onQueueSheetOpened()
                        },
                        onPlayQueueItem = onPlayQueueItem,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onClearQueue = onClearQueue,
                        onClearAutoplayQueue = onClearAutoplayQueue,
                        onContinuousPlayChange = onContinuousPlayChange,
                        onShuffleToggle = onShuffleToggle,
                        onMoveQueueItem = onMoveQueueItem,
                        onBeginQueueReorder = onBeginQueueReorder,
                        onCommitQueueReorder = onCommitQueueReorder,
                        onShareQueue = onShareQueue,
                        onSaveQueueAsPlaylist = onSaveQueueAsPlaylist,
                        onPlayHistoryTrack = onPlayHistoryTrack,
                        onRemoveFromQueueBatch = onRemoveFromQueueBatch,
                        defaultQueuePlaylistName = defaultQueuePlaylistName,
                    )

                    // ── Lyrics overlay — slides up over the player ──
                    PlayerLyricsOverlay(
                        state = state,
                        positionMs = position,
                        durationMs = duration,
                        visible = showLyrics,
                        onDismiss = { showLyrics = false },
                        onSeek = onSeek,
                    )
                } // closes dismiss wrapper Box
            }
        }
    } // closes with(state)
}

// ═══ Extracted PlayerBar regions ═══════════════════════════════════════
// The PlayerBar monolith exceeded ART's 64K method-compiler instruction limit
// (33,410 instructions logged every second, flooding logcat). Each region
// below owns a narrow slice of the UI so a 200ms position tick only
// recomposes the regions that read position/duration.

/** Mini (collapsed bottom-bar) mode. */
@Composable
private fun PlayerMiniBar(
    state: PlayerBarState,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    with(state) {
        val miniBgBrush = remember(colors.darkMuted, colors.vibrant) {
            if (colors.darkMuted != null && colors.vibrant != null) {
                Brush.verticalGradient(listOf(colors.darkMuted!!, colors.vibrant!!))
            } else {
                Brush.linearGradient(
                    listOf(Color(0xFF1A1A2E), Color(0xFF1E1A30)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            }
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = spacingS())
                .height(miniPlayerHeight())
                .clip(RoundedCornerShape(cornerL()))
                .background(miniBgBrush)
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(cornerL()))
                .clickable { onClick() },
        ) {
            // Progress bar at TOP of mini player (Item 6)
            if (duration > 0) {
                val rawMiniProgress = (position.toFloat() / duration).coerceIn(0f, 1f)
                val miniProgress by animateFloatAsState(
                    rawMiniProgress,
                    tween(200, easing = LinearEasing),
                    label = "miniFrac",
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                ) {
                    if (miniProgress > 0f) {
                        Box(
                            Modifier.weight(miniProgress).fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(BrandTeal, BrandPurple))),
                        )
                    }
                    if (miniProgress < 1f) {
                        Box(
                            Modifier.weight((1f - miniProgress).coerceAtLeast(0.001f)).fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.08f)),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = spacingS()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Skip Back button (left) — per updated design spec
                Box(
                    Modifier.size(adp(40f)).background(Color.White.copy(alpha = 0.07f), CircleShape).clickable {
                        onSkipPrev()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        stringResource(R.string.player_previous),
                        modifier = Modifier.size(iconSmall()),
                    )
                }
                Spacer(Modifier.width(spacingXS()))
                // Cover art (small) with cast indicator dot
                Box(Modifier.size(adp(44f))) {
                    if (coverArtUrl != null) {
                        AsyncImage(
                            model = coverArtUrl,
                            contentDescription = "Cover",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(cornerS())),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            "No cover",
                            modifier = Modifier.fillMaxSize().padding(spacingS()),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Cast indicator dot — bottom-right when casting
                    if (isCasting) {
                        Box(
                            modifier = Modifier
                                .size(adp(6f))
                                .clip(CircleShape)
                                .background(BrandTeal)
                                .align(Alignment.BottomEnd),
                        )
                    }
                }
                Spacer(Modifier.width(spacingS()))
                // Track info
                Column(modifier = Modifier.weight(1f)) {
                    FittingText(
                        text = title ?: stringResource(R.string.player_no_track),
                        fontSize = textHeadingS(),
                        minFontSize = textMicro(),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val artistLine = artist ?: ""
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCasting && castDeviceName != null) {
                            // Casting — show EQ bars + device name per design spec
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                listOf(3, 5, 7).forEach { h ->
                                    Box(
                                        Modifier.width(2.dp).height(adp(h.toFloat() * 1.5f))
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(if (!isQueueSynced) Color(0xFFF0A040) else BrandTeal),
                                    )
                                }
                            }
                            Spacer(Modifier.width(spacingXS()))
                            FittingText(
                                text = castDeviceName!!,
                                color = if (!isQueueSynced) Color(0xFFF0A040) else BrandTeal,
                                fontSize = textMicro(),
                                minFontSize = textMicro(),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f, fill = false),
                                fillMaxWidth = false,
                            )
                        } else {
                            FittingText(
                                text = artistLine.ifEmpty { "" },
                                fontSize = textLabelM(),
                                minFontSize = textMicro(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f, fill = false),
                                fillMaxWidth = false,
                            )
                        }
                        if (priorityQueueSize > 0) {
                            Spacer(Modifier.width(spacingXS()))
                            Surface(color = BrandPurple.copy(alpha = 0.2f), shape = RoundedCornerShape(cornerS())) {
                                Text(
                                    "+$priorityQueueSize",
                                    modifier = Modifier.padding(horizontal = spacingXS(), vertical = 1.dp),
                                    color = BrandPurple,
                                    fontSize = textMicro(),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(spacingS()))
                // Controls — Play/Pause (+ buffering) + Skip Next
                Box(
                    Modifier.size(adp(48f)).background(Color.White.copy(alpha = 0.08f), CircleShape).clickable {
                        onPlayPause()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(iconSmall()),
                            color = BrandTeal,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) {
                                stringResource(R.string.player_pause)
                            } else {
                                stringResource(R.string.player_play)
                            },
                            modifier = Modifier.size(iconSmall()),
                        )
                    }
                }
                Spacer(Modifier.width(spacingXS()))
                Box(
                    Modifier.size(adp(48f)).background(Color.White.copy(alpha = 0.08f), CircleShape).clickable {
                        onSkipNext()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        stringResource(R.string.player_next),
                        modifier = Modifier.size(iconSmall()),
                    )
                }
            }
        }
    }
}

/** Full-mode header: cast banner + back/title/artist/album top bar + sleep timer. */
@Composable
private fun PlayerFullHeader(
    state: PlayerBarState,
    onBack: () -> Unit,
    onArtistClick: () -> Unit,
    onAlbumClick: () -> Unit,
    onSleepTimer: () -> Unit = {},
) {
    with(state) {
        if (isCasting) {
            Row(
                Modifier.fillMaxWidth()
                    .background(BrandTeal.copy(alpha = 0.12f))
                    .padding(horizontal = spacingXL(), vertical = spacingS()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Cast, null, tint = BrandTeal, modifier = Modifier.size(adp(12f)))
                Spacer(Modifier.width(spacingS()))
                FittingText(
                    text = if (castDeviceName != null) {
                        stringResource(R.string.player_casting_to, castDeviceName!!)
                    } else {
                        stringResource(R.string.player_casting)
                    },
                    color = BrandTeal,
                    fontSize = textLabelS(),
                    minFontSize = textMicro(),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!isQueueSynced) {
                    val rotation = rememberInfiniteTransition(label = "castSync").animateFloat(
                        0f,
                        360f,
                        infiniteRepeatable(tween(1000, easing = LinearEasing)),
                        "syncSpin",
                    )
                    Icon(
                        Icons.Default.Refresh,
                        null,
                        tint = Color(0xFFF0A040),
                        modifier = Modifier.size(adp(10f)).rotate(rotation.value),
                    )
                }
            }
        }
        // Sleep timer countdown strip (teal, mirrors the cast banner).
        if (sleepTimerEndMs > 0L) {
            val remainingSec by produceSleepCountdown(sleepTimerEndMs)
            if (remainingSec > 0L) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(BrandTeal.copy(alpha = 0.10f))
                        .padding(horizontal = spacingXL(), vertical = spacingS())
                        .clickable { onSleepTimer() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Timer, null, tint = BrandTeal, modifier = Modifier.size(adp(12f)))
                    Spacer(Modifier.width(spacingS()))
                    FittingText(
                        text = stringResource(
                            R.string.player_sleep_timer_remaining,
                            formatPlayerBarTime(remainingSec * 1000),
                        ),
                        color = BrandTeal,
                        fontSize = textLabelS(),
                        minFontSize = textMicro(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        // Top bar with back, title, sleep timer, cast
        Row(
            Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingM()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(adp(48f))
                    .clip(RoundedCornerShape(cornerL()))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    stringResource(R.string.player_back),
                    tint = Color.White,
                    modifier = Modifier.size(adp(22f)),
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.player_now_playing),
                    color = Color.White,
                    fontSize = textBodyM(),
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(adp(6f))) {
                    if (artist != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = BrandTeal.copy(alpha = 0.15f),
                            modifier = Modifier.padding(top = spacingXS()).clickable { onArtistClick() },
                        ) {
                            Text(
                                artist ?: "",
                                color = BrandTeal,
                                fontSize = textMicro(),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(
                                    horizontal = spacingS(),
                                    vertical = adp(2f),
                                ).widthIn(max = adp(110f)),
                            )
                        }
                    }
                    if (album != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.padding(top = spacingXS()).clickable { onAlbumClick() },
                        ) {
                            Text(
                                album ?: "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = textMicro(),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(
                                    horizontal = spacingS(),
                                    vertical = adp(2f),
                                ).widthIn(max = adp(110f)),
                            )
                        }
                    }
                }
            }
            Box(
                Modifier.size(adp(48f))
                    .clip(CircleShape)
                    .clickable { onSleepTimer() }
                    .testTag("sleep_timer_entry"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Timer,
                    stringResource(R.string.player_sleep_timer),
                    tint = if (sleepTimerEndMs > 0L) BrandTeal else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(iconSmall()),
                )
            }
            CastButton()
        }
    }
}

/** 1 Hz countdown of seconds remaining until [endMs] (0 once expired). Local
 *  state so the 1 s tick recomposes only this chip, not the player tree. */
@Composable
private fun produceSleepCountdown(endMs: Long): State<Long> {
    val remaining = remember { mutableLongStateOf(((endMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)) }
    LaunchedEffect(endMs) {
        while (isActive) {
            remaining.longValue = ((endMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    return remaining
}

/** Full-mode main body: album art, track info, seek bar, transport, cast volume.
 *  ColumnScope extension — its root Column uses `weight(1f)` inside the parent
 *  full-mode Column. */
@Composable
private fun ColumnScope.PlayerMainBody(
    state: PlayerBarState,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onRepeatToggle: () -> Unit,
    onShuffleToggle: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onRate: (Int) -> Unit,
    onShowLyrics: () -> Unit,
) {
    with(state) {
        Column(Modifier.weight(1f).padding(bottom = spacing3XL())) {
            // Swipeable album art — left = next, right = prev.
            // Flexible area: art shrinks to fit the screen (never scrolls).
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val artSizeDp = minOf(maxWidth.value, maxHeight.value)
                    .coerceIn(0f, 224f).dp
                // Neighboring track art for the swipe preview (nextTracks
                // starts at index 0, so prev/current/next are all present).
                val currentIdx = nextTracks.indexOfFirst { it.isCurrent }
                val nextArt = nextTracks.getOrNull(currentIdx + 1)?.coverArtUrl
                val prevArt = nextTracks.getOrNull(currentIdx - 1)?.coverArtUrl
                SwipeableAlbumArt(
                    coverArtUrl = coverArtUrl,
                    nextCoverArtUrl = nextArt,
                    prevCoverArtUrl = prevArt,
                    isCasting = isCasting,
                    canSwipe = queueSize > 1,
                    onNext = onSkipNext,
                    onPrev = onSkipPrev,
                    artSize = artSizeDp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacingL()),
                )
            }

            // Track info row — title / artist+actions / rating
            Row(Modifier.fillMaxWidth().padding(horizontal = spacingL()), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    FittingText(
                        text = title ?: "",
                        color = Color.White,
                        fontSize = textHeadingL(),
                        minFontSize = textMicro(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.padding(top = spacingXS()), verticalAlignment = Alignment.CenterVertically) {
                        FittingText(
                            text = artist ?: "",
                            color = NavUnselected,
                            fontSize = textBodyM(),
                            minFontSize = textMicro(),
                            modifier = Modifier.weight(1f),
                            fillMaxWidth = false,
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = BrandTeal.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.25f)),
                            modifier = Modifier.padding(start = spacingXS()).clickable { onShowLyrics() },
                        ) {
                            Row(
                                Modifier.padding(horizontal = spacingS(), vertical = adp(2f)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Mic,
                                    null,
                                    tint = BrandTeal,
                                    modifier = Modifier.size(adp(10f)),
                                )
                                Spacer(Modifier.width(adp(3f)))
                                Text(
                                    stringResource(R.string.player_lyrics_chip),
                                    color = BrandTeal,
                                    fontSize = textMicro(),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        ReactionCircle(
                            active = isStarred,
                            activeColor = BrandTeal,
                            icon = { tint ->
                                Icon(
                                    Icons.Filled.ThumbUp,
                                    if (isStarred) "Unlike" else "Like",
                                    tint = tint,
                                    modifier = Modifier.size(reactionGlyphSize()),
                                )
                            },
                            onClick = onToggleLike,
                            modifier = Modifier.padding(start = spacingXS()),
                        )
                        ReactionCircle(
                            active = isDisliked,
                            activeColor = Color(0xFFE84040),
                            icon = { tint ->
                                Icon(
                                    Icons.Filled.ThumbDown,
                                    if (isDisliked) "Remove dislike" else "Dislike",
                                    tint = tint,
                                    modifier = Modifier.size(reactionGlyphSize()),
                                )
                            },
                            onClick = onToggleDislike,
                            modifier = Modifier.padding(start = spacingXS()),
                        )
                    }
                    // Interactive 0-5 star rating
                    InteractiveStarRating(
                        rating = trackRating,
                        onRate = onRate,
                        modifier = Modifier.padding(top = spacingS()),
                    )
                }
            }
            Spacer(Modifier.height(adp(16f)))

            // Seek bar: only for tracks with known duration (skip live/radio)
            if (duration > 0) {
                // Waveform scrubber (if bars available) or fallback progress bar
                if (waveformBars.isNotEmpty() && duration > 0) {
                    val rawFraction = (position.toFloat() / duration).coerceIn(0f, 1f)
                    // Fraction animation lives inside WaveformScrubber so the whole
                    // PlayerBar body doesn't recompose at animation frame rate.
                    WaveformScrubber(
                        bars = waveformBars,
                        fraction = rawFraction,
                        position = position,
                        duration = duration,
                        onSeek = onSeek,
                        modifier = Modifier.padding(horizontal = spacing2XL()),
                    )
                } else {
                    var isDragging by remember { mutableStateOf(false) }
                    var dragFraction by remember { mutableFloatStateOf(0f) }
                    val rawFraction = if (isDragging) {
                        dragFraction
                    } else if (duration > 0) {
                        position.toFloat() / duration
                    } else {
                        0f
                    }
                    val displayFraction by animateFloatAsState(
                        rawFraction,
                        tween(200, easing = LinearEasing),
                        label = "barFrac",
                    )
                    Column(Modifier.padding(horizontal = spacing2XL())) {
                        Box(
                            Modifier.fillMaxWidth().height(adp(24f))
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        if (duration > 0) {
                                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                            onSeek(frac)
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { offset ->
                                            if (duration > 0) {
                                                isDragging = true
                                                dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                            }
                                        },
                                        onDragEnd = {
                                            if (isDragging && duration > 0) onSeek(dragFraction)
                                            isDragging = false
                                        },
                                        onDragCancel = { isDragging = false },
                                        onHorizontalDrag = { _, dragAmount ->
                                            if (duration > 0 && isDragging) {
                                                val delta = dragAmount / size.width
                                                dragFraction = (dragFraction + delta).coerceIn(0f, 1f)
                                            }
                                        },
                                    )
                                },
                        )
                        val trackColor = Color.White.copy(alpha = 0.15f)
                        val gradientBrush = Brush.horizontalGradient(listOf(BrandTeal, BrandPurple))
                        Box(Modifier.fillMaxWidth().height(adp(16f))) {
                            Box(
                                Modifier.fillMaxWidth().height(
                                    4.dp,
                                ).align(Alignment.Center).clip(RoundedCornerShape(adp(2f))).background(trackColor),
                            )
                            val clampedFraction = displayFraction.coerceIn(0.001f, 0.999f)
                            Row(Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center)) {
                                Box(
                                    Modifier.weight(
                                        clampedFraction,
                                    ).fillMaxHeight().clip(RoundedCornerShape(adp(2f))).background(gradientBrush),
                                )
                                Box(Modifier.weight(1f - clampedFraction).fillMaxHeight())
                            }
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val knobX = (maxWidth - 14.dp) * displayFraction.coerceIn(0f, 1f)
                                Box(
                                    Modifier
                                        .offset(x = knobX, y = 1.dp)
                                        .size(knobSize())
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                            }
                        }
                        Spacer(Modifier.height(spacingXS()))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                formatPlayerBarTime(position),
                                color = NavUnselected,
                                fontSize = textBodyM(),
                                fontFamily = interFontFamily(),
                            )
                            Text(
                                "-${formatPlayerBarTime((duration - position).coerceAtLeast(0))}",
                                color = NavUnselected,
                                fontSize = textBodyM(),
                                fontFamily = interFontFamily(),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(spacingM()))
            // Transport controls
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onShuffleToggle) {
                    Icon(
                        Icons.Default.Shuffle,
                        stringResource(R.string.player_shuffle),
                        tint = if (shuffleModeEnabled) BrandTeal else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(iconSmall()),
                    )
                }
                IconButton(onClick = onSkipPrev) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        stringResource(R.string.player_previous),
                        tint = Color.White,
                        modifier = Modifier.size(iconMedium()),
                    )
                }
                Box(
                    Modifier.size(miniPlayerHeight())
                        .clip(RoundedCornerShape(cornerL()))
                        .background(
                            Brush.linearGradient(
                                listOf(BrandTeal, BrandPurple),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                            ),
                        )
                        .clickable { onPlayPause() }
                        .testTag("transport_play"),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(iconMedium()).testTag("transport_buffering"),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) {
                                stringResource(R.string.player_pause)
                            } else {
                                stringResource(R.string.player_play)
                            },
                            tint = Color.White,
                            modifier = Modifier.size(iconMedium()),
                        )
                    }
                }
                IconButton(onClick = onSkipNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        stringResource(R.string.player_next),
                        tint = Color.White,
                        modifier = Modifier.size(iconMedium()),
                    )
                }
                IconButton(onClick = onRepeatToggle) {
                    val repeatIcon = if (repeatMode ==
                        androidx.media3.common.Player.REPEAT_MODE_ONE
                    ) {
                        Icons.Filled.RepeatOne
                    } else {
                        Icons.Default.Repeat
                    }
                    Icon(
                        repeatIcon,
                        stringResource(R.string.player_repeat),
                        tint = if (repeatMode > 0) BrandTeal else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(iconSmall()),
                    )
                }
            }

            // Cast volume slider — after transport per design
            if (isCasting) {
                var volumeDragging by remember { mutableStateOf(false) }
                var volumeDragValue by remember { mutableFloatStateOf(volumeToDisplay(volume)) }
                LaunchedEffect(volume) {
                    if (!volumeDragging) {
                        volumeDragValue = volumeToDisplay(volume)
                    }
                }
                var lastVolumeSendMs by remember { mutableLongStateOf(0L) }
                var dragStartVolume by remember { mutableFloatStateOf(0f) }
                var dragStartDisplay by remember { mutableFloatStateOf(0f) }
                var hasDragged by remember { mutableStateOf(false) }
                val displayVolume = when {
                    volumeDragging -> volumeDragValue
                    else -> volumeToDisplay(volume)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacing3XL()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.VolumeDown,
                        "Volume",
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(knobSize()),
                    )
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = displayVolume,
                        onValueChange = { display ->
                            if (!volumeDragging) {
                                // M3 Slider can emit display=0 on first press
                                // (zero-width / press quirk). That plus the
                                // delta formula sends SET_VOLUME(0). Ignore
                                // only before drag-start; a real slide-to-0
                                // after drag started must still send.
                                if (isSpuriousZeroDisplay(display, volumeToDisplay(volume))) {
                                    return@Slider
                                }
                                volumeDragging = true
                                dragStartVolume = volume
                                dragStartDisplay = volumeToDisplay(volume)
                            }
                            hasDragged = true
                            volumeDragValue = display
                            val now = System.currentTimeMillis()
                            if (now - lastVolumeSendMs >= 250L) {
                                lastVolumeSendMs = now
                                onVolumeChange(
                                    volumeForDrag(display, dragStartVolume, dragStartDisplay),
                                )
                            }
                        },
                        onValueChangeFinished = {
                            volumeDragging = false
                            if (hasDragged) {
                                hasDragged = false
                                onVolumeChange(
                                    volumeForDrag(volumeDragValue, dragStartVolume, dragStartDisplay),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = BrandTeal,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(adp(72f))) // clearance for the queue peek strip
        }
    }
}

/**
 * Queue peek strip + sheet. Declared as a BoxScope extension so the sheet can
 * `align(BottomCenter)` inside the dismiss wrapper.
 */
@Composable
private fun BoxScope.PlayerQueuePanel(
    state: PlayerBarState,
    duration: Long,
    showQueue: Boolean,
    queueOffset: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    queueScope: CoroutineScope,
    queueSheetHeightPx: Float,
    onShowQueueChange: (Boolean) -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onClearAutoplayQueue: () -> Unit,
    onContinuousPlayChange: (Boolean) -> Unit,
    onShuffleToggle: () -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onBeginQueueReorder: (entryId: Int, fromIndex: Int) -> Unit,
    onCommitQueueReorder: () -> Unit,
    onShareQueue: () -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    onPlayHistoryTrack: (String) -> Unit,
    onRemoveFromQueueBatch: (Set<Int>) -> Unit,
    defaultQueuePlaylistName: String,
) {
    with(state) {
        var selectionMode by remember { mutableStateOf(false) }
        var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
        var showSaveDialog by remember { mutableStateOf(false) }
        LaunchedEffect(showQueue) {
            if (!showQueue) {
                selectionMode = false
                selectedIndices = emptySet()
                showSaveDialog = false
            }
        }
        // ── Queue peek strip — always composed (the sheet covers it when
        // open). Swipe up with finger-follow: the sheet translates live
        // during the drag and springs to an anchor on release. ──
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("queue_peek_strip")
                .clickable {
                    // Tap to open — settle fully open
                    queueScope.launch {
                        onShowQueueChange(true)
                        queueOffset.stop()
                        queueOffset.snapTo(0f)
                        queueOffset.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            // Compose the sheet immediately so it tracks the
                            // finger from the first movement.
                            queueScope.launch {
                                queueOffset.stop()
                                onShowQueueChange(true)
                            }
                        },
                        onDragEnd = {
                            queueScope.launch {
                                if (queueOffset.value > 0.05f) {
                                    queueOffset.animateTo(
                                        1f,
                                        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                                    )
                                } else {
                                    queueOffset.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                                    )
                                    onShowQueueChange(false)
                                }
                            }
                        },
                        onDragCancel = {
                            queueScope.launch {
                                queueOffset.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                                )
                                onShowQueueChange(false)
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            // dragAmount < 0 = finger up (opening) → fraction grows
                            queueScope.launch {
                                queueOffset.snapTo(
                                    (queueOffset.value - dragAmount / queueSheetHeightPx).coerceIn(0f, 1f),
                                )
                            }
                        },
                    )
                },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingM()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.width(
                        adp(36f),
                    ).height(adp(4f)).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f)),
                )
                Spacer(Modifier.height(spacingM()))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val hasNext = nextTrackTitle != null
                    Text(
                        if (hasNext) {
                            val nextTrack = nextTracks.firstOrNull { it.queueIndex == trackIndex + 1 }
                            val nextIsPriority = nextTrack?.isPriority == true
                            if (nextIsPriority) {
                                stringResource(R.string.player_queue_next)
                            } else {
                                stringResource(R.string.player_up_next)
                            }
                        } else {
                            stringResource(R.string.player_end_of_queue)
                        },
                        color = NavUnselected,
                        fontSize = textMicro(),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    if (hasNext) {
                        // Single line "TrackName (artist)" — same font,
                        // artist slightly dimmed for separation.
                        Text(
                            buildAnnotatedString {
                                append(nextTrackTitle ?: "")
                                if (!nextTrackArtist.isNullOrEmpty()) {
                                    append(" (")
                                    withStyle(SpanStyle(color = NavUnselected)) {
                                        append(nextTrackArtist)
                                    }
                                    append(")")
                                }
                            },
                            color = Color.White,
                            fontSize = textBodyM(),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(spacingS()))
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            "Expand queue",
                            tint = NavUnselected,
                            modifier = Modifier.size(adp(18f)),
                        )
                    }
                }
            }
        }

        // ── Queue sheet — translates with the finger (fraction 0..1), springs
        // to an anchor on release. Composed only while open/opening so the
        // queue rows' art is not loaded while closed. ──
        if (showQueue) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxSize()
                    .offset { IntOffset(0, ((1f - queueOffset.value) * queueSheetHeightPx).roundToInt()) },
            ) {
                Column(
                    Modifier.fillMaxSize().testTag("queue_sheet")
                        .background(SurfaceElevated)
                        .clip(RoundedCornerShape(topStart = cornerL(), topEnd = cornerL()))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.07f),
                            RoundedCornerShape(topStart = cornerL(), topEnd = cornerL()),
                        ),
                ) {
                    // Drag handle — generous hit area, tap or swipe down to close
                    Column(
                        Modifier.fillMaxWidth().testTag("queue_sheet_handle").height(adp(40f))
                            .clickable {
                                queueScope.launch {
                                    queueOffset.stop()
                                    queueOffset.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                                    )
                                    onShowQueueChange(false)
                                }
                            }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        queueScope.launch {
                                            if (queueOffset.value < 0.5f) {
                                                queueOffset.animateTo(
                                                    0f,
                                                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                                                )
                                                onShowQueueChange(false)
                                            } else {
                                                queueOffset.animateTo(
                                                    1f,
                                                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                                                )
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        queueScope.launch {
                                            queueOffset.animateTo(
                                                1f,
                                                spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                                            )
                                        }
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        // dragAmount > 0 = finger down (closing) → fraction shrinks
                                        queueScope.launch {
                                            queueOffset.snapTo(
                                                (queueOffset.value - dragAmount / queueSheetHeightPx).coerceIn(0f, 1f),
                                            )
                                        }
                                    },
                                )
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier.width(
                                adp(40f),
                            ).height(adp(4f)).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)),
                        )
                    }

                    // Scrollable content below the fixed drag handle.
                    // Industry: Queue (PRIORITY) → Next from → Autoplay (ADR-0053 / 0070).
                    val priorityTracks = nextTracks.filter { it.isPriority && !it.isCurrent }
                    val continueTracks = nextTracks.filter { !it.isPriority && !it.isAutoplay && !it.isCurrent }
                    val autoplayTracks = nextTracks.filter { !it.isPriority && it.isAutoplay && !it.isCurrent }
                    val reorderableTracks = priorityTracks + continueTracks + autoplayTracks
                    val queueListState = rememberLazyListState()
                    val reorderableState = rememberReorderableLazyListState(
                        lazyListState = queueListState,
                        onMove = { from, to ->
                            val fromIdx = reorderableTracks.firstOrNull {
                                queueRowKey(it) == from.key
                            }?.queueIndex ?: return@rememberReorderableLazyListState
                            val toIdx = reorderableTracks.firstOrNull {
                                queueRowKey(it) == to.key
                            }?.queueIndex ?: return@rememberReorderableLazyListState
                            onMoveQueueItem(fromIdx, toIdx)
                        },
                    )
                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("queue_scroll"),
                    ) {
                        // Cast flatten notice
                        item(key = "cast-notice") {
                            if (isCasting && castDeviceName != null) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = spacingXL(), vertical = spacingS())
                                        .clip(RoundedCornerShape(cornerM()))
                                        .background(BrandTeal.copy(alpha = 0.07f))
                                        .border(
                                            1.dp,
                                            BrandTeal.copy(alpha = 0.18f),
                                            RoundedCornerShape(cornerM()),
                                        )
                                        .padding(horizontal = spacingM(), vertical = spacingS()),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.CastConnected,
                                        null,
                                        tint = BrandTeal,
                                        modifier = Modifier.size(adp(11f)),
                                    )
                                    Spacer(Modifier.width(spacingS()))
                                    FittingText(
                                        text = stringResource(R.string.player_cast_flatten_notice),
                                        color = BrandTeal,
                                        fontSize = textLabelS(),
                                        minFontSize = textMicro(),
                                        maxLines = 2,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        // Now Playing anchor
                        item(key = "now-playing-anchor") {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = spacingXL(), vertical = spacingS())
                                    .clip(RoundedCornerShape(cornerM()))
                                    .border(1.dp, BrandTeal.copy(alpha = 0.25f), RoundedCornerShape(cornerM()))
                                    .background(BrandTeal.copy(alpha = 0.05f)),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = spacingM(), vertical = spacingXS()),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isPlaying) {
                                        Row(
                                            Modifier.width(adp(16f)).height(adp(14f)),
                                            horizontalArrangement = Arrangement.spacedBy(adp(2f)),
                                            verticalAlignment = Alignment.Bottom,
                                        ) {
                                            val eqAnim = rememberInfiniteTransition(label = "eq_anchor")
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
                                                    Modifier.width(adp(3f))
                                                        .fillMaxHeight(anim)
                                                        .clip(RoundedCornerShape(adp(1f)))
                                                        .background(BrandTeal),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(spacingS()))
                                    Text(
                                        stringResource(R.string.player_now_playing_label),
                                        color = BrandTeal,
                                        fontSize = textMicro(),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                    )
                                }
                                Spacer(
                                    Modifier.height(adp(1f)).fillMaxWidth()
                                        .background(BrandTeal.copy(alpha = 0.15f)),
                                )
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = spacingM(), vertical = spacingS()),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (coverArtUrl != null) {
                                        AsyncImage(
                                            model = coverArtUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(adp(44f)).clip(RoundedCornerShape(cornerS())),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Box(
                                            Modifier.size(adp(44f))
                                                .clip(RoundedCornerShape(cornerS()))
                                                .background(Color(0xFF1E1E1E)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.MusicNote,
                                                null,
                                                tint = NavUnselected,
                                                modifier = Modifier.size(adp(20f)),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(spacingM()))
                                    Column(Modifier.weight(1f)) {
                                        FittingText(
                                            text = title ?: stringResource(R.string.player_no_track),
                                            color = Color.White,
                                            fontSize = textHeadingS(),
                                            minFontSize = textMicro(),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        FittingText(
                                            text = artist ?: "",
                                            color = NavUnselected,
                                            fontSize = textLabelM(),
                                            minFontSize = textMicro(),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    if (duration > 0) {
                                        Text(
                                            formatPlayerBarTime(duration),
                                            color = NavUnselected,
                                            fontSize = textLabelM(),
                                            fontFamily = interFontFamily(),
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "playing-from") {
                            if (contextSource != null) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = spacingXL(), vertical = spacingXS()),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.List,
                                        null,
                                        tint = NavUnselected,
                                        modifier = Modifier.size(adp(12f)),
                                    )
                                    Spacer(Modifier.width(spacingXS()))
                                    FittingText(
                                        text = "Playing from: $contextSource",
                                        color = NavUnselected,
                                        fontSize = textLabelS(),
                                        minFontSize = textMicro(),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        item(key = "up-next-header") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = spacingXL()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.player_up_next),
                                    color = NavUnselected,
                                    fontSize = textBodyM(),
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 4.sp,
                                    modifier = Modifier.padding(vertical = spacingS()),
                                )
                                if (isOffline) {
                                    Row(
                                        Modifier.padding(horizontal = spacingS(), vertical = spacingXS())
                                            .clip(RoundedCornerShape(cornerS()))
                                            .background(OfflineYellow.copy(alpha = 0.12f))
                                            .padding(horizontal = spacingS(), vertical = adp(3f)),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.CloudOff,
                                            null,
                                            tint = OfflineYellow,
                                            modifier = Modifier.size(adp(10f)),
                                        )
                                        Spacer(Modifier.width(spacingXS()))
                                        Text(
                                            stringResource(R.string.player_offline),
                                            color = OfflineYellow,
                                            fontSize = textMicro(),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                } else if (isQueueSynced) {
                                    Row(
                                        Modifier.padding(horizontal = spacingS(), vertical = spacingXS())
                                            .clip(RoundedCornerShape(cornerS()))
                                            .background(BrandTeal.copy(alpha = 0.1f))
                                            .padding(horizontal = spacingS(), vertical = adp(3f)),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.Wifi,
                                            null,
                                            tint = BrandTeal,
                                            modifier = Modifier.size(adp(10f)),
                                        )
                                        Spacer(Modifier.width(spacingXS()))
                                        Text(
                                            stringResource(R.string.player_synced),
                                            color = BrandTeal,
                                            fontSize = textMicro(),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                } else {
                                    Row(
                                        Modifier.padding(horizontal = spacingS(), vertical = spacingXS())
                                            .clip(RoundedCornerShape(cornerS()))
                                            .background(OfflineYellow.copy(alpha = 0.1f))
                                            .padding(horizontal = spacingS(), vertical = adp(3f)),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.ErrorOutline,
                                            null,
                                            tint = OfflineYellow,
                                            modifier = Modifier.size(adp(10f)),
                                        )
                                        Spacer(Modifier.width(spacingXS()))
                                        Text(
                                            stringResource(R.string.player_saving),
                                            color = OfflineYellow,
                                            fontSize = textMicro(),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                if (selectionMode) {
                                    Text(
                                        stringResource(R.string.player_cancel_select),
                                        color = NavUnselected,
                                        fontSize = textLabelM(),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clickable {
                                                selectionMode = false
                                                selectedIndices = emptySet()
                                            }
                                            .padding(spacingS())
                                            .heightIn(min = adp(48f))
                                            .wrapContentHeight(Alignment.CenterVertically)
                                            .testTag("queue_select_cancel"),
                                    )
                                    Text(
                                        stringResource(R.string.player_remove_selected),
                                        color = DestructiveRed,
                                        fontSize = textLabelM(),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clickable(enabled = selectedIndices.isNotEmpty()) {
                                                onRemoveFromQueueBatch(selectedIndices)
                                                selectionMode = false
                                                selectedIndices = emptySet()
                                            }
                                            .padding(spacingS())
                                            .heightIn(min = adp(48f))
                                            .wrapContentHeight(Alignment.CenterVertically)
                                            .testTag("queue_select_remove"),
                                    )
                                } else {
                                    if (reorderableTracks.isNotEmpty()) {
                                        Text(
                                            stringResource(R.string.player_select),
                                            color = NavUnselected,
                                            fontSize = textLabelM(),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clickable { selectionMode = true }
                                                .padding(spacingS())
                                                .heightIn(min = adp(48f))
                                                .wrapContentHeight(Alignment.CenterVertically)
                                                .testTag("queue_select_enter"),
                                        )
                                    }
                                    Icon(
                                        Icons.Default.Shuffle,
                                        stringResource(R.string.player_shuffle),
                                        tint = if (shuffleModeEnabled) BrandTeal else NavUnselected,
                                        modifier = Modifier
                                            .size(adp(48f))
                                            .padding(end = spacingXS())
                                            .clickable { onShuffleToggle() }
                                            .testTag("queue_sheet_shuffle"),
                                    )
                                    Icon(
                                        Icons.Default.PlaylistAdd,
                                        stringResource(R.string.player_save_queue),
                                        tint = NavUnselected,
                                        modifier = Modifier
                                            .size(adp(48f))
                                            .padding(end = spacingXS())
                                            .clickable { showSaveDialog = true }
                                            .testTag("queue_sheet_save"),
                                    )
                                    Icon(
                                        Icons.Default.Share,
                                        stringResource(R.string.player_share_queue),
                                        tint = NavUnselected,
                                        modifier = Modifier
                                            .size(adp(48f))
                                            .padding(end = spacingXS())
                                            .clickable { onShareQueue() }
                                            .testTag("queue_sheet_share"),
                                    )
                                }
                                Spacer(Modifier.width(spacingS()))
                                Text(
                                    stringResource(R.string.player_tracks_count, queueSize),
                                    color = NavUnselected,
                                    fontSize = textLabelM(),
                                )
                            }
                        }

                        if (priorityTracks.isNotEmpty()) {
                            item(key = "hdr-priority") {
                                Spacer(Modifier.height(spacingS()))
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = spacingXL())
                                        .testTag("queue_section_priority")
                                        .clip(RoundedCornerShape(cornerM()))
                                        .border(
                                            1.dp,
                                            BrandPurple.copy(alpha = 0.3f),
                                            RoundedCornerShape(cornerM()),
                                        )
                                        .background(BrandPurple.copy(alpha = 0.06f)),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(
                                            horizontal = spacingM(),
                                            vertical = spacingXS(),
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.QueueMusic,
                                            null,
                                            tint = BrandPurple,
                                            modifier = Modifier.size(adp(14f)),
                                        )
                                        Spacer(Modifier.width(spacingS()))
                                        Text(
                                            stringResource(R.string.player_queue_section, priorityTracks.size),
                                            color = BrandPurple,
                                            fontSize = textLabelS(),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        // Spotify/Apple: Clear removes manual Next in Queue only
                                        Text(
                                            stringResource(R.string.player_clear_queue),
                                            color = DestructiveRed,
                                            fontSize = textLabelM(),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clickable { onClearQueue() }
                                                .padding(spacingS())
                                                .heightIn(min = adp(48f))
                                                .wrapContentHeight(Alignment.CenterVertically)
                                                .testTag("queue_clear_priority"),
                                        )
                                    }
                                    Spacer(
                                        Modifier.height(adp(1f)).fillMaxWidth()
                                            .background(BrandPurple.copy(alpha = 0.15f)),
                                    )
                                }
                            }
                            items(priorityTracks, key = { queueRowKey(it) }) { track ->
                                ReorderableItem(state = reorderableState, key = queueRowKey(track)) { isDragging ->
                                    QueueDismissTrackRow(
                                        track = track,
                                        isPlaying = isPlaying,
                                        isDragging = isDragging && !selectionMode,
                                        downloadedTrackIds = downloadedTrackIds,
                                        onPlayQueueItem = onPlayQueueItem,
                                        onRemoveFromQueue = onRemoveFromQueue,
                                        selectionMode = selectionMode,
                                        selected = track.queueIndex in selectedIndices,
                                        onToggleSelect = {
                                            selectedIndices = if (track.queueIndex in selectedIndices) {
                                                selectedIndices - track.queueIndex
                                            } else {
                                                selectedIndices + track.queueIndex
                                            }
                                        },
                                        onLongPressSelect = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedIndices = setOf(track.queueIndex)
                                            }
                                        },
                                        dragHandleModifier = if (selectionMode) {
                                            Modifier
                                        } else {
                                            Modifier.draggableHandle(
                                                onDragStarted = {
                                                    onBeginQueueReorder(track.entryId, track.queueIndex)
                                                },
                                                onDragStopped = { onCommitQueueReorder() },
                                            )
                                        },
                                    )
                                }
                            }
                            item(key = "spacer-priority") { Spacer(Modifier.height(spacingS())) }
                        }

                        if (continueTracks.isNotEmpty()) {
                            item(key = "hdr-next-from") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = spacingXL(), vertical = spacingS())
                                        .testTag("queue_section_next_from"),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.List,
                                        null,
                                        tint = NavUnselected,
                                        modifier = Modifier.size(adp(14f)),
                                    )
                                    Spacer(Modifier.width(spacingS()))
                                    Text(
                                        if (contextSource != null) {
                                            stringResource(
                                                R.string.player_next_from_source_section,
                                                contextSource!!,
                                                continueTracks.size,
                                            )
                                        } else {
                                            stringResource(R.string.player_next_from_section, continueTracks.size)
                                        },
                                        color = NavUnselected,
                                        fontSize = textLabelS(),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                    )
                                }
                            }
                            items(continueTracks, key = { queueRowKey(it) }) { track ->
                                ReorderableItem(state = reorderableState, key = queueRowKey(track)) { isDragging ->
                                    QueueDismissTrackRow(
                                        track = track,
                                        isPlaying = isPlaying,
                                        isDragging = isDragging && !selectionMode,
                                        downloadedTrackIds = downloadedTrackIds,
                                        onPlayQueueItem = onPlayQueueItem,
                                        onRemoveFromQueue = onRemoveFromQueue,
                                        selectionMode = selectionMode,
                                        selected = track.queueIndex in selectedIndices,
                                        onToggleSelect = {
                                            selectedIndices = if (track.queueIndex in selectedIndices) {
                                                selectedIndices - track.queueIndex
                                            } else {
                                                selectedIndices + track.queueIndex
                                            }
                                        },
                                        onLongPressSelect = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedIndices = setOf(track.queueIndex)
                                            }
                                        },
                                        dragHandleModifier = if (selectionMode) {
                                            Modifier
                                        } else {
                                            Modifier.draggableHandle(
                                                onDragStarted = {
                                                    onBeginQueueReorder(track.entryId, track.queueIndex)
                                                },
                                                onDragStopped = { onCommitQueueReorder() },
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        // Apple: Autoplay band + in-queue toggle always visible (ADR-0074)
                        item(key = "hdr-autoplay") {
                            var continuousPlayOn by remember(continuousPlayEnabled) {
                                mutableStateOf(continuousPlayEnabled)
                            }
                            Spacer(Modifier.height(spacingS()))
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = spacingXL())
                                    .testTag("queue_section_autoplay")
                                    .clip(RoundedCornerShape(cornerM()))
                                    .border(
                                        1.dp,
                                        BrandTeal.copy(alpha = 0.28f),
                                        RoundedCornerShape(cornerM()),
                                    )
                                    .background(BrandTeal.copy(alpha = 0.05f)),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(
                                        horizontal = spacingM(),
                                        vertical = spacingXS(),
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        null,
                                        tint = BrandTeal,
                                        modifier = Modifier.size(adp(14f)),
                                    )
                                    Spacer(Modifier.width(spacingS()))
                                    Text(
                                        if (autoplayTracks.isNotEmpty()) {
                                            stringResource(R.string.player_autoplay_section, autoplayTracks.size)
                                        } else {
                                            stringResource(R.string.player_autoplay_empty)
                                        },
                                        color = BrandTeal,
                                        fontSize = textLabelS(),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (autoplayTracks.isNotEmpty()) {
                                        Text(
                                            stringResource(R.string.player_clear_autoplay),
                                            color = BrandTeal,
                                            fontSize = textLabelM(),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clickable { onClearAutoplayQueue() }
                                                .padding(spacingS())
                                                .heightIn(min = adp(48f))
                                                .wrapContentHeight(Alignment.CenterVertically)
                                                .testTag("queue_clear_autoplay"),
                                        )
                                    }
                                    Switch(
                                        checked = continuousPlayOn,
                                        onCheckedChange = {
                                            continuousPlayOn = it
                                            onContinuousPlayChange(it)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Foreground,
                                            checkedTrackColor = BrandTeal,
                                            uncheckedThumbColor = NavUnselected,
                                            uncheckedTrackColor = Dimmed,
                                        ),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = adp(48f))
                                            .testTag("queue_continuous_play_switch"),
                                    )
                                }
                            }
                        }
                        if (autoplayTracks.isNotEmpty()) {
                            items(autoplayTracks, key = { queueRowKey(it) }) { track ->
                                ReorderableItem(state = reorderableState, key = queueRowKey(track)) { isDragging ->
                                    QueueDismissTrackRow(
                                        track = track,
                                        isPlaying = isPlaying,
                                        isDragging = isDragging && !selectionMode,
                                        downloadedTrackIds = downloadedTrackIds,
                                        onPlayQueueItem = onPlayQueueItem,
                                        onRemoveFromQueue = onRemoveFromQueue,
                                        selectionMode = selectionMode,
                                        selected = track.queueIndex in selectedIndices,
                                        onToggleSelect = {
                                            selectedIndices = if (track.queueIndex in selectedIndices) {
                                                selectedIndices - track.queueIndex
                                            } else {
                                                selectedIndices + track.queueIndex
                                            }
                                        },
                                        onLongPressSelect = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedIndices = setOf(track.queueIndex)
                                            }
                                        },
                                        dragHandleModifier = if (selectionMode) {
                                            Modifier
                                        } else {
                                            Modifier.draggableHandle(
                                                onDragStarted = {
                                                    onBeginQueueReorder(track.entryId, track.queueIndex)
                                                },
                                                onDragStopped = { onCommitQueueReorder() },
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        if (queueHistory.isNotEmpty() && !selectionMode) {
                            item(key = "hdr-history") {
                                Spacer(Modifier.height(spacingM()))
                                Text(
                                    stringResource(R.string.player_recently_played_section, queueHistory.size),
                                    color = NavUnselected,
                                    fontSize = textLabelS(),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier
                                        .padding(horizontal = spacingXL(), vertical = spacingS())
                                        .testTag("queue_section_history"),
                                )
                            }
                            items(queueHistory, key = { "hist-${it.id}" }) { hist ->
                                QueueHistoryRow(
                                    track = hist,
                                    onPlayNext = { onPlayHistoryTrack(hist.id) },
                                )
                            }
                        }

                        if (priorityTracks.isEmpty() && continueTracks.isEmpty() && autoplayTracks.isEmpty()) {
                            item(key = "empty-queue") {
                                Spacer(Modifier.height(spacing2XL()))
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = spacingXL()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic,
                                        null,
                                        tint = Color(0xFF2A2A3E),
                                        modifier = Modifier.size(adp(36f)),
                                    )
                                    Spacer(Modifier.height(spacingM()))
                                    Text(
                                        stringResource(R.string.player_queue_empty),
                                        color = NavUnselected,
                                        fontSize = textBodyM(),
                                    )
                                    Spacer(Modifier.height(spacingXS()))
                                    Text(
                                        stringResource(R.string.player_queue_empty_hint),
                                        color = Dimmed,
                                        fontSize = textLabelM(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                        item(key = "footer-spacer") { Spacer(Modifier.height(spacing3XL())) }
                    }
                }
                if (showSaveDialog) {
                    QueueSavePlaylistDialog(
                        initialName = defaultQueuePlaylistName,
                        onSave = { name ->
                            showSaveDialog = false
                            onSaveQueueAsPlaylist(name)
                        },
                        onDismiss = { showSaveDialog = false },
                    )
                }
            }
        }
    }
}

/** Lyrics overlay — slides up over the player. */
@Composable
private fun PlayerLyricsOverlay(
    state: PlayerBarState,
    positionMs: Long,
    durationMs: Long,
    visible: Boolean,
    onDismiss: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        with(state) {
            Column(Modifier.fillMaxSize().background(Color(0xFF0A0A12).copy(alpha = 0.97f))) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingM()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Mic, null, tint = BrandTeal, modifier = Modifier.size(adp(15f)))
                        Spacer(Modifier.width(spacingS()))
                        Text(
                            stringResource(R.string.player_lyrics),
                            color = Color.White,
                            fontSize = textHeadingM(),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(
                            adp(32f),
                        ).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable {
                            onDismiss()
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            stringResource(R.string.player_close_lyrics),
                            tint = NavUnselected,
                            modifier = Modifier.size(adp(16f)),
                        )
                    }
                }
                LyricsContent(
                    artist = artist,
                    title = title,
                    trackId = trackId,
                    positionMs = positionMs,
                    lyricsText = lyricsText,
                    lyricLines = lyricLines,
                    isLoading = lyricsLoading,
                    onSeekToMs = { positionMs ->
                        val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                        onSeek(fraction)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** All non-callback state parameters for PlayerBar packed into one class
 *  to avoid exceeding the dex method signature limit (30+ params). */
@androidx.compose.runtime.Immutable
data class PlayerBarState(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val coverArtUrl: String? = null,
    val coverArtId: String? = null,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null,
    val volume: Float = 1.0f,
    val repeatMode: Int = 0,
    val shuffleModeEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerEndMs: Long = 0L,
    val isStarred: Boolean = false,
    /** v41: local thumbs-down. Mutually exclusive with isStarred (like). */
    val isDisliked: Boolean = false,
    /** 0-5 user rating for the current track. */
    val trackRating: Int = 0,
    val nextTrackTitle: String? = null,
    val nextTrackArtist: String? = null,
    val trackIndex: Int = 0,
    val queueSize: Int = 0,
    val isOffline: Boolean = false,
    val isQueueSynced: Boolean = true,
    val downloadedTrackIds: Set<String> = emptySet(),
    val nextTracks: List<com.lucasdss.ftpmusic.app.playback.UpcomingTrack> = emptyList(),
    val expanded: Boolean = false,
    val colors: PlayerBarColors = PlayerBarColors(),
    val trackId: String? = null,
    val lyricsText: String? = null,
    val lyricLines: List<LyricLine> = emptyList(),
    val lyricsLoading: Boolean = false,
    val contextSource: String? = null,
    val priorityQueueSize: Int = 0,
    /** Apple-style Autoplay / Continuous Play (ADR-0053 / ADR-0074). */
    val continuousPlayEnabled: Boolean = true,
    /** Recently played for history band (ADR-0075); empty = hide section. */
    val queueHistory: List<com.lucasdss.ftpmusic.app.playback.QueueHistoryTrack> = emptyList(),
    val waveformBars: List<Float> = emptyList(),
)

/** A single synced lyric line with timestamp (milliseconds from song start). */
data class LyricLine(val timeMs: Long, val text: String)

/** Strip HTML tags and LRC timestamps from lyrics text. */
fun cleanLyricText(text: String): String = text
    .replace(Regex("\\[[0-9]{2}:[0-9]{2}\\.[0-9]{2,3}]"), "") // strip LRC timestamps [mm:ss.xx]
    .replace("\\n", "\n") // literal backslash-n from JSON → actual newline
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]*>"), "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .trim()

/** Parse LRC-format text into synced LyricLines. Example: "[00:15.23]Line one\n[00:32.50]Line two" */
fun parseLrcText(text: String): List<LyricLine> {
    val regex = Regex("\\[([0-9]{2}):([0-9]{2})(?:\\.([0-9]{1,3}))?\\]([^\\[]*)")
    val lines = regex.findAll(text).map { match ->
        val min = match.groupValues[1].toLong()
        val sec = match.groupValues[2].toLong()
        val rawMs = match.groupValues[3]
        val msPart = rawMs.ifEmpty { "0" }.padEnd(3, '0').take(3).toLong()
        val timeMs = min * 60_000 + sec * 1000 + msPart
        val lyricText = cleanLyricText(match.groupValues[4])
        LyricLine(timeMs, lyricText)
    }.toList()

    // Capture text before the first timestamp (untimed intro stanza)
    val preText = text.substringBefore("[", "").trim()
    return if (preText.isNotEmpty() && preText.any { it.isLetter() }) {
        listOf(LyricLine(0, cleanLyricText(preText))) + lines
    } else {
        lines
    }
}

/** Strip HTML tags from lyrics text (legacy, delegates to cleanLyricText). */
internal fun stripHtmlTags(text: String): String = cleanLyricText(text)

/**
 * Binary search: index of the last line whose timeMs <= positionMs, or -1.
 * Lines must be time-sorted (see LyricsContent). Replaces the O(n) indexOfLast
 * scan that ran on every 200 ms position tick (P4).
 */
internal fun lastLyricIndexBefore(lines: List<LyricLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.size - 1
    var result = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= positionMs) {
            result = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return result
}

/** Colors extracted from album cover art for gradient backgrounds. */
@androidx.compose.runtime.Immutable
data class PlayerBarColors(val darkMuted: Color? = null, val vibrant: Color? = null)

/** Formats milliseconds as m:ss for display on seek bars. */
@VisibleForTesting
fun formatPlayerBarTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000) / 60
    return "%d:%02d".format(minutes, seconds)
}

/** Album-art swipe resolution. Negative offset (finger left) = next track. */
internal enum class SwipeDecision { NEXT, PREV, NONE }

internal fun swipeDecision(offsetPx: Float, thresholdPx: Float): SwipeDecision = when {
    abs(offsetPx) <= thresholdPx -> SwipeDecision.NONE
    offsetPx < 0f -> SwipeDecision.NEXT
    else -> SwipeDecision.PREV
}

/**
 * Design v4 swipeable album art: left swipe = next track, right swipe = prev.
 * The art follows the finger at 25% translate + a slight rotation, with spring
 * overshoot back to center on release under the threshold (55dp). Next/prev
 * album art render as near-full-size decorative cards at the screen edges,
 * barely visible at rest (~4% hairline), sliding in at 50% of the drag.
 * Disabled while casting or with a single-item queue.
 */
@Composable
private fun SwipeableAlbumArt(
    coverArtUrl: String?,
    isCasting: Boolean,
    canSwipe: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier,
    artSize: Dp = (LocalConfiguration.current.screenWidthDp * 0.6f).coerceAtMost(224f).dp,
    nextCoverArtUrl: String? = null,
    prevCoverArtUrl: String? = null,
) {
    val density = LocalDensity.current
    val maxDragPx = with(density) { adp(200f).toPx() }
    val thresholdPx = with(density) { adp(55f).toPx() }
    // YT Music-style neighbors: near-full-size cards (0.9x the main art),
    // almost fully off-screen at rest (~4% hairline visible), sliding in
    // decisively (0.5x follow) while dragging.
    val peekSize = ((artSize * 0.9f).coerceAtLeast(adp(48f))).coerceAtMost(artSize)
    val peekSizePx = with(density) { peekSize.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val albumArtShape = RoundedCornerShape(16.dp)
    val albumArtGlowColor = BrandTeal.copy(alpha = if (isCasting) 0.45f else 0.30f)
    val albumArtElevation = if (isCasting) 30.dp else 24.dp

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Prev art peek — screen-left edge, drawn behind the current card.
        // Idle: ~4% hairline visible; swipe right pulls it inward. Decorative
        // (no pointer input), so it never blocks the drag on the current card.
        if (canSwipe && prevCoverArtUrl != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(peekSize)
                    .offset { IntOffset((-peekSizePx * 0.96f + offset.value * 0.5f).roundToInt(), 0) }
                    .clip(albumArtShape)
                    .testTag("now_playing_prev_peek"),
            ) {
                AsyncImage(
                    model = prevCoverArtUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        // Next art peek — screen-right edge. Swipe left pulls it inward.
        if (canSwipe && nextCoverArtUrl != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(peekSize)
                    .offset { IntOffset((peekSizePx * 0.96f + offset.value * 0.5f).roundToInt(), 0) }
                    .clip(albumArtShape)
                    .testTag("now_playing_next_peek"),
            ) {
                AsyncImage(
                    model = nextCoverArtUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        // Current art card — renders exactly as it did pre-carousel (proven to
        // load); clip + shadow keep it visually on top of the peek cards.
        Box(
            Modifier
                .size(artSize)
                .shadow(
                    elevation = albumArtElevation,
                    shape = albumArtShape,
                    ambientColor = albumArtGlowColor,
                    spotColor = albumArtGlowColor,
                )
                .clip(albumArtShape)
                .pointerInput(canSwipe) {
                    // Swipe works the same during Cast — onNext/onPrev route to
                    // the Cast skip (queueNext/queuePrev) via the existing flow.
                    if (canSwipe) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when (swipeDecision(offset.value, thresholdPx)) {
                                    SwipeDecision.NEXT -> onNext()
                                    SwipeDecision.PREV -> onPrev()
                                    SwipeDecision.NONE -> {}
                                }
                                scope.launch {
                                    offset.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                                    )
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    offset.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                                    )
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                scope.launch {
                                    offset.snapTo((offset.value + dragAmount).coerceIn(-maxDragPx, maxDragPx))
                                }
                            },
                        )
                    }
                },
        ) {
            // Current art — translated/rotated by the drag (25% follow).
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = offset.value * 0.25f
                    rotationZ = offset.value * 0.015f
                },
            ) {
                if (coverArtUrl != null) {
                    AsyncImage(
                        model = coverArtUrl,
                        contentDescription = "Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1E1E1E)), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Album,
                            null,
                            tint = Color(0xFF444444),
                            modifier = Modifier.size(miniPlayerHeight()),
                        )
                    }
                }
            }
        }
    }
}

/** Design v4 round reaction button — market hit [reactionHitSize]. */
@Composable
private fun ReactionCircle(
    active: Boolean,
    activeColor: Color,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(reactionHitSize())
            .clip(CircleShape)
            .then(
                if (active) {
                    Modifier.background(activeColor.copy(alpha = 0.18f))
                        .border(1.dp, activeColor.copy(alpha = 0.4f), CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(if (active) activeColor else NavUnselected)
    }
}

@VisibleForTesting
internal fun queueRowKey(track: com.lucasdss.ftpmusic.app.playback.UpcomingTrack): Any = when {
    track.entryId > 0 -> track.entryId
    track.trackId != null -> "id-${track.trackId}"
    else -> "qi-${track.queueIndex}-${track.title}"
}

/** Idle (non-drag) fill for queue rows — opaque so swipe-delete red cannot bleed. */
@VisibleForTesting
internal val QueueTrackRowIdleBackground = SurfaceElevated

/** Swipe-to-remove wrapper around [QueueTrackRow] for the prod queue sheet. */
@Composable
private fun QueueDismissTrackRow(
    track: com.lucasdss.ftpmusic.app.playback.UpcomingTrack,
    isPlaying: Boolean,
    isDragging: Boolean,
    downloadedTrackIds: Set<String>,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPressSelect: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
) {
    if (selectionMode) {
        QueueTrackRow(
            index = track.queueIndex,
            track = track,
            isPlaying = isPlaying,
            onPlayQueueItem = onPlayQueueItem,
            onRemoveFromQueue = onRemoveFromQueue,
            downloadedTrackIds = downloadedTrackIds,
            isDragging = isDragging,
            selectionMode = true,
            selected = selected,
            onToggleSelect = onToggleSelect,
            onLongPressSelect = onLongPressSelect,
            dragHandleModifier = dragHandleModifier,
        )
        return
    }
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onRemoveFromQueue(track.queueIndex)
                    true
                } else {
                    false
                }
            },
        ),
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(DestructiveRed),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    stringResource(R.string.player_remove),
                    tint = Foreground,
                    modifier = Modifier.padding(end = spacingXL()),
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        QueueTrackRow(
            index = track.queueIndex,
            track = track,
            isPlaying = isPlaying,
            onPlayQueueItem = onPlayQueueItem,
            onRemoveFromQueue = onRemoveFromQueue,
            downloadedTrackIds = downloadedTrackIds,
            isDragging = isDragging,
            selectionMode = false,
            selected = false,
            onToggleSelect = onToggleSelect,
            onLongPressSelect = onLongPressSelect,
            dragHandleModifier = dragHandleModifier,
        )
    }
}

/** Reusable queue track row for both priority and context sections. */
@Composable
private fun QueueTrackRow(
    index: Int,
    track: com.lucasdss.ftpmusic.app.playback.UpcomingTrack,
    isPlaying: Boolean,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    downloadedTrackIds: Set<String>,
    dimmed: Boolean = false,
    isDragging: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPressSelect: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
) {
    val rowBg = when {
        selected -> MaterialTheme.colorScheme.surfaceVariant
        isDragging -> MaterialTheme.colorScheme.surfaceVariant
        else -> QueueTrackRowIdleBackground
    }
    Row(
        Modifier.fillMaxWidth()
            .background(rowBg)
            .testTag("queue_track_row")
            .pointerInput(selectionMode, selected) {
                detectTapGestures(
                    onLongPress = { onLongPressSelect() },
                    onTap = {
                        if (selectionMode) onToggleSelect() else onPlayQueueItem(index)
                    },
                )
            }
            .padding(vertical = adp(6f), horizontal = spacingS()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = BrandTeal,
                    uncheckedColor = NavUnselected,
                    checkmarkColor = Foreground,
                ),
                modifier = Modifier
                    .size(adp(48f))
                    .testTag("queue_select_checkbox"),
            )
        } else {
            Icon(
                Icons.Default.DragHandle,
                stringResource(R.string.player_drag_handle),
                tint = NavUnselected,
                modifier = Modifier
                    .size(adp(48f))
                    .padding(spacingS())
                    .then(dragHandleModifier)
                    .testTag("queue_drag_handle"),
            )
        }
        Spacer(Modifier.width(spacingXS()))
        Box(Modifier.size(adp(40f)).clip(RoundedCornerShape(cornerS())), contentAlignment = Alignment.Center) {
            if (track.coverArtUrl != null) {
                AsyncImage(
                    model = track.coverArtUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(Surface), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = NavUnselected, modifier = Modifier.size(adp(16f)))
                }
            }
            if (track.isCurrent && !dimmed && isPlaying) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.width(adp(16f)).height(adp(14f)),
                        horizontalArrangement = Arrangement.spacedBy(adp(2f)),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        val eqAnim = rememberInfiniteTransition(label = "eq_pb_${track.trackId ?: "q$index"}")
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
                                Modifier.width(adp(3f))
                                    .fillMaxHeight(anim)
                                    .clip(RoundedCornerShape(adp(1f)))
                                    .background(BrandTeal),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(spacingM()))
        Column(Modifier.weight(1f)) {
            FittingText(
                text = track.title,
                color = if (dimmed) NavUnselected else Foreground,
                fontSize = textHeadingS(),
                minFontSize = textMicro(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
            track.artist?.let {
                FittingText(
                    text = it,
                    color = NavUnselected,
                    fontSize = textLabelM(),
                    minFontSize = textMicro(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (track.durationMs > 0) {
                Text(
                    formatPlayerBarTime(track.durationMs),
                    color = NavUnselected,
                    fontSize = textLabelS(),
                    fontFamily = interFontFamily(),
                )
            }
        }
        if (track.trackId != null && track.trackId in downloadedTrackIds) {
            DownloadDot("downloaded")
            Spacer(Modifier.width(spacingS()))
        }
        if (!selectionMode) {
            Box(
                Modifier
                    .size(adp(48f))
                    .clickable { onRemoveFromQueue(index) }
                    .testTag("queue_remove"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    stringResource(R.string.player_remove),
                    tint = NavUnselected,
                    modifier = Modifier.size(iconSmall()),
                )
            }
        }
    }
}

/** Recently Played band row — no drag; tap / Play Next → PRIORITY insert. */
@Composable
private fun QueueHistoryRow(track: com.lucasdss.ftpmusic.app.playback.QueueHistoryTrack, onPlayNext: () -> Unit) {
    val coverUrl = com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl(track.coverArtId, size = 80)
    Row(
        Modifier
            .fillMaxWidth()
            .background(QueueTrackRowIdleBackground)
            .clickable(onClick = onPlayNext)
            .padding(vertical = adp(6f), horizontal = spacingS())
            .testTag("queue_history_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(adp(40f)).clip(RoundedCornerShape(cornerS())),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(Surface), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = NavUnselected, modifier = Modifier.size(adp(16f)))
                }
            }
        }
        Spacer(Modifier.width(spacingM()))
        Column(Modifier.weight(1f)) {
            FittingText(
                text = track.title,
                color = Foreground,
                fontSize = textHeadingS(),
                minFontSize = textMicro(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
            track.artist?.let {
                FittingText(
                    text = it,
                    color = NavUnselected,
                    fontSize = textLabelM(),
                    minFontSize = textMicro(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Box(
            Modifier
                .size(adp(48f))
                .clickable(onClick = onPlayNext)
                .testTag("queue_history_play_next"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlaylistPlay,
                stringResource(R.string.player_play_next),
                tint = NavUnselected,
                modifier = Modifier.size(iconSmall()),
            )
        }
    }
}

/** Compact save-queue-as-playlist dialog (mirrors Album CreatePlaylistDialog). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSavePlaylistDialog(initialName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var saving by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(horizontal = spacingXL(), vertical = spacingM())) {
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(adp(32f))
                        .height(adp(4f))
                        .clip(RoundedCornerShape(adp(2f)))
                        .background(Color(0xFF444444)),
                )
            }
            Spacer(Modifier.height(spacingL()))
            Text(
                stringResource(R.string.player_save_queue_title),
                color = Foreground,
                fontSize = textHeadingM(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(spacingL()))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = {
                    Text(stringResource(R.string.player_save_queue), color = Color(0xFF666666))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Foreground,
                    unfocusedTextColor = Foreground,
                    focusedBorderColor = BrandTeal,
                    unfocusedBorderColor = Foreground.copy(alpha = 0.1f),
                    focusedContainerColor = Color(0xFF252538),
                    unfocusedContainerColor = Color(0xFF252538),
                    cursorColor = BrandTeal,
                ),
                modifier = Modifier.fillMaxWidth().testTag("queue_save_name_field"),
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
                    Text(
                        stringResource(R.string.player_cancel_select),
                        color = NavUnselected,
                        fontSize = textBodyM(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(
                    onClick = {
                        saving = true
                        onSave(name.trim())
                    },
                    enabled = name.isNotBlank() && !saving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(adp(48f))
                        .background(
                            Brush.linearGradient(listOf(BrandTeal, BrandPurple)),
                            RoundedCornerShape(cornerM()),
                        )
                        .testTag("queue_save_confirm"),
                ) {
                    Text(
                        stringResource(R.string.player_save_queue),
                        color = Foreground,
                        fontSize = textBodyM(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(spacingXL()))
        }
    }
}

/** Synced lyrics display with time-based highlighting. Fetches via getLyrics API. */
@Composable
private fun LyricsContent(
    artist: String?,
    title: String?,
    trackId: String? = null,
    positionMs: Long,
    lyricsText: String? = null,
    lyricLines: List<LyricLine> = emptyList(),
    isLoading: Boolean = false,
    onSeekToMs: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Reset scroll when track changes (not first-line timeMs — collisions across tracks).
    val listState = remember(trackId) { androidx.compose.foundation.lazy.LazyListState() }

    // Keep screen on while viewing lyrics
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val sortedLines = remember(lyricLines) { lyricLines.sortedBy { it.timeMs } }
    val isSynced = remember(sortedLines) { sortedLines.any { it.timeMs > 0L } }
    val cleanedUnstructured = remember(lyricsText) { lyricsText?.let { cleanLyricText(it) } }

    val activeIndex = lastLyricIndexBefore(sortedLines, positionMs)

    // Pause auto-scroll briefly after user interaction so animateScroll does not fight finger.
    var suppressAutoScrollUntil by remember(trackId) { mutableLongStateOf(0L) }
    var programmaticScroll by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !programmaticScroll) {
            suppressAutoScrollUntil = System.currentTimeMillis() + 3_000L
        }
    }
    LaunchedEffect(activeIndex) {
        if (System.currentTimeMillis() < suppressAutoScrollUntil) return@LaunchedEffect
        if (activeIndex >= 0 && sortedLines.isNotEmpty()) {
            programmaticScroll = true
            try {
                listState.animateScrollToItem(maxOf(0, activeIndex - 2))
            } finally {
                programmaticScroll = false
            }
        }
    }

    Column(modifier = modifier.padding(horizontal = spacingL())) {
        Row(Modifier.padding(vertical = spacingS()), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Mic, null, tint = BrandTeal, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (isSynced) {
                    stringResource(R.string.player_synced_lyrics)
                } else {
                    stringResource(R.string.player_lyrics)
                },
                color = NavUnselected,
                fontSize = textLabelS(),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                sortedLines.isNotEmpty() && isSynced -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(spacingXS()),
                        contentPadding = PaddingValues(vertical = spacingL()),
                    ) {
                        itemsIndexed(sortedLines, key = { idx, line -> "${line.timeMs}-$idx" }) { idx, line ->
                            val isActive = idx == activeIndex
                            val isPast = idx < activeIndex
                            Row(
                                modifier = Modifier.fillMaxWidth().height(
                                    IntrinsicSize.Max,
                                ).padding(vertical = spacingXS()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.width(adp(40f)).fillMaxHeight(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isActive) {
                                        Box(
                                            Modifier
                                                .width(adp(3f))
                                                .fillMaxHeight(0.6f)
                                                .clip(RoundedCornerShape(adp(1.5f)))
                                                .background(BrandTeal),
                                        )
                                    }
                                }
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    val textColor = when {
                                        isActive -> Color.White
                                        isPast -> Color(0xFF2E2E4A)
                                        else -> Color(0xFF4A4A6A)
                                    }
                                    val fontSize = if (isActive) asp(24f) else asp(18f)
                                    Text(
                                        // Already cleaned at parse time — no per-tick stripHtmlTags.
                                        text = line.text,
                                        color = textColor,
                                        fontSize = fontSize,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = (fontSize.value * 1.4).sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier
                                            .clickable { onSeekToMs(line.timeMs) },
                                    )
                                }
                                Spacer(Modifier.width(adp(40f)))
                            }
                        }
                    }
                }

                cleanedUnstructured != null && cleanedUnstructured.isNotBlank() -> {
                    val scrollState = rememberScrollState()
                    Text(
                        text = cleanedUnstructured,
                        color = Color(0xFFCCCCCC),
                        fontSize = textBodyL(),
                        lineHeight = asp(24f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .padding(vertical = spacingL()),
                    )
                }

                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isLoading) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = BrandTeal,
                                    modifier = Modifier.size(adp(32f)),
                                    strokeWidth = adp(3f),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.player_loading_lyrics),
                                    color = NavUnselected,
                                    fontSize = textBodyM(),
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    null,
                                    tint = Color(0xFF333333),
                                    modifier = Modifier.size(iconLarge()),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (artist != null && title != null) {
                                        stringResource(
                                            R.string.player_track_by_artist,
                                            title,
                                            artist,
                                        )
                                    } else {
                                        stringResource(R.string.player_no_track_playing)
                                    },
                                    color = NavUnselected,
                                    fontSize = textBodyM(),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.player_no_lyrics),
                                    color = NavUnselected,
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

/** Map device volume (0.0–1.0) to display slider value with cube-root curve. */
internal fun volumeToDisplay(deviceVolume: Float): Float {
    if (deviceVolume <= 0f) return 0f
    return kotlin.math.cbrt(deviceVolume.coerceIn(0f, 1f))
}

/** Map display slider value (0.0–1.0) to device volume with power-of-3 curve. */
internal fun displayToVolume(display: Float): Float = display.coerceIn(0f, 1f).let { it * it * it }

/** First-press display=0 while the thumb is not at 0. Mid-drag 0 is real. */
internal fun isSpuriousZeroDisplay(display: Float, baselineDisplay: Float): Boolean =
    display <= 0.001f && baselineDisplay > 0.05f

/** Delta send for the cast slider. */
internal fun volumeForDrag(display: Float, dragStartVolume: Float, dragStartDisplay: Float): Float {
    val delta = display - dragStartDisplay
    val deltaVolume = displayToVolume(dragStartDisplay + delta) - displayToVolume(dragStartDisplay)
    return (dragStartVolume + deltaVolume).coerceIn(0f, 1f)
}
