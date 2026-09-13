package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.Player

/**
 * Map a Cast receiver's `currentItemId` to the LOCAL (full) queue index by
 * matching [queueEntryId] (Cast `setItemId`). Returns null when the item is
 * unknown or `INVALID_ITEM_ID`.
 *
 * Fallback: `mediaId.hashCode()` for unstamped pre-0040 items.
 */
internal fun resolveLocalIndexFromReceiverId(player: Player, receiverItemId: Int): Int? {
    if (receiverItemId == com.google.android.gms.cast.MediaQueueItem.INVALID_ITEM_ID) return null
    val byEntry = (0 until player.mediaItemCount).firstOrNull { i ->
        player.getMediaItemAt(i).queueEntryId() == receiverItemId
    }
    if (byEntry != null) return byEntry
    return (0 until player.mediaItemCount).firstOrNull { i ->
        val item = player.getMediaItemAt(i)
        !item.hasQueueEntryId() && item.mediaId.hashCode() == receiverItemId
    }
}
