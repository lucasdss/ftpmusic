package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.ui.BrandTeal

/**
 * Pure gate — when scrolling, skip infinite EQ transitions (ADR-0102).
 * Unit-testable without Compose animation clocks.
 */
fun shouldAnimateEqBars(scrollInProgress: Boolean): Boolean = !scrollInProgress

/**
 * Teal EQ bars for active album/track overlays. Animates only when idle;
 * shows a static mid-height triad while [scrollInProgress] to avoid fling jank.
 */
@Composable
fun ScrollAwareEqBars(scrollInProgress: Boolean, modifier: Modifier = Modifier) {
    if (!shouldAnimateEqBars(scrollInProgress)) {
        StaticEqBars(modifier)
        return
    }
    val eqAnimation = rememberInfiniteTransition(label = "eqScrollAware")
    Row(
        modifier.width(24.dp).height(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val delays = listOf(0, 150, 300)
        val heights = listOf(0.5f, 0.7f, 1.0f)
        for (i in 0..2) {
            val anim by eqAnimation.animateFloat(
                initialValue = heights[i] * 0.3f,
                targetValue = heights[i],
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + delays[i], easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "eqScrollAware$i",
            )
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight(anim)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrandTeal),
            )
        }
    }
}

@Composable
private fun StaticEqBars(modifier: Modifier = Modifier) {
    Row(
        modifier.width(24.dp).height(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val heights = listOf(0.45f, 0.75f, 0.55f)
        for (h in heights) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrandTeal),
            )
        }
    }
}
