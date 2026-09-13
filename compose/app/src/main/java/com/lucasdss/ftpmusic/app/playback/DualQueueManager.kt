package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal object QueueRevisionTracker {
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    fun notifyChanged() {
        _revision.update { it + 1 }
    }
}

/**
 * Canonical dual-queue aligned to Spotify / Apple Music:
 *
 * - CONTEXT = album/playlist/mix ("Continue Playing" / "Next from …")
 * - PRIORITY = user Queue ("Next in Queue")
 *
 * Merged playback order (never interleaved):
 * `[CONTEXT 0..anchor] + [PRIORITY] + [CONTEXT after anchor]`
 *
 * Upcoming after the current track is always PRIORITY then CONTEXT suffix.
 */
@Singleton
class DualQueueManager @Inject constructor() {

    private enum class Origin { CONTEXT, PRIORITY }

    private data class Entry(val item: MediaItem, val origin: Origin)

    data class SnapshotEntry(val item: MediaItem, val isContext: Boolean)

    private val context = mutableListOf<MediaItem>()
    private val priority = mutableListOf<MediaItem>()

    /** Inclusive index into [context] for the current context track (merge split). */
    private var contextAnchor = 0

    /** Next unused [queueEntryId]. Never reset on [clear] (Cast itemIds). */
    private var nextEntryId = 1

    private val entries = mutableListOf<Entry>()

    private fun stamp(item: MediaItem): MediaItem {
        val existing = item.queueEntryId()
        if (existing > 0) {
            if (existing >= nextEntryId) nextEntryId = existing + 1
            return item
        }
        val id = nextEntryId++
        return item.withQueueEntryId(id)
    }

    private fun stampAll(items: List<MediaItem>): List<MediaItem> = items.map { stamp(it) }

    private fun markChanged() {
        QueueRevisionTracker.notifyChanged()
    }

    private fun rebuildMerged() {
        entries.clear()
        if (context.isEmpty()) {
            entries.addAll(priority.map { Entry(it, Origin.PRIORITY) })
        } else {
            val anchor = contextAnchor.coerceIn(0, context.lastIndex)
            contextAnchor = anchor
            for (i in 0..anchor) {
                entries.add(Entry(context[i], Origin.CONTEXT))
            }
            entries.addAll(priority.map { Entry(it, Origin.PRIORITY) })
            for (i in (anchor + 1)..context.lastIndex) {
                entries.add(Entry(context[i], Origin.CONTEXT))
            }
        }
        markChanged()
    }

    /**
     * Replace CONTEXT; keep PRIORITY. [startIndex] is the playing track within
     * the new context — PRIORITY is inserted immediately after it (industry).
     */
    @Synchronized
    fun setContext(items: List<MediaItem>, startIndex: Int = 0) {
        context.clear()
        context.addAll(stampAll(items))
        contextAnchor = if (items.isEmpty()) {
            0
        } else {
            startIndex.coerceIn(0, items.lastIndex)
        }
        rebuildMerged()
    }

    @Synchronized
    fun setContextWithSource(items: List<MediaItem>, source: String, startIndex: Int = 0) {
        setContext(items, startIndex)
        contextSource = source
    }

    /** The name of the current context source (album, playlist, artist, etc.). */
    @Volatile
    var contextSource: String? = null
        private set

    @Synchronized
    fun setSingleContext(item: MediaItem) {
        setContext(listOf(item), 0)
    }

    @Synchronized
    fun setSingleContextWithSource(item: MediaItem, source: String) {
        setContext(listOf(item), 0)
        contextSource = source
    }

    /**
     * Play Next — insert at front of PRIORITY, or after [afterIndex] when that
     * index already sits inside the PRIORITY block.
     */
    @Synchronized
    fun playNext(item: MediaItem, afterIndex: Int? = null) {
        val insertAt = priorityInsertIndex(afterIndex)
        priority.add(insertAt, stamp(item))
        rebuildMerged()
    }

    @Synchronized
    fun playNextAll(items: List<MediaItem>, afterIndex: Int? = null) {
        if (items.isEmpty()) return
        val insertAt = priorityInsertIndex(afterIndex)
        priority.addAll(insertAt, stampAll(items))
        rebuildMerged()
    }

