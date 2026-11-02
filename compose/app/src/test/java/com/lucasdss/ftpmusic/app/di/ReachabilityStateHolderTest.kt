package com.lucasdss.ftpmusic.app.di

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * N8 hysteresis. Interceptor failures need two failures inside the 10 s window
 * before the published boolean flips; a success recovers instantly. The
 * dedicated keepalive probe stays trusted and flips immediately.
 */
class ReachabilityStateHolderTest {

    @Before
    fun setUp() {
        ReachabilityStateHolder.resetForTests()
    }

    @After
    fun tearDown() {
        ReachabilityStateHolder.resetForTests()
    }

    @Test
    fun `initial state is reachable`() = runTest {
        assertEquals(true, ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `single interceptor failure does not flip the boolean`() = runTest {
        ReachabilityStateHolder.onApiFailure()
        assertEquals(true, ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `second interceptor failure inside the window flips to unreachable`() = runTest {
        ReachabilityStateHolder.onApiFailure()
        ReachabilityStateHolder.onApiFailure()
        assertEquals(false, ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `success between failures clears the run`() = runTest {
        ReachabilityStateHolder.onApiFailure()
        ReachabilityStateHolder.onApiSuccess()
        ReachabilityStateHolder.onApiFailure()
        assertEquals(true, ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `success recovers instantly from unreachable`() = runTest {
        ReachabilityStateHolder.onApiFailure()
        ReachabilityStateHolder.onApiFailure()
        assertEquals(false, ReachabilityStateHolder.isReachable.first())
        ReachabilityStateHolder.onApiSuccess()
        assertEquals(true, ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `probe failure is trusted and flips immediately`() = runTest {
        ReachabilityStateHolder.onProbeFailure()
        assertEquals(false, ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `probe success recovers and clears the failure run`() = runTest {
        ReachabilityStateHolder.onApiFailure()
        ReachabilityStateHolder.onProbeSuccess()
        assertEquals(true, ReachabilityStateHolder.isReachable.first())
        // The single interceptor failure before the probe must not count now.
        ReachabilityStateHolder.onApiFailure()
        assertEquals(true, ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `repeated failures while already unreachable stay unreachable`() = runTest {
        ReachabilityStateHolder.onProbeFailure()
        ReachabilityStateHolder.onApiFailure()
        ReachabilityStateHolder.onApiFailure()
        assertEquals(false, ReachabilityStateHolder.isReachable.first())
    }
}
