package com.lucasdss.ftpmusic.app.playback

/**
 * Pure gate for Continuous Play continuation loading.
 * Extracted from [MediaService] so unit tests cover branch conditions without
 * the Android service lifecycle (ADR-0052).
 *
 * ADR-0093: Cast is allowed — [PlaybackManager.appendToContext] already mutates
 * Dual + Cast via `emitCastAddsOrCommit` (ADR-0111: single AddAll / one ack).
 * ADR-0095: While casting, timeline SoT is Dual (CastPlayer may be empty/windowed).
 */
object ContinuousPlayGate {

    /**
     * Pick index/count for the gate. Casting → Dual; local → active player.
     */
    fun resolveTimeline(
        isCasting: Boolean,
        playerIndex: Int,
        playerCount: Int,
        dualIndex: Int,
        dualCount: Int,
    ): Pair<Int, Int> = if (isCasting) {
        dualIndex to dualCount
    } else {
        playerIndex to playerCount
    }

    /**
     * Whether to load a journal-based continuation burst onto **context**.
     *
     * Requires: timeline on last item, flag not yet spent for this last-item
     * stint, and Continuous Play enabled. Works local and Cast (Dual SoT).
     */
    fun shouldLoadContinuation(
        isCasting: Boolean,
        currentIndex: Int,
        mediaItemCount: Int,
        hasLoadedContinuation: Boolean,
        continuousPlayEnabled: Boolean,
    ): Boolean {
        // isCasting retained for call-site / resolveTimeline pairing
        @Suppress("UNUSED_PARAMETER")
        val casting = isCasting
        if (!continuousPlayEnabled) return false
        if (hasLoadedContinuation) return false
        if (mediaItemCount <= 0) return false
        return currentIndex >= mediaItemCount - 1
    }

    /**
     * Reset continuation flag when the playing media id changes so a new
     * last-item stint can fire Continuous Play again.
     */
    fun shouldResetContinuation(previousTrackId: String?, mediaId: String): Boolean = mediaId != previousTrackId
}
