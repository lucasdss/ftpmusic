package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.db.SessionStateDao
import com.lucasdss.ftpmusic.app.data.db.SessionStateEntity
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Branch coverage extensions for [SessionPersistenceService]: the 5-second
 * throttle branch, null-value JSON serialization, empty-queue restore, and
 * flush-without-pending.
 */
class SessionPersistenceServiceBranchTest {

    private val dao: SessionStateDao = mockk(relaxed = true)
    private val service = SessionPersistenceService(dao)

    @Test
    fun `save builds a pending state and flushes immediately on first save`() = runTest {
        coEvery { dao.upsert(any()) } just Runs
        service.save(listOf(mapOf<String, Any>("id" to "t1", "title" to "Track 1")), 0, 0)

        val slot = io.mockk.slot<SessionStateEntity>()
        coVerify(exactly = 1) { dao.upsert(capture(slot)) }
        org.junit.Assert.assertEquals(1, slot.captured.id)
        org.junit.Assert.assertEquals(0, slot.captured.currentIndex)
        org.junit.Assert.assertEquals(0, slot.captured.positionMs)
    }

    @Test
    fun `save twice within throttle window only flushes once`() = runTest {
        coEvery { dao.upsert(any()) } just Runs
        // First save flushes immediately (lastSaveMs = 0)
        service.save(listOf(mapOf<String, Any>("id" to "t1")), 0, 0)
        coVerify(exactly = 1) { dao.upsert(any()) }

        // Second save within 5s → pending only, no flush
        service.save(listOf(mapOf<String, Any>("id" to "t2")), 1, 1)
        coVerify(exactly = 1) { dao.upsert(any()) }

        // Explicit flush persists the pending state
        service.flush()
        coVerify(exactly = 2) { dao.upsert(any()) }
    }

    @Test
    fun `restore returns null when stored queue is empty`() = runTest {
        coEvery { dao.get() } returns SessionStateEntity(
            id = 1,
            queueJson = "",
            currentIndex = 0,
            positionMs = 0,
        )
        assertNull(service.restore())
    }

    @Test
    fun `flush without pending state does not write`() = runTest {
        coEvery { dao.upsert(any()) } just Runs
        service.flush()
        coVerify(exactly = 0) { dao.upsert(any()) }
    }
}
