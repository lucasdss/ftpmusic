package com.lucasdss.ftpmusic.app.playback

/**
 * Pure gate for Continuous Play continuation loading.
 * Extracted from [MediaService] so unit tests cover branch conditions without
 * the Android service lifecycle (ADR-0052).
 *
 * ADR-0093: Cast is allowed — [PlaybackManager.appendToContext] already mutates
 * Dual + Cast via `emitCastAddsOrCommit` (ADR-0111: single AddAll / one ack).
 * ADR-0095: While casting, timeline SoT is Dual (CastPlayer may be empty/windowed).
 *
 * Market timing (Spotify / YTM): the **first** Autoplay burst fires only when
 * the playhead is on the last user-selected (non-autoplay) row — never mid
 * album/playlist. After an Autoplay tail exists, radio may re-arm near the end
 * of that tail ([LOW_WATER_MARK]).
 */
object ContinuousPlayGate {

    /**
     * Prefetch lead for **Autoplay radio re-arm** only (after a tail exists).
     * First burst for finite user rows uses last-user-track only (remaining 0).
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
     * Whether to load a Continuous Play continuation burst onto **context**.
     *
     * Requires master [continuousPlayEnabled] and [sessionAutoplayEnabled]
     * (Queue kill switch). Uses [isAutoplayFlags] aligned with the timeline:
     * - On a user row: fire only when no non-autoplay rows remain after the
     *   playhead and no Autoplay tail is already queued (first burst).
     * - On an Autoplay row: fire when total remaining ≤ [lowWaterMark] (re-arm).
     * When [isAutoplayFlags] is null/mismatched, every row is treated as user
     * (first burst = last item only).
     */
    fun shouldLoadContinuation(
        isCasting: Boolean,
        currentIndex: Int,
        mediaItemCount: Int,
        hasLoadedContinuation: Boolean,
        continuousPlayEnabled: Boolean,
        sessionAutoplayEnabled: Boolean = continuousPlayEnabled,
        isAutoplayFlags: List<Boolean>? = null,
        lowWaterMark: Int = LOW_WATER_MARK,
    ): Boolean {
        // isCasting retained for call-site / resolveTimeline pairing
        @Suppress("UNUSED_PARAMETER")
        val casting = isCasting
        if (!continuousPlayEnabled || !sessionAutoplayEnabled) return false
        if (hasLoadedContinuation) return false
        if (mediaItemCount <= 0) return false
        // CP-14/CP-5 guard: a missing or windowed index (CastPlayer/Dual -1)
        // must fail closed instead of looking like "past the last item".
        if (currentIndex < 0 || currentIndex >= mediaItemCount) return false

        val flags = isAutoplayFlags?.takeIf { it.size == mediaItemCount }
        fun isAutoplayAt(index: Int): Boolean = flags?.getOrElse(index) { false } == true

        val totalRemaining = mediaItemCount - 1 - currentIndex
        val userRemaining = ((currentIndex + 1) until mediaItemCount).count { !isAutoplayAt(it) }
        val autoplayAfter = ((currentIndex + 1) until mediaItemCount).any { isAutoplayAt(it) }
        val currentIsAutoplay = isAutoplayAt(currentIndex)

        return if (currentIsAutoplay) {
            totalRemaining <= lowWaterMark.coerceAtLeast(0)
        } else {
            // First burst: last user-selected track only — never mid-album.
            userRemaining == 0 && !autoplayAfter
        }
    }

    /**
     * Reset continuation flag when the playing media id changes so a new
     * last-item stint can fire Continuous Play again.
     */
    fun shouldResetContinuation(previousTrackId: String?, mediaId: String): Boolean = mediaId != previousTrackId
}
