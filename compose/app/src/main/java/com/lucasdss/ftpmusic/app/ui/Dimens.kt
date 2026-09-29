package com.lucasdss.ftpmusic.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Adaptive design tokens that scale with screen width.
 *
 * Reference width: 360dp (standard phone).
 * Width factor: **0.85–1.25×** (downscales narrow phones, caps tablets).
 * Text tokens ([asp]) use width factor only and still use `.sp`, so system
 * accessibility fontScale is honored. Constrained slots use FittingText.
 */
object AdaptiveScale {
    /** Pure width scale. Clamp [0.85 .. 1.25]. */
    fun widthFactor(screenWidthDp: Int): Float = (screenWidthDp / 360f).coerceIn(0.85f, 1.25f)

    /** Screen-width scale factor for spacing/icons/text tokens. */
    @Composable
    fun factor(): Float = widthFactor(LocalConfiguration.current.screenWidthDp)
}

// ── Adaptive dp / sp helpers ─────────────────────────────────────────────────

/** Scale a dp value to current screen width. */
@Composable
fun adp(base: Float): Dp = (base * AdaptiveScale.factor()).dp

/** Scale a sp value to current screen width. System fontScale still applies via `.sp`. */
@Composable
fun asp(base: Float): TextUnit = (base * AdaptiveScale.factor()).sp

// ── Spacing tokens ───────────────────────────────────────────────────────────

@Composable fun spacingXS(): Dp = adp(4f)

@Composable fun spacingS(): Dp = adp(8f)

@Composable fun spacingM(): Dp = adp(12f)

@Composable fun spacingL(): Dp = adp(16f)

@Composable fun spacingXL(): Dp = adp(20f)

@Composable fun spacing2XL(): Dp = adp(24f)

@Composable fun spacing3XL(): Dp = adp(32f)

// ── Icon size tokens ─────────────────────────────────────────────────────────

@Composable fun iconMicro(): Dp = adp(12f)

@Composable fun iconSmall(): Dp = adp(20f)

@Composable fun iconMedium(): Dp = adp(28f)

@Composable fun iconLarge(): Dp = adp(48f)

// ── Text size tokens ─────────────────────────────────────────────────────────

@Composable fun textMicro(): TextUnit = asp(10f)

@Composable fun textLabelS(): TextUnit = asp(11f)

@Composable fun textLabelM(): TextUnit = asp(12f)

@Composable fun textLabelL(): TextUnit = asp(13f)

@Composable fun textBodyM(): TextUnit = asp(14f)

@Composable fun textBodyL(): TextUnit = asp(15f)

@Composable fun textHeadingS(): TextUnit = asp(16f)

@Composable fun textHeadingM(): TextUnit = asp(18f)

@Composable fun textHeadingL(): TextUnit = asp(20f)

@Composable fun textDisplay(): TextUnit = asp(24f)

// ── Corner radius tokens ─────────────────────────────────────────────────────

@Composable fun cornerS(): Dp = adp(8f)

@Composable fun cornerM(): Dp = adp(12f)

@Composable fun cornerL(): Dp = adp(24f)

// ── Component size tokens ────────────────────────────────────────────────────

@Composable fun albumCardWidth(): Dp = adp(144f)

@Composable fun heroHeaderHeight(): Dp = adp(260f)

@Composable fun coverArtSize(): Dp = adp(200f)

@Composable fun miniPlayerHeight(): Dp = adp(64f)

@Composable fun playButtonSize(): Dp = adp(64f)

@Composable fun trackRowHeight(): Dp = adp(56f)

@Composable fun chipHeight(): Dp = adp(36f)

@Composable fun genreCardHeight(): Dp = adp(64f)

@Composable fun progressBarThick(): Dp = adp(4f)

@Composable fun progressBarThin(): Dp = adp(2f)

@Composable fun dividerThickness(): Dp = adp(1f)

@Composable fun knobSize(): Dp = adp(14f)
