package com.lucasdss.ftpmusic.app.data.cache

import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalOnlyPolicyTest {

    @Before
    fun reset() {
        NetworkAvailabilityHolder.resetForTests(true)
        NetworkPolicyState.resetForTests()
    }

    @Test
    fun `offline alone is local-only`() {
        assertTrue(LocalOnlyPolicy.isLocalOnly(isOffline = true, hasOsNetwork = true, cellularHardLocal = false))
    }

    @Test
    fun `no OS network alone is local-only`() {
        assertTrue(LocalOnlyPolicy.isLocalOnly(isOffline = false, hasOsNetwork = false, cellularHardLocal = false))
    }

    @Test
    fun `online with OS network is not local-only`() {
        assertFalse(LocalOnlyPolicy.isLocalOnly(isOffline = false, hasOsNetwork = true, cellularHardLocal = false))
    }

    @Test
    fun `cellular hard local is local-only`() {
        assertTrue(LocalOnlyPolicy.isLocalOnly(isOffline = false, hasOsNetwork = true, cellularHardLocal = true))
    }

    @Test
    fun `reads NetworkAvailabilityHolder by default`() {
        NetworkAvailabilityHolder.resetForTests(false)
        NetworkPolicyState.resetForTests(wifiOrEthernet = true)
        assertTrue(LocalOnlyPolicy.isLocalOnly(isOffline = false))
        NetworkAvailabilityHolder.resetForTests(true)
        assertFalse(LocalOnlyPolicy.isLocalOnly(isOffline = false))
    }
}
