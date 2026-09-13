package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaQueueItem

sealed class CastQueueAction {
    data class Add(val mediaItem: MediaItem, val beforeEntryId: Int? = null) : CastQueueAction()
    data class Remove(val entryId: Int) : CastQueueAction()
    data class Move(val entryId: Int, val beforeEntryId: Int?) : CastQueueAction()
    data class JumpTo(val entryId: Int) : CastQueueAction()
    data class ClearAndPlay(val mediaItems: List<MediaItem>, val startIndex: Int = 0, val startPositionMs: Long = 0L) :
        CastQueueAction()
}

interface CastQueueReceiver {
    val itemIds: Set<Int>

    fun insert(item: MediaQueueItem, beforeItemId: Int)
    fun remove(itemId: Int)
    fun move(itemId: Int, beforeItemId: Int)
    fun jumpTo(itemId: Int)

    /** Returns true when CastPlayer accepted the replacement. */
    fun replaceThroughPlayer(items: List<MediaItem>, startIndex: Int, startPositionMs: Long): Boolean

    fun load(items: List<MediaQueueItem>, startIndex: Int, startPositionMs: Long)
}

/**
 * Executes one canonical queue action against one Cast receiver.
 *
 * Entry IDs remain stable across receiver prefix trimming. Receiver-specific
 * integer IDs equal [queueEntryId] and are resolved only when the command executes.
 */
class CastQueueCommandExecutor(private val toQueueItem: (MediaItem) -> MediaQueueItem) {
    fun execute(action: CastQueueAction, receiver: CastQueueReceiver) {
        when (action) {
            is CastQueueAction.Add -> {
                val beforeId = resolve(action.beforeEntryId, receiver.itemIds)
                receiver.insert(toQueueItem(action.mediaItem), beforeId)
            }

            is CastQueueAction.Remove -> {
                resolveExisting(action.entryId, receiver.itemIds)?.let(receiver::remove)
            }

            is CastQueueAction.Move -> {
                val itemIds = receiver.itemIds
                val itemId = resolveExisting(action.entryId, itemIds) ?: return
                receiver.move(itemId, resolve(action.beforeEntryId, itemIds))
            }

            is CastQueueAction.JumpTo -> {
                resolveExisting(action.entryId, receiver.itemIds)?.let(receiver::jumpTo)
            }

            is CastQueueAction.ClearAndPlay -> {
                if (action.mediaItems.isEmpty()) return
                if (receiver.replaceThroughPlayer(
                        action.mediaItems,
                        action.startIndex,
                        action.startPositionMs,
                    )
                ) {
                    return
                }
                receiver.load(
                    action.mediaItems.map(toQueueItem),
                    action.startIndex,
                    action.startPositionMs,
                )
            }
        }
    }

    private fun resolve(entryId: Int?, itemIds: Set<Int>): Int =
        entryId?.takeIf(itemIds::contains) ?: MediaQueueItem.INVALID_ITEM_ID

    private fun resolveExisting(entryId: Int, itemIds: Set<Int>): Int? = entryId.takeIf(itemIds::contains)
}
