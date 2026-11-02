package com.lucasdss.ftpmusic.app.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the Navidrome server is reachable.
 *
 * Updated by `BaseUrlInterceptor` on every OkHttp API call (hysteresis: N8), and
 * by [com.lucasdss.ftpmusic.app.data.repository.ServerReachabilityMonitor] via
 * [onProbeSuccess]/[onProbeFailure] (trusted single-flight probe — the only
 * signal when the app is idle).
 */
object ReachabilityStateHolder {
    private val _isReachable = MutableStateFlow(true)
    val isReachable: StateFlow<Boolean> = _isReachable.asStateFlow()

    private var policy = ReachabilityPolicyState()

    /** Any successful API response — instant recovery, clears the failure run. */
    @Synchronized
    internal fun onApiSuccess() {
        policy = policy.onSuccess()
        markReachable("server reachable again")
    }

    /**
     * Interceptor failure. N8: one `SocketTimeoutException` on any request used
     * to flip the whole server down (the export has sub-second flaps); require a
     * second failure inside the window before publishing `false`.
     */
    @Synchronized
    internal fun onApiFailure() {
        val (next, trip) = policy.onFailure(System.currentTimeMillis())
        policy = next
        if (trip) markUnreachable("server unreachable")
    }

    /** Dedicated keepalive probe succeeded. */
    @Synchronized
    internal fun onProbeSuccess() {
        policy = policy.onSuccess()
        markReachable("server reachable again")
    }

    /**
     * Dedicated keepalive probe failed. The probe is single-flight with a 5 s
     * timeout and is the only communication when idle, so it is trusted and flips
     * immediately (the 15 s/60 s cadence bounds re-flapping).
     */
    @Synchronized
    internal fun onProbeFailure() {
        policy = policy.onSuccess()
        markUnreachable("server unreachable")
    }

    private fun markReachable(log: String) {
        if (!_isReachable.value) {
            _isReachable.value = true
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w("ftpmusic-reach", log)
        }
    }

    private fun markUnreachable(log: String) {
        if (_isReachable.value) {
            _isReachable.value = false
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w("ftpmusic-reach", log)
        }
    }

    /** Test helper. */
    @Synchronized
    internal fun resetForTests(reachable: Boolean = true) {
        _isReachable.value = reachable
        policy = ReachabilityPolicyState()
    }
}
