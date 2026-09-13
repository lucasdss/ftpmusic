package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimistic queue delegate wrapping [DualQueueManager].
 * Captures snapshots before mutations so the UI can roll back
 * if the background persistence or Cast sync fails.
 */
@Singleton
class OptimisticQueueDelegate @Inject constructor(private val dualQueue: DualQueueManager) {

    /** Snapshot of the merged queue at a point in time, with queue separation preserved. */
    data class Snapshot(
        val items: List<MediaItem>,
        val contextItems: List<MediaItem> = items,
        val priorityItems: List<MediaItem> = emptyList(),
        internal val orderedEntries: List<DualQueueManager.SnapshotEntry> = emptyList(),
        val revision: Long = 0L,
    )

    private var lastSnapshot: Snapshot? = null
    private var nextRevision = 0L

    /** Capture the current queue state for potential rollback. */
    fun snapshot(): Snapshot {
        nextRevision++
        return Snapshot(
            items = dualQueue.getMerged(),
            contextItems = dualQueue.contextItems(),
            priorityItems = dualQueue.priorityItems(),
            orderedEntries = dualQueue.snapshotEntries(),
            revision = nextRevision,
        ).also { lastSnapshot = it }
    }

    /** Get the last captured snapshot, or null if none. */
    fun getLastSnapshot(): Snapshot? = lastSnapshot

    /** Roll back to the last captured snapshot. No-op if no snapshot exists. */
    fun rollback(): Boolean {
        val snap = lastSnapshot ?: return false
        if (snap.orderedEntries.isNotEmpty()) {
            dualQueue.restoreEntries(snap.orderedEntries)
        } else {
            dualQueue.setContext(snap.contextItems)
            dualQueue.clearPriority()
            snap.priorityItems.forEach { dualQueue.addToQueue(it) }
        }
        lastSnapshot = null
        return true
    }

    /** Commit the optimistic change (discard snapshot). */
    fun commit() {
        lastSnapshot = null
    }

    fun rollbackIf(revision: Long): Boolean {
        if (lastSnapshot?.revision != revision) return false
        return rollback()
    }

    fun commitIf(revision: Long) {
        if (lastSnapshot?.revision == revision) commit()
    }

    /** Play Next with optimistic snapshot. */
    fun playNextOptimistic(item: MediaItem, afterIndex: Int? = null): Snapshot {
        val snap = snapshot()
        dualQueue.playNext(item, afterIndex)
        return snap
    }

    /** Add to Queue with optimistic snapshot. */
    fun addToQueueOptimistic(item: MediaItem): Snapshot {
        val snap = snapshot()
        dualQueue.addToQueue(item)
        return snap
    }

    /** Remove with optimistic snapshot. */
    fun removeOptimistic(index: Int): Pair<MediaItem?, Snapshot> {
        val snap = snapshot()
        val removed = dualQueue.remove(index)
        return Pair(removed, snap)
    }

    /** Move with optimistic snapshot. */
    fun moveOptimistic(from: Int, to: Int): Pair<Boolean, Snapshot> {
        val snap = snapshot()
        val success = dualQueue.move(from, to)
        return Pair(success, snap)
    }

    /** Set context with optimistic snapshot. */
    fun setContextOptimistic(items: List<MediaItem>): Snapshot {
        val snap = snapshot()
        dualQueue.setContext(items)
        return snap
    }
}
