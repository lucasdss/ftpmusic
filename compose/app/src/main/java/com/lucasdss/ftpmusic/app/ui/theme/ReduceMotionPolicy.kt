package com.lucasdss.ftpmusic.app.ui.theme

/**
 * Pure policy for reduced-motion gating (player visual refresh).
 * System animator/transition scales of 0 mean "reduce"; Appearance can force reduce.
 */
object ReduceMotionPolicy {
    fun shouldReduce(
        animatorDurationScale: Float,
        transitionAnimationScale: Float = 1f,
        appearanceForceReduce: Boolean = false,
    ): Boolean {
        if (appearanceForceReduce) return true
        return animatorDurationScale == 0f || transitionAnimationScale == 0f
    }
}
