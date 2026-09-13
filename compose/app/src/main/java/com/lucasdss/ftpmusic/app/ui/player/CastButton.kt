package com.lucasdss.ftpmusic.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.lucasdss.ftpmusic.app.ui.*

/**
 * Cast button — adaptively sized circle per design v4.
 * Idle: rgba(255,255,255,0.06) bg, rgba(255,255,255,0.08) border, #666 icon.
 * Active (casting): rgba(0,200,180,0.15) bg, rgba(0,200,180,0.30) border, #00c8b4 icon.
 * Tap → always opens Cast picker.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    if (!CastButtonState.isButtonVisible) return

    val isCasting = CastButtonState.isCasting.value

    Box(
        modifier = modifier
            .size(adp(36f))
            .clip(CircleShape)
            .background(
                if (isCasting) {
                    Color(0xFF00C8B4).copy(alpha = 0.15f)
                } else {
                    Color.White.copy(alpha = 0.06f)
                },
            )
            .border(
                width = adp(1f),
                color = if (isCasting) {
                    Color(0xFF00C8B4).copy(alpha = 0.30f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
                shape = CircleShape,
            )
            .clickable {
                CastButtonState.showDialog.value = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
            contentDescription = if (isCasting) "Disconnect Cast" else "Cast to device",
            tint = if (isCasting) Color(0xFF00C8B4) else Color(0xFF666666),
            modifier = Modifier.size(adp(15f)),
        )
    }
}
