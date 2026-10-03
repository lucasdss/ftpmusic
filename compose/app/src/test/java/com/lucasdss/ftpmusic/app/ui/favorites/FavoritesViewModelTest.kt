package com.lucasdss.ftpmusic.app.ui.favorites

import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
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
    private val playbackManager: PlaybackManager = mockk(relaxed = true)
    private val authHelper: SubsonicAuthHelper = mockk(relaxed = true)

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
        coEvery { trackDao.getStarred(any(), any()) } returns emptyList()
        coEvery { metadataDao.getStarredAlbums(any(), any()) } returns emptyList()
        coEvery { metadataDao.getStarredArtists(any(), any()) } returns emptyList()
        coEvery { trackDao.getDisliked(any(), any()) } returns emptyList()
        coEvery { metadataDao.getDislikedAlbums(any(), any()) } returns emptyList()
        coEvery { metadataDao.getDislikedArtists(any(), any()) } returns emptyList()
        coEvery { radioFavoriteDao.getAll() } returns emptyList()
        every { authHelper.buildStreamUrl(any(), any(), any(), any()) } returns "http://stream"
        viewModel = FavoritesViewModel(
            trackDao,
            favoriteRepository,
            metadataDao,
            radioFavoriteDao,
            storage,
            playbackManager,
            authHelper,
        )
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
        // observe() uses Flow as invalidation signal then refreshes via suspend queries
        coEvery { trackDao.getStarred(any(), any()) } answers {
            val limit = firstArg<Int>()
            if (limit == 1) emptyList() else listOf(TrackEntity(id = "t1", title = "Arcade Heart"))
        }
        coEvery { radioFavoriteDao.getAll() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                stationId = "st-1",
                name = "Retro",
                streamUrl = "https://s",
            ),
        )

        // Recreate so init.observe picks up the stubs above.
        viewModel = FavoritesViewModel(
            trackDao,
            favoriteRepository,
            metadataDao,
            radioFavoriteDao,
            storage,
            playbackManager,
            authHelper,
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.tracks.size)
        assertEquals(1, viewModel.state.value.radio.size)

        // New emission (e.g. an artist like from another screen) → live update.
        coEvery { metadataDao.getStarredArtists(any(), any()) } answers {
            val limit = firstArg<Int>()
            if (limit == 1) {
                emptyList()
            } else {
                listOf(com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-1", name = "Neon Circuit"))
            }
        }
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
        // Probe past window → hasMoreTracks
        coEvery { trackDao.getStarred(1, 50) } returns listOf(TrackEntity(id = "probe", title = "P"))
        viewModel.load()
        advanceUntilIdle()
        assertEquals(50, viewModel.state.value.tracks.size)
        assertTrue(viewModel.state.value.hasMoreLikedTracks)

        coEvery { trackDao.getStarred(50, 50) } returns page1
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(51, viewModel.state.value.tracks.size)
        assertEquals("t51", viewModel.state.value.tracks.last().id)
        assertFalse(viewModel.state.value.hasMoreLikedTracks)
    }

    @Test
    fun `per-entity hasMore albums full tracks short`() = runTest {
        val albums = (1..50).map {
            com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "a$it", name = "A$it")
        }
        coEvery { trackDao.getStarred(50, 0) } returns listOf(TrackEntity(id = "t1", title = "Only"))
        coEvery { trackDao.getStarred(1, 1) } returns emptyList()
        coEvery { metadataDao.getStarredAlbums(50, 0) } returns albums
        coEvery { metadataDao.getStarredAlbums(1, 50) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "a51", name = "More"),
        )
        viewModel.load()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hasMoreLikedTracks)
        assertTrue(viewModel.state.value.hasMoreLikedAlbums)
        assertTrue(viewModel.state.value.hasMoreLiked)
    }

    @Test
    fun `refresh preserves appended pages`() = runTest {
        val page0 = (1..50).map { TrackEntity(id = "t$it", title = "T$it") }
        val page1 = listOf(TrackEntity(id = "t51", title = "T51"))
        val starredFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<TrackEntity>>(extraBufferCapacity = 1)
        every { trackDao.getStarredFlow(50, 0) } returns starredFlow
        // Rebuild VM with emitting flow
        viewModel = FavoritesViewModel(
            trackDao,
            favoriteRepository,
            metadataDao,
            radioFavoriteDao,
            storage,
            playbackManager,
            authHelper,
        )
        coEvery { trackDao.getStarred(50, 0) } returns page0
        coEvery { trackDao.getStarred(1, 50) } returns listOf(TrackEntity(id = "probe", title = "P"))
        viewModel.load()
        advanceUntilIdle()
        coEvery { trackDao.getStarred(50, 50) } returns page1
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(51, viewModel.state.value.tracks.size)

        // Flow tick → refresh window of 51, must keep t51
        val window51 = page0 + page1
        coEvery { trackDao.getStarred(51, 0) } returns window51
        coEvery { trackDao.getStarred(1, 51) } returns emptyList()
        starredFlow.tryEmit(page0)
        advanceUntilIdle()
        assertEquals(51, viewModel.state.value.tracks.size)
        assertEquals("t51", viewModel.state.value.tracks.last().id)
    }

    @Test
    fun `unstar pending survives stale refresh`() = runTest {
        val tracks = listOf(
            TrackEntity(id = "1", title = "Track 1"),
            TrackEntity(id = "2", title = "Track 2"),
        )
        coEvery { trackDao.getStarred(50, 0) } returns tracks
        coEvery { trackDao.getStarred(1, 2) } returns emptyList()
        viewModel.load()
        advanceUntilIdle()
        coEvery { favoriteRepository.unstarTrack("1") } coAnswers { kotlinx.coroutines.delay(10_000) }
        viewModel.unstarTrack("1")
        assertEquals(1, viewModel.state.value.tracks.size)
        // Stale Room still returns both — pending Neutral filters id 1
        coEvery { trackDao.getStarred(any(), any()) } answers {
            val limit = firstArg<Int>()
            if (limit == 1) emptyList() else tracks
        }
        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.tracks.none { it.id == "1" })
        assertEquals(1, viewModel.state.value.tracks.size)
    }

    @Test
    fun `playTrack passes full liked list and startIndex`() = runTest {
        val tracks = listOf(
            TrackEntity(id = "t1", title = "One"),
            TrackEntity(id = "t2", title = "Two"),
            TrackEntity(id = "t3", title = "Three"),
        )
        coEvery { trackDao.getStarred(50, 0) } returns tracks
        viewModel.load()
        advanceUntilIdle()

        viewModel.playTrack(1)

        verify {
            playbackManager.playAlbum(
                match { it.size == 3 && it[0].id == "t1" && it[1].id == "t2" && it[2].id == "t3" },
                match { it.size == 3 },
                eq(1),
                eq(false),
            )
        }
        verify(exactly = 0) { playbackManager.playSingleTrack(any(), any()) }
    }

    @Test
    fun `playTrack no-op on out-of-bounds index`() = runTest {
        coEvery { trackDao.getStarred(50, 0) } returns listOf(TrackEntity(id = "t1", title = "One"))
        viewModel.load()
        advanceUntilIdle()
        viewModel.playTrack(5)
        verify(exactly = 0) { playbackManager.playAlbum(any(), any(), startIndex = any()) }
    }

    @Test
    fun `playTrack uses disliked list when in disliked mode`() = runTest {
        val disliked = listOf(
            TrackEntity(id = "d1", title = "D1", isDisliked = true),
            TrackEntity(id = "d2", title = "D2", isDisliked = true),
        )
        coEvery { trackDao.getDisliked(50, 0) } returns disliked
        viewModel.load()
        advanceUntilIdle()
        viewModel.setMode(FavoritesMode.DISLIKED)

        viewModel.playTrack(0)

        verify {
            playbackManager.playAlbum(
                match { it.size == 2 && it[0].id == "d1" && it[1].id == "d2" },
                match { it.size == 2 },
                eq(0),
                eq(false),
            )
        }
    }

    @Test
    fun `loadMoreDisliked appends next page`() = runTest {
        val page0 = (1..50).map {
            TrackEntity(id = "d$it", title = "D$it", isDisliked = true, dislikedAt = it.toLong())
        }
        coEvery { trackDao.getDisliked(50, 0) } returns page0
        coEvery { trackDao.getDisliked(1, 50) } returns listOf(
            TrackEntity(id = "probe", title = "P", isDisliked = true, dislikedAt = 99L),
        )
        viewModel.load()
        advanceUntilIdle()
        viewModel.setMode(FavoritesMode.DISLIKED)
        assertTrue(viewModel.state.value.hasMoreDislikedTracks)

        coEvery { trackDao.getDisliked(50, 50) } returns listOf(
            TrackEntity(id = "d51", title = "D51", isDisliked = true, dislikedAt = 51L),
        )
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(51, viewModel.state.value.dislikedTracks.size)
        assertFalse(viewModel.state.value.hasMoreDislikedTracks)
    }
}
