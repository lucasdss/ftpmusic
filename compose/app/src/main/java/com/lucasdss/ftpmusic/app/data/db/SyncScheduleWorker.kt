package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager CoroutineWorker that delegates to [MetadataSyncWorker]
 * for periodic library metadata sync (ADR-0045: DELTA or weekly FULL).
 *
 * Scheduled via WorkManager in [com.lucasdss.ftpmusic.app.FtpmusicApp].
 */
@HiltWorker
class SyncScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val metadataSyncWorker: MetadataSyncWorker,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (!ReachabilityStateHolder.isReachable.value) {
                Log.d("ftpmusic-work", "Periodic sync deferred — server unreachable")
                DiagnosticLog.w("ftpmusic-work", "periodic sync deferred — unreachable")
                return Result.retry()
            }
            val mode = MetadataSyncWorker.resolvePeriodicMode(metadataSyncWorker.lastFullSyncMs())
            Log.d("ftpmusic-work", "Periodic sync starting mode=$mode")
            DiagnosticLog.d("ftpmusic-work", "periodic sync start mode=$mode")
            val started = metadataSyncWorker.syncNow(forceTrackResync = false, mode = mode)
            if (!started) {
                Log.d("ftpmusic-work", "Periodic sync skipped — already running, cooldown, or offline")
                DiagnosticLog.d("ftpmusic-work", "periodic sync skipped")
            }
            Result.success()
        } catch (e: Exception) {
            Log.w("ftpmusic-work", "Sync work failed: ${e.message}", e)
            DiagnosticLog.e("ftpmusic-work", "periodic sync failed: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