    /** Index within [priority] for a Play Next insert. */
    private fun priorityInsertIndex(afterIndex: Int?): Int {
        if (afterIndex == null || entries.isEmpty()) return 0
        val idx = afterIndex.coerceIn(-1, entries.lastIndex)
        if (idx < 0) return 0
        // Count how many PRIORITY entries sit at or before idx in merged list.
        var priBeforeOrAt = 0
        var seenPri = 0
        for (i in entries.indices) {
            if (entries[i].origin == Origin.PRIORITY) {
                if (i <= idx) priBeforeOrAt = seenPri + 1
                seenPri++
            }
        }
        // If afterIndex points at a PRIORITY row, insert after that row in PRIORITY.
        if (idx in entries.indices && entries[idx].origin == Origin.PRIORITY) {
            return priBeforeOrAt
        }
        // afterIndex in CONTEXT prefix (incl. anchor) or before PRIORITY → front of queue.
        return 0
    }

    /** Append to the end of the PRIORITY queue (Add to Queue). */
    @Synchronized
    fun addToQueue(item: MediaItem) {
        priority.add(stamp(item))
        rebuildMerged()
    }

    @Synchronized
    fun addAllToQueue(items: List<MediaItem>) {
        if (items.isEmpty()) return
        priority.addAll(stampAll(items))
        rebuildMerged()
    }

    /** Append to CONTEXT suffix (continuous-play extension). */
    @Synchronized
    fun addToContext(item: MediaItem) {
        context.add(stamp(item))
        if (context.size == 1) contextAnchor = 0
        rebuildMerged()
    }

    @Synchronized
    fun addAllToContext(items: List<MediaItem>) {
        if (items.isEmpty()) return
        context.addAll(stampAll(items))
        if (contextAnchor > context.lastIndex) contextAnchor = context.lastIndex.coerceAtLeast(0)
        rebuildMerged()
    }

    @Synchronized
    fun remove(index: Int): MediaItem? {
        if (index < 0 || index >= entries.size) return null
        val entry = entries[index]
        when (entry.origin) {
            Origin.PRIORITY -> {
                val pIdx = priorityIndexForMerged(index) ?: return null
                priority.removeAt(pIdx)
            }

            Origin.CONTEXT -> {
                val cIdx = contextIndexForMerged(index) ?: return null
                context.removeAt(cIdx)
                if (context.isEmpty()) {
                    contextAnchor = 0
                } else if (cIdx <= contextAnchor) {
                    contextAnchor = (contextAnchor - 1).coerceAtLeast(0)
                }
            }
        }
        rebuildMerged()
        return entry.item
    }

    /**
     * Move within merged list. Cross-section drag changes origin so the
     * industry contiguous layout is preserved after rebuild.
     */
    @Synchronized
    fun move(from: Int, to: Int): Boolean {
        if (from < 0 || from >= entries.size || to < 0 || to >= entries.size || from == to) return false
        val snapshot = entries.map { SnapshotEntry(it.item, it.origin == Origin.CONTEXT) }.toMutableList()
        val moved = snapshot.removeAt(from)
        snapshot.add(to, moved)
        restoreFromMergedSnapshot(snapshot)
        return true
    }

    @Synchronized
    fun getMerged(): List<MediaItem> = entries.map { it.item }

    @Synchronized
    internal fun snapshotEntries(): List<SnapshotEntry> =
        entries.map { SnapshotEntry(it.item, it.origin == Origin.CONTEXT) }

    /**
     * Restore from a merged snapshot. Contiguous PRIORITY block is extracted;
     * CONTEXT prefix length becomes the anchor (last CONTEXT before PRIORITY,
     * or last CONTEXT if no PRIORITY).
     */
    @Synchronized
    internal fun restoreEntries(snapshot: List<SnapshotEntry>) {
        restoreFromMergedSnapshot(snapshot)
    }

