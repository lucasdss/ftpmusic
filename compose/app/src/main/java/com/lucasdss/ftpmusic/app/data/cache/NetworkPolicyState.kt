package com.lucasdss.ftpmusic.app.data.cache

import android.net.ConnectivityManager
import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder

/**
 * Process-wide network policy snapshot for sync / download / playback gates (ADR-0105).
 * Updated by [NetworkPolicyHolder] and [ConnectivityNetworkWatcher].
 */
object NetworkPolicyState {
    @Volatile
    var cellularMediaPolicy: CellularMediaPolicy = CellularMediaPolicy.AUTO_CACHE

    @Volatile
    var librarySyncWifiOnly: Boolean = false

    /** Active network is Wi‑Fi or Ethernet with INTERNET. Optimistic true until first probe. */
    @Volatile
    var wifiOrEthernet: Boolean = true

    /** One-shot override after user confirms cellular sync warn (not persisted). */
    @Volatile
    private var oneShotCellularSyncOverride: Boolean = false

    fun updateTransport(connectivity: ConnectivityManager) {
        wifiOrEthernet = NetworkTransportPolicy.isWifiOrEthernet(connectivity)
    }

    fun isCellularActive(): Boolean = NetworkAvailabilityHolder.hasOsNetwork.value && !wifiOrEthernet

    fun grantCellularSyncOverride() {
        oneShotCellularSyncOverride = true
    }

    fun consumeCellularSyncOverride(): Boolean {
        val granted = oneShotCellularSyncOverride
        oneShotCellularSyncOverride = false
        return granted
    }

    /**
     * Skip automatic metadata sync on cellular when hard-local or Wi‑Fi-only
     * (unless [allowCellularOverride] bypasses the Wi‑Fi-only flag only).
     */
    fun shouldSkipMetadataSync(allowCellularOverride: Boolean = false): Boolean {
        if (!isCellularActive()) return false
        if (cellularMediaPolicy == CellularMediaPolicy.LOCAL_ONLY) return true
        if (librarySyncWifiOnly && !allowCellularOverride) return true
        return false
    }

    /** Hard local on cellular — browse/stream/sync must stay on cache. */
    fun isCellularHardLocal(): Boolean = isCellularActive() && cellularMediaPolicy == CellularMediaPolicy.LOCAL_ONLY

    /**
     * DownloadManager priority gate on current transport.
     * Priority 0 = queue urgent, 1 = pin download, 2 = cache warm.
     */
    fun allowsDownloadPriority(priority: Int): Boolean {
        if (!isCellularActive()) return true
        return when (cellularMediaPolicy) {
            CellularMediaPolicy.AUTO_CACHE -> true
            CellularMediaPolicy.MINIMAL -> priority == 0
            CellularMediaPolicy.LOCAL_ONLY -> false
        }
    }

    /** Test helper. */
    internal fun resetForTests(
        policy: CellularMediaPolicy = CellularMediaPolicy.AUTO_CACHE,
        syncWifiOnly: Boolean = false,
        wifiOrEthernet: Boolean = true,
    ) {
        cellularMediaPolicy = policy
        librarySyncWifiOnly = syncWifiOnly
        this.wifiOrEthernet = wifiOrEthernet
        oneShotCellularSyncOverride = false
    }
}
