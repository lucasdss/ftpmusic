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
 * P0 market-drift fixes (ADR 0067): Dual persist SoT, playStream Dual,
 * Cast clearQueue ClearAndPlay, keep PRIORITY on radio CONTEXT.
 */
class PlaybackQueueMarketDriftTest {

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
        every { mockPlayer.currentMediaItem } returns null
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

    @Test
    fun `addToQueue local persist uses Dual not Player snapshot`() {
        val m = mgr()
        m.playAlbum(listOf(track("a"), track("b")), listOf(url("a"), url("b")), skipPersistence = true)
        m.addToQueue(track("p"), url("p"))
        // Dual order: CONTEXT a,b + PRIORITY p (anchor at 0 → a | p | b)
        val dualIds = m.buildQueueStateFromDual().first.map { it.id }
        assertTrue(dualIds.contains("p"))
        assertEquals(m.dualQueueSize, dualIds.size)
        coVerify(atLeast = 1) {
            mockPersistenceManager.save(
                match { it.map { t -> t.id } == dualIds },
                any(),
                any(),
                any(),
                any(),
                match { flags -> flags.size == dualIds.size && flags.any { it } },
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `persistCurrentQueue snapshots Dual`() {
        val m = mgr()
        m.playAlbum(listOf(track("a"), track("b")), listOf(url("a"), url("b")), skipPersistence = true)
        m.addToQueue(track("p"), url("p"))
        clearMocks(mockPersistenceManager, recordedCalls = true, answers = false)

        m.persistCurrentQueue()

        val dualIds = m.buildQueueStateFromDual().first.map { it.id }
        coVerify {
            mockPersistenceManager.save(
                match { it.map { t -> t.id } == dualIds },
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
    fun `playStream enters Dual CONTEXT and keeps PRIORITY`() {
        val m = mgr()
        m.playAlbum(listOf(track("a"), track("b")), listOf(url("a"), url("b")), skipPersistence = true)
        m.addToQueue(track("p"), url("p"))
        assertTrue(m.priorityQueueSize > 0)

        val radioUrl = "http://radio.example.com/stream"
        m.playStream(radioUrl, "My Radio")

        val ids = m.buildQueueStateFromDual().first.map { it.id }
        assertTrue(ids.any { it.startsWith("radio:") })
        assertTrue(ids.contains("p"))
        assertEquals(1, m.contextSize)
        assertTrue(m.priorityQueueSize >= 1)
        coVerify(atLeast = 1) {
            mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `playStream during Cast emits ClearAndPlay`() {
        val m = mgr()
        m.playAlbum(listOf(track("a")), listOf(url("a")), skipPersistence = true)
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        PlayerHolder.isCasting = true
        val actions = mutableListOf<CastQueueAction>()
        m.castQueueListener = { actions.add(it) }

        m.playStream("http://radio.example.com/live", "Live")

        assertTrue(actions.any { it is CastQueueAction.ClearAndPlay })
        val clear = actions.filterIsInstance<CastQueueAction.ClearAndPlay>().last()
        assertTrue(clear.mediaItems.any { it.mediaId.startsWith("radio:") })
    }

    @Test
    fun `clearQueue keeps current and persists Dual remaining`() {
        val m = mgr()
        m.playAlbum(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b"), url("c")),
            skipPersistence = true,
        )
        every { mockPlayer.currentMediaItem } returns
            MediaItem.Builder().setMediaId("a").setUri(url("a")).build()
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 3
        clearMocks(mockPersistenceManager, recordedCalls = true, answers = false)

        m.clearQueue()

        assertEquals(listOf("a"), m.buildQueueStateFromDual().first.map { it.id })
        coVerify {
            mockPersistenceManager.save(
                match { it.map { t -> t.id } == listOf("a") },
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
        coVerify(exactly = 0) { mockPersistenceManager.clear() }
    }

    @Test
    fun `clearQueue during Cast emits ClearAndPlay of remaining`() {
        val m = mgr()
        m.playAlbum(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b"), url("c")),
            skipPersistence = true,
        )
        every { mockPlayer.currentMediaItem } returns
            MediaItem.Builder().setMediaId("a").setUri(url("a")).build()
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        PlayerHolder.isCasting = true
        val actions = mutableListOf<CastQueueAction>()
        m.castQueueListener = { actions.add(it) }

        m.clearQueue()

        val clear = actions.filterIsInstance<CastQueueAction.ClearAndPlay>().single()
        assertEquals(listOf("a"), clear.mediaItems.map { it.mediaId })
        assertEquals(0, clear.startIndex)
        verify { exo.setMediaItems(any(), any(), any()) }
    }

    @Test
    fun `clearQueue cast ack fail rolls back Dual`() {
        val m = mgr()
        m.playAlbum(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b"), url("c")),
            skipPersistence = true,
        )
        every { mockPlayer.currentMediaItem } returns
            MediaItem.Builder().setMediaId("a").setUri(url("a")).build()
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        PlayerHolder.isCasting = true
        var revision = -1L
        m.castQueueListener = {
            // Capture pending revision via getLastQueueSnapshot after clear
        }
        m.clearQueue()
        val snap = m.getLastQueueSnapshot()
        assertNotNull(snap)
        revision = snap!!.revision

        // Simulate Cast ack failure → Dual restored to pre-clear (3 items)
        m.onCastCommandAck(revision, success = false)
        assertEquals(3, m.dualQueueSize)
    }

    @Test
    fun `playAlbum keeps PRIORITY after mid-album startIndex — market row tap`() {
        val m = mgr()
        m.playAlbum(listOf(track("x")), listOf(url("x")), skipPersistence = true)
        m.addToQueue(track("p"), url("p"))
        val priorityBefore = m.priorityQueueSize

        m.playAlbum(
            listOf(track("a"), track("b"), track("c")),
            listOf(url("a"), url("b"), url("c")),
            startIndex = 1,
            skipPersistence = true,
        )

        assertEquals(priorityBefore, m.priorityQueueSize)
        assertTrue(m.buildQueueStateFromDual().first.any { it.id == "p" })
    }

    @Test
    fun `tryStartContext ASK blocks when PRIORITY nonempty — market header`() {
        val m = mgr()
        m.playAlbum(listOf(track("a")), listOf(url("a")), skipPersistence = true)
        m.addToQueue(track("p"), url("p"))

        val started = m.tryStartContext(
            listOf(track("n1"), track("n2")),
            listOf(url("n1"), url("n2")),
            startIndex = 0,
            sourceType = "album",
            sourceId = "al-new",
            sourceName = "New Album",
        )

        assertFalse(started)
        assertTrue(m.priorityQueueSize > 0)
        assertTrue(m.buildQueueStateFromDual().first.any { it.id == "p" })
        assertFalse(m.buildQueueStateFromDual().first.any { it.id == "n1" })
    }
}
