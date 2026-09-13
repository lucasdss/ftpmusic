package com.lucasdss.ftpmusic.app.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.ui.*

/**
 * @deprecated Replaced by unified [PlayerBar] composable. Cast controls are now built into
 *             PlayerBar which shows playback controls for both local and Cast playback.
 */
@Deprecated(
    "Use PlayerBar composable instead",
    ReplaceWith("PlayerBar(...)", "com.lucasdss.ftpmusic.app.ui.player.PlayerBar"),
)
@Composable
fun CastMiniController(
    deviceName: String,
    trackTitle: String?,
    trackArtist: String?,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onDisconnect: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Track info row
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.CastConnected,
                    "Casting",
                    modifier = Modifier.size(36.dp).padding(6.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = trackTitle ?: "Connected",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Casting to $deviceName",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDisconnect) {
                    Icon(Icons.Default.Cast, "Disconnect")
                }
                Spacer(Modifier.width(4.dp))
            }
            // Volume slider row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacingM(), vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Cast volume",
                    modifier = Modifier.size(iconSmall()),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = volumeToDisplay(volume),
                    onValueChange = { onVolumeChange(displayToVolume(it)) },
                    modifier = Modifier.weight(1f).padding(horizontal = spacingS()),
                    valueRange = 0f..1f,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
