package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.ui.*

/** Inline download status indicator — design v3 DownloadDot component. */
@Composable
fun DownloadDot(status: String, modifier: Modifier = Modifier) {
    when (status) {
        "downloaded" -> Icon(
            Icons.Filled.CheckCircle,
            "downloaded",
            tint = Color(0xFFB040E8),
            modifier = modifier.size(knobSize()),
        )

        "cached" -> Icon(Icons.Filled.Check, "cached", tint = Color(0xFF888888), modifier = modifier.size(knobSize()))

        "queued" -> Icon(
            Icons.Filled.HourglassEmpty,
            "queued",
            tint = Color(0xFF666666),
            modifier = modifier.size(knobSize()),
        )

        else -> { /* "none" — render nothing */ }
    }
}

/** Overlay badge on album art — design v3 AlbumDownloadBadge. */
@Composable
fun AlbumDownloadBadge(status: String, modifier: Modifier = Modifier) {
    when (status) {
        "downloaded" -> Box(
            modifier = modifier.size(iconSmall()).clip(CircleShape)
                .background(Color(0xB0, 0x40, 0xE8, 0xE6)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(knobSize()))
        }

        "cached" -> Box(
            modifier = modifier.size(iconSmall()).clip(CircleShape)
                .background(Color(0x1E, 0x1E, 0x30, 0xE0)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(knobSize()))
        }

        "queued" -> Box(
            modifier = modifier.size(iconSmall()).clip(CircleShape)
                .background(Color(0x1E, 0x1E, 0x30, 0xE0)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.HourglassEmpty, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(knobSize()))
        }

        else -> { /* "none" — render nothing */ }
    }
}

/** Determines download status string from cached/downloaded/queued booleans. */
fun downloadStatus(isDownloaded: Boolean, isQueued: Boolean, isCached: Boolean): String = when {
    isDownloaded -> "downloaded"
    isQueued -> "queued"
    isCached -> "cached"
    else -> "none"
}
