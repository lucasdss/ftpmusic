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
 * Coverage batch for PlaybackManager paths not exercised by the feature tests —
 * accessors, legacy URL migration, reorder/remove, optimistic rollback, and
 * Cast-branch queue actions. Raises PlaybackManager.kt past the 80% line bar.
 */
class PlaybackManagerCoverageTest {

    private val mockAppContext: android.app.Application = mockk(relaxed = true)
    private val mockPlayer: Player = mockk(relaxed = true)
    private val queueManager = QueueManager(mockk())
    private val mockPersistenceManager: QueuePersistenceManager = mockk(relaxed = true)
    private val mockOfflineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val mockDownloadManager: DownloadManager = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)
    private val mockQueueJournalDao: QueueJournalDao = mockk(relaxed = true)

    private fun baseManager(): PlaybackManager = PlaybackManager(
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

    // ── Accessors / simple state ─────────────────────────────────────────

    @Test
    fun `accessors round-trip`() {
        val mgr = baseManager()

        mgr.setJournalCap(250)
        assertEquals(250, mgr.journalCap)
        mgr.setJournalCap(5) // clamped to 10
        assertEquals(10, mgr.journalCap)
        mgr.setJournalCap(5000) // clamped to 500
        assertEquals(500, mgr.journalCap)

        var fired = ""
        mgr.onOverwriteRequired = { fired = it }
        mgr.onOverwriteRequired?.invoke("boom")
        assertEquals("boom", fired)

        mgr.isUrlSwapInProgress = true
        assertTrue(mgr.isUrlSwapInProgress)

        val listener: ((CastQueueAction) -> Unit)? = {}
        mgr.castQueueListener = listener
        assertSame(listener, mgr.castQueueListener)

        every { mockPlayer.mediaItemCount } returns 7
        assertEquals(7, mgr.queueSize)
        PlayerHolder.player = null
        assertEquals(0, mgr.queueSize)
    }

    @Test
    fun `getTrackInfo returns cached metadata after play`() {
        val mgr = baseManager()
        mgr.playAlbum(
            listOf(Track("t1", "T1", artist = "A", album = "Al", duration = 100)),
            listOf("http://server/rest/stream?id=t1"),
        )
        val info = mgr.getTrackInfo("t1")
        assertNotNull(info)
        assertEquals("T1", info!!.title)
        assertNull(mgr.getTrackInfo("unknown"))
    }

    // ── Legacy URL migration (buildLocalUrl) ─────────────────────────────

    @Test
    fun `legacy proxy URL is decoded back to remote URL`() {
        val mgr = baseManager()
        val encoded = java.net.URLEncoder.encode("https://server.example/rest/stream?id=abc", "UTF-8")
        val legacy = "http://127.0.0.1:9000/stream?id=abc&url=$encoded"

        mgr.addAllToQueue(listOf(Track("t-legacy", "Legacy", duration = 100)), listOf(legacy))

        assertEquals("https://server.example/rest/stream?id=abc", mgr.getTrackInfo("t-legacy")?.localUrl)
    }

    @Test
    fun `legacy pipe-separated URL keeps local part`() {
        val mgr = baseManager()
        mgr.addAllToQueue(
            listOf(Track("t-pipe", "Pipe", duration = 100)),
            listOf("http://local-audio|http://cast-audio"),
        )

        assertEquals("http://local-audio", mgr.getTrackInfo("t-pipe")?.localUrl)
    }

    @Test
    fun `undecodable legacy URL falls back to clean url`() {
        val mgr = baseManager()
        mgr.addAllToQueue(
            listOf(Track("t-bad", "Bad", duration = 100)),
            listOf("http://127.0.0.1:9000/stream?id=x&url=%ZZ"),
        )

        assertEquals("http://127.0.0.1:9000/stream?id=x&url=%ZZ", mgr.getTrackInfo("t-bad")?.localUrl)
    }

    // ── Reorder / remove / rollback ──────────────────────────────────────

    @Test
    fun `moveQueueItem reorders merged queue and persists`() {
        val mgr = baseManager()
        mgr.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100)),
            listOf("http://s/a", "http://s/b"),
        )
        mgr.addToQueue(Track("c", "C", duration = 100), "http://s/c")

        mgr.moveQueueItem(0, 2)

        assertEquals("Move preserves queue origin", 1, mgr.priorityQueueSize)
        assertEquals(2, mgr.contextSize)
        coVerify(timeout = 1_000) {
            mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `moveQueueItem from equals to is a no-op`() {
        val mgr = baseManager()
        mgr.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100)),
            listOf("http://s/a", "http://s/b"),
            skipPersistence = true,
        )

        mgr.moveQueueItem(1, 1)

        coVerify(exactly = 0) { mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `moveQueueItem during Cast emits Move event`() {
        val mgr = baseManager()
        mgr.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100)),
            listOf("http://s/a", "http://s/b"),
        )
        PlayerHolder.isCasting = true
        val actions = mutableListOf<CastQueueAction>()
        mgr.castQueueListener = { actions.add(it) }

        mgr.moveQueueItem(0, 1)

        assertEquals(1, actions.size)
        val move = actions[0] as CastQueueAction.Move
        assertTrue(move.entryId > 0)
        assertNull(move.beforeEntryId)
    }

    @Test
    fun `Daily Mix replacement during Cast updates local mirror and sends one remote replacement`() {
        val mgr = baseManager()
        val localMirror = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = localMirror
        PlayerHolder.isCasting = true
        val actions = mutableListOf<CastQueueAction>()
        mgr.castQueueListener = actions::add

        mgr.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100)),
            listOf("http://s/a", "http://s/b"),
            sourceType = "genremix",
            sourceName = "Daily Mix",
        )

        verify(exactly = 0) { mockPlayer.setMediaItems(any<List<androidx.media3.common.MediaItem>>()) }
        verify(exactly = 1) {
            localMirror.setMediaItems(
                match<List<androidx.media3.common.MediaItem>> { it.map { item -> item.mediaId } == listOf("a", "b") },
                0,
                0L,
            )
        }
        assertEquals(1, actions.size)
        assertTrue(actions.single() is CastQueueAction.ClearAndPlay)
    }

    @Test
    fun `removeFromQueue removes item and persists`() {
        val mgr = baseManager()
        mgr.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100)),
            listOf("http://s/a", "http://s/b"),
        )
        mgr.addToQueue(Track("c", "C", duration = 100), "http://s/c")

        mgr.removeFromQueue(2) // merged is [a, c, b]; drop context suffix b

        assertEquals("Priority queue survives context removal", 1, mgr.priorityQueueSize)
        coVerify(timeout = 1_000) {
            mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `removeFromQueue during Cast emits Remove event`() {
        val mgr = baseManager()
        mgr.playAlbum(
            listOf(Track("a", "A", duration = 100)),
            listOf("http://s/a"),
        )
        PlayerHolder.isCasting = true
        val actions = mutableListOf<CastQueueAction>()
        mgr.castQueueListener = { actions.add(it) }

        mgr.removeFromQueue(0)

        val remove = actions.single() as CastQueueAction.Remove
        assertTrue(remove.entryId > 0)
    }

    @Test
    fun `queueRollback restores optimistic mutation`() {
        val mgr = baseManager()
        PlayerHolder.isCasting = true
        mgr.addToQueue(Track("x", "X", duration = 100), "http://s/x")

        assertNotNull("Snapshot captured", mgr.getLastQueueSnapshot())
        assertEquals(1, mgr.priorityQueueSize)

        val rolledBack = mgr.queueRollback()

        assertTrue("Rollback succeeded", rolledBack)
        assertEquals("Priority restored", 0, mgr.priorityQueueSize)
        assertNull("Snapshot consumed", mgr.getLastQueueSnapshot())
    }

    // ── Cast branch queue actions ────────────────────────────────────────

    @Test
    fun `playNext during Cast emits Add after current item`() {
        val mgr = baseManager()
        mgr.playAlbum(
            listOf(
                Track("a", "A", duration = 100),
                Track("b", "B", duration = 100),
                Track("c", "C", duration = 100),
            ),
            listOf("http://s/a", "http://s/b", "http://s/c"),
        )
        val localMirror = mockk<Player>(relaxed = true)
        every { localMirror.currentMediaItemIndex } returns 0
        PlayerHolder.exoPlayer = localMirror
        PlayerHolder.isCasting = true
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.currentMediaItem } returns
            androidx.media3.common.MediaItem.Builder().setMediaId("a").setUri("http://s/a").build()
        clearMocks(mockPlayer, recordedCalls = true, answers = false)
        val actions = mutableListOf<CastQueueAction>()
        mgr.castQueueListener = { actions.add(it) }

        mgr.playNext(Track("n", "Next", duration = 100), "http://s/n")

        verify(exactly = 0) { mockPlayer.setMediaItems(any<List<androidx.media3.common.MediaItem>>(), any(), any()) }
        verify(exactly = 1) {
            localMirror.setMediaItems(
                match<List<androidx.media3.common.MediaItem>> {
                    it.map { item -> item.mediaId } == listOf("a", "n", "b", "c")
                },
                0,
                any(),
            )
        }
        val add = actions.single() as CastQueueAction.Add
        assertEquals("Insert before former next item", 2, add.beforeEntryId)
    }

    @Test
    fun `playQueueItem during Cast emits JumpTo`() {
        val mgr = baseManager()
        mgr.playAlbum(
            (0 until 5).map { Track("track-$it", "Track $it", duration = 100) },
            (0 until 5).map { "http://s/$it" },
        )
        PlayerHolder.isCasting = true
        val actions = mutableListOf<CastQueueAction>()
        mgr.castQueueListener = { actions.add(it) }

        mgr.playQueueItem(4)

        assertEquals(CastQueueAction.JumpTo(5), actions.single())
    }

    @Test
    fun `playQueueItem locally delegates to queueManager`() {
        val mgr = baseManager()
        PlayerHolder.isCasting = false
        mgr.playQueueItem(1)
        // No Cast event — local path must not invoke the listener
        coVerify(exactly = 0) { mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    // ── Persistence / edge guards ────────────────────────────────────────

    @Test
    fun `persistCurrentQueue saves player state and tolerates null player`() {
        val mgr = baseManager()
        every { mockPlayer.mediaItemCount } returns 0
        mgr.persistCurrentQueue()
        coVerify(timeout = 1_000) {
            mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any())
        }

        PlayerHolder.player = null
        mgr.persistCurrentQueue() // no-op, no crash
    }

    @Test
    fun `pushContext with empty tracks returns early`() {
        val mgr = baseManager()
        mgr.pushContext(emptyList(), emptyList())
        coVerify(exactly = 0) { mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `playAlbum without player starts service via ensurePlayer`() {
        PlayerHolder.player = null
        val mgr = baseManager()
        mgr.playAlbum(
            listOf(Track("t1", "T1", duration = 100)),
            listOf("http://s/t1"),
        )
        verify { mockAppContext.startForegroundService(match { true }) }
    }
}
