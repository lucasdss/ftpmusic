package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Industry dual-queue: CONTEXT prefix..anchor + PRIORITY + CONTEXT suffix. */
class DualQueueManagerTest {

    private lateinit var manager: DualQueueManager

    private fun item(id: String) = MediaItem.Builder()
        .setMediaId(id)
        .setUri("http://ex.com/$id")
        .build()

    @Before
    fun setup() {
        manager = DualQueueManager()
    }

    @Test
    fun `initial state is empty`() {
        assertTrue(manager.isEmpty)
        assertEquals(0, manager.size)
        assertEquals(0, manager.contextSize)
        assertEquals(0, manager.prioritySize)
    }

    @Test
    fun `setContext replaces context queue`() {
        manager.setContext(listOf(item("ctx1"), item("ctx2")))
        assertEquals(2, manager.size)
        assertEquals(2, manager.contextSize)
        assertEquals(0, manager.prioritySize)
        // anchor=0 → ctx1 | (empty pri) | ctx2
        assertEquals(listOf("ctx1", "ctx2"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `setContext preserves priority before context suffix`() {
        manager.addToQueue(item("pri1"))
        manager.setContext(listOf(item("a"), item("b"), item("c")), startIndex = 0)
        // industry: a + pri1 + b,c
        assertEquals(listOf("a", "pri1", "b", "c"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `setContext with startIndex places priority after current`() {
        manager.addToQueue(item("pri1"))
        manager.setContext(listOf(item("a"), item("b"), item("c")), startIndex = 1)
        assertEquals(listOf("a", "b", "pri1", "c"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `setContext with empty list clears context`() {
        manager.setContext(listOf(item("ctx1")))
        manager.setContext(emptyList())
        assertEquals(0, manager.contextSize)
    }

    @Test
    fun `setSingleContext replaces context with one item`() {
        manager.setContext(listOf(item("ctx1"), item("ctx2")))
        manager.setSingleContext(item("single"))
        assertEquals(1, manager.contextSize)
        assertEquals("single", manager.getMerged().first().mediaId)
    }

    @Test
    fun `playNext inserts at priority queue front`() {
        manager.setContext(listOf(item("ctx1")))
        manager.addToQueue(item("pri1"))
        manager.playNext(item("next"))
        assertEquals(listOf("ctx1", "next", "pri1"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `playNext mid-album stays before remainder`() {
        manager.setContext(listOf(item("a"), item("b"), item("c")), startIndex = 0)
        manager.playNext(item("next"), afterIndex = 0)
        assertEquals(listOf("a", "next", "b", "c"), manager.getMerged().map { it.mediaId })
        assertEquals(3, manager.contextSize)
        assertEquals(1, manager.prioritySize)
    }

    @Test
    fun `multiple playNext inserts in order`() {
        manager.playNext(item("second"))
        manager.playNext(item("first"))
        assertEquals(listOf("first", "second"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `addToQueue appends to priority before suffix`() {
        manager.setContext(listOf(item("a"), item("b")), startIndex = 0)
        manager.addToQueue(item("pri1"))
        manager.addToQueue(item("pri2"))
        assertEquals(listOf("a", "pri1", "pri2", "b"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `addToQueue with no context`() {
        manager.addToQueue(item("pri1"))
        assertEquals(listOf("pri1"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `addToContext appends to context suffix`() {
        manager.setContext(listOf(item("c1"), item("c2")))
        manager.addToContext(item("n1"))
        assertEquals(3, manager.contextSize)
        assertEquals(0, manager.prioritySize)
        assertEquals("n1", manager.getMerged().last().mediaId)
    }

    @Test
    fun `addAllToContext preserves priority before new suffix`() {
        manager.setContext(listOf(item("c1")))
        manager.addToQueue(item("p1"))
        manager.addAllToContext(listOf(item("n1")))
        // context=[c1,n1] anchor=0 → c1 + p1 + n1
        assertEquals(listOf("c1", "p1", "n1"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `playNextAll inserts multiple at front of priority`() {
        manager.setContext(listOf(item("ctx1")))
        manager.addToQueue(item("pri1"))
        manager.playNextAll(listOf(item("a"), item("b")))
        assertEquals(listOf("ctx1", "a", "b", "pri1"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `remove priority by merged index`() {
        manager.setContext(listOf(item("ctx1"), item("ctx2")), startIndex = 0)
        manager.addToQueue(item("pri1"))
        // merge: ctx1, pri1, ctx2 — remove(1)=pri1
        val removed = manager.remove(1)
        assertEquals("pri1", removed?.mediaId)
        assertEquals(listOf("ctx1", "ctx2"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `remove context suffix by merged index`() {
        manager.setContext(listOf(item("ctx1"), item("ctx2")), startIndex = 0)
        manager.addToQueue(item("pri1"))
        // merge: ctx1, pri1, ctx2 — remove(2)=ctx2
        val removed = manager.remove(2)
        assertEquals("ctx2", removed?.mediaId)
        assertEquals(listOf("ctx1", "pri1"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `remove returns null for out-of-bounds index`() {
        assertNull(manager.remove(-1))
        assertNull(manager.remove(0))
        manager.setContext(listOf(item("ctx1")))
        assertNull(manager.remove(5))
    }

    @Test
    fun `move within context prefix`() {
        manager.setContext(listOf(item("a"), item("b"), item("c")), startIndex = 2)
        // merge all context contiguous when no priority: a,b,c
        manager.move(0, 2)
        assertEquals(listOf("b", "c", "a"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `clearPriority preserves context`() {
        manager.setContext(listOf(item("a"), item("b")), startIndex = 0)
        manager.addToQueue(item("p"))
        manager.clearPriority()
        assertEquals(0, manager.prioritySize)
        assertEquals(listOf("a", "b"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `clearContext preserves priority`() {
        manager.setContext(listOf(item("a")))
        manager.addToQueue(item("b"))
        manager.clearContext()
        assertEquals(listOf("b"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `repeated context switches keep priority before new suffix`() {
        manager.addToQueue(item("pri1"))
        manager.setContext(listOf(item("ctx1")))
        manager.playNext(item("pri2"))
        manager.setContext(listOf(item("new_ctx")))
        assertEquals(listOf("new_ctx", "pri2", "pri1"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `merged list is a defensive copy`() {
        manager.setContext(listOf(item("a")))
        val merged = manager.getMerged()
        manager.addToQueue(item("b"))
        assertEquals(1, merged.size)
    }

    @Test
    fun `setContext does NOT clear accumulated priority — getMerged grows`() {
        val context = (1..113).map { item("ctx$it") }
        manager.setContext(context)
        manager.addAllToQueue((1..50).map { item("pri$it") })
        manager.setContext(context)
        assertEquals(50, manager.prioritySize)
        assertEquals(163, manager.size)
    }

    @Test
    fun `clearPriority then setContext restores exactly the context`() {
        val context = (1..113).map { item("ctx$it") }
        manager.setContext(context)
        manager.addAllToQueue((1..50).map { item("pri$it") })
        manager.clearPriority()
        manager.setContext(context)
        assertEquals(0, manager.prioritySize)
        assertEquals(113, manager.size)
    }

    @Test
    fun `industry order priority before context suffix`() {
        manager.setContext(listOf(item("ctx1"), item("ctx2")), startIndex = 0)
        manager.addAllToQueue(listOf(item("pri1"), item("pri2")))
        assertEquals(listOf("ctx1", "pri1", "pri2", "ctx2"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `restoreLists places priority after startIndex`() {
        manager.restoreLists(
            contextItems = listOf(item("a"), item("b"), item("c")),
            priorityItems = listOf(item("p1")),
            startIndex = 1,
        )
        assertEquals(listOf("a", "b", "p1", "c"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `originIsContextFlags match industry merge`() {
        manager.setContext(listOf(item("a"), item("b")), startIndex = 0)
        manager.addToQueue(item("p"))
        assertEquals(listOf(true, false, true), manager.originIsContextFlags())
    }

    @Test
    fun `stamps distinct entry ids for duplicate mediaId`() {
        manager.setContext(listOf(item("same")))
        manager.playNext(item("same"))
        val ids = manager.entryIds()
        assertEquals(2, ids.size)
        assertTrue(ids[0] != ids[1])
        assertTrue(ids.all { it > 0 })
    }

    @Test
    fun `restore keeps existing entry ids and adopts next`() {
        val a = item("a").withQueueEntryId(10)
        val p = item("p").withQueueEntryId(11)
        manager.restoreEntries(
            listOf(
                DualQueueManager.SnapshotEntry(a, isContext = true),
                DualQueueManager.SnapshotEntry(p, isContext = false),
            ),
        )
        manager.adoptNextEntryId(20)
        assertEquals(listOf(10, 11), manager.entryIds())
        assertEquals(20, manager.peekNextEntryId())
        manager.addToQueue(item("n"))
        assertEquals(20, manager.entryIds().last())
        assertEquals(21, manager.peekNextEntryId())
    }

    @Test
    fun `clear does not reset nextEntryId`() {
        manager.setContext(listOf(item("a"), item("b")))
        val next = manager.peekNextEntryId()
        manager.clear()
        assertEquals(next, manager.peekNextEntryId())
    }

    @Test
    fun `playNext after priority row inserts after that row`() {
        manager.setContext(listOf(item("a"), item("b")), startIndex = 0)
        manager.addToQueue(item("p1"))
        manager.addToQueue(item("p2"))
        // merge a,p1,p2,b — afterIndex=2 is p2
        manager.playNext(item("n"), afterIndex = 2)
        assertEquals(listOf("a", "p1", "p2", "n", "b"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `move across sections changes origin`() {
        manager.setContext(listOf(item("a"), item("b")), startIndex = 0)
        manager.addToQueue(item("p"))
        // a,p,b — drag p into suffix
        assertTrue(manager.move(1, 2))
        assertEquals(listOf("a", "b", "p"), manager.getMerged().map { it.mediaId })
        assertFalse(manager.move(0, 0))
        assertFalse(manager.move(-1, 1))
    }

    @Test
    fun `remove last context and empty restore`() {
        manager.setContext(listOf(item("only")))
        assertEquals("only", manager.remove(0)?.mediaId)
        assertTrue(manager.isEmpty)
        manager.restoreEntries(emptyList())
        assertTrue(manager.isEmpty)
        manager.playNextAll(emptyList())
        manager.addAllToQueue(emptyList())
        manager.addAllToContext(emptyList())
        val next = manager.peekNextEntryId()
        manager.adoptNextEntryId(0)
        assertEquals(next, manager.peekNextEntryId())
    }

    @Test
    fun `alignAnchorToMergedIndex when playing context suffix`() {
        manager.setContext(listOf(item("a"), item("b"), item("c")), startIndex = 0)
        manager.addToQueue(item("p"))
        // a,p,b,c
        manager.alignAnchorToMergedIndex(3)
        assertEquals(listOf("a", "b", "c", "p"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `setContextWithSource and setSingleContextWithSource`() {
        manager.setContextWithSource(listOf(item("a")), "Album", 0)
        assertEquals("Album", manager.contextSource)
        manager.setSingleContextWithSource(item("s"), "Single")
        assertEquals("Single", manager.contextSource)
        assertEquals("s", manager.getMerged().single().mediaId)
    }

    @Test
    fun `remove context prefix lowers anchor`() {
        manager.setContext(listOf(item("a"), item("b"), item("c")), startIndex = 1)
        manager.addToQueue(item("p"))
        // a,b,p,c — remove a (index 0)
        manager.remove(0)
        assertEquals(listOf("b", "p", "c"), manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `alignAnchor no-op on empty or priority current`() {
        manager.alignAnchorToMergedIndex(0)
        manager.setContext(listOf(item("a")), startIndex = 0)
        manager.addToQueue(item("p"))
        val before = manager.getMerged().map { it.mediaId }
        manager.alignAnchorToMergedIndex(1)
        assertEquals(before, manager.getMerged().map { it.mediaId })
    }

    @Test
    fun `clear no-ops on empty`() {
        manager.clear()
        manager.clearContext()
        manager.clearPriority()
        assertTrue(manager.isEmpty)
    }
}
