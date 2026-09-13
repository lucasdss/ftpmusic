package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.content.SharedPreferences
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Branch coverage extensions for [PlaylistSyncWorker]: the "create" change
 * (temp-ID remapping), zero-track sync_tracks, conflict marking, the flushNow
 * re-entrancy guard, empty-credentials skip, backoff computation, and the
 * constructor default scope.
 */
class PlaylistSyncWorkerBranchTest {

    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val prefsEditor: SharedPreferences.Editor = mockk(relaxed = true)
    private val pendingDao: PendingPlaylistChangeDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val api: SubsonicApi = mockk(relaxed = true)
    private val authHelper = SubsonicAuthHelper()
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var worker: PlaylistSyncWorker

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { SubsonicCredentials.username } returns "testuser"
        every { SubsonicCredentials.password } returns "testpass"
        every { DynamicBaseUrl.url } returns "https://music.example.com"
        every { context.getSharedPreferences("ftpmusic_sync", any()) } returns prefs
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefsEditor.putInt(any(), any()) } returns prefsEditor
        every { prefsEditor.apply() } just Runs
        every { offlineModeManager.isOfflineEnabled() } returns false
        coEvery { playlistDao.count() } returns 0
        worker = PlaylistSyncWorker(
            context,
            pendingDao,
            playlistDao,
            api,
            authHelper,
            offlineModeManager,
            scope = CoroutineScope(testDispatcher),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    companion object {
        init {
            // Mock the singletons ONCE per class — per-test mockkObject
            // redefinition churns the javaagent and can trigger the
            // "can't create name string" instrumentation crash.
            mockkObject(SubsonicCredentials)
            mockkObject(DynamicBaseUrl)
        }
    }

    private fun change(id: Int, playlistId: String, type: String, payload: String) =
        PendingPlaylistChangeEntity(id = id, playlistId = playlistId, changeType = type, payload = payload)

    // ── create change + temp-id remapping ───────────────────────────────────

    @Test
    fun `create change remaps temp id to server id`() = runTest {
        val c = change(1, "new-123", "create", "name=My Mix")
        coEvery { pendingDao.getPending() } returns listOf(c)
        coEvery { playlistDao.getById(any()) } returns null // avoid relaxed mock entity in updateLastSyncedAt
        coEvery { api.createPlaylist(any(), name = "My Mix") } returns mapOf(
            "subsonic-response" to mapOf(
                "playlist" to mapOf("id" to "server-9", "name" to "My Mix", "songCount" to 3, "coverArt" to "ca"),
            ),
        )

        worker.flushNow()
        advanceUntilIdle()

        coVerify { playlistDao.delete("new-123") }
        coVerify { playlistDao.upsertAll(match { it.size == 1 && it[0].id == "server-9" && it[0].trackCount == 3 }) }
        coVerify { pendingDao.remapPlaylistId("new-123", "server-9") }
        coVerify { pendingDao.markFlushed(1) }
    }

    @Test
    fun `create change keeps local id when server returns the same id`() = runTest {
        val c = change(2, "same-id", "create", "name=X")
        coEvery { pendingDao.getPending() } returns listOf(c)
        coEvery { api.createPlaylist(any(), name = "X") } returns mapOf(
            "subsonic-response" to mapOf("playlist" to mapOf("id" to "same-id")),
        )

        worker.flushNow()
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistDao.delete(any()) }
        coVerify(exactly = 0) { pendingDao.remapPlaylistId(any(), any()) }
        coVerify { pendingDao.markFlushed(2) }
    }

    @Test
    fun `create change handles missing server id without remapping`() = runTest {
        val c = change(3, "new-xyz", "create", "name=Y")
        coEvery { pendingDao.getPending() } returns listOf(c)
        coEvery { api.createPlaylist(any(), name = "Y") } returns
            mapOf("subsonic-response" to mapOf<String, Any?>())

        worker.flushNow()
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistDao.delete(any()) }
        coVerify(exactly = 0) { pendingDao.remapPlaylistId(any(), any()) }
        coVerify { pendingDao.markFlushed(3) }
    }

    // ── sync_tracks edge branches ───────────────────────────────────────────

    @Test
    fun `sync_tracks with zero server tracks uses empty removeIndices`() = runTest {
        val c = change(4, "pl-0", "sync_tracks", "t1")
        coEvery { pendingDao.getPending() } returns listOf(c)
        coEvery { playlistDao.getEntries("pl-0") } returns listOf(
            PlaylistEntryEntity(playlistId = "pl-0", trackId = "t1", position = 0),
        )
        coEvery { api.getPlaylist(any(), id = "pl-0") } returns mapOf("songCount" to 0)

        worker.flushNow()
        advanceUntilIdle()

        coVerify { api.updatePlaylist(any(), "pl-0", removeIndices = "", addIds = "t1") }
        coVerify { pendingDao.markFlushed(4) }
    }

    @Test
    fun `sync_tracks without metadata row skips meta upsert`() = runTest {
        val c = change(5, "pl-nometa", "sync_tracks", "t1,t2")
        coEvery { pendingDao.getPending() } returns listOf(c)
        coEvery { playlistDao.getEntries("pl-nometa") } returns listOf(
            PlaylistEntryEntity(playlistId = "pl-nometa", trackId = "t1", position = 0),
            PlaylistEntryEntity(playlistId = "pl-nometa", trackId = "t2", position = 1),
        )
        coEvery { api.getPlaylist(any(), id = "pl-nometa") } returns mapOf("songCount" to 2)
        coEvery { playlistDao.getById("pl-nometa") } returns null

        worker.flushNow()
        advanceUntilIdle()

        coVerify { api.updatePlaylist(any(), "pl-nometa", removeIndices = "0,1", addIds = "t1,t2") }
        coVerify(exactly = 0) { playlistDao.upsertAll(any()) }
    }

    // ── failure/conflict branches ───────────────────────────────────────────

    @Test
    fun `generic exception marks playlist conflicted when row exists`() = runTest {
        val c = change(6, "pl-conflict", "rename", "name=New")
        coEvery { pendingDao.getPending() } returns listOf(c)
        coEvery { api.updatePlaylist(any(), any(), name = any()) } throws RuntimeException("rejected")
        coEvery { playlistDao.getById("pl-conflict") } returns PlaylistEntity(id = "pl-conflict", name = "Old")

        worker.flushNow()
        advanceUntilIdle()

        coVerify { pendingDao.markFlushed(6) }
        coVerify {
            playlistDao.upsertAll(
                match {
                    it.size == 1 && it[0].isConflicted &&
                        it[0].conflictMessage!!.contains("rejected")
                },
            )
        }
    }

    @Test
    fun `flushNow no-ops when already flushing`() = runTest {
        val c = change(7, "pl-re", "rename", "name=A")
        coEvery { pendingDao.getPending() } returns listOf(c)

        worker.flushNow() // sets isFlushing = true, launches
        worker.flushNow() // CAS fails → returns immediately
        advanceUntilIdle()

        coVerify(exactly = 1) { api.updatePlaylist(any(), any(), name = "A") }
    }

    @Test
    fun `flush with pending changes skips when credentials empty`() = runTest {
        every { SubsonicCredentials.username } returns ""
        val c = change(8, "pl-nocred", "rename", "name=A")
        coEvery { pendingDao.getPending() } returns listOf(c)

        worker.flushNow()
        advanceUntilIdle()

        coVerify(exactly = 0) { api.updatePlaylist(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { pendingDao.markFlushed(any()) }
    }

    @Test
    fun `rename without equals separator passes payload through`() = runTest {
        val c = change(9, "pl-raw", "rename", "just-a-name")
        coEvery { pendingDao.getPending() } returns listOf(c)

        worker.flushNow()
        advanceUntilIdle()

        coVerify { api.updatePlaylist(any(), "pl-raw", name = "just-a-name") }
    }

    // ── loop lifecycle + backoff ────────────────────────────────────────────

    @Test
    fun `start loop breaks after max consecutive failures`() = runTest {
        val field = PlaylistSyncWorker::class.java.getDeclaredField("consecutiveFailures")
        field.isAccessible = true
        field.setInt(worker, 10)

        worker.start()
        advanceTimeBy(1)

        // Loop breaks before flushing
        coVerify(exactly = 0) { pendingDao.getPending() }
        worker.stop()
    }

    @Test
    fun `cleanup failure is swallowed by the loop`() = runTest {
        coEvery { pendingDao.getPending() } returns emptyList()
        coEvery { pendingDao.deleteFlushedOlderThan(any()) } throws RuntimeException("db error")

        worker.start()
        advanceTimeBy(1)

        // First cycle completed despite cleanup failure
        coVerify(exactly = 1) { pendingDao.getPending() }
        worker.stop()
    }

    @Test
    fun `stats are written after a successful flush`() = runTest {
        val c = change(10, "pl-stats", "rename", "name=A")
        coEvery { pendingDao.getPending() } returns listOf(c)

        worker.flushNow()
        advanceUntilIdle()

        coVerify { prefsEditor.putLong("last_playlist_sync_ms", any()) }
        coVerify { prefsEditor.putInt("playlist_count", 0) }
    }

    @Test
    fun `worker without explicit scope uses the main dispatcher`() = runTest {
        val defaultWorker = PlaylistSyncWorker(
            context,
            pendingDao,
            playlistDao,
            api,
            authHelper,
            offlineModeManager,
        )
        assertNotNull(defaultWorker)
        coEvery { pendingDao.getPending() } returns emptyList()
        defaultWorker.flushNow()
        advanceUntilIdle()
        coVerify(exactly = 0) { api.updatePlaylist(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `calculateBackoff scales with failure count`() {
        val method = PlaylistSyncWorker::class.java.getDeclaredMethod("calculateBackoff", Int::class.javaPrimitiveType)
        method.isAccessible = true
        assertEquals(15_000L, method.invoke(worker, 0))
        assertEquals(60_000L, method.invoke(worker, 1))
        assertEquals(60_000L, method.invoke(worker, 3))
        assertEquals(5 * 60_000L, method.invoke(worker, 6))
        assertEquals(30 * 60_000L, method.invoke(worker, 7))
    }

    @Test
    fun `create change remaps entries written under the temp id`() = runTest {
        val c = change(4, "new-456", "create", "name=Race")
        coEvery { pendingDao.getPending() } returns listOf(c)
        coEvery { playlistDao.getById(any()) } returns null
        coEvery { api.createPlaylist(any(), name = "Race") } returns mapOf(
            "subsonic-response" to mapOf("playlist" to mapOf("id" to "server-7", "name" to "Race", "songCount" to 0)),
        )
        // tracks added to the temp id before this flush
        coEvery { playlistDao.getEntries("server-7") } returns listOf(
            PlaylistEntryEntity(playlistId = "server-7", trackId = "t1", position = 0),
            PlaylistEntryEntity(playlistId = "server-7", trackId = "t2", position = 1),
        )

        worker.flushNow()
        advanceUntilIdle()

        coVerify { playlistDao.remapEntries("new-456", "server-7") }
        // trackCount reflects the remapped entries (2), not just server songCount (0)
        coVerify { playlistDao.upsertAll(match { it.size == 1 && it[0].id == "server-7" && it[0].trackCount == 2 }) }
    }
}
