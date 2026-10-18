package com.lucasdss.ftpmusic.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lucasdss.ftpmusic.app.MainActivity

/**
 * Starts [MediaService] with [MediaServiceStartRequest.ACTION_BT_AUTOPLAY].
 * On FGS background-start denial, posts a high-priority tap-to-resume notification.
 * Empty-queue / offline-no-cache post a DEFAULT-priority informational notice instead.
 */
object BtAutoplayStarter {
    const val RESUME_CHANNEL_ID = "ftpmusic_bt_resume"
    const val RESUME_NOTIFICATION_ID = 1002

    /** ADR-0148: informational channel (empty queue / offline-no-cache) — never HIGH. */
    const val INFO_CHANNEL_ID = "ftpmusic_bt_info"
    const val INFO_NOTIFICATION_ID = 1003

    fun startAutoplay(context: Context): StartResult = startAutoplay(
        context = context,
        startForegroundService = { ctx, intent -> ctx.startForegroundService(intent) },
        postFallback = { postResumeNotification(it) },
    )

    /**
     * Testable entry: inject start + fallback to exercise success / FGS-blocked
     * paths without a real Service (JaCoCo-friendly JVM tests).
     */
    internal fun startAutoplay(
        context: Context,
        startForegroundService: (Context, Intent) -> Unit,
        postFallback: (Context) -> Unit,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): StartResult {
        MediaServiceStartRequest.foregroundRequested = true
        // ADR-0133: preserve an in-flight Resume tap across A2DP reconnect storms;
        // otherwise clear so a stale tap cannot leak into a pure background wake.
        val preservePendingTap = MediaServiceStartRequest.btAutoplayRequested &&
            MediaServiceStartRequest.userInitiatedResume
        MediaServiceStartRequest.btAutoplayRequested = true
        if (!preservePendingTap) {
            MediaServiceStartRequest.userInitiatedResume = false
        }
        val intent = Intent(context, MediaService::class.java).apply {
            action = MediaServiceStartRequest.ACTION_BT_AUTOPLAY
        }
        return try {
            startForegroundService(context, intent)
            android.util.Log.i(
                "ftpmusic-bt",
                "startForegroundService OK action=${intent.action} sdk=$sdkInt",
            )
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
                "ftpmusic-bt",
                "fgsBlocked=false result=Started sdk=$sdkInt",
            )
            StartResult.Started
        } catch (e: Exception) {
            MediaServiceStartRequest.foregroundRequested = false
            val fgsBlocked = sdkInt >= Build.VERSION_CODES.S &&
                e.javaClass.name.endsWith("ForegroundServiceStartNotAllowedException")
            android.util.Log.w(
                "ftpmusic-bt",
                "startForegroundService failed fgsBlocked=$fgsBlocked sdk=$sdkInt: ${e.message}",
            )
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
                "ftpmusic-bt",
                "fgsBlocked=$fgsBlocked result=NotificationFallback sdk=$sdkInt",
            )
            // ADR-0087: car never sees this; phone tap / MediaButton resumption remains
            // the recovery path when Doze blocks background FGS.
            postFallback(context)
            StartResult.NotificationFallback
        }
    }

    fun ensureResumeChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(RESUME_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            RESUME_CHANNEL_ID,
            context.getString(com.lucasdss.ftpmusic.app.R.string.bt_resume_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(com.lucasdss.ftpmusic.app.R.string.bt_resume_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun ensureInfoChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(INFO_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            INFO_CHANNEL_ID,
            context.getString(com.lucasdss.ftpmusic.app.R.string.bt_info_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(com.lucasdss.ftpmusic.app.R.string.bt_info_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** True when the OS will deliver app notifications (ADR-0101). */
    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Cancel Resume notif after successful tap-play / autoplay (ADR-0133). */
    fun cancelResumeNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        manager.cancel(RESUME_NOTIFICATION_ID)
    }

    fun postResumeNotification(
        context: Context,
        @StringRes bodyRes: Int = com.lucasdss.ftpmusic.app.R.string.bt_resume_notif_body,
    ) {
        ensureResumeChannel(context)
        if (!notificationsEnabled(context)) {
            android.util.Log.w(
                "ftpmusic-bt",
                "Resume notif blocked — POST_NOTIFICATIONS / notifications disabled; " +
                    "MediaSession seat / MediaButton resumption still required",
            )
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
                "ftpmusic-bt",
                "result=notif_disabled",
            )
            BtResumeEvents.record("notif_disabled")
        }
        val launch = Intent(context, MediaService::class.java).apply {
            action = MediaServiceStartRequest.ACTION_BT_AUTOPLAY
            // ADR-0133: tap must play on API ≥37 — distinguish from background A2DP.
            putExtra(MediaServiceStartRequest.EXTRA_USER_INITIATED_RESUME, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, RESUME_NOTIFICATION_ID, launch, flags)
        } else {
            PendingIntent.getService(context, RESUME_NOTIFICATION_ID, launch, flags)
        }
        val notification = NotificationCompat.Builder(context, RESUME_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(context.getString(com.lucasdss.ftpmusic.app.R.string.bt_resume_notif_title))
            .setContentText(context.getString(bodyRes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(
                0,
                context.getString(com.lucasdss.ftpmusic.app.R.string.bt_resume_notif_action),
                pending,
            )
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(RESUME_NOTIFICATION_ID, notification)
    }

    /**
     * ADR-0148: informational notice for empty-queue / offline-no-cache.
     * No "Resume" action; content intent opens the app. Uses a DEFAULT-priority
     * channel (never [RESUME_CHANNEL_ID]) so it cannot recreate the friction the
     * seat-only change removes.
     */
    fun postInformationalNotification(context: Context, @StringRes titleRes: Int, @StringRes bodyRes: Int) {
        ensureInfoChannel(context)
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, INFO_NOTIFICATION_ID, launch, flags)
        val notification = NotificationCompat.Builder(context, INFO_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(bodyRes))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(INFO_NOTIFICATION_ID, notification)
    }

    enum class StartResult {
        Started,
        NotificationFallback,
    }
}
