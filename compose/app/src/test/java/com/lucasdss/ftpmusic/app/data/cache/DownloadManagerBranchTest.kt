package com.lucasdss.ftpmusic.app.data.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.lucasdss.ftpmusic.app.data.db.CacheQueueDao
import com.lucasdss.ftpmusic.app.data.db.CacheQueueItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Branch coverage extensions for [DownloadManager]: enqueue priority/dedup
 * upgrade paths and the connectivity/battery constraints (exercised through
 * the private checkConstraints via reflection — no real network, no sleeps).
 */
class DownloadManagerBranchTest {

    private val dao: CacheQueueDao = mockk(relaxed = true)
    private val cacheService: CacheService = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val connectivity: ConnectivityManager = mockk(relaxed = true)
    private val batteryManager: BatteryManager = mockk(relaxed = true)

    private fun manager() = DownloadManager(
        dao,
        cacheService,
        mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
        context,
    )

    private fun constraints(manager: DownloadManager): Boolean {
        val method = DownloadManager::class.java.getDeclaredMethod("checkConstraints")
        method.isAccessible = true
        return method.invoke(manager) as Boolean
    }

    // ── enqueue upgrade paths ───────────────────────────────────────────────

    @Test
    fun `enqueue upgrades priority of a pending lower-priority item`() = runTest {
        coEvery { dao.getByTrackId("tr-a") } returns CacheQueueItemEntity(
            id = 1,
            trackId = "tr-a",
            remoteUrl = "http://x",
            status = "pending",
            priority = 2,
            isDownload = false,
        )

        manager().enqueue("tr-a", "http://x", priority = 0)

        coVerify { dao.updatePriority(1, 0) }
        coVerify(exactly = 0) { dao.markAsDownload(any()) }
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
    }

    @Test
    fun `enqueue plain item on pending row does not touch priority or download flag`() = runTest {
        coEvery { dao.getByTrackId("tr-b") } returns CacheQueueItemEntity(
            id = 2,
            trackId = "tr-b",
            remoteUrl = "http://x",
            status = "pending",
            priority = 1,
            isDownload = false,
        )

        manager().enqueue("tr-b", "http://x", priority = 2)

        coVerify(exactly = 0) { dao.updatePriority(any(), any()) }
        coVerify(exactly = 0) { dao.markAsDownload(any()) }
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
    }

    @Test
    fun `enqueue completed cached item without download is a no-op`() = runTest {
        coEvery { dao.getByTrackId("tr-c") } returns CacheQueueItemEntity(
            id = 3,
            trackId = "tr-c",
            remoteUrl = "http://x",
            status = "completed",
            priority = 2,
            isDownload = false,
        )
        coEvery { cacheService.isStoredInCache("tr-c") } returns true

        manager().enqueue("tr-c", "http://x", priority = 0)

        coVerify(exactly = 0) { dao.updateStatus(any(), "pending") }
        coVerify { dao.updatePriority(3, 0) } // still upgraded
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
    }

    @Test
    fun `enqueue completed cached item without download keeps priority when not higher`() = runTest {
        coEvery { dao.getByTrackId("tr-d") } returns CacheQueueItemEntity(
            id = 4,
            trackId = "tr-d",
            remoteUrl = "http://x",
            status = "completed",
            priority = 0,
            isDownload = false,
        )
        coEvery { cacheService.isStoredInCache("tr-d") } returns true

        manager().enqueue("tr-d", "http://x", priority = 2)

        coVerify(exactly = 0) { dao.updatePriority(any(), any()) }
        coVerify(exactly = 0) { dao.updateStatus(any(), "pending") }
    }

    @Test
    fun `enqueue completed non-cached item resets to pending`() = runTest {
        coEvery { dao.getByTrackId("tr-e") } returns CacheQueueItemEntity(
            id = 5,
            trackId = "tr-e",
            remoteUrl = "http://x",
            status = "completed",
            priority = 2,
            isDownload = false,
        )
        coEvery { cacheService.isStoredInCache("tr-e") } returns false

        manager().enqueue("tr-e", "http://x", priority = 0)

        coVerify { dao.updateStatus(5, "pending") }
        coVerify { dao.updatePriority(5, 0) }
        coVerify(exactly = 0) { dao.insertIgnore(any()) }
    }

    @Test
    fun `enqueue applies upgrades to a row that won the insert race`() = runTest {
        coEvery { dao.getByTrackId("tr-f") } returns null
        coEvery { dao.insertIgnore(any()) } returns -1L
        coEvery { dao.getByTrackId("tr-f") } returnsMany listOf(
            null,
            CacheQueueItemEntity(
                id = 9,
                trackId = "tr-f",
                remoteUrl = "http://x",
                status = "pending",
                priority = 2,
                isDownload = false,
            ),
        )

        manager().enqueue("tr-f", "http://x", priority = 0)

        coVerify { dao.updatePriority(9, 0) }
        coVerify(exactly = 0) { dao.markAsDownload(any()) }
    }

