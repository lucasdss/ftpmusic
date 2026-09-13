package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.player.CastButton

/**
 * Shared persistent app header — used across all 5 main tabs.
 *
 * Layout (left → right):
 * [ Logo 56×56 | "FTP Music" / "Flow Tempo Pulse" (2/3 width) ]  [ Offline badge? ]  [ Cast button ]
 */
@Composable
fun AppHeader(modifier: Modifier = Modifier) {
    val isReachable by ReachabilityStateHolder.isReachable.collectAsState()

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingS()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Logo ──
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF101018))
                .border(1.dp, Color(0xFF00C8B4).copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .shadow(
                    elevation = spacingXS(),
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color(0xFF00C8B4),
                    spotColor = Color(0xFF00C8B4),
                ),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.play_store_icon_512),
                contentDescription = "FTP Music",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.width(12.dp))

        // ── App name + tagline (2/3 of free space) ──
        Column(modifier = Modifier.weight(2f)) {
            Text(
                text = "FTP Music",
                color = Color(0xFFE8E8F0),
                fontSize = textHeadingM(),
                fontWeight = FontWeight.Bold,
                fontFamily = outfitFontFamily(),
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = "Flow Tempo Pulse",
                color = Color(0xFF00C8B4),
                fontSize = textLabelM(),
                fontWeight = FontWeight.Medium,
                fontFamily = outfitFontFamily(),
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.weight(1f))

        // ── Offline badge (conditional) ──
        if (!isReachable) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFFC800).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFFFFC800).copy(alpha = 0.30f), RoundedCornerShape(50))
                    .padding(horizontal = spacingS(), vertical = spacingXS()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    null,
                    tint = Color(0xFFFFC800),
                    modifier = Modifier.size(adp(10f)),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Offline",
                    color = Color(0xFFFFC800),
                    fontSize = textMicro(),
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        // ── Cast button ──
        CastButton()
    }
}
