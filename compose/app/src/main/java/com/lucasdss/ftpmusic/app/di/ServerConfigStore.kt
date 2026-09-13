package com.lucasdss.ftpmusic.app.di

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucasdss.ftpmusic.app.BuildConfig
import com.lucasdss.ftpmusic.app.data.security.SecretRead
import com.lucasdss.ftpmusic.app.data.security.SecretStore
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable snapshot of the configured Subsonic server. */
data class ServerConfig(val url: String = "", val username: String = "", val password: String = "") {
    val isConfigured: Boolean get() = url.isNotBlank() && username.isNotBlank()
}

/**
 * Compose-observable snapshot of the active server config. Updated only by
 * [ServerConfigStore]. Composables that build URLs read this so a config that
 * arrives *after* first composition (transient storage failure, late restore)
 * triggers recomposition instead of leaving null URLs cached forever.
 */
object ServerConfigState {
    var value: ServerConfig by mutableStateOf(ServerConfig())
        internal set
}

/**
 * Single source of truth for the server configuration.
 *
 * Replaces the previous pattern of three independent restore paths writing to
 * process-global `@Volatile` vars:
 *  - [initialize] reads persisted config once per process and publishes it to
 *    both the legacy globals (interceptors/workers) and [ServerConfigState]
 *    (Compose).
 *  - A transient read failure never clobbers a working in-memory config; the
 *    store stays uninitialized so the next caller retries.
 *  - [set] persists atomically and publishes in one step.
 */
@Singleton
class ServerConfigStore @Inject constructor(private val storage: SecretStore) {
    private val _state = MutableStateFlow(ServerConfigState.value)
    val state: StateFlow<ServerConfig> = _state.asStateFlow()

    @Volatile
    private var initialized = false

    fun current(): ServerConfig = _state.value

    /**
     * Restore the persisted configuration.
     *
     * @param force re-read storage even if a previous call succeeded (used by
     *   the splash and MediaService as a self-heal hook).
     */
    fun initialize(force: Boolean = false): ServerConfig {
        synchronized(this) {
            if (initialized && !force) return _state.value

            val urlRead = storage.read(SecureStorage.KEY_URL)
            val userRead = storage.read(SecureStorage.KEY_USERNAME)
            val passRead = storage.read(SecureStorage.KEY_PASSWORD)

            val failure = listOf(urlRead, userRead, passRead)
                .filterIsInstance<SecretRead.Failed>()
                .firstOrNull()
            if (failure != null) {
                // Transient failure (keystore busy, locked credential storage):
                // keep the current config and leave `initialized` false so a
                // later call retries.
                if (BuildConfig.IMAGE_DIAGNOSTICS) {
                    android.util.Log.w(
                        "ftpmusic-images",
                        "[diag] ServerConfigStore.initialize read FAILED " +
                            "(${failure.cause.javaClass.simpleName}) — keeping current config",
                    )
                }
                return _state.value
            }

            val config = ServerConfig(
                url = (urlRead as? SecretRead.Ok)?.value.orEmpty().trimEnd('/'),
                username = (userRead as? SecretRead.Ok)?.value.orEmpty(),
                password = (passRead as? SecretRead.Ok)?.value.orEmpty(),
            )
            apply(config)
            initialized = true
            if (BuildConfig.IMAGE_DIAGNOSTICS) {
                android.util.Log.w(
                    "ftpmusic-images",
                    "[diag] ServerConfigStore.initialize configured=${config.isConfigured} " +
                        "url=${config.url.isNotBlank()} user=${config.username.isNotBlank()} " +
                        "t=${System.currentTimeMillis()}",
                )
            }
            return config
        }
    }

    /** Persist + publish a new configuration atomically. */
    fun set(url: String, username: String, password: String): ServerConfig {
        val config = ServerConfig(
            url = url.trim().trimEnd('/'),
            username = username.trim(),
            password = password,
        )
        synchronized(this) {
            storage.putAll(
                mapOf(
                    SecureStorage.KEY_URL to config.url,
                    SecureStorage.KEY_USERNAME to config.username,
                    SecureStorage.KEY_PASSWORD to config.password,
                ),
            )
            apply(config)
            initialized = true
        }
        return config
    }

    private fun apply(config: ServerConfig) {
        DynamicBaseUrl.url = config.url
        SubsonicCredentials.username = config.username
        SubsonicCredentials.password = config.password
        _state.value = config
        ServerConfigState.value = config
    }
}

/** Compose/entry-point access to the Hilt [ServerConfigStore] singleton. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServerConfigEntryPoint {
    fun serverConfigStore(): ServerConfigStore
}

/** Resolve the process-wide [ServerConfigStore] from any Compose context. */
fun serverConfigStore(context: android.content.Context): ServerConfigStore = EntryPointAccessors.fromApplication(
    context.applicationContext,
    ServerConfigEntryPoint::class.java,
).serverConfigStore()
