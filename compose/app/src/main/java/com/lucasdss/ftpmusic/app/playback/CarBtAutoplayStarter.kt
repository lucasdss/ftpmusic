package com.lucasdss.ftpmusic.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Starts [MediaService] with [MediaServiceStartRequest.ACTION_CAR_BT_AUTOPLAY].
 * On FGS background-start denial, posts a high-priority tap-to-resume notification.
 */
object CarBtAutoplayStarter {
    const val RESUME_CHANNEL_ID = "ftpmusic_car_bt_resume"
    const val RESUME_NOTIFICATION_ID = 1002

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
        MediaServiceStartRequest.carBtAutoplayRequested = true
        val intent = Intent(context, MediaService::class.java).apply {
            action = MediaServiceStartRequest.ACTION_CAR_BT_AUTOPLAY
        }
        return try {
            startForegroundService(context, intent)
            StartResult.Started
        } catch (e: Exception) {
            MediaServiceStartRequest.foregroundRequested = false
            val fgsBlocked = sdkInt >= Build.VERSION_CODES.S &&
                e.javaClass.name.endsWith("ForegroundServiceStartNotAllowedException")
            if (!fgsBlocked) {
                android.util.Log.w("ftpmusic-carbt", "startForegroundService failed: ${e.message}")
            }
            // Always offer a user-visible path so deep-sleep connect is not silent.
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
            context.getString(com.lucasdss.ftpmusic.app.R.string.car_bt_resume_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(com.lucasdss.ftpmusic.app.R.string.car_bt_resume_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun postResumeNotification(context: Context) {
        ensureResumeChannel(context)
        val launch = Intent(context, MediaService::class.java).apply {
            action = MediaServiceStartRequest.ACTION_CAR_BT_AUTOPLAY
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, RESUME_NOTIFICATION_ID, launch, flags)
        } else {
            PendingIntent.getService(context, RESUME_NOTIFICATION_ID, launch, flags)
        }
        val notification = NotificationCompat.Builder(context, RESUME_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(context.getString(com.lucasdss.ftpmusic.app.R.string.car_bt_resume_notif_title))
            .setContentText(context.getString(com.lucasdss.ftpmusic.app.R.string.car_bt_resume_notif_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(
                0,
                context.getString(com.lucasdss.ftpmusic.app.R.string.car_bt_resume_notif_action),
                pending,
            )
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(RESUME_NOTIFICATION_ID, notification)
    }

    enum class StartResult {
        Started,
        NotificationFallback,
    }
}
