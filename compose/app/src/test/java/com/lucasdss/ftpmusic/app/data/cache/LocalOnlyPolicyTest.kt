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
    }

    @Test
    fun `offline alone is local-only`() {
        assertTrue(LocalOnlyPolicy.isLocalOnly(isOffline = true, hasOsNetwork = true))
    }

    @Test
    fun `no OS network alone is local-only`() {
        assertTrue(LocalOnlyPolicy.isLocalOnly(isOffline = false, hasOsNetwork = false))
    }

    @Test
    fun `online with OS network is not local-only`() {
        assertFalse(LocalOnlyPolicy.isLocalOnly(isOffline = false, hasOsNetwork = true))
    }

    @Test
    fun `reads NetworkAvailabilityHolder by default`() {
        NetworkAvailabilityHolder.resetForTests(false)
        assertTrue(LocalOnlyPolicy.isLocalOnly(isOffline = false))
        NetworkAvailabilityHolder.resetForTests(true)
        assertFalse(LocalOnlyPolicy.isLocalOnly(isOffline = false))
    }
}
