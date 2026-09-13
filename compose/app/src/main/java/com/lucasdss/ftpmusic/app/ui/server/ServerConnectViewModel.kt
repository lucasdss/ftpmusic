package com.lucasdss.ftpmusic.app.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.network.ServerProbe
import com.lucasdss.ftpmusic.app.di.ServerConfigStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConnectState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isConnecting: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
)

@HiltViewModel
class ServerConnectViewModel @Inject constructor(
    private val serverConfigStore: ServerConfigStore,
    private val serverProbe: ServerProbe,
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectState())
    val state: StateFlow<ConnectState> = _state.asStateFlow()

    init {
        // Self-heal + prefill: force a fresh read so a transient failure during
        // Application.onCreate is retried when the connect screen opens.
        val config = serverConfigStore.initialize(force = true)
        _state.value = _state.value.copy(
            serverUrl = config.url,
            username = config.username,
            password = config.password,
        )
        // Auto-connect only if all credentials are stored
        if (config.url.isNotBlank() && config.username.isNotBlank() && config.password.isNotBlank()) {
            connect { /* LaunchedEffect handles navigation */ }
        }
    }

    fun onUrlChanged(url: String) {
        _state.value = _state.value.copy(serverUrl = url)
    }
    fun onUsernameChanged(u: String) {
        _state.value = _state.value.copy(username = u)
    }
    fun onPasswordChanged(p: String) {
        _state.value = _state.value.copy(password = p)
    }

    fun connect(onSuccess: () -> Unit) {
        val s = _state.value
        val input = validateServerConnectionInput(s.serverUrl, s.username, s.password)
            .getOrElse {
                _state.value = _state.value.copy(error = it.message, connected = false)
                return
            }
        viewModelScope.launch {
            _state.value = _state.value.copy(isConnecting = true, error = null)
            try {
                // Probe the candidate server without mutating the active config,
                // then persist atomically only after it verifies.
                serverProbe.ping(input.url, input.username, input.password)
                serverConfigStore.set(input.url, input.username, input.password)
                _state.value = _state.value.copy(isConnecting = false, connected = true)
                onSuccess()
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    isConnecting = false,
                    connected = false,
                    error = "Unable to connect. Check the server address and credentials.",
                )
            }
        }
    }
}
