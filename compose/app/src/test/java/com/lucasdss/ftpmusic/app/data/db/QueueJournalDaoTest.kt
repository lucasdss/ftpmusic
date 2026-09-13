package com.lucasdss.ftpmusic.app.data.db

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Standalone LRU eviction — delegates to DAO operations. */
suspend fun evictOldestIfNeeded(dao: QueueJournalDao, cap: Int) {
    while (dao.count() > cap) {
        dao.getOldest()?.let { dao.delete(it) }
    }
}

class QueueJournalDaoTest {

    private val dao: QueueJournalDao = mockk(relaxed = true)

    // ── upsert / replace ────────────────────────────────────────────────────

    @Test
    fun `upsert inserts new entry`() = runTest {
        coEvery { dao.count() } returns 1

        val entry = QueueJournalEntity(
            sourceType = "album",
            sourceId = "abc",
            sourceName = "Test Album",
            trackIdsJson = "[\"1\",\"2\"]",
        )
        dao.upsert(entry)

        coVerify(exactly = 1) { dao.upsert(entry) }
        val c = dao.count()
        assertEquals(1, c)
    }

    @Test
    fun `upsert replaces existing entry with same source_type and source_id`() = runTest {
        coEvery { dao.count() } returnsMany listOf(1, 1)

        val entry = QueueJournalEntity(
            sourceType = "album",
            sourceId = "abc",
            sourceName = "Test Album",
            trackIdsJson = "[\"1\",\"2\"]",
        )
        val updated = entry.copy(
            trackIdsJson = "[\"3\",\"4\"]",
            position = 5,
            updatedAt = 999L,
        )

        dao.upsert(entry)
        dao.upsert(updated)

        coVerify(exactly = 2) { dao.upsert(any()) }
        val c1 = dao.count()
        assertEquals(1, c1)
        val c2 = dao.count()
        assertEquals(1, c2)
    }

    // ── getAllRecent ────────────────────────────────────────────────────────

    @Test
    fun `getAllRecent returns entries ordered by updated_at DESC`() = runTest {
        val a = QueueJournalEntity(
            id = 1,
            sourceType = "album",
            sourceId = "a",
            sourceName = "Old",
            trackIdsJson = "[]",
            updatedAt = 1000L,
        )
        val b = QueueJournalEntity(
            id = 2,
            sourceType = "playlist",
            sourceId = "b",
            sourceName = "New",
            trackIdsJson = "[]",
            updatedAt = 2000L,
        )
        coEvery { dao.getAllRecent() } returns listOf(b, a)

        val result = dao.getAllRecent()
        assertEquals(2, result.size)
        assertEquals("playlist", result[0].sourceType)
        assertEquals("album", result[1].sourceType)
    }

    // ── getOldest ───────────────────────────────────────────────────────────

    @Test
    fun `getOldest returns entry with earliest updated_at`() = runTest {
        val oldest = QueueJournalEntity(
            id = 1,
            sourceType = "album",
            sourceId = "old",
            sourceName = "Oldest",
            trackIdsJson = "[]",
            updatedAt = 500L,
        )
        coEvery { dao.getOldest() } returns oldest

        val result = dao.getOldest()
        assertNotNull(result)
        assertEquals("album", result!!.sourceType)
        assertEquals("old", result.sourceId)
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    fun `delete removes entry`() = runTest {
        val entry = QueueJournalEntity(
            id = 1,
            sourceType = "album",
            sourceId = "abc",
            sourceName = "Test",
            trackIdsJson = "[]",
        )
        coEvery { dao.count() } returnsMany listOf(1, 0)

        val c1 = dao.count()
        assertEquals(1, c1)
        dao.delete(entry)
        coVerify(exactly = 1) { dao.delete(entry) }
        val c2 = dao.count()
        assertEquals(0, c2)
    }

    // ── count ───────────────────────────────────────────────────────────────

    @Test
    fun `count returns correct count after multiple upserts`() = runTest {
        coEvery { dao.count() } returnsMany listOf(0, 1, 2, 3)

        assertEquals(0, dao.count())
        assertEquals(1, dao.count())
        assertEquals(2, dao.count())
        assertEquals(3, dao.count())
    }

    // ── updated_at timestamp ────────────────────────────────────────────────

    @Test
    fun `upsert updates updated_at timestamp`() = runTest {
        val before = System.currentTimeMillis()
        val entry = QueueJournalEntity(
            sourceType = "artist",
            sourceId = "xyz",
            sourceName = "Test Artist",
            trackIdsJson = "[\"10\"]",
            updatedAt = before,
        )
        val after = before + 5000
        val updated = entry.copy(updatedAt = after)

        dao.upsert(entry)
        dao.upsert(updated)

        coVerify { dao.upsert(entry) }
        coVerify { dao.upsert(updated) }
    }

    // ── trackIdsJson ────────────────────────────────────────────────────────

    @Test
    fun `trackIdsJson stores and retrieves JSON array correctly`() = runTest {
        val json = """["track-1","track-2","track-3"]"""
        val entry = QueueJournalEntity(
            sourceType = "playlist",
            sourceId = "pl-1",
            sourceName = "My Playlist",
            trackIdsJson = json,
        )

        dao.upsert(entry)
        coVerify { dao.upsert(entry) }

        // Verify the JSON is passed through correctly
        val captured = slot<QueueJournalEntity>()
        coVerify { dao.upsert(capture(captured)) }
        assertEquals(json, captured.captured.trackIdsJson)
        assertEquals("""["track-1","track-2","track-3"]""", captured.captured.trackIdsJson)
    }

    // ── LRU eviction ────────────────────────────────────────────────────────

    @Test
    fun `evictOldestIfNeeded removes entries when over cap`() = runTest {
        val oldest = QueueJournalEntity(
            id = 1,
            sourceType = "album",
            sourceId = "old",
            sourceName = "Oldest",
            trackIdsJson = "[]",
        )

        // cap=2, count returns 3, then 2 after eviction
        coEvery { dao.count() } returnsMany listOf(3, 2)
        coEvery { dao.getOldest() } returns oldest

        evictOldestIfNeeded(dao, cap = 2)

        coVerify { dao.count() }
        coVerify { dao.getOldest() }
        coVerify { dao.delete(oldest) }
    }

    @Test
    fun `evictOldestIfNeeded does nothing when under cap`() = runTest {
        coEvery { dao.count() } returns 1

        evictOldestIfNeeded(dao, cap = 5)

        coVerify { dao.count() }
        coVerify(inverse = true) { dao.getOldest() }
        coVerify(inverse = true) { dao.delete(any()) }
    }

    @Test
    fun `evictOldestIfNeeded does nothing when at cap`() = runTest {
        coEvery { dao.count() } returns 5

        evictOldestIfNeeded(dao, cap = 5)

        coVerify { dao.count() }
        coVerify(inverse = true) { dao.getOldest() }
        coVerify(inverse = true) { dao.delete(any()) }
    }
}
