package com.lucasdss.ftpmusic.app.data.db

/**
 * Pure first-login / SyncingScreen exit predicates.
 * Success requires this run's [SyncStatus.runId] plus [SyncStatus.drainComplete] —
 * never `phase=complete` alone (soft-stop and stale latch both emit complete).
 */
object SyncExitGate {
    fun shouldAutoComplete(status: SyncStatus, expectedRunId: Long): Boolean =
        expectedRunId > 0L &&
            !status.isRunning &&
            status.phase == "complete" &&
            status.drainComplete &&
            status.runId == expectedRunId

    fun isSoftStopped(status: SyncStatus, expectedRunId: Long): Boolean =
        expectedRunId > 0L &&
            !status.isRunning &&
            status.phase == "complete" &&
            !status.drainComplete &&
            status.runId == expectedRunId

    fun isErrorForRun(status: SyncStatus, expectedRunId: Long): Boolean =
        expectedRunId > 0L &&
            !status.isRunning &&
            status.phase == "error" &&
            status.runId == expectedRunId
}
