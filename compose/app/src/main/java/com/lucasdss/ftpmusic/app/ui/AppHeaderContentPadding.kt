package com.lucasdss.ftpmusic.app.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Top inset for primary-tab Lazy lists under the overlay AppHeader (ADR-0107 hard-fix).
 *
 * Fixed to full header height — list viewport does not resize during enterAlways
 * collapse; content scrolls into the header band when chrome translates away.
 */
val LocalAppHeaderContentPadding = compositionLocalOf { 0.dp }

/**
 * Reader for AppHeader collapse offset (ADR-0114). Call [AppHeaderOffsetPx.offsetPx]
 * inside `graphicsLayer` so chrome rides the header without recomposing the list.
 */
fun interface AppHeaderOffsetPx {
    fun offsetPx(): Float
}

val ZeroAppHeaderOffsetPx = AppHeaderOffsetPx { 0f }

val LocalAppHeaderOffsetPx = staticCompositionLocalOf { ZeroAppHeaderOffsetPx }

/**
 * Pure helper for tests — same rule as shell CompositionLocalProvider.
 * [lastKnownHeight] bridges the first-frame gap before measure (`headerHeight == 0`).
 */
fun resolveAppHeaderContentPadding(showHeader: Boolean, headerHeight: Dp, lastKnownHeight: Dp = 0.dp): Dp {
    if (!showHeader) return 0.dp
    return when {
        headerHeight > 0.dp -> headerHeight
        lastKnownHeight > 0.dp -> lastKnownHeight
        else -> 0.dp
    }
}

/** Chrome translate Y for fixed-inset tabs (Library / Favorites). Pure for tests. */
fun appHeaderChromeTranslationY(offsetPx: Float): Float = -offsetPx
