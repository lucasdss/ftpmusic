package com.lucasdss.ftpmusic.app.di

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkAvailabilityHolderTest {

    @Before
    fun reset() {
        NetworkAvailabilityHolder.resetForTests(true)
    }

    @Test
    fun `initial default is available`() = runTest {
        assertEquals(true, NetworkAvailabilityHolder.hasOsNetwork.first())
    }

    @Test
    fun `setAvailable false then true`() = runTest {
        NetworkAvailabilityHolder.setAvailable(false)
        assertFalse(NetworkAvailabilityHolder.hasOsNetwork.value)
        NetworkAvailabilityHolder.setAvailable(true)
        assertTrue(NetworkAvailabilityHolder.hasOsNetwork.value)
    }

    @Test
    fun `setAvailable same value is no-op`() {
        NetworkAvailabilityHolder.setAvailable(true)
        NetworkAvailabilityHolder.setAvailable(true)
        assertTrue(NetworkAvailabilityHolder.hasOsNetwork.value)
    }

    @Test
    fun `initialize from ConnectivityManager no active network`() {
        val cm = mockk<ConnectivityManager>()
        every { cm.activeNetwork } returns null
        NetworkAvailabilityHolder.initialize(cm)
        assertFalse(NetworkAvailabilityHolder.hasOsNetwork.value)
    }

    @Test
    fun `initialize from ConnectivityManager with INTERNET`() {
        val cm = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val caps = mockk<NetworkCapabilities>()
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns caps
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        NetworkAvailabilityHolder.initialize(cm)
        assertTrue(NetworkAvailabilityHolder.hasOsNetwork.value)
    }

    @Test
    fun `hasInternetCapability false when caps null`() {
        val cm = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns null
        assertFalse(NetworkAvailabilityHolder.hasInternetCapability(cm))
    }
}
