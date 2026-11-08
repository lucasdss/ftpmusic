package com.lucasdss.ftpmusic.app.data.db

/**
 * Shared SplashRouter + [MetadataSyncWorker.start] predicate for incomplete track drain.
 * Ghost-album counts are diagnostic only — not part of this gate.
 */
object LibraryDrainState {
    fun isDrainIncomplete(lastDrainCompleteMs: Long, lastOutcome: String?): Boolean =
        lastDrainCompleteMs <= 0L ||
            lastOutcome == MetadataSyncWorker.SYNC_OUTCOME_SOFT_STOPPED
}
