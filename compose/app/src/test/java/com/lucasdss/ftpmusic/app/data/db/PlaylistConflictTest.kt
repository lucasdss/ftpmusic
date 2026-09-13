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
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaylistConflictTest {

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
        every { SubsonicCredentials.username } returns "user"
        every { SubsonicCredentials.password } returns "pass"
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
    fun `server rejection marks change flushed and playlist conflicted`() = runTest(testDispatcher) {
        val change = PendingPlaylistChangeEntity(
            id = 1,
            playlistId = "pl-1",
            changeType = "rename",
            payload = "name=NewName",
        )
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { api.updatePlaylist(any(), any(), name = any()) } throws RuntimeException("Server rejected")
        coEvery { playlistDao.getById("pl-1") } returns PlaylistEntity(
            id = "pl-1",
            name = "OldName",
        )

        worker.flushNow()
        advanceUntilIdle()

        coVerify { pendingDao.markFlushed(1) }
        coVerify {
            playlistDao.upsertAll(
                match { entities ->
                    entities.any { it.isConflicted && it.conflictMessage?.contains("Server rejected") == true }
                },
            )
        }
    }

    @Test
    fun `network timeout does not mark flushed`() = runTest(testDispatcher) {
        val change = PendingPlaylistChangeEntity(
            id = 2,
            playlistId = "pl-2",
            changeType = "delete",
            payload = "",
        )
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { api.deletePlaylist(any(), any()) } throws SocketTimeoutException("timeout")

        worker.flushNow()
        advanceUntilIdle()

        coVerify(exactly = 0) { pendingDao.markFlushed(2) }
    }

    @Test
    fun `DNS failure does not mark flushed`() = runTest(testDispatcher) {
        val change = PendingPlaylistChangeEntity(
            id = 3,
            playlistId = "pl-3",
            changeType = "rename",
            payload = "",
        )
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { api.updatePlaylist(any(), any(), name = any()) } throws UnknownHostException("no DNS")

        worker.flushNow()
        advanceUntilIdle()

        coVerify(exactly = 0) { pendingDao.markFlushed(3) }
    }

    @Test
    fun `network IO error does not mark flushed`() = runTest(testDispatcher) {
        val change = PendingPlaylistChangeEntity(
            id = 4,
            playlistId = "pl-4",
            changeType = "delete",
            payload = "",
        )
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { api.deletePlaylist(any(), any()) } throws IOException("network down")

        worker.flushNow()
        advanceUntilIdle()

        coVerify(exactly = 0) { pendingDao.markFlushed(4) }
    }

    @Test
    fun `successful rename marks flushed without conflict`() = runTest(testDispatcher) {
        val change = PendingPlaylistChangeEntity(
            id = 5,
            playlistId = "pl-5",
            changeType = "rename",
            payload = "name=New",
        )
        coEvery { pendingDao.getPending() } returns listOf(change)

        worker.flushNow()
        advanceUntilIdle()

        coVerify { pendingDao.markFlushed(5) }
    }

    @Test
    fun `successful create marks flushed`() = runTest(testDispatcher) {
        val change = PendingPlaylistChangeEntity(
            id = 6,
            playlistId = "pl-new",
            changeType = "create",
            payload = "name=NewPlaylist",
        )
        coEvery { pendingDao.getPending() } returns listOf(change)
        coEvery { api.createPlaylist(any(), name = any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "playlist" to mapOf("id" to "pl-new", "name" to "NewPlaylist"),
            ),
        )

        worker.flushNow()
        advanceUntilIdle()

        coVerify { pendingDao.markFlushed(6) }
    }
}
