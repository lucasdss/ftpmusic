package com.lucasdss.ftpmusic.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.library.CoverArtColors

/**
 * UI state for the MiniPlayer, derived from PlaybackState.
 */
data class MiniPlayerUiState(val position: Long = 0L, val duration: Long = 0L) {
    val progressFraction: Float
        get() = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
}

fun formatTimeMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

/**
 * @deprecated Replaced by unified [PlayerBar] composable. MiniPlayer and CastMiniController
 *             have been consolidated into PlayerBar which adapts to [isCasting] automatically.
 *             The data class [MiniPlayerUiState] and utility function [formatTimeMs] are kept
 *             for tests until they can be migrated.
 */
@Deprecated(
    "Use PlayerBar composable instead",
    ReplaceWith("PlayerBar(...)", "com.lucasdss.ftpmusic.app.ui.player.PlayerBar"),
)
@Composable
fun MiniPlayer(
    title: String?,
    subtitle: String?,
    isPlaying: Boolean,
    position: Long = 0L,
    duration: Long = 0L,
    coverArtUrl: String? = null,
    colors: CoverArtColors = CoverArtColors(),
    onPlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit,
    showSkipControls: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val uiState = MiniPlayerUiState(position = position, duration = duration)

    AnimatedVisibility(visible = title != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(
                    brush = if (colors.hasColors) {
                        Brush.verticalGradient(listOf(colors.darkMuted, colors.vibrant))
                    } else {
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant),
                        )
                    },
                ),
        ) {
            Column {
                // Progress bar at top
                if (uiState.duration > 0) {
                    LinearProgressIndicator(
                        progress = { uiState.progressFraction },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                // Main content
                Row(
                    modifier = Modifier.fillMaxWidth().height(miniPlayerHeight()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(4.dp))

                    // Album art thumbnail or music note placeholder
                    if (coverArtUrl != null) {
                        AsyncImage(
                            model = coverArtUrl,
                            contentDescription = "Album art",
                            modifier = Modifier
                                .size(iconLarge())
                                .padding(4.dp)
                                .clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            null,
                            modifier = Modifier.size(iconLarge()).padding(8.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = title ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (showSkipControls) {
                        IconButton(onClick = onSkipPrev) {
                            Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(iconSmall()))
                        }
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                        )
                    }
                    if (showSkipControls) {
                        IconButton(onClick = onSkipNext) {
                            Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(iconSmall()))
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}
