package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.content.SharedPreferences
import com.lucasdss.ftpmusic.app.data.cache.CellularMediaPolicy
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.cache.NetworkPolicyState
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MetadataSyncCellularPolicyTest {

    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val offline: OfflineModeManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { offline.isQueueEnabled() } returns false
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns mockk(relaxed = true)
        NetworkAvailabilityHolder.resetForTests(true)
        ReachabilityStateHolder.resetForTests(true)
        NetworkPolicyState.resetForTests()
    }

    @After
    fun tearDown() {
        NetworkPolicyState.resetForTests()
        NetworkAvailabilityHolder.resetForTests(true)
        ReachabilityStateHolder.resetForTests(true)
    }

    private fun worker() = MetadataSyncWorker(
        context,
        mockk<SubsonicApi>(relaxed = true),
        SubsonicAuthHelper(),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk<CoverArtFallbackService>(relaxed = true),
        offline,
        mockk(relaxed = true),
    )

    @Test
    fun `syncNowAsync skips on cellular when wifi-only`() = runTest {
        NetworkPolicyState.resetForTests(syncWifiOnly = true, wifiOrEthernet = false)
        assertNull(worker().syncNowAsync())
    }

    @Test
    fun `syncNowAsync runs on cellular with override when wifi-only`() = runTest {
        NetworkPolicyState.resetForTests(syncWifiOnly = true, wifiOrEthernet = false)
        // Will still fail later (no creds/API) but must pass cellular gate (non-null Job or CAS).
        // Offline/reachability pass; cooldown fresh — CAS starts job.
        val job = worker().syncNowAsync(allowCellularOverride = true)
        // Job may complete quickly with error phase; null only if gate skipped.
        // Force path: if job null because CAS or other, retry once after stop.
        assertNotNull(job)
        job?.cancel()
    }

    @Test
    fun `syncNowAsync skips on cellular local-only even with override`() = runTest {
        NetworkPolicyState.resetForTests(
            policy = CellularMediaPolicy.LOCAL_ONLY,
            wifiOrEthernet = false,
        )
        assertNull(worker().syncNowAsync(allowCellularOverride = true))
    }
}
