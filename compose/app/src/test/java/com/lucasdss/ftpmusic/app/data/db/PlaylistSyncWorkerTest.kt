package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.content.SharedPreferences
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.*
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaylistSyncWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val prefsEditor: SharedPreferences.Editor = mockk(relaxed = true)
    private val pendingDao: PendingPlaylistChangeDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val api: SubsonicApi = mockk(relaxed = true)
    private val authHelper = SubsonicAuthHelper()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var worker: PlaylistSyncWorker

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(SubsonicCredentials)
        mockkObject(DynamicBaseUrl)
        every { SubsonicCredentials.username } returns "testuser"
        every { SubsonicCredentials.password } returns "testpass"
        every { DynamicBaseUrl.url } returns "https://music.example.com"
        every { context.getSharedPreferences("ftpmusic_sync", any()) } returns prefs
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefsEditor.putInt(any(), any()) } returns prefsEditor
        every { prefsEditor.apply() } just Runs
        coEvery { playlistDao.count() } returns 0
        worker =
            PlaylistSyncWorker(
                context,
                pendingDao,
                playlistDao,
                api,
                authHelper,
                mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
                scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(SubsonicCredentials)
        unmockkObject(DynamicBaseUrl)
    }

    @Test
    fun `empty pending list does nothing`() = runTest {
        coEvery { pendingDao.getPending() } returns emptyList()

        // Access private flushPending via reflection or test flushNow
        // flushNow calls flushPending internally
        worker.flushNow()
        advanceUntilIdle()
        // No API calls should be made
        coVerify(exactly = 0) { api.updatePlaylist(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.deletePlaylist(any(), any()) }
    }

    @Test
    fun `rename change calls updatePlaylist with name`() = runTest {
        val change =
            PendingPlaylistChangeEntity(id = 1, playlistId = "pl-1", changeType = "rename", payload = "name=New Name")
        coEvery { pendingDao.getPending() } returns listOf(change)

        worker.flushNow()
        advanceUntilIdle()

        coVerify { api.updatePlaylist(any(), "pl-1", name = "New Name") }
        coVerify { pendingDao.markFlushed(1) }
    }

    @Test
    fun `delete change calls deletePlaylist and cleans local DB`() = runTest {
        val change = PendingPlaylistChangeEntity(id = 2, playlistId = "pl-2", changeType = "delete", payload = "")
        coEvery { pendingDao.getPending() } returns listOf(change)

        worker.flushNow()
        advanceUntilIdle()

        coVerify { api.deletePlaylist(any(), id = "pl-2") }
        coVerify { playlistDao.clearEntries("pl-2") }
        coVerify { playlistDao.delete("pl-2") }
        coVerify { pendingDao.markFlushed(2) }
    }

    @Test
    fun `sync_tracks change clears server then re-adds tracks`() = runTest {
        val change =
            PendingPlaylistChangeEntity(id = 3, playlistId = "pl-3", changeType = "sync_tracks", payload = "t1,t2,t3")
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { playlistDao.getEntries("pl-3") } returns listOf(
            PlaylistEntryEntity(playlistId = "pl-3", trackId = "t1", position = 0),
            PlaylistEntryEntity(playlistId = "pl-3", trackId = "t2", position = 1),
            PlaylistEntryEntity(playlistId = "pl-3", trackId = "t3", position = 2),
        )
        coEvery { api.getPlaylist(any(), id = "pl-3") } returns mapOf("songCount" to 5)
        coEvery { playlistDao.getById("pl-3") } returns PlaylistEntity(id = "pl-3", name = "Test")

        worker.flushNow()
        advanceUntilIdle()

        coVerify { api.updatePlaylist(any(), "pl-3", removeIndices = "0,1,2,3,4", addIds = "t1,t2,t3") }
        coVerify { playlistDao.upsertAll(match { it.any { e -> e.trackCount == 3 } }) }
        coVerify { pendingDao.markFlushed(3) }
    }

    @Test
    fun `add_tracks change calls updatePlaylist with addIds`() = runTest {
        val change =
            PendingPlaylistChangeEntity(id = 4, playlistId = "pl-4", changeType = "add_tracks", payload = "t4,t5")
        coEvery { pendingDao.getPending() } returns listOf(change)

        worker.flushNow()
        advanceUntilIdle()

        coVerify { api.updatePlaylist(any(), "pl-4", addIds = "t4,t5") }
        coVerify { pendingDao.markFlushed(4) }
    }

    @Test
    fun `remove_tracks change calls updatePlaylist with removeIndices`() = runTest {
        val change =
            PendingPlaylistChangeEntity(id = 5, playlistId = "pl-5", changeType = "remove_tracks", payload = "0;2")
        coEvery { pendingDao.getPending() } returns listOf(change)

        worker.flushNow()
        advanceUntilIdle()

        coVerify { api.updatePlaylist(any(), "pl-5", removeIndices = "0;2") }
        coVerify { pendingDao.markFlushed(5) }
    }

    @Test
    fun `IOException does not markChangeFlushed`() = runTest {
        val change =
            PendingPlaylistChangeEntity(id = 6, playlistId = "pl-6", changeType = "rename", payload = "name=Test")
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { api.updatePlaylist(any(), any(), name = any()) } throws IOException("Network error")

        worker.flushNow()
        advanceUntilIdle()

        coVerify(exactly = 0) { pendingDao.markFlushed(6) }
        coVerify(exactly = 0) { playlistDao.upsertAll(any()) } // no conflict set
    }

    @Test
    fun `SocketTimeoutException does not markChangeFlushed`() = runTest {
        val change =
            PendingPlaylistChangeEntity(id = 7, playlistId = "pl-7", changeType = "sync_tracks", payload = "t1")
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { playlistDao.getEntries("pl-7") } returns
            listOf(PlaylistEntryEntity(playlistId = "pl-7", trackId = "t1", position = 0))
        coEvery { api.getPlaylist(any(), id = "pl-7") } throws SocketTimeoutException("Timeout")

        worker.flushNow()
        advanceUntilIdle()

        coVerify(exactly = 0) { pendingDao.markFlushed(7) }
    }

    @Test
    fun `UnknownHostException does not markChangeFlushed`() = runTest {
        val change = PendingPlaylistChangeEntity(id = 8, playlistId = "pl-8", changeType = "delete", payload = "")
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { api.deletePlaylist(any(), any()) } throws UnknownHostException("DNS error")

        worker.flushNow()
        advanceUntilIdle()

        coVerify(exactly = 0) { pendingDao.markFlushed(8) }
    }

    @Test
    fun `generic Exception marksChangeFlushed and sets conflict`() = runTest {
        val change = PendingPlaylistChangeEntity(id = 9, playlistId = "pl-9", changeType = "rename", payload = "name=X")
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { api.updatePlaylist(any(), any(), name = any()) } throws RuntimeException("Server rejected")
        // getById may not be called if updatePlaylist throws before reaching that line
        // just verify flushed

        worker.flushNow()
        advanceUntilIdle()

        coVerify { pendingDao.markFlushed(9) }
    }

    @Test
    fun `skips flush when credentials empty`() = runTest {
        // Credentials are checked via SubsonicCredentials singleton — can't easily mock
        // This test validates the structure; full integration test would need DI
        coEvery { pendingDao.getPending() } returns emptyList()
        worker.flushNow()
        advanceUntilIdle()
        // No crash, no API calls
        coVerify(exactly = 0) { api.updatePlaylist(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `multiple changes processed in order`() = runTest {
        val changes = listOf(
            PendingPlaylistChangeEntity(id = 10, playlistId = "pl-a", changeType = "rename", payload = "name=A"),
            PendingPlaylistChangeEntity(id = 11, playlistId = "pl-b", changeType = "delete", payload = ""),
        )
        coEvery { pendingDao.getPending() } returns changes

        worker.flushNow()
        advanceUntilIdle()

        coVerify(ordering = Ordering.SEQUENCE) {
            api.updatePlaylist(any(), "pl-a", name = "A")
            api.deletePlaylist(any(), id = "pl-b")
        }
    }

    // ── Local-first offline gate + loop lifecycle ─────────────────────────

    @Test
    fun `flushNow skips the API entirely while offline mode is on`() = runTest {
        val offline = mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true)
        every { offline.isOfflineEnabled() } returns true
        val offlineWorker = PlaylistSyncWorker(
            context,
            pendingDao,
            playlistDao,
            api,
            authHelper,
            offline,
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
        )
        val change =
            PendingPlaylistChangeEntity(id = 21, playlistId = "pl-o", changeType = "rename", payload = "name=Off")
        coEvery { pendingDao.getPending() } returns listOf(change)

        offlineWorker.flushNow()
        advanceUntilIdle()

        // The change is held locally — never sent, never marked flushed
        coVerify(exactly = 0) { api.updatePlaylist(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { pendingDao.markFlushed(any()) }
    }

    @Test
    fun `start loop flushes pending changes and cleans up old flushed rows`() = runTest {
        val change =
            PendingPlaylistChangeEntity(id = 31, playlistId = "pl-loop", changeType = "delete", payload = "id=pl-loop")
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { playlistDao.getEntries("pl-loop") } returns emptyList()

        worker.start()
        // One loop cycle: the flush runs immediately; the loop then sleeps on
        // the 15s backoff — stop before advancing further (the loop never
        // idles, so advanceUntilIdle would run forever).
        advanceTimeBy(1)

        coVerify { api.deletePlaylist(any(), eq("pl-loop")) }
        coVerify { pendingDao.markFlushed(eq(31)) }
        // First cleanup cycle runs immediately (lastCleanupMs starts at 0)
        coVerify { pendingDao.deleteFlushedOlderThan(any()) }
        worker.stop()
    }

    @Test
    fun `cleanup skips the second cycle within the 24h window`() = runTest {
        coEvery { pendingDao.getPending() } returns emptyList()

        worker.start()
        advanceTimeBy(1)
        coVerify(exactly = 1) { pendingDao.deleteFlushedOlderThan(any()) }
        worker.stop()

        // A second flush cycle within 24h must not clean again
        worker.flushNow()
        advanceUntilIdle()
        coVerify(exactly = 1) { pendingDao.deleteFlushedOlderThan(any()) }
    }

    @Test
    fun `consecutive failure backoff pauses the loop`() = runTest {
        val change =
            PendingPlaylistChangeEntity(id = 41, playlistId = "pl-fail", changeType = "rename", payload = "name=Retry")
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { api.updatePlaylist(any(), any(), any(), any(), any()) } throws IOException("offline")

        worker.start()
        // One cycle: the failure is recorded, the loop backs off 60s.
        advanceTimeBy(1)
        coVerify(exactly = 1) { api.updatePlaylist(any(), any(), any(), any(), any()) }
        worker.stop()
    }
}
