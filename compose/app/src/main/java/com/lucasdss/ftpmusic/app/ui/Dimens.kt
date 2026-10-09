package com.lucasdss.ftpmusic.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Adaptive design tokens that scale with screen width and user density
 * (ADR-0099 / ADR-0103).
 *
 * Reference width: 360dp (standard phone).
 * Width factor: **0.85–1.25×** (downscales narrow phones, caps tablets).
 * Density: Tiny…Bigger from [LocalTypographyPrefs].
 * Text tokens ([asp]) use width × density × role scale and still use `.sp`,
 * so system accessibility fontScale is honored. Constrained slots use FittingText.
 */
object AdaptiveScale {
    /** Pure width scale. Clamp [0.85 .. 1.25]. */
    fun widthFactor(screenWidthDp: Int): Float = (screenWidthDp / 360f).coerceIn(0.85f, 1.25f)

    /** Pure: width × UI density (icons/art/spacing + text base). */
    fun combinedFactor(widthFactor: Float, densityScale: Float): Float = widthFactor * densityScale

    /** Screen-width × density scale for spacing/icons/art tokens. */
    @Composable
    fun factor(): Float {
        val density = LocalTypographyPrefs.current.density.scale
        return combinedFactor(widthFactor(LocalConfiguration.current.screenWidthDp), density)
    }
}

// ── Adaptive dp / sp helpers ─────────────────────────────────────────────────

/** Scale a dp value to current screen width and UI density. */
@Composable
fun adp(base: Float): Dp = (base * AdaptiveScale.factor()).dp

/**
 * Scale a sp value to width × density × typography role scale.
 * System fontScale still applies via `.sp`.
 */
@Composable
fun asp(base: Float, role: TextRole = TextRole.Body): TextUnit {
    val prefs = LocalTypographyPrefs.current
    val roleScale = prefs.scaleFor(role)
    return (base * AdaptiveScale.factor() * roleScale).sp
}

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

@Composable fun textMicro(): TextUnit = asp(10f, TextRole.Label)

@Composable fun textLabelS(): TextUnit = asp(11f, TextRole.Label)

@Composable fun textLabelM(): TextUnit = asp(12f, TextRole.Label)

@Composable fun textLabelL(): TextUnit = asp(13f, TextRole.Label)

@Composable fun textBodyM(): TextUnit = asp(14f, TextRole.Body)

@Composable fun textBodyL(): TextUnit = asp(15f, TextRole.Body)

@Composable fun textHeadingS(): TextUnit = asp(16f, TextRole.Heading)

@Composable fun textHeadingM(): TextUnit = asp(18f, TextRole.Heading)

@Composable fun textHeadingL(): TextUnit = asp(20f, TextRole.Heading)

@Composable fun textDisplay(): TextUnit = asp(24f, TextRole.Heading)

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

/** Detail Play/Shuffle button height (Album + Artist action row). */
@Composable fun detailActionHeight(): Dp = adp(42f)

/** Album grid horizontal gap (Library + Artist Albums tab). */
@Composable fun gridGapH(): Dp = adp(10f)

/** Album grid vertical gap (Library + Artist Albums tab). */
@Composable fun gridGapV(): Dp = adp(14f)

/** Spacer between cover art and title on album cards. */
@Composable fun spacingBelowArt(): Dp = adp(6f)

/** Interactive reaction glyph (thumbs / bookmark) — market ~20dp. */
@Composable fun reactionGlyphSize(): Dp = adp(ReactionIconTokens.GLYPH_BASE_DP)

/** Visible circle hit for overlay/player reaction buttons — market ~40dp. */
@Composable fun reactionHitSize(): Dp = adp(ReactionIconTokens.HIT_BASE_DP)

/** Interactive 0–5★ glyph size (pair with minimumInteractiveComponentSize). */
@Composable fun ratingStarInteractiveSize(): Dp = adp(ReactionIconTokens.STAR_BASE_DP)
