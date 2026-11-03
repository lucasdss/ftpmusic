package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.QueueJournalDao
import com.lucasdss.ftpmusic.app.data.model.Track
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * restoreQueue: origin from is_priority flags. context_size ignored.
 */
class RestoreQueueTest {

    private val mockAppContext: android.app.Application = mockk(relaxed = true)
    private val mockPlayer: Player = mockk(relaxed = true)
    private val queueManager = QueueManager(mockk())
    private val mockPersistenceManager: QueuePersistenceManager = mockk(relaxed = true)
    private val mockOfflineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val mockDownloadManager: DownloadManager = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)
    private val mockQueueJournalDao: QueueJournalDao = mockk(relaxed = true)

    private fun manager(): PlaybackManager = PlaybackManager(
        queueManager,
        mockPersistenceManager,
        mockOfflineModeManager,
        mockDownloadManager,
        castPreferences,
        mockAppContext,
        mockQueueJournalDao,
    )

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns Uri.EMPTY
        PlayerHolder.player = mockPlayer
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
        PlayerHolder.isCasting = false
    }

    private fun tracks(vararg ids: String): Pair<List<Track>, List<String>> {
        val t = ids.map { Track(it, "Title $it", duration = 100) }
        val u = ids.map { "http://s/$it" }
        return t to u
    }

    @Test
    fun `restoreQueue with isPriorityFlags keeps industry layout`() {
        val mgr = manager()
        val (tracks, urls) = tracks("c1", "p1", "c2")
        mgr.restoreQueue(
            tracks,
            urls,
            startIndex = 0,
            isPriorityFlags = listOf(false, true, false),
        )

        assertEquals(2, mgr.contextSize)
        assertEquals(1, mgr.priorityQueueSize)
        assertEquals(listOf("c1", "p1", "c2"), mgr.buildQueueStateFromDual().first.map { it.id })
    }

    @Test
    fun `restoreQueue without flags treats everything as context`() {
        val mgr = manager()
        val (tracks, urls) = tracks("a", "b", "c")

        mgr.restoreQueue(tracks, urls, startIndex = 0, contextSize = 2)

        assertEquals(3, mgr.contextSize)
        assertEquals(0, mgr.priorityQueueSize)
    }

    @Test
    fun `restoreQueue entry ids survive`() {
        val mgr = manager()
        val (tracks, urls) = tracks("c1", "p1", "c2")
        mgr.restoreQueue(
            tracks,
            urls,
            startIndex = 0,
            isPriorityFlags = listOf(false, true, false),
            entryIds = listOf(10, 11, 12),
            nextEntryId = 20,
        )

        assertEquals(listOf(10, 11, 12), mgr.entryIds())
        assertEquals(20, mgr.peekNextEntryId())
    }

    @Test
    fun `restoreQueue with empty tracks is a no-op`() {
        val mgr = manager()

        mgr.restoreQueue(emptyList(), emptyList(), startIndex = 0, contextSize = 0)

        assertEquals(0, mgr.contextSize)
        assertEquals(0, mgr.priorityQueueSize)
    }

    @Test
    fun `restoreQueue starts atomically at saved index and position`() {
        val player = mockk<androidx.media3.common.Player>(relaxed = true)
        PlayerHolder.player = player
        val mgr = manager()
        val (tracks, urls) = tracks("t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7", "animal")

        mgr.restoreQueue(tracks, urls, startIndex = 8, positionMs = 142629L)

        verify {
            player.setMediaItems(any<List<androidx.media3.common.MediaItem>>(), 8, 142629L)
        }
        verify(exactly = 0) { player.seekToDefaultPosition(any()) }
    }

    @Test
    fun `restoreQueue mismatched flags falls back to all context`() {
        val mgr = manager()
        val (tracks, urls) = tracks("a", "b", "c")

        mgr.restoreQueue(
            tracks,
            urls,
            startIndex = 1,
            isPriorityFlags = listOf(false, true), // wrong size
        )

        assertEquals(3, mgr.contextSize)
        assertEquals(0, mgr.priorityQueueSize)
    }

    @Test
    fun `restoreQueue mismatched entryIds ignored and zero ids skip stamp`() {
        val mgr = manager()
        val (tracks, urls) = tracks("a", "b", "c")

        mgr.restoreQueue(
            tracks,
            urls,
            startIndex = 0,
            isPriorityFlags = listOf(false, true, false),
            entryIds = listOf(10, 0, 12),
            nextEntryId = 20,
        )

        val ids = mgr.entryIds()
        assertEquals(3, ids.size)
        assertEquals(10, ids[0])
        assertTrue("zero entryId must not stamp 0", ids[1] != 0)
        assertEquals(12, ids[2])
        assertEquals(20, mgr.peekNextEntryId())
    }

    @Test
    fun `restoreQueue with empty urls is a no-op`() {
        val mgr = manager()
        val ok = mgr.restoreQueue(
            listOf(Track("a", "A", duration = 100)),
            emptyList(),
            startIndex = 0,
        )
        assertFalse(ok)
        assertEquals(0, mgr.contextSize)
    }

    @Test
    fun `restoreQueue tracks urls coerce preserves prefix flags and entryIds`() {
        val mgr = manager()
        val tracks = listOf(
            Track("a", "A", duration = 100),
            Track("b", "B", duration = 100),
            Track("c", "C", duration = 100),
        )
        // urls shorter → coerce to 2; parallel arrays sized for original 3 → take(2)
        val ok = mgr.restoreQueue(
            tracks,
            listOf("http://s/a", "http://s/b"),
            startIndex = 1,
            isPriorityFlags = listOf(false, true, false),
            entryIds = listOf(10, 11, 12),
            nextEntryId = 20,
        )
        assertTrue(ok)
        assertEquals(1, mgr.contextSize)
        assertEquals(1, mgr.priorityQueueSize)
        assertEquals(listOf("a", "b"), mgr.buildQueueStateFromDual().first.map { it.id })
        assertEquals(listOf(10, 11), mgr.entryIds())
        assertEquals(20, mgr.peekNextEntryId())
    }

    @Test
    fun `restoreQueue returns true when queue rebuilt`() {
        val mgr = manager()
        val (tracks, urls) = tracks("a", "b")
        assertTrue(mgr.restoreQueue(tracks, urls, startIndex = 0))
        assertEquals(2, mgr.contextSize)
    }

    @Test
    fun `restoreQueue stamps autoplay flags from persistence`() {
        val mgr = manager()
        val (tracks, urls) = tracks("a", "ap1", "ap2")
        assertTrue(
            mgr.restoreQueue(
                tracks,
                urls,
                startIndex = 0,
                isPriorityFlags = listOf(false, false, false),
                isAutoplayFlags = listOf(false, true, true),
            ),
        )
        assertEquals(listOf(false, true, true), mgr.isAutoplayFlags())
    }

    @Test
    fun `restoreQueue coerces autoplay flags with tracks urls skew`() {
        val mgr = manager()
        val tracks = listOf(
            Track("a", "A", duration = 100),
            Track("b", "B", duration = 100),
            Track("c", "C", duration = 100),
        )
        assertTrue(
            mgr.restoreQueue(
                tracks,
                listOf("http://s/a", "http://s/b"),
                startIndex = 0,
                isAutoplayFlags = listOf(false, true, true),
            ),
        )
        assertEquals(listOf(false, true), mgr.isAutoplayFlags())
    }

    /** base + [autoplayCount] autoplay rows, in merged order. */
    private fun autoplayTracks(autoplayCount: Int): Triple<List<Track>, List<String>, List<Boolean>> {
        val tracks = listOf(Track("base", "Base", duration = 100)) +
            (1..autoplayCount).map { Track("ap$it", "AP $it", duration = 100) }
        val urls = tracks.map { "http://s/${it.id}" }
        val flags = listOf(false) + List(autoplayCount) { true }
        return Triple(tracks, urls, flags)
    }

    @Test
    fun `restoreQueue trims a legacy oversized autoplay tail and persists it`() {
        val mgr = manager()
        val (tracks, urls, autoplay) = autoplayTracks(65)
        val mergedSlot = slot<List<androidx.media3.common.MediaItem>>()
        val indexSlot = slot<Int>()

        assertTrue(
            mgr.restoreQueue(
                tracks,
                urls,
                startIndex = 65,
                isPriorityFlags = List(tracks.size) { false },
                isAutoplayFlags = autoplay,
            ),
        )

        // base + the 50 newest autoplay rows.
        assertEquals(51, mgr.dualQueueSize)
        assertEquals(50, mgr.isAutoplayFlags().count { it })
        val mediaIds = mgr.currentQueueMediaIds()
        assertTrue(mediaIds.contains("base"))
        assertFalse(mediaIds.contains("ap1"))
        assertTrue(mediaIds.contains("ap65"))
        verify { mockPlayer.setMediaItems(capture(mergedSlot), capture(indexSlot), any()) }
        assertEquals(51, mergedSlot.captured.size)
        assertEquals(50, indexSlot.captured)
        coVerify(timeout = 2_000) {
            mockPersistenceManager.save(
                match { it.size == 51 },
                any(),
                eq(50),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `restoreQueue trim protects the playing autoplay row and remaps its index`() {
        val mgr = manager()
        val (tracks, urls, autoplay) = autoplayTracks(65)
        val mergedSlot = slot<List<androidx.media3.common.MediaItem>>()
        val indexSlot = slot<Int>()

        assertTrue(
            mgr.restoreQueue(
                tracks,
                urls,
                startIndex = 1,
                isPriorityFlags = List(tracks.size) { false },
                isAutoplayFlags = autoplay,
            ),
        )

        assertEquals(51, mgr.dualQueueSize)
        assertTrue(mgr.currentQueueMediaIds().contains("ap1"))
        assertFalse(mgr.currentQueueMediaIds().contains("ap2"))
        verify { mockPlayer.setMediaItems(capture(mergedSlot), capture(indexSlot), any()) }
        assertEquals("ap1", mergedSlot.captured[indexSlot.captured].mediaId)
    }
}
