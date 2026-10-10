package com.lucasdss.ftpmusic.app.data.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.lucasdss.ftpmusic.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class PlayAppUpdateChecker @Inject constructor(private val appUpdateManager: AppUpdateManager) : AppUpdateChecker {

    @Volatile
    private var cachedInfo: AppUpdateInfo? = null

    override suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val info = awaitAppUpdateInfo()
            cachedInfo = info
            UpdateCheckMapper.fromPlayInfo(info)
        } catch (e: Exception) {
            cachedInfo = null
            UpdateCheckResult.Unavailable(
                e.message?.takeIf { it.isNotBlank() }
                    ?: "Unable to check Play Store for updates",
            )
        }
    }

    override fun startFlexibleUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ): Boolean {
        val info = cachedInfo ?: return false
        if (!info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return false
        return try {
            appUpdateManager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun completeUpdate(): Boolean = try {
        appUpdateManager.completeUpdate()
        true
    } catch (_: Exception) {
        false
    }

    override fun observeFlexibleInstall(): Flow<FlexibleInstallEvent> = callbackFlow {
        val listener = InstallStateUpdatedListener { state ->
            val event = UpdateCheckMapper.fromInstallStatus(state.installStatus())
            if (event != null) {
                trySend(event)
            }
            if (state.installStatus() == InstallStatus.DOWNLOADED ||
                state.installStatus() == InstallStatus.INSTALLED ||
                state.installStatus() == InstallStatus.FAILED ||
                state.installStatus() == InstallStatus.CANCELED
            ) {
                // Keep listener until collector cancels — DOWNLOADED may need completeUpdate.
            }
        }
        appUpdateManager.registerListener(listener)
        // Surface already-downloaded state from last check (same session).
        cachedInfo?.let { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                trySend(FlexibleInstallEvent.Downloaded)
            } else if (
                info.installStatus() == InstallStatus.DOWNLOADING ||
                info.installStatus() == InstallStatus.PENDING ||
                info.installStatus() == InstallStatus.INSTALLING
            ) {
                trySend(FlexibleInstallEvent.Downloading)
            }
        }
        awaitClose { appUpdateManager.unregisterListener(listener) }
    }.distinctUntilChanged()

    override fun applicationId(): String = BuildConfig.APPLICATION_ID

    private suspend fun awaitAppUpdateInfo(): AppUpdateInfo = suspendCancellableCoroutine { cont ->
        val task = appUpdateManager.appUpdateInfo
        task
            .addOnSuccessListener { info ->
                if (cont.isActive) cont.resume(info)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }
    }
}
