package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OptimisticQueueDelegateTest {

    private lateinit var dualQueue: DualQueueManager
    private lateinit var delegate: OptimisticQueueDelegate

    private fun item(id: String) = MediaItem.Builder()
        .setMediaId(id)
        .setUri("http://ex.com/$id")
        .build()

    @Before
    fun setup() {
        dualQueue = DualQueueManager()
        delegate = OptimisticQueueDelegate(dualQueue)
    }

    @Test
    fun `snapshot captures current merged queue`() {
        dualQueue.setContext(listOf(item("a"), item("b")))
        val snap = delegate.snapshot()
        assertEquals(listOf("a", "b"), snap.items.map { it.mediaId })
    }

    @Test
    fun `snapshot is independent of subsequent mutations`() {
        dualQueue.setContext(listOf(item("a")))
        val snap = delegate.snapshot()
        dualQueue.addToQueue(item("b"))
        assertEquals(1, snap.items.size)
    }

    @Test
    fun `rollback restores previous state`() {
        dualQueue.setContext(listOf(item("a")))
        delegate.snapshot()
        dualQueue.setContext(listOf(item("x"), item("y")))
        delegate.rollback()
        assertEquals(listOf("a"), dualQueue.getMerged().map { it.mediaId })
    }

    @Test
    fun `rollback is no-op when no snapshot exists`() {
        assertFalse(delegate.rollback())
    }

    @Test
    fun `commit discards snapshot`() {
        dualQueue.setContext(listOf(item("a")))
        delegate.snapshot()
        delegate.commit()
        assertFalse("rollback must fail after commit", delegate.rollback())
    }

    @Test
    fun `playNextOptimistic captures snapshot before mutation`() {
        dualQueue.setContext(listOf(item("a"), item("b")))
        val snap = delegate.playNextOptimistic(item("c"))
        // Current state changed
        assertEquals(listOf("a", "c", "b"), dualQueue.getMerged().map { it.mediaId })
        // Snapshot captures state before change
        assertEquals(listOf("a", "b"), snap.items.map { it.mediaId })
    }

    @Test
    fun `playNextOptimistic followed by rollback reverts`() {
        dualQueue.setContext(listOf(item("a")))
        delegate.playNextOptimistic(item("b"))
        assertEquals(listOf("a", "b"), dualQueue.getMerged().map { it.mediaId })
        delegate.rollback()
        assertEquals(listOf("a"), dualQueue.getMerged().map { it.mediaId })
    }

    @Test
    fun `addToQueueOptimistic followed by rollback reverts`() {
        dualQueue.setContext(listOf(item("a")))
        delegate.addToQueueOptimistic(item("b"))
        assertEquals(listOf("a", "b"), dualQueue.getMerged().map { it.mediaId })
        delegate.rollback()
        assertEquals(listOf("a"), dualQueue.getMerged().map { it.mediaId })
    }

    @Test
    fun `removeOptimistic followed by rollback reverts`() {
        dualQueue.setContext(listOf(item("a"), item("b"), item("c")))
        val (removed, _) = delegate.removeOptimistic(1)
        assertEquals("b", removed?.mediaId)
        assertEquals(listOf("a", "c"), dualQueue.getMerged().map { it.mediaId })
        delegate.rollback()
        assertEquals(listOf("a", "b", "c"), dualQueue.getMerged().map { it.mediaId })
    }

    @Test
    fun `moveOptimistic followed by rollback reverts`() {
        dualQueue.setContext(listOf(item("a"), item("b"), item("c")))
        delegate.moveOptimistic(0, 2)
        assertEquals(listOf("b", "c", "a"), dualQueue.getMerged().map { it.mediaId })
        delegate.rollback()
        assertEquals(listOf("a", "b", "c"), dualQueue.getMerged().map { it.mediaId })
    }

    @Test
    fun `setContextOptimistic followed by rollback reverts`() {
        dualQueue.setContext(listOf(item("a")))
        dualQueue.addToQueue(item("p1"))
        delegate.setContextOptimistic(listOf(item("x"), item("y")))
        assertEquals(listOf("x", "p1", "y"), dualQueue.getMerged().map { it.mediaId })
        delegate.rollback()
        assertEquals(listOf("a", "p1"), dualQueue.getMerged().map { it.mediaId })
    }

    @Test
    fun `chain of optimistic ops commits correctly`() {
        dualQueue.setContext(listOf(item("a")))
        delegate.addToQueueOptimistic(item("b")).let { delegate.commit() }
        delegate.addToQueueOptimistic(item("c")).let { delegate.commit() }
        delegate.playNextOptimistic(item("d")).let { delegate.commit() }
        assertEquals(listOf("a", "d", "b", "c"), dualQueue.getMerged().map { it.mediaId })
        // After commits, rollback should do nothing
        delegate.getLastSnapshot()?.let { delegate.rollback() }
        assertEquals(listOf("a", "d", "b", "c"), dualQueue.getMerged().map { it.mediaId })
    }

    @Test
    fun `getLastSnapshot returns null when no snapshot taken`() {
        assertNull(delegate.getLastSnapshot())
    }

    // ── Edge case tests ─────────────────────────────────────────────────

    @Test
    fun `rollback on empty queue`() {
        // Snapshot taken on empty queue, then add, then rollback
        delegate.snapshot()
        dualQueue.addToQueue(item("a"))
        delegate.rollback()
        assertTrue(dualQueue.isEmpty)
    }

    @Test
    fun `multiple rollbacks only revert once`() {
        dualQueue.setContext(listOf(item("a")))
        delegate.snapshot()
        dualQueue.setContext(listOf(item("b")))
        delegate.rollback() // back to [a]
        delegate.rollback() // no more snapshots — no-op
        assertEquals(listOf("a"), dualQueue.getMerged().map { it.mediaId })
    }

    @Test
    fun `setContextOptimistic preserves priority queue on rollback`() {
        dualQueue.addToQueue(item("pri"))
        delegate.setContextOptimistic(listOf(item("ctx")))
        delegate.rollback()
        assertEquals(listOf("pri"), dualQueue.getMerged().map { it.mediaId })
    }

    @Test
    fun `removeOptimistic from empty queue returns null`() {
        val (removed, _) = delegate.removeOptimistic(0)
        assertNull(removed)
    }

    @Test
    fun `moveOptimistic with invalid indices returns false`() {
        dualQueue.setContext(listOf(item("a")))
        val (success, _) = delegate.moveOptimistic(0, 5)
        assertFalse(success)
    }

    @Test
    fun `rollbackIf ignores stale revision`() {
        dualQueue.setContext(listOf(item("a")))
        val first = delegate.snapshot()
        dualQueue.addToQueue(item("b"))
        val second = delegate.snapshot()
        dualQueue.addToQueue(item("c"))
        assertFalse(delegate.rollbackIf(first.revision))
        assertEquals(listOf("a", "b", "c"), dualQueue.getMerged().map { it.mediaId })
        assertTrue(delegate.rollbackIf(second.revision))
        assertEquals(listOf("a", "b"), dualQueue.getMerged().map { it.mediaId })
    }

    @Test
    fun `commitIf only clears matching revision`() {
        dualQueue.setContext(listOf(item("a")))
        val snap = delegate.snapshot()
        delegate.commitIf(snap.revision + 1)
        assertNotNull(delegate.getLastSnapshot())
        delegate.commitIf(snap.revision)
        assertNull(delegate.getLastSnapshot())
    }
}
