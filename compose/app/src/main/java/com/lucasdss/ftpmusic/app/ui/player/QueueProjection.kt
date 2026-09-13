package com.lucasdss.ftpmusic.app.ui.player

import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.playback.queueEntryId

data class QueueTrackMetadata(val title: String, val artist: String?, val album: String?)

data class QueueProjectionItem(
    val id: String,
    val index: Int,
    val title: String,
    val artist: String?,
    val album: String?,
    val coverArtUrl: String?,
    val durationMs: Long,
    val userRating: Int?,
    val isCurrent: Boolean,
    val entryId: Int = 0,
    val isPriority: Boolean = false,
)

/** Converts the canonical Player timeline into immutable Queue UI values. */
object QueueProjection {
    fun project(
        player: Player,
        currentIndex: Int,
        trackLookup: (String) -> QueueTrackMetadata?,
    ): List<QueueProjectionItem> = project(player, currentIndex, trackLookup) { false }

    fun project(
        player: Player,
        currentIndex: Int,
        trackLookup: (String) -> QueueTrackMetadata?,
        isPriorityAt: (Int) -> Boolean,
    ): List<QueueProjectionItem> = (0 until player.mediaItemCount).map { index ->
        val item = player.getMediaItemAt(index)
        val cached = trackLookup(item.mediaId)
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        QueueProjectionItem(
            id = item.mediaId.ifEmpty { "item-$index" },
            index = index,
            title = metadata.title?.toString() ?: cached?.title ?: "Unknown",
            artist = metadata.artist?.toString() ?: cached?.artist,
            album = metadata.albumTitle?.toString() ?: cached?.album,
            coverArtUrl = metadata.artworkUri?.toString(),
            durationMs = extras?.getLong("duration") ?: 0L,
            userRating = extras?.getInt("userRating")?.takeIf { it > 0 },
            isCurrent = index == currentIndex,
            entryId = item.queueEntryId(),
            isPriority = isPriorityAt(index),
        )
    }
}
