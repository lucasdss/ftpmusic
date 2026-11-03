package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaQueueItem

sealed class CastQueueAction {
    data class Add(val mediaItem: MediaItem, val beforeEntryId: Int? = null) : CastQueueAction()

    /** One RMC insert for many items — single optimistic revision / castAck (ADR-0111). */
    data class AddAll(val mediaItems: List<MediaItem>, val beforeEntryId: Int? = null) : CastQueueAction()

    /** One RMC remove for many items — receiver autoplay-tail prune (ADR-0053). */
    data class RemoveAll(val entryIds: List<Int>) : CastQueueAction()

    /**
     * CP-7 Cast prune+append as one optimistic revision: remove then insert,
     * with a single final castAck (ADR-0111). Separate RemoveAll→AddAll would
     * commit on the first ack and leave the second unable to roll back.
     */
    data class RemoveThenAddAll(
        val entryIds: List<Int>,
        val mediaItems: List<MediaItem>,
        val beforeEntryId: Int? = null,
    ) : CastQueueAction()

    data class Remove(val entryId: Int) : CastQueueAction()
    data class Move(val entryId: Int, val beforeEntryId: Int?) : CastQueueAction()
    data class JumpTo(val entryId: Int) : CastQueueAction()
    data class ClearAndPlay(val mediaItems: List<MediaItem>, val startIndex: Int = 0, val startPositionMs: Long = 0L) :
        CastQueueAction()
}

/** Stable diagnostic token — never R8-minified simpleName alone (ADR-0111). */
internal fun castQueueActionDiag(action: CastQueueAction): String = when (action) {
    is CastQueueAction.Add -> "Add"

    is CastQueueAction.AddAll -> "AddAll count=${action.mediaItems.size}"

    is CastQueueAction.RemoveAll -> "RemoveAll count=${action.entryIds.size}"

    is CastQueueAction.RemoveThenAddAll ->
        "RemoveThenAddAll remove=${action.entryIds.size} add=${action.mediaItems.size}"

    is CastQueueAction.Remove -> "Remove"

    is CastQueueAction.Move -> "Move"

    is CastQueueAction.JumpTo -> "JumpTo"

    is CastQueueAction.ClearAndPlay -> "ClearAndPlay count=${action.mediaItems.size}"
}

interface CastQueueReceiver {
    val itemIds: Set<Int>

    fun insert(item: MediaQueueItem, beforeItemId: Int)
    fun insertAll(items: Array<MediaQueueItem>, beforeItemId: Int)
    fun remove(itemId: Int)

    /** Batch remove — one RMC revision (CP-7 autoplay-tail prune). */
    fun removeAll(itemIds: IntArray)

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

            is CastQueueAction.AddAll -> {
                if (action.mediaItems.isEmpty()) return
                val beforeId = resolve(action.beforeEntryId, receiver.itemIds)
                receiver.insertAll(action.mediaItems.map(toQueueItem).toTypedArray(), beforeId)
            }

            is CastQueueAction.RemoveAll -> {
                val itemIds = receiver.itemIds
                val resolved = action.entryIds.filter(itemIds::contains).toIntArray()
                // Absent ids mean the receiver already trimmed the prefix; never
                // send INVALID_ITEM_ID as a removal target.
                if (resolved.isNotEmpty()) receiver.removeAll(resolved)
            }

            is CastQueueAction.RemoveThenAddAll -> {
                val itemIds = receiver.itemIds
                val resolved = action.entryIds.filter(itemIds::contains).toIntArray()
                if (resolved.isNotEmpty()) receiver.removeAll(resolved)
                if (action.mediaItems.isEmpty()) return
                val beforeId = resolve(action.beforeEntryId, receiver.itemIds)
                receiver.insertAll(action.mediaItems.map(toQueueItem).toTypedArray(), beforeId)
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
