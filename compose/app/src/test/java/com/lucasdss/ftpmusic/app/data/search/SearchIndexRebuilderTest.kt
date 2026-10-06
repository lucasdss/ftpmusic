package com.lucasdss.ftpmusic.app.data.search

import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.GenreEntity
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.SearchFtsDao
import com.lucasdss.ftpmusic.app.data.db.SearchFtsTypes
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackSearchIndexRow
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexRebuilderTest {

    private val ftsDao = mockk<SearchFtsDao>(relaxed = true)
    private val trackDao = mockk<TrackDao>()
    private val metadataDao = mockk<CachedMetadataDao>()
    private val playlistDao = mockk<PlaylistDao>()
    private val genreDao = mockk<GenreDao>()
    private val lyricsCacheDao = mockk<LyricsCacheDao>()
    private val storage = mockk<SecureStorage>()

    private val rebuilder = SearchIndexRebuilder(
        ftsDao,
        trackDao,
        metadataDao,
        playlistDao,
        genreDao,
        lyricsCacheDao,
        storage,
    )

    @Test
    fun `rebuildAll inserts track album artist playlist genre rows`() = runTest {
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns "false"
        coEvery { trackDao.getAllTracksForSearchIndex() } returns listOf(
            TrackSearchIndexRow(
                id = "t1",
                title = "Title",
                artist = "Art",
                album = "Alb",
                genre = "Rock",
                path = null,
                year = 1994,
                musicbrainzId = null,
            ),
        )
        coEvery { metadataDao.getAllAlbums() } returns listOf(
            CachedAlbumEntity(id = "al1", name = "Alb", artist = "Art", notes = "notes", year = 1994),
        )
        coEvery { metadataDao.getAllArtists() } returns listOf(
            CachedArtistEntity(
                id = "ar1",
                name = "Art",
                biography = "bio",
                searchAliases = "alias",
                searchTags = "rock indie",
                similarArtistsJson = """["x"]""",
                musicbrainzId = "mbid-1",
            ),
        )
        coEvery { playlistDao.getAll() } returns listOf(
            PlaylistEntity(id = "pl1", name = "Mix", comment = "c"),
        )
        coEvery { genreDao.getAllByPopularity() } returns listOf(
            GenreEntity(name = "Rock", songCount = 1, albumCount = 1),
        )

        rebuilder.rebuildAll()

        val slot = slot<List<com.lucasdss.ftpmusic.app.data.db.SearchFtsEntity>>()
        coVerify { ftsDao.replaceAll(capture(slot)) }
        val types = slot.captured.map { it.entityType }.toSet()
        assertEquals(
            setOf(
                SearchFtsTypes.TRACK,
                SearchFtsTypes.ALBUM,
                SearchFtsTypes.ARTIST,
                SearchFtsTypes.PLAYLIST,
                SearchFtsTypes.GENRE,
            ),
            types,
        )
        assertTrue(slot.captured.any { it.entityType == SearchFtsTypes.ARTIST && it.body.contains("bio") })
        assertTrue(slot.captured.any { it.entityType == SearchFtsTypes.ARTIST && it.body.contains("rock") })
        assertTrue(slot.captured.any { it.entityType == SearchFtsTypes.ARTIST && it.body.contains("mbid-1") })
        assertTrue(slot.captured.any { it.entityType == SearchFtsTypes.ALBUM && it.body.contains("notes") })
        assertTrue(slot.captured.any { it.entityType == SearchFtsTypes.TRACK && it.body.contains("1994") })
    }

    @Test
    fun `rebuildAll includes lyrics when setting enabled`() = runTest {
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns "true"
        coEvery { trackDao.getAllTracksForSearchIndex() } returns emptyList()
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.getAllArtists() } returns emptyList()
        coEvery { playlistDao.getAll() } returns emptyList()
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        coEvery { lyricsCacheDao.getAll() } returns listOf(
            LyricsCacheEntity(
                trackId = "t9",
                artist = "A",
                title = "T",
                unstructuredText = "hello world lyrics",
                syncedLinesJson = null,
                fetchedAt = 1L,
            ),
            LyricsCacheEntity(
                trackId = "t10",
                artist = "B",
                title = "Blank",
                unstructuredText = "  ",
                syncedLinesJson = null,
                fetchedAt = 1L,
            ),
        )

        rebuilder.rebuildAll()

        val slot = slot<List<com.lucasdss.ftpmusic.app.data.db.SearchFtsEntity>>()
        coVerify { ftsDao.replaceAll(capture(slot)) }
        assertEquals(1, slot.captured.size)
        assertEquals(SearchFtsTypes.LYRICS, slot.captured[0].entityType)
        assertEquals("t9", slot.captured[0].entityId)
        assertTrue(slot.captured[0].body.contains("hello world lyrics"))
    }

    @Test
    fun `ftsCount caches until rebuild`() = runTest {
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns "false"
        coEvery { trackDao.getAllTracksForSearchIndex() } returns emptyList()
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.getAllArtists() } returns emptyList()
        coEvery { playlistDao.getAll() } returns emptyList()
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        coEvery { ftsDao.count() } returns 7

        assertEquals(7, rebuilder.ftsCount())
        assertEquals(7, rebuilder.ftsCount())
        coVerify(exactly = 1) { ftsDao.count() }

        rebuilder.rebuildAll()
        assertEquals(0, rebuilder.ftsCount())
        coVerify(exactly = 1) { ftsDao.count() }
    }

    @Test
    fun `ftsCount exception returns zero`() = runTest {
        coEvery { ftsDao.count() } throws RuntimeException("missing table")
        assertEquals(0, rebuilder.ftsCount())
    }

    @Test
    fun `track body includes path folder tokens`() = runTest {
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns "false"
        coEvery { trackDao.getAllTracksForSearchIndex() } returns listOf(
            TrackSearchIndexRow(
                id = "t1",
                title = "Song",
                artist = null,
                album = null,
                genre = "Electronic",
                path = "Music/Artist_Name/Album-Name/track.mp3",
                year = null,
                musicbrainzId = null,
            ),
        )
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.getAllArtists() } returns emptyList()
        coEvery { playlistDao.getAll() } returns emptyList()
        coEvery { genreDao.getAllByPopularity() } returns emptyList()

        rebuilder.rebuildAll()
        val slot = slot<List<com.lucasdss.ftpmusic.app.data.db.SearchFtsEntity>>()
        coVerify { ftsDao.replaceAll(capture(slot)) }
        val body = slot.captured.single().body
        assertTrue(body.contains("Artist"))
        assertTrue(body.contains("Name"))
        assertTrue(body.contains("Electronic"))
    }

    @Test
    fun `replaceAll failure invalidates cached count to zero`() = runTest {
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns "false"
        coEvery { trackDao.getAllTracksForSearchIndex() } returns emptyList()
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.getAllArtists() } returns emptyList()
        coEvery { playlistDao.getAll() } returns emptyList()
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        coEvery { ftsDao.count() } returns 5
        assertEquals(5, rebuilder.ftsCount())
        coEvery { ftsDao.replaceAll(any()) } throws RuntimeException("fts missing")
        rebuilder.rebuildAll()
        assertEquals(0, rebuilder.ftsCount())
        coVerify(exactly = 1) { ftsDao.count() }
    }

    @Test
    fun `lyrics row uses synced json when unstructured null`() = runTest {
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns "true"
        coEvery { trackDao.getAllTracksForSearchIndex() } returns emptyList()
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.getAllArtists() } returns emptyList()
        coEvery { playlistDao.getAll() } returns emptyList()
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        coEvery { lyricsCacheDao.getAll() } returns listOf(
            LyricsCacheEntity(
                trackId = "t1",
                artist = "A",
                title = "T",
                unstructuredText = null,
                syncedLinesJson = "synced body",
                fetchedAt = 1L,
            ),
            LyricsCacheEntity(
                trackId = "t2",
                artist = "B",
                title = "U",
                unstructuredText = null,
                syncedLinesJson = null,
                fetchedAt = 1L,
            ),
        )
        rebuilder.rebuildAll()
        val slot = slot<List<com.lucasdss.ftpmusic.app.data.db.SearchFtsEntity>>()
        coVerify { ftsDao.replaceAll(capture(slot)) }
        assertEquals(1, slot.captured.size)
        assertTrue(slot.captured[0].body.contains("synced body"))
    }

    @Test
    fun `track year below 1900 omits decade label`() = runTest {
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns "false"
        coEvery { trackDao.getAllTracksForSearchIndex() } returns listOf(
            TrackSearchIndexRow(
                id = "t1",
                title = "Old",
                artist = null,
                album = null,
                genre = null,
                path = null,
                year = 1800,
                musicbrainzId = null,
            ),
        )
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.getAllArtists() } returns emptyList()
        coEvery { playlistDao.getAll() } returns emptyList()
        coEvery { genreDao.getAllByPopularity() } returns emptyList()
        rebuilder.rebuildAll()
        val slot = slot<List<com.lucasdss.ftpmusic.app.data.db.SearchFtsEntity>>()
        coVerify { ftsDao.replaceAll(capture(slot)) }
        assertFalse(slot.captured.single().body.contains("1800s") || slot.captured.single().body.contains("00s"))
    }
}
