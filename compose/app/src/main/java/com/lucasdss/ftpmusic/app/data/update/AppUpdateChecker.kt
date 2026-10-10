package com.lucasdss.ftpmusic.app.data.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/** Result of a Play Store update availability check (Play SDK types stay behind the impl). */
sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()

    data class UpdateAvailable(val availableVersionCode: Int, val flexibleAllowed: Boolean) : UpdateCheckResult()

    data class Unavailable(val message: String) : UpdateCheckResult()
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

    /** Package id used for Play listing Intents. */
    fun applicationId(): String
}
