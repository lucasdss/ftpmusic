package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem

/**
 * Immutable Up-Next snapshot for Android Auto browse Queue (ADR-0138).
 * Captured on the main thread; consumed by [AutoBrowseCatalog] on IO.
 */
data class AutoQueueBrowseRow(
    val entryId: Int,
    val mediaId: String,
    val title: String,
    val artist: String?,
    val isPriority: Boolean,
    val isLate: Boolean,
    val isAutoplay: Boolean,
)

data class AutoQueueBrowseSnapshot(val revision: Long, val currentIndex: Int, val rows: List<AutoQueueBrowseRow>)

fun interface AutoQueueSnapshotProvider {
    fun snapshot(): AutoQueueBrowseSnapshot
}

/** Map Dual [MediaItem] → browse row (entryId required). */
fun MediaItem.toAutoQueueBrowseRow(isPriority: Boolean, isLate: Boolean, isAutoplay: Boolean): AutoQueueBrowseRow? {
    val entryId = queueEntryId().takeIf { it > 0 } ?: return null
    val mediaId = mediaId?.takeIf { it.isNotBlank() } ?: return null
    return AutoQueueBrowseRow(
        entryId = entryId,
        mediaId = mediaId,
        title = mediaMetadata.title?.toString().orEmpty().ifBlank { mediaId },
        artist = mediaMetadata.artist?.toString(),
        isPriority = isPriority,
        isLate = isLate,
        isAutoplay = isAutoplay,
    )
}
