package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.db.SessionStateDao
import com.lucasdss.ftpmusic.app.data.db.SessionStateEntity
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SessionPersistenceServiceTest {

    private val dao: SessionStateDao = mockk(relaxed = true)
    private val service = SessionPersistenceService(dao)

    @Test
    fun `restore returns null when no session`() = runTest {
        coEvery { dao.get() } returns null
        assertNull(service.restore())
    }

    @Test
    fun `save then restore returns queue`() = runTest {
        val queue = listOf(
            mapOf("id" to "t1", "title" to "Track 1", "artist" to "Artist"),
            mapOf("id" to "t2", "title" to "Track 2", "artist" to "Artist"),
        )
        coEvery { dao.upsert(any()) } just Runs
        coEvery { dao.get() } returns SessionStateEntity(
            id = 1,
            queueJson =
                """[{"id":"t1","title":"Track 1","artist":"Artist"},{"id":"t2","title":"Track 2","artist":"Artist"}]""",
            currentIndex = 0,
            positionMs = 15000,
        )

        service.save(queue, 0, 15000)
        service.flush()
        val restored = service.restore()

        assertNotNull(restored)
        assertEquals(0, restored!!.currentIndex)
        assertEquals(15000, restored.positionMs)
        assertTrue(restored.queueJson.contains("Track 1"))
    }

    @Test
    fun `clear removes session`() = runTest {
        coEvery { dao.clear() } just Runs
        coEvery { dao.get() } returns null
        service.clear()
        assertNull(service.restore())
    }
}
