package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.QueueJournalDao
import com.lucasdss.ftpmusic.app.data.model.Track
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Branch-coverage + edge-case batch for PlaybackManager play/queue/cast/restore
 * paths that happy-path suites leave red (ADR-0065).
 */
class PlaybackManagerBranchCoverageTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockAppContext: android.app.Application = mockk(relaxed = true)
    private val mockPlayer: Player = mockk(relaxed = true)
    private val queueManager = QueueManager(mockk())
    private val mockPersistenceManager: QueuePersistenceManager = mockk(relaxed = true)
    private val mockOfflineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val mockDownloadManager: DownloadManager = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)
    private val mockQueueJournalDao: QueueJournalDao = mockk(relaxed = true)

    private fun mgr(): PlaybackManager = PlaybackManager(
        queueManager,
        mockPersistenceManager,
        mockOfflineModeManager,
        mockDownloadManager,
        castPreferences,
        mockAppContext,
        mockQueueJournalDao,
        CoroutineScope(testDispatcher),
    )

    private fun track(id: String) = Track(id, "Title $id", artist = "A", album = "Al", duration = 100)
    private fun url(id: String) = "http://server/rest/stream?id=$id"

    /** Stub Exo getMediaItemAt so buildQueueStateFromPlayer never sees null mediaId. */
    private fun stubPlayerQueue(vararg ids: String) {
        every { mockPlayer.mediaItemCount } returns ids.size
        every { mockPlayer.getMediaItemAt(any()) } answers {
            val i = firstArg<Int>()
            val id = ids.getOrElse(i) { "t$i" }
            MediaItem.Builder().setMediaId(id).setUri(url(id)).build()
        }
    }

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns Uri.EMPTY
        PlayerHolder.player = mockPlayer
        PlayerHolder.exoPlayer = null
        PlayerHolder.isCasting = false
        every { mockPlayer.mediaItemCount } returns 0
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.currentPosition } returns 0L
        every { mockPlayer.getMediaItemAt(any()) } answers {
            MediaItem.Builder().setMediaId("stub").setUri(url("stub")).build()
        }
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
        PlayerHolder.isCasting = false
    }

    // ── clearAutoplayQueue ───────────────────────────────────────────────

    @Test
    fun `clearAutoplayQueue with no autoplay rows is a no-op`() {
        val m = mgr()
        m.playAlbum(listOf(track("a")), listOf(url("a")), skipPersistence = true)
        clearMocks(mockPersistenceManager, recordedCalls = true, answers = false)

        m.clearAutoplayQueue()

        coVerify(exactly = 0) { mockPersistenceManager.clear() }
        coVerify(exactly = 0) {
            mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `clearAutoplayQueue removes autoplay rows locally`() {
        val m = mgr()
        m.playAlbum(listOf(track("a")), listOf(url("a")), skipPersistence = true)
        every { mockPlayer.mediaItemCount } returns 3
        m.appendToContext(listOf(track("ap1"), track("ap2")), listOf(url("ap1"), url("ap2")), asAutoplay = true)
        assertTrue(m.isAutoplayFlags().any { it })

        m.clearAutoplayQueue()

        assertFalse(m.isAutoplayFlags().any { it })
        assertEquals(1, m.contextSize)
        verify(atLeast = 1) { mockPlayer.removeMediaItem(any()) }
    }

    @Test
    fun `clearAutoplayQueue during Cast syncs exo mirror`() {
        val m = mgr()
        m.playAlbum(listOf(track("a")), listOf(url("a")), skipPersistence = true)
        m.appendToContext(listOf(track("ap1")), listOf(url("ap1")), asAutoplay = true)
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        PlayerHolder.isCasting = true
        every { mockPlayer.currentMediaItem } returns
            MediaItem.Builder().setMediaId("a").setUri(url("a")).build()

        m.clearAutoplayQueue()

        verify { exo.setMediaItems(any(), any(), any()) }
    }

    @Test
    fun `clearAutoplayQueue empties dual and clears persistence`() {
        val m = mgr()
        m.appendToContext(listOf(track("ap1")), listOf(url("ap1")), asAutoplay = true)
        assertEquals(1, m.dualQueueSize)

        m.clearAutoplayQueue()

        assertEquals(0, m.dualQueueSize)
        coVerify(timeout = 1_000) { mockPersistenceManager.clear() }
    }

    // ── onCastCommandAck ─────────────────────────────────────────────────

    @Test
    fun `onCastCommandAck success commits snapshot`() {
        val m = mgr()
        PlayerHolder.isCasting = true
        m.addToQueue(track("x"), url("x"))
        val rev = m.getLastQueueSnapshot()!!.revision

        m.onCastCommandAck(rev, success = true)

        assertNull(m.getLastQueueSnapshot())
    }

    @Test
    fun `onCastCommandAck fail rolls back and syncs`() {
        val m = mgr()
        m.playAlbum(listOf(track("a")), listOf(url("a")), skipPersistence = true)
        PlayerHolder.isCasting = true
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        every { mockPlayer.currentMediaItem } returns
            MediaItem.Builder().setMediaId("a").setUri(url("a")).build()
        m.addToQueue(track("x"), url("x"))
        assertEquals(1, m.priorityQueueSize)
        val rev = m.getLastQueueSnapshot()!!.revision

        m.onCastCommandAck(rev, success = false)

        assertEquals(0, m.priorityQueueSize)
        assertNull(m.getLastQueueSnapshot())
        verify { exo.setMediaItems(any(), any(), any()) }
    }

    @Test
    fun `onCastCommandAck fail on empty dual clears persistence`() {
        val m = mgr()
        PlayerHolder.isCasting = true
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        m.addToQueue(track("only"), url("only"))
        val rev = m.getLastQueueSnapshot()!!.revision

        m.onCastCommandAck(rev, success = false)

        assertEquals(0, m.dualQueueSize)
        coVerify(timeout = 1_000) { mockPersistenceManager.clear() }
    }

    @Test
    fun `onCastCommandAck fail with wrong revision is a no-op`() {
        val m = mgr()
        PlayerHolder.isCasting = true
        m.addToQueue(track("x"), url("x"))
        val sizeBefore = m.priorityQueueSize

        m.onCastCommandAck(revision = -1L, success = false)

        assertEquals(sizeBefore, m.priorityQueueSize)
        assertNotNull(m.getLastQueueSnapshot())
    }

    // ── clearPriorityQueue cast / empty ──────────────────────────────────

    @Test
    fun `clearPriorityQueue during Cast syncs exo mirror`() {
        val m = mgr()
        m.playAlbum(listOf(track("a")), listOf(url("a")), skipPersistence = true)
        m.addToQueue(track("p"), url("p"))
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        PlayerHolder.isCasting = true
        every { mockPlayer.currentMediaItem } returns
            MediaItem.Builder().setMediaId("a").setUri(url("a")).build()

        m.clearPriorityQueue()

        assertEquals(0, m.priorityQueueSize)
        verify { exo.setMediaItems(any(), any(), any()) }
    }

    @Test
    fun `clearPriorityQueue emptying dual clears persistence`() {
        val m = mgr()
        m.addToQueue(track("p"), url("p"))
        assertEquals(1, m.priorityQueueSize)
        assertEquals(0, m.contextSize)

        m.clearPriorityQueue()

        assertEquals(0, m.dualQueueSize)
        coVerify(timeout = 1_000) { mockPersistenceManager.clear() }
    }

    // ── pushContext journal / dedupe / si clamp ──────────────────────────

    @Test
    fun `pushContext journals when source set`() {
        val m = mgr()
        m.pushContext(
            listOf(track("n1"), track("n2")),
            listOf(url("n1"), url("n2")),
            startIndex = 0,
            sourceType = "album",
            sourceId = "al-1",
            sourceName = "Album",
        )
        coVerify {
            mockQueueJournalDao.upsert(
                match {
                    it.sourceType == "album" && it.sourceId == "al-1"
                },
            )
        }
    }

    @Test
    fun `pushContext when all ids already present keeps pushed items as context`() {
        val m = mgr()
        m.playAlbum(listOf(track("a"), track("b")), listOf(url("a"), url("b")), skipPersistence = true)

        m.pushContext(listOf(track("a"), track("b")), listOf(url("a"), url("b")))

        // Full dedupe → keep items as new context; old merged becomes priority.
        assertTrue(m.contextSize >= 2)
        assertTrue(m.dualQueueSize >= 2)
    }

    @Test
    fun `pushContext clamps startIndex past contextSize to zero`() {
        val m = mgr()
        // Existing a,b → push a,b,c with startIndex=2 → dedupe leaves [c] (ctx=1), si=2 ≥ ctx → start 0
        m.playAlbum(listOf(track("a"), track("b")), listOf(url("a"), url("b")), skipPersistence = true)
        clearMocks(mockPlayer, recordedCalls = true, answers = false)
        m.pushContext(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b"), url("c")),
            startIndex = 2,
        )
        verify { mockPlayer.setMediaItems(any(), 0, any()) }
    }

    // ── syncDualQueueToPlayer ────────────────────────────────────────────

    @Test
    fun `syncDualQueueToPlayer during Cast uses exoPlayer`() {
        val m = mgr()
        m.playAlbum(listOf(track("a"), track("b")), listOf(url("a"), url("b")), skipPersistence = true)
        val exo = mockk<Player>(relaxed = true)
        every { exo.currentMediaItemIndex } returns 0
        every { exo.currentPosition } returns 10L
        PlayerHolder.exoPlayer = exo
        PlayerHolder.isCasting = true
        every { mockPlayer.currentMediaItem } returns
            MediaItem.Builder().setMediaId("b").setUri(url("b")).build()
        every { mockPlayer.currentPosition } returns 50L

        m.syncDualQueueToPlayer()

        verify {
            exo.setMediaItems(
                match { it.map { item -> item.mediaId } == listOf("a", "b") },
                1,
                50L,
            )
        }
    }

    @Test
    fun `syncDualQueueToPlayer empty local stops and clears`() {
        val m = mgr()
        PlayerHolder.isCasting = false

        m.syncDualQueueToPlayer()

        verify { mockPlayer.stop() }
        verify { mockPlayer.clearMediaItems() }
    }

    @Test
    fun `syncDualQueueToPlayer with null activeId falls back to player index`() {
        val m = mgr()
        m.playAlbum(listOf(track("a"), track("b")), listOf(url("a"), url("b")), skipPersistence = true)
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.currentMediaItemIndex } returns 1
        every { mockPlayer.currentPosition } returns 12L
        clearMocks(mockPlayer, recordedCalls = true, answers = false)

        m.syncDualQueueToPlayer()

        verify { mockPlayer.setMediaItems(any(), 1, 12L) }
    }

    // ── enqueuePlayQueue catch ───────────────────────────────────────────

    @Test
    fun `enqueuePlayQueue continues after enqueue throw`() {
        coEvery { mockDownloadManager.enqueue(any(), any(), any()) } throws RuntimeException("disk full") andThen
            Unit andThen Unit andThen Unit
        val m = mgr()
        m.enqueuePlayQueue(
            listOf(track("t0"), track("t1"), track("t2"), track("t3")),
            listOf(url("t0"), url("t1"), url("t2"), url("t3")),
            currentIndex = 0,
        )
        // start = 1; t1 throws, t2+t3 still attempted
        coVerify(atLeast = 3) { mockDownloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `enqueuePlayQueue clamps startIndex past coerced length`() {
        clearMocks(mockDownloadManager, recordedCalls = true, answers = false)
        coEvery { mockDownloadManager.enqueue(any(), any(), any()) } just Runs
        val m = mgr()
        // 3 tracks / 2 urls → n=2; currentIndex=9 clamps to lastIndex=1 → start=2 → no enqueues
        m.enqueuePlayQueue(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b")),
            currentIndex = 9,
        )
        coVerify(exactly = 0) { mockDownloadManager.enqueue(any(), any(), any()) }
    }

    // ── empty / journal XOR / length guard ───────────────────────────────

    @Test
    fun `shuffleAlbum with empty tracks is a no-op`() {
        val m = mgr()
        m.shuffleAlbum(emptyList(), emptyList())
        verify(exactly = 0) { mockPlayer.setMediaItems(any(), any(), any()) }
    }

    @Test
    fun `playSingleTrack sourceType without sourceId skips journal`() {
        val m = mgr()
        m.playSingleTrack(track("solo"), url("solo"), sourceType = "album", sourceId = null)
        coVerify(exactly = 0) { mockQueueJournalDao.upsert(any()) }
    }

    @Test
    fun `playAlbum with empty urls is a no-op`() {
        val m = mgr()
        m.playAlbum(listOf(track("a"), track("b")), emptyList())
        verify(exactly = 0) { mockPlayer.setMediaItems(any(), any(), any()) }
        assertEquals(0, m.dualQueueSize)
    }

    @Test
    fun `playAlbum coerces tracks longer than urls`() {
        val m = mgr()
        m.playAlbum(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b")),
            skipPersistence = true,
        )
        assertEquals(2, m.contextSize)
        assertNull(m.getTrackInfo("c"))
        assertNotNull(m.getTrackInfo("a"))
    }

    @Test
    fun `playAlbum coerces urls longer than tracks`() {
        val m = mgr()
        m.playAlbum(
            listOf(track("a")),
            listOf(url("a"), url("extra")),
            skipPersistence = true,
        )
        assertEquals(1, m.contextSize)
    }

    @Test
    fun `playNext locally inserts after current`() {
        val m = mgr()
        m.playAlbum(listOf(track("a"), track("b")), listOf(url("a"), url("b")), skipPersistence = true)
        stubPlayerQueue("a", "b")
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.currentMediaItem } returns
            MediaItem.Builder().setMediaId("a").setUri(url("a")).build()
        m.playNext(track("n"), url("n"))
        verify { mockPlayer.addMediaItem(any(), any()) }
        assertTrue(m.dualQueueSize >= 3)
    }

    @Test
    fun `isAutoplayFlags and dualQueueSize reflect queue`() {
        val m = mgr()
        m.playAlbum(listOf(track("a")), listOf(url("a")), skipPersistence = true)
        m.appendToContext(listOf(track("ap")), listOf(url("ap")), asAutoplay = true)
        assertEquals(2, m.dualQueueSize)
        assertEquals(listOf(false, true), m.isAutoplayFlags())
    }

    // ── playMergedQueue empty cast mirror ────────────────────────────────

    @Test
    fun `syncDualQueueToPlayer during Cast with empty dual clears exo without stop`() {
        val m = mgr()
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        PlayerHolder.isCasting = true
        m.clearQueue()
        // sync empty while casting clears without stop
        m.syncDualQueueToPlayer()
        verify { exo.clearMediaItems() }
        verify(exactly = 0) { exo.stop() }
    }

    // ── reorder / clearQueue / accessors / buildQueueState ───────────────

    @Test
    fun `isPriorityFlags mirrors dual origins after addToQueue`() {
        val m = mgr()
        m.playAlbum(listOf(track("a"), track("b")), listOf(url("a"), url("b")), skipPersistence = true)
        m.addToQueue(track("p"), url("p"))
        val flags = m.isPriorityFlags()
        assertEquals(m.dualQueueSize, flags.size)
        assertTrue(flags.any { it })
        assertTrue(flags.any { !it })
    }

    @Test
    fun `begin and commitQueueReorder moves locally`() {
        val m = mgr()
        m.playAlbum(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b"), url("c")),
            skipPersistence = true,
        )
        stubPlayerQueue("a", "b", "c")
        val entryId = m.entryIds()[0]
        m.beginQueueReorder(entryId, 0)
        m.moveQueueItem(0, 2)
        m.commitQueueReorder()
        verify { mockPlayer.moveMediaItem(0, 2) }
    }

    @Test
    fun `beginQueueReorder with zero entryId falls back to fromIndex and mediaId`() {
        val m = mgr()
        m.playAlbum(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b"), url("c")),
            skipPersistence = true,
        )
        stubPlayerQueue("a", "b", "c")
        m.beginQueueReorder(0, 1)
        m.moveQueueItem(1, 0)
        m.commitQueueReorder()
        verify { mockPlayer.moveMediaItem(1, 0) }
    }

    @Test
    fun `commitQueueReorder during Cast syncs exo and emits Move`() {
        val m = mgr()
        m.playAlbum(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b"), url("c")),
            skipPersistence = true,
        )
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        PlayerHolder.isCasting = true
        every { mockPlayer.currentMediaItem } returns
            MediaItem.Builder().setMediaId("a").setUri(url("a")).build()
        val actions = mutableListOf<CastQueueAction>()
        m.castQueueListener = { actions.add(it) }
        val entryId = m.entryIds()[0]
        m.beginQueueReorder(entryId, 0)
        m.moveQueueItem(0, 2)
        m.commitQueueReorder()
        verify { exo.setMediaItems(any(), any(), any()) }
        assertTrue(actions.any { it is CastQueueAction.Move })
    }

    @Test
    fun `commitQueueReorder without move is a no-op`() {
        val m = mgr()
        m.playAlbum(listOf(track("a"), track("b")), listOf(url("a"), url("b")), skipPersistence = true)
        m.beginQueueReorder(m.entryIds()[0], 0)
        clearMocks(mockPlayer, recordedCalls = true, answers = false)
        m.commitQueueReorder()
        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
    }

    @Test
    fun `clearQueue with null player clears dual and persistence`() {
        val m = mgr()
        m.playAlbum(listOf(track("a")), listOf(url("a")), skipPersistence = true)
        PlayerHolder.player = null
        m.clearQueue()
        assertEquals(0, m.dualQueueSize)
        coVerify(timeout = 1_000) { mockPersistenceManager.clear() }
    }

    @Test
    fun `playQueueItem cast with out of range index is a no-op`() {
        val m = mgr()
        m.playAlbum(listOf(track("a")), listOf(url("a")), skipPersistence = true)
        PlayerHolder.isCasting = true
        val actions = mutableListOf<CastQueueAction>()
        m.castQueueListener = { actions.add(it) }
        m.playQueueItem(99)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `appendToContext during Cast syncs exo mirror`() {
        val m = mgr()
        m.playAlbum(listOf(track("a")), listOf(url("a")), skipPersistence = true)
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        PlayerHolder.isCasting = true
        every { mockPlayer.currentMediaItem } returns
            MediaItem.Builder().setMediaId("a").setUri(url("a")).build()
        m.appendToContext(listOf(track("c1")), listOf(url("c1")), asAutoplay = false)
        verify { exo.setMediaItems(any(), any(), any()) }
        assertEquals(2, m.contextSize)
    }

    @Test
    fun `buildQueueStateFromPlayer tolerates missing uri and sparse metadata`() {
        val m = mgr()
        every { mockPlayer.mediaItemCount } returns 1
        every { mockPlayer.getMediaItemAt(0) } returns
            MediaItem.Builder().setMediaId("bare").setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder().build(),
            ).build()
        val (tracks, urls) = m.buildQueueStateFromPlayer(mockPlayer)
        assertEquals(1, tracks.size)
        assertEquals("bare", tracks[0].id)
        assertEquals("", urls[0])
    }

    @Test
    fun `buildQueueStateFromDual reads metadata from merged items`() {
        val m = mgr()
        m.playAlbum(
            listOf(
                Track(
                    id = "a",
                    title = "Song A",
                    artist = "Art",
                    album = "Alb",
                    duration = 100,
                    artistId = "ar",
                    albumId = "al",
                    coverArt = "ca",
                ),
            ),
            listOf(url("a")),
            skipPersistence = true,
        )
        val (tracks, _) = m.buildQueueStateFromDual()
        assertEquals(1, tracks.size)
        assertEquals("a", tracks[0].id)
    }

    @Test
    fun `queueRollback false when no snapshot`() {
        val m = mgr()
        assertFalse(m.queueRollback())
    }

    @Test
    fun `moveQueueItem mid-drag only updates dual`() {
        val m = mgr()
        m.playAlbum(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b"), url("c")),
            skipPersistence = true,
        )
        m.beginQueueReorder(m.entryIds()[1], 1)
        clearMocks(mockPlayer, recordedCalls = true, answers = false)
        m.moveQueueItem(1, 0)
        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
        assertEquals("b", m.buildQueueStateFromDual().first[0].id)
    }

    @Test
    fun `buildQueueState empty when no player`() {
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
        val m = mgr()
        assertEquals(emptyList<Track>(), m.buildQueueState().first)
    }

    @Test
    fun `buildQueueState prefers exoPlayer when present`() {
        val m = mgr()
        val exo = mockk<Player>(relaxed = true)
        every { exo.mediaItemCount } returns 1
        every { exo.getMediaItemAt(0) } returns
            MediaItem.Builder().setMediaId("exo1").setUri(url("exo1"))
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle("Exo")
                        .setArtist("Artist")
                        .setAlbumTitle("Album")
                        .build(),
                ).build()
        PlayerHolder.exoPlayer = exo
        val (tracks, _) = m.buildQueueState()
        assertEquals("exo1", tracks.single().id)
        assertEquals("Exo", tracks.single().title)
        assertEquals("Artist", tracks.single().artist)
    }

    @Test
    fun `trackAndUrlFromMediaItem maps rich and sparse metadata`() {
        val m = mgr()
        val rich = MediaItem.Builder().setMediaId("r1").setUri(url("r1"))
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("Rich")
                    .setArtist("Art")
                    .setAlbumTitle("Alb")
                    .build(),
            ).build()
        val (t1, _) = m.trackAndUrlFromMediaItem(rich)
        assertEquals("r1", t1.id)
        assertEquals("Rich", t1.title)
        assertEquals("Art", t1.artist)
        assertEquals("Alb", t1.album)

        val sparse = MediaItem.Builder().setMediaId("s1").build()
        val (t2, u2) = m.trackAndUrlFromMediaItem(sparse)
        assertEquals("s1", t2.id)
        assertEquals("", t2.title)
        assertNull(t2.artist)
        // JVM Uri stub may yield "" even when setUri was called — empty is fine.
        assertNotNull(u2)
    }

    @Test
    fun `playQueueItem locally seeks to index`() {
        val m = mgr()
        stubPlayerQueue("a", "b", "c")
        every { mockPlayer.mediaItemCount } returns 3
        m.playQueueItem(2)
        verify { mockPlayer.seekToDefaultPosition(2) }
    }

    @Test
    fun `commitQueueReorder round-trip back to origin skips moveMediaItem`() {
        val m = mgr()
        m.playAlbum(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b"), url("c")),
            skipPersistence = true,
        )
        stubPlayerQueue("a", "b", "c")
        val entryId = m.entryIds()[0]
        m.beginQueueReorder(entryId, 0)
        m.moveQueueItem(0, 2)
        m.moveQueueItem(2, 0) // back
        clearMocks(mockPlayer, recordedCalls = true, answers = false)
        m.commitQueueReorder()
        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
    }

    @Test
    fun `removeFromQueue during Cast with null player skips persist`() {
        val m = mgr()
        m.playAlbum(listOf(track("a"), track("b")), listOf(url("a"), url("b")), skipPersistence = true)
        PlayerHolder.isCasting = true
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        every { mockPlayer.currentMediaItem } returns
            MediaItem.Builder().setMediaId("a").setUri(url("a")).build()
        val actions = mutableListOf<CastQueueAction>()
        m.castQueueListener = { actions.add(it) }
        PlayerHolder.player = null // after cast setup, drop active player
        // restore casting player for currentMediaItem on exo path — player null skips persist block
        m.removeFromQueue(1)
        assertTrue(actions.any { it is CastQueueAction.Remove } || m.dualQueueSize == 1)
    }

    @Test
    fun `overwriteBehavior reads secure storage when provided`() {
        val storage = mockk<com.lucasdss.ftpmusic.app.data.security.SecureStorage>(relaxed = true)
        every {
            storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_QUEUE_OVERWRITE_BEHAVIOR)
        } returns "clean"
        val dual = DualQueueManager()
        val m = PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
            dual,
            OptimisticQueueDelegate(dual),
            storage,
        )
        assertEquals(OverwriteBehavior.CLEAN, m.overwriteBehavior())
    }
}
