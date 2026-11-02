package com.lucasdss.ftpmusic.app.playback

/**
 * Serializes Continuous Play loads and preserves a `STATE_ENDED` force-retry
 * that arrives while another load is still in flight (CP-14).
 *
 * Without this, `maybeLoadContinuousPlay` returned early on
 * `continuousPlayInFlight` and the ENDED retry was silently dropped, so an
 * empty/failed burst left the queue ended with no second attempt.
 *
 * All transitions are synchronized because the load runs on an IO coroutine
 * while the ENDED callback can arrive on Main.
 */
internal class ContinuousPlayRetry {

    private val lock = Any()
    private var inFlight = false
    private var retryPending = false

    /**
     * Returns true when the caller may start a load. If one is already
     * running, a [forceRetry] request is remembered and the caller must
     * return; the running load will schedule one retry when it finishes.
     */
    fun tryBegin(forceRetry: Boolean): Boolean = synchronized(lock) {
        if (inFlight) {
            if (forceRetry) retryPending = true
            false
        } else {
            inFlight = true
            retryPending = false
            true
        }
    }

    /**
     * Clears the in-flight latch and reports whether a deferred ENDED retry
     * should run. A successful append already extended the queue, so it
     * satisfies the queued retry. [canRetry] is false when the coroutine was
     * cancelled, which must not schedule more work.
     */
    fun finish(appended: Boolean, canRetry: Boolean = true): Boolean = synchronized(lock) {
        inFlight = false
        val retry = retryPending
        retryPending = false
        retry && !appended && canRetry
    }

    val isInFlight: Boolean
        get() = synchronized(lock) { inFlight }
}
