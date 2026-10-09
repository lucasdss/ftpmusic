package com.lucasdss.ftpmusic.app.data.repository

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectivityNetworkWatcherTest {

    @Before
    fun resetHolder() {
        NetworkAvailabilityHolder.resetForTests(true)
    }

    @Test
    fun `start registers callback — onAvailable and INTERNET caps fire listener`() {
        val cm = mockk<ConnectivityManager>(relaxed = true)
        every { cm.activeNetwork } returns null
        val request = mockk<NetworkRequest>(relaxed = true)
        val cbSlot = slot<ConnectivityManager.NetworkCallback>()
        every { cm.registerNetworkCallback(request, capture(cbSlot)) } just runs

        var available = 0
        var lost = 0
        val watcher = ConnectivityNetworkWatcher(cm, request)
        watcher.start(onAvailable = { available++ }, onLost = { lost++ })

        // Seed with no active network → false
        assertFalse(NetworkAvailabilityHolder.hasOsNetwork.value)

        val network = mockk<Network>(relaxed = true)
        cbSlot.captured.onAvailable(network)
        assertEquals(1, available)

        val capsWithInternet = mockk<NetworkCapabilities>(relaxed = true)
        every { capsWithInternet.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { capsWithInternet.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        cbSlot.captured.onCapabilitiesChanged(network, capsWithInternet)
        assertEquals(2, available)

        val capsNoInternet = mockk<NetworkCapabilities>(relaxed = true)
        every { capsNoInternet.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        cbSlot.captured.onCapabilitiesChanged(network, capsNoInternet)
        assertEquals(1, lost)

        cbSlot.captured.onLost(network)
        assertEquals(2, lost)

        watcher.stop()
        verify(exactly = 1) { cm.unregisterNetworkCallback(cbSlot.captured) }

        // Idempotent stop
        watcher.stop()
        verify(exactly = 1) { cm.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }

    @Test
    fun `onLost with other network still INTERNET fires onAvailable not onLost`() {
        val cm = mockk<ConnectivityManager>(relaxed = true)
        val wifi = mockk<Network>(relaxed = true)
        val cell = mockk<Network>(relaxed = true)
        val cellCaps = mockk<NetworkCapabilities>(relaxed = true)
        every { cellCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { cellCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { cellCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { cellCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { cm.activeNetwork } returns null andThen cell
        every { cm.getNetworkCapabilities(cell) } returns cellCaps

        val request = mockk<NetworkRequest>(relaxed = true)
        val cbSlot = slot<ConnectivityManager.NetworkCallback>()
        every { cm.registerNetworkCallback(request, capture(cbSlot)) } just runs

        var available = 0
        var lost = 0
        ConnectivityNetworkWatcher(cm, request).start(
            onAvailable = { available++ },
            onLost = { lost++ },
        )
        assertFalse(NetworkAvailabilityHolder.hasOsNetwork.value)

        // WiFi lost but cell remains active with INTERNET
        cbSlot.captured.onLost(wifi)
        assertEquals(1, available)
        assertEquals(0, lost)

        // Caps lose INTERNET on this iface but activeNetwork still has it
        val noInternet = mockk<NetworkCapabilities>(relaxed = true)
        every { noInternet.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        cbSlot.captured.onCapabilitiesChanged(wifi, noInternet)
        assertEquals(2, available)
        assertEquals(0, lost)
    }

    @Test
    fun `onLost with no remaining INTERNET fires onLost`() {
        val cm = mockk<ConnectivityManager>(relaxed = true)
        every { cm.activeNetwork } returns null
        val request = mockk<NetworkRequest>(relaxed = true)
        val cbSlot = slot<ConnectivityManager.NetworkCallback>()
        every { cm.registerNetworkCallback(request, capture(cbSlot)) } just runs

        var lost = 0
        ConnectivityNetworkWatcher(cm, request).start(
            onAvailable = {},
            onLost = { lost++ },
        )
        cbSlot.captured.onLost(mockk(relaxed = true))
        assertEquals(1, lost)
    }

    @Test
    fun `context constructor resolves ConnectivityManager`() {
        val cm = mockk<ConnectivityManager>(relaxed = true)
        every { cm.activeNetwork } returns null
        val ctx = mockk<android.content.Context>()
        every { ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) } returns cm
        val request = mockk<NetworkRequest>(relaxed = true)
        val cbSlot = slot<ConnectivityManager.NetworkCallback>()
        every { cm.registerNetworkCallback(request, capture(cbSlot)) } just runs

        // Primary default (null request) + Context ctor both construct without NetworkRequest.Builder.
        val viaCm = ConnectivityNetworkWatcher(cm)
        viaCm.stop() // no-op before start

        val viaCtx = ConnectivityNetworkWatcher(ctx)
        // Inject request by constructing the testable 2-arg form after resolving CM
        val watcher = ConnectivityNetworkWatcher(cm, request)
        watcher.start(onAvailable = {}, onLost = {})
        assertFalse(NetworkAvailabilityHolder.hasOsNetwork.value)
        watcher.stop()
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
