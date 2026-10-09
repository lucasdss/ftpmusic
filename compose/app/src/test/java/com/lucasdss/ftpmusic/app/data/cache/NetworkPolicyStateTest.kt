package com.lucasdss.ftpmusic.app.data.cache

import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkPolicyStateTest {

    @Before
    fun setUp() {
        NetworkAvailabilityHolder.resetForTests(true)
        NetworkPolicyState.resetForTests()
    }

    @After
    fun tearDown() {
        NetworkPolicyState.resetForTests()
        NetworkAvailabilityHolder.resetForTests(true)
    }

    @Test
    fun `wifi not cellular`() {
        NetworkPolicyState.resetForTests(wifiOrEthernet = true)
        assertFalse(NetworkPolicyState.isCellularActive())
    }

    @Test
    fun `non-wifi with OS net is cellular`() {
        NetworkPolicyState.resetForTests(wifiOrEthernet = false)
        assertTrue(NetworkPolicyState.isCellularActive())
    }

    @Test
    fun `skip sync on cellular when wifi-only without override`() {
        NetworkPolicyState.resetForTests(
            syncWifiOnly = true,
            wifiOrEthernet = false,
        )
        assertTrue(NetworkPolicyState.shouldSkipMetadataSync(allowCellularOverride = false))
        assertFalse(NetworkPolicyState.shouldSkipMetadataSync(allowCellularOverride = true))
    }

    @Test
    fun `local_only skips sync even with override`() {
        NetworkPolicyState.resetForTests(
            policy = CellularMediaPolicy.LOCAL_ONLY,
            syncWifiOnly = false,
            wifiOrEthernet = false,
        )
        assertTrue(NetworkPolicyState.shouldSkipMetadataSync(allowCellularOverride = true))
    }

    @Test
    fun `download priority matrix on cellular`() {
        NetworkPolicyState.resetForTests(
            policy = CellularMediaPolicy.MINIMAL,
            wifiOrEthernet = false,
        )
        assertTrue(NetworkPolicyState.allowsDownloadPriority(0))
        assertFalse(NetworkPolicyState.allowsDownloadPriority(1))
        assertFalse(NetworkPolicyState.allowsDownloadPriority(2))

        NetworkPolicyState.resetForTests(
            policy = CellularMediaPolicy.LOCAL_ONLY,
            wifiOrEthernet = false,
        )
        assertFalse(NetworkPolicyState.allowsDownloadPriority(0))

        NetworkPolicyState.resetForTests(
            policy = CellularMediaPolicy.AUTO_CACHE,
            wifiOrEthernet = false,
        )
        assertTrue(NetworkPolicyState.allowsDownloadPriority(2))
    }

    @Test
    fun `one-shot override consumed once`() {
        NetworkPolicyState.grantCellularSyncOverride()
        assertTrue(NetworkPolicyState.consumeCellularSyncOverride())
        assertFalse(NetworkPolicyState.consumeCellularSyncOverride())
    }

    @Test
    fun `legacy download mobile migration`() {
        assertEquals(
            CellularMediaPolicy.AUTO_CACHE,
            CellularMediaPolicy.fromLegacyDownloadMobileData(true),
        )
        assertEquals(
            CellularMediaPolicy.MINIMAL,
            CellularMediaPolicy.fromLegacyDownloadMobileData(false),
        )
        assertEquals(CellularMediaPolicy.LOCAL_ONLY, CellularMediaPolicy.fromStorage("local_only"))
    }
}
