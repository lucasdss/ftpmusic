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
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
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
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                api,
                mockk<OfflineModeManager>(relaxed = true),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onQueryChanged sets query and clears results`() = runTest(testDispatcher) {
        viewModel.onQueryChanged("test")
        val state = viewModel.state.first { it.query == "test" }
        assertTrue(state.artists.isEmpty())
        assertFalse(state.hasSearched)
        assertEquals("test", state.query)
    }

    @Test
    fun `search triggers API call and populates results`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search("rock", "user", "pass", any()) } returns SearchResults(
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
        coEvery { repository.search("test", "user", "pass", any()) } returns SearchResults(
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
        coEvery { repository.search("pink floyd", any(), any(), any()) } returns SearchResults()

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
        coEvery { repository.search(any(), "user", "pass", any()) } returns SearchResults()
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
        coEvery { repository.search(any(), "user", "pass", any()) } returns SearchResults()

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
        coEvery { repository.search(any(), "user", "pass", any()) } returns SearchResults()
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
    fun `search skips API call when offline and shows cached results`() = runTest(testDispatcher) {
        val offlineFlow = MutableStateFlow(true)
        val offlineManager = mockk<OfflineModeManager>()
        every { offlineManager.isOffline } returns offlineFlow
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                api,
                offlineManager,
            )
        viewModel.onQueryChanged("test")
        viewModel.search()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.search(any(), any(), any(), any()) }
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `catch block reapplies download filter on API failure`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { repository.search(any(), any(), any(), any()) } throws RuntimeException("Network error")

        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                mockk(relaxed = true),
                mockk(relaxed = true),
                api,
                mockk<OfflineModeManager>(relaxed = true),
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
        val trackDao = mockk<TrackDao>()
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>()
        coEvery { trackDao.searchAllTracks("test") } returns listOf(
            TrackEntity(
                id = "t1",
                title = "Track 1",
                artist = "Artist",
                artistId = "a1",
                albumId = "al1",
                coverArtUrl = "",
                durationSeconds = 200,
                isDownloaded = true,
                cachedFilePath = null,
            ),
            TrackEntity(
                id = "t2",
                title = "Track 2",
                artist = "Artist",
                artistId = "a1",
                albumId = "al1",
                coverArtUrl = "",
                durationSeconds = 180,
                isDownloaded = false,
                cachedFilePath = null,
            ),
        )
        coEvery { trackDao.getTracksByIds(listOf("t1", "t2")) } returns listOf(
            TrackEntity(
                id = "t1",
                title = "Track 1",
                artist = "Artist",
                artistId = "a1",
                albumId = "al1",
                coverArtUrl = "",
                durationSeconds = 200,
                isDownloaded = true,
                cachedFilePath = null,
            ),
        )
        coEvery { metadataDao.searchAlbums("test") } returns emptyList()
        coEvery { metadataDao.searchArtists("test") } returns emptyList()
        coEvery { repository.search(any(), any(), any(), any()) } throws RuntimeException("Network error")
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                trackDao,
                metadataDao,
                api,
                mockk<OfflineModeManager>(relaxed = true),
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
}
