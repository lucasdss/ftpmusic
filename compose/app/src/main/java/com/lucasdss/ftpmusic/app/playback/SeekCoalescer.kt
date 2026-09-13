package com.lucasdss.ftpmusic.app.playback

/**
 * Serializes seek requests so overlapping fast-forward seeks never interleave
 * with a track transition. Rapid seeks are coalesced: while a seek is
 * in-flight, subsequent requests are deferred, and only the LATEST survives to
 * be applied once the current seek settles (position discontinuity / ready).
 *
 * This keeps the metadata/position state consistent: a seek that triggers an
 * end-of-track skip can't be followed by a stale-fraction seek landing in the
 * wrong track.
 *
 * Stuck-seek guard: `inProgress` is normally cleared by [onSettled] when the
 * player reports a position discontinuity. If that settle never arrives — the
 * seek was applied to a track with unknown duration, or while BUFFERING, or on
 * a radio/live stream where ExoPlayer never fires a discontinuity — the
 * coalescer would defer every later seek forever. [request] therefore force-
 * clears a stale in-flight seek after [stuckTimeoutMs], so the seek bar can
 * never die until the next track transition.
 */
class SeekCoalescer(
    /** Max age of an in-flight seek before a new request force-clears it. */
    private val stuckTimeoutMs: Long = 1500L,
) {

    private var inProgress = false
    private var inProgressSince = 0L
    private var pending: Float? = null

    /**
     * Request a seek to [fraction].
     * @return the fraction to apply NOW, or null if a seek is already in-flight
     *         (this request is coalesced and will be applied on the next settle).
     */
    fun request(fraction: Float): Float? {
        pending = fraction
        if (inProgress) {
            val stale = System.currentTimeMillis() - inProgressSince >= stuckTimeoutMs
            if (!stale) return null
            // Previous seek never settled (unknown-duration/buffering/live):
            // un-stick so this request can proceed.
            inProgress = false
        }
        inProgress = true
        inProgressSince = System.currentTimeMillis()
        pending = null
        return fraction
    }

    /**
     * Called when the current seek has settled. Returns the next coalesced
     * fraction to apply (or null when idle).
     */
    fun onSettled(): Float? {
        inProgress = false
        val next = pending
        pending = null
        return next
    }

    /** Force-abandon any in-flight/pending seek (player error, transition, teardown). */
    fun reset() {
        inProgress = false
        pending = null
    }

    /** True while a seek is in-flight (subsequent requests are deferred). */
    val isSeeking: Boolean get() = inProgress
}
