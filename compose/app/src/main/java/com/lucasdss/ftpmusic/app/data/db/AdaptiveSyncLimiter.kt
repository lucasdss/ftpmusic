package com.lucasdss.ftpmusic.app.data.db

/**
 * Adaptive pacing for album-track fetch batches during metadata sync (ADR-0045).
 * Starts conservative; raises concurrency on success streaks; backs off on failures.
 * Not user-configurable — keeps OkHttp host pool from melting Navidrome.
 */
class AdaptiveSyncLimiter(
    private val minConcurrency: Int = 2,
    private val maxConcurrency: Int = 4,
    private val minDelayMs: Long = 100L,
    private val maxDelayMs: Long = 500L,
    private val initialDelayMs: Long = 200L,
    private val successStreakToRaise: Int = 3,
) {
    var concurrency: Int = minConcurrency
        private set
    var batchDelayMs: Long = initialDelayMs
        private set

    private var successStreak: Int = 0

    /** Call after a batch where every fetch in the batch succeeded. */
    fun onBatchSuccess() {
        successStreak++
        if (successStreak >= successStreakToRaise) {
            successStreak = 0
            if (concurrency < maxConcurrency) concurrency++
            if (batchDelayMs > minDelayMs) {
                batchDelayMs = (batchDelayMs - 50L).coerceAtLeast(minDelayMs)
            }
        }
    }

    /** Call after a batch that saw one or more failed album fetches. */
    fun onBatchFailure() {
        successStreak = 0
        concurrency = minConcurrency
        batchDelayMs = (batchDelayMs + 150L).coerceAtMost(maxDelayMs)
    }

    fun reset() {
        concurrency = minConcurrency
        batchDelayMs = initialDelayMs
        successStreak = 0
    }
}
