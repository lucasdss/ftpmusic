package com.lucasdss.ftpmusic.app.di

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReachabilityStateHolderTest {

    @Test
    fun `initial state is reachable`() = runTest {
        assertEquals(true, ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `onApiSuccess sets reachable to true`() = runTest {
        ReachabilityStateHolder.onApiFailure()
        ReachabilityStateHolder.onApiSuccess()
        assertEquals(true, ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `onApiFailure sets reachable to false`() = runTest {
        ReachabilityStateHolder.onApiFailure()
        assertEquals(false, ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `onApiSuccess when already true is no-op`() = runTest {
        ReachabilityStateHolder.onApiSuccess()
        assertEquals(true, ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `onApiFailure when already false is no-op`() = runTest {
        ReachabilityStateHolder.onApiFailure()
        ReachabilityStateHolder.onApiFailure()
        assertEquals(false, ReachabilityStateHolder.isReachable.first())
    }
}
