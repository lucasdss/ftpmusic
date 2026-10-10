package com.lucasdss.ftpmusic.app.data.update

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/** Pure mapping from Play [AppUpdateInfo] → [UpdateCheckResult] (unit-testable). */
object UpdateCheckMapper {
    fun fromPlayInfo(info: AppUpdateInfo): UpdateCheckResult = when (info.updateAvailability()) {
        UpdateAvailability.UPDATE_AVAILABLE ->
            UpdateCheckResult.UpdateAvailable(
                availableVersionCode = info.availableVersionCode(),
                flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            )

        UpdateAvailability.UPDATE_NOT_AVAILABLE -> UpdateCheckResult.UpToDate

        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
            UpdateCheckResult.UpdateAvailable(
                availableVersionCode = info.availableVersionCode(),
                flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            )

        else ->
            UpdateCheckResult.Unavailable("Play Store could not determine update status")
    }
}
