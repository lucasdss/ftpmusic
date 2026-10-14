package com.lucasdss.ftpmusic.app.data.update

import android.content.Context
import android.util.Log
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the last Play-confirmed update for the AppHeader issue tray (ADR-0140).
 * Dismiss snoozes until Play reports a higher [AvailableUpdate.versionCode].
 */
@Singleton
class AppUpdateAvailabilityHolder @Inject constructor(@ApplicationContext private val context: Context) {
    private val _available = MutableStateFlow<AvailableUpdate?>(null)
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    fun publish(result: UpdateCheckResult) {
        try {
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> maybeEmit(result.availableVersionCode, result.flexibleAllowed)

                // In-flight flexible install is Settings-owned; keep tray quiet.
                is UpdateCheckResult.InProgress -> Unit

                // Only confirmed UpToDate clears the tray. Unavailable is a
                // Play/network blip — keep the last confirmed AvailableUpdate.
                UpdateCheckResult.UpToDate -> _available.value = null

                is UpdateCheckResult.Unavailable -> Unit
            }
        } catch (e: Exception) {
            Log.w(TAG, "publish failed: ${e.message}", e)
        }
    }

    fun dismiss(versionCode: Int) {
        try {
            prefs().edit().putInt(PREF_DISMISSED_UPDATE_VERSION_CODE, versionCode).apply()
            if (_available.value?.versionCode == versionCode) {
                _available.value = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "dismiss failed: ${e.message}", e)
        }
    }

    fun dismissedVersionCode(): Int = try {
        prefs().getInt(PREF_DISMISSED_UPDATE_VERSION_CODE, 0)
    } catch (e: Exception) {
        Log.w(TAG, "read dismissed failed: ${e.message}", e)
        0
    }

    private fun maybeEmit(versionCode: Int, flexibleAllowed: Boolean) {
        // Malformed codes must not wipe a previously confirmed update.
        if (versionCode <= 0) return
        val dismissed = dismissedVersionCode()
        if (versionCode <= dismissed) {
            _available.value = null
            return
        }
        _available.value = AvailableUpdate(versionCode, flexibleAllowed)
    }

    private fun prefs() = context.getSharedPreferences(MetadataSyncWorker.PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ftpmusic-update"
        const val PREF_DISMISSED_UPDATE_VERSION_CODE = "dismissed_update_version_code"
    }
}
