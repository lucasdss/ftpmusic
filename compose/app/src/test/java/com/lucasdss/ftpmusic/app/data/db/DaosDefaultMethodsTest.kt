package com.lucasdss.ftpmusic.app.data.db

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JVM tests for the concrete (non-abstract) DAO logic in [Daos.kt]:
 * default interface methods. A MockK mock would intercept the default bodies,
 * so each DAO is wrapped in a Kotlin `by`-delegation fake — the default
 * methods run their REAL implementation (calling the delegated mock for the
 * abstract @Query/@Insert methods), which is what JaCoCo sees.
 */
class DaosDefaultMethodsTest {

    // ── TrackDao.syncRatings ────────────────────────────────────────────────

    @Test
    fun `syncRatings applies every rating`() = runTest {
        val delegate = mockk<TrackDao>(relaxed = true)
        val dao = FakeTrackDao(delegate)
        dao.syncRatings(mapOf("t1" to 4, "t2" to 5))
        coVerify { delegate.setRating("t1", 4) }
        coVerify { delegate.setRating("t2", 5) }
    }

    @Test
    fun `syncRatings with empty map does nothing`() = runTest {
        val delegate = mockk<TrackDao>(relaxed = true)
        val dao = FakeTrackDao(delegate)
        dao.syncRatings(emptyMap())
        coVerify(exactly = 0) { delegate.setRating(any(), any()) }
    }

    // ── QueueDao default methods ────────────────────────────────────────────

    @Test
    fun `savePositionOnly uses a default state when none exists`() = runTest {
        val delegate = mockk<QueueDao>(relaxed = true)
        val dao = FakeQueueDao(delegate)
        coEvery { delegate.getState() } returns null
        dao.savePositionOnly(3, 1000L)
        coVerify { delegate.saveState(match { it.id == 1 && it.currentIndex == 3 && it.positionMs == 1000L }) }
    }

    @Test
    fun `savePositionOnly copies fields from the existing state`() = runTest {
        val delegate = mockk<QueueDao>(relaxed = true)
        val dao = FakeQueueDao(delegate)
        coEvery { delegate.getState() } returns
            QueueStateEntity(id = 7, currentIndex = 1, positionMs = 5L, contextSize = 2)
        dao.savePositionOnly(4, 4000L)
        coVerify {
            delegate.saveState(
                match {
                    it.id == 7 && it.contextSize == 2 && it.currentIndex == 4 &&
                        it.positionMs == 4000L
                },
            )
        }
    }

    @Test
    fun `atomicReplace clears then inserts`() = runTest {
        val delegate = mockk<QueueDao>(relaxed = true)
        val dao = FakeQueueDao(delegate)
        val items = listOf(QueueItemEntity(trackId = "t1", title = "T", url = "http://a", position = 0))
        dao.atomicReplace(items)
        coVerifyOrder {
            delegate.clear()
            delegate.insertAll(items)
        }
    }

    @Test
    fun `replaceAllAndState clears inserts and saves state in order`() = runTest {
        val delegate = mockk<QueueDao>(relaxed = true)
        val dao = FakeQueueDao(delegate)
        val items = listOf(QueueItemEntity(trackId = "t1", title = "T", url = "http://a", position = 0))
        val state = QueueStateEntity(currentIndex = 1, positionMs = 2L)
        dao.replaceAllAndState(items, state)
        coVerifyOrder {
            delegate.clear()
            delegate.insertAll(items)
            delegate.saveState(state)
        }
    }

    // ── PlaylistDao default methods ─────────────────────────────────────────

    @Test
    fun `replaceEntries clears then upserts`() = runTest {
        val delegate = mockk<PlaylistDao>(relaxed = true)
        val dao = FakePlaylistDao(delegate)
        val entries = listOf(PlaylistEntryEntity(playlistId = "pl", trackId = "t1", position = 0))
        dao.replaceEntries("pl", entries)
        coVerifyOrder {
            delegate.clearEntries("pl")
            delegate.upsertEntries(entries)
        }
    }

    @Test
    fun `addTracksToPlaylist dedupes existing tracks and continues positions`() = runTest {
        val delegate = mockk<PlaylistDao>(relaxed = true)
        val dao = FakePlaylistDao(delegate)
        coEvery { delegate.getEntries("pl") } returns
            listOf(PlaylistEntryEntity(playlistId = "pl", trackId = "t1", position = 0))
        coEvery { delegate.getById("pl") } returns PlaylistEntity(id = "pl", name = "P", trackCount = 1)

        val count = dao.addTracksToPlaylist("pl", listOf("t1", "t2"))

        assertEquals(2, count)
        coVerify { delegate.upsertEntries(match { it.size == 1 && it[0].trackId == "t2" && it[0].position == 1 }) }
        coVerify { delegate.upsertAll(match { it.size == 1 && it[0].trackCount == 2 }) }
    }

