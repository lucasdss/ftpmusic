package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Narrow coverage for OS-network lost wiring (ADR 0051). Kept separate from
 * [ServerReachabilityMonitorTest] so jacoco heap pressure does not cascade.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerReachabilityMonitorNetworkLostTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeWatcher: CapturingWatcher
    private lateinit var monitor: ServerReachabilityMonitor

    @Before
    fun setUp() {
        NetworkAvailabilityHolder.resetForTests(true)
        fakeWatcher = CapturingWatcher()
        monitor = ServerReachabilityMonitor(
            api = mockk(relaxed = true),
            offlineModeManager = mockk<OfflineModeManager>(relaxed = true),
            networkWatcher = fakeWatcher,
            dispatcher = testDispatcher,
            clockMs = { 0L },
        )
    }

    @After
    fun tearDown() {
        if (::monitor.isInitialized) monitor.stop()
        NetworkAvailabilityHolder.resetForTests(true)
    }

    @Test
    fun `start wires onLost to NetworkAvailabilityHolder`() = runTest(testDispatcher) {
        monitor.start()
        try {
            assertTrue(NetworkAvailabilityHolder.hasOsNetwork.value)
            fakeWatcher.fireLost()
            assertFalse(NetworkAvailabilityHolder.hasOsNetwork.value)
            // setAvailable(true) only — skip fireAvailable (would launch keepalive probe)
            NetworkAvailabilityHolder.setAvailable(true)
            assertTrue(NetworkAvailabilityHolder.hasOsNetwork.value)
        } finally {
            // Must stop before runTest drains the infinite keepalive job (OOM otherwise).
            monitor.stop()
        }
    }

    private class CapturingWatcher : NetworkWatcher {
        private var onAvailable: (() -> Unit)? = null
        private var onLost: (() -> Unit)? = null

        override fun start(onAvailable: () -> Unit, onLost: () -> Unit) {
            this.onAvailable = onAvailable
            this.onLost = onLost
        }

        override fun stop() {
            onAvailable = null
            onLost = null
        }

        fun fireAvailable() = onAvailable?.invoke()
        fun fireLost() = onLost?.invoke()
    }
}
