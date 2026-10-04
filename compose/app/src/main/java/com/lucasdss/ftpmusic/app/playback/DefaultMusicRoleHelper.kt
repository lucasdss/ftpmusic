package com.lucasdss.ftpmusic.app.playback

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Guides the user toward making FTP Music the preferred system media app.
 *
 * Note: [android.app.role.RoleManager] has no ROLE_MUSIC on current SDKs
 * (API 36). We deep-link to default-apps / app-details settings instead and
 * rely on [MediaLibrarySession.setSessionActivity] + an active MediaSession
 * for lock-screen / car steering-wheel routing (ADR-0071).
 */
object DefaultMusicRoleHelper {
    const val REQUEST_CODE_MUSIC_ROLE = 7101

    /** Always true — we can at least open default-apps or app-details settings. */
    fun isRoleAvailable(context: Context): Boolean = true

    /**
     * Platform cannot report a music default role. Always false; UI uses subtitle
     * copy that does not claim "already default".
     */
    @Suppress("UNUSED_PARAMETER")
    fun isRoleHeld(context: Context): Boolean = false

    /**
     * Open OS default-apps settings (preferred) or app-details as fallback.
     * @return true if an intent was started
     */
    fun requestDefaultMusicApp(activity: Activity): Boolean {
        if (openDefaultAppsSettings(activity)) return true
        return openAppDetails(activity)
    }

    fun openDefaultAppsSettings(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Exception) {
        openAppDetails(context)
    }

    fun openAppDetails(context: Context): Boolean = try {
        val uri = Uri.fromParts("package", context.packageName, null)
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