    @Test
    fun `addTracksToPlaylist all duplicates returns existing size`() = runTest {
        val delegate = mockk<PlaylistDao>(relaxed = true)
        val dao = FakePlaylistDao(delegate)
        coEvery { delegate.getEntries("pl") } returns listOf(
            PlaylistEntryEntity(playlistId = "pl", trackId = "t1", position = 0),
            PlaylistEntryEntity(playlistId = "pl", trackId = "t2", position = 1),
        )

        val count = dao.addTracksToPlaylist("pl", listOf("t1", "t2"))

        assertEquals(2, count)
        coVerify(exactly = 0) { delegate.upsertEntries(any()) }
    }

    @Test
    fun `addTracksToPlaylist missing metadata row skips meta upsert`() = runTest {
        val delegate = mockk<PlaylistDao>(relaxed = true)
        val dao = FakePlaylistDao(delegate)
        coEvery { delegate.getEntries("pl") } returns emptyList()
        coEvery { delegate.getById("pl") } returns null

        val count = dao.addTracksToPlaylist("pl", listOf("t1"))

        assertEquals(1, count)
        coVerify { delegate.upsertEntries(match { it.size == 1 && it[0].position == 0 }) }
        coVerify(exactly = 0) { delegate.upsertAll(any()) }
    }

    @Test
    fun `addTracksToPlaylist appends after the last position`() = runTest {
        val delegate = mockk<PlaylistDao>(relaxed = true)
        val dao = FakePlaylistDao(delegate)
        coEvery { delegate.getEntries("pl") } returns listOf(
            PlaylistEntryEntity(playlistId = "pl", trackId = "t1", position = 5),
        )
        coEvery { delegate.getById("pl") } returns null

        dao.addTracksToPlaylist("pl", listOf("t2", "t3"))

        coVerify { delegate.upsertEntries(match { it[0].position == 6 && it[1].position == 7 }) }
    }

    // ── CacheStatusRow data class ───────────────────────────────────────────

    @Test
    fun `cacheStatusRow exposes its fields`() {
        val row = TrackDao.CacheStatusRow(cached_file_path = "path", is_downloaded = true)
        assertEquals("path", row.cached_file_path)
        assertTrue(row.is_downloaded)
    }

    // ── QueueJournalDao.evictIfNeeded ───────────────────────────────────────

    @Test
    fun `evictIfNeeded deletes oldest entries while over cap`() = runTest {
        val delegate = mockk<QueueJournalDao>(relaxed = true)
        val dao = FakeQueueJournalDao(delegate)
        coEvery { delegate.count() } returnsMany listOf(3, 2, 1)
        coEvery { delegate.getOldest() } returnsMany listOf(
            QueueJournalEntity(id = 1, sourceType = "s", sourceId = "1", sourceName = null, trackIdsJson = "[]"),
            QueueJournalEntity(id = 2, sourceType = "s", sourceId = "2", sourceName = null, trackIdsJson = "[]"),
        )

        dao.evictIfNeeded(cap = 1)

        coVerify(exactly = 2) { delegate.delete(any()) }
        coVerify(exactly = 1) { delegate.delete(match { it.id == 1 }) }
        coVerify(exactly = 1) { delegate.delete(match { it.id == 2 }) }
    }

    @Test
    fun `evictIfNeeded breaks when no oldest row exists`() = runTest {
        val delegate = mockk<QueueJournalDao>(relaxed = true)
        val dao = FakeQueueJournalDao(delegate)
        coEvery { delegate.count() } returns 5
        coEvery { delegate.getOldest() } returns null

        dao.evictIfNeeded(cap = 1)

        coVerify(exactly = 0) { delegate.delete(any()) }
    }

    @Test
    fun `evictIfNeeded does nothing when under cap`() = runTest {
        val delegate = mockk<QueueJournalDao>(relaxed = true)
        val dao = FakeQueueJournalDao(delegate)
        coEvery { delegate.count() } returns 1

        dao.evictIfNeeded(cap = 5)

        coVerify(exactly = 0) { delegate.delete(any()) }
    }

    // ── Delegation fakes: abstract methods → mock, default methods → real ───

    private class FakeTrackDao(delegate: TrackDao) : TrackDao by delegate {
        override suspend fun syncRatings(ratings: Map<String, Int>) = super<TrackDao>.syncRatings(ratings)
    }

    private class FakeQueueDao(delegate: QueueDao) : QueueDao by delegate {
        override suspend fun savePositionOnly(index: Int, positionMs: Long) =
            super<QueueDao>.savePositionOnly(index, positionMs)

        override suspend fun atomicReplace(items: List<QueueItemEntity>) = super<QueueDao>.atomicReplace(items)

        override suspend fun replaceAllAndState(items: List<QueueItemEntity>, state: QueueStateEntity) =
            super<QueueDao>.replaceAllAndState(items, state)
    }

    private class FakePlaylistDao(delegate: PlaylistDao) : PlaylistDao by delegate {
        override suspend fun replaceEntries(playlistId: String, entries: List<PlaylistEntryEntity>) =
            super<PlaylistDao>.replaceEntries(playlistId, entries)

        override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>): Int =
            super<PlaylistDao>.addTracksToPlaylist(playlistId, trackIds)
    }

    private class FakeQueueJournalDao(delegate: QueueJournalDao) : QueueJournalDao by delegate {
        override suspend fun evictIfNeeded(cap: Int) = super<QueueJournalDao>.evictIfNeeded(cap)
    }
}
