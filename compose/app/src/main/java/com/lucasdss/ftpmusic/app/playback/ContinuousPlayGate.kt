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
     * Start prefetching when this many tracks (or fewer) remain after the
     * current one. A 2–3 track lead covers a slow similar-songs round trip
     * without waiting for `STATE_ENDED` and the audible gap (CP-5).
     */
    const val LOW_WATER_MARK = 3

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
     * Fires when at most [lowWaterMark] tracks remain after the current one
     * (including the last item and a one-item queue), the flag is not yet
     * spent for this stint, and Continuous Play is enabled. Prefetching a few
     * tracks early gives the similar-songs/journal fetch time to finish before
     * playback ends (CP-5); `STATE_ENDED` remains the last-chance retry.
     * Works local and Cast (Dual SoT).
     */
    fun shouldLoadContinuation(
        isCasting: Boolean,
        currentIndex: Int,
        mediaItemCount: Int,
        hasLoadedContinuation: Boolean,
        continuousPlayEnabled: Boolean,
        lowWaterMark: Int = LOW_WATER_MARK,
    ): Boolean {
        // isCasting retained for call-site / resolveTimeline pairing
        @Suppress("UNUSED_PARAMETER")
        val casting = isCasting
        if (!continuousPlayEnabled) return false
        if (hasLoadedContinuation) return false
        if (mediaItemCount <= 0) return false
        // CP-14/CP-5 guard: a missing or windowed index (CastPlayer/Dual -1)
        // must fail closed instead of looking like "past the last item".
        if (currentIndex < 0 || currentIndex >= mediaItemCount) return false
        val remaining = mediaItemCount - 1 - currentIndex
        return remaining <= lowWaterMark.coerceAtLeast(0)
    }

    /**
     * Reset continuation flag when the playing media id changes so a new
     * last-item stint can fire Continuous Play again.
     */
    fun shouldResetContinuation(previousTrackId: String?, mediaId: String): Boolean = mediaId != previousTrackId
}
