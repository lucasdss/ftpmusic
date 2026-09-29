package com.lucasdss.ftpmusic.app.data.cache

import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder

/**
 * Local-only browse/search: Simulate Offline **or** no OS INTERNET.
 * Does not flip [OfflineModeManager] (ADR 0043 / 0051).
 */
object LocalOnlyPolicy {
    fun isLocalOnly(
        isOffline: Boolean,
        hasOsNetwork: Boolean = NetworkAvailabilityHolder.hasOsNetwork.value,
    ): Boolean = isOffline || !hasOsNetwork
}
