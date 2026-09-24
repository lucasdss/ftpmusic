package com.lucasdss.ftpmusic.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Keeps [ReachabilityStateHolder] honest when the UI is idle.
 *
 * Stuck-offline root cause: BaseUrlInterceptor only clears `isReachable=false`
 * on the next successful API call. No traffic → Offline chip/banner linger after
 * the server/network recover. This monitor:
 *  - pings Subsonic on a keepalive timer (15s unreachable / 60s reachable)
 *  - probes immediately (debounced) when OS reports INTERNET via NetworkCallback
 *
 * Does NOT touch [OfflineModeManager] (intentional Simulate Offline stays sticky).
 */
@Singleton
class ServerReachabilityMonitor(
    private val api: SubsonicApi,
    private val offlineModeManager: OfflineModeManager,
    private val networkWatcher: NetworkWatcher,
    private val dispatcher: CoroutineDispatcher,
    private val clockMs: () -> Long,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        api: SubsonicApi,
        offlineModeManager: OfflineModeManager,
    ) : this(
        api = api,
        offlineModeManager = offlineModeManager,
        networkWatcher = ConnectivityNetworkWatcher(context),
        dispatcher = Dispatchers.IO,
        clockMs = { System.currentTimeMillis() },
    )

    companion object {
        const val INTERVAL_UNREACHABLE_MS = 15_000L
        const val INTERVAL_REACHABLE_MS = 60_000L
        const val NETWORK_DEBOUNCE_MS = 1_000L
        const val PROBE_TIMEOUT_MS = 5_000L
        private const val TAG = "ftpmusic-reachability"
    }

    private val auth = SubsonicAuthHelper()
    private val probeMutex = Mutex()

    @Volatile
    private var started = false
    private var scope: CoroutineScope? = null
    private var keepaliveJob: Job? = null
    private var lastDebouncedProbeAt = 0L

    fun start() {
        if (started) return
        started = true
        val s = CoroutineScope(dispatcher + SupervisorJob())
        scope = s
        try {
            networkWatcher.start { onNetworkAvailable() }
        } catch (e: Exception) {
            Log.w(TAG, "NetworkCallback register failed: ${e.message}")
        }
        keepaliveJob = s.launch {
            while (isActive) {
                delay(currentIntervalMs())
                probeOnce()
            }
        }
        Log.i(TAG, "Reachability monitor started")
    }

    fun stop() {
        if (!started) return
        started = false
        keepaliveJob?.cancel()
        keepaliveJob = null
        try {
            networkWatcher.stop()
        } catch (e: Exception) {
            Log.w(TAG, "NetworkCallback unregister failed: ${e.message}")
        }
        scope?.cancel()
        scope = null
        Log.i(TAG, "Reachability monitor stopped")
    }

    /** Interval used by the keepalive loop (readable for tests). */
    fun currentIntervalMs(): Long = if (ReachabilityStateHolder.isReachable.value) {
        INTERVAL_REACHABLE_MS
    } else {
        INTERVAL_UNREACHABLE_MS
    }

    /**
     * OS network became available. Debounced so onAvailable + onCapabilitiesChanged
     * storms collapse to one probe.
     */
    fun onNetworkAvailable() {
        val now = clockMs()
        if (now - lastDebouncedProbeAt < NETWORK_DEBOUNCE_MS) return
        lastDebouncedProbeAt = now
        val s = scope
        if (s == null) {
            return
        }
        s.launch { probeOnce() }
    }

    /**
     * Single-flight Subsonic ping. Updates [ReachabilityStateHolder] explicitly
     * so unit tests (mocked API, no OkHttp interceptor) still flip UI state.
     *
     * @return true if ping succeeded; false if skipped or failed.
     */
    suspend fun probeOnce(): Boolean = probeMutex.withLock {
        if (offlineModeManager.isOfflineEnabled()) return@withLock false
        if (!DynamicBaseUrl.isConfigured()) return@withLock false
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isBlank() || password.isBlank()) return@withLock false

        return@withLock try {
            withTimeout(PROBE_TIMEOUT_MS) {
                val params = auth.buildAuthParams(username, password)
                api.ping(username, params.getValue("t"), params.getValue("s"))
            }
            ReachabilityStateHolder.onApiSuccess()
            true
        } catch (_: Exception) {
            ReachabilityStateHolder.onApiFailure()
            false
        }
    }
}

/** Abstraction over ConnectivityManager so unit tests can fire net-up without Robolectric. */
interface NetworkWatcher {
    fun start(onAvailable: () -> Unit)
    fun stop()
}

class ConnectivityNetworkWatcher(
    private val connectivity: ConnectivityManager,
    private val request: NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build(),
) : NetworkWatcher {
    constructor(context: Context) : this(
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
    )

    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun start(onAvailable: () -> Unit) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onAvailable()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    onAvailable()
                }
            }
        }
        callback = cb
        connectivity.registerNetworkCallback(request, cb)
    }

    override fun stop() {
        val cb = callback ?: return
        callback = null
        connectivity.unregisterNetworkCallback(cb)
    }
}
