package com.lucasdss.ftpmusic.app.ui.favorites

import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val favoriteRepository: FavoriteRepository = mockk(relaxed = true)
    private val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao = mockk(relaxed = true)
    private val radioFavoriteDao: com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao = mockk(relaxed = true)
    private val storage: com.lucasdss.ftpmusic.app.data.security.SecureStorage = mockk(relaxed = true)

    private lateinit var viewModel: FavoritesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = FavoritesViewModel(trackDao, favoriteRepository, metadataDao, radioFavoriteDao, storage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load should populate tracks from TrackDao`() = runTest {
        val tracks = listOf(
            TrackEntity(id = "1", title = "Track 1", artist = "Artist A"),
            TrackEntity(id = "2", title = "Track 2", artist = "Artist B"),
        )
        coEvery { trackDao.getStarred(50) } returns tracks

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.tracks.size)
        assertEquals("Track 1", state.tracks[0].title)
    }

    @Test
    fun `load should handle empty starred list`() = runTest {
        coEvery { trackDao.getStarred(50) } returns emptyList()

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.tracks.isEmpty())
    }

    @Test
    fun `load should handle TrackDao exception gracefully`() = runTest {
        coEvery { trackDao.getStarred(50) } throws RuntimeException("DB error")

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.tracks.isEmpty())
    }

    @Test
    fun `unstarTrack should remove track optimistically`() = runTest {
        val tracks = listOf(
            TrackEntity(id = "1", title = "Track 1"),
            TrackEntity(id = "2", title = "Track 2"),
        )
        coEvery { trackDao.getStarred(50) } returns tracks
        coEvery { favoriteRepository.unstarTrack("1") } just Runs

        viewModel.load()
        advanceUntilIdle()

        viewModel.unstarTrack("1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.tracks.size)
        assertEquals("2", state.tracks[0].id)
    }

    @Test
    fun `unstarTrack should restore track on API failure`() = runTest {
        val tracks = listOf(
            TrackEntity(id = "1", title = "Track 1"),
            TrackEntity(id = "2", title = "Track 2"),
        )
        coEvery { trackDao.getStarred(50) } returns tracks
        coEvery { favoriteRepository.unstarTrack("1") } throws RuntimeException("Network error")

        viewModel.load()
        advanceUntilIdle()

        viewModel.unstarTrack("1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.tracks.size)
        assertTrue(state.tracks.any { it.id == "1" })
    }

    @Test
    fun `unstarTrack should not crash on non-existent track`() = runTest {
        coEvery { trackDao.getStarred(50) } returns emptyList()
        coEvery { favoriteRepository.unstarTrack("nonexistent") } just Runs

        viewModel.load()
        advanceUntilIdle()

        viewModel.unstarTrack("nonexistent")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.tracks.isEmpty())
    }

    @Test
    fun `initial state should have empty tracks`() {
        val state = viewModel.state.value
        assertTrue(state.tracks.isEmpty())
    }

    // ── v43: Entity-level sections (albums / artists / radio) ─────────────

    @Test
    fun `load populates albums artists and radio sections`() = runTest {
        coEvery { trackDao.getStarred(50) } returns emptyList()
        coEvery { metadataDao.getStarredAlbums(50) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "al-1", name = "Album One", artist = "Artist A"),
        )
        coEvery { metadataDao.getStarredArtists(50) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-1", name = "Artist A"),
        )
        coEvery { radioFavoriteDao.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                stationId = "st-1",
                name = "Retro",
                streamUrl = "https://s",
            ),
        )

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.albums.size)
        assertEquals(1, state.artists.size)
        assertEquals(1, state.radio.size)
        assertTrue(state.showFavAlbumsSection)
    }

    @Test
    fun `load reads section visibility toggles from storage`() = runTest {
        coEvery { trackDao.getStarred(50) } returns emptyList()
        every { storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_HOME_SHOW_FAV_ALBUMS) } returns
            "false"

        viewModel.load()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showFavAlbumsSection)
        assertTrue(viewModel.state.value.showFavArtistsSection)
    }

    @Test
    fun `unlikeAlbum removes album optimistically`() = runTest {
        val albums = listOf(
            com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "al-1", name = "Album One"),
            com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "al-2", name = "Album Two"),
        )
        coEvery { metadataDao.getStarredAlbums(50) } returns albums
        coEvery { favoriteRepository.unlikeAlbum("al-1") } just Runs

        viewModel.load()
        advanceUntilIdle()

        viewModel.unlikeAlbum("al-1")
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.albums.size)
        assertEquals("al-2", viewModel.state.value.albums[0].id)
    }

    @Test
    fun `unlikeAlbum restores on API failure`() = runTest {
        val albums = listOf(
            com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "al-1", name = "Album One"),
        )
        coEvery { metadataDao.getStarredAlbums(50) } returns albums
        coEvery { favoriteRepository.unlikeAlbum("al-1") } throws RuntimeException("Network error")

        viewModel.load()
        advanceUntilIdle()

        viewModel.unlikeAlbum("al-1")
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.albums.size)
    }

    @Test
    fun `unlikeArtist removes artist optimistically`() = runTest {
        coEvery { metadataDao.getStarredArtists(50) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-1", name = "Artist One"),
        )
        coEvery { favoriteRepository.unlikeArtist("ar-1") } just Runs

        viewModel.load()
        advanceUntilIdle()

        viewModel.unlikeArtist("ar-1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.artists.isEmpty())
    }

    @Test
    fun `unbookmarkRadio removes station optimistically`() = runTest {
        coEvery { radioFavoriteDao.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                stationId = "st-1",
                name = "Retro",
                streamUrl = "https://s",
            ),
        )
        coEvery { favoriteRepository.unbookmarkRadio("st-1") } just Runs

        viewModel.load()
        advanceUntilIdle()

        viewModel.unbookmarkRadio("st-1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.radio.isEmpty())
    }

    // ── v45: reactive observation ────────────────────────────────────────

    @Test
    fun `observe updates tracks and radio sections live`() = runTest {
        val albumsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<com.lucasdss.ftpmusic.app.data.db.AlbumEntity>>(
            emptyList(),
        )
        val artistsFlow =
            kotlinx.coroutines.flow.MutableStateFlow<List<com.lucasdss.ftpmusic.app.data.db.ArtistEntity>>(
                emptyList(),
            )
        coEvery { trackDao.getStarredFlow(50) } returns kotlinx.coroutines.flow.flowOf(
            listOf(com.lucasdss.ftpmusic.app.data.db.TrackEntity(id = "t1", title = "Arcade Heart")),
        )
        coEvery { metadataDao.getStarredAlbumsFlow(50) } returns albumsFlow
        coEvery { metadataDao.getStarredArtistsFlow(50) } returns artistsFlow
        coEvery { radioFavoriteDao.getAllFlow() } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                    stationId = "st-1",
                    name = "Retro",
                    streamUrl = "https://s",
                ),
            ),
        )

        viewModel.observe()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.tracks.size)
        assertEquals(1, viewModel.state.value.radio.size)

        // New emission (e.g. an artist like from another screen) → live update.
        artistsFlow.value = listOf(com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-1", name = "Neon Circuit"))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.artists.size)
    }
}
