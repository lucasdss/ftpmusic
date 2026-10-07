package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.BrandBg
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Foreground
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.OfflineYellow
import com.lucasdss.ftpmusic.app.ui.player.CastButton

/**
 * Shared persistent app header — primary tabs + settings route.
 *
 * Layout (left → right):
 * [ Logo | branding ]  [ Server unreachable? ]  [ Settings gear ]  [ Cast ]
 *
 * Settings lives here (not bottom nav) per ADR-0055. Search remains a bottom tab (ADR-0014).
 */
@Composable
fun AppHeader(
    modifier: Modifier = Modifier,
    settingsSelected: Boolean = false,
    /** False while collapsed — blocks ghost taps on clipped remnant (ADR 0097). */
    interactive: Boolean = true,
    onSettingsClick: (() -> Unit)? = null,
) {
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
                .background(BrandBg)
                .border(1.dp, BrandTeal.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .shadow(
                    elevation = spacingXS(),
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = BrandTeal,
                    spotColor = BrandTeal,
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
                color = Foreground,
                fontSize = textHeadingM(),
                fontWeight = FontWeight.Bold,
                fontFamily = outfitFontFamily(),
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = "Flow Tempo Pulse",
                color = BrandTeal,
                fontSize = textLabelM(),
                fontWeight = FontWeight.Medium,
                fontFamily = outfitFontFamily(),
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.weight(1f))

        // ── Server-unreachable badge (not Simulate Offline) ──
        if (!isReachable) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(OfflineYellow.copy(alpha = 0.12f))
                    .border(1.dp, OfflineYellow.copy(alpha = 0.30f), RoundedCornerShape(50))
                    .padding(horizontal = spacingS(), vertical = spacingXS()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    null,
                    tint = OfflineYellow,
                    modifier = Modifier.size(adp(10f)),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Server unreachable",
                    color = OfflineYellow,
                    fontSize = textMicro(),
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        if (onSettingsClick != null) {
            Box(
                Modifier
                    .size(minTouchTarget())
                    .clip(CircleShape)
                    .testTag("app_header_settings")
                    .semantics { contentDescription = "Settings" }
                    .clickable(enabled = interactive, onClick = onSettingsClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (settingsSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = if (settingsSelected) BrandTeal else NavUnselected,
                    modifier = Modifier.size(adp(22f)),
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        // ── Cast button ──
        CastButton()
    }
}
