package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*

/**
 * Auto-reconnects to the last known server on app restart.
 * Pings the server, sets offline mode if unreachable.
 */
@Singleton
class ServerReconnectionService @Inject constructor(private val api: SubsonicApi) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val auth = SubsonicAuthHelper()

    data class ReconnectResult(val connected: Boolean, val serverUrl: String? = null, val username: String? = null)

    suspend fun tryReconnect(url: String, username: String, password: String): ReconnectResult = withTimeout(5000) {
        try {
            val authParams = auth.buildAuthParams(username, password)
            api.ping(username, authParams["t"]!!, authParams["s"]!!)
            ReconnectResult(true, url, username)
        } catch (e: Exception) {
            ReconnectResult(false)
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
