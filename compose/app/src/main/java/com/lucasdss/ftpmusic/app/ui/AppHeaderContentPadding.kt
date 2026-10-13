package com.lucasdss.ftpmusic.app.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

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

/**
 * Translate Y for fixed-inset tabs (Library / Favorites): chrome **and** list
 * share one `graphicsLayer` (ADR-0114). Pure for tests.
 */
fun appHeaderChromeTranslationY(offsetPx: Float): Float = -offsetPx

/**
 * Fixed over-measure extra (px) so riding content can fill the bottom band after
 * translate without per-frame Lazy remasure (ADR-0114 amendment / black-bar fix).
 * Pure for tests.
 */
fun appHeaderRidingExtraPx(maxOffsetPx: Float): Int = if (maxOffsetPx <= 0f) 0 else ceil(maxOffsetPx.toDouble()).toInt()

/**
 * Child measure height for [Modifier.appHeaderRidingContent]: parent viewport [constraintsMaxHeight]
 * plus fixed [maxOffsetPx]. Unbounded height unchanged. Pure for tests.
 */
fun appHeaderRidingMeasureHeight(constraintsMaxHeight: Int, maxOffsetPx: Float): Int {
    if (constraintsMaxHeight == Constraints.Infinity) return constraintsMaxHeight
    return constraintsMaxHeight + appHeaderRidingExtraPx(maxOffsetPx)
}

/**
 * Library/Favorites chrome+list ride: measure child at `H + maxOffsetPx` (fixed;
 * changes only when header pad changes), report layout `H`, paint-translate by
 * `-offsetPx`. Pair with parent `clipToBounds()` so offset=0 overhang never paints
 * over nav/mini. ADR-0107: Lazy size stable during fling (extra not tied to live offset).
 */
fun Modifier.appHeaderRidingContent(offsetPx: () -> Float, maxOffsetPx: Float): Modifier = this
    .layout { measurable, constraints ->
        val targetHeight = appHeaderRidingMeasureHeight(constraints.maxHeight, maxOffsetPx)
        val childConstraints =
            if (targetHeight == Constraints.Infinity) {
                constraints
            } else {
                constraints.copy(
                    minHeight = targetHeight,
                    maxHeight = targetHeight,
                )
            }
        val placeable = measurable.measure(childConstraints)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(0, 0)
        }
    }
    .graphicsLayer {
        translationY = appHeaderChromeTranslationY(offsetPx())
    }
