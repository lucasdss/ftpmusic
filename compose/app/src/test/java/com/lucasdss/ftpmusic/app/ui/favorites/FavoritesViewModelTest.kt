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
        // SharedFlows with no replay: observe() waits; load() tests keep their state.
        every { trackDao.getStarredFlow(50, 0) } returns
            kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 1)
        every { metadataDao.getStarredAlbumsFlow(50, 0) } returns
            kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 1)
        every { metadataDao.getStarredArtistsFlow(50, 0) } returns
            kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 1)
        every { radioFavoriteDao.getAllFlow() } returns
            kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 1)
        every { trackDao.getDislikedFlow(50, 0) } returns
            kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 1)
        every { metadataDao.getDislikedAlbumsFlow(50, 0) } returns
            kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 1)
        every { metadataDao.getDislikedArtistsFlow(50, 0) } returns
            kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 1)
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
        coEvery { trackDao.getStarred(50, 0) } returns tracks

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.tracks.size)
        assertEquals("Track 1", state.tracks[0].title)
    }

    @Test
    fun `load should handle empty starred list`() = runTest {
        coEvery { trackDao.getStarred(50, 0) } returns emptyList()

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.tracks.isEmpty())
    }

    @Test
    fun `load should handle TrackDao exception gracefully`() = runTest {
        coEvery { trackDao.getStarred(50, 0) } throws RuntimeException("DB error")

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
        coEvery { trackDao.getStarred(50, 0) } returns tracks
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
        coEvery { trackDao.getStarred(50, 0) } returns tracks
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
        coEvery { trackDao.getStarred(50, 0) } returns emptyList()
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
        coEvery { trackDao.getStarred(50, 0) } returns emptyList()
        coEvery { metadataDao.getStarredAlbums(50, 0) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "al-1", name = "Album One", artist = "Artist A"),
        )
        coEvery { metadataDao.getStarredArtists(50, 0) } returns listOf(
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
        coEvery { trackDao.getStarred(50, 0) } returns emptyList()
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
        coEvery { metadataDao.getStarredAlbums(50, 0) } returns albums
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
        coEvery { metadataDao.getStarredAlbums(50, 0) } returns albums
        coEvery { favoriteRepository.unlikeAlbum("al-1") } throws RuntimeException("Network error")

        viewModel.load()
        advanceUntilIdle()

        viewModel.unlikeAlbum("al-1")
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.albums.size)
    }

    @Test
    fun `unlikeArtist removes artist optimistically`() = runTest {
        coEvery { metadataDao.getStarredArtists(50, 0) } returns listOf(
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
        every { trackDao.getStarredFlow(50, 0) } returns kotlinx.coroutines.flow.flowOf(
            listOf(com.lucasdss.ftpmusic.app.data.db.TrackEntity(id = "t1", title = "Arcade Heart")),
        )
        every { metadataDao.getStarredAlbumsFlow(50, 0) } returns albumsFlow
        every { metadataDao.getStarredArtistsFlow(50, 0) } returns artistsFlow
        every { radioFavoriteDao.getAllFlow() } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                    stationId = "st-1",
                    name = "Retro",
                    streamUrl = "https://s",
                ),
            ),
        )
        every { trackDao.getDislikedFlow(50, 0) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        every { metadataDao.getDislikedAlbumsFlow(50, 0) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        every { metadataDao.getDislikedArtistsFlow(50, 0) } returns kotlinx.coroutines.flow.flowOf(emptyList())

        // Recreate so init.observe picks up the stubs above.
        viewModel = FavoritesViewModel(trackDao, favoriteRepository, metadataDao, radioFavoriteDao, storage)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.tracks.size)
        assertEquals(1, viewModel.state.value.radio.size)

        // New emission (e.g. an artist like from another screen) → live update.
        artistsFlow.value = listOf(com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-1", name = "Neon Circuit"))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.artists.size)
    }

    @Test
    fun `setMode switches Liked and Disliked`() = runTest {
        assertEquals(FavoritesMode.LIKED, viewModel.state.value.mode)
        viewModel.setMode(FavoritesMode.DISLIKED)
        assertEquals(FavoritesMode.DISLIKED, viewModel.state.value.mode)
        viewModel.setMode(FavoritesMode.LIKED)
        assertEquals(FavoritesMode.LIKED, viewModel.state.value.mode)
    }

    @Test
    fun `load populates disliked lists`() = runTest {
        coEvery { trackDao.getDisliked(50, 0) } returns listOf(
            TrackEntity(id = "d1", title = "Downer", isDisliked = true, dislikedAt = 100L),
        )
        coEvery { metadataDao.getDislikedAlbums(50, 0) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.AlbumEntity(
                id = "al-d",
                name = "Bad Album",
                isDisliked = true,
                dislikedAt = 90L,
            ),
        )
        coEvery { metadataDao.getDislikedArtists(50, 0) } returns emptyList()

        viewModel.load()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.dislikedTracks.size)
        assertEquals("Downer", viewModel.state.value.dislikedTracks[0].title)
        assertEquals(1, viewModel.state.value.dislikedAlbums.size)
    }

    @Test
    fun `clearDislikeTrack removes optimistically and rolls back on failure`() = runTest {
        coEvery { trackDao.getDisliked(50, 0) } returns listOf(
            TrackEntity(id = "d1", title = "Downer", isDisliked = true, dislikedAt = 1L),
            TrackEntity(id = "d2", title = "Worse", isDisliked = true, dislikedAt = 2L),
        )
        viewModel.load()
        advanceUntilIdle()

        coEvery { favoriteRepository.clearDislikeTrack("d1") } throws RuntimeException("db")
        viewModel.clearDislikeTrack("d1")
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.dislikedTracks.size)

        coEvery { favoriteRepository.clearDislikeTrack("d1") } just Runs
        viewModel.clearDislikeTrack("d1")
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.dislikedTracks.size)
        assertEquals("d2", viewModel.state.value.dislikedTracks[0].id)
    }

    @Test
    fun `loadMoreLiked appends next page and clears hasMore on short page`() = runTest {
        val page0 = (1..50).map { TrackEntity(id = "t$it", title = "T$it") }
        val page1 = listOf(TrackEntity(id = "t51", title = "T51"))
        coEvery { trackDao.getStarred(50, 0) } returns page0
        coEvery { metadataDao.getStarredAlbums(50, 0) } returns emptyList()
        coEvery { metadataDao.getStarredArtists(50, 0) } returns emptyList()
        coEvery { trackDao.getDisliked(50, 0) } returns emptyList()
        coEvery { metadataDao.getDislikedAlbums(50, 0) } returns emptyList()
        coEvery { metadataDao.getDislikedArtists(50, 0) } returns emptyList()
        coEvery { radioFavoriteDao.getAll() } returns emptyList()
        viewModel.load()
        advanceUntilIdle()
        assertEquals(50, viewModel.state.value.tracks.size)
        assertTrue(viewModel.state.value.hasMoreLiked)

        coEvery { trackDao.getStarred(50, 50) } returns page1
        coEvery { metadataDao.getStarredAlbums(50, 50) } returns emptyList()
        coEvery { metadataDao.getStarredArtists(50, 50) } returns emptyList()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(51, viewModel.state.value.tracks.size)
        assertEquals("t51", viewModel.state.value.tracks.last().id)
        assertFalse(viewModel.state.value.hasMoreLiked)
    }

    @Test
    fun `loadMoreDisliked appends next page`() = runTest {
        val page0 = (1..50).map {
            TrackEntity(id = "d$it", title = "D$it", isDisliked = true, dislikedAt = it.toLong())
        }
        coEvery { trackDao.getStarred(50, 0) } returns emptyList()
        coEvery { metadataDao.getStarredAlbums(50, 0) } returns emptyList()
        coEvery { metadataDao.getStarredArtists(50, 0) } returns emptyList()
        coEvery { trackDao.getDisliked(50, 0) } returns page0
        coEvery { metadataDao.getDislikedAlbums(50, 0) } returns emptyList()
        coEvery { metadataDao.getDislikedArtists(50, 0) } returns emptyList()
        coEvery { radioFavoriteDao.getAll() } returns emptyList()
        viewModel.load()
        advanceUntilIdle()
        viewModel.setMode(FavoritesMode.DISLIKED)
        assertTrue(viewModel.state.value.hasMoreDisliked)

        coEvery { trackDao.getDisliked(50, 50) } returns listOf(
            TrackEntity(id = "d51", title = "D51", isDisliked = true, dislikedAt = 51L),
        )
        coEvery { metadataDao.getDislikedAlbums(50, 50) } returns emptyList()
        coEvery { metadataDao.getDislikedArtists(50, 50) } returns emptyList()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(51, viewModel.state.value.dislikedTracks.size)
        assertFalse(viewModel.state.value.hasMoreDisliked)
    }
}
