package com.lucasdss.ftpmusic.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveSyncLimiterTest {

    @Test
    fun `starts at min concurrency and initial delay`() {
        val limiter = AdaptiveSyncLimiter()
        assertEquals(2, limiter.concurrency)
        assertEquals(200L, limiter.batchDelayMs)
    }

    @Test
    fun `raises concurrency after success streak`() {
        val limiter = AdaptiveSyncLimiter(successStreakToRaise = 2)
        limiter.onBatchSuccess()
        assertEquals(2, limiter.concurrency)
        limiter.onBatchSuccess()
        assertEquals(3, limiter.concurrency)
        limiter.onBatchSuccess()
        limiter.onBatchSuccess()
        assertEquals(4, limiter.concurrency)
        limiter.onBatchSuccess()
        limiter.onBatchSuccess()
        assertEquals(4, limiter.concurrency) // capped
    }

    @Test
    fun `failure drops concurrency and raises delay`() {
        val limiter = AdaptiveSyncLimiter(successStreakToRaise = 1)
        limiter.onBatchSuccess() // → concurrency 3, delay 150
        limiter.onBatchSuccess() // → concurrency 4, delay 100
        assertEquals(4, limiter.concurrency)
        assertEquals(100L, limiter.batchDelayMs)
        limiter.onBatchFailure()
        assertEquals(2, limiter.concurrency)
        assertEquals(250L, limiter.batchDelayMs) // 100 + 150
        limiter.onBatchFailure()
        assertEquals(400L, limiter.batchDelayMs)
        limiter.onBatchFailure()
        assertEquals(500L, limiter.batchDelayMs) // capped
    }

    @Test
    fun `reset restores defaults`() {
        val limiter = AdaptiveSyncLimiter(successStreakToRaise = 1)
        limiter.onBatchSuccess()
        limiter.onBatchFailure()
        limiter.reset()
        assertEquals(2, limiter.concurrency)
        assertEquals(200L, limiter.batchDelayMs)
    }
}
