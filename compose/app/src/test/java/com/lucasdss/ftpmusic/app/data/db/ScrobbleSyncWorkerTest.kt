package com.lucasdss.ftpmusic.app.data.db

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScrobbleSyncWorkerTest {

    private val listenEventDao: ListenEventDao = mockk(relaxed = true)
    private val api: SubsonicApi = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)

    private fun TestScope.worker(offline: Boolean = false): ScrobbleSyncWorker {
        every { offlineModeManager.isQueueEnabled() } returns offline
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "u"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "p"
        return ScrobbleSyncWorker(
            listenEventDao,
            api,
            storage,
            offlineModeManager,
            scope = this,
        )
    }

    @Test
    fun `flush skips when offline`() = runTest {
        val w = worker(offline = true)
        assertEquals(false, w.flushNowAndAwait())
        coVerify(exactly = 0) { api.scrobble(any(), id = any(), submission = any(), time = any()) }
    }

    @Test
    fun `flush marks synced on ok with historical time`() = runTest {
        val event = ListenEventEntity(
            id = 9,
            trackId = "t1",
            listenedAt = 1_700_000_000_000L,
            listenedSeconds = 120,
            isBackfill = false,
            serverSyncedAt = null,
        )
        coEvery {
            listenEventDao.getPendingScrobbles(any(), any())
        } returnsMany listOf(listOf(event), emptyList())
        coEvery {
            api.scrobble(any(), id = "t1", submission = true, time = 1_700_000_000_000L)
        } returns mapOf("subsonic-response" to mapOf("status" to "ok"))

        val w = worker()
        assertTrue(w.flushNowAndAwait())
        coVerify { listenEventDao.markSynced(eq(9L), any()) }
    }

    @Test
    fun `flush bumps attempts and stops batch on network error`() = runTest {
        val e1 = ListenEventEntity(
            id = 1,
            trackId = "a",
            listenedAt = 1L,
            listenedSeconds = 10,
            serverSyncedAt = null,
        )
        val e2 = ListenEventEntity(
            id = 2,
            trackId = "b",
            listenedAt = 2L,
            listenedSeconds = 10,
            serverSyncedAt = null,
        )
        coEvery { listenEventDao.getPendingScrobbles(any(), any()) } returns listOf(e1, e2)
        coEvery {
            api.scrobble(any(), id = "a", submission = true, time = 1L)
        } throws IOException("down")

        val w = worker()
        w.flushNowAndAwait()
        coVerify { listenEventDao.bumpSyncAttempts(1L) }
        coVerify(exactly = 0) { api.scrobble(any(), id = "b", submission = true, time = 2L) }
    }

    @Test
    fun `backoff idle when empty`() {
        val w = ScrobbleSyncWorker(listenEventDao, api, storage, offlineModeManager)
        assertEquals(ScrobbleSyncWorker.IDLE_INTERVAL_MS, w.backoffMs(0, pendingEmpty = true))
        assertTrue(w.backoffMs(1, pendingEmpty = false) >= 15_000L)
    }
}
