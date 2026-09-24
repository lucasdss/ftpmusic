package com.lucasdss.ftpmusic.app.data.repository

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityNetworkWatcherTest {

    @Test
    fun `start registers callback — onAvailable and INTERNET caps fire listener`() {
        val cm = mockk<ConnectivityManager>(relaxed = true)
        val request = mockk<NetworkRequest>(relaxed = true)
        val cbSlot = slot<ConnectivityManager.NetworkCallback>()
        every { cm.registerNetworkCallback(request, capture(cbSlot)) } just runs

        var fired = 0
        val watcher = ConnectivityNetworkWatcher(cm, request)
        watcher.start { fired++ }

        val network = mockk<Network>(relaxed = true)
        cbSlot.captured.onAvailable(network)
        assertEquals(1, fired)

        val capsWithInternet = mockk<NetworkCapabilities>()
        every { capsWithInternet.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        cbSlot.captured.onCapabilitiesChanged(network, capsWithInternet)
        assertEquals(2, fired)

        val capsNoInternet = mockk<NetworkCapabilities>()
        every { capsNoInternet.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        cbSlot.captured.onCapabilitiesChanged(network, capsNoInternet)
        assertEquals(2, fired)

        watcher.stop()
        verify(exactly = 1) { cm.unregisterNetworkCallback(cbSlot.captured) }

        // Idempotent stop
        watcher.stop()
        verify(exactly = 1) { cm.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }

    @Test
    fun `stop before start is no-op`() {
        val cm = mockk<ConnectivityManager>(relaxed = true)
        val watcher = ConnectivityNetworkWatcher(cm, mockk(relaxed = true))
        watcher.stop()
        verify(exactly = 0) { cm.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
        assertTrue(true)
    }
}
