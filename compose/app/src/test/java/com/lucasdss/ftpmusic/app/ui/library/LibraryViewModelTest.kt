package com.lucasdss.ftpmusic.app.ui.library

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorker
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.DailyMixGenerationCoordinator
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val api: SubsonicApi = mockk()
    private val trackDao: TrackDao = mockk()
    private val genreDao: GenreDao = mockk()
    private val genreMixDao: com.lucasdss.ftpmusic.app.data.db.GenreMixDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk()
    private val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao = mockk(relaxed = true)
    private val downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val playbackManager: PlaybackManager = mockk(relaxed = true)
    private val playlistRepo: com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository = mockk(relaxed = true)
    private val dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository =
        mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        DailyMixGenerationCoordinator.resetForTest()
        every { storage.get(SecureStorage.KEY_USERNAME) } returns null
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns null
    }

    @After
    fun tearDown() {
        DailyMixGenerationCoordinator.resetForTest()
        Dispatchers.resetMain()
    }

    private fun buildArtistResponse(artists: List<Map<String, Any?>>): Map<String, Any> = mapOf(
        "subsonic-response" to mapOf(
            "artists" to mapOf(
                "index" to listOf(
                    mapOf("artist" to artists),
                ),
            ),
        ),
    )

    private fun buildAlbumListResponse(albums: List<Map<String, Any?>>): Map<String, Any> = mapOf(
        "subsonic-response" to mapOf(
            "albumList2" to mapOf(
                "album" to albums,
            ),
        ),
    )

    @Test
    fun `loadArtists populates artist list`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        val artistMaps = listOf(
            mapOf<String, Any?>("id" to "a1", "name" to "Artist One", "coverArt" to "ca-1", "albumCount" to 3),
            mapOf<String, Any?>("id" to "a2", "name" to "Artist Two", "coverArt" to null, "albumCount" to 1),
        )
        coEvery { api.getArtists(any()) } returns buildArtistResponse(artistMaps)

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        val state = viewModel.state.first { !it.isLoading && it.artists.isNotEmpty() }

        assertEquals(2, state.artists.size)
        assertEquals("Artist One", state.artists[0].name)
        assertEquals("a1", state.artists[0].id)
        assertEquals("ca-1", state.artists[0].coverArt)
    }

    @Test
    fun `loadArtists overlays cached albumCount over API count`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        // Cached data: AC/DC has 9 albums (recomputed by updateArtistAlbumCounts)
        coEvery { metadataDao.getAllArtists() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity(id = "acdc", name = "AC/DC", albumCount = 9),
        )
        // API refresh says 11 (all appearances incl. compilations) — must NOT win
        val artistMaps = listOf(
            mapOf<String, Any?>("id" to "acdc", "name" to "AC/DC", "coverArt" to null, "albumCount" to 11),
        )
        coEvery { api.getArtists(any()) } returns buildArtistResponse(artistMaps)

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                metadataDao,
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        val state = viewModel.state.first { !it.isLoading && it.artists.isNotEmpty() }

        assertEquals("Cached albumCount must override API count", 9, state.artists[0].albumCount)
    }

    @Test
    fun `loadAlbums populates album list`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())

        val albumMaps = listOf(
            mapOf<String, Any?>(
                "id" to "al-1",
                "name" to "Album One",
                "artist" to "Artist A",
                "year" to 2023,
                "coverArt" to "cov-1",
            ),
            mapOf<String, Any?>(
                "id" to "al-2",
                "name" to "Album Two",
                "artist" to "Artist B",
                "year" to null,
                "coverArt" to null,
            ),
        )
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(albumMaps)
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        val state = viewModel.state.first { !it.isLoading && it.albums.isNotEmpty() }

        assertEquals(2, state.albums.size)
        assertEquals("Album One", state.albums[0].name)
        assertEquals("al-1", state.albums[0].id)
    }

    @Test
    fun `loadRandomAlbums populates random albums`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())

        val randomMaps = listOf(
            mapOf<String, Any?>(
                "id" to "ral-1",
                "name" to "Random Album",
                "artist" to "Random Artist",
                "coverArt" to "rc-1",
            ),
        )
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(randomMaps)

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        val state = viewModel.state.first { it.randomAlbums.isNotEmpty() }

        assertEquals(1, state.randomAlbums.size)
        assertEquals("Random Album", state.randomAlbums[0].name)
    }

    @Test
    fun `loadRecentlyPlayed returns cached tracks`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())

        val cachedTracks = listOf(
            TrackEntity(id = "t1", title = "Cached Track", durationSeconds = 200, playCount = 5),
            TrackEntity(id = "t2", title = "Another Cached", durationSeconds = 180, playCount = 3),
        )
        coEvery { trackDao.getRecentlyPlayed(20) } returns cachedTracks

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        val state = viewModel.state.first { it.recentlyPlayed.isNotEmpty() }

        assertEquals(2, state.recentlyPlayed.size)
        assertEquals("t1", state.recentlyPlayed[0].id)
        assertEquals("Cached Track", state.recentlyPlayed[0].title)
    }

    @Test
    fun `connect updates credentials and reloads`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        val artistMaps = listOf(
            mapOf<String, Any?>("id" to "a1", "name" to "Artist One", "coverArt" to null, "albumCount" to 1),
        )
        coEvery { api.getArtists(any()) } returns buildArtistResponse(artistMaps)
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { trackDao.getRecentlyPlayed(any()) } returns emptyList()

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )

        // After connect, new credentials should be used
        viewModel.connect("https://new.example.com", "newuser", "newpass")

        val state = viewModel.state.first { !it.isLoading && it.artists.isNotEmpty() }
        assertEquals("Artist One", state.artists[0].name)

        coVerify(atLeast = 1) { api.getArtists(any()) }
        coVerify(atLeast = 1) { api.getAlbumList2("newest", 50, 0, any()) }
        coVerify(atLeast = 1) { api.getAlbumList2("newest", 10, 0, any()) }
    }

    @Test
    fun `recentlyPlayed is empty when no tracks played`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { trackDao.getRecentlyPlayed(20) } returns emptyList()
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        coEvery { genreDao.getRecentlyPlayedGenres() } returns emptyList()

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        val state = viewModel.state.first { !it.isLoading }

        assertTrue(state.recentlyPlayed.isEmpty())
    }

    @Test
    fun `refreshRecentlyPlayed re-queries DAO`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        coEvery { genreDao.getRecentlyPlayedGenres() } returns emptyList()

        // First call returns empty, second returns data
        coEvery { trackDao.getRecentlyPlayed(20) } returns emptyList() andThen listOf(
            TrackEntity(id = "new", title = "New Track", durationSeconds = 300, playCount = 1),
        )

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        var state = viewModel.state.first { !it.isLoading }
        assertTrue(state.recentlyPlayed.isEmpty())

        viewModel.refreshRecentlyPlayed()
        state = viewModel.state.first { it.recentlyPlayed.isNotEmpty() }
        assertEquals(1, state.recentlyPlayed.size)
        assertEquals("New Track", state.recentlyPlayed[0].title)
    }

    @Test
    fun `stats computed from DAO queries`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { trackDao.getRecentlyPlayed(20) } returns emptyList()
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        coEvery { genreDao.getRecentlyPlayedGenres() } returns emptyList()

        coEvery { trackDao.getTotalPlays() } returns 42
        coEvery { trackDao.getTotalListeningSeconds() } returns 7200 // 120 minutes
        coEvery { trackDao.getArtistCount() } returns 5
        coEvery { trackDao.getTrackCount() } returns 15

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadStats()
        val state = viewModel.state.first { it.stats.totalPlays > 0 }

        assertEquals(42, state.stats.totalPlays)
        assertEquals(120, state.stats.listeningMinutes) // 7200/60
        assertEquals(5, state.stats.artistCount)
        assertEquals(15, state.stats.trackCount)
    }

    @Test
    fun `stats default to zero when no data`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { trackDao.getRecentlyPlayed(20) } returns emptyList()
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        coEvery { genreDao.getRecentlyPlayedGenres() } returns emptyList()

        coEvery { trackDao.getTotalPlays() } returns 0
        coEvery { trackDao.getTotalListeningSeconds() } returns 0
        coEvery { trackDao.getArtistCount() } returns 0
        coEvery { trackDao.getTrackCount() } returns 0

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadStats()
        val state = viewModel.state.first { !it.isLoading }

        assertEquals(0, state.stats.totalPlays)
        assertEquals(0, state.stats.listeningMinutes)
        assertEquals(0, state.stats.artistCount)
        assertEquals(0, state.stats.trackCount)
    }

    @Test
    fun `recently played tracks include artist field`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        coEvery { genreDao.getRecentlyPlayedGenres() } returns emptyList()

        val tracks = listOf(
            TrackEntity(id = "t1", title = "Song", artist = "Band", durationSeconds = 240, playCount = 3),
            TrackEntity(id = "t2", title = "Song 2", artist = null, durationSeconds = 180, playCount = 1),
        )
        coEvery { trackDao.getRecentlyPlayed(20) } returns tracks

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        val state = viewModel.state.first { it.recentlyPlayed.isNotEmpty() }

        assertEquals(2, state.recentlyPlayed.size)
        assertEquals("Band", state.recentlyPlayed[0].artist)
        assertNull(state.recentlyPlayed[1].artist)
    }

    @Test
    fun `loadGenres populates genres from recently played tracks`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { trackDao.getRecentlyPlayed(20) } returns emptyList()

        coEvery { genreDao.getRecentlyPlayedGenres() } returns listOf("Rock", "Jazz", "Blues")
        coEvery { genreDao.getAllByPopularity() } returns emptyList()

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadGenres()
        val state = viewModel.state.first { it.genres.isNotEmpty() }

        assertEquals(listOf("Rock", "Jazz", "Blues"), state.genres)
    }

    // ── Radio stations tests ───────────────────────────────────────────────

    @Test
    fun `loadRadioStations populates state from API`() = runTest {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { trackDao.getRecentlyPlayed(20) } returns emptyList()
        coEvery { api.getInternetRadioStations(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "internetRadioStations" to mapOf(
                    "internetRadioStation" to listOf(
                        mapOf(
                            "id" to "r1",
                            "name" to "Synthwave FM",
                            "streamUrl" to "https://stream.example.com/128",
                            "homePageUrl" to "https://example.com",
                        ),
                    ),
                ),
            ),
        )

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadRadioStations()
        val state = viewModel.state.first { it.radioStations.isNotEmpty() }

        assertEquals(1, state.radioStations.size)
        assertEquals("Synthwave FM", state.radioStations[0].name)
        assertEquals("https://stream.example.com/128", state.radioStations[0].streamUrl)
    }

    @Test
    fun `loadRadioStations handles empty response`() = runTest {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { trackDao.getRecentlyPlayed(20) } returns emptyList()
        coEvery { api.getInternetRadioStations(any()) } returns mapOf(
            "subsonic-response" to mapOf("internetRadioStations" to emptyMap<String, Any>()),
        )

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadRadioStations()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.radioStations.isEmpty())
    }

    @Test
    fun `loadRadioStations handles API failure gracefully`() = runTest {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { trackDao.getRecentlyPlayed(20) } returns emptyList()
        coEvery { api.getInternetRadioStations(any()) } throws RuntimeException("Network error")

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadRadioStations()
        advanceUntilIdle()

        // Should not crash, radioStations remains empty
        assertTrue(viewModel.state.value.radioStations.isEmpty())
    }

    @Test
    fun `loadRadioStations populates radio stations on success`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { trackDao.getRecentlyPlayed(20) } returns emptyList()

        coEvery { api.getInternetRadioStations(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "internetRadioStations" to mapOf(
                    "internetRadioStation" to listOf(
                        mapOf(
                            "id" to "r1",
                            "name" to "Jazz FM",
                            "streamUrl" to "http://jazz.fm/stream",
                            "homePageUrl" to "http://jazz.fm",
                        ),
                        mapOf("id" to "r2", "name" to "Rock Radio", "streamUrl" to "http://rock.radio/stream"),
                    ),
                ),
            ),
        )

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadRadioStations()
        advanceUntilIdle()

        val stations = viewModel.state.value.radioStations
        assertEquals(2, stations.size)
        assertEquals("Jazz FM", stations[0].name)
        assertEquals("http://jazz.fm/stream", stations[0].streamUrl)
        assertEquals("http://jazz.fm", stations[0].homePageUrl)
        assertEquals("r1", stations[0].id)
    }

    // ── New methods: searchAlbums, searchArtists, importPlaylist, loadServerPlaylists ──

    @Test
    fun `searchAlbums queries metadataDao and updates state`() = runTest {
        val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao = mockk(relaxed = true)
        val albums = listOf(
            com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity(id = "al-1", name = "Abbey Road", artist = "Beatles"),
        )
        coEvery { metadataDao.searchAlbums("Abbey") } returns albums

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                metadataDao,
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.searchAlbums("Abbey")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(0, state.albums.size)
        assertEquals(1, state.albumSearchResults?.size)
        assertEquals("Abbey Road", state.albumSearchResults!![0].name)
    }

    @Test
    fun `clearAlbumSearch restores browse without reload`() = runTest {
        val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao = mockk(relaxed = true)
        val browse = listOf(
            com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity(id = "a0", name = "Browse Album", artist = "X"),
        )
        val hit = listOf(
            com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity(id = "a1", name = "Abbey Road", artist = "Beatles"),
        )
        coEvery { metadataDao.getAllAlbums() } returns browse
        coEvery { metadataDao.searchAlbums("Abbey") } returns hit

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                metadataDao,
                mockk(relaxed = true),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadAlphaAlbums()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.albums.size)
        assertEquals("Browse Album", viewModel.state.value.albums[0].name)

        viewModel.searchAlbums("Abbey")
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.albums.size)
        assertEquals("Browse Album", viewModel.state.value.albums[0].name)
        assertEquals("Abbey Road", viewModel.state.value.albumSearchResults!![0].name)

        viewModel.clearAlbumSearch()
        advanceUntilIdle()
        assertNull(viewModel.state.value.albumSearchResults)
        assertEquals(1, viewModel.state.value.albums.size)
        assertEquals("Browse Album", viewModel.state.value.albums[0].name)
        coVerify(exactly = 1) { metadataDao.getAllAlbums() }
    }

    @Test
    fun `searchArtists queries metadataDao and updates state`() = runTest {
        val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao = mockk(relaxed = true)
        val artists = listOf(
            com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity(id = "ar-1", name = "Adele"),
        )
        coEvery { metadataDao.searchArtists("Ade") } returns artists

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                metadataDao,
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.searchArtists("Ade")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(0, state.artists.size)
        assertEquals(1, state.artistSearchResults?.size)
        assertEquals("Adele", state.artistSearchResults!![0].name)
    }

    @Test
    fun `importPlaylist saves playlist metadata entries and tracks to DB`() = runTest {
        val playlistRepo: PlaylistRepository = mockk(relaxed = true)
        coEvery { playlistRepo.importPlaylist("pl-import") } returns Unit

        // Need a fresh ViewModel with the specific playlistRepo mock
        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                playlistRepo,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.importPlaylist("pl-import")
        advanceUntilIdle()

        coVerify { playlistRepo.importPlaylist("pl-import") }
    }

    @Test
    fun `loadPlaylists shows local DB playlists only`() = runTest {
        val local = listOf(
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntity(
                id = "pl-1",
                name = "My Mix",
                trackCount = 12,
                coverArt = "ca-1",
            ),
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntity(id = "pl-2", name = "Favorites", trackCount = 8),
        )
        coEvery { playlistDao.getAll() } returns local

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadPlaylists()
        advanceUntilIdle()

        val playlists = viewModel.state.value.playlists
        assertEquals(2, playlists.size)
        assertEquals("My Mix", playlists[0].name)
        assertEquals("pl-1", playlists[0].id)
        assertEquals(12, playlists[0].trackCount)
        assertEquals("Favorites", playlists[1].name)
    }

    @Test
    fun `createPlaylist saves locally immediately and enqueues sync`() = runTest {
        val playlistRepo: PlaylistRepository = mockk(relaxed = true)
        coEvery { playlistRepo.createPlaylist("My New Playlist") } returns "new-temp-id"

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                playlistRepo,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.createPlaylist("My New Playlist")
        advanceUntilIdle()

        coVerify { playlistRepo.createPlaylist("My New Playlist") }
        assertEquals("My New Playlist", viewModel.state.value.playlists[0].name)
        assertTrue(viewModel.state.value.playlistCreated)
    }

    @Test
    fun `loadPlaylists returns empty when no local playlists`() = runTest {
        coEvery { playlistDao.getAll() } returns emptyList()

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadPlaylists()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.playlists.isEmpty())
    }

    @Test
    fun `loadPlaylists does NOT call API`() = runTest {
        coEvery { playlistDao.getAll() } returns emptyList()

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadPlaylists()
        advanceUntilIdle()

        coVerify(exactly = 0) { api.getPlaylists(any()) }
    }

    @Test
    fun `loadServerPlaylists filters out already-imported`() = runTest {
        val playlistRepo: PlaylistRepository = mockk(relaxed = true)
        coEvery { playlistRepo.loadServerPlaylists() } returns listOf(
            PlaylistView(id = "server-2", name = "Server Only", trackCount = 10),
        )

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                playlistRepo,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadServerPlaylists()
        advanceUntilIdle()

        val serverList = viewModel.serverPlaylists.value
        assertEquals(1, serverList.size)
        assertEquals("Server Only", serverList[0].name)
        assertEquals("server-2", serverList[0].id)
    }

    @Test
    fun `resyncAll does NOT overwrite local playlists with server data`() = runTest {
        val localPlaylists = listOf(
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntity(id = "local-1", name = "My Mix", trackCount = 5),
        )
        coEvery { playlistDao.getAll() } returns localPlaylists
        coEvery { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        coEvery { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadPlaylists()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.playlists.size)

        viewModel.resyncAll()
        advanceUntilIdle()

        // resyncAll should NOT call getPlaylists (local-first — no server override)
        coVerify(exactly = 0) { api.getPlaylists(any()) }
        // Local playlists remain unchanged after resync
        assertEquals(1, viewModel.state.value.playlists.size)
        assertEquals("My Mix", viewModel.state.value.playlists[0].name)
    }

    // ── Alpha Albums DB-first ────────────────────────────────────────────────

    @Test
    fun `loadAlphaAlbums loads from cached_albums DB first`() = runTest {
        val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao = mockk(relaxed = true)
        val cachedAlbums = listOf(
            com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity(
                id = "al-1",
                name = "Abbey Road",
                artist = "The Beatles",
                year = 1969,
                coverArt = "ca-1",
                songCount = 17,
            ),
            com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity(
                id = "al-2",
                name = "Back in Black",
                artist = "AC/DC",
                year = 1980,
                coverArt = "ca-2",
                songCount = 10,
            ),
        )
        coEvery { metadataDao.getAllAlbums() } returns cachedAlbums

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                metadataDao,
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadAlphaAlbums()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.albums.size)
        assertEquals("Abbey Road", state.albums[0].name)
        assertEquals("Back in Black", state.albums[1].name)
        // alphaLetters scrubber removed — field stays empty default
        assertTrue(state.alphaLetters.isEmpty())
    }

    @Test
    fun `loadMoreAlbums is a no-op after loadAlphaAlbums`() = runTest {
        val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao = mockk(relaxed = true)
        val cachedAlbums = listOf(
            com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity(
                id = "al-1",
                name = "Abbey Road",
                artist = "The Beatles",
                year = 1969,
                coverArt = "ca-1",
                songCount = 17,
            ),
        )
        coEvery { metadataDao.getAllAlbums() } returns cachedAlbums
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns buildAlbumListResponse(emptyList())

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                metadataDao,
                mockk(relaxed = true),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadAlphaAlbums()
        advanceUntilIdle()
        val sizeBefore = viewModel.state.value.albums.size
        clearMocks(api, answers = false, recordedCalls = true)

        viewModel.loadMoreAlbums()
        advanceUntilIdle()

        assertEquals(sizeBefore, viewModel.state.value.albums.size)
        coVerify(exactly = 0) { api.getAlbumList2("newest", any(), any(), any()) }
    }

    @Test
    fun `loadAlphaAlbums emits empty state when cache is empty`() = runTest {
        val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao = mockk(relaxed = true)
        coEvery { metadataDao.getAllAlbums() } returns emptyList()

        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                metadataDao,
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )
        viewModel.loadAlphaAlbums()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(0, state.albums.size)
        assertEquals(0, state.alphaLetters.size)
    }

    // ── Custom Daily Mix cards (v47) ───────────────────────────────────────

    private fun idleWorker(): com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker {
        val w = mockk<com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker>(relaxed = true)
        every { w.status } returns kotlinx.coroutines.flow.MutableStateFlow(
            com.lucasdss.ftpmusic.app.data.db.SyncStatus(),
        )
        return w
    }

    private fun mixVm(
        repo: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository,
        worker: com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker = idleWorker(),
    ): LibraryViewModel = LibraryViewModel(
        api, trackDao, genreDao, genreMixDao, playlistDao,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        metadataDao,
        worker,
        downloadManager, storage, playbackManager,
        mockk<OfflineModeManager>(relaxed = true),
        mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true),
        mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true),
        repo,
        ioDispatcher = testDispatcher,
    )

    private fun rockMix(id: Long = 1L) = com.lucasdss.ftpmusic.app.data.repository.CustomMix(
        id = id,
        name = "Rock Mix",
        filters = com.lucasdss.ftpmusic.app.data.repository.MixFilters(genres = listOf("Rock")),
        autoCache = false,
        isDefault = true,
    )

    @Test
    fun `loadDailyMixes returns empty when no albums synced`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 0
        val vm = mixVm(dailyMixRepository)

        vm.loadDailyMixes()
        advanceUntilIdle()

        assertTrue(vm.state.value.mixCards.isEmpty())
        coVerify(exactly = 0) { dailyMixRepository.getAll() }
    }

    @Test
    fun `loadDailyMixes reads existing mixes without lazy generation`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { dailyMixRepository.getAll() } returns listOf(rockMix())
        val today = java.time.LocalDate.now().toString()
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        coEvery { genreMixDao.getDailyMixesForDates(listOf(today, yesterday)) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.DailyMixEntity(id = 10, date = today, mixId = 1L),
        )
        coEvery { genreMixDao.getDailyMixTrackCounts(listOf(10)) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.DailyMixTrackCount(mixId = 10, trackCount = 2),
        )
        coEvery { genreMixDao.getDailyMixCovers(10) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.CoverArtProjection("ca-1"),
        )
        val vm = mixVm(dailyMixRepository)

        vm.loadDailyMixes()
        advanceUntilIdle()

        coVerify(exactly = 0) { dailyMixRepository.generateAll(any(), any(), any()) }
        assertEquals(1, vm.state.value.mixCards.size)
        assertEquals("Rock Mix", vm.state.value.mixCards[0].name)
        assertEquals(2, vm.state.value.mixCards[0].songCount)
        assertEquals(listOf("ca-1"), vm.state.value.mixCards[0].coverArts)
    }

    @Test
    fun `loadDailyMixes hides zero-track mixes`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { dailyMixRepository.getAll() } returns listOf(rockMix(1L), rockMix(2L).copy(name = "Empty Mix"))
        val today = java.time.LocalDate.now().toString()
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        coEvery { genreMixDao.getDailyMixesForDates(listOf(today, yesterday)) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.DailyMixEntity(id = 10, date = today, mixId = 1L),
            com.lucasdss.ftpmusic.app.data.db.DailyMixEntity(id = 20, date = today, mixId = 2L),
        )
        coEvery { genreMixDao.getDailyMixTrackCounts(listOf(10, 20)) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.DailyMixTrackCount(mixId = 10, trackCount = 1),
        )
        coEvery { genreMixDao.getDailyMixCovers(10) } returns emptyList()
        val vm = mixVm(dailyMixRepository)

        vm.loadDailyMixes()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.mixCards.size)
        assertEquals(1L, vm.state.value.mixCards[0].id)
    }

    @Test
    fun `loadDailyMixes generates when no visible mixes exist`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { dailyMixRepository.getAll() } returns listOf(rockMix())
        coEvery { dailyMixRepository.generateAll(any(), false, any()) } returns 1
        val vm = mixVm(dailyMixRepository)

        vm.loadDailyMixes()
        advanceUntilIdle()

        coVerify(exactly = 1) { dailyMixRepository.generateAll(any(), false, any()) }
        assertFalse(vm.state.value.isGeneratingMixes)
    }

    @Test
    fun `loadDailyMixes generates when some mixes lack a tracklist`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { dailyMixRepository.getAll() } returns listOf(rockMix(1L), rockMix(2L).copy(name = "New Mix"))
        val today = java.time.LocalDate.now().toString()
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        // Only mix 1 has a tracklist → partial set must trigger generation.
        coEvery { genreMixDao.getDailyMixesForDates(listOf(today, yesterday)) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.DailyMixEntity(id = 10, date = today, mixId = 1L),
        )
        coEvery { genreMixDao.getDailyMixTrackCounts(listOf(10)) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.DailyMixTrackCount(mixId = 10, trackCount = 1),
        )
        coEvery { genreMixDao.getDailyMixCovers(10) } returns emptyList()
        coEvery { dailyMixRepository.generateAll(any(), false, any()) } returns 1
        val vm = mixVm(dailyMixRepository)

        vm.loadDailyMixes()
        advanceUntilIdle()

        coVerify(exactly = 1) { dailyMixRepository.generateAll(any(), false, any()) }
    }

    @Test
    fun `loadDailyMixes skips generation while worker sync is running`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { dailyMixRepository.getAll() } returns listOf(rockMix())
        val worker = mockk<com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker>(relaxed = true)
        every { worker.status } returns kotlinx.coroutines.flow.MutableStateFlow(
            com.lucasdss.ftpmusic.app.data.db.SyncStatus(isRunning = true),
        )
        val vm = mixVm(dailyMixRepository, worker)

        vm.loadDailyMixes()
        advanceUntilIdle()

        coVerify(exactly = 0) { dailyMixRepository.generateAll(any(), any(), any()) }
    }

    @Test
    fun `loadDailyMixes does not retry same-day after an empty outcome`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { dailyMixRepository.getAll() } returns listOf(rockMix())
        coEvery { dailyMixRepository.generateAll(any(), false, any()) } returns 0
        val vm = mixVm(dailyMixRepository)

        vm.loadDailyMixes()
        advanceUntilIdle()
        vm.loadDailyMixes()
        advanceUntilIdle()

        coVerify(exactly = 1) { dailyMixRepository.generateAll(any(), false, any()) }
    }

    @Test
    fun `loadDailyMixes does not double generate when coordinator is busy`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { dailyMixRepository.getAll() } returns listOf(rockMix())
        DailyMixGenerationCoordinator.tryBegin()
        val vm = mixVm(dailyMixRepository)

        vm.loadDailyMixes()
        advanceUntilIdle()

        coVerify(exactly = 0) { dailyMixRepository.generateAll(any(), any(), any()) }
    }

    @Test
    fun `refreshMix regenerates one mix and reloads`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { dailyMixRepository.getAll() } returns emptyList()
        val vm = mixVm(dailyMixRepository)

        vm.refreshMix(5L)
        advanceUntilIdle()

        coVerify(exactly = 1) { dailyMixRepository.generateOne(5L, manual = true) }
        coVerify(atLeast = 1) { dailyMixRepository.getAll() }
    }

    @Test
    fun `refreshAllMixes regenerates all mixes manually`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { dailyMixRepository.getAll() } returns emptyList()
        val vm = mixVm(dailyMixRepository)

        vm.refreshAllMixes()
        advanceUntilIdle()

        coVerify(exactly = 1) { dailyMixRepository.generateAll(any(), true, any()) }
    }

    // ── Surprise Me offline fallback ───────────────────────────────────

    private fun offlineVm(): Pair<LibraryViewModel, OfflineModeManager> {
        val offlineManager =
            OfflineModeManager(mockk<com.lucasdss.ftpmusic.app.data.security.SecureStorage>(relaxed = true))
        val vm = LibraryViewModel(
            api, trackDao, genreDao, genreMixDao, playlistDao,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), metadataDao,
            mockk(
                relaxed = true,
            ),
            downloadManager, storage, playbackManager, offlineManager,
            mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                relaxed = true,
            ),
            mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true),
            dailyMixRepository,
        )
        return vm to offlineManager
    }

    private fun cachedEntity(id: String) = TrackEntity(
        id = id,
        title = "Song $id",
        artist = "Artist",
        coverArtUrl = "ca-$id",
        contentType = "audio/mpeg",
        suffix = "mp3",
    )

    @Test
    fun `playSurpriseMe offline uses cached tracks and skips API`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        val (vm, offlineManager) = offlineVm()
        offlineManager.enable()
        coEvery { trackDao.getRandomCachedTracks(50) } returns listOf(cachedEntity("t1"), cachedEntity("t2"))
        PlayerHolder.player = null

        vm.playSurpriseMe()
        advanceUntilIdle()

        coVerify(exactly = 1) { trackDao.getRandomCachedTracks(50) }
        coVerify(exactly = 0) { api.getRandomSongs(any(), size = 50) }
        coVerify(exactly = 1) {
            playbackManager.tryStartContext(
                match { it.size == 2 },
                any(),
                0,
                "random",
                "surprise-me",
                "Surprise Me",
            )
        }
    }

    @Test
    fun `playSurpriseMe offline with no cached tracks is a no-op`() = runTest(testDispatcher) {
        val (vm, offlineManager) = offlineVm()
        offlineManager.enable()
        coEvery { trackDao.getRandomCachedTracks(50) } returns emptyList()

        vm.playSurpriseMe()
        advanceUntilIdle()

        coVerify(exactly = 0) { playbackManager.playAlbum(any(), any()) }
        coVerify(exactly = 0) { playbackManager.addAllToQueue(any(), any()) }
    }

    @Test
    fun `playSurpriseMe offline uses tryStartContext even when already playing`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        val (vm, offlineManager) = offlineVm()
        offlineManager.enable()
        coEvery { trackDao.getRandomCachedTracks(50) } returns listOf(cachedEntity("t1"))
        val mockPlayer = mockk<androidx.media3.common.Player>(relaxed = true)
        every { mockPlayer.isPlaying } returns true
        every { mockPlayer.mediaItemCount } returns 5
        PlayerHolder.player = mockPlayer

        vm.playSurpriseMe()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            playbackManager.tryStartContext(
                any(),
                any(),
                0,
                "random",
                "surprise-me",
                "Surprise Me",
            )
        }
        coVerify(exactly = 0) { playbackManager.addAllToQueue(any(), any()) }
    }

    @Test
    fun `playSurpriseMe online calls tryStartContext when already playing`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        val (vm, offlineManager) = offlineVm()
        offlineManager.disable()
        coEvery { api.getRandomSongs(any(), size = 50) } returns mapOf(
            "subsonic-response" to mapOf(
                "randomSongs" to mapOf(
                    "song" to listOf(
                        mapOf<String, Any?>("id" to "r1", "title" to "Rand 1"),
                        mapOf<String, Any?>("id" to "r2", "title" to "Rand 2"),
                    ),
                ),
            ),
        )
        val mockPlayer = mockk<androidx.media3.common.Player>(relaxed = true)
        every { mockPlayer.isPlaying } returns true
        every { mockPlayer.mediaItemCount } returns 5
        PlayerHolder.player = mockPlayer

        vm.playSurpriseMe()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            playbackManager.tryStartContext(
                match { it.size == 2 },
                any(),
                0,
                "random",
                "surprise-me",
                "Surprise Me",
            )
        }
        coVerify(exactly = 0) { playbackManager.addAllToQueue(any(), any()) }
    }

    @Test
    fun `playSurpriseMe online still calls API when not offline`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        val (vm, offlineManager) = offlineVm()
        offlineManager.disable()
        coEvery { api.getRandomSongs(any(), size = 50) } returns mapOf(
            "subsonic-response" to mapOf(
                "randomSongs" to mapOf("song" to listOf<Map<String, Any?>>()),
            ),
        )

        vm.playSurpriseMe()
        advanceUntilIdle()

        coVerify(exactly = 1) { api.getRandomSongs(any(), size = 50) }
        coVerify(exactly = 0) { trackDao.getRandomCachedTracks(50) }
    }

    @Test
    fun `maybeRefillRandomQueue offline refills from cached tracks`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        val (vm, offlineManager) = offlineVm()
        offlineManager.enable()
        coEvery { trackDao.getRandomCachedTracks(50) } returns listOf(cachedEntity("t1"))
        val mockExo = mockk<androidx.media3.common.Player>(relaxed = true)
        every { mockExo.mediaItemCount } returns 5 // below refill threshold
        PlayerHolder.exoPlayer = mockExo
        PlayerHolder.player = mockk(relaxed = true)

        vm.maybeRefillRandomQueue()
        advanceUntilIdle()

        coVerify(exactly = 1) { trackDao.getRandomCachedTracks(50) }
        coVerify(exactly = 0) { api.getRandomSongs(any(), size = 50) }
        coVerify(exactly = 1) { playbackManager.addAllToQueue(any(), any()) }
    }

    // ── resyncAll internal loaders (coverage of *Internal parse paths) ──

    @Test
    fun `resyncAll populates all scopes through internal loaders`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(
            listOf(
                mapOf<String, Any?>("id" to "a1", "name" to "Artist One", "coverArt" to "ca-1", "albumCount" to 3),
            ),
        )
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(
            listOf(
                mapOf<String, Any?>(
                    "id" to "al-1",
                    "name" to "Album One",
                    "artist" to "A",
                    "year" to 2023,
                    "coverArt" to "c1",
                    "userRating" to 5,
                ),
            ),
        )
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(
            listOf(
                mapOf<String, Any?>("id" to "ra-1", "name" to "Random Album", "artist" to "R", "coverArt" to "rc"),
            ),
        )
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "genres" to mapOf(
                    "genre" to listOf(
                        mapOf<String, Any?>("value" to "Rock", "songCount" to 42, "albumCount" to 5),
                    ),
                ),
            ),
        )
        coEvery { genreDao.getRecentlyPlayedGenres() } returns emptyList()
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        coEvery { genreDao.upsertAll(any()) } returns Unit
        coEvery { trackDao.getRecentlyPlayed(20) } returns emptyList()
        coEvery { trackDao.getTracksByAlbumIds(any()) } returns emptyList()

        viewModel = LibraryViewModel(
            api, trackDao, genreDao, genreMixDao, playlistDao,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), metadataDao,
            mockk(relaxed = true), downloadManager, storage, playbackManager,
            mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(
                relaxed = true,
            ),
            mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                relaxed = true,
            ),
            mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true),
            dailyMixRepository,
        )
        viewModel.resyncAll()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Artists from internal loader", 1, state.artists.size)
        assertEquals("Albums from internal loader", 1, state.albums.size)
        assertEquals("Random albums from internal loader", 1, state.randomAlbums.size)
        coVerify(exactly = 1) { api.getGenres(any()) }
        coVerify(exactly = 1) { genreDao.upsertAll(any()) }
        // Init runs the public loaders AND resyncAll runs the internal ones → ≥1
        coVerify(atLeast = 1) { trackDao.getRecentlyPlayed(20) }
        assertFalse("Resync flag cleared", state.isResyncing)
    }

    @Test
    fun `resyncAll with cached genres triggers throttled background sync`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getAlbumList2("newest", 10, 0, any()) } returns buildAlbumListResponse(emptyList())
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "genres" to mapOf(
                    "genre" to listOf(
                        mapOf<String, Any?>("value" to "Jazz", "songCount" to 12, "albumCount" to 3),
                    ),
                ),
            ),
        )
        coEvery { genreDao.getRecentlyPlayedGenres() } returns emptyList()
        coEvery { genreDao.getAllByPopularity() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.GenreEntity(name = "Rock", songCount = 1, albumCount = 1),
        )
        coEvery { genreDao.upsertAll(any()) } returns Unit
        coEvery { trackDao.getRecentlyPlayed(20) } returns emptyList()

        viewModel = LibraryViewModel(
            api, trackDao, genreDao, genreMixDao, playlistDao,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), metadataDao,
            mockk(relaxed = true), downloadManager, storage, playbackManager,
            mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(
                relaxed = true,
            ),
            mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                relaxed = true,
            ),
            mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true),
            dailyMixRepository,
        )
        viewModel.resyncAll()
        advanceUntilIdle()
        assertEquals("Cached genres shown immediately", listOf("Rock"), viewModel.state.value.genres)
        coVerify(exactly = 1) { genreDao.upsertAll(any()) } // background sync ran

        // Second resync: genre hash identical + 1h throttle → sync skipped
        viewModel.resyncAll()
        advanceUntilIdle()
        coVerify(exactly = 1) { genreDao.upsertAll(any()) } // still exactly once
    }

    // ── v43: Favorites (starred albums/artists, bookmarked radio) ─────────

    private fun favoritesVm(
        favoriteRepository: com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository,
        radioFavoriteDao: com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao,
    ): LibraryViewModel = LibraryViewModel(
        api, trackDao, genreDao, genreMixDao, playlistDao,
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), metadataDao,
        mockk(relaxed = true), downloadManager, storage, playbackManager,
        mockk<OfflineModeManager>(relaxed = true), favoriteRepository, radioFavoriteDao,
        dailyMixRepository = dailyMixRepository,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `loadFavorites populates starred entities and id sets`() = runTest(testDispatcher) {
        val favRepo = mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true)
        val radioDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        coEvery { metadataDao.getStarredAlbums(50) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "al-1", name = "Album One", artist = "Artist One"),
        )
        coEvery { metadataDao.getStarredArtists(50) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-1", name = "Artist One"),
        )
        coEvery { radioDao.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                stationId = "st-1",
                name = "Retro",
                streamUrl = "https://s",
            ),
        )
        coEvery { metadataDao.getDislikedAlbumIds() } returns emptyList()
        coEvery { metadataDao.getDislikedArtistIds() } returns emptyList()

        val vm = favoritesVm(favRepo, radioDao)
        vm.loadFavorites()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(1, s.starredAlbums.size)
        assertEquals("Album One", s.starredAlbums[0].name)
        assertEquals(1, s.starredArtists.size)
        assertEquals(1, s.bookmarkedRadio.size)
        assertEquals(setOf("al-1"), s.likedAlbumIds)
        assertEquals(setOf("ar-1"), s.likedArtistIds)
        assertEquals(setOf("st-1"), s.bookmarkedStationIds)
    }

    @Test
    fun `toggleAlbumLike likes optimistically and persists`() = runTest(testDispatcher) {
        val favRepo = mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true)
        val radioDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        val vm = favoritesVm(favRepo, radioDao)

        vm.toggleAlbumLike("al-9")

        assertTrue(vm.state.value.likedAlbumIds.contains("al-9"))
        advanceUntilIdle()
        coVerify { favRepo.likeAlbum("al-9") }
    }

    @Test
    fun `toggleAlbumLike rolls back on repository failure`() = runTest(testDispatcher) {
        val favRepo = mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true)
        coEvery { favRepo.likeAlbum("al-9") } throws RuntimeException("offline")
        val radioDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        val vm = favoritesVm(favRepo, radioDao)

        vm.toggleAlbumLike("al-9")
        advanceUntilIdle()

        assertFalse(vm.state.value.likedAlbumIds.contains("al-9"))
    }

    @Test
    fun `toggleAlbumDislike clears a like`() = runTest(testDispatcher) {
        val favRepo = mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true)
        coEvery { metadataDao.getStarredAlbums(50) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "al-9", name = "Album Nine"),
        )
        val radioDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        val vm = favoritesVm(favRepo, radioDao)
        vm.loadFavorites()
        advanceUntilIdle()

        vm.toggleAlbumDislike("al-9")

        assertTrue(vm.state.value.dislikedAlbumIds.contains("al-9"))
        assertFalse(vm.state.value.likedAlbumIds.contains("al-9"))
        advanceUntilIdle()
        coVerify { favRepo.dislikeAlbum("al-9") }
    }

    @Test
    fun `toggleArtistLike and dislike are mutually exclusive`() = runTest(testDispatcher) {
        val favRepo = mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true)
        val radioDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        val vm = favoritesVm(favRepo, radioDao)

        vm.toggleArtistLike("ar-7")
        vm.toggleArtistDislike("ar-7")

        assertTrue(vm.state.value.dislikedArtistIds.contains("ar-7"))
        assertFalse(vm.state.value.likedArtistIds.contains("ar-7"))
        advanceUntilIdle()
        coVerify { favRepo.dislikeArtist("ar-7") }
    }

    @Test
    fun `toggleRadioBookmark bookmarks then unbookmarks`() = runTest(testDispatcher) {
        val favRepo = mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true)
        val radioDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        // DB round-trip: empty → bookmarked → empty (mirrors upsert/delete)
        coEvery { radioDao.getAll() } returnsMany listOf(
            emptyList(),
            listOf(
                com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                    stationId = "st-1",
                    name = "Retro",
                    streamUrl = "https://s",
                ),
            ),
            emptyList(),
        )
        val vm = favoritesVm(favRepo, radioDao)
        val station = RadioStation(id = "st-1", name = "Retro", streamUrl = "https://s")

        vm.toggleRadioBookmark(station)
        advanceUntilIdle()
        assertTrue(vm.state.value.bookmarkedStationIds.contains("st-1"))
        coVerify { favRepo.bookmarkRadio("st-1", "Retro", "https://s", null) }

        vm.toggleRadioBookmark(station)
        advanceUntilIdle()
        assertFalse(vm.state.value.bookmarkedStationIds.contains("st-1"))
        coVerify { favRepo.unbookmarkRadio("st-1") }
    }

    @Test
    fun `refreshHomePrefs reads visibility toggles from storage`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_HOME_SHOW_PLAYLISTS) } returns "false"
        every { storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ARTISTS) } returns "true"
        every { storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ALBUMS) } returns "false"
        every { storage.get(SecureStorage.KEY_HOME_SHOW_FAV_RADIO) } returns "true"
        val vm = favoritesVm(mockk(relaxed = true), mockk(relaxed = true))

        vm.refreshHomePrefs()

        assertFalse(vm.state.value.showPlaylistsOnHome)
        assertTrue(vm.state.value.showFavArtistsSection)
        assertFalse(vm.state.value.showFavAlbumsSection)
        assertTrue(vm.state.value.showFavRadioSection)
    }

    @Test
    fun `loadPlaylistMontages builds montage and falls back to single cover`() = runTest(testDispatcher) {
        val favRepo = mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true)
        val radioDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        coEvery { playlistDao.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntity(
                id = "pl-1",
                serverId = "",
                name = "Mix",
                lastSyncedAt = 123L,
            ),
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntity(
                id = "pl-2",
                serverId = "",
                name = "Empty",
                coverArt = "ca-fallback",
                lastSyncedAt = 456L,
            ),
        )
        coEvery { playlistDao.getEntries("pl-1") } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity(playlistId = "pl-1", trackId = "t1", position = 0),
        )
        coEvery { playlistDao.getEntries("pl-2") } returns emptyList()
        coEvery { metadataDao.getPlaylistMontageCovers(listOf("t1")) } returns listOf("ca-1", "ca-2")
        val vm = favoritesVm(favRepo, radioDao)

        vm.loadPlaylists()
        advanceUntilIdle()

        assertEquals(listOf("ca-1", "ca-2"), vm.state.value.playlistMontages["pl-1"])
        assertNull(vm.state.value.playlistMontages["pl-2"])
    }

    // ── v44: ledger healing (network-free fix-up) ────────────────────────

    @Test
    fun `healEmptyLedger populates albums and artists ledgers when sparse`() = runTest(testDispatcher) {
        val favRepo = mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true)
        val radioDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        coEvery { metadataDao.albumCount() } returns 100
        coEvery { metadataDao.artistCount() } returns 50
        coEvery { metadataDao.ledgerAlbumCount() } returns 3
        coEvery { metadataDao.ledgerArtistCount() } returns 2
        val vm = favoritesVm(favRepo, radioDao)

        vm.healEmptyLedger()
        advanceUntilIdle()

        coVerify(exactly = 1) { metadataDao.syncAlbumLedger() }
        coVerify(exactly = 1) { metadataDao.syncArtistLedger() }
    }

    @Test
    fun `healEmptyLedger skips when ledger already covers cached metadata`() = runTest(testDispatcher) {
        val favRepo = mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true)
        val radioDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        coEvery { metadataDao.albumCount() } returns 100
        coEvery { metadataDao.ledgerAlbumCount() } returns 100
        coEvery { metadataDao.ledgerArtistCount() } returns 50
        val vm = favoritesVm(favRepo, radioDao)

        vm.healEmptyLedger()
        advanceUntilIdle()

        coVerify(exactly = 0) { metadataDao.syncAlbumLedger() }
        coVerify(exactly = 0) { metadataDao.syncArtistLedger() }
    }

    // ── v45: reactive favorites (Room Flow observation) ──────────────────

    @Test
    fun `observeFavorites updates state live when flows emit`() = runTest(testDispatcher) {
        val favRepo = mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true)
        val radioDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        coEvery { metadataDao.getStarredAlbumsFlow(50) } returns kotlinx.coroutines.flow.flowOf(
            listOf(com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "al-1", name = "Album One", artist = "Artist A")),
        )
        coEvery { metadataDao.getStarredArtistsFlow(50) } returns kotlinx.coroutines.flow.flowOf(
            listOf(com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-1", name = "Artist A")),
        )
        coEvery { metadataDao.getDislikedAlbumIdsFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { metadataDao.getDislikedArtistIdsFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { radioDao.getAllFlow() } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                    stationId = "st-1",
                    name = "Retro",
                    streamUrl = "https://s",
                ),
            ),
        )
        val vm = favoritesVm(favRepo, radioDao)
        vm.observeFavorites()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(1, s.starredAlbums.size)
        assertEquals(1, s.starredArtists.size)
        assertEquals(1, s.bookmarkedRadio.size)
        assertEquals(setOf("ar-1"), s.likedArtistIds)
    }

    @Test
    fun `observeFavorites reacts to a second flow emission`() = runTest(testDispatcher) {
        val favRepo = mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true)
        val radioDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        val albumsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<com.lucasdss.ftpmusic.app.data.db.AlbumEntity>>(
            emptyList(),
        )
        coEvery { metadataDao.getStarredAlbumsFlow(50) } returns albumsFlow
        coEvery { metadataDao.getStarredArtistsFlow(50) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { metadataDao.getDislikedAlbumIdsFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { metadataDao.getDislikedArtistIdsFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { radioDao.getAllFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        val vm = favoritesVm(favRepo, radioDao)
        vm.observeFavorites()
        advanceUntilIdle()
        assertTrue(vm.state.value.starredAlbums.isEmpty())

        // New emission (e.g. a like from another screen) → state updates without
        // any loadFavorites call.
        albumsFlow.value = listOf(com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "al-2", name = "Album Two"))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.starredAlbums.size)
        assertEquals(setOf("al-2"), vm.state.value.likedAlbumIds)
    }

    // ── Playlist create + Add Songs flow ───────────────────────────────────

    private fun playlistVm(repo: PlaylistRepository = playlistRepo): LibraryViewModel = LibraryViewModel(
        api, trackDao, genreDao, genreMixDao, playlistDao,
        mockk(relaxed = true), mockk(relaxed = true), repo, mockk(relaxed = true), mockk(relaxed = true),
        downloadManager, storage, playbackManager,
        mockk<OfflineModeManager>(relaxed = true),
        mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(relaxed = true),
        mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true),
        dailyMixRepository = dailyMixRepository,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `createPlaylist exposes createdPlaylist and clearCreatedPlaylist resets it`() = runTest {
        coEvery { playlistRepo.createPlaylist("Road Trip") } returns "new-123"
        viewModel = playlistVm()

        viewModel.createPlaylist("Road Trip")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.playlistCreated)
        assertEquals("new-123", viewModel.state.value.createdPlaylist?.id)
        assertEquals("Road Trip", viewModel.state.value.createdPlaylist?.name)
        // New playlist appears at the top of the local list
        assertEquals("new-123", viewModel.state.value.playlists.firstOrNull()?.id)

        viewModel.clearCreatedPlaylist()
        assertFalse(viewModel.state.value.playlistCreated)
        assertNull(viewModel.state.value.createdPlaylist)
    }

    @Test
    fun `addTracksToNewPlaylist calls repository and reloads playlists`() = runTest {
        coEvery { playlistDao.getAll() } returns listOf(
            PlaylistEntity(id = "new-1", name = "Road Trip", trackCount = 2),
        )
        coEvery { playlistDao.getEntries(any()) } returns emptyList()
        viewModel = playlistVm()

        viewModel.addTracksToNewPlaylist("new-1", listOf("t1", "t2"))
        advanceUntilIdle()

        coVerify { playlistRepo.addToPlaylist("new-1", listOf("t1", "t2")) }
        // Auto-download mirrors PlaylistDetailViewModel when the setting is on
        coVerify { downloadManager.enqueue("t1", any(), priority = 1) }
        coVerify { downloadManager.enqueue("t2", any(), priority = 1) }
        // Local list refreshed from DB with the new track count
        assertEquals(2, viewModel.state.value.playlists.firstOrNull { it.id == "new-1" }?.trackCount)
    }

    @Test
    fun `addTracksToNewPlaylist no-ops on blank playlist id or empty tracks`() = runTest {
        viewModel = playlistVm()

        viewModel.addTracksToNewPlaylist("", listOf("t1"))
        viewModel.addTracksToNewPlaylist("new-1", emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistRepo.addToPlaylist(any(), any()) }
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `addTracksToNewPlaylist skips auto-download when disabled`() = runTest {
        coEvery { playlistDao.getAll() } returns listOf(
            PlaylistEntity(id = "new-1", name = "Road Trip", trackCount = 1),
        )
        coEvery { playlistDao.getEntries(any()) } returns emptyList()
        every { storage.get("auto_download_playlists") } returns "false"
        viewModel = playlistVm()

        viewModel.addTracksToNewPlaylist("new-1", listOf("t1"))
        advanceUntilIdle()

        coVerify { playlistRepo.addToPlaylist("new-1", listOf("t1")) }
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `addTracksToNewPlaylist resolves remapped server id by name`() = runTest {
        // Create flush already ran: temp id gone, server id holds the same name
        coEvery { playlistDao.getAll() } returns listOf(
            PlaylistEntity(id = "server-9", name = "Road Trip", trackCount = 0),
        )
        coEvery { playlistDao.getEntries(any()) } returns emptyList()
        every { storage.get("auto_download_playlists") } returns "false"
        viewModel = playlistVm()
        // createdPlaylist still carries the stale temp id
        val field = LibraryViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<LibraryState>
        stateFlow.value = stateFlow.value.copy(createdPlaylist = PlaylistView(id = "new-123", name = "Road Trip"))

        viewModel.addTracksToNewPlaylist("new-123", listOf("t1"))
        advanceUntilIdle()

        // Repo receives the CURRENT (server) id, not the dead temp id
        coVerify { playlistRepo.addToPlaylist("server-9", listOf("t1")) }
        coVerify(exactly = 0) { playlistRepo.addToPlaylist("new-123", any()) }
    }

    @Test
    fun `removePlaylistLocally clears entries, deletes, and refreshes the list`() = runTest {
        coEvery { playlistDao.getAll() } returns listOf(
            PlaylistEntity(id = "pl-2", name = "Remaining", trackCount = 1),
        )
        coEvery { playlistDao.getEntries(any()) } returns emptyList()
        coEvery { playlistDao.clearEntries(any()) } returns Unit
        coEvery { playlistDao.delete(any()) } returns Unit
        viewModel = playlistVm()
        // Pre-populate the local list with the playlist being removed
        val field = LibraryViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<LibraryState>
        stateFlow.value = stateFlow.value.copy(
            playlists = listOf(
                PlaylistView(id = "pl-1", name = "Doomed", trackCount = 3),
                PlaylistView(id = "pl-2", name = "Remaining", trackCount = 1),
            ),
        )

        viewModel.removePlaylistLocally("pl-1")
        advanceUntilIdle()

        coVerify { playlistDao.clearEntries("pl-1") }
        coVerify { playlistDao.delete("pl-1") }
        // list refreshed from DB: pl-1 gone, pl-2 kept
        assertEquals(1, viewModel.state.value.playlists.size)
        assertEquals("pl-2", viewModel.state.value.playlists.first().id)
    }

    @Test
    fun `removePlaylistLocally clears createdPlaylist when deleting the new playlist`() = runTest {
        coEvery { playlistDao.getAll() } returns emptyList()
        coEvery { playlistDao.getEntries(any()) } returns emptyList()
        coEvery { playlistDao.clearEntries(any()) } returns Unit
        coEvery { playlistDao.delete(any()) } returns Unit
        viewModel = playlistVm()
        val field = LibraryViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<LibraryState>
        stateFlow.value = stateFlow.value.copy(
            playlistCreated = true,
            createdPlaylist = PlaylistView(id = "new-123", name = "Fresh"),
        )

        viewModel.removePlaylistLocally("new-123")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.playlistCreated)
        assertNull(viewModel.state.value.createdPlaylist)
        coVerify { playlistDao.delete("new-123") }
    }

    @Test
    fun `removePlaylistLocally no-ops on blank id`() = runTest {
        viewModel = playlistVm()

        viewModel.removePlaylistLocally("")
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistDao.clearEntries(any()) }
        coVerify(exactly = 0) { playlistDao.delete(any()) }
    }

    @Test
    fun `picker passthroughs delegate to trackDao`() = runTest {
        val searched = listOf(TrackEntity(id = "t1", title = "Search Hit"))
        val recent = listOf(TrackEntity(id = "t2", title = "Recent"))
        coEvery { trackDao.searchAllTracks("hit") } returns searched
        coEvery { trackDao.getRecentlyPlayed(limit = 50) } returns recent
        viewModel = playlistVm()

        assertEquals(searched, viewModel.searchPickerTracks("hit"))
        assertEquals(recent, viewModel.pickerSuggestions())
        coVerify { trackDao.searchAllTracks("hit") }
        coVerify { trackDao.getRecentlyPlayed(limit = 50) }
    }

    // ── Loading-robustness tests (eternal-spinner regression) ───────────────

    @Test
    fun `loadAlbums clears isLoading when the Room read stalls`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        // Simulate the startup DB write storm: the cached-albums read never returns.
        coEvery { metadataDao.getAllAlbums() } coAnswers {
            delay(100_000)
            emptyList()
        }
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns mapOf(
            "subsonic-response" to mapOf("albumList2" to mapOf("album" to emptyList<Any>())),
        )
        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                metadataDao,
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )

        // init already fired loadAlbums; wait past the 30s Room-read timeout.
        advanceTimeBy(31_000)
        advanceUntilIdle()

        assertFalse("isLoading must clear even when the Room read stalls", viewModel.state.value.isLoading)
    }

    @Test
    fun `loadArtists clears isLoading when the Room read stalls`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getAllArtists() } coAnswers {
            delay(100_000)
            emptyList()
        }
        coEvery { api.getArtists(any()) } returns buildArtistResponse(emptyList())
        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                metadataDao,
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )

        advanceTimeBy(31_000)
        advanceUntilIdle()

        assertFalse("isLoading must clear even when the Room read stalls", viewModel.state.value.isLoading)
    }

    @Test
    fun `hasLoadedOnce flips true after cached albums load`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getAllAlbums() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity(id = "al-1", name = "Album One"),
        )
        coEvery { api.getAlbumList2("newest", 50, 0, any()) } returns mapOf(
            "subsonic-response" to mapOf("albumList2" to mapOf("album" to emptyList<Any>())),
        )
        viewModel =
            LibraryViewModel(
                api, trackDao, genreDao, genreMixDao, playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                metadataDao,
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(
                    relaxed = true,
                ),
                dailyMixRepository = dailyMixRepository,
                ioDispatcher = testDispatcher,
            )

        advanceUntilIdle()

        assertTrue("hasLoadedOnce must flip after a cached load", viewModel.state.value.hasLoadedOnce)
    }
}
