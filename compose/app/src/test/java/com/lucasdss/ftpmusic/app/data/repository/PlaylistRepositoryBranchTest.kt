package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorker
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Branch coverage extensions for [PlaylistRepository]: error/edge branches of
 * importPlaylist and loadServerPlaylists (missing response sections, malformed
 * entries, defaulted fallbacks) that the primary test class does not reach.
 */
class PlaylistRepositoryBranchTest {

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

    private fun okResponse(playlist: Map<String, Any?>): Map<String, Any> =
        mapOf("subsonic-response" to mapOf("status" to "ok", "playlist" to playlist))

    @Test
    fun `importPlaylist bails when subsonic-response or playlist is missing`() = runTest {
        coEvery { api.getPlaylist(any(), id = "no-sr") } returns mapOf("other" to 1)
        repo.importPlaylist("no-sr")
        coVerify(exactly = 0) { playlistDao.upsertAll(any()) }

        coEvery { api.getPlaylist(any(), id = "no-pl") } returns
            mapOf("subsonic-response" to mapOf<String, Any?>())
        repo.importPlaylist("no-pl")
        coVerify(exactly = 0) { playlistDao.upsertAll(any()) }

        coEvery { api.getPlaylist(any(), id = "pl-not-map") } returns
            mapOf("subsonic-response" to mapOf("playlist" to "nope"))
        repo.importPlaylist("pl-not-map")
        coVerify(exactly = 0) { playlistDao.upsertAll(any()) }
    }

    @Test
    fun `importPlaylist falls back to defaults for missing fields`() = runTest {
        val response = okResponse(mapOf<String, Any?>("name" to 42))
        coEvery { api.getPlaylist(any(), id = "defaults") } returns response

        repo.importPlaylist("defaults")

        // id falls back to the requested id, name to "Playlist", isPublic/trackCount to defaults
        coVerify {
            playlistDao.upsertAll(
                match {
                    it.size == 1 && it[0].id == "defaults" && it[0].name == "Playlist" &&
                        !it[0].isPublic &&
                        it[0].trackCount == 0
                },
            )
        }
    }

    @Test
    fun `importPlaylist defaults auto-download to true when flag is unset or invalid`() = runTest {
        every { storage.get("auto_download_playlists") } returns null
        val response = okResponse(
            mapOf("id" to "pl-1", "name" to "A", "entry" to listOf(mapOf<String, Any?>("id" to "t1", "title" to "T"))),
        )
        coEvery { api.getPlaylist(any(), id = "pl-1") } returns response
        repo.importPlaylist("pl-1")
        coVerify(exactly = 1) { downloadManager.enqueue("t1", any(), priority = 1) }

        every { storage.get("auto_download_playlists") } returns "garbage"
        repo.importPlaylist("pl-1")
        coVerify(exactly = 2) { downloadManager.enqueue("t1", any(), priority = 1) }
    }

    @Test
    fun `importPlaylist skips malformed entries but keeps valid ones`() = runTest {
        val response = okResponse(
            mapOf(
                "id" to "pl-1",
                "name" to "A",
                "entry" to listOf(
                    "not-a-map",
                    mapOf<String, Any?>("title" to "NoId"),
                    mapOf<String, Any?>("id" to "t1", "title" to "T1"),
                    mapOf<String, Any?>("id" to "t2"),
                ),
            ),
        )
        coEvery { api.getPlaylist(any(), id = "pl-1") } returns response

        repo.importPlaylist("pl-1")

        coVerify { playlistDao.replaceEntries(eq("pl-1"), match { it.map { e -> e.trackId } == listOf("t1", "t2") }) }
        // t2 has no title → title falls back to id
        coVerify {
            trackDao.upsertAll(
                match {
                    it.size == 2 && it[0].id == "t1" && it[0].title == "T1" &&
                        it[1].id == "t2" &&
                        it[1].title == "t2"
                },
            )
        }
    }

    @Test
    fun `loadServerPlaylists bails when response sections are missing`() = runTest {
        coEvery { api.getPlaylists(any()) } returns mapOf("x" to 1)
        assertTrue(repo.loadServerPlaylists().isEmpty())

        coEvery { api.getPlaylists(any()) } returns mapOf("subsonic-response" to mapOf<String, Any?>())
        assertTrue(repo.loadServerPlaylists().isEmpty())

        coEvery { api.getPlaylists(any()) } returns
            mapOf("subsonic-response" to mapOf("playlists" to "nope"))
        assertTrue(repo.loadServerPlaylists().isEmpty())

        coEvery { api.getPlaylists(any()) } returns
            mapOf("subsonic-response" to mapOf("playlists" to mapOf<String, Any?>()))
        assertTrue(repo.loadServerPlaylists().isEmpty())
    }

    @Test
    fun `loadServerPlaylists filters malformed and already-imported entries`() = runTest {
        coEvery { api.getPlaylists(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "playlists" to mapOf(
                    "playlist" to listOf(
                        "junk",
                        mapOf<String, Any?>("name" to "NoId"),
                        mapOf<String, Any?>("id" to "local", "name" to "L", "songCount" to 1),
                        mapOf<String, Any?>("id" to "fresh", "name" to "F", "songCount" to 7),
                    ),
                ),
            ),
        )
        coEvery { playlistDao.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntity(id = "local", name = "L"),
        )

        val result = repo.loadServerPlaylists()

        assertEquals(listOf("fresh"), result.map { it.id })
        assertEquals(7, result[0].trackCount)
    }

    @Test
    fun `authParams tolerates missing stored credentials`() = runTest {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns null
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns null
        coEvery { api.getPlaylists(any()) } returns mapOf(
            "subsonic-response" to mapOf("playlists" to mapOf("playlist" to emptyList<Any>())),
        )
        assertTrue(repo.loadServerPlaylists().isEmpty())
        // auth.buildAuthParams("", "") was invoked with empty strings — no crash
        verify { storage.get(SecureStorage.KEY_USERNAME) }
    }
}
