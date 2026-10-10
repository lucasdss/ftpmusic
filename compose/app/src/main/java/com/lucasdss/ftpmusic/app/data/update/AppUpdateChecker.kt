package com.lucasdss.ftpmusic.app.data.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.Flow

/** Result of a Play Store update availability check (Play SDK types stay behind the impl). */
sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()

    data class UpdateAvailable(val availableVersionCode: Int, val flexibleAllowed: Boolean) : UpdateCheckResult()

    /** Flexible (or immediate) update already started by this app; wait for download/install. */
    data class InProgress(val availableVersionCode: Int) : UpdateCheckResult()

    data class Unavailable(val message: String) : UpdateCheckResult()
}

/** Same-session flexible install progress (process-death resume out of scope). */
sealed class FlexibleInstallEvent {
    data object Downloading : FlexibleInstallEvent()

    data object Downloaded : FlexibleInstallEvent()

    data class Failed(val message: String) : FlexibleInstallEvent()
}

/**
 * Play Store update check seam. Implementations may cache the last Play
 * [com.google.android.play.core.appupdate.AppUpdateInfo] for [startFlexibleUpdate].
 */
interface AppUpdateChecker {
    suspend fun check(): UpdateCheckResult

    /**
     * Starts Play flexible update UI using the last successful
     * [UpdateCheckResult.UpdateAvailable] check. Returns false if no cached
     * info or Play rejects the flow.
     */
    fun startFlexibleUpdate(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean

    /**
     * Applies a downloaded flexible update (restarts the app). Returns false
     * if Play rejects the call.
     */
    fun completeUpdate(): Boolean

    /** Hot install-state events while a flexible update is in flight. */
    fun observeFlexibleInstall(): Flow<FlexibleInstallEvent>

    /** Package id used for Play listing Intents. */
    fun applicationId(): String
}
