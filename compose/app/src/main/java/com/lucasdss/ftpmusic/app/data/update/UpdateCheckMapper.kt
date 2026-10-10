package com.lucasdss.ftpmusic.app.data.update

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
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
            // Do not re-offer startFlexibleUpdate; observe install + completeUpdate.
            UpdateCheckResult.InProgress(availableVersionCode = info.availableVersionCode())

        else ->
            UpdateCheckResult.Unavailable("Play Store could not determine update status")
    }

    /** Map Play install status codes to seam events (null = ignore / no UI change). */
    fun fromInstallStatus(status: Int): FlexibleInstallEvent? = when (status) {
        InstallStatus.PENDING,
        InstallStatus.DOWNLOADING,
        InstallStatus.INSTALLING,
        -> FlexibleInstallEvent.Downloading

        InstallStatus.DOWNLOADED -> FlexibleInstallEvent.Downloaded

        InstallStatus.FAILED -> FlexibleInstallEvent.Failed("Play update download failed")

        InstallStatus.CANCELED -> FlexibleInstallEvent.Failed("Play update canceled")

        else -> null
    }
}
