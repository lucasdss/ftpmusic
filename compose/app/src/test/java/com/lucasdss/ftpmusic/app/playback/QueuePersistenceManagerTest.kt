package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.QueueDao
import com.lucasdss.ftpmusic.app.data.db.QueueItemEntity
import com.lucasdss.ftpmusic.app.data.db.QueueStateEntity
import com.lucasdss.ftpmusic.app.data.model.Track
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QueuePersistenceManagerTest {

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun teardown() {
        Dispatchers.resetMain()
    }

    private val dao: QueueDao = mockk(relaxed = true)
    private val mockContext: android.content.Context = mockk(relaxed = true)
    private val manager = QueuePersistenceManager(dao)

    @Test
    fun `save inserts queue items via atomicReplace`() = runTest {
        val tracks = listOf(
            Track(
                "t1",
                "Song 1",
                artist = "Artist A",
                album = "Album A",
                albumId = "al-1",
                coverArt = "ca-1",
                duration = 200,
            ),
            Track(
                "t2",
                "Song 2",
                artist = "Artist B",
                album = "Album B",
                albumId = "al-1",
                coverArt = "ca-2",
                duration = 300,
            ),
        )
        val urls = listOf("http://ex.com/1", "http://ex.com/2")

        manager.save(tracks, urls, currentIndex = 1, positionMs = 45000L)

        coVerify {
            dao.replaceAllAndState(
                match { items ->
                    items.size == 2 &&
                        items[0].trackId == "t1" &&
                        items[0].title == "Song 1" &&
                        items[0].position == 0 &&
                        items[1].trackId == "t2" &&
                        items[1].position == 1
                },
                any(),
            )
        }
    }

    @Test
    fun `restore returns tracks from DAO`() = runTest {
        val entities = listOf(
            QueueItemEntity(
                trackId = "r1",
                title = "R Song 1",
                artist = "R Artist",
                album = "R Album",
                coverArtId = "ca-r1",
                url = "http://ex.com/r1",
                position = 0,
                durationSeconds = 180,
            ),
            QueueItemEntity(
                trackId = "r2",
                title = "R Song 2",
                artist = "R Artist",
                album = "R Album",
                coverArtId = "ca-r2",
                url = "http://ex.com/r2",
                position = 1,
                durationSeconds = 240,
            ),
        )
        coEvery { dao.getAllOnce() } returns entities

        val result = manager.restore()

        assertNotNull(result)
        assertEquals(2, result!!.tracks.size)
        assertEquals("R Song 1", result.tracks[0].title)
        assertEquals("ca-r1", result.tracks[0].coverArt)
    }

    @Test
    fun `restore returns null when queue is empty`() = runTest {
        coEvery { dao.getAllOnce() } returns emptyList()
        assertNull(manager.restore())
    }

    @Test
    fun `clear calls dao clear`() = runTest {
        manager.clear()
        coVerify { dao.clear() }
    }

    @Test
    fun `save with empty list clears`() = runTest {
        manager.save(emptyList(), emptyList(), 0, 0L)
        coVerify { dao.clear() }
        coVerify(exactly = 0) { dao.replaceAllAndState(any(), any()) }
    }

    @Test
    fun `save persists position and restore returns it`() = runTest {
        val tracks = listOf(
            Track("t1", "Song 1", artist = "A", album = "B", albumId = "al-1", coverArt = "ca-1", duration = 200),
        )
        val urls = listOf("http://ex.com/1")

        coEvery { dao.getState() } returns QueueStateEntity(currentIndex = 0, positionMs = 127_000L)
        coEvery { dao.replaceAllAndState(any(), any()) } just Runs

        manager.save(tracks, urls, currentIndex = 0, positionMs = 127_000L)
        coVerify { dao.replaceAllAndState(any(), any()) }
    }

    @Test
    fun `restore returns saved position after Cast plays for a while`() = runTest {
        val entities = listOf(
            QueueItemEntity(
                trackId = "t1",
                title = "Cast Song",
                artist = "Artist",
                album = "Album",
                coverArtId = "ca-1",
                url = "http://ex.com/t1",
                position = 0,
                durationSeconds = 300,
            ),
        )
        val state = QueueStateEntity(currentIndex = 0, positionMs = 185_000L)
        coEvery { dao.getAllOnce() } returns entities
        coEvery { dao.getState() } returns state

        val result = manager.restore()

        assertNotNull(result)
        assertEquals(1, result!!.tracks.size)
        assertEquals("Cast Song", result.tracks[0].title)
        assertEquals(0, result.currentIndex)
        assertEquals(185_000L, result.positionMs)
    }

    @Test
    fun `restore returns zero position when not saved`() = runTest {
        val entities = listOf(
            QueueItemEntity(
                trackId = "t1",
                title = "Song",
                artist = "A",
                album = "B",
                coverArtId = "ca-1",
                url = "http://ex.com/t1",
                position = 0,
                durationSeconds = 200,
            ),
        )
        // No state saved — getState returns null
        coEvery { dao.getAllOnce() } returns entities
        coEvery { dao.getState() } returns null

        val result = manager.restore()

        assertNotNull(result)
        // Default state has positionMs = 0L
        assertEquals(0L, result!!.positionMs)
    }

    @Test
    fun `save then restore roundtrip preserves position`() = runTest {
        val tracks = listOf(
            Track("t1", "Song", artist = "A", album = "B", albumId = "al-1", coverArt = "ca-1", duration = 250),
        )
        val urls = listOf("http://ex.com/t1")

        coEvery { dao.replaceAllAndState(any(), any()) } just Runs
        coEvery { dao.getState() } returns QueueStateEntity(currentIndex = 0, positionMs = 153_000L)
        coEvery { dao.getAllOnce() } returns listOf(
            QueueItemEntity(
                trackId = "t1",
                title = "Song",
                artist = "A",
                album = "B",
                coverArtId = "ca-1",
                url = "http://ex.com/t1",
                position = 0,
                durationSeconds = 250,
            ),
        )

        // Save during Cast
        manager.save(tracks, urls, currentIndex = 0, positionMs = 153_000L)

        // Restore after disconnect
        val result = manager.restore()
        assertNotNull(result)
        assertEquals(153_000L, result!!.positionMs)
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun `restore after simulated process death returns full queue with position`() = runTest {
        val tracks = listOf(
            Track("t1", "Track 1", artist = "Artist", coverArt = "ca-1", duration = 200),
            Track("t2", "Track 2", artist = "Artist", coverArt = "ca-2", duration = 180),
        )
        val urls = listOf("http://ex.com/t1", "http://ex.com/t2")

        coEvery { dao.replaceAllAndState(any(), any()) } just Runs
        coEvery { dao.getState() } returns QueueStateEntity(currentIndex = 1, positionMs = 45_000L)
        coEvery { dao.getAllOnce() } returns listOf(
            QueueItemEntity(
                trackId = "t1",
                title = "Track 1",
                artist = "Artist",
                album = "Album",
                coverArtId = "ca-1",
                url = "http://ex.com/t1",
                position = 0,
                durationSeconds = 200,
            ),
            QueueItemEntity(
                trackId = "t2",
                title = "Track 2",
                artist = "Artist",
                album = "Album",
                coverArtId = "ca-2",
                url = "http://ex.com/t2",
                position = 1,
                durationSeconds = 180,
            ),
        )

        // Save before process death (synchronous on track transition)
        manager.save(tracks, urls, currentIndex = 1, positionMs = 45_000L)
        manager.savePositionOnly(1, 45_000L)

        // New process: restore from DB
        val result = manager.restore()
        assertNotNull(result)
        assertEquals(2, result!!.tracks.size)
        assertEquals("t1", result.tracks[0].id)
        assertEquals("t2", result.tracks[1].id)
        assertEquals(1, result.currentIndex)
        assertEquals(45_000L, result.positionMs)
    }

    @Test
    fun `save persists isPriorityFlags and restore returns them`() = runTest {
        val tracks = listOf(
            Track("c1", "C1", duration = 100),
            Track("p1", "P1", duration = 100),
            Track("c2", "C2", duration = 100),
        )
        val urls = listOf("http://ex.com/c1", "http://ex.com/p1", "http://ex.com/c2")
        val itemsSlot = slot<List<QueueItemEntity>>()
        coEvery { dao.replaceAllAndState(capture(itemsSlot), any()) } just Runs
        coEvery { dao.getAllOnce() } answers {
            itemsSlot.captured
        }
        coEvery { dao.getState() } returns QueueStateEntity(currentIndex = 0, positionMs = 0L, contextSize = 2)

        manager.save(
            tracks,
            urls,
            currentIndex = 0,
            positionMs = 0L,
            isPriorityFlags = listOf(false, true, false),
        )

        assertEquals(listOf(false, true, false), itemsSlot.captured.map { it.isPriority })
        assertEquals(2, itemsSlot.captured.let { flags -> flags.count { !it.isPriority } })

        val restored = manager.restore()
        assertEquals(listOf(false, true, false), restored!!.isPriorityFlags)
        assertEquals(2, restored.contextSize)
        assertEquals(listOf(1, 2, 3), restored.entryIds)
    }

    @Test
    fun `save persists entry ids and nextEntryId`() = runTest {
        val tracks = listOf(Track("c1", "C1", duration = 100), Track("p1", "P1", duration = 100))
        val urls = listOf("http://ex.com/c1", "http://ex.com/p1")
        val itemsSlot = slot<List<QueueItemEntity>>()
        val stateSlot = slot<QueueStateEntity>()
        coEvery { dao.replaceAllAndState(capture(itemsSlot), capture(stateSlot)) } just Runs

        manager.save(
            tracks,
            urls,
            currentIndex = 0,
            positionMs = 0L,
            isPriorityFlags = listOf(false, true),
            entryIds = listOf(10, 11),
            nextEntryId = 20,
        )

        assertEquals(listOf(10, 11), itemsSlot.captured.map { it.entryId })
        assertEquals(20, stateSlot.captured.nextEntryId)
        assertEquals(1, stateSlot.captured.contextSize)
    }

    @Test
    fun `save with contextSize derives trailing priority flags`() = runTest {
        val tracks = listOf(
            Track("c1", "C1", duration = 100),
            Track("c2", "C2", duration = 100),
            Track("p1", "P1", duration = 100),
        )
        val urls = listOf("http://ex.com/c1", "http://ex.com/c2", "http://ex.com/p1")
        val itemsSlot = slot<List<QueueItemEntity>>()
        coEvery { dao.replaceAllAndState(capture(itemsSlot), any()) } just Runs

        manager.save(tracks, urls, currentIndex = 0, positionMs = 0L, contextSize = 2)

        assertEquals(listOf(false, false, true), itemsSlot.captured.map { it.isPriority })
    }

    @Test
    fun `restore returns legacy contextSize -1 when state has no split`() = runTest {
        coEvery { dao.getAllOnce() } returns listOf(
            QueueItemEntity(
                trackId = "t1",
                title = "T1",
                url = "http://ex.com/t1",
                position = 0,
                durationSeconds = 100,
            ),
        )
        // Legacy row — QueueStateEntity default contextSize = -1
        coEvery { dao.getState() } returns QueueStateEntity()

        val restored = manager.restore()

        // Derived from is_priority flags (all false → all context)
        assertEquals(1, restored!!.contextSize)
        assertEquals(listOf(false), restored.isPriorityFlags)
    }

    @Test
    fun `savePositionOnly preserves stored contextSize`() = runTest {
        coEvery { dao.getState() } returns QueueStateEntity(currentIndex = 1, positionMs = 9_000L, contextSize = 3)

        manager.savePositionOnly(2, 12_000L)

        coVerify { dao.savePositionOnly(2, 12_000L) }
    }

    @Test
    fun `save derives trailing flags and position ids when omitted`() = runTest {
        val tracks = listOf(Track("a", "A", duration = 1), Track("b", "B", duration = 1))
        val urls = listOf("http://a", "http://b")
        val itemsSlot = slot<List<QueueItemEntity>>()
        coEvery { dao.replaceAllAndState(capture(itemsSlot), any()) } just Runs
        manager.save(tracks, urls, 0, 0L, contextSize = 1)
        assertEquals(listOf(false, true), itemsSlot.captured.map { it.isPriority })
        assertEquals(listOf(1, 2), itemsSlot.captured.map { it.entryId })
    }

    @Test
    fun `save ignores mismatched flag and entryId lists`() = runTest {
        val tracks = listOf(Track("a", "A", duration = 1), Track("b", "B", duration = 1))
        val urls = listOf("http://a", "http://b")
        val itemsSlot = slot<List<QueueItemEntity>>()
        coEvery { dao.replaceAllAndState(capture(itemsSlot), any()) } just Runs
        manager.save(
            tracks,
            urls,
            0,
            0L,
            contextSize = 2,
            isPriorityFlags = listOf(true),
            entryIds = listOf(9),
        )
        assertEquals(listOf(false, false), itemsSlot.captured.map { it.isPriority })
        assertEquals(listOf(1, 2), itemsSlot.captured.map { it.entryId })
    }

    @Test
    fun `save bumps nextEntryId past max stored id`() = runTest {
        val tracks = listOf(Track("a", "A", duration = 1))
        val urls = listOf("http://a")
        val stateSlot = slot<QueueStateEntity>()
        coEvery { dao.replaceAllAndState(any(), capture(stateSlot)) } just Runs
        manager.save(tracks, urls, 0, 0L, entryIds = listOf(7), nextEntryId = 1)
        assertEquals(8, stateSlot.captured.nextEntryId)
    }

    @Test
    fun `restore strips getCoverArt sentinel and null cover`() = runTest {
        coEvery { dao.getAllOnce() } returns listOf(
            QueueItemEntity(
                trackId = "t1",
                title = "T1",
                url = "http://ex.com/t1",
                position = 0,
                coverArtId = "getCoverArt",
                durationSeconds = 100,
                entryId = 4,
            ),
            QueueItemEntity(
                trackId = "t2",
                title = "T2",
                url = "http://ex.com/t2",
                position = 1,
                coverArtId = null,
                durationSeconds = 100,
                entryId = 5,
            ),
        )
        coEvery { dao.getState() } returns QueueStateEntity(nextEntryId = 1)

        val restored = manager.restore()
        assertNull(restored!!.tracks[0].coverArt)
        assertNull(restored.tracks[1].coverArt)
        assertEquals(6, restored.nextEntryId)
    }
}
