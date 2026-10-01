package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.lucasdss.ftpmusic.app.ui.adp
import com.lucasdss.ftpmusic.app.ui.iconSmall
import com.lucasdss.ftpmusic.app.ui.spacingL

/** Material minimum touch target (dp) for back / gear chrome (ADR-0056). */
const val MIN_TOUCH_TARGET_DP = 48f

@Composable
fun minTouchTarget(): Dp = adp(MIN_TOUCH_TARGET_DP)

/**
 * Canonical floating back control for detail screens (album/artist/playlist/…).
 * Circle + ChevronLeft — same affordance everywhere AppHeader is hidden.
 *
 * Outer hit box is ≥48dp (Material). Visual disc may match the hit box.
 *
 * @param inset when true (default), applies top/start [spacingL] for overlay on heroes.
 *              Set false when embedding in a TopAppBar / custom title row.
 */
@Composable
fun DetailBackButton(onBack: () -> Unit, modifier: Modifier = Modifier, inset: Boolean = true) {
    val base = if (inset) {
        modifier.padding(top = spacingL(), start = spacingL())
    } else {
        modifier
    }
    Box(
        base
            .size(minTouchTarget())
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .testTag("detail_back_button")
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.ChevronLeft,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(iconSmall()),
        )
    }
}
