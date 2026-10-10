package com.lucasdss.ftpmusic.app.data.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.lucasdss.ftpmusic.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
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

    override fun applicationId(): String = BuildConfig.APPLICATION_ID

    private suspend fun awaitAppUpdateInfo(): AppUpdateInfo = suspendCoroutine { cont ->
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}
