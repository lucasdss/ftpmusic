package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerReachabilityMonitorTest {

    private val api: SubsonicApi = mockk()
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private var clock = 0L
    private lateinit var fakeWatcher: FakeNetworkWatcher
    private lateinit var monitor: ServerReachabilityMonitor

    @Before
    fun setUp() {
        ReachabilityStateHolder.onApiSuccess()
        DynamicBaseUrl.url = "https://music.example.com"
        SubsonicCredentials.username = "user"
        SubsonicCredentials.password = "pass"
        every { offlineModeManager.isOfflineEnabled() } returns false
        fakeWatcher = FakeNetworkWatcher()
        clock = 0L
        monitor = ServerReachabilityMonitor(
            api = api,
            offlineModeManager = offlineModeManager,
            networkWatcher = fakeWatcher,
            dispatcher = testDispatcher,
            clockMs = { clock },
        )
    }

    @After
    fun tearDown() {
        monitor.stop()
        ReachabilityStateHolder.onApiSuccess()
        DynamicBaseUrl.url = ""
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
    }

    @Test
    fun `probeOnce success marks reachable`() = runTest(testDispatcher) {
        ReachabilityStateHolder.onApiFailure()
        coEvery { api.ping(any(), any(), any()) } returns mapOf("status" to "ok")

        assertTrue(monitor.probeOnce())
        assertTrue(ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `probeOnce failure marks unreachable`() = runTest(testDispatcher) {
        coEvery { api.ping(any(), any(), any()) } throws IOException("down")

        assertFalse(monitor.probeOnce())
        assertFalse(ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `probeOnce skips when offline mode enabled`() = runTest(testDispatcher) {
        every { offlineModeManager.isOfflineEnabled() } returns true
        ReachabilityStateHolder.onApiFailure()

        assertFalse(monitor.probeOnce())
        coVerify(exactly = 0) { api.ping(any(), any(), any()) }
        // State unchanged — intentional Simulate Offline must not be poked
        assertFalse(ReachabilityStateHolder.isReachable.first())
    }

    @Test
    fun `probeOnce skips when server unconfigured`() = runTest(testDispatcher) {
        DynamicBaseUrl.url = ""
        assertFalse(monitor.probeOnce())
        coVerify(exactly = 0) { api.ping(any(), any(), any()) }
    }

    @Test
    fun `probeOnce skips when credentials blank`() = runTest(testDispatcher) {
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
        assertFalse(monitor.probeOnce())
        coVerify(exactly = 0) { api.ping(any(), any(), any()) }
    }

    @Test
    fun `currentIntervalMs is 15s when unreachable`() {
        ReachabilityStateHolder.onApiFailure()
        assertEquals(
            ServerReachabilityMonitor.INTERVAL_UNREACHABLE_MS,
            monitor.currentIntervalMs(),
        )
    }

    @Test
    fun `currentIntervalMs is 60s when reachable`() {
        ReachabilityStateHolder.onApiSuccess()
        assertEquals(
            ServerReachabilityMonitor.INTERVAL_REACHABLE_MS,
            monitor.currentIntervalMs(),
        )
    }

    @Test
    fun `keepalive probes every 15s while unreachable`() = runTest(testDispatcher) {
        ReachabilityStateHolder.onApiFailure()
        coEvery { api.ping(any(), any(), any()) } throws IOException("still down")

        monitor.start()
        // Never advanceUntilIdle — keepalive loop is infinite and would hang.
        advanceTimeBy(ServerReachabilityMonitor.INTERVAL_UNREACHABLE_MS - 1)
        runCurrent()
        coVerify(exactly = 0) { api.ping(any(), any(), any()) }

        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 1) { api.ping(any(), any(), any()) }

        advanceTimeBy(ServerReachabilityMonitor.INTERVAL_UNREACHABLE_MS)
        runCurrent()
        coVerify(exactly = 2) { api.ping(any(), any(), any()) }
        monitor.stop()
    }

    @Test
    fun `keepalive recovers to reachable without other API traffic`() = runTest(testDispatcher) {
        ReachabilityStateHolder.onApiFailure()
        coEvery { api.ping(any(), any(), any()) } returns mapOf("status" to "ok")

        monitor.start()
        advanceTimeBy(ServerReachabilityMonitor.INTERVAL_UNREACHABLE_MS)
        runCurrent()

        assertTrue(ReachabilityStateHolder.isReachable.first())
        monitor.stop()
    }

    @Test
    fun `network available debounces rapid callbacks to one probe`() = runTest(testDispatcher) {
        coEvery { api.ping(any(), any(), any()) } returns mapOf("status" to "ok")
        ReachabilityStateHolder.onApiFailure()
        monitor.start()
        runCurrent()

        clock = 1_000L
        monitor.onNetworkAvailable()
        clock = 1_500L // within 1s debounce window
        monitor.onNetworkAvailable()
        runCurrent()

        coVerify(exactly = 1) { api.ping(any(), any(), any()) }
        assertTrue(ReachabilityStateHolder.isReachable.first())
        monitor.stop()
    }

    @Test
    fun `network available after debounce window probes again`() = runTest(testDispatcher) {
        coEvery { api.ping(any(), any(), any()) } returns mapOf("status" to "ok")
        ReachabilityStateHolder.onApiFailure()
        monitor.start()

        clock = 1_000L
        monitor.onNetworkAvailable()
        runCurrent()
        clock = 1_000L + ServerReachabilityMonitor.NETWORK_DEBOUNCE_MS
        monitor.onNetworkAvailable()
        runCurrent()

        coVerify(exactly = 2) { api.ping(any(), any(), any()) }
        monitor.stop()
    }

    @Test
    fun `fake NetworkWatcher start fires probe via callback`() = runTest(testDispatcher) {
        coEvery { api.ping(any(), any(), any()) } returns mapOf("status" to "ok")
        ReachabilityStateHolder.onApiFailure()
        monitor.start()
        assertTrue(fakeWatcher.started)

        clock = 5_000L
        fakeWatcher.fireAvailable()
        runCurrent()

        coVerify(atLeast = 1) { api.ping(any(), any(), any()) }
        assertTrue(ReachabilityStateHolder.isReachable.first())
        monitor.stop()
    }

    @Test
    fun `stop unregisters watcher and cancels keepalive`() = runTest(testDispatcher) {
        coEvery { api.ping(any(), any(), any()) } returns mapOf("status" to "ok")
        monitor.start()
        assertTrue(fakeWatcher.started)
        monitor.stop()
        assertFalse(fakeWatcher.started)

        advanceTimeBy(ServerReachabilityMonitor.INTERVAL_REACHABLE_MS * 2)
        runCurrent()
        coVerify(exactly = 0) { api.ping(any(), any(), any()) }
    }

    @Test
    fun `start is idempotent`() = runTest(testDispatcher) {
        monitor.start()
        monitor.start()
        assertEquals(1, fakeWatcher.startCount)
        monitor.stop()
    }

    @Test
    fun `single-flight serializes probes across debounce windows`() = runTest(testDispatcher) {
        var entered = 0
        coEvery { api.ping(any(), any(), any()) } coAnswers {
            entered++
            mapOf("status" to "ok")
        }
        ReachabilityStateHolder.onApiFailure()
        monitor.start()

        clock = 10_000L
        monitor.onNetworkAvailable()
        runCurrent()
        clock = 10_000L + ServerReachabilityMonitor.NETWORK_DEBOUNCE_MS
        monitor.onNetworkAvailable()
        runCurrent()

        assertEquals(2, entered)
        assertTrue(ReachabilityStateHolder.isReachable.first())
        monitor.stop()
    }

    @Test
    fun `start survives NetworkWatcher register failure`() = runTest(testDispatcher) {
        val broken = object : NetworkWatcher {
            override fun start(onAvailable: () -> Unit): Unit = throw IllegalStateException("no permission")
            override fun stop() {}
        }
        val m = ServerReachabilityMonitor(
            api = api,
            offlineModeManager = offlineModeManager,
            networkWatcher = broken,
            dispatcher = testDispatcher,
            clockMs = { clock },
        )
        m.start() // must not throw
        m.stop()
    }

    @Test
    fun `onNetworkAvailable no-ops when monitor not started`() = runTest(testDispatcher) {
        coEvery { api.ping(any(), any(), any()) } returns mapOf("status" to "ok")
        clock = 100L
        monitor.onNetworkAvailable()
        runCurrent()
        coVerify(exactly = 0) { api.ping(any(), any(), any()) }
    }

    private class FakeNetworkWatcher : NetworkWatcher {
        var started = false
            private set
        var startCount = 0
            private set
        private var onAvailable: (() -> Unit)? = null

        override fun start(onAvailable: () -> Unit) {
            startCount++
            started = true
            this.onAvailable = onAvailable
        }

        override fun stop() {
            started = false
            onAvailable = null
        }

        fun fireAvailable() {
            onAvailable?.invoke()
        }
    }
}
