package com.lucasdss.ftpmusic.app.data.cache

import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder

/**
 * Local-only browse/search/playback: Simulate Offline, no OS INTERNET, or
 * cellular + [CellularMediaPolicy.LOCAL_ONLY] (ADR-0105).
 * Does not flip [OfflineModeManager] (ADR 0043 / 0051).
 */
object LocalOnlyPolicy {
    fun isLocalOnly(
        isOffline: Boolean,
        hasOsNetwork: Boolean = NetworkAvailabilityHolder.hasOsNetwork.value,
        cellularHardLocal: Boolean = NetworkPolicyState.isCellularHardLocal(),
    ): Boolean = isOffline || !hasOsNetwork || cellularHardLocal
}
