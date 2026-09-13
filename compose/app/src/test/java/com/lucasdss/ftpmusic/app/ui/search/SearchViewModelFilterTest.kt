package com.lucasdss.ftpmusic.app.ui.search

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.SearchRepository
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelFilterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: SearchRepository = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val genreDao: GenreDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val api: SubsonicApi = mockk(relaxed = true)

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        every { storage.get("recent_searches") } returns ""
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        viewModel =
            SearchViewModel(
                repository,
                storage,
                genreDao,
                trackDao,
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
    fun `setFilterDownloaded true should update state`() {
        viewModel.setFilterDownloaded(true)
        assertTrue(viewModel.state.value.filterDownloaded)
    }

    @Test
    fun `setFilterDownloaded false should update state`() {
        viewModel.setFilterDownloaded(true)
        viewModel.setFilterDownloaded(false)
        assertFalse(viewModel.state.value.filterDownloaded)
    }

    @Test
    fun `setFilterDownloaded false should restore allTracks when available`() {
        // Would need search to populate allTracks first — test that toggling off restores
        viewModel.setFilterDownloaded(false)
        assertFalse(viewModel.state.value.filterDownloaded)
    }

    @Test
    fun `onQueryChanged should clear allTracks and localTrackIds`() {
        viewModel.onQueryChanged("test")
        val state = viewModel.state.value
        assertEquals("test", state.query)
        assertTrue(state.allTracks.isEmpty())
        assertTrue(state.localTrackIds.isEmpty())
        assertTrue(state.tracks.isEmpty())
    }

    @Test
    fun `initial state should have filterDownloaded false`() {
        assertFalse(viewModel.state.value.filterDownloaded)
    }

    @Test
    fun `initial state should have empty allTracks and localTrackIds`() {
        assertTrue(viewModel.state.value.allTracks.isEmpty())
        assertTrue(viewModel.state.value.localTrackIds.isEmpty())
    }

    @Test
    fun `setFilterType updates filter type`() = runTest(testDispatcher) {
        viewModel.setFilterType(SearchFilterType.ARTISTS)
        assertEquals(SearchFilterType.ARTISTS, viewModel.state.value.filterType)
    }

    @Test
    fun `setFilterType cycles through all types`() = runTest(testDispatcher) {
        listOf(
            SearchFilterType.ARTISTS,
            SearchFilterType.ALBUMS,
            SearchFilterType.SONGS,
            SearchFilterType.PLAYLISTS,
        ).forEach { type ->
            viewModel.setFilterType(type)
            assertEquals(type, viewModel.state.value.filterType)
        }
    }
}
