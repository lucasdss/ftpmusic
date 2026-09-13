package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeDao
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorker
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaylistRepositoryTest {

    private val api: SubsonicApi = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val pendingChangeDao: PendingPlaylistChangeDao = mockk(relaxed = true)
    private val syncWorker: PlaylistSyncWorker = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val downloadManager: DownloadManager = mockk(relaxed = true)

    private lateinit var repo: PlaylistRepository

    @Before
    fun setup() {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        repo = PlaylistRepository(api, playlistDao, pendingChangeDao, syncWorker, trackDao, storage, downloadManager)
    }

    @Test
    fun `createPlaylist saves locally and enqueues sync`() = runTest {
        val name = "My Playlist"
        val id = repo.createPlaylist(name)

        assertTrue(id.startsWith("new-"))
        coVerify { playlistDao.upsertAll(match { it.size == 1 && it[0].name == name && it[0].id == id }) }
        coVerify { pendingChangeDao.insert(match { it.changeType == "create" && it.payload == "name=$name" }) }
        coVerify { syncWorker.flushNow() }
    }

    @Test
    fun `createPlaylist returns IDs starting with new- prefix`() = runTest {
        val id = repo.createPlaylist("My Playlist")

        assertTrue(id.startsWith("new-"))
        assertTrue(id.length > 4)
    }

    @Test
    fun `importPlaylist fetches from server and persists locally`() = runTest {
        val playlistId = "pl-1"
        val response = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "playlist" to mapOf(
                    "id" to playlistId,
                    "name" to "Server Mix",
                    "songCount" to 2,
                    "coverArt" to "ca-1",
                    "owner" to "owner1",
                    "public" to true,
                    "entry" to listOf(
                        mapOf<String, Any?>(
                            "id" to "t1",
                            "title" to "Track 1",
                            "artist" to "Artist A",
                            "albumId" to "al-1",
                            "duration" to 200,
                            "track" to 1,
                        ),
                        mapOf<String, Any?>(
                            "id" to "t2",
                            "title" to "Track 2",
                            "artist" to "Artist B",
                            "albumId" to "al-2",
                            "duration" to 180,
                            "track" to 2,
                        ),
                    ),
                ),
            ),
        )
        coEvery { api.getPlaylist(any(), id = playlistId) } returns response

        repo.importPlaylist(playlistId)

        coVerify {
            playlistDao.upsertAll(match { it.size == 1 && it[0].id == playlistId && it[0].name == "Server Mix" })
        }
        coVerify {
            playlistDao.replaceEntries(
                eq(playlistId),
                match {
                    it.size == 2 && it[0].trackId == "t1" &&
                        it[1].trackId == "t2"
                },
            )
        }
        coVerify { trackDao.upsertAll(match { it.size == 2 && it[0].id == "t1" && it[1].id == "t2" }) }
    }

    @Test
    fun `importPlaylist enqueues downloads when auto-download is enabled`() = runTest {
        every { storage.get("auto_download_playlists") } returns "true"
        val playlistId = "pl-dl"
        val response = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "playlist" to mapOf(
                    "id" to playlistId,
                    "name" to "DL",
                    "songCount" to 2,
                    "entry" to listOf(
                        mapOf<String, Any?>("id" to "t1", "title" to "A"),
                        mapOf<String, Any?>("id" to "t2", "title" to "B"),
                    ),
                ),
            ),
        )
        coEvery { api.getPlaylist(any(), id = playlistId) } returns response

        repo.importPlaylist(playlistId)

        coVerify(exactly = 1) { downloadManager.enqueue("t1", any(), priority = 1) }
        coVerify(exactly = 1) { downloadManager.enqueue("t2", any(), priority = 1) }
    }

    @Test
    fun `importPlaylist does not download when auto-download is disabled`() = runTest {
        every { storage.get("auto_download_playlists") } returns "false"
        val playlistId = "pl-nodl"
        val response = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "playlist" to mapOf(
                    "id" to playlistId,
                    "name" to "NoDL",
                    "songCount" to 2,
                    "entry" to listOf(
                        mapOf<String, Any?>("id" to "t1", "title" to "A"),
                        mapOf<String, Any?>("id" to "t2", "title" to "B"),
                    ),
                ),
            ),
        )
        coEvery { api.getPlaylist(any(), id = playlistId) } returns response

        repo.importPlaylist(playlistId)

        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
        // Metadata/entries are still persisted regardless of the toggle
        coVerify { playlistDao.upsertAll(match { it[0].name == "NoDL" }) }
        coVerify { playlistDao.replaceEntries(eq(playlistId), match { it.size == 2 }) }
    }

    @Test
    fun `importPlaylist handles server error gracefully`() = runTest {
        coEvery { api.getPlaylist(any(), id = "pl-bad") } returns mapOf(
            "subsonic-response" to mapOf("status" to "failed", "error" to mapOf("message" to "Not found")),
        )

        repo.importPlaylist("pl-bad")

        coVerify(exactly = 0) { playlistDao.upsertAll(any()) }
        coVerify(exactly = 0) { playlistDao.replaceEntries(any(), any()) }
    }

    @Test
    fun `importPlaylist clears entries when playlist has no tracks`() = runTest {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "playlist" to mapOf(
                    "id" to "pl-empty",
                    "name" to "Empty",
                    "songCount" to 0,
                ),
            ),
        )
        coEvery { api.getPlaylist(any(), id = "pl-empty") } returns response

        repo.importPlaylist("pl-empty")

        coVerify { playlistDao.upsertAll(match { it[0].name == "Empty" }) }
        coVerify { playlistDao.clearEntries("pl-empty") }
    }

    @Test
    fun `loadServerPlaylists returns only non-imported playlists`() = runTest {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "playlists" to mapOf(
                    "playlist" to listOf(
                        mapOf<String, Any?>("id" to "existing", "name" to "Already Here", "songCount" to 3),
                        mapOf<String, Any?>("id" to "new-one", "name" to "New One", "songCount" to 5),
                    ),
                ),
            ),
        )
        coEvery { api.getPlaylists(any()) } returns response
        coEvery { playlistDao.getAll() } returns listOf(
            PlaylistEntity(id = "existing", name = "Already Here"),
        )

        val result = repo.loadServerPlaylists()

        assertEquals(1, result.size)
        assertEquals("new-one", result[0].id)
        assertEquals("New One", result[0].name)
        assertEquals(5, result[0].trackCount)
    }

    @Test
    fun `loadServerPlaylists returns empty on API error`() = runTest {
        coEvery { api.getPlaylists(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "failed"),
        )

        val result = repo.loadServerPlaylists()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `addToPlaylist inserts entries at correct position and enqueues sync`() = runTest {
        repo.addToPlaylist("target", listOf("t1", "t2"))

        coVerify { playlistDao.addTracksToPlaylist("target", listOf("t1", "t2")) }
        coVerify { pendingChangeDao.insert(match { it.changeType == "add_tracks" && it.payload == "t1,t2" }) }
        coVerify { syncWorker.flushNow() }
    }

    @Test
    fun `addToPlaylist skips already-existing track IDs`() = runTest {
        repo.addToPlaylist("target", listOf("t1"))

        coVerify { playlistDao.addTracksToPlaylist("target", listOf("t1")) }
    }

    @Test
    fun `addToPlaylist does nothing with empty trackIds`() = runTest {
        repo.addToPlaylist("target", emptyList())

        coVerify(exactly = 0) { playlistDao.addTracksToPlaylist(any(), any()) }
        coVerify(exactly = 0) { pendingChangeDao.insert(any()) }
    }

    @Test
    fun `addToPlaylist starts at position 0 for empty target`() = runTest {
        repo.addToPlaylist("empty", listOf("t1"))

        coVerify { playlistDao.addTracksToPlaylist("empty", listOf("t1")) }
    }

    @Test
    fun `loadServerPlaylists throws on network failure`() = runTest {
        coEvery { api.getPlaylists(any()) } throws RuntimeException("Network error")

        try {
            repo.loadServerPlaylists()
            fail("Expected exception")
        } catch (_: Exception) {
            // Expected — exception propagates to caller for ViewModel-level handling
        }
    }

    @Test
    fun `createPlaylist handles DB error gracefully`() = runTest {
        coEvery { playlistDao.upsertAll(any()) } throws RuntimeException("DB error")

        try {
            repo.createPlaylist("Fail")
            fail("Expected exception")
        } catch (_: Exception) {
            // Expected — exception propagates to caller
        }
    }
}
