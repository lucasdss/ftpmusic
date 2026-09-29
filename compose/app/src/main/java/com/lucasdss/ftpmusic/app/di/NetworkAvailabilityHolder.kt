package com.lucasdss.ftpmusic.app.di

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OS-level INTERNET capability (airplane / no validated network).
 *
 * Distinct from [ReachabilityStateHolder] (Subsonic ping) and
 * [com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager] (Simulate Offline).
 * Updated by [com.lucasdss.ftpmusic.app.data.repository.ConnectivityNetworkWatcher]
 * / [com.lucasdss.ftpmusic.app.data.repository.ServerReachabilityMonitor].
 */
object NetworkAvailabilityHolder {
    private val _hasOsNetwork = MutableStateFlow(true)
    val hasOsNetwork: StateFlow<Boolean> = _hasOsNetwork.asStateFlow()

    /** Seed from ConnectivityManager at boot (before first NetworkCallback). */
    fun initialize(connectivity: ConnectivityManager) {
        _hasOsNetwork.value = hasInternetCapability(connectivity)
    }

    fun setAvailable(available: Boolean) {
        if (_hasOsNetwork.value != available) {
            _hasOsNetwork.value = available
            android.util.Log.i(
                "ftpmusic-net",
                if (available) "OS network available" else "OS network lost",
            )
        }
    }

    /** Test / reset helper — restores optimistic default. */
    internal fun resetForTests(available: Boolean = true) {
        _hasOsNetwork.value = available
    }

    fun hasInternetCapability(connectivity: ConnectivityManager): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
