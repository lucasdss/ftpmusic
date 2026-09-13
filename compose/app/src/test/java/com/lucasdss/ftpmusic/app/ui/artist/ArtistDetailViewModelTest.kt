package com.lucasdss.ftpmusic.app.ui.artist

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
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
class ArtistDetailViewModelTest {

    private val api: SubsonicApi = mockk()
    private val storage: SecureStorage = mockk(relaxed = true)
    private val playbackManager: PlaybackManager = mockk(relaxed = true)
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val musicBrainzService: com.lucasdss.ftpmusic.app.data.network.MusicBrainzService = mockk(relaxed = true)
    private val lastFmService: com.lucasdss.ftpmusic.app.data.network.LastFmService = mockk(relaxed = true)
    private val cacheService: com.lucasdss.ftpmusic.app.data.cache.CacheService = mockk(relaxed = true)
    private val downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager = mockk(relaxed = true)
    private val playlistDao: com.lucasdss.ftpmusic.app.data.db.PlaylistDao = mockk(relaxed = true)
    private val playlistRepo: com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository = mockk(relaxed = true)
    private val favoriteRepository: com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: ArtistDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { offlineModeManager.isOffline } returns MutableStateFlow(false)
        viewModel = ArtistDetailViewModel(
            api, storage, playbackManager, metadataDao, trackDao, offlineModeManager,
            musicBrainzService, lastFmService, cacheService, downloadManager,
            playlistDao, playlistRepo, favoriteRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildArtistResponse(name: String, albums: List<Map<String, Any?>>): Map<String, Any> = mapOf(
        "subsonic-response" to mapOf(
            "artist" to mapOf<String, Any?>(
                "id" to "ar-1",
                "name" to name,
                "album" to albums,
            ),
        ),
    )

    private fun cachedAlbum(id: String, name: String, artist: String?, year: Int? = null, coverArt: String? = null) =
        CachedAlbumEntity(id = id, name = name, artist = artist, year = year, coverArt = coverArt)

    @Test
    fun `loadArtist uses cached albums when available without API call`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(id = "ar-1", name = "Test Artist")
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns listOf(
            cachedAlbum("al-1", "Debut Album", "Test Artist", 2020, "cov-1"),
            cachedAlbum("al-2", "Second Album", "Test Artist", 2022, "cov-2"),
        )

        viewModel.loadArtist("ar-1")
        val state = viewModel.state.first { !it.isLoading && it.albums.isNotEmpty() }

        assertEquals("Test Artist", state.name)
        assertEquals(2, state.albums.size)
        assertEquals("Debut Album", state.albums[0].name)
        assertEquals(2020, state.albums[0].year)
        assertEquals("cov-1", state.albums[0].coverArt)
        assertNull(state.error)
        // Local-first: API must NOT be called when cache has data
        coVerify(exactly = 0) { api.getArtist(any(), any()) }
    }

    @Test
    fun `loadArtist falls back to API when cache is empty and online`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns null
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()

        val albumMaps = listOf(
            mapOf<String, Any?>(
                "id" to "al-1",
                "name" to "Debut Album",
                "artist" to "Test Artist",
                "year" to 2020,
                "coverArt" to "cov-1",
            ),
            mapOf<String, Any?>(
                "id" to "al-2",
                "name" to "Second Album",
                "artist" to "Test Artist",
                "year" to 2022,
                "coverArt" to "cov-2",
            ),
        )
        coEvery { api.getArtist(id = "ar-1", auth = any()) } returns buildArtistResponse("Test Artist", albumMaps)

        viewModel.loadArtist("ar-1")
        val state = viewModel.state.first { !it.isLoading && it.albums.isNotEmpty() }

