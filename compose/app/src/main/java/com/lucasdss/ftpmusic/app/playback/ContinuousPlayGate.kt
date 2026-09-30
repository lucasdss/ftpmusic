package com.lucasdss.ftpmusic.app.playback

/**
 * Pure gate for Continuous Play continuation loading.
 * Extracted from [MediaService] so unit tests cover branch conditions without
 * the Android service lifecycle (ADR-0052).
 */
object ContinuousPlayGate {

    /**
     * Whether to load a journal-based continuation burst onto **context**.
     *
     * Requires: local (not Cast), player on last timeline item, flag not yet
     * spent for this last-item stint, and Continuous Play enabled.
     */
    fun shouldLoadContinuation(
        isCasting: Boolean,
        currentIndex: Int,
        mediaItemCount: Int,
        hasLoadedContinuation: Boolean,
        continuousPlayEnabled: Boolean,
    ): Boolean {
        if (isCasting) return false
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
