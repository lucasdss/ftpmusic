package com.lucasdss.ftpmusic.app.ui.playlist

import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.db.CacheQueueDao
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorker
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaylistDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val api: SubsonicApi = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val pendingChangeDao: PendingPlaylistChangeDao = mockk(relaxed = true)
    private val syncWorker: PlaylistSyncWorker = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val playbackManager: PlaybackManager = mockk(relaxed = true)
    private val cacheService: CacheService = mockk(relaxed = true)
    private val downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager = mockk(relaxed = true)
    private val cacheQueueDao: CacheQueueDao = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)

    private val viewModel = PlaylistDetailViewModel(
        api, playlistDao, pendingChangeDao, syncWorker, trackDao, playbackManager,
        cacheService, downloadManager, cacheQueueDao, storage,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        every { storage.get("auto_download_playlists") } returns "true"
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPlaylist populates state from server response`() = runTest {
        val playlistMap = mapOf<String, Any?>(
            "id" to "pl-1",
            "name" to "My Playlist",
            "songCount" to 2,
            "coverArt" to "ca-123",
            "entry" to listOf(
                mapOf<String, Any?>("id" to "t1", "title" to "Track 1", "artist" to "Artist A", "duration" to 180),
                mapOf<String, Any?>("id" to "t2", "title" to "Track 2", "artist" to "Artist B", "duration" to 210),
            ),
        )
        val response = mapOf<String, Any>("subsonic-response" to mapOf("playlist" to playlistMap))
        coEvery { api.getPlaylist(any(), id = "pl-1") } returns response
        coEvery { playlistDao.getById("pl-1") } returns null

        viewModel.loadPlaylist("pl-1")
        advanceUntilIdle()

        val state = viewModel.state.first { !it.isLoading && it.playlist != null }
        assertEquals("My Playlist", state.playlist?.name)
        assertEquals(2, state.tracks.size)
        assertEquals("Track 1", state.tracks[0].title)
        assertEquals("Track 2", state.tracks[1].title)

        // Verify persistence was triggered
        coVerify { playlistDao.upsertAll(any()) }
        coVerify { playlistDao.clearEntries("pl-1") }
        coVerify { playlistDao.upsertEntries(any()) }
    }

    @Test
    fun `loadPlaylist uses cached playlist metadata when present`() = runTest {
        val cached = PlaylistEntity(
            id = "pl-1",
            name = "Cached Playlist",
            trackCount = 3,
            coverArt = "ca-456",
        )
        coEvery { playlistDao.getById("pl-1") } returns cached
        val playlistMap = mapOf<String, Any?>(
            "id" to "pl-1",
            "name" to "Server Playlist",
            "songCount" to 3,
            "entry" to listOf(
                mapOf<String, Any?>("id" to "t1", "title" to "Track 1", "duration" to 180),
            ),
        )
        coEvery { api.getPlaylist(any(), id = "pl-1") } returns
            mapOf("subsonic-response" to mapOf("playlist" to playlistMap))

        viewModel.loadPlaylist("pl-1")
        advanceUntilIdle()

        // Should show cached name first, but server data arrives after
        val state = viewModel.state.first { !it.isLoading && it.playlist != null }
        assertTrue(state.playlist?.id == "pl-1")
    }

    @Test
    fun `server metadata overrides cached playlist fields`() = runTest {
        val cached = PlaylistEntity(
            id = "pl-1",
            name = "Stale Name",
            trackCount = 0,
            coverArt = "stale-cover",
        )
        coEvery { playlistDao.getById("pl-1") } returns cached
        val playlistMap = mapOf<String, Any?>(
            "id" to "pl-1",
            "name" to "Fresh Name",
            "songCount" to 42,
            "coverArt" to "fresh-cover",
            "entry" to emptyList<Map<String, Any?>>(),
        )
        coEvery { api.getPlaylist(any(), id = "pl-1") } returns
            mapOf("subsonic-response" to mapOf("playlist" to playlistMap))

        viewModel.loadPlaylist("pl-1")
        advanceUntilIdle()

        val state = viewModel.state.first { !it.isLoading && it.playlist != null }
        assertEquals("Fresh Name", state.playlist?.name)
        assertEquals(42, state.playlist?.trackCount)
        assertEquals("fresh-cover", state.playlist?.coverArt)
    }

    @Test
    fun `loadPlaylist loads tracks from DB entries without API call when cached`() = runTest {
        val cachedPlaylist = PlaylistEntity(id = "pl-db", name = "DB Playlist", trackCount = 2)
        coEvery { playlistDao.getById("pl-db") } returns cachedPlaylist
        coEvery { playlistDao.getEntries("pl-db") } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity(
                id = 1,
                playlistId = "pl-db",
                trackId = "t-a",
                position = 0,
            ),
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity(
                id = 2,
                playlistId = "pl-db",
                trackId = "t-b",
                position = 1,
            ),
        )
        coEvery { trackDao.getTracksByIds(listOf("t-a", "t-b")) } returns listOf(
            TrackEntity(
                id = "t-a",
                title = "First DB Track",
                artist = "Artist X",
                durationSeconds = 200,
                albumId = "al-1",
            ),
            TrackEntity(
                id = "t-b",
                title = "Second DB Track",
                artist = "Artist Y",
                durationSeconds = 180,
                albumId = "al-2",
            ),
        )
        // API not mocked — should not be called if DB has data

        viewModel.loadPlaylist("pl-db")
        advanceUntilIdle()

        val state = viewModel.state.first { !it.isLoading && it.tracks.isNotEmpty() }
        assertEquals("DB Playlist", state.playlist?.name)
        assertEquals(2, state.tracks.size)
        assertEquals("First DB Track", state.tracks[0].title)
        assertEquals("t-a", state.tracks[0].id)
        assertEquals("Second DB Track", state.tracks[1].title)
        assertEquals("t-b", state.tracks[1].id)
    }

    @Test
    fun `loadPlaylist preserves DB entry position order in tracks`() = runTest {
        // DB returns entries sorted by position ASC
        coEvery { playlistDao.getById("pl-order") } returns PlaylistEntity(id = "pl-order", name = "Ordered")
        coEvery { playlistDao.getEntries("pl-order") } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity(
                id = 1,
                playlistId = "pl-order",
                trackId = "t-0",
                position = 0,
            ),
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity(
                id = 2,
                playlistId = "pl-order",
                trackId = "t-1",
                position = 1,
            ),
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity(
                id = 3,
                playlistId = "pl-order",
                trackId = "t-2",
                position = 2,
            ),
        )
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "t-0", title = "Zero", durationSeconds = 100),
            TrackEntity(id = "t-1", title = "One", durationSeconds = 110),
            TrackEntity(id = "t-2", title = "Two", durationSeconds = 120),
        )
        coEvery { api.getPlaylist(any(), id = "pl-order") } throws RuntimeException("no API")

        viewModel.loadPlaylist("pl-order")
        advanceUntilIdle()

        val state = viewModel.state.first { !it.isLoading }
        assertEquals(3, state.tracks.size)
        // Entries ordered by position: 0, 1, 2
        assertEquals("t-0", state.tracks[0].id)
        assertEquals("t-1", state.tracks[1].id)
        assertEquals("t-2", state.tracks[2].id)
    }

    @Test
    fun `syncFromServer guards against double invocation`() = runTest {
        setupTracks(listOf(Track("t1", "First", duration = 200)))
        val response = mapOf(
            "subsonic-response" to mapOf(
                "playlist" to mapOf(
                    "id" to "pl-1",
                    "name" to "Test",
                    "songCount" to 1,
                    "entry" to listOf(mapOf<String, Any?>("id" to "t1", "title" to "First", "duration" to 200)),
                ),
            ),
        )
        // Simulate real network delay so guard can catch double-invocation
        coEvery { api.getPlaylist(any(), id = "pl-1") } coAnswers {
            delay(100)
            response
        }
        coEvery { playlistDao.clearEntries(any()) } returns Unit
        coEvery { playlistDao.upsertEntries(any()) } returns Unit
        coEvery { playlistDao.upsertAll(any()) } returns Unit
        coEvery { playlistDao.getById(any()) } returns null
        coEvery { trackDao.upsert(any()) } returns Unit

        viewModel.syncFromServer()
        viewModel.syncFromServer()
        advanceUntilIdle()

        coVerify(exactly = 1) { api.getPlaylist(any(), id = "pl-1") }
    }

    @Test
    fun `loadPlaylist falls back to cached entries on server error`() = runTest {
        val cached = PlaylistEntity(id = "pl-1", name = "Cached", trackCount = 1)
        coEvery { playlistDao.getById("pl-1") } returns cached
        coEvery { api.getPlaylist(any(), id = "pl-1") } throws RuntimeException("network error")
        coEvery { playlistDao.getEntries("pl-1") } returns emptyList()

        viewModel.loadPlaylist("pl-1")
        advanceUntilIdle()

        val state = viewModel.state.first { !it.isLoading }
        assertEquals("Cached", state.playlist?.name)
        assertTrue(state.tracks.isEmpty())
    }

    @Test
    fun `loadPlaylist shows empty when no cache and server fails in background`() = runTest {
        coEvery { playlistDao.getById("pl-1") } returns null
        coEvery { playlistDao.getEntries("pl-1") } returns emptyList()
        coEvery { api.getPlaylist(any(), id = "pl-1") } throws RuntimeException("network error")

        viewModel.loadPlaylist("pl-1")
        advanceUntilIdle()

        val state = viewModel.state.first { !it.isLoading }
        assertNull(state.error)
        assertNull(state.playlist)
        assertTrue(state.tracks.isEmpty())
    }

    @Test
    fun `playAll calls PlaybackManager playAlbum with source`() = runTest {
        setupTracks(
            listOf(
                Track("t1", "First", duration = 200),
                Track("t2", "Second", duration = 300),
            ),
        )

        viewModel.playAll()
        advanceUntilIdle()

        verify {
            playbackManager.tryStartContext(
                match { it.size == 2 },
                match { it.size == 2 },
                0,
                "playlist",
                "pl-1",
                "Test Playlist",
            )
        }
    }

    @Test
    fun `shuffle calls PlaybackManager shuffleAlbum with source`() = runTest {
        setupTracks(
            listOf(
                Track("t1", "First", duration = 200),
                Track("t2", "Second", duration = 300),
            ),
        )

        viewModel.shuffle()
        advanceUntilIdle()

        verify {
            playbackManager.tryShuffleContext(
                match { it.size == 2 },
                match { it.size == 2 },
                "playlist",
                "pl-1",
                "Test Playlist",
            )
        }
    }

    @Test
    fun `playTrack passes correct startIndex and preserves album order`() = runTest {
        setupTracks(
            listOf(
                Track("t1", "First", duration = 200),
                Track("t2", "Second", duration = 300),
                Track("t3", "Third", duration = 400),
            ),
        )

        viewModel.playTrack(2)
        advanceUntilIdle()

        verify {
            playbackManager.playAlbum(
                match { it[0].id == "t1" && it[1].id == "t2" && it[2].id == "t3" },
                match { it.size == 3 },
                eq(2),
                eq(false),
            )
        }
    }

    @Test
    fun `playTrack does nothing for out-of-bounds index`() = runTest {
        setupTracks(listOf(Track("t1", "Only", duration = 100)))

        viewModel.playTrack(5)
        advanceUntilIdle()

        verify(exactly = 0) { playbackManager.playAlbum(any(), any()) }
    }

    @Test
    fun `syncFromServer calls API and reloads playlist`() = runTest {
        setupTracks(listOf(Track("t1", "First", duration = 200)))
        val response = mapOf(
            "subsonic-response" to mapOf(
                "playlist" to mapOf(
                    "id" to "pl-1",
                    "name" to "Test",
                    "songCount" to 1,
                    "entry" to listOf(mapOf<String, Any?>("id" to "t1", "title" to "First", "duration" to 200)),
                ),
            ),
        )
        coEvery { api.getPlaylist(any(), id = "pl-1") } returns response

        viewModel.syncFromServer()
        advanceUntilIdle()

        // API called exactly once (no redundant reload)
        coVerify(exactly = 1) { api.getPlaylist(any(), id = "pl-1") }
    }

    @Test
    fun `downloadAll enqueues all tracks`() = runTest {
        setupTracks(
            listOf(
                Track("t1", "First", duration = 200),
                Track("t2", "Second", duration = 300),
            ),
        )
        coEvery { cacheService.promoteToDownload(any()) } returns false

        viewModel.downloadAll()
        advanceUntilIdle()

        coVerify { downloadManager.enqueue("t1", any(), priority = 1) }
        coVerify { downloadManager.enqueue("t2", any(), priority = 1) }
    }

    @Test
    fun `buildCoverArtUrl returns valid URL`() {
        val url = viewModel.buildCoverArtUrl("ca-123")
        assertTrue(url.contains("getCoverArt"))
        assertTrue(url.contains("id=ca-123"))
    }

    @Test
    fun `buildStreamUrl returns valid URL`() {
        val url = viewModel.buildStreamUrl("tr-1")
        assertTrue(url.contains("stream"))
        assertTrue(url.contains("id=tr-1"))
    }

    // ── Playlist CRUD tests ────────────────────────────────────────────────

    @Test
    fun `renamePlaylist persists locally and enqueues sync`() = runTest {
        setupTracks(emptyList())

        viewModel.renamePlaylist("New Name")
        advanceUntilIdle()

        coVerify { playlistDao.upsertAll(match { it.size == 1 && it[0].name == "New Name" }) }
        coVerify { pendingChangeDao.insert(match { it.changeType == "rename" && it.payload == "name=New Name" }) }
        coVerify { syncWorker.flushNow() }
    }

    @Test
    fun `renamePlaylist ignores blank names`() = runTest {
        setupTracks(emptyList())

        viewModel.renamePlaylist("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { pendingChangeDao.insert(any()) }
    }

    @Test
    fun `deletePlaylist removes from DB and enqueues sync`() = runTest {
        setupTracks(emptyList())

        viewModel.deletePlaylist()
        advanceUntilIdle()

        coVerify { playlistDao.clearEntries("pl-1") }
        coVerify { playlistDao.delete("pl-1") }
        coVerify { pendingChangeDao.insert(match { it.changeType == "delete" }) }
        coVerify { syncWorker.flushNow() }
    }

    @Test
    fun `deletePlaylist invokes onComplete after local delete`() = runTest {
        setupTracks(emptyList())
        var completed = false

        viewModel.deletePlaylist { completed = true }
        advanceUntilIdle()

        assertTrue(completed)
        coVerify { playlistDao.delete("pl-1") }
    }

    @Test
    fun `copyPlaylist creates copy with (copy) suffix`() = runTest {
        setupTracks(
            listOf(
                Track("t1", "First", duration = 200),
                Track("t2", "Second", duration = 300),
            ),
        )
        val createResponse = mapOf("subsonic-response" to mapOf("playlist" to mapOf("id" to "pl-copy")))
        coEvery { api.createPlaylist(any(), name = "Test Playlist (copy)", songIds = "t1,t2") } returns createResponse

        viewModel.copyPlaylist()
        advanceUntilIdle()

        coVerify { api.createPlaylist(any(), name = "Test Playlist (copy)", songIds = "t1,t2") }
        coVerify { playlistDao.upsertAll(match { it.size == 1 && it[0].id == "pl-copy" }) }
    }

    @Test
    fun `copyPlaylist does nothing when tracks empty`() = runTest {
        setupTracks(emptyList())

        viewModel.copyPlaylist()
        advanceUntilIdle()

        coVerify(exactly = 0) { api.createPlaylist(any(), name = any()) }
    }

    @Test
    fun `addToQueue adds tracks via PlaybackManager`() = runTest {
        setupTracks(
            listOf(
                Track("t1", "First", duration = 200),
                Track("t2", "Second", duration = 300),
            ),
        )

        viewModel.addToQueue()
        advanceUntilIdle()

        coVerify { playbackManager.addToQueue(match { it.id == "t1" }, any()) }
        coVerify { playbackManager.addToQueue(match { it.id == "t2" }, any()) }
    }

    @Test
    fun `playNext calls PlaybackManager playNext with correct track`() = runTest {
        setupTracks(
            listOf(
                Track("t-first", "First", duration = 200),
                Track("t-next", "Next Up", duration = 300),
            ),
        )

        viewModel.playNext(1)
        advanceUntilIdle()

        verify { playbackManager.playNext(match { it.id == "t-next" }, match { it.contains("id=t-next") }) }
    }

    @Test
    fun `removeLocally deletes playlist from local DB`() = runTest {
        setupTracks(emptyList())

        viewModel.removeLocally()
        advanceUntilIdle()

        coVerify { playlistDao.clearEntries("pl-1") }
        coVerify { playlistDao.delete("pl-1") }
    }

    @Test
    fun `syncToServer clears conflict state immediately`() = runTest {
        // Pre-set conflicted state
        val field = PlaylistDetailViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<PlaylistDetailState>
        stateFlow.value = PlaylistDetailState(
            playlist = PlaylistEntity(
                id = "pl-1",
                name = "Test Playlist",
                isConflicted = true,
                conflictMessage = "Server rejected",
            ),
            tracks = listOf(Track("t1", "First", duration = 200)),
            isConflicted = true,
            conflictMessage = "Server rejected",
        )

        viewModel.syncToServer()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isConflicted)
        assertNull(state.conflictMessage)
    }

    @Test
    fun `syncToServer enqueues track sync`() = runTest {
        setupTracks(listOf(Track("t1", "First", duration = 200)))

        viewModel.syncToServer()
        advanceUntilIdle()

        coVerify { playlistDao.upsertAll(match { it.size == 1 && it[0].trackCount == 1 }) }
        coVerify { pendingChangeDao.insert(match { it.changeType == "sync_tracks" }) }
        coVerify { syncWorker.flushNow() }
    }

    @Test
    fun `syncToServer handles multiple tracks`() = runTest {
        setupTracks(listOf(Track("t1", "First", duration = 200), Track("t2", "Second", duration = 300)))

        viewModel.syncToServer()
        advanceUntilIdle()

        coVerify { pendingChangeDao.insert(match { it.changeType == "sync_tracks" && it.payload == "t1,t2" }) }
    }

    @Test
    fun `syncToServer guards against double invocation`() = runTest {
        setupTracks(listOf(Track("t1", "First", duration = 200)))
        // Add delay so guard can catch the second invocation
        coEvery { playlistDao.upsertAll(any()) } coAnswers {
            delay(50)
            Unit
        }

        viewModel.syncToServer()
        viewModel.syncToServer()
        advanceUntilIdle()

        coVerify(exactly = 1) { pendingChangeDao.insert(any()) }
    }

    @Test
    fun `refreshFromServer clears isConflicted after successful refresh`() = runTest {
        val cached = PlaylistEntity(
            id = "pl-1",
            name = "Conflicted",
            trackCount = 1,
            isConflicted = true,
            conflictMessage = "Server rejected rename: conflict",
        )
        coEvery { playlistDao.getById("pl-1") } returns cached
        coEvery { playlistDao.getEntries("pl-1") } returns emptyList()
        val playlistMap = mapOf<String, Any?>(
            "id" to "pl-1",
            "name" to "Fresh",
            "songCount" to 1,
            "entry" to listOf(mapOf<String, Any?>("id" to "t1", "title" to "Track", "duration" to 100)),
        )
        coEvery { api.getPlaylist(any(), id = "pl-1") } returns
            mapOf("subsonic-response" to mapOf("playlist" to playlistMap))
        coEvery { playlistDao.upsertAll(any()) } returns Unit
        coEvery { playlistDao.clearEntries(any()) } returns Unit
        coEvery { playlistDao.upsertEntries(any()) } returns Unit
        coEvery { trackDao.upsertAll(any()) } returns Unit

        viewModel.loadPlaylist("pl-1")
        advanceUntilIdle()

        val state = viewModel.state.first { !it.isLoading && it.tracks.isNotEmpty() }
        assertFalse(state.isConflicted)
        assertNull(state.conflictMessage)
    }

    @Test
    fun `copyPlaylist saves TrackEntity to DB`() = runTest {
        setupTracks(
            listOf(
                Track(
                    "t1",
                    "First",
                    duration = 200,
                    artist = "Artist A",
                    albumId = "al-1",
                    suffix = "mp3",
                    contentType = "audio/mpeg",
                ),
                Track("t2", "Second", duration = 300, artist = "Artist B", albumId = "al-2"),
            ),
        )
        val createResponse = mapOf("subsonic-response" to mapOf("playlist" to mapOf("id" to "pl-copy")))
        coEvery { api.createPlaylist(any(), name = "Test Playlist (copy)", songIds = "t1,t2") } returns createResponse
        coEvery { playlistDao.upsertAll(any()) } returns Unit
        coEvery { playlistDao.clearEntries(any()) } returns Unit
        coEvery { playlistDao.upsertEntries(any()) } returns Unit
        coEvery { trackDao.upsertAll(any()) } returns Unit

        viewModel.copyPlaylist()
        advanceUntilIdle()

        coVerify { trackDao.upsertAll(match { it.size == 2 && it[0].id == "t1" && it[1].id == "t2" }) }
    }

    @Test
    fun `refreshCacheStatus caps watchers at MAX_WATCHER_TRACKS limit`() = runTest {
        val manyTracks = (1..250).map { i ->
            Track("t$i", "Track $i", duration = 200)
        }
        setupTracks(manyTracks)
        val tracksMap = manyTracks.associate {
            it.id to
                mapOf<String, Any?>("id" to it.id, "title" to it.title, "duration" to 200)
        }
        val response = mapOf(
            "subsonic-response" to mapOf(
                "playlist" to mapOf(
                    "id" to "pl-1",
                    "name" to "Test",
                    "songCount" to 250,
                    "entry" to tracksMap.values.toList(),
                ),
            ),
        )
        coEvery { api.getPlaylist(any(), id = "pl-1") } returns response
        coEvery { cacheService.watchCacheStatus(any()) } returns emptyFlow()
        coEvery { cacheService.getTrackEntity(any()) } returns null
        coEvery { cacheQueueDao.watchByTrackId(any()) } returns emptyFlow()
        coEvery { playlistDao.clearEntries(any()) } returns Unit
        coEvery { playlistDao.upsertEntries(any()) } returns Unit
        coEvery { playlistDao.upsertAll(any()) } returns Unit
        coEvery { playlistDao.getById(any()) } returns null
        coEvery { trackDao.upsertAll(any()) } returns Unit

        viewModel.syncFromServer()
        advanceUntilIdle()

        // MAX_WATCHER_TRACKS = 200, so at most 200 calls per watcher type
        coVerify(atMost = 200) { cacheService.watchCacheStatus(any()) }
        coVerify(atMost = 200) { cacheQueueDao.watchByTrackId(any()) }
    }

    @Test
    fun `removeFromPlaylist removes entry updates state and enqueues sync`() = runTest {
        setupTracks(
            listOf(
                Track("t1", "First", duration = 200),
                Track("t2", "Second", duration = 300),
                Track("t3", "Third", duration = 100),
            ),
        )
        coEvery { playlistDao.removeEntry(any(), any()) } returns Unit
        coEvery { playlistDao.upsertAll(any()) } returns Unit

        // Remove track at index 1 (t2) — sync payload should be "1"
        viewModel.removeFromPlaylist("t2")
        advanceUntilIdle()

        coVerify { playlistDao.removeEntry("pl-1", "t2") }
        coVerify { playlistDao.upsertAll(match { it.size == 1 && it[0].trackCount == 2 }) }
        coVerify { pendingChangeDao.insert(match { it.changeType == "remove_tracks" && it.payload == "1" }) }
        coVerify { syncWorker.flushNow() }
        assertEquals(2, viewModel.state.value.tracks.size)
        assertEquals("t1", viewModel.state.value.tracks[0].id)
        assertEquals("t3", viewModel.state.value.tracks[1].id)
    }

    @Test
    fun `addToPlaylist adds entries to target playlist and syncs`() = runTest {
        setupTracks(listOf(Track("t1", "First", duration = 200)))
        coEvery { playlistDao.getEntries("target-pl") } returns listOf(
            PlaylistEntryEntity(id = 1, playlistId = "target-pl", trackId = "existing", position = 0),
        )
        coEvery { playlistDao.getById("target-pl") } returns PlaylistEntity(
            id = "target-pl",
            name = "Target",
            trackCount = 1,
        )
        coEvery { playlistDao.upsertEntries(any()) } returns Unit
        coEvery { playlistDao.upsertAll(any()) } returns Unit

        viewModel.addToPlaylist("target-pl", listOf("t1"))
        advanceUntilIdle()

        // Entry added at position 1 (nextPosition = 1 after existing at 0)
        coVerify { playlistDao.upsertEntries(match { it.size == 1 && it[0].trackId == "t1" && it[0].position == 1 }) }
        coVerify { playlistDao.upsertAll(match { it.size == 1 && it[0].trackCount == 2 }) }
        coVerify { pendingChangeDao.insert(match { it.changeType == "add_tracks" && it.payload == "t1" }) }
        coVerify { syncWorker.flushNow() }
        coVerify { downloadManager.enqueue("t1", any(), priority = 1) }
    }

    @Test
    fun `addToPlaylist skips already-existing track IDs`() = runTest {
        setupTracks(listOf(Track("t1", "First", duration = 200)))
        // Target already has t1
        coEvery { playlistDao.getEntries("target-pl") } returns listOf(
            PlaylistEntryEntity(id = 1, playlistId = "target-pl", trackId = "t1", position = 0),
        )

        viewModel.addToPlaylist("target-pl", listOf("t1"))
        advanceUntilIdle()

        // No new entries added — t1 already exists
        coVerify(exactly = 0) { playlistDao.upsertEntries(any()) }
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `addToPlaylist to empty target starts at position 0`() = runTest {
        setupTracks(listOf(Track("t1", "First", duration = 200)))
        coEvery { playlistDao.getEntries("empty-pl") } returns emptyList()
        coEvery { playlistDao.getById("empty-pl") } returns PlaylistEntity(
            id = "empty-pl",
            name = "Empty",
            trackCount = 0,
        )
        coEvery { playlistDao.upsertEntries(any()) } returns Unit
        coEvery { playlistDao.upsertAll(any()) } returns Unit

        viewModel.addToPlaylist("empty-pl", listOf("t1"))
        advanceUntilIdle()

        // nextPosition = (-1) + 1 = 0
        coVerify { playlistDao.upsertEntries(match { it.size == 1 && it[0].position == 0 }) }
        coVerify { playlistDao.upsertAll(match { it.size == 1 && it[0].trackCount == 1 }) }
        coVerify { downloadManager.enqueue("t1", any(), priority = 1) }
    }

    @Test
    fun `addToPlaylist does nothing with empty trackIds`() = runTest {
        setupTracks(emptyList())

        viewModel.addToPlaylist("target", emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistDao.upsertEntries(any()) }
    }

    @Test
    fun `moveTrack reorders tracks persists new positions and syncs`() = runTest {
        setupTracks(
            listOf(
                Track("t1", "First", duration = 200),
                Track("t2", "Second", duration = 300),
                Track("t3", "Third", duration = 100),
            ),
        )
        coEvery { playlistDao.clearEntries(any()) } returns Unit
        coEvery { playlistDao.upsertEntries(any()) } returns Unit

        // Move t2 (index 1) down → should be at index 2
        viewModel.moveTrack(1, 2)
        advanceUntilIdle()

        // Verify positions: t1=0, t3=1, t2=2
        coVerify {
            playlistDao.upsertEntries(
                match {
                    it.size == 3 && it[0].trackId == "t1" && it[0].position == 0 &&
                        it[1].trackId == "t3" && it[1].position == 1 &&
                        it[2].trackId == "t2" && it[2].position == 2
                },
            )
        }
        coVerify { pendingChangeDao.insert(match { it.changeType == "sync_tracks" }) }
        coVerify { syncWorker.flushNow() }
        assertEquals("t1", viewModel.state.value.tracks[0].id)
        assertEquals("t3", viewModel.state.value.tracks[1].id)
        assertEquals("t2", viewModel.state.value.tracks[2].id)
    }

    @Test
    fun `moveTrack does nothing for same index`() = runTest {
        setupTracks(listOf(Track("t1", "First", duration = 200)))

        viewModel.moveTrack(0, 0)
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistDao.clearEntries(any()) }
    }

    @Test
    fun `moveTrack up swaps track with previous`() = runTest {
        setupTracks(
            listOf(
                Track("t1", "First", duration = 200),
                Track("t2", "Second", duration = 300),
            ),
        )
        coEvery { playlistDao.clearEntries(any()) } returns Unit
        coEvery { playlistDao.upsertEntries(any()) } returns Unit

        // Move t2 (index 1) up → should swap to index 0
        viewModel.moveTrack(1, 0)
        advanceUntilIdle()

        coVerify {
            playlistDao.upsertEntries(
                match {
                    it.size == 2 && it[0].trackId == "t2" && it[0].position == 0 &&
                        it[1].trackId == "t1" && it[1].position == 1
                },
            )
        }
        assertEquals("t2", viewModel.state.value.tracks[0].id)
        assertEquals("t1", viewModel.state.value.tracks[1].id)
    }

    @Test
    fun `moveTrack guards against out of bounds`() = runTest {
        setupTracks(listOf(Track("t1", "First", duration = 200)))

        viewModel.moveTrack(0, 5)
        viewModel.moveTrack(-1, 0)
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistDao.clearEntries(any()) }
    }

    @Test
    fun `loadAvailablePlaylists excludes current playlist`() = runTest {
        setupTracks(listOf(Track("t1", "First", duration = 200)))
        val allPlaylists = listOf(
            PlaylistEntity(id = "pl-1", name = "Current"),
            PlaylistEntity(id = "pl-2", name = "Other 1"),
            PlaylistEntity(id = "pl-3", name = "Other 2"),
        )
        coEvery { playlistDao.getAll() } returns allPlaylists

        viewModel.loadAvailablePlaylists()
        advanceUntilIdle()

        val available = viewModel.availablePlaylists.value
        assertEquals(2, available.size)
        assertEquals("pl-2", available[0].id)
        assertEquals("pl-3", available[1].id)
    }

    // ── Add Songs picker tests ─────────────────────────────────────────────

    @Test
    fun `addTracksToThisPlaylist appends tracks to empty playlist`() = runTest {
        setupTracks(emptyList())
        coEvery { playlistDao.getEntries("pl-1") } returns emptyList()
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "t1", title = "Song One", artist = "A", durationSeconds = 180),
            TrackEntity(id = "t2", title = "Song Two", artist = "B", durationSeconds = 200),
        )
        coEvery { playlistDao.upsertEntries(any()) } returns Unit
        coEvery { playlistDao.upsertAll(any()) } returns Unit
        coEvery { cacheService.watchCacheStatus(any()) } returns emptyFlow()
        coEvery { cacheQueueDao.watchByTrackId(any()) } returns emptyFlow()
        coEvery { cacheService.getTrackEntity(any()) } returns null

        viewModel.addTracksToThisPlaylist(listOf("t1", "t2"))
        advanceUntilIdle()

        // Entries appended at positions 0,1; meta count updated; pending sync + auto-download
        coVerify {
            playlistDao.upsertEntries(
                match {
                    it.size == 2 && it[0].trackId == "t1" && it[0].position == 0 &&
                        it[1].trackId == "t2" && it[1].position == 1
                },
            )
        }
        coVerify { playlistDao.upsertAll(match { it.size == 1 && it[0].trackCount == 2 }) }
        coVerify { pendingChangeDao.insert(match { it.changeType == "add_tracks" && it.payload == "t1,t2" }) }
        coVerify { syncWorker.flushNow() }
        coVerify { downloadManager.enqueue("t1", any(), priority = 1) }
        coVerify { downloadManager.enqueue("t2", any(), priority = 1) }
        // Local state updated immediately
        assertEquals(2, viewModel.state.value.tracks.size)
        assertEquals("Song One", viewModel.state.value.tracks[0].title)
    }

    @Test
    fun `addTracksToThisPlaylist deduplicates existing tracks`() = runTest {
        setupTracks(listOf(Track("t1", "Song One", duration = 180)))
        coEvery { playlistDao.getEntries("pl-1") } returns listOf(
            PlaylistEntryEntity(id = 1, playlistId = "pl-1", trackId = "t1", position = 0),
        )
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "t2", title = "Song Two", artist = "B", durationSeconds = 200),
        )
        coEvery { playlistDao.upsertEntries(any()) } returns Unit
        coEvery { playlistDao.upsertAll(any()) } returns Unit
        coEvery { cacheService.watchCacheStatus(any()) } returns emptyFlow()
        coEvery { cacheQueueDao.watchByTrackId(any()) } returns emptyFlow()
        coEvery { cacheService.getTrackEntity(any()) } returns null

        viewModel.addTracksToThisPlaylist(listOf("t1", "t2"))
        advanceUntilIdle()

        // Only t2 appended at position 1; pending payload + download exclude t1
        coVerify { playlistDao.upsertEntries(match { it.size == 1 && it[0].trackId == "t2" && it[0].position == 1 }) }
        coVerify { playlistDao.upsertAll(match { it.size == 1 && it[0].trackCount == 2 }) }
        coVerify { pendingChangeDao.insert(match { it.changeType == "add_tracks" && it.payload == "t2" }) }
        coVerify(exactly = 0) { downloadManager.enqueue("t1", any(), any()) }
        assertEquals(2, viewModel.state.value.tracks.size)
    }

    @Test
    fun `addTracksToThisPlaylist no-ops on empty input`() = runTest {
        setupTracks(listOf(Track("t1", "Song One", duration = 180)))

        viewModel.addTracksToThisPlaylist(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistDao.upsertEntries(any()) }
        coVerify(exactly = 0) { pendingChangeDao.insert(any()) }
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `addTracksToThisPlaylist no-ops when no playlist loaded`() = runTest {
        val field = PlaylistDetailViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<PlaylistDetailState>
        stateFlow.value = PlaylistDetailState()

        viewModel.addTracksToThisPlaylist(listOf("t1"))
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistDao.upsertEntries(any()) }
        coVerify(exactly = 0) { pendingChangeDao.insert(any()) }
    }

    @Test
    fun `searchPickerTracks delegates to trackDao`() = runTest {
        val expected = listOf(TrackEntity(id = "t1", title = "Abracadabra"))
        coEvery { trackDao.searchAllTracks("abra") } returns expected

        val result = viewModel.searchPickerTracks("abra")

        assertEquals(expected, result)
        coVerify { trackDao.searchAllTracks("abra") }
    }

    @Test
    fun `pickerSuggestions returns recently played tracks`() = runTest {
        val expected = listOf(TrackEntity(id = "t1", title = "Recent"))
        coEvery { trackDao.getRecentlyPlayed(limit = 50) } returns expected

        val result = viewModel.pickerSuggestions()

        assertEquals(expected, result)
        coVerify { trackDao.getRecentlyPlayed(limit = 50) }
    }

    @Test
    fun `addTracksToThisPlaylist no-ops when all tracks already exist`() = runTest {
        setupTracks(listOf(Track("t1", "Song One", duration = 180)))
        coEvery { playlistDao.getEntries("pl-1") } returns listOf(
            PlaylistEntryEntity(id = 1, playlistId = "pl-1", trackId = "t1", position = 0),
        )

        viewModel.addTracksToThisPlaylist(listOf("t1"))
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistDao.upsertEntries(any()) }
        coVerify(exactly = 0) { playlistDao.upsertAll(any()) }
        coVerify(exactly = 0) { pendingChangeDao.insert(any()) }
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `addTracksToThisPlaylist skips auto-download when disabled`() = runTest {
        setupTracks(emptyList())
        every { storage.get("auto_download_playlists") } returns "false"
        coEvery { playlistDao.getEntries("pl-1") } returns emptyList()
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "t1", title = "Song One", artist = "A", durationSeconds = 180),
        )
        coEvery { playlistDao.upsertEntries(any()) } returns Unit
        coEvery { playlistDao.upsertAll(any()) } returns Unit
        coEvery { cacheService.watchCacheStatus(any()) } returns emptyFlow()
        coEvery { cacheQueueDao.watchByTrackId(any()) } returns emptyFlow()
        coEvery { cacheService.getTrackEntity(any()) } returns null

        viewModel.addTracksToThisPlaylist(listOf("t1"))
        advanceUntilIdle()

        coVerify { pendingChangeDao.insert(match { it.changeType == "add_tracks" && it.payload == "t1" }) }
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    private fun setupTracks(tracks: List<Track>) {
        val field = PlaylistDetailViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<PlaylistDetailState>
        stateFlow.value = PlaylistDetailState(
            playlist = PlaylistEntity(id = "pl-1", name = "Test Playlist"),
            tracks = tracks,
        )
    }
}
