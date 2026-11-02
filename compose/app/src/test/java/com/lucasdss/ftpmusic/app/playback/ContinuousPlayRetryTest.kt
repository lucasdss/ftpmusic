package com.lucasdss.ftpmusic.app.playback

import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ContinuousPlayRetryTest {

    @Test
    fun `starts a load when idle`() {
        val retry = ContinuousPlayRetry()
        assertTrue(retry.tryBegin(forceRetry = false))
        assertTrue(retry.isInFlight)
    }

    @Test
    fun `drops a second non-forced request while in flight`() {
        val retry = ContinuousPlayRetry()
        assertTrue(retry.tryBegin(forceRetry = false))
        assertFalse(retry.tryBegin(forceRetry = false))
        assertTrue(retry.isInFlight)
        assertFalse(retry.finish(appended = false))
    }

    @Test
    fun `STATE_ENDED while in flight schedules exactly one retry`() {
        val retry = ContinuousPlayRetry()
        assertTrue(retry.tryBegin(forceRetry = false))
        // STATE_ENDED arrives while the prefetch is running.
        assertFalse(retry.tryBegin(forceRetry = true))
        assertTrue(retry.finish(appended = false))
        // The retry was consumed; a later finish/ENDED must not loop.
        assertFalse(retry.finish(appended = false))
        assertFalse(retry.isInFlight)
    }

    @Test
    fun `an append satisfies the queued STATE_ENDED retry`() {
        val retry = ContinuousPlayRetry()
        assertTrue(retry.tryBegin(forceRetry = false))
        assertFalse(retry.tryBegin(forceRetry = true))
        assertFalse(retry.finish(appended = true))
        assertFalse(retry.finish(appended = false))
    }

    @Test
    fun `cancelled load does not schedule a retry`() {
        val retry = ContinuousPlayRetry()
        assertTrue(retry.tryBegin(forceRetry = false))
        assertFalse(retry.tryBegin(forceRetry = true))
        assertFalse(retry.finish(appended = false, canRetry = false))
        assertFalse(retry.isInFlight)
    }

    @Test
    fun `STATE_ENDED after the load cleared starts a fresh load`() {
        val retry = ContinuousPlayRetry()
        assertTrue(retry.tryBegin(forceRetry = false))
        assertFalse(retry.finish(appended = false))
        // No load is running, so forceRetry must begin immediately.
        assertTrue(retry.tryBegin(forceRetry = true))
        assertTrue(retry.isInFlight)
    }

    @Test
    fun `can start another load after finish`() {
        val retry = ContinuousPlayRetry()
        assertTrue(retry.tryBegin(forceRetry = false))
        retry.finish(appended = true)
        assertFalse(retry.isInFlight)
        assertTrue(retry.tryBegin(forceRetry = false))
    }

    @Test
    fun `a cancelled coroutine computes canRetry false`() = runBlocking {
        val retry = ContinuousPlayRetry()
        assertTrue(retry.tryBegin(forceRetry = false))
        assertFalse(retry.tryBegin(forceRetry = true))
        var canRetryInFinally = true
        val job = launch {
            try {
                awaitCancellation()
            } finally {
                canRetryInFinally = coroutineContext[Job]?.isCancelled != true
                assertFalse(retry.finish(appended = false, canRetry = canRetryInFinally))
            }
        }
        yield() // let the child enter awaitCancellation before cancelling
        job.cancelAndJoin()
        assertFalse(canRetryInFinally)
        assertFalse(retry.isInFlight)
    }

    @Test
    fun `a normally completed coroutine computes canRetry true`() = runBlocking {
        var canRetryInFinally = false
        launch {
            try {
                // no-op
            } finally {
                canRetryInFinally = coroutineContext[Job]?.isCancelled != true
            }
        }.join()
        assertTrue(canRetryInFinally)
    }
}
