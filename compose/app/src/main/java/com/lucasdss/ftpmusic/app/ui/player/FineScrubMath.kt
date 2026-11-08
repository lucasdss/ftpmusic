package com.lucasdss.ftpmusic.app.ui.player

import kotlin.math.abs
import kotlin.math.exp

/**
 * Fine-scrub mapping: vertical pull away from the waveform reduces horizontal
 * seek sensitivity (iOS-style). Pure math — no Compose.
 */
object FineScrubMath {
    /** Pixels of downward pull at which sensitivity reaches ~half. */
    const val HALF_SPEED_PULL_PX = 80f

    /** Below this vertical pull, treat as pure horizontal scrub. */
    const val AXIS_LOCK_THRESHOLD_PX = 24f

    /**
     * Sensitivity in [0.05, 1]: 1 at the bar, lower as [verticalPullPx] grows.
     */
    fun sensitivity(verticalPullPx: Float): Float {
        val pull = verticalPullPx.coerceAtLeast(0f)
        // Exponential decay so pull ≈ HALF_SPEED_PULL_PX → ~0.5
        val s = exp(-0.693147f * pull / HALF_SPEED_PULL_PX)
        return s.coerceIn(0.05f, 1f)
    }

    /**
     * Apply a horizontal drag delta to [currentFraction].
     * [trackWidthPx] must be > 0.
     */
    fun applyHorizontalDelta(
        currentFraction: Float,
        deltaXPx: Float,
        trackWidthPx: Float,
        verticalPullPx: Float,
    ): Float {
        if (trackWidthPx <= 0f) return currentFraction.coerceIn(0f, 1f)
        val sens = sensitivity(verticalPullPx)
        val deltaFrac = (deltaXPx / trackWidthPx) * sens
        return (currentFraction + deltaFrac).coerceIn(0f, 1f)
    }

    /**
     * True when vertical motion should yield to the queue sheet / parent
     * (axis lock: stay horizontal-primary until threshold).
     */
    fun shouldYieldVertical(verticalPullPx: Float, horizontalAccumPx: Float): Boolean {
        if (abs(horizontalAccumPx) > abs(verticalPullPx)) return false
        return verticalPullPx >= AXIS_LOCK_THRESHOLD_PX
    }

    fun fractionFromTapX(xPx: Float, startXPx: Float, stepPx: Float, barCount: Int): Float {
        if (barCount <= 0 || stepPx <= 0f) return 0f
        val index = ((xPx - startXPx) / stepPx).toInt().coerceIn(0, barCount - 1)
        return (index.toFloat() / barCount).coerceIn(0f, 1f)
    }
}
