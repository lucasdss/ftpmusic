package com.lucasdss.ftpmusic.app.data.db

import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListenHistorySeedTest {

    @Test
    fun `parsePlayDateMs handles iso and epoch`() {
        assertEquals(
            1_700_000_000_000L,
            MetadataSyncWorker.parsePlayDateMs(1_700_000_000_000L),
        )
        assertEquals(
            1_700_000_000_000L,
            MetadataSyncWorker.parsePlayDateMs(1_700_000_000L),
        )
        assertEquals(
            java.time.Instant.parse("2024-01-15T12:00:00Z").toEpochMilli(),
            MetadataSyncWorker.parsePlayDateMs("2024-01-15T12:00:00Z"),
        )
        assertNull(MetadataSyncWorker.parsePlayDateMs(null))
        assertNull(MetadataSyncWorker.parsePlayDateMs(""))
    }

    @Test
    fun `seedListenHistoryIfNeeded writes backfill and sets flag`() = runTest {
        val trackDao: TrackDao = mockk(relaxed = true)
        val listenEventDao: ListenEventDao = mockk(relaxed = true)
        val storage: SecureStorage = mockk(relaxed = true)
        every { storage.get(SecureStorage.KEY_LISTEN_HISTORY_SEEDED) } returns null
        coEvery { listenEventDao.countHonestEvents() } returns 0
        coEvery { listenEventDao.countEventsForTrack(any()) } returns 0

        val worker = MetadataSyncWorker(
            context = mockk(relaxed = true),
            api = mockk(relaxed = true),
            authHelper = mockk(relaxed = true),
            metadataDao = mockk(relaxed = true),
            trackDao = trackDao,
            genreMixDao = mockk(relaxed = true),
            coverArtFallback = mockk(relaxed = true),
            offlineModeManager = mockk(relaxed = true),
            dailyMixRepository = mockk(relaxed = true),
            desiredRepository = mockk(relaxed = true),
            listenEventDao = listenEventDao,
            storage = storage,
        )

        worker.rememberServerPlayStats(
            "t1",
            mapOf(
                "playCount" to 3,
                "playDate" to "2024-01-15T12:00:00Z",
                "duration" to 200,
                "title" to "Song",
                "artist" to "Artist",
            ),
        )

        worker.seedListenHistoryIfNeeded(fullDrainComplete = true)

        coVerify { trackDao.mergeServerPlayStats("t1", 3, any()) }
        coVerify {
            listenEventDao.insertAll(
                match { list ->
                    list.size == 3 &&
                        list.all { it.isBackfill && it.serverSyncedAt != null && it.trackId == "t1" }
                },
            )
        }
        verify { storage.put(SecureStorage.KEY_LISTEN_HISTORY_SEEDED, "true") }
    }

    @Test
    fun `seed skips when honest events already exist`() = runTest {
        val listenEventDao: ListenEventDao = mockk(relaxed = true)
        val storage: SecureStorage = mockk(relaxed = true)
        every { storage.get(SecureStorage.KEY_LISTEN_HISTORY_SEEDED) } returns null
        coEvery { listenEventDao.countHonestEvents() } returns 5

        val worker = MetadataSyncWorker(
            context = mockk(relaxed = true),
            api = mockk(relaxed = true),
            authHelper = mockk(relaxed = true),
            metadataDao = mockk(relaxed = true),
            trackDao = mockk(relaxed = true),
            genreMixDao = mockk(relaxed = true),
            coverArtFallback = mockk(relaxed = true),
            offlineModeManager = mockk(relaxed = true),
            dailyMixRepository = mockk(relaxed = true),
            desiredRepository = mockk(relaxed = true),
            listenEventDao = listenEventDao,
            storage = storage,
        )
        worker.seedListenHistoryIfNeeded(fullDrainComplete = true)
        coVerify(exactly = 0) { listenEventDao.insertAll(any()) }
        verify { storage.put(SecureStorage.KEY_LISTEN_HISTORY_SEEDED, "true") }
    }
}