    @Test
    fun `enqueue winner upgrade also marks as download when requested`() = runTest {
        coEvery { dao.getByTrackId("tr-g") } returns null
        coEvery { dao.insertIgnore(any()) } returns -1L
        coEvery { dao.getByTrackId("tr-g") } returnsMany listOf(
            null,
            CacheQueueItemEntity(
                id = 10,
                trackId = "tr-g",
                remoteUrl = "http://x",
                status = "pending",
                priority = 0,
                isDownload = false,
            ),
        )

        manager().enqueue("tr-g", "http://x", priority = 1)

        coVerify(exactly = 0) { dao.updatePriority(any(), any()) } // 1 !< 0
        coVerify { dao.markAsDownload(10) }
    }

    // ── checkConstraints (via reflection) ───────────────────────────────────

    private fun managerWithConnectivity(): DownloadManager {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        every { context.getSystemService(Context.BATTERY_SERVICE) } returns batteryManager
        val network = mockk<Network>(relaxed = true)
        every { connectivity.activeNetwork } returns network
        every { connectivity.getNetworkCapabilities(network) } returns mockk<NetworkCapabilities>(relaxed = true)
        return manager()
    }

    @Test
    fun `checkConstraints false when offline mode enabled`() {
        val offline = mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true)
        every { offline.isOfflineEnabled() } returns true
        val mgr = DownloadManager(dao, cacheService, offline, context)
        assertFalse(constraints(mgr))
    }

    @Test
    fun `checkConstraints false when no active network`() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        every { connectivity.activeNetwork } returns null
        assertFalse(constraints(manager()))
    }

    @Test
    fun `checkConstraints false when capabilities unavailable`() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        val network = mockk<Network>(relaxed = true)
        every { connectivity.activeNetwork } returns network
        every { connectivity.getNetworkCapabilities(network) } returns null
        assertFalse(constraints(manager()))
    }

    @Test
    fun `checkConstraints false without internet capability`() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        val network = mockk<Network>(relaxed = true)
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { connectivity.activeNetwork } returns network
        every { connectivity.getNetworkCapabilities(network) } returns caps
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        assertFalse(constraints(manager()))
    }

    @Test
    fun `checkConstraints false on cellular when mobile data disallowed`() {
        DownloadManager.allowMobileData = false
        try {
            every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
            every { context.getSystemService(Context.BATTERY_SERVICE) } returns batteryManager
            val network = mockk<Network>(relaxed = true)
            val caps = mockk<NetworkCapabilities>(relaxed = true)
            every { connectivity.activeNetwork } returns network
            every { connectivity.getNetworkCapabilities(network) } returns caps
            every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
            every { caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
            every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 80
            assertFalse(constraints(manager()))
        } finally {
            DownloadManager.allowMobileData = true
        }
    }

    @Test
    fun `checkConstraints true on wifi when mobile data disallowed`() {
        DownloadManager.allowMobileData = false
        try {
            every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
            every { context.getSystemService(Context.BATTERY_SERVICE) } returns batteryManager
            val network = mockk<Network>(relaxed = true)
            val caps = mockk<NetworkCapabilities>(relaxed = true)
            every { connectivity.activeNetwork } returns network
            every { connectivity.getNetworkCapabilities(network) } returns caps
            every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
            every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 80
            assertTrue(constraints(manager()))
        } finally {
            DownloadManager.allowMobileData = true
        }
    }

    @Test
    fun `checkConstraints false on low battery while not charging`() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        every { context.getSystemService(Context.BATTERY_SERVICE) } returns batteryManager
        val network = mockk<Network>(relaxed = true)
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { connectivity.activeNetwork } returns network
        every { connectivity.getNetworkCapabilities(network) } returns caps
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 10
        every { batteryManager.isCharging } returns false
        assertFalse(constraints(manager()))
    }

    @Test
    fun `checkConstraints true on low battery while charging`() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        every { context.getSystemService(Context.BATTERY_SERVICE) } returns batteryManager
        val network = mockk<Network>(relaxed = true)
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { connectivity.activeNetwork } returns network
        every { connectivity.getNetworkCapabilities(network) } returns caps
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 10
        every { batteryManager.isCharging } returns true
        assertTrue(constraints(manager()))
    }

    @Test
    fun `checkConstraints tolerates battery service failure`() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        every { context.getSystemService(Context.BATTERY_SERVICE) } returns batteryManager
        val network = mockk<Network>(relaxed = true)
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { connectivity.activeNetwork } returns network
        every { connectivity.getNetworkCapabilities(network) } returns caps
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { batteryManager.getIntProperty(any()) } throws RuntimeException("battery broken")
        assertTrue(constraints(manager()))
    }

    @Test
    fun `checkConstraints swallows connectivity service failures`() {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        every { connectivity.activeNetwork } throws RuntimeException("no service")
        assertFalse(constraints(manager()))
    }

    @Test
    fun `start is idempotent while already running`() {
        val mgr =
            DownloadManager(
                dao,
                cacheService,
                mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
                context,
            )
        coEvery { dao.getNextPendingByPriority(any()) } returns null
        try {
            mgr.start()
            mgr.start() // second start is a no-op while isRunning
            coVerify(timeout = 2000, exactly = 1) { dao.resetProcessingToPending() }
        } finally {
            mgr.stop()
        }
    }
}