        assertEquals("Test Artist", state.name)
        assertEquals(2, state.albums.size)
        coVerify(exactly = 1) { api.getArtist(id = "ar-1", auth = any()) }
    }

    @Test
    fun `loadArtist does not call API when offline and cache empty`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        every { offlineModeManager.isOffline } returns MutableStateFlow(true)
        // Artist NOT cached at all
        coEvery { metadataDao.getArtistById("ar-1") } returns null
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()

        viewModel.loadArtist("ar-1")
        val state = viewModel.state.first { it.error != null }

        assertTrue(state.albums.isEmpty())
        assertNotNull(state.error)
        coVerify(exactly = 0) { api.getArtist(any(), any()) }
    }

    @Test
    fun `loadArtist finds albums by artist name when id match is empty`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        // Artist cached, but albums have NULL artist_id (compilation case like 2Cellos)
        coEvery { metadataDao.getArtistById("ar-2c") } returns CachedArtistEntity(id = "ar-2c", name = "2Cellos")
        coEvery { metadataDao.getAlbumsByArtistId("ar-2c") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName("2Cellos") } returns listOf(
            cachedAlbum("al-1", "Celloverse", "2Cellos", 2015, "cov-1"),
        )

        viewModel.loadArtist("ar-2c")
        val state = viewModel.state.first { !it.isLoading && it.albums.isNotEmpty() }

        assertEquals("2Cellos", state.name)
        assertEquals(1, state.albums.size)
        assertEquals("Celloverse", state.albums[0].name)
        coVerify(exactly = 0) { api.getArtist(any(), any()) }
    }

    @Test
    fun `loadArtist merges id and name matched albums with dedup`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-mix") } returns CachedArtistEntity(id = "ar-mix", name = "Mix Artist")
        // ID match returns 2 albums (one also matches by name — will dedup)
        coEvery { metadataDao.getAlbumsByArtistId("ar-mix") } returns listOf(
            cachedAlbum("al-1", "Album A", "Mix Artist", 2020),
            cachedAlbum("al-2", "Album B", "Mix Artist", 2021),
        )
        // Name match returns the SAME al-2 plus a new al-3 (different artist_id but same name)
        coEvery { metadataDao.getAlbumsByArtistName("Mix Artist") } returns listOf(
            cachedAlbum("al-2", "Album B", "Mix Artist", 2021),
            cachedAlbum("al-3", "Album C", "Mix Artist", 2022),
        )

        viewModel.loadArtist("ar-mix")
        val state = viewModel.state.first { !it.isLoading && it.albums.isNotEmpty() }

        // al-1 (id only), al-2 (both — deduped), al-3 (name only) = 3 distinct
        assertEquals(3, state.albums.size)
        val ids = state.albums.map { it.id }.toSet()
        assertEquals(setOf("al-1", "al-2", "al-3"), ids)
    }

    @Test
    fun `loadArtist sets error on API failure when no cache`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("bad-id") } returns null
        coEvery { metadataDao.getAlbumsByArtistId("bad-id") } returns emptyList()
        coEvery { api.getArtist(id = "bad-id", auth = any()) } throws RuntimeException("Network error")

        viewModel.loadArtist("bad-id")
        val state = viewModel.state.first { it.error != null }

        assertNotNull(state.error)
        assertEquals("Network error", state.error)
        assertTrue(state.albums.isEmpty())
    }

    @Test
    fun `loadArtist preserves coverArt and year from cache`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(id = "ar-1", name = "Artist X")
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns listOf(
            cachedAlbum("al-1", "Album A", "Artist X", 2021, "cov-abc"),
            cachedAlbum("al-2", "Album B", "Artist X", null, null),
        )

        viewModel.loadArtist("ar-1")
        val state = viewModel.state.first { !it.isLoading && it.albums.size == 2 }

        assertEquals(2, state.albums.size)
        assertEquals("Album A", state.albums[0].name)
        assertEquals(2021, state.albums[0].year)
        assertEquals("cov-abc", state.albums[0].coverArt)
        assertEquals("Album B", state.albums[1].name)
        assertNull(state.albums[1].year)
        assertNull(state.albums[1].coverArt)
    }

    @Test
    fun `cached album count matches detail page album list`() = runTest(testDispatcher) {
        // Simulates the consistency guarantee: the artist list count (from
        // cached_artists.album_count updated by updateArtistAlbumCounts) matches
        // the number of albums returned by getAlbumsByArtistId.
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        // 9 cached albums for AC/DC (not the 11 the getArtists API reported)
        val acdcAlbums = (1..9).map { i -> cachedAlbum("al-$i", "Album $i", "AC/DC") }
        coEvery { metadataDao.getArtistById("ar-acdc") } returns CachedArtistEntity(
            id = "ar-acdc",
            name = "AC/DC",
            albumCount = 9,
        )
        coEvery { metadataDao.getAlbumsByArtistId("ar-acdc") } returns acdcAlbums

        viewModel.loadArtist("ar-acdc")
        val state = viewModel.state.first { !it.isLoading && it.albums.isNotEmpty() }

        assertEquals(
            "Detail page album count must match cached artist count",
            9,
            state.albums.size,
        )
    }

    // ── Cursor-based track pagination ────────────────────────────────────

    private fun trackEntity(id: String, title: String, artistId: String = "ar-1") =
        TrackEntity(id = id, title = title, artist = "Artist", artistId = artistId)

    @Test
    fun `loadArtist loads first page of tracks`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(id = "ar-1", name = "Test Artist")
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        // First page: 2 tracks (less than page size → hasMore=false)
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns listOf(
            trackEntity("t1", "Alpha"),
            trackEntity("t2", "Beta"),
        )

        viewModel.loadArtist("ar-1")
        val state = viewModel.state.first { !it.isLoading && it.tracks.isNotEmpty() }

        assertEquals(2, state.tracks.size)
        assertEquals("Alpha", state.tracks[0].title)
        assertEquals("Beta", state.tracks[1].title)
        assertFalse("hasMoreTracks must be false when page is not full", state.hasMoreTracks)
    }

    @Test
    fun `loadMoreTracks appends next page using cursor`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(id = "ar-1", name = "Test Artist")
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        // First page is FULL (50 tracks) → hasMore=true
        val firstPage = (1..50).map { i -> trackEntity("t$i", "Track ${i.toString().padStart(3, '0')}") }
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns firstPage
        // Second page via cursor (title > "Track 050")
        val secondPage = (51..60).map { i -> trackEntity("t$i", "Track ${i.toString().padStart(3, '0')}") }
        coEvery { trackDao.getTracksByArtistIdAfter("ar-1", "Track 050", 50) } returns secondPage

        viewModel.loadArtist("ar-1")
        val loaded = viewModel.state.first { !it.isLoading && it.tracks.size == 50 }
        assertTrue("hasMoreTracks must be true when page is full", loaded.hasMoreTracks)

        viewModel.loadMoreTracks()
        val more = viewModel.state.first { !it.isLoadingMoreTracks && it.tracks.size == 60 }

        assertEquals(60, more.tracks.size)
        assertEquals("Track 051", more.tracks[50].title)
        assertEquals("Track 060", more.tracks[59].title)
        assertFalse("hasMoreTracks must be false after short page", more.hasMoreTracks)
        coVerify { trackDao.getTracksByArtistIdAfter("ar-1", "Track 050", 50) }
    }

    @Test
    fun `loadMoreTracks is a no-op when no more tracks`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(id = "ar-1", name = "Test Artist")
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns listOf(trackEntity("t1", "Only One"))

        viewModel.loadArtist("ar-1")
        val loaded = viewModel.state.first { !it.isLoading && it.tracks.size == 1 }
        assertFalse(loaded.hasMoreTracks)

        viewModel.loadMoreTracks()
        advanceUntilIdle()

        // No extra fetch — hasMoreTracks is false
        coVerify(exactly = 1) { trackDao.getTracksByArtistId("ar-1", 50) }
        coVerify(exactly = 0) { trackDao.getTracksByArtistIdAfter(any(), any(), any()) }
    }

    @Test
    fun `playAll uses loaded tracks`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(id = "ar-1", name = "Test Artist")
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns listOf(
            trackEntity("t1", "Alpha"),
            trackEntity("t2", "Beta"),
        )

        viewModel.loadArtist("ar-1")
        val loaded = viewModel.state.first { !it.isLoading && it.tracks.size == 2 }

        viewModel.playAll()
        advanceUntilIdle()

        // playAll() now routes through tryStartContext (overwrite protection).
        verify { playbackManager.tryStartContext(any(), any(), 0, "artist", "ar-1", "Test Artist") }
    }

    // ── MusicBrainz public rating ────────────────────────────────────────

    @Test
    fun `loadArtist uses cached public rating when present`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(
            id = "ar-1",
            name = "Test Artist",
            publicRating = 4.5,
            publicRatingVotes = 80,
        )
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns emptyList()

        viewModel.loadArtist("ar-1")
        val state = viewModel.state.first { !it.isLoading && it.artistId == "ar-1" }

        assertEquals("Cached public rating must load into state", 4.5, state.publicRating!!, 0.001)
        assertEquals(80, state.publicRatingVotes)
        // Cached → no MusicBrainz fetch
        coVerify(exactly = 0) { musicBrainzService.fetchArtistRating(any()) }
    }

    @Test
    fun `loadArtist fetches public rating when not cached`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(
            id = "ar-1",
            name = "Test Artist",
            publicRating = null,
        )
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns emptyList()
        coEvery { musicBrainzService.fetchArtistRating("Test Artist") } returns
            Pair(com.lucasdss.ftpmusic.app.data.network.MusicBrainzService.MusicBrainzRating(4.2, 45), "mbid-1")
        coEvery { metadataDao.setArtistPublicRating(any(), any(), any(), any()) } returns Unit

        viewModel.loadArtist("ar-1")
        advanceUntilIdle()

        coVerify { musicBrainzService.fetchArtistRating("Test Artist") }
        coVerify { metadataDao.setArtistPublicRating("ar-1", 4.2, 45, "mbid-1") }
        val state = viewModel.state.value
        assertEquals("Fetched public rating must update state", 4.2, state.publicRating!!, 0.001)
    }

    @Test
    fun `loadArtist skips MusicBrainz fetch when offline`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        every { offlineModeManager.isOffline } returns MutableStateFlow(true)
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(
            id = "ar-1",
            name = "Test Artist",
            publicRating = null,
        )
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns emptyList()

        viewModel.loadArtist("ar-1")
        advanceUntilIdle()

        coVerify(exactly = 0) { musicBrainzService.fetchArtistRating(any()) }
    }

    // ── Similar artists (last.fm) ───────────────────────────────────────

    @Test
    fun `loadArtist loads cached similar artists`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        val similarJson =
            """[{"name":"Similar A","mbid":"mb-a","match":0.9},{"name":"Similar B","mbid":null,"match":0.7}]"""
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(
            id = "ar-1",
            name = "Test Artist",
            publicRating = 4.5,
            similarArtistsJson = similarJson,
        )
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns emptyList()
        // Simulate the real parser (org.json is stubbed in non-Robolectric tests)
        every { lastFmService.parseStoredJson(any()) } returns listOf(
            com.lucasdss.ftpmusic.app.data.network.LastFmService.SimilarArtist("Similar A", "mb-a", 0.9),
            com.lucasdss.ftpmusic.app.data.network.LastFmService.SimilarArtist("Similar B", null, 0.7),
        )

        viewModel.loadArtist("ar-1")
        val state = viewModel.state.first { !it.isLoading && it.artistId == "ar-1" }

        assertEquals(2, state.similarArtists.size)
        assertEquals("Similar A", state.similarArtists[0].name)
        assertEquals("mb-a", state.similarArtists[0].mbid)
        assertEquals(0.9, state.similarArtists[0].match!!, 0.001)
        assertNull(state.similarArtists[1].mbid)
        // Cached → no last.fm fetch
        coVerify(exactly = 0) { lastFmService.fetchSimilarArtists(any()) }
    }

    @Test
    fun `loadArtist fetches and persists similar artists when not cached`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(
            id = "ar-1",
            name = "Test Artist",
            publicRating = 4.5,
            similarArtistsJson = null,
        )
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns emptyList()
        coEvery { lastFmService.fetchSimilarArtists("Test Artist") } returns listOf(
            com.lucasdss.ftpmusic.app.data.network.LastFmService.SimilarArtist("Similar A", "mb-a", 0.9),
        )
        coEvery { metadataDao.setArtistSimilarArtists(any(), any()) } returns Unit

        viewModel.loadArtist("ar-1")
        advanceUntilIdle()

        coVerify { lastFmService.fetchSimilarArtists("Test Artist") }
        coVerify { metadataDao.setArtistSimilarArtists("ar-1", any()) }
        assertEquals(1, viewModel.state.value.similarArtists.size)
        assertEquals("Similar A", viewModel.state.value.similarArtists[0].name)
    }

    @Test
    fun `loadArtist skips lastfm fetch when offline`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        every { offlineModeManager.isOffline } returns MutableStateFlow(true)
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(
            id = "ar-1",
            name = "Test Artist",
            publicRating = 4.5,
            similarArtistsJson = null,
        )
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns emptyList()

        viewModel.loadArtist("ar-1")
        advanceUntilIdle()

        coVerify(exactly = 0) { lastFmService.fetchSimilarArtists(any()) }
    }

    @Test
    fun `loadArtist finds albums via track artist attribution`() = runTest(testDispatcher) {
        // Simulates Navidrome: album artist is "Original Soundtrack" but tracks
        // are by the actual artist (e.g., Aerosmith). The album appears via the
        // track-level artist_id fallback query.
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-aero") } returns CachedArtistEntity(id = "ar-aero", name = "Aerosmith")
        coEvery { metadataDao.getAlbumsByArtistId("ar-aero") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName("Aerosmith") } returns emptyList()
        // Track-artist fallback finds the albums
        coEvery { metadataDao.getAlbumsByTrackArtistId("ar-aero") } returns listOf(
            cachedAlbum("al-1", "Draw the Line", "Original Soundtrack", 1977),
            cachedAlbum("al-2", "Permanent Vacation", "Original Soundtrack", 1987),
        )
        coEvery { trackDao.getTracksByArtistId("ar-aero", 50) } returns emptyList()

        viewModel.loadArtist("ar-aero")
        val state = viewModel.state.first { !it.isLoading && it.albums.isNotEmpty() }

        assertEquals("Aerosmith", state.name)
        assertEquals(2, state.albums.size)
        assertEquals("Draw the Line", state.albums[0].name)
        assertEquals("Original Soundtrack", state.albums[0].artist)
        coVerify { metadataDao.getAlbumsByTrackArtistId("ar-aero") }
    }

    // ── Queue action parity (Play Next / Add to Queue) ─────────────────

    private suspend fun seedTracksForQueue() {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(id = "ar-1", name = "Test Artist")
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName("Test Artist") } returns emptyList()
        coEvery { metadataDao.getAlbumsByTrackArtistId("ar-1") } returns emptyList()
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns listOf(
            te("t1", "One"),
            te("t2", "Two"),
        )
        viewModel.loadArtist("ar-1")
        viewModel.state.first { it.tracks.size == 2 }
    }

    private fun te(id: String, title: String) =
        com.lucasdss.ftpmusic.app.data.db.TrackEntity(id = id, title = title, artist = "Artist", coverArtUrl = "ca-$id")

    @Test
    fun `playNextAll inserts tracks reversed so first plays next`() = runTest(testDispatcher) {
        seedTracksForQueue()
        viewModel.playNextAll()
        // Reverse order: t2 first (playNext inserts at priority front), then t1.
        coVerify(exactly = 1) { playbackManager.playNext(match { it.id == "t2" }, any()) }
        coVerify(exactly = 1) { playbackManager.playNext(match { it.id == "t1" }, any()) }
    }

    @Test
    fun `addAllToQueue appends tracks in order`() = runTest(testDispatcher) {
        seedTracksForQueue()
        viewModel.addAllToQueue()
        coVerify(exactly = 1) { playbackManager.addToQueue(match { it.id == "t1" }, any()) }
        coVerify(exactly = 1) { playbackManager.addToQueue(match { it.id == "t2" }, any()) }
    }

    @Test
    fun `playNextAll and addAllToQueue are no-ops on empty track list`() = runTest(testDispatcher) {
        viewModel.playNextAll()
        viewModel.addAllToQueue()
        coVerify(exactly = 0) { playbackManager.playNext(any(), any()) }
        coVerify(exactly = 0) { playbackManager.addToQueue(any(), any()) }
    }

    @Test
    fun `rateTrack writes local rating first then syncs best effort`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        viewModel.rateTrack("t-rate", 4)
        advanceUntilIdle()

        coVerify { trackDao.setRating("t-rate", 4) }
        coVerify { api.setRating(any(), id = "t-rate", rating = 4) }
        assertEquals("Optimistic rating", 4, viewModel.getTrackRating("t-rate"))
    }

    @Test
    fun `rateTrack clamps rating to 0-5`() = runTest(testDispatcher) {
        viewModel.rateTrack("t-clamp", 9)
        advanceUntilIdle()

        coVerify { trackDao.setRating("t-clamp", 5) }
        assertEquals(5, viewModel.getTrackRating("t-clamp"))
    }

    // ── Album favorites (thumbs) ───────────────────────────────────────────
    // NOTE: the VM's init collector captures the reaction flows at construction,
    // so these tests build a fresh VM AFTER stubbing the flows.

    private fun freshViewModel(): ArtistDetailViewModel = ArtistDetailViewModel(
        api, storage, playbackManager, metadataDao, trackDao, offlineModeManager,
        musicBrainzService, lastFmService, cacheService, downloadManager,
        playlistDao, playlistRepo, favoriteRepository,
    )

    @Test
    fun `album reactions populate from room flows`() = runTest(testDispatcher) {
        every { metadataDao.getStarredAlbumIdsFlow() } returns MutableStateFlow(listOf("al-1"))
        every { metadataDao.getDislikedAlbumIdsFlow() } returns MutableStateFlow(listOf("al-2"))
        viewModel = freshViewModel()

        advanceUntilIdle()

        assertEquals(setOf("al-1"), viewModel.state.value.likedAlbumIds)
        assertEquals(setOf("al-2"), viewModel.state.value.dislikedAlbumIds)
    }

    @Test
    fun `toggleAlbumLike adds to liked and calls likeAlbum`() = runTest(testDispatcher) {
        every { metadataDao.getStarredAlbumIdsFlow() } returns MutableStateFlow(emptyList())
        every { metadataDao.getDislikedAlbumIdsFlow() } returns MutableStateFlow(emptyList())
        viewModel = freshViewModel()
        advanceUntilIdle()

        viewModel.toggleAlbumLike("al-1")
        advanceUntilIdle()

        assertTrue("al-1" in viewModel.state.value.likedAlbumIds)
        coVerify(exactly = 1) { favoriteRepository.likeAlbum("al-1") }
    }

    @Test
    fun `toggleAlbumLike removes when already liked`() = runTest(testDispatcher) {
        every { metadataDao.getStarredAlbumIdsFlow() } returns MutableStateFlow(listOf("al-1"))
        every { metadataDao.getDislikedAlbumIdsFlow() } returns MutableStateFlow(emptyList())
        viewModel = freshViewModel()
        advanceUntilIdle()

        viewModel.toggleAlbumLike("al-1")
        advanceUntilIdle()

        assertTrue("al-1" !in viewModel.state.value.likedAlbumIds)
        coVerify(exactly = 1) { favoriteRepository.unlikeAlbum("al-1") }
    }

    @Test
    fun `toggleAlbumLike clears dislike (mutual exclusion)`() = runTest(testDispatcher) {
        every { metadataDao.getStarredAlbumIdsFlow() } returns MutableStateFlow(emptyList())
        every { metadataDao.getDislikedAlbumIdsFlow() } returns MutableStateFlow(listOf("al-1"))
        viewModel = freshViewModel()
        advanceUntilIdle()

        viewModel.toggleAlbumLike("al-1")
        advanceUntilIdle()

        assertTrue("al-1" in viewModel.state.value.likedAlbumIds)
        assertTrue("al-1" !in viewModel.state.value.dislikedAlbumIds)
    }

    @Test
    fun `toggleAlbumDislike adds and clears like (mutual exclusion)`() = runTest(testDispatcher) {
        every { metadataDao.getStarredAlbumIdsFlow() } returns MutableStateFlow(listOf("al-1"))
        every { metadataDao.getDislikedAlbumIdsFlow() } returns MutableStateFlow(emptyList())
        viewModel = freshViewModel()
        advanceUntilIdle()

        viewModel.toggleAlbumDislike("al-1")
        advanceUntilIdle()

        assertTrue("al-1" in viewModel.state.value.dislikedAlbumIds)
        assertTrue("al-1" !in viewModel.state.value.likedAlbumIds)
        coVerify(exactly = 1) { favoriteRepository.dislikeAlbum("al-1") }
    }

    @Test
    fun `toggleAlbumDislike removes when already disliked`() = runTest(testDispatcher) {
        every { metadataDao.getStarredAlbumIdsFlow() } returns MutableStateFlow(emptyList())
        every { metadataDao.getDislikedAlbumIdsFlow() } returns MutableStateFlow(listOf("al-1"))
        viewModel = freshViewModel()
        advanceUntilIdle()

        viewModel.toggleAlbumDislike("al-1")
        advanceUntilIdle()

        assertTrue("al-1" !in viewModel.state.value.dislikedAlbumIds)
        coVerify(exactly = 1) { favoriteRepository.clearDislikeAlbum("al-1") }
    }

    @Test
    fun `toggleAlbumLike rolls back on repository failure`() = runTest(testDispatcher) {
        every { metadataDao.getStarredAlbumIdsFlow() } returns MutableStateFlow(emptyList())
        every { metadataDao.getDislikedAlbumIdsFlow() } returns MutableStateFlow(emptyList())
        viewModel = freshViewModel()
        advanceUntilIdle()
        coEvery { favoriteRepository.likeAlbum(any()) } throws RuntimeException("db down")

        viewModel.toggleAlbumLike("al-1")
        advanceUntilIdle()

        assertTrue("al-1" !in viewModel.state.value.likedAlbumIds)
    }

    // ── Play / overwrite modal ─────────────────────────────────────────────

    @Test
    fun `playAll opens overwrite modal when queue blocks context start`() = runTest(testDispatcher) {
        seedTracksForQueue()
        coEvery {
            playbackManager.tryStartContext(any(), any(), sourceType = "artist", sourceId = any(), sourceName = any())
        } returns false

        viewModel.playAll()
        advanceUntilIdle()

        assertTrue(viewModel.showOverwriteModal.value)
    }

    @Test
    fun `shuffle opens overwrite modal when queue blocks`() = runTest(testDispatcher) {
        seedTracksForQueue()
        coEvery {
            playbackManager.tryShuffleContext(any(), any(), sourceType = "artist", sourceId = any(), sourceName = any())
        } returns false

        viewModel.shuffle()
        advanceUntilIdle()

        assertTrue(viewModel.showOverwriteModal.value)
    }

    @Test
    fun `resolveOverwrite forwards decision and closes modal`() = runTest(testDispatcher) {
        seedTracksForQueue()
        coEvery {
            playbackManager.tryStartContext(any(), any(), sourceType = "artist", sourceId = any(), sourceName = any())
        } returns false
        viewModel.playAll()
        advanceUntilIdle()
        assertTrue(viewModel.showOverwriteModal.value)

        viewModel.resolveOverwrite(true)
        advanceUntilIdle()

        assertFalse(viewModel.showOverwriteModal.value)
        coVerify { playbackManager.resolveOverwrite(true) }
    }

    // ── Track toggles ──────────────────────────────────────────────────────

    @Test
    fun `toggleTrackLike adds to liked set and calls likeTrack`() = runTest(testDispatcher) {
        viewModel.toggleTrackLike("t1")
        advanceUntilIdle()

        assertTrue(viewModel.isTrackLiked("t1"))
        coVerify { favoriteRepository.likeTrack("t1") }
    }

    @Test
    fun `toggleTrackDislike adds to disliked and clears like`() = runTest(testDispatcher) {
        viewModel.toggleTrackLike("t1")
        advanceUntilIdle()
        viewModel.toggleTrackDislike("t1")
        advanceUntilIdle()

        assertTrue(viewModel.isTrackDisliked("t1"))
        assertFalse(viewModel.isTrackLiked("t1"))
        coVerify { favoriteRepository.dislikeTrack("t1") }
    }

    // ── Download ───────────────────────────────────────────────────────────

    @Test
    fun `downloadTrack marks downloaded when promoted`() = runTest(testDispatcher) {
        seedTracksForQueue()
        coEvery { cacheService.promoteToDownload("t1") } returns true
        assertFalse(viewModel.isDownloaded("t1"))

        viewModel.downloadTrack(Track(id = "t1", title = "T1"))
        advanceUntilIdle()

        assertTrue(viewModel.isDownloaded("t1"))
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), priority = any()) }
    }

    @Test
    fun `downloadTrack enqueues when not promoted`() = runTest(testDispatcher) {
        seedTracksForQueue()
        coEvery { cacheService.promoteToDownload("t1") } returns false

        viewModel.downloadTrack(Track(id = "t1", title = "T1"))
        advanceUntilIdle()

        coVerify { downloadManager.enqueue("t1", any(), priority = 1) }
    }

    @Test
    fun `downloadAll is no-op with no tracks`() = runTest(testDispatcher) {
        viewModel.downloadAll()
        advanceUntilIdle()

        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), priority = any()) }
        coVerify(exactly = 0) { cacheService.promoteToDownload(any()) }
    }

    // ── Queue single-track helpers ─────────────────────────────────────────

    @Test
    fun `playNextTrack and addToQueueTrack delegate to playback manager`() = runTest(testDispatcher) {
        val track = Track(id = "t1", title = "T1")

        viewModel.playNextTrack(track, "http://url")
        viewModel.addToQueueTrack(track, "http://url")

        coVerify { playbackManager.playNext(track, "http://url") }
        coVerify { playbackManager.addToQueue(track, "http://url") }
    }

    // ── Playlist picker ────────────────────────────────────────────────────

    @Test
    fun `loadPlaylists loads local playlists`() = runTest(testDispatcher) {
        val pl = mockk<com.lucasdss.ftpmusic.app.data.db.PlaylistEntity>(relaxed = true)
        every { pl.id } returns "pl-1"
        every { pl.name } returns "Roadtrip"
        every { pl.trackCount } returns 7
        coEvery { playlistDao.getAll() } returns listOf(pl)

        viewModel.loadPlaylists()
        advanceUntilIdle()

        assertEquals(1, viewModel.playlists.value.size)
        assertEquals("Roadtrip", viewModel.playlists.value[0].name)
    }

    @Test
    fun `addToPlaylist delegates to repository`() = runTest(testDispatcher) {
        coEvery { playlistRepo.addToPlaylist("pl-1", listOf("t1")) } just Runs

        viewModel.addToPlaylist("pl-1", listOf("t1"))
        advanceUntilIdle()

        coVerify { playlistRepo.addToPlaylist("pl-1", listOf("t1")) }
    }

    // ── Remaining branch coverage ──────────────────────────────────────────

    @Test
    fun `playTrack no-op on out-of-range index`() = runTest(testDispatcher) {
        seedTracksForQueue()
        viewModel.playTrack(5)
        coVerify(exactly = 0) { playbackManager.playAlbum(any(), any(), startIndex = any()) }
    }

    @Test
    fun `playAll and shuffle no-op on empty tracks`() = runTest(testDispatcher) {
        viewModel.playAll()
        viewModel.shuffle()
        advanceUntilIdle()
        assertFalse(viewModel.showOverwriteModal.value)
        coVerify(exactly = 0) { playbackManager.tryStartContext(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { playbackManager.tryShuffleContext(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `downloadAll enqueues each track when promoted`() = runTest(testDispatcher) {
        seedTracksForQueue()
        coEvery { cacheService.promoteToDownload(any()) } returns false

        viewModel.downloadAll()
        advanceUntilIdle()

        coVerify { downloadManager.enqueue("t1", any(), priority = 1) }
        coVerify { downloadManager.enqueue("t2", any(), priority = 1) }
    }

    @Test
    fun `downloadTrack tolerates cache service failure`() = runTest(testDispatcher) {
        seedTracksForQueue()
        coEvery { cacheService.promoteToDownload("t1") } throws RuntimeException("cache broken")

        viewModel.downloadTrack(Track(id = "t1", title = "T1"))
        advanceUntilIdle()

        assertFalse(viewModel.isDownloaded("t1"))
    }

    @Test
    fun `toggleTrackLike rolls back on repository failure`() = runTest(testDispatcher) {
        coEvery { favoriteRepository.likeTrack(any()) } throws RuntimeException("db down")

        viewModel.toggleTrackLike("t1")
        advanceUntilIdle()

        assertFalse(viewModel.isTrackLiked("t1"))
    }

    @Test
    fun `rateTrack tolerates dao failure`() = runTest(testDispatcher) {
        coEvery { trackDao.setRating(any(), any()) } throws RuntimeException("db down")

        viewModel.rateTrack("t-rate", 4)
        advanceUntilIdle()

        assertEquals(4, viewModel.getTrackRating("t-rate")) // optimistic value retained
    }

    @Test
    fun `loadPlaylists tolerates dao failure`() = runTest(testDispatcher) {
        coEvery { playlistDao.getAll() } throws RuntimeException("db down")

        viewModel.loadPlaylists()
        advanceUntilIdle()

        assertEquals(0, viewModel.playlists.value.size)
    }

    @Test
    fun `addToPlaylist tolerates repository failure`() = runTest(testDispatcher) {
        coEvery { playlistRepo.addToPlaylist(any(), any()) } throws RuntimeException("db down")

        viewModel.addToPlaylist("pl-1", listOf("t1"))
        advanceUntilIdle()
        // no crash; nothing to assert beyond completion
    }

    @Test
    fun `loadArtist keeps public rating null when fetch returns no value`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(
            id = "ar-1",
            name = "Test Artist",
            publicRating = null,
        )
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns emptyList()
        coEvery { musicBrainzService.fetchArtistRating("Test Artist") } returns
            Pair(com.lucasdss.ftpmusic.app.data.network.MusicBrainzService.MusicBrainzRating(null, null), null)

        viewModel.loadArtist("ar-1")
        advanceUntilIdle()

        coVerify(exactly = 0) { metadataDao.setArtistPublicRating(any(), any(), any(), any()) }
        assertNull(viewModel.state.value.publicRating)
    }

    @Test
    fun `loadArtist keeps similar artists empty when lastfm returns none`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        coEvery { metadataDao.getArtistById("ar-1") } returns CachedArtistEntity(
            id = "ar-1",
            name = "Test Artist",
            similarArtistsJson = null,
        )
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns emptyList()
        coEvery { metadataDao.getAlbumsByArtistName(any()) } returns emptyList()
        coEvery { trackDao.getTracksByArtistId("ar-1", 50) } returns emptyList()
        coEvery { lastFmService.fetchSimilarArtists("Test Artist") } returns emptyList()

        viewModel.loadArtist("ar-1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.similarArtists.isEmpty())
    }

    @Test
    fun `album reactions tolerate flow failure`() = runTest(testDispatcher) {
        every { metadataDao.getStarredAlbumIdsFlow() } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("flow broken")
        }
        every { metadataDao.getDislikedAlbumIdsFlow() } returns MutableStateFlow(emptyList())
        viewModel = freshViewModel()

        advanceUntilIdle()

        assertTrue(viewModel.state.value.likedAlbumIds.isEmpty())
        assertTrue(viewModel.state.value.dislikedAlbumIds.isEmpty())
    }
}
