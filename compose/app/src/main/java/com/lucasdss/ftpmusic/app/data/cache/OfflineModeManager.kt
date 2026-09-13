package com.lucasdss.ftpmusic.app.data.cache

import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Offline Mode: user toggle that blocks all network calls to the Subsonic server.
 * When enabled, playback is restricted to Downloads + Auto-cache only.
 *
 * Components that make network requests should check [isOffline] before calling out.
 * The PlaybackManager skips proxy URL generation and falls back to cache-only play.
 */
@Singleton
class OfflineModeManager @Inject constructor(private val storage: SecureStorage) {
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline

    /** Direct Boolean read for synchronous gate checks (workers, download
     *  constraints, scrobble). Also mock-friendly: relaxed mocks return false
     *  without needing a stubbed StateFlow. */
    fun isOfflineEnabled(): Boolean = _isOffline.value

    /** Restore the persisted toggle. MUST run at app startup before any worker
     *  (download manager, sync workers, scrobble service) can make network calls —
     *  otherwise the user believes they are offline while the app streams. */
    fun initialize() {
        val persisted = storage.get(SecureStorage.KEY_OFFLINE_MODE)?.toBooleanStrictOrNull() ?: false
        if (persisted) enable()
        android.util.Log.i("ftpmusic-offline", "Offline mode restored: $persisted")
    }

    fun enable() {
        _isOffline.value = true
        storage.put(SecureStorage.KEY_OFFLINE_MODE, "true")
        android.util.Log.i("ftpmusic-offline", "Offline mode ENABLED — network blocked")
    }

    fun disable() {
        _isOffline.value = false
        storage.put(SecureStorage.KEY_OFFLINE_MODE, "false")
        android.util.Log.i("ftpmusic-offline", "Offline mode DISABLED — network available")
    }

    fun toggle() {
        if (_isOffline.value) disable() else enable()
    }
}
