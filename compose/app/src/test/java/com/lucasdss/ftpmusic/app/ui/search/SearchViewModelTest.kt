package com.lucasdss.ftpmusic.app.ui.search

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.data.model.Artist
import com.lucasdss.ftpmusic.app.data.model.SearchResults
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.SearchRepository
import com.lucasdss.ftpmusic.app.data.search.LocalSearchHit
import com.lucasdss.ftpmusic.app.data.search.LocalSearchRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val repository: SearchRepository = mockk()
    private val storage: SecureStorage = mockk(relaxed = true)
    private val genreDao: GenreDao = mockk(relaxed = true)
    private val api: SubsonicApi = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val offlineFlow = MutableStateFlow(false)
    private val offlineManager: OfflineModeManager = mockk(relaxed = true)
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        NetworkAvailabilityHolder.resetForTests(true)
        offlineFlow.value = false
        every { offlineManager.isOffline } returns offlineFlow
        Dispatchers.setMain(testDispatcher)
        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true), // playlistDao
                mockk(relaxed = true), // localSearch
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                offlineManager,
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
    }

    @After
    fun tearDown() {
        NetworkAvailabilityHolder.resetForTests(true)
        Dispatchers.resetMain()
    }

    @Test
    fun `onQueryChanged sets query without clearing prior results`() = runTest(testDispatcher) {
        viewModel.onQueryChanged("te")
        val state = viewModel.state.first { it.query == "te" }
        assertEquals("te", state.query)
        assertTrue(state.isLoading) // ≥2 chars → loading until search
    }

    @Test
    fun `onQueryChanged keeps painted results across keystrokes`() = runTest(testDispatcher) {
        val localSearch = mockk<LocalSearchRepository>(relaxed = true)
        coEvery { localSearch.search(any(), any(), any()) } returns LocalSearchHit(
            artists = emptyList(),
            albums = emptyList(),
            tracks = listOf(
                TrackEntity(id = "t1", title = "Rock Song", artist = "Band"),
            ),
        )
        every { storage.get(any()) } returns null
        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                localSearch,
                mockk(relaxed = true),
                mockk(relaxed = true),
                api,
                offlineManager,
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            SearchResults(artists = emptyList(), albums = emptyList(), tracks = emptyList())
        viewModel.onQueryChanged("ro")
        advanceUntilIdle()
        val afterSearch = viewModel.state.first { it.hasSearched && it.tracks.isNotEmpty() }
        assertEquals(1, afterSearch.tracks.size)

        viewModel.onQueryChanged("roc")
        val midType = viewModel.state.first { it.query == "roc" }
        assertEquals(1, midType.tracks.size)
        assertTrue(midType.isLoading)
        assertTrue(midType.hasSearched)
    }

    @Test
    fun `onQueryChanged empty clears results`() = runTest(testDispatcher) {
        viewModel.onQueryChanged("ab")
        viewModel.onQueryChanged("")
        val state = viewModel.state.first { it.query.isEmpty() }
        assertTrue(state.artists.isEmpty())
        assertFalse(state.hasSearched)
        assertFalse(state.isLoading)
    }

    @Test
    fun `search triggers API call and populates results`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns SearchResults(
            artists = listOf(Artist("a1", "Rock Band")),
        )

        viewModel.onQueryChanged("rock")
        viewModel.search()
        advanceUntilIdle()

        val after = viewModel.state.first { it.hasSearched }
        assertTrue(after.hasSearched)
        assertEquals(1, after.artists.size)
        assertEquals("Rock Band", after.artists[0].name)
    }

    @Test
    fun `search returns artists albums and tracks`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns SearchResults(
            artists = listOf(Artist("a1", "Artist X")),
            albums = listOf(Album("al1", "Album X")),
            tracks = listOf(Track("t1", "Track X", duration = 180)),
        )

        viewModel.onQueryChanged("test")
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.state.first { it.hasSearched }
        assertTrue(state.hasSearched)
        assertFalse(state.isLoading)
        assertEquals(1, state.artists.size)
        assertEquals(1, state.albums.size)
        assertEquals(1, state.tracks.size)
    }

    @Test
    fun `onRecentTap sets query and searches`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns SearchResults()

        viewModel.onRecentTap("pink floyd")
        advanceUntilIdle()

        val state = viewModel.state.first { it.hasSearched }
        assertEquals("pink floyd", state.query)
        assertTrue(state.hasSearched)
    }

    @Test
    fun `clearRecent removes item from recent searches`() = runTest(testDispatcher) {
        // Simulate two searches to populate recents
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns SearchResults()
        every { storage.put(any(), any()) } returns Unit

        // Perform searches to populate recents
        viewModel.onQueryChanged("rock")
        viewModel.search()
        advanceUntilIdle()
        viewModel.onQueryChanged("pop")
        viewModel.search()
        advanceUntilIdle()
        viewModel.onQueryChanged("jazz")
        viewModel.search()
        advanceUntilIdle()

        val before = viewModel.state.first { it.recentSearches.size == 3 }
        assertEquals(3, before.recentSearches.size)

        viewModel.clearRecent("pop")
        val after = viewModel.state.first { it.recentSearches.size == 2 }
        assertEquals(listOf("jazz", "rock"), after.recentSearches)
    }

    @Test
    fun `saveRecentSearch preserves MutableStateFlow order and caps at MAX_RECENT`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns SearchResults()

        // Perform 12 different searches — each calls saveRecentSearch which caps at 10
        every { storage.put(any(), any()) } returns Unit
        for (i in 1..12) {
            viewModel.onQueryChanged("query$i")
            viewModel.search()
            advanceUntilIdle()
        }

        val state = viewModel.state.first { it.hasSearched }
        assertEquals(10, state.recentSearches.size)
        assertEquals("query12", state.recentSearches.first())
    }

    @Test
    fun `duplicate recent search moves to front`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns SearchResults()
        every { storage.put(any(), any()) } returns Unit

        // pre-populate with some queries
        viewModel.onQueryChanged("rock")
        viewModel.search()
        advanceUntilIdle()
        viewModel.onQueryChanged("pop")
        viewModel.search()
        advanceUntilIdle()

        // Search for "rock" again — should move to front, no duplicate
        viewModel.onQueryChanged("rock")
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.state.first { it.hasSearched && it.recentSearches.size == 2 }
        assertEquals("rock", state.recentSearches[0])
        assertEquals("pop", state.recentSearches[1])
    }

    @Test
    fun `search skips API call when offline and shows playable results only`() = runTest(testDispatcher) {
        val offlineFlow = MutableStateFlow(true)
        val offlineManager = mockk<OfflineModeManager>()
        every { offlineManager.isOffline } returns offlineFlow
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val localSearch = mockk<LocalSearchRepository>()
        val playable = TrackEntity(
            id = "t1",
            title = "Cached Song",
            artist = "Artist",
            coverArtUrl = "",
            durationSeconds = 100,
            isDownloaded = true,
        )
        coEvery { localSearch.search("test", any(), playableOnly = true) } returns LocalSearchHit(
            tracks = listOf(playable),
        )

        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                trackDao,
                metadataDao,
                mockk(relaxed = true), // playlistDao
                localSearch,
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                offlineManager,
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        viewModel.onQueryChanged("test")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.search(any(), any(), any(), any()) }
        coVerify(exactly = 1) { localSearch.search("test", any(), playableOnly = true) }
        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.filterDownloaded)
        assertEquals(1, viewModel.state.value.tracks.size)
        assertEquals("t1", viewModel.state.value.tracks[0].id)
    }

    @Test
    fun `search playable-only when OS network lost without Simulate Offline`() = runTest(testDispatcher) {
        NetworkAvailabilityHolder.resetForTests(false)
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val localSearch = mockk<LocalSearchRepository>()
        coEvery { localSearch.search("air", any(), playableOnly = true) } returns LocalSearchHit()

        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                trackDao,
                metadataDao,
                mockk(relaxed = true), // playlistDao
                localSearch,
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                offlineManager,
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        assertTrue(viewModel.isLocalOnly())
        viewModel.onQueryChanged("air")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.search(any(), any(), any(), any()) }
        coVerify(exactly = 1) { localSearch.search("air", any(), playableOnly = true) }
        assertTrue(viewModel.state.value.filterDownloaded)
    }

    @Test
    fun `OS network loss mid-session re-runs active search as playable-only`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns SearchResults(
            tracks = listOf(Track("online1", "Online", duration = 1)),
        )
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val localSearch = mockk<LocalSearchRepository>()
        coEvery { localSearch.search("mid", any(), playableOnly = false) } returns LocalSearchHit()
        coEvery { localSearch.search("mid", any(), playableOnly = true) } returns LocalSearchHit(
            tracks = listOf(
                TrackEntity(
                    id = "local1",
                    title = "Local",
                    artist = "A",
                    coverArtUrl = "",
                    durationSeconds = 1,
                    isDownloaded = true,
                ),
            ),
        )
        coEvery { trackDao.getTracksByIds(any()) } returns emptyList()

        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                trackDao,
                metadataDao,
                mockk(relaxed = true), // playlistDao
                localSearch,
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                offlineManager,
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        viewModel.onQueryChanged("mid")
        viewModel.search()
        advanceUntilIdle()
        assertEquals("online1", viewModel.state.value.tracks.firstOrNull()?.id)

        NetworkAvailabilityHolder.resetForTests(false)
        advanceUntilIdle()

        coVerify(atLeast = 1) { localSearch.search("mid", any(), playableOnly = true) }
        assertEquals("local1", viewModel.state.value.tracks.firstOrNull()?.id)
        assertTrue(viewModel.state.value.filterDownloaded)
    }

    @Test
    fun `observeLocalOnly tolerates missing offline flow`() = runTest(testDispatcher) {
        val broken = mockk<OfflineModeManager>()
        every { broken.isOffline } throws RuntimeException("no flow")
        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true), // playlistDao
                mockk(relaxed = true), // localSearch
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                broken,
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        advanceUntilIdle()
        assertFalse(viewModel.isLocalOnly())
    }

    @Test
    fun `observeLocalOnly skips re-search when no active query`() = runTest(testDispatcher) {
        val trackDao = mockk<TrackDao>(relaxed = true)
        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                trackDao,
                mockk(relaxed = true),
                mockk(relaxed = true), // playlistDao
                mockk(relaxed = true), // localSearch
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                offlineManager,
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        advanceUntilIdle()
        NetworkAvailabilityHolder.resetForTests(false)
        advanceUntilIdle()
        coVerify(exactly = 0) { trackDao.searchPlayableTracks(any()) }
        assertTrue(viewModel.isLocalOnly())
    }

    @Test
    fun `setFilterDownloaded refuse clear while local-only`() = runTest(testDispatcher) {
        val offlineFlow = MutableStateFlow(true)
        val offlineManager = mockk<OfflineModeManager>()
        every { offlineManager.isOffline } returns offlineFlow
        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true), // playlistDao
                mockk(relaxed = true), // localSearch
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                offlineManager,
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        viewModel.setFilterDownloaded(true)
        viewModel.setFilterDownloaded(false)
        assertTrue(viewModel.state.value.filterDownloaded)
    }

    @Test
    fun `catch block reapplies download filter on API failure`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("Network error")

        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true), // playlistDao
                mockk(relaxed = true), // localSearch
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                mockk<OfflineModeManager>(relaxed = true),
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        viewModel.onQueryChanged("test")
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.state.first { !it.isLoading }
        assertTrue(state.hasSearched)
        // Should not crash — filterDownloaded remains false by default
        assertEquals(0, state.tracks.size)
    }

    @Test
    fun `catch block filters cached tracks when filterDownloaded is true`() = runTest(testDispatcher) {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val localSearch = mockk<LocalSearchRepository>()
        val t1 = TrackEntity(
            id = "t1",
            title = "Track 1",
            artist = "Artist",
            artistId = "a1",
            albumId = "al1",
            coverArtUrl = "",
            durationSeconds = 200,
            isDownloaded = true,
            cachedFilePath = null,
        )
        val t2 = TrackEntity(
            id = "t2",
            title = "Track 2",
            artist = "Artist",
            artistId = "a1",
            albumId = "al1",
            coverArtUrl = "",
            durationSeconds = 180,
            isDownloaded = false,
            cachedFilePath = null,
        )
        coEvery { localSearch.search("test", any(), playableOnly = false) } returns LocalSearchHit(tracks = listOf(t1, t2))
        coEvery { trackDao.getTracksByIds(listOf("t1", "t2")) } returns listOf(t1)
        coEvery { repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("Network error")
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                trackDao,
                metadataDao,
                mockk(relaxed = true), // playlistDao
                localSearch,
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                mockk<OfflineModeManager>(relaxed = true),
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        viewModel.onQueryChanged("test")
        viewModel.setFilterDownloaded(true)
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.state.first { !it.isLoading }
        assertTrue(state.hasSearched)
        assertEquals(2, state.allTracks.size)
        assertEquals(1, state.tracks.size)
        assertEquals("t1", state.tracks[0].id)
    }

    @Test
    fun `loadMoreSearchResults skips when local-only`() = runTest(testDispatcher) {
        NetworkAvailabilityHolder.resetForTests(false)
        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true), // playlistDao
                mockk(relaxed = true), // localSearch
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                mockk<OfflineModeManager>(relaxed = true),
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        viewModel.onQueryChanged("more")
        viewModel.loadMoreSearchResults()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.search(any(), any(), any(), any()) }
    }

    @Test
    fun `search short query is no-op`() = runTest(testDispatcher) {
        viewModel.onQueryChanged("a")
        viewModel.search()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.search(any(), any(), any(), any()) }
        assertFalse(viewModel.state.value.hasSearched)
    }

    @Test
    fun `setFilterType updates state`() = runTest(testDispatcher) {
        viewModel.setFilterType(SearchFilterType.SONGS)
        assertEquals(SearchFilterType.SONGS, viewModel.state.value.filterType)
    }

    @Test
    fun `setFilterDownloaded applies and clears when online`() = runTest(testDispatcher) {
        NetworkAvailabilityHolder.resetForTests(true)
        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true), // playlistDao
                mockk(relaxed = true), // localSearch
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                mockk<OfflineModeManager>(relaxed = true),
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        // Seed state via search failure path with empty results
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns SearchResults(
            tracks = listOf(Track("t1", "A", duration = 1), Track("t2", "B", duration = 1)),
        )
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.searchAllTracks(any()) } returns emptyList()
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "t1", title = "A", artist = "", coverArtUrl = "", isDownloaded = true),
        )
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                trackDao,
                metadataDao,
                mockk(relaxed = true), // playlistDao
                mockk(relaxed = true), // localSearch
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                mockk(relaxed = true),
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        viewModel.onQueryChanged("ab")
        viewModel.search()
        advanceUntilIdle()
        viewModel.setFilterDownloaded(true)
        assertTrue(viewModel.state.value.filterDownloaded)
        assertEquals(1, viewModel.state.value.tracks.size)
        viewModel.setFilterDownloaded(false)
        assertFalse(viewModel.state.value.filterDownloaded)
        assertEquals(2, viewModel.state.value.tracks.size)
    }

    @Test
    fun `search unions local artist when server returns other artists`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val localSearch = mockk<LocalSearchRepository>()
        coEvery { localSearch.search("rare", any(), playableOnly = false) } returns LocalSearchHit(
            artists = listOf(
                com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity(id = "local-x", name = "Rare Artist X"),
            ),
        )
        val serverArtists = (1..20).map { Artist("s$it", "Server Artist $it") }
        coEvery {
            repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns SearchResults(artists = serverArtists)

        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                trackDao,
                metadataDao,
                mockk(relaxed = true), // playlistDao
                localSearch,
                mockk(relaxed = true), // searchIndexRebuilder
                mockk(relaxed = true), // metadataSyncWorker
                api,
                offlineManager,
                mockk(relaxed = true), // musicBrainz
                mockk(relaxed = true), // lastFm
            )
        viewModel.onQueryChanged("rare")
        viewModel.search()
        advanceUntilIdle()

        val artists = viewModel.state.value.artists
        assertTrue("local artist must survive server page", artists.any { it.id == "local-x" })
        assertTrue(artists.size >= 21)
    }
}
