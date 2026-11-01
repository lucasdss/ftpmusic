package com.lucasdss.ftpmusic.app.data.sync

/**
 * Decides whether the app should compensate for a silent WorkManager.
 *
 * ADR-0045 / ADR-0109 make `SyncScheduleWorker` the only *automatic* library
 * sync trigger. Periodic work can legitimately be deferred for days (OEM
 * battery managers, Doze, job quota), and from inside the process we never
 * witness the failure — we only observe an absence of evidence. So the decision
 * is a pure comparison of persisted stamps, made at a moment the app is alive:
 *
 *  - [Signals.lastSuccessMs]      — last completed FULL/DELTA (persisted).
 *  - [Signals.lastWmAttemptMs]    — last `SyncScheduleWorker` heartbeat
 *    (`MetadataSyncWorker.recordWmAttempt`). Note: WM's own
 *    `WorkInfo.runAttemptCount` is *not* usable here — it is per-run and resets
 *    on success.
 *  - [Signals.lastFallbackAttemptMs] / [Signals.fallbackStreak] — persisted
 *    fallback state, so a process kill cannot reset the backoff (the existing
 *    `MIN_SYNC_COOLDOWN_MS` and reachability gates are in-memory).
 *
 * Silence is `now - max(success, heartbeat)`: an attempt that ran but failed
 * (server down, offline) counts as a sign of life, so a dead server never
 * produces a fallback storm — the failure is surfaced, not retried harder.
 */
object SyncHealthPolicy {

    /** Extra slack past the configured interval before calling WM silent. */
    const val DEFAULT_GRACE_MS: Long = 6L * 60 * 60 * 1000

    private val BACKOFF_STEPS_MS = longArrayOf(
        60L * 60 * 1000, // 1 h
        6L * 60 * 60 * 1000, // 6 h
        24L * 60 * 60 * 1000, // 24 h (capped)
    )

    data class Signals(
        val nowMs: Long,
        val lastSuccessMs: Long,
        val lastWmAttemptMs: Long,
        val lastFallbackAttemptMs: Long,
        val fallbackStreak: Int,
        val intervalMs: Long,
        val offline: Boolean,
        val reachable: Boolean,
    )

    sealed interface Verdict {
        /** No sync has ever completed and WM has never run — first-run handles it. */
        data object NeverRan : Verdict

        /** Within the interval (+ grace) — WorkManager is not overdue. */
        data class NotDue(val silenceMs: Long) : Verdict

        data object SkippedOffline : Verdict

        data object SkippedUnreachable : Verdict

        /** A fallback already fired for this failure epoch; wait until [retryAtMs]. */
        data class Backoff(val retryAtMs: Long) : Verdict

        /** WM is silent past the deadline — compensate with a DELTA sync. */
        data class Trigger(val silenceMs: Long, val streak: Int) : Verdict
    }

    fun backoffFor(streak: Int): Long = BACKOFF_STEPS_MS[streak.coerceIn(0, BACKOFF_STEPS_MS.lastIndex)]

    fun decide(s: Signals): Verdict {
        if (s.offline) return Verdict.SkippedOffline
        if (!s.reachable) return Verdict.SkippedUnreachable
        val lastLife = maxOf(s.lastSuccessMs, s.lastWmAttemptMs)
        if (lastLife <= 0L) return Verdict.NeverRan
        val silenceMs = (s.nowMs - lastLife).coerceAtLeast(0L)
        val intervalMs = s.intervalMs.coerceAtLeast(1L)
        if (silenceMs < intervalMs + DEFAULT_GRACE_MS) return Verdict.NotDue(silenceMs)
        val retryAtMs = s.lastFallbackAttemptMs + backoffFor(s.fallbackStreak)
        if (s.lastFallbackAttemptMs > 0L && s.nowMs < retryAtMs) return Verdict.Backoff(retryAtMs)
        return Verdict.Trigger(silenceMs, s.fallbackStreak)
    }

    /** Stable short token for diagnostics (never free text, never a URL). */
    fun tag(verdict: Verdict): String = when (verdict) {
        Verdict.NeverRan -> "never"
        is Verdict.NotDue -> "fresh"
        Verdict.SkippedOffline -> "offline"
        Verdict.SkippedUnreachable -> "unreachable"
        is Verdict.Backoff -> "backoff"
        is Verdict.Trigger -> "trigger"
    }
}
