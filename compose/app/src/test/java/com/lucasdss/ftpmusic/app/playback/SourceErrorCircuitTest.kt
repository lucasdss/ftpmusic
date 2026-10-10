package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceErrorCircuitTest {

    @Test
    fun `first two errors do not trip`() {
        var state = SourceErrorCircuitState()
        val (s1, t1) = state.onSourceError(nowMs = 1_000L)
        assertFalse(t1)
        assertEquals(1, s1.consecutiveErrors)
        val (s2, t2) = s1.onSourceError(nowMs = 2_000L)
        assertFalse(t2)
        assertEquals(2, s2.consecutiveErrors)
    }

    @Test
    fun `third error within window trips circuit`() {
        var state = SourceErrorCircuitState()
        state = state.onSourceError(1_000L).first
        state = state.onSourceError(2_000L).first
        val (s3, tripped) = state.onSourceError(3_000L)
        assertTrue(tripped)
        assertEquals(3, s3.consecutiveErrors)
    }

    @Test
    fun `error after window resets count`() {
        var state = SourceErrorCircuitState()
        state = state.onSourceError(1_000L).first
        state = state.onSourceError(2_000L).first
        val (next, tripped) = state.onSourceError(1_000L + SOURCE_ERROR_CIRCUIT_WINDOW_MS + 1)
        assertFalse(tripped)
        assertEquals(1, next.consecutiveErrors)
    }

    @Test
    fun `successful play and user seek clear circuit`() {
        var state = SourceErrorCircuitState(consecutiveErrors = 2, windowStartMs = 100L)
        state = state.onSuccessfulPlay()
        assertEquals(0, state.consecutiveErrors)
        state = SourceErrorCircuitState(consecutiveErrors = 2, windowStartMs = 100L).onUserSeek()
        assertEquals(0, state.consecutiveErrors)
    }

    @Test
    fun `scrobble storm guard skips only ghost near-zero listens`() {
        assertFalse(shouldScrobbleAfterListen(0L))
        assertFalse(shouldScrobbleAfterListen(SCROBBLE_STORM_MAX_GHOST_MS))
        // Short real listens (e.g. 3s track past 60%) must still scrobble.
        assertTrue(shouldScrobbleAfterListen(SCROBBLE_STORM_MAX_GHOST_MS + 1))
        assertTrue(shouldScrobbleAfterListen(1_000L))
        assertTrue(shouldScrobbleAfterListen(4_999L))
        assertTrue(shouldScrobbleAfterListen(120_000L))
    }

    @Test
    fun `circuit clear after trip allows fresh window`() {
        var state = SourceErrorCircuitState()
        state = state.onSourceError(1_000L).first
        state = state.onSourceError(2_000L).first
        val (trippedState, tripped) = state.onSourceError(3_000L)
        assertTrue(tripped)
        // MediaService CIRCUIT_STOP path clears via onSuccessfulPlay (ADR-0112).
        val cleared = trippedState.onSuccessfulPlay()
        assertEquals(0, cleared.consecutiveErrors)
        val (again, tripAgain) = cleared.onSourceError(3_100L)
        assertFalse(tripAgain)
        assertEquals(1, again.consecutiveErrors)
    }
}
