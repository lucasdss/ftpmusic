package com.lucasdss.ftpmusic.app.data.cache

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Wi‑Fi / Ethernet vs cellular transport helpers (ADR-0105). */
object NetworkTransportPolicy {
    fun isWifiOrEthernet(connectivity: ConnectivityManager): Boolean {
        val net = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(net) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun isWifiOrEthernet(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** Cellular (or other non-Wi‑Fi/Ethernet) with INTERNET — not airplane. */
    fun isCellularLike(connectivity: ConnectivityManager): Boolean {
        val net = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(net) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !isWifiOrEthernet(caps)
    }
}
