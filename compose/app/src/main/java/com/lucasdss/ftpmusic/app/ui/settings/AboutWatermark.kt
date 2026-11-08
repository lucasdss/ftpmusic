package com.lucasdss.ftpmusic.app.ui.settings

/** About “last updated” watermark — prefer the newer of metadata vs full-drain stamps. */
object AboutWatermark {
    fun displayMs(lastMetadataSyncMs: Long, lastFullSyncMs: Long): Long =
        maxOf(lastMetadataSyncMs.coerceAtLeast(0L), lastFullSyncMs.coerceAtLeast(0L))

    fun neverSynced(lastMetadataSyncMs: Long, lastFullSyncMs: Long): Boolean =
        displayMs(lastMetadataSyncMs, lastFullSyncMs) <= 0L
}
