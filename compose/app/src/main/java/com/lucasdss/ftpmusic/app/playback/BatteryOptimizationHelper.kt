package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Deep-links to battery optimization Settings (ADR-0119).
 * Prefer OS screens over [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS]
 * (Play policy friction). Unrestricted battery restores FGS-from-background
 * exemption used by A2DP → [BtAutoplayStarter] after deep sleep.
 */
object BatteryOptimizationHelper {

    /** True when OS will not Doze-restrict this package (or API has no PowerManager). */
    fun isUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Open battery-optimization list, else app-details.
     * @return true if an intent was started
     */
    fun openBatterySettings(context: Context): Boolean {
        if (openIgnoreBatteryOptimizationSettings(context)) return true
        return openAppDetails(context)
    }

    fun openIgnoreBatteryOptimizationSettings(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Exception) {
        false
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
