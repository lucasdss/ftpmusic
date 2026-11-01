package com.lucasdss.ftpmusic.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.LibrarySyncMode
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foreground self-healing for a silent WorkManager (report §11.4 / §6.4, F1/F6).
 *
 * Runs once per app foreground (see `FtpmusicApp`), decides with the pure
 * [SyncHealthPolicy], and at most fires one DELTA sync. It is deliberately not a
 * second scheduler: it cannot help while the app is closed, and adding an
 * AlarmManager/WM watchdog would inherit the same throttling it is meant to
 * survive.
 *
 * Storm safety is layered:
 *  - every gate inside [MetadataSyncWorker.syncNowAsync] still applies
 *    (offline, reachable, cellular policy, 5 min cooldown, isSyncing CAS);
 *  - this guard's own [PREF_FALLBACK_ATTEMPT_MS]/[PREF_FALLBACK_STREAK] are
 *    **persisted**, unlike the in-memory cooldown, so cold starts cannot reset
 *    the backoff (1 h → 6 h → 24 h);
 *  - the stamp is written *before* the trigger, so a kill mid-sync does not
 *    re-arm the same epoch.
 */
@Singleton
class SyncHealthGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataSyncWorker: MetadataSyncWorker,
    private val offlineModeManager: OfflineModeManager,
    private val storage: SecureStorage,
) {
    companion object {
        private const val TAG = "ftpmusic-synchealth"

        /** Last fallback trigger (persisted; survives process death). */
        const val PREF_FALLBACK_ATTEMPT_MS = "sync_fallback_attempt_ms"

        /** Consecutive fallback triggers since the last successful sync. */
        const val PREF_FALLBACK_STREAK = "sync_fallback_streak"

        private const val DEFAULT_INTERVAL_HOURS = 12
    }

    /** Test seam; production reads the wall clock. */
    internal var nowMs: () -> Long = { System.currentTimeMillis() }

    /**
     * Called from the process lifecycle's ON_START. Must stay cheap: prefs reads
     * only, and [MetadataSyncWorker.syncNowAsync] returns a Job without blocking.
     */
    fun onAppForeground(): SyncHealthPolicy.Verdict {
        val prefs = prefs()
        val now = nowMs()
        val lastSuccess = maxOf(
            prefs.getLong(MetadataSyncWorker.PREF_LAST_DELTA_SYNC_MS, 0L),
            prefs.getLong(MetadataSyncWorker.PREF_LAST_FULL_SYNC_MS, 0L),
        )
        val lastFallback = prefs.getLong(PREF_FALLBACK_ATTEMPT_MS, 0L)
        val streak = resetStreakIfRecovered(prefs, lastSuccess, lastFallback)
        val intervalHours = storage.get(SecureStorage.KEY_SYNC_INTERVAL_HOURS)
            ?.toIntOrNull()
            ?.coerceIn(1, 24)
            ?: DEFAULT_INTERVAL_HOURS
        val signals = SyncHealthPolicy.Signals(
            nowMs = now,
            lastSuccessMs = lastSuccess,
            lastWmAttemptMs = prefs.getLong(MetadataSyncWorker.PREF_LAST_WM_ATTEMPT_MS, 0L),
            lastFallbackAttemptMs = lastFallback,
            fallbackStreak = streak,
            intervalMs = intervalHours * 3_600_000L,
            offline = offlineModeManager.isOfflineEnabled(),
            reachable = com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder.isReachable.value,
        )
        val verdict = SyncHealthPolicy.decide(signals)
        DiagnosticLog.d(
            TAG,
            "sync-health verdict=${SyncHealthPolicy.tag(verdict)} " +
                "silenceMs=${signals.nowMs - maxOf(signals.lastSuccessMs, signals.lastWmAttemptMs)} " +
                "intervalH=$intervalHours streak=$streak skipReason=" +
                (prefs.getString(MetadataSyncWorker.PREF_LAST_WM_SKIP_REASON, "") ?: "-").ifEmpty { "-" },
        )
        if (verdict is SyncHealthPolicy.Verdict.Trigger) {
            // Stamp first: a process kill between here and the sync completing
            // must not allow the same failure epoch to retry immediately.
            prefs.edit()
                .putLong(PREF_FALLBACK_ATTEMPT_MS, now)
                .putInt(PREF_FALLBACK_STREAK, streak + 1)
                .apply()
            metadataSyncWorker.syncNowAsync(mode = LibrarySyncMode.DELTA)
        }
        return verdict
    }

    private fun resetStreakIfRecovered(
        prefs: SharedPreferences,
        lastSuccessMs: Long,
        lastFallbackAttemptMs: Long,
    ): Int {
        val stored = prefs.getInt(PREF_FALLBACK_STREAK, 0)
        if (stored > 0 && lastSuccessMs > lastFallbackAttemptMs) {
            prefs.edit().putInt(PREF_FALLBACK_STREAK, 0).apply()
            return 0
        }
        return stored
    }

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences(MetadataSyncWorker.PREFS_NAME, Context.MODE_PRIVATE)
}
