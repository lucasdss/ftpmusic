package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedAlbumTrackEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixEntity
import com.lucasdss.ftpmusic.app.data.db.DailyMixEntity
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.GenreEntity
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoBrowseCatalogTest {

    private lateinit var trackDao: TrackDao
    private lateinit var metadataDao: CachedMetadataDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var genreDao: GenreDao
    private lateinit var customMixDao: CustomMixDao
    private lateinit var genreMixDao: GenreMixDao
    private lateinit var dailyMixRepository: DailyMixRepository
    private lateinit var radioSource: AutoRadioStationsSource
    private lateinit var catalog: AutoBrowseCatalog

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns (firstArg() as String)
            uri
        }
        every { Uri.encode(any()) } answers { firstArg() }
        every { Uri.decode(any()) } answers { firstArg() }
        trackDao = mockk(relaxed = true)
        metadataDao = mockk(relaxed = true)
        playlistDao = mockk(relaxed = true)
        genreDao = mockk(relaxed = true)
        customMixDao = mockk(relaxed = true)
        genreMixDao = mockk(relaxed = true)
        dailyMixRepository = mockk(relaxed = true)
        radioSource = mockk(relaxed = true)
        catalog = AutoBrowseCatalog(
            trackDao = trackDao,
            metadataDao = metadataDao,
            playlistDao = playlistDao,
            artworkUriFor = { id -> id?.let { Uri.parse("art://$it") } },
            streamUriFor = { id -> Uri.parse("stream://$id") },
            genreDao = genreDao,
            customMixDao = customMixDao,
            genreMixDao = genreMixDao,
            dailyMixRepository = dailyMixRepository,
            radioSource = radioSource,
        )
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `root has nine browse nodes`() = runBlocking {
        val kids = catalog.children(AutoBrowseIds.ROOT, page = 0, pageSize = 50)
        assertEquals(9, kids.size)
        assertEquals(
            listOf(
                AutoBrowseIds.RECENT,
                AutoBrowseIds.OFFLINE,
                AutoBrowseIds.FAVORITES,
                AutoBrowseIds.MIXES,
                AutoBrowseIds.PLAYLISTS,
                AutoBrowseIds.ALBUMS,
                AutoBrowseIds.ARTISTS,
                AutoBrowseIds.GENRES,
                AutoBrowseIds.RADIO,
            ),
            kids.map { it.mediaId },
        )
        kids.forEach { assertTrue(it.mediaMetadata.isBrowsable == true) }
    }

    @Test
    fun `empty library returns empty children`() = runBlocking {
        coEvery { trackDao.getRecentlyPlayedPaged(any(), any()) } returns emptyList()
        coEvery { trackDao.getStarred(any(), any()) } returns emptyList()
        coEvery { playlistDao.getAll() } returns emptyList()
        coEvery { metadataDao.getAlbumsPaged(any(), any()) } returns emptyList()
        coEvery { metadataDao.getArtistsPaged(any(), any()) } returns emptyList()

        assertTrue(catalog.children(AutoBrowseIds.RECENT, 0, 20).isEmpty())
        assertTrue(catalog.children(AutoBrowseIds.FAVORITES, 0, 20).isEmpty())
        assertTrue(catalog.children(AutoBrowseIds.PLAYLISTS, 0, 20).isEmpty())
        assertTrue(catalog.children(AutoBrowseIds.ALBUMS, 0, 20).isEmpty())
        assertTrue(catalog.children(AutoBrowseIds.ARTISTS, 0, 20).isEmpty())
    }

    @Test
    fun `favorites returns playable tracks`() = runBlocking {
        coEvery { trackDao.getStarred(limit = 20, offset = 0) } returns listOf(
            TrackEntity(id = "t1", title = "Liked", artist = "A"),
        )
        val kids = catalog.children(AutoBrowseIds.FAVORITES, 0, 20)
        assertEquals(1, kids.size)
        assertEquals("t1", kids[0].mediaId)
        assertTrue(kids[0].mediaMetadata.isPlayable == true)
        assertFalse(kids[0].mediaMetadata.isBrowsable == true)
    }

    @Test
    fun `playlist expand returns entry order`() = runBlocking {
        coEvery { playlistDao.getEntries("p1") } returns listOf(
            PlaylistEntryEntity(playlistId = "p1", trackId = "t2", position = 0),
            PlaylistEntryEntity(playlistId = "p1", trackId = "t1", position = 1),
        )
        coEvery { trackDao.getTracksByIds(listOf("t2", "t1")) } returns listOf(
            TrackEntity(id = "t1", title = "One"),
            TrackEntity(id = "t2", title = "Two"),
        )
        val items = catalog.expandForPlayback(AutoBrowseIds.playlist("p1"))
        assertEquals(listOf("t2", "t1"), items.map { it.mediaId })
    }

    @Test
    fun `album expand returns cached tracks`() = runBlocking {
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(id = "a", albumId = "al1", title = "A", trackNumber = 1),
            CachedAlbumTrackEntity(id = "b", albumId = "al1", title = "B", trackNumber = 2),
        )
        val items = catalog.expandForPlayback(AutoBrowseIds.album("al1"))
        assertEquals(listOf("a", "b"), items.map { it.mediaId })
    }

    @Test
    fun `artist children are albums`() = runBlocking {
        coEvery { metadataDao.getAlbumsByArtistId("ar1") } returns listOf(
            CachedAlbumEntity(id = "al1", name = "Debut", artistId = "ar1"),
        )
        val kids = catalog.children(AutoBrowseIds.artist("ar1"), 0, 50)
        assertEquals(1, kids.size)
        assertEquals(AutoBrowseIds.album("al1"), kids[0].mediaId)
    }

    @Test
    fun `item resolves playlist and track`() = runBlocking {
        coEvery { playlistDao.getById("p1") } returns PlaylistEntity(
            id = "p1",
            name = "Mix",
            trackCount = 3,
        )
        coEvery { trackDao.getTrack("t9") } returns TrackEntity(id = "t9", title = "Song")
        assertEquals("Mix", catalog.item(AutoBrowseIds.playlist("p1"))?.mediaMetadata?.title)
        assertEquals("Song", catalog.item("t9")?.mediaMetadata?.title)
    }

    @Test
    fun `browse node ids detected`() {
        assertTrue(AutoBrowseIds.isBrowseNode(AutoBrowseIds.ROOT))
        assertTrue(AutoBrowseIds.isBrowseNode(AutoBrowseIds.album("x")))
        assertTrue(AutoBrowseIds.isBrowseNode(AutoBrowseIds.OFFLINE))
        assertTrue(AutoBrowseIds.isBrowseNode(AutoBrowseIds.mix(1)))
        assertTrue(AutoBrowseIds.isBrowseNode(AutoBrowseIds.genre("Rock")))
        assertTrue(AutoBrowseIds.isBrowseNode(AutoBrowseIds.radio("s1")))
        assertTrue(AutoBrowseIds.isRadioMediaId(AutoBrowseIds.radio("s1")))
        assertTrue(AutoBrowseIds.isRadioMediaId("radio:123"))
        assertFalse(AutoBrowseIds.isBrowseNode("plain-track-id"))
    }

    @Test
    fun `offline downloads paged`() = runBlocking {
        coEvery { trackDao.getDownloadedPaged(20, 0) } returns listOf(
            TrackEntity(id = "d1", title = "Dl"),
        )
        val kids = catalog.children(AutoBrowseIds.OFFLINE, 0, 20)
        assertEquals("d1", kids.single().mediaId)
    }

    @Test
    fun `genres list and expand`() = runBlocking {
        coEvery { genreDao.getAllByPopularity() } returns listOf(
            GenreEntity(id = 1, name = "Rock", songCount = 5),
            GenreEntity(id = 2, name = "Jazz", songCount = 0),
        )
        val folders = catalog.children(AutoBrowseIds.GENRES, 0, 20)
        assertEquals(2, folders.size)
        assertTrue(folders[0].mediaMetadata.isPlayable == true)
        assertFalse(folders[1].mediaMetadata.isPlayable == true)

        coEvery { trackDao.getTracksByGenrePaged("Rock", 500, 0) } returns listOf(
            TrackEntity(id = "r1", title = "Riff", genre = "Rock"),
        )
        val expanded = catalog.expandForPlayback(AutoBrowseIds.genre("Rock"))
        assertEquals(listOf("r1"), expanded.map { it.mediaId })
    }

    @Test
    fun `daily mix expand uses today then yesterday`() = runBlocking {
        val today = java.time.LocalDate.now().toString()
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()
        coEvery { genreMixDao.getDailyMix(today, 7L) } returns null
        coEvery { genreMixDao.getDailyMix(yesterday, 7L) } returns DailyMixEntity(
            id = 42,
            date = yesterday,
            mixId = 7L,
        )
        coEvery { genreMixDao.getDailyMixTrackIds(42) } returns listOf("t1", "t2")
        coEvery { dailyMixRepository.filterPlayableMixTrackIds(listOf("t1", "t2")) } returns listOf("t1", "t2")
        coEvery { trackDao.getTracksByIds(listOf("t1", "t2")) } returns listOf(
            TrackEntity(id = "t1", title = "One"),
            TrackEntity(id = "t2", title = "Two"),
        )
        val items = catalog.expandForPlayback(AutoBrowseIds.mix(7))
        assertEquals(listOf("t1", "t2"), items.map { it.mediaId })
    }

    @Test
    fun `empty mix folder is not playable`() = runBlocking {
        coEvery { customMixDao.getAll() } returns listOf(
            CustomMixEntity(id = 1, name = "Empty Mix", sourceKind = "genres"),
        )
        coEvery { genreMixDao.getDailyMix(any(), any()) } returns null
        val kids = catalog.children(AutoBrowseIds.MIXES, 0, 20)
        assertFalse(kids.single().mediaMetadata.isPlayable == true)
    }

    @Test
    fun `radio stations from source`() = runBlocking {
        coEvery { radioSource.loadStations() } returns listOf(
            AutoRadioStation(id = "s1", name = "Synth", streamUrl = "https://stream.example/1"),
        )
        val kids = catalog.children(AutoBrowseIds.RADIO, 0, 20)
        assertEquals(1, kids.size)
        assertEquals(AutoBrowseIds.radio("s1"), kids[0].mediaId)
        assertTrue(kids[0].mediaMetadata.isPlayable == true)
        assertEquals(
            androidx.media3.common.MediaMetadata.MEDIA_TYPE_RADIO_STATION,
            kids[0].mediaMetadata.mediaType,
        )

        val expanded = catalog.expandForPlayback(AutoBrowseIds.radio("s1"))
        assertEquals(1, expanded.size)
        assertEquals("https://stream.example/1", expanded[0].localConfiguration?.uri?.toString())
    }

    @Test
    fun `offline radio empty when source empty`() = runBlocking {
        coEvery { radioSource.loadStations() } returns emptyList()
        assertTrue(catalog.children(AutoBrowseIds.RADIO, 0, 20).isEmpty())
    }

    @Test
    fun `artist with zero albums is not playable`() = runBlocking {
        coEvery { metadataDao.getArtistsPaged(any(), any()) } returns listOf(
            CachedArtistEntity(id = "ar0", name = "Empty", albumCount = 0),
            CachedArtistEntity(id = "ar1", name = "Has", albumCount = 2),
        )
        val kids = catalog.children(AutoBrowseIds.ARTISTS, 0, 20)
        assertFalse(kids[0].mediaMetadata.isPlayable == true)
        assertTrue(kids[1].mediaMetadata.isPlayable == true)
    }

    @Test
    fun `playlist page skips dangling entries and fills page`() = runBlocking {
        coEvery { playlistDao.getEntries("p1") } returns listOf(
            PlaylistEntryEntity(playlistId = "p1", trackId = "missing", position = 0),
            PlaylistEntryEntity(playlistId = "p1", trackId = "t1", position = 1),
            PlaylistEntryEntity(playlistId = "p1", trackId = "t2", position = 2),
        )
        coEvery { trackDao.getTracksByIds(any()) } answers {
            val ids = firstArg<List<String>>()
            ids.filter { it != "missing" }.map { TrackEntity(id = it, title = it) }
        }
        val kids = catalog.children(AutoBrowseIds.playlist("p1"), 0, 2)
        assertEquals(listOf("t1", "t2"), kids.map { it.mediaId })
    }

    @Test
    fun `artists paged from dao`() = runBlocking {
        coEvery { metadataDao.getArtistsPaged(10, 0) } returns listOf(
            CachedArtistEntity(id = "ar1", name = "Artist One"),
        )
        val kids = catalog.children(AutoBrowseIds.ARTISTS, 0, 10)
        assertEquals(AutoBrowseIds.artist("ar1"), kids.single().mediaId)
    }

    @Test
    fun `track without uri is not playable`() = runBlocking {
        val noStream = AutoBrowseCatalog(
            trackDao = trackDao,
            metadataDao = metadataDao,
            playlistDao = playlistDao,
            artworkUriFor = { null },
            streamUriFor = { null },
        )
        coEvery { trackDao.getStarred(any(), any()) } returns listOf(
            TrackEntity(id = "t1", title = "NoUri"),
        )
        val kids = noStream.children(AutoBrowseIds.FAVORITES, 0, 20)
        assertFalse(kids.single().mediaMetadata.isPlayable == true)
    }

    @Test
    fun `track with cache path still uses stream uri not file`() = runBlocking {
        coEvery { trackDao.getStarred(any(), any()) } returns listOf(
            TrackEntity(id = "t1", title = "Cached", cachedFilePath = "/data/t1.cache"),
        )
        val kids = catalog.children(AutoBrowseIds.FAVORITES, 0, 20)
        assertTrue(kids.single().mediaMetadata.isPlayable == true)
        assertEquals("stream://t1", kids.single().localConfiguration?.uri?.toString())
        assertFalse(kids.single().localConfiguration?.uri?.toString()?.startsWith("file://") == true)
    }

    @Test
    fun `empty playlist folder is not playable`() = runBlocking {
        coEvery { playlistDao.getAll() } returns listOf(
            PlaylistEntity(id = "p0", name = "Empty", trackCount = 0),
        )
        val kids = catalog.children(AutoBrowseIds.PLAYLISTS, 0, 20)
        assertFalse(kids.single().mediaMetadata.isPlayable == true)
    }

    @Test
    fun `empty album folder is not playable`() = runBlocking {
        coEvery { metadataDao.getAlbumsPaged(any(), any()) } returns listOf(
            CachedAlbumEntity(id = "a0", name = "Empty Album", songCount = 0),
            CachedAlbumEntity(id = "a1", name = "Unknown Count", songCount = null),
            CachedAlbumEntity(id = "a2", name = "Has Tracks", songCount = 3),
        )
        val kids = catalog.children(AutoBrowseIds.ALBUMS, 0, 20)
        assertEquals(3, kids.size)
        assertFalse(kids[0].mediaMetadata.isPlayable == true)
        assertFalse(kids[1].mediaMetadata.isPlayable == true)
        assertTrue(kids[2].mediaMetadata.isPlayable == true)
    }
}
