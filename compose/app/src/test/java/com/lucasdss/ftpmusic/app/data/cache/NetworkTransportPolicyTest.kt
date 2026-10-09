package com.lucasdss.ftpmusic.app.data.cache

import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTransportPolicyTest {

    @Test
    fun `wifi transport is wifi or ethernet`() {
        val caps = mockk<NetworkCapabilities>()
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        assertTrue(NetworkTransportPolicy.isWifiOrEthernet(caps))
    }

    @Test
    fun `cellular only is not wifi or ethernet`() {
        val caps = mockk<NetworkCapabilities>()
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        assertFalse(NetworkTransportPolicy.isWifiOrEthernet(caps))
    }

    @Test
    fun `null caps false`() {
        assertFalse(NetworkTransportPolicy.isWifiOrEthernet(null as NetworkCapabilities?))
    }
}
