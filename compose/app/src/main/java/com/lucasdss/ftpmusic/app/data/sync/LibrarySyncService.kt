package com.lucasdss.ftpmusic.app.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lucasdss.ftpmusic.app.MainActivity
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service (`dataSync`) that keeps the process alive for long
 * uncapped library metadata syncs (ADR-0139). Started/stopped by
 * [MetadataSyncWorker] around the real sync Job — not a second sync engine.
 */
@AndroidEntryPoint
class LibrarySyncService : Service() {

    @Inject lateinit var metadataSyncWorker: MetadataSyncWorker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statusJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        promoteForeground(buildNotification(getString(R.string.library_sync_notif_body)))
        statusJob = scope.launch {
            metadataSyncWorker.status.collectLatest { status ->
                val body = when {
                    status.phase == "tracks" && status.albumTracksProgressTotal > 0 ->
                        "Tracks ${status.albumTracksProgress}/${status.albumTracksProgressTotal}"

                    status.phase.isNotBlank() && status.phase != "idle" ->
                        getString(R.string.library_sync_notif_phase, status.phase)

                    else -> getString(R.string.library_sync_notif_body)
                }
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(body))
                if (!status.isRunning &&
                    (status.phase == "complete" || status.phase == "error" || status.phase == "idle")
                ) {
                    stopSelfSafely()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelfSafely()

            else -> promoteForeground(
                buildNotification(getString(R.string.library_sync_notif_body)),
            )
        }
        return START_NOT_STICKY
    }

    /**
     * Android 15+ `dataSync` 6-hour timeout — stop cleanly instead of letting the
     * OS kill the FGS without tearing down our notification / collectors.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "onTimeout startId=$startId fgsType=$fgsType — stopping")
        DiagnosticLog.w(TAG, "onTimeout startId=$startId fgsType=$fgsType")
        stopSelfSafely()
    }

    override fun onDestroy() {
        statusJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopSelfSafely() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun promoteForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}")
            DiagnosticLog.w(TAG, "startForeground failed: ${e.message}")
        }
    }

    private fun buildNotification(body: String): Notification {
        val launch = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.library_sync_notif_title))
            .setContentText(body)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "ftpmusic-library-sync"
        const val CHANNEL_ID = "library_sync"
        const val NOTIFICATION_ID = 7102
        const val ACTION_START = "com.lucasdss.ftpmusic.app.action.LIBRARY_SYNC_START"
        const val ACTION_STOP = "com.lucasdss.ftpmusic.app.action.LIBRARY_SYNC_STOP"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.library_sync_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.library_sync_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        fun start(context: Context) {
            ensureChannel(context)
            val intent = Intent(context, LibrarySyncService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                DiagnosticLog.d(TAG, "startForegroundService OK")
            } catch (e: Exception) {
                val fgsBlocked = Build.VERSION.SDK_INT >= 31 &&
                    e.javaClass.name.endsWith("ForegroundServiceStartNotAllowedException")
                Log.w(TAG, "startForegroundService failed fgsBlocked=$fgsBlocked: ${e.message}")
                DiagnosticLog.w(
                    TAG,
                    "startForegroundService failed fgsBlocked=$fgsBlocked: ${e.message}",
                )
                // Fall back: sync continues in-process without FGS when OS blocks it.
            }
        }

        /** Prefer [Context.stopService] — never startService just to tear down. */
        fun stop(context: Context) {
            try {
                val stopped = context.stopService(Intent(context, LibrarySyncService::class.java))
                DiagnosticLog.d(TAG, "stopService stopped=$stopped")
            } catch (e: Exception) {
                Log.w(TAG, "stopService failed: ${e.message}")
            }
        }
    }
}
