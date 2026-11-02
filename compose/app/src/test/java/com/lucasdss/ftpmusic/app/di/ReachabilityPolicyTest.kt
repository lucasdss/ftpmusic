package com.lucasdss.ftpmusic.app.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReachabilityPolicyTest {

    @Test
    fun `first failure starts a run without tripping`() {
        val (state, trip) = ReachabilityPolicyState().onFailure(nowMs = 1_000L)
        assertFalse(trip)
        assertEquals(1, state.consecutiveFailures)
        assertEquals(1_000L, state.firstFailureMs)
    }

    @Test
    fun `second failure inside the window trips`() {
        val first = ReachabilityPolicyState().onFailure(1_000L).first
        val (second, trip) = first.onFailure(1_500L)
        assertTrue(trip)
        assertEquals(2, second.consecutiveFailures)
        assertEquals(1_000L, second.firstFailureMs)
    }

    @Test
    fun `failure outside the window restarts the count`() {
        val first = ReachabilityPolicyState().onFailure(1_000L).first
        val (second, trip) = first.onFailure(
            1_000L + REACHABILITY_FAILURE_WINDOW_MS + 1,
        )
        assertFalse(trip)
        assertEquals(1, second.consecutiveFailures)
        assertEquals(1_000L + REACHABILITY_FAILURE_WINDOW_MS + 1, second.firstFailureMs)
    }

    @Test
    fun `window boundary is inclusive`() {
        val first = ReachabilityPolicyState().onFailure(1_000L).first
        val (_, trip) = first.onFailure(1_000L + REACHABILITY_FAILURE_WINDOW_MS)
        assertTrue(trip)
    }

    @Test
    fun `success clears the run`() {
        val state = ReachabilityPolicyState()
            .onFailure(1_000L).first
            .onFailure(1_100L).first
            .onSuccess()
        assertEquals(0, state.consecutiveFailures)
        assertFalse(state.onFailure(2_000L).second)
    }

    @Test
    fun `custom threshold is honoured`() {
        var state = ReachabilityPolicyState()
        var tripped = false
        repeat(3) { i ->
            val (next, trip) = state.onFailure(1_000L + i, threshold = 3)
            state = next
            tripped = trip
        }
        assertTrue(tripped)
    }
}
