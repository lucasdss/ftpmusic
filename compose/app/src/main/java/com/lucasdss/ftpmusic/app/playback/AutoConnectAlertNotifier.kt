package com.lucasdss.ftpmusic.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.session.MediaSession
import com.lucasdss.ftpmusic.app.MainActivity
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.data.security.SecureStorage

/**
 * Phone notice when Android Auto (or Automotive) first connects as a media
 * controller (ADR-0158). Separate from Bluetooth A2DP autoplay.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AutoConnectAlertNotifier {
    const val CHANNEL_ID = "ftpmusic_android_auto"
    const val NOTIFICATION_ID = 1004

    fun maybeNotify(
        context: Context,
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        storage: SecureStorage,
    ) {
        if (!session.isAutoCompanionController(controller) &&
            !session.isAutomotiveController(controller)
        ) {
            return
        }
        if (!alertsEnabled(storage)) return
        val fingerprint = controllerFingerprint(controller)
        if (fingerprint.isBlank()) return
        val seen = seenFingerprints(storage)
        if (fingerprint in seen) return
        postNotification(context)
        storage.put(
            SecureStorage.KEY_AA_SEEN_CONTROLLERS,
            (seen + fingerprint).sorted().joinToString(","),
        )
        android.util.Log.i(
            "ftpmusic-aa",
            "Auto availability alert posted fingerprint=$fingerprint",
        )
    }

    fun alertsEnabled(storage: SecureStorage): Boolean =
        storage.get(SecureStorage.KEY_AA_CONNECT_ALERT_ENABLED)?.toBooleanStrictOrNull() ?: true

    fun setAlertsEnabled(storage: SecureStorage, enabled: Boolean) {
        storage.put(SecureStorage.KEY_AA_CONNECT_ALERT_ENABLED, enabled.toString())
    }

    fun controllerFingerprint(controller: MediaSession.ControllerInfo): String {
        val pkg = controller.packageName?.trim().orEmpty()
        return if (pkg.isNotEmpty()) pkg else "uid:${controller.uid}"
    }

    fun seenFingerprints(storage: SecureStorage): Set<String> =
        storage.get(SecureStorage.KEY_AA_SEEN_CONTROLLERS)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.aa_connect_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.aa_connect_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun postNotification(context: Context) {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            android.util.Log.w("ftpmusic-aa", "Auto availability alert blocked — notifications off")
            return
        }
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(context.getString(R.string.aa_connect_notif_title))
            .setContentText(context.getString(R.string.aa_connect_notif_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            android.util.Log.w("ftpmusic-aa", "Auto availability notify SecurityException: ${e.message}")
        }
    }
}
