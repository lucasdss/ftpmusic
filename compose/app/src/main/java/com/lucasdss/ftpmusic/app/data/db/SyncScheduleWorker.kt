package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager CoroutineWorker that delegates to [MetadataSyncWorker]
 * for periodic library metadata sync.
 *
 * Scheduled via [SyncScheduler] in [com.lucasdss.ftpmusic.app.FtpmusicApp].
 */
@HiltWorker
class SyncScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val metadataSyncWorker: MetadataSyncWorker,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = try {
        // Periodic runs must NOT fire a full sync right after install or
        // update on a populated library: the cooldown bypass
        // (lastSyncFinishMs == 0) used to trigger a full sync then, and
        // the resulting DB write storm starved the Home loaders' Room
        // reads (eternal spinner). Fresh installs (empty DB) still sync.
        val hasMetadata = runCatching { metadataSyncWorker.hasMetadata() }.getOrDefault(false)
        if (hasMetadata) {
            Log.d("ftpmusic-work", "Periodic sync skipped — library already populated")
        } else {
            val started = metadataSyncWorker.syncNow()
            if (!started) {
                Log.d("ftpmusic-work", "Periodic sync skipped — already running")
            }
        }
        Result.success()
    } catch (e: Exception) {
        Log.w("ftpmusic-work", "Sync work failed: ${e.message}", e)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
