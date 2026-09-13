package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasdss.ftpmusic.app.ui.*

/**
 * Overwrite protection dialog per design spec.
 * Shown when user plays a new album/playlist while priority queue has items.
 *
 * Design spec: ~/workspace/ftpmusic-design/src/app/App.tsx lines 219-249
 *
 * - "Clear & Play": teal→purple gradient button — clears priority queue, starts new context
 * - "Keep Queue": grey #252538 button — aborts context start, keeps priority queue intact
 */
@Composable
fun OverwriteModal(contextName: String, onKeepQueue: () -> Unit, onClearAndPlay: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = spacing2XL())
                .clip(RoundedCornerShape(cornerL()))
                .background(Color(0xFF1C1C2E))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(cornerL()))
                .padding(spacing2XL()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.QueueMusic,
                contentDescription = null,
                tint = Color(0xFFB040E8),
                modifier = Modifier.size(18.dp),
            )

            Spacer(Modifier.height(spacingS()))

            Text(
                "Tracks in your queue",
                color = Color.White,
                fontSize = textHeadingS(),
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(spacingXS()))

            Text(
                "You have tracks in your Priority Queue from \"$contextName\". Do you want to clear them and play this, or keep them?",
                color = Color(0xFF888888),
                fontSize = textBodyM(),
                textAlign = TextAlign.Center,
                lineHeight = asp(20f),
            )

            Spacer(Modifier.height(spacingL()))

            // Keep Queue button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerM()))
                    .background(Color(0xFF252538))
                    .clickable { onKeepQueue() }
                    .padding(vertical = spacingM()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Keep Queue",
                    color = Color.White,
                    fontSize = textBodyM(),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(spacingS()))

            // Clear & Play button — teal→purple gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerM()))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF00C8B4), Color(0xFFB040E8)),
                        ),
                    )
                    .clickable { onClearAndPlay() }
                    .padding(vertical = spacingM()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Clear & Play",
                    color = Color.White,
                    fontSize = textBodyM(),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