    private fun restoreFromMergedSnapshot(snapshot: List<SnapshotEntry>) {
        context.clear()
        priority.clear()
        if (snapshot.isEmpty()) {
            contextAnchor = 0
            rebuildMerged()
            return
        }
        // Industry normalize: collect CONTEXT in order and PRIORITY in order of appearance.
        val ctx = mutableListOf<MediaItem>()
        val pri = mutableListOf<MediaItem>()
        var anchor = -1
        var seenPriority = false
        for (s in snapshot) {
            if (s.isContext) {
                ctx.add(s.item)
                if (!seenPriority) anchor = ctx.lastIndex
            } else {
                seenPriority = true
                pri.add(s.item)
            }
        }
        context.addAll(stampAll(ctx))
        priority.addAll(stampAll(pri))
        contextAnchor = when {
            ctx.isEmpty() -> 0
            anchor >= 0 -> anchor
            else -> 0
        }
        rebuildMerged()
    }

    /**
     * Rebuild from separate lists (Room is_priority restore / legacy migrate).
     * [startIndex] is the playing index in the **merged** industry layout after rebuild.
     */
    @Synchronized
    fun restoreLists(contextItems: List<MediaItem>, priorityItems: List<MediaItem>, startIndex: Int = 0) {
        context.clear()
        context.addAll(stampAll(contextItems))
        priority.clear()
        priority.addAll(stampAll(priorityItems))
        contextAnchor = if (context.isEmpty()) 0 else startIndex.coerceIn(0, context.lastIndex)
        rebuildMerged()
        alignAnchorToMergedIndex(startIndex)
    }

    /** Ensure [mergedIndex] is the current item by adjusting contextAnchor when in CONTEXT. */
    @Synchronized
    fun alignAnchorToMergedIndex(mergedIndex: Int) {
        if (entries.isEmpty()) return
        val idx = mergedIndex.coerceIn(0, entries.lastIndex)
        if (entries[idx].origin == Origin.CONTEXT) {
            val cIdx = contextIndexForMerged(idx) ?: return
            contextAnchor = cIdx
            rebuildMerged()
        }
        // Playing inside PRIORITY: keep existing anchor (queue plays before suffix).
    }

    private fun priorityIndexForMerged(mergedIndex: Int): Int? {
        var p = 0
        for (i in 0..mergedIndex) {
            if (i >= entries.size) return null
            if (entries[i].origin == Origin.PRIORITY) {
                if (i == mergedIndex) return p
                p++
            }
        }
        return null
    }

    private fun contextIndexForMerged(mergedIndex: Int): Int? {
        var c = 0
        for (i in 0..mergedIndex) {
            if (i >= entries.size) return null
            if (entries[i].origin == Origin.CONTEXT) {
                if (i == mergedIndex) return c
                c++
            }
        }
        return null
    }

    @Synchronized
    internal fun contextItems(): List<MediaItem> = context.toList()

    @Synchronized
    internal fun priorityItems(): List<MediaItem> = priority.toList()

    /** Per-merged-index origin flags for persistence (true = CONTEXT). */
    @Synchronized
    fun originIsContextFlags(): List<Boolean> = entries.map { it.origin == Origin.CONTEXT }

    @Synchronized
    fun entryIds(): List<Int> = entries.map { it.item.queueEntryId() }

    fun peekNextEntryId(): Int = nextEntryId

    @Synchronized
    fun adoptNextEntryId(value: Int) {
        if (value > nextEntryId) nextEntryId = value
    }

    val size: Int get() = entries.size

    val contextSize: Int get() = context.size

    val prioritySize: Int get() = priority.size

    val isEmpty: Boolean get() = context.isEmpty() && priority.isEmpty()

    @Synchronized
    fun clear() {
        if (isEmpty) return
        context.clear()
        priority.clear()
        contextAnchor = 0
        rebuildMerged()
    }

    @Synchronized
    fun clearContext() {
        if (context.isEmpty()) return
        context.clear()
        contextAnchor = 0
        rebuildMerged()
    }

    @Synchronized
    fun clearPriority() {
        if (priority.isEmpty()) return
        priority.clear()
        rebuildMerged()
    }
}
