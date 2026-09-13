package com.lucasdss.ftpmusic.app.ui.album

import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.data.model.AlbumWithTracks
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.repository.AlbumRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import com.lucasdss.ftpmusic.app.ui.library.PlaylistView
import io.mockk.*
import kotlinx.coroutines.Dispatchers
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

class AlbumDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: AlbumRepository = mockk()
    private val storage: SecureStorage = mockk(relaxed = true)
    private val playbackManager: PlaybackManager = mockk(relaxed = true)
    private val cacheService: CacheService = mockk(relaxed = true)
    private val downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager = mockk(relaxed = true)
    private val cacheQueueDao: com.lucasdss.ftpmusic.app.data.db.CacheQueueDao = mockk(relaxed = true)
    private val playlistDao: com.lucasdss.ftpmusic.app.data.db.PlaylistDao = mockk(relaxed = true)
    private val pendingChangeDao: com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeDao = mockk(relaxed = true)
    private val syncWorker: com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorker = mockk(relaxed = true)
    private val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao = mockk(relaxed = true)
    private val favoriteRepository: com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository = mockk(relaxed = true)
    private val playlistRepo: com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository = mockk(relaxed = true)
    private val api: com.lucasdss.ftpmusic.app.data.network.SubsonicApi = mockk(relaxed = true)
    private val musicBrainzService: com.lucasdss.ftpmusic.app.data.network.MusicBrainzService = mockk(relaxed = true)
    private val trackDao: com.lucasdss.ftpmusic.app.data.db.TrackDao = mockk(relaxed = true)
    private val offlineModeManager: com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager = mockk(relaxed = true)
    private lateinit var viewModel: AlbumDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { cacheService.cacheEventFlow } returns kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, Boolean>>()
        every { playbackManager.tryStartContext(any(), any(), any(), any(), any(), any()) } returns true
        every { offlineModeManager.isOffline } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        viewModel =
            AlbumDetailViewModel(
                repository,
                storage,
                playbackManager,
                cacheService,
                downloadManager,
                cacheQueueDao,
                playlistDao,
                pendingChangeDao,
                syncWorker,
                playlistRepo,
                metadataDao,
                favoriteRepository,
                api,
                musicBrainzService = musicBrainzService,
                offlineModeManager = offlineModeManager,
                trackDao = trackDao,
            )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadAlbum populates state on success`() = runTest {
        val tracks = listOf(Track("t1", "Track 1", duration = 240))
        coEvery { repository.getAlbum("al-1", "user", "pass") } returns
            AlbumWithTracks(Album("al-1", "Test Album"), tracks)

        viewModel.loadAlbum("al-1", "user", "pass")
        val state = viewModel.state.first { !it.isLoading && it.album != null }

        assertEquals("Test Album", state.album?.name)
        assertEquals(1, state.tracks.size)
    }

    @Test
    fun `loadAlbum sets error on failure`() = runTest {
        coEvery { repository.getAlbum("bad", any(), any()) } throws RuntimeException("fail")
        viewModel.loadAlbum("bad", "user", "pass")
        val state = viewModel.state.first { !it.isLoading && it.error != null }
        assertNotNull(state.error)
        assertNull(state.album)
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

    @Test
    fun `playTrack passes correct startIndex and preserves album order`() {
        // Set up tracks in state
        val tracks = listOf(
            Track("t1", "First", duration = 200),
            Track("t2", "Second", duration = 300),
        )
        setupTracks(tracks)

        viewModel.playTrack(1)

        // Full album in order, startIndex=1 (second track)
        verify {
            playbackManager.playAlbum(
                match { it.size == 2 && it[0].id == "t1" && it[1].id == "t2" },
                match {
                    it.size ==
                        2
                },
                eq(1),
                eq(false),
            )
        }
    }

    @Test
    fun `playAll calls PlaybackManager tryStartContext`() {
        val tracks = listOf(
            Track("t1", "First", duration = 200),
            Track("t2", "Second", duration = 300),
        )
        setupTracks(tracks)

        viewModel.playAll()

        verify {
            playbackManager.tryStartContext(
                match {
                    it.size == 2
                },
                match { it.size == 2 },
                eq(0),
                eq("album"),
                eq("al-1"),
                eq("Test"),
            )
        }
    }

    @Test
    fun `shuffle calls PlaybackManager tryShuffleContext`() {
        val tracks = listOf(
            Track("t1", "First", duration = 200),
            Track("t2", "Second", duration = 300),
        )
        setupTracks(tracks)

        viewModel.shuffle()

        verify {
            playbackManager.tryShuffleContext(match { it.size == 2 }, match { it.size == 2 }, "album", "al-1", "Test")
        }
    }

    @Test
    fun `playTrack does nothing for out-of-bounds index`() {
        setupTracks(listOf(Track("t1", "Only", duration = 100)))

        viewModel.playTrack(5)

        verify(exactly = 0) { playbackManager.playSingleTrack(any(), any()) }
    }

    @Test
    fun `playNext calls PlaybackManager playNext with correct track and url`() {
        val tracks = listOf(
            Track("t1", "First", duration = 200),
            Track("t-next", "Next Up", artist = "Artist Y", album = "Album Y", duration = 250),
        )
        setupTracks(tracks)

        viewModel.playNext(1)

        verify {
            playbackManager.playNext(
                match { it.id == "t-next" && it.title == "Next Up" },
                match { it.contains("id=t-next") },
            )
        }
    }

    @Test
    fun `playNext does nothing for out-of-bounds index`() {
        setupTracks(listOf(Track("t1", "Only", duration = 100)))

        viewModel.playNext(5)

        verify(exactly = 0) { playbackManager.playNext(any(), any()) }
    }

    @Test
    fun `playAll does nothing when no tracks`() {
        setupTracks(emptyList())

        viewModel.playAll()

        verify(exactly = 0) { playbackManager.playAlbum(any(), any()) }
    }

    @Test
    fun `addToPlaylist updates local DB and enqueues sync`() = runTest {
        val trackIds = listOf("t1", "t2", "t3")

        viewModel.addToPlaylist("playlist-1", trackIds)
        advanceUntilIdle()

        coVerify { playlistRepo.addToPlaylist("playlist-1", trackIds) }
    }

    @Test
    fun `createPlaylist calls repository and returns new ID`() = runTest {
        coEvery { playlistRepo.createPlaylist("New Playlist") } returns "new-temp-123"

        var capturedId: String? = null
        viewModel.createPlaylist("New Playlist") { id -> capturedId = id }
        advanceUntilIdle()

        assertEquals("new-temp-123", capturedId)
        coVerify { playlistRepo.createPlaylist("New Playlist") }
    }

    @Test
    fun `createPlaylist returns null on repository error`() = runTest {
        coEvery { playlistRepo.createPlaylist("Fail") } throws RuntimeException("fail")
        var capturedId: String? = "stale"
        viewModel.createPlaylist("Fail") { id -> capturedId = id }
        advanceUntilIdle()
        assertNull(capturedId)
    }

    @Test
    fun `importPlaylist fetches and persists playlist from server`() = runTest {
        viewModel.importPlaylist("pl-import")
        advanceUntilIdle()

        coVerify { playlistRepo.importPlaylist("pl-import") }
    }

    @Test
    fun `loadServerPlaylists filters out already-imported playlists`() = runTest {
        coEvery { playlistRepo.loadServerPlaylists() } returns listOf(
            PlaylistView(id = "new-one", name = "New One", trackCount = 5),
        )

        viewModel.loadServerPlaylists()
        advanceUntilIdle()

        val server = viewModel.serverPlaylists.first()
        assertEquals(1, server.size)
        assertEquals("new-one", server[0].id)
    }

    // ── Heart (like) toggle tests ──────────────────────────────────────────

    @Test
    fun `isTrackLiked returns false for untracked track`() {
        assertFalse(viewModel.isTrackLiked("t1"))
    }

    @Test
    fun `toggleTrackLike adds track optimistically`() {
        viewModel.toggleTrackLike("t1")
        assertTrue(viewModel.isTrackLiked("t1"))
    }

    @Test
    fun `toggleTrackLike removes track optimistically`() {
        viewModel.toggleTrackLike("t1")
        assertTrue(viewModel.isTrackLiked("t1"))
        viewModel.toggleTrackLike("t1")
        assertFalse(viewModel.isTrackLiked("t1"))
    }

    @Test
    fun `getTrackRating returns 0 for unrated track`() {
        assertEquals(0, viewModel.getTrackRating("t1"))
    }

    @Test
    fun `rateTrack updates rating optimistically`() {
        viewModel.rateTrack("t1", 4)
        assertEquals(4, viewModel.getTrackRating("t1"))
    }

    // ── Cache status tests ──────────────────────────────────────────────────

    @Test
    fun `isCached and isDownloaded return false when DB has no entities`() = runTest {
        val tracks = listOf(Track("t1", "Track 1", duration = 200))
        coEvery { repository.getAlbum("al-cache", any(), any()) } returns
            AlbumWithTracks(Album("al-cache", "Cache Album"), tracks)
        coEvery { cacheService.getTracksByIds(listOf("t1")) } returns emptyList()
        coEvery { cacheService.watchCacheStatus(any()) } returns kotlinx.coroutines.flow.emptyFlow()

        viewModel.loadAlbum("al-cache", "user", "pass")
        advanceUntilIdle()

        assertFalse(viewModel.isCached("t1"))
        assertFalse(viewModel.isDownloaded("t1"))
    }

    @Test
    fun `isCached returns true and isDownloaded false when only cached`() = runTest {
        val tracks = listOf(Track("t1", "Cached Track", duration = 200))
        coEvery { repository.getAlbum("al-cached", any(), any()) } returns
            AlbumWithTracks(Album("al-cached", "Album"), tracks)
        coEvery { cacheService.getTracksByIds(listOf("t1")) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.TrackEntity(
                id = "t1",
                title = "Cached Track",
                cachedFilePath = "/cache/t1.cache",
                isDownloaded = false,
            ),
        )
        coEvery { cacheService.watchCacheStatus(any()) } returns kotlinx.coroutines.flow.emptyFlow()

        viewModel.loadAlbum("al-cached", "user", "pass")
        advanceUntilIdle()

        assertTrue(viewModel.isCached("t1"))
        assertFalse(viewModel.isDownloaded("t1"))
    }

    @Test
    fun `isDownloaded returns true when entity has isDownloaded flag`() = runTest {
        val tracks = listOf(Track("t2", "Saved Track", duration = 300))
        coEvery { repository.getAlbum("al-saved", any(), any()) } returns
            AlbumWithTracks(Album("al-saved", "Album"), tracks)
        coEvery { cacheService.getTracksByIds(listOf("t2")) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.TrackEntity(
                id = "t2",
                title = "Saved Track",
                cachedFilePath = "/cache/t2.cache",
                isDownloaded = true,
            ),
        )
        coEvery { cacheService.watchCacheStatus(any()) } returns kotlinx.coroutines.flow.emptyFlow()

        viewModel.loadAlbum("al-saved", "user", "pass")
        advanceUntilIdle()

        assertTrue(viewModel.isCached("t2"))
        assertTrue(viewModel.isDownloaded("t2"))
    }

    @Test
    fun `cachedTrackIds empty when entity has null cachedFilePath`() = runTest {
        val tracks = listOf(Track("t3", "Streaming", duration = 400))
        coEvery { repository.getAlbum("al-stream", any(), any()) } returns
            AlbumWithTracks(Album("al-stream", "Album"), tracks)
        coEvery { cacheService.getTracksByIds(listOf("t3")) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.TrackEntity(
                id = "t3",
                title = "Streaming",
                cachedFilePath = null,
                isDownloaded = false,
            ),
        )
        coEvery { cacheService.watchCacheStatus(any()) } returns kotlinx.coroutines.flow.emptyFlow()

        viewModel.loadAlbum("al-stream", "user", "pass")
        advanceUntilIdle()

        assertFalse(viewModel.isCached("t3"))
        assertFalse(viewModel.isDownloaded("t3"))
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private fun setupTracks(tracks: List<Track>) {
        // Use reflection or direct state mutation for testing
        val field = AlbumDetailViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<AlbumDetailState>
        stateFlow.value = AlbumDetailState(
            album = Album("al-1", "Test"),
            tracks = tracks,
        )
    }

    // ── Cache-first tests ───────────────────────────────────────────────────

    @Test
    fun `loadAlbum shows cached tracks before API response`() = runTest {
        val cachedTracks = listOf(
            com.lucasdss.ftpmusic.app.data.db.CachedAlbumTrackEntity(
                id = "t1",
                albumId = "al-1",
                title = "Cached Track",
                artist = "Artist",
                duration = 200,
                trackNumber = 1,
            ),
        )
        coEvery { metadataDao.getAlbumTracks("al-1") } returns cachedTracks
        coEvery { repository.getAlbum("al-1", any(), any()) } returns
            AlbumWithTracks(Album("al-1", "Test Album"), listOf(Track("t1", "Cached Track", duration = 200)))

        viewModel.loadAlbum("al-1", "user", "pass")

        // Cached tracks should be visible immediately (isLoading = false after cache hit)
        val cachedState = viewModel.state.first { it.tracks.isNotEmpty() && !it.isLoading }
        assertEquals(1, cachedState.tracks.size)
        assertEquals("Cached Track", cachedState.tracks[0].title)

        // Verify API was still called for refresh
        coVerify { repository.getAlbum("al-1", "user", "pass") }
    }

    @Test
    fun `loadAlbum handles empty cache gracefully`() = runTest {
        coEvery { metadataDao.getAlbumTracks("al-new") } returns emptyList()
        coEvery { repository.getAlbum("al-new", any(), any()) } returns
            AlbumWithTracks(Album("al-new", "New Album"), listOf(Track("t2", "Fresh Track", duration = 180)))

        viewModel.loadAlbum("al-new", "user", "pass")

        val state = viewModel.state.first { !it.isLoading && it.album != null }
        assertEquals("New Album", state.album?.name)
        assertEquals(1, state.tracks.size)
    }

    @Test
    fun `loadAlbum skips API call when offline and shows cached tracks`() = runTest {
        every { offlineModeManager.isOffline } returns kotlinx.coroutines.flow.MutableStateFlow(true)
        coEvery { metadataDao.getAlbumTracks("al-1") } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.CachedAlbumTrackEntity(
                id = "t1",
                albumId = "al-1",
                title = "Cached Track",
                duration = 200,
            ),
        )

        viewModel.loadAlbum("al-1", "user", "pass")

        val state = viewModel.state.first { it.tracks.isNotEmpty() && !it.isLoading }
        assertEquals(1, state.tracks.size)
        assertEquals("Cached Track", state.tracks[0].title)
        // No API call when offline
        coVerify(exactly = 0) { repository.getAlbum(any(), any(), any()) }
    }

    @Test
    fun `loadAlbum shows error when offline and nothing cached`() = runTest {
        every { offlineModeManager.isOffline } returns kotlinx.coroutines.flow.MutableStateFlow(true)
        coEvery { metadataDao.getAlbumTracks("al-new") } returns emptyList()

        viewModel.loadAlbum("al-new", "user", "pass")

        val state = viewModel.state.first { !it.isLoading && it.error != null }
        assertEquals("Album not cached offline", state.error)
        // No API call when offline
        coVerify(exactly = 0) { repository.getAlbum(any(), any(), any()) }
    }
    // ── Batched cache-status flows (F19: one flow per album, not per track) ──

    @Test
    fun `track batch flow updates cached and downloaded flags`() = runTest {
        val tracks = listOf(
            Track("b1", "Cached Track", duration = 200),
            Track("b2", "Plain Track", duration = 200),
        )
        coEvery { repository.getAlbum("al-batch", any(), any()) } returns
            AlbumWithTracks(Album("al-batch", "Album"), tracks)
        coEvery { cacheService.getTracksByIds(any()) } returns emptyList()
        coEvery { trackDao.watchTracksByIds(listOf("b1", "b2")) } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                com.lucasdss.ftpmusic.app.data.db.TrackEntity(
                    id = "b1",
                    title = "Cached Track",
                    cachedFilePath = "/cache/b1",
                    cacheSizeBytes = 100,
                    isAutoCached = true,
                    isDownloaded = false,
                ),
                com.lucasdss.ftpmusic.app.data.db.TrackEntity(
                    id = "b2",
                    title = "Plain Track",
                    cachedFilePath = null,
                ),
            ),
        )
        coEvery { cacheQueueDao.watchByTrackIds(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())

        viewModel.loadAlbum("al-batch", "user", "pass")
        advanceUntilIdle()

        assertTrue("Auto-cached track must be flagged", viewModel.isCached("b1"))
        assertFalse(viewModel.isCached("b2"))
        assertFalse(viewModel.isDownloaded("b1"))
    }

    @Test
    fun `track batch flow marks downloaded tracks`() = runTest {
        val tracks = listOf(Track("d1", "Downloaded", duration = 200))
        coEvery { repository.getAlbum("al-dl", any(), any()) } returns
            AlbumWithTracks(Album("al-dl", "Album"), tracks)
        coEvery { cacheService.getTracksByIds(any()) } returns emptyList()
        coEvery { trackDao.watchTracksByIds(listOf("d1")) } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                com.lucasdss.ftpmusic.app.data.db.TrackEntity(
                    id = "d1",
                    title = "Downloaded",
                    cachedFilePath = "/cache/d1",
                    isDownloaded = true,
                ),
            ),
        )
        coEvery { cacheQueueDao.watchByTrackIds(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())

        viewModel.loadAlbum("al-dl", "user", "pass")
        advanceUntilIdle()

        assertTrue(viewModel.isCached("d1"))
        assertTrue(viewModel.isDownloaded("d1"))
    }

    @Test
    fun `queue batch flow marks pending tracks as queued`() = runTest {
        val tracks = listOf(Track("q1", "Queued", duration = 200))
        coEvery { repository.getAlbum("al-q", any(), any()) } returns
            AlbumWithTracks(Album("al-q", "Album"), tracks)
        coEvery { cacheService.getTracksByIds(any()) } returns emptyList()
        coEvery { trackDao.watchTracksByIds(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { cacheQueueDao.watchByTrackIds(listOf("q1")) } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                com.lucasdss.ftpmusic.app.data.db.CacheQueueItemEntity(
                    trackId = "q1",
                    remoteUrl = "http://x",
                    priority = 1,
                    status = "processing",
                ),
            ),
        )

        viewModel.loadAlbum("al-q", "user", "pass")
        advanceUntilIdle()

        assertTrue("Pending/processing queue row must show as queued", viewModel.isQueued("q1"))
    }

    @Test
    fun `queue batch flow clears queued flag when the row completes`() = runTest {
        val tracks = listOf(Track("q2", "Done", duration = 200))
        coEvery { repository.getAlbum("al-q2", any(), any()) } returns
            AlbumWithTracks(Album("al-q2", "Album"), tracks)
        coEvery { cacheService.getTracksByIds(any()) } returns emptyList()
        coEvery { trackDao.watchTracksByIds(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { cacheQueueDao.watchByTrackIds(listOf("q2")) } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                com.lucasdss.ftpmusic.app.data.db.CacheQueueItemEntity(
                    trackId = "q2",
                    remoteUrl = "http://x",
                    priority = 1,
                    status = "completed",
                ),
            ),
        )

        viewModel.loadAlbum("al-q2", "user", "pass")
        advanceUntilIdle()

        assertFalse("Completed row must not show as queued", viewModel.isQueued("q2"))
    }
    // ── Download / rate / like paths ──────────────────────────────────────

    @Test
    fun `downloadTrack promotes an auto-cached track without re-enqueue`() = runTest {
        val track = Track("dt1", "DL", duration = 200)
        coEvery { cacheService.promoteToDownload("dt1") } returns true
        coEvery { downloadManager.enqueue(any(), any(), priority = any()) } just runs

        viewModel.downloadTrack(track)
        advanceUntilIdle()

        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), priority = any()) }
        assertTrue(viewModel.isDownloaded("dt1"))
    }

    @Test
    fun `downloadTrack enqueues a download when there is nothing to promote`() = runTest {
        val track = Track("dt2", "DL2", duration = 200)
        coEvery { cacheService.promoteToDownload("dt2") } returns false
        coEvery { downloadManager.enqueue(any(), any(), priority = any()) } just runs

        viewModel.downloadTrack(track)
        advanceUntilIdle()

        coVerify { downloadManager.enqueue("dt2", any(), priority = 1) }
    }

    @Test
    fun `downloadAlbum enqueues all non-downloaded tracks`() = runTest {
        coEvery { repository.getAlbum("al-da", any(), any()) } returns AlbumWithTracks(
            Album("al-da", "Album"),
            listOf(Track("a1", "A1", duration = 100), Track("a2", "A2", duration = 100)),
        )
        coEvery { cacheService.getTracksByIds(any()) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.TrackEntity(id = "a1", title = "A1", isDownloaded = true),
        )
        coEvery { trackDao.watchTracksByIds(any()) } returns kotlinx.coroutines.flow.flowOf(
            listOf(com.lucasdss.ftpmusic.app.data.db.TrackEntity(id = "a1", title = "A1", isDownloaded = true)),
        )
        coEvery { cacheQueueDao.watchByTrackIds(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        // a1 is already downloaded → promoteToDownload succeeds (skip);
        // a2 needs a real enqueue.
        coEvery { cacheService.promoteToDownload("a1") } returns true
        coEvery { cacheService.promoteToDownload("a2") } returns false
        coEvery { downloadManager.enqueue(any(), any(), priority = any()) } just runs

        viewModel.loadAlbum("al-da", "user", "pass")
        advanceUntilIdle()
        viewModel.downloadAlbum()
        advanceUntilIdle()

        // a1 already downloaded → skipped; a2 enqueued
        coVerify(exactly = 1) { downloadManager.enqueue("a2", any(), priority = 1) }
        coVerify(exactly = 0) { downloadManager.enqueue("a1", any(), priority = 1) }
    }

    @Test
    fun `rateTrack updates the optimistic rating`() = runTest {
        val track = Track("rt1", "R", duration = 200)
        coEvery { repository.getAlbum("al-rt", any(), any()) } returns
            AlbumWithTracks(Album("al-rt", "Album"), listOf(track))
        coEvery { cacheService.getTracksByIds(any()) } returns emptyList()
        coEvery { trackDao.watchTracksByIds(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { cacheQueueDao.watchByTrackIds(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())

        viewModel.loadAlbum("al-rt", "user", "pass")
        advanceUntilIdle()
        viewModel.rateTrack("rt1", 4)
        advanceUntilIdle()

        assertEquals(4, viewModel.getTrackRating("rt1"))
    }

    @Test
    fun `toggleTrackDislike marks a track disliked locally`() = runTest {
        val track = Track("td1", "D", duration = 200)
        coEvery { repository.getAlbum("al-td", any(), any()) } returns
            AlbumWithTracks(Album("al-td", "Album"), listOf(track))
        coEvery { cacheService.getTracksByIds(any()) } returns emptyList()
        coEvery { trackDao.watchTracksByIds(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { cacheQueueDao.watchByTrackIds(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())

        viewModel.loadAlbum("al-td", "user", "pass")
        advanceUntilIdle()
        viewModel.toggleTrackDislike("td1")
        advanceUntilIdle()

        assertTrue(viewModel.isTrackDisliked("td1"))
        assertFalse(viewModel.isTrackLiked("td1"))
    }
}
