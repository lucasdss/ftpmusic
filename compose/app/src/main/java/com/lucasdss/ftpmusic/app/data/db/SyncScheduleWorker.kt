package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog
import com.lucasdss.ftpmusic.app.data.update.AppUpdateProbe
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * WorkManager CoroutineWorker that delegates to [MetadataSyncWorker]
 * for periodic library metadata sync (ADR-0045: DELTA or weekly FULL;
 * ADR-0068: await completion + retry on error phase;
 * ADR-0139: soft-skip → Result.retry except already_running / cooldown;
 * ADR-0140: after success, probe Play for update-available issue tray).
 *
 * Scheduled via WorkManager in [com.lucasdss.ftpmusic.app.FtpmusicApp].
 */
@HiltWorker
class SyncScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val metadataSyncWorker: MetadataSyncWorker,
    private val appUpdateProbe: AppUpdateProbe,
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
            val job = metadataSyncWorker.syncNowAsync(forceTrackResync = false, mode = mode)
            if (job == null) {
                val reason = metadataSyncWorker.lastSkipReason()
                Log.d("ftpmusic-work", "Periodic sync skipped reason=$reason")
                DiagnosticLog.d("ftpmusic-work", "periodic sync skipped reason=$reason")
                return when (reason) {
                    // Cooldown cannot succeed until wall-clock elapses — avoid hot WM retry.
                    "already_running", "cooldown" -> Result.success()

                    "offline", "unreachable", "cellular_policy" -> Result.retry()

                    else -> Result.retry()
                }
            }
            job.join()
            val phase = metadataSyncWorker.status.value.phase
            if (phase == "error") {
                Log.w("ftpmusic-work", "Periodic sync ended in error phase — retry")
                DiagnosticLog.w("ftpmusic-work", "periodic sync error phase — retry")
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
            // ADR-0140: piggyback Play update check; never fail WM on probe errors.
            try {
                appUpdateProbe.probe()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ftpmusic-work", "Update probe after sync failed: ${e.message}")
                DiagnosticLog.w("ftpmusic-work", "update probe failed: ${e.message}")
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ftpmusic-work", "Sync work failed: ${e.message}", e)
            DiagnosticLog.e("ftpmusic-work", "periodic sync failed: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
