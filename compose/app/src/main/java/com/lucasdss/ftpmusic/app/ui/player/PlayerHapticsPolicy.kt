package com.lucasdss.ftpmusic.app.ui.player

/**
 * Pure haptic decision policy for player scrub / transport.
 * UI fires LocalHapticFeedback when [shouldFire] is true — no VIBRATE permission.
 */
object PlayerHapticsPolicy {
    enum class Kind { Selection, ImpactLight, ImpactHeavy }

    fun enabled(
        appearanceEnabled: Boolean,
        reduceMotion: Boolean,
        hasVibrator: Boolean,
    ): Boolean = appearanceEnabled && !reduceMotion && hasVibrator

    /** Selection tick when playhead crosses an integer minute while scrubbing. */
    fun minuteCrossingKind(prevFraction: Float, nextFraction: Float, durationMs: Long): Kind? {
        if (durationMs <= 0L) return null
        val prevMin = ((prevFraction.coerceIn(0f, 1f) * durationMs) / 60_000L).toInt()
        val nextMin = ((nextFraction.coerceIn(0f, 1f) * durationMs) / 60_000L).toInt()
        return if (nextMin != prevMin) Kind.Selection else null
    }

    /** Heavy impact when scrub hits track start or end. */
    fun boundaryKind(fraction: Float): Kind? = when {
        fraction <= 0.001f || fraction >= 0.999f -> Kind.ImpactHeavy
        else -> null
    }

    fun playPauseKind(): Kind = Kind.ImpactLight

    fun shouldFire(
        appearanceEnabled: Boolean,
        reduceMotion: Boolean,
        hasVibrator: Boolean,
        kind: Kind?,
    ): Boolean = kind != null && enabled(appearanceEnabled, reduceMotion, hasVibrator)
}
