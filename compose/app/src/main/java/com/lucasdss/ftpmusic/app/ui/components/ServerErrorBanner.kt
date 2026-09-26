package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder

/**
 * Warning banner for server problems. Two sources:
 * - [configWarning]: the configured URL points at the device itself
 *   (127.0.0.1 / localhost) — almost always a stale dev proxy (the old
 *   `http://127.0.0.1:9999` phone proxy). Explicit "enter your real server URL"
 *   prompt — never a block (adb-reverse dev setups are legitimate).
 * - Otherwise: [ReachabilityStateHolder.isReachable] flipped false by the API
 *   layer on connect failures/timeouts/DNS errors.
 *
 * Suppressed while [isOffline] (intentional offline mode must not nag) and
 * dismissible per state change.
 */
@Composable
fun ServerErrorBanner(
    configWarning: Boolean,
    isOffline: Boolean,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isReachable by ReachabilityStateHolder.isReachable.collectAsState()
    var dismissed by remember(configWarning, isReachable) { mutableStateOf(false) }
    if (isOffline || dismissed || (isReachable && !configWarning)) return

    val message = if (configWarning) {
        val shown = DynamicBaseUrl.url.trimEnd('/').ifBlank { "device-local address" }
        "Your server setting points to \"$shown\" — a device-local address from an old proxy setup. Enter your real server URL."
    } else {
        "Server unreachable — phone may have data, but can't reach your music server. Cached tracks still play."
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFFC800).copy(alpha = 0.10f))
            .border(1.dp, Color(0xFFFFC800).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, null, tint = Color(0xFFFFC800), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            message,
            color = Color(0xFFE8C766),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Fix",
            color = Color(0xFF00C8B4),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { onOpenServerSettings() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.Close,
            "Dismiss",
            tint = Color(0xFF888888),
            modifier = Modifier
                .size(16.dp)
                .clickable { dismissed = true },
        )
    }
}
