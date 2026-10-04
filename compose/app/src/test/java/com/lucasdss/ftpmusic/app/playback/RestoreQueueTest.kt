package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
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
        PlayerHolder.player = mockk(relaxed = true)
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
        mgr.restoreQueue(
            listOf(Track("a", "A", duration = 100)),
            emptyList(),
            startIndex = 0,
        )
        assertEquals(0, mgr.contextSize)
    }
}
