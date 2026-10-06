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
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        ftsDao, trackDao, metadataDao, playlistDao, genreDao, lyricsCacheDao, storage,
    )

    @Test
    fun `rebuildAll inserts track album artist playlist genre rows`() = runTest {
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns "false"
        coEvery { trackDao.getAllTracksForSearchIndex() } returns listOf(
            TrackEntity(id = "t1", title = "Title", artist = "Art", album = "Alb", genre = "Rock"),
        )
        coEvery { metadataDao.getAllAlbums() } returns listOf(
            CachedAlbumEntity(id = "al1", name = "Alb", artist = "Art", notes = "notes"),
        )
        coEvery { metadataDao.getAllArtists() } returns listOf(
            CachedArtistEntity(
                id = "ar1",
                name = "Art",
                biography = "bio",
                searchAliases = "alias",
                similarArtistsJson = """["x"]""",
            ),
        )
        coEvery { playlistDao.getAll() } returns listOf(
            PlaylistEntity(id = "pl1", name = "Mix", comment = "c"),
        )
        coEvery { genreDao.getAllByPopularity() } returns listOf(
            GenreEntity(name = "Rock", songCount = 1, albumCount = 1),
        )

        rebuilder.rebuildAll()

        coVerify { ftsDao.clearAll() }
        val slot = slot<List<com.lucasdss.ftpmusic.app.data.db.SearchFtsEntity>>()
        coVerify { ftsDao.insertAll(capture(slot)) }
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
        assertTrue(slot.captured.any { it.entityType == SearchFtsTypes.ALBUM && it.body.contains("notes") })
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
        coVerify { ftsDao.insertAll(capture(slot)) }
        assertEquals(1, slot.captured.size)
        assertEquals(SearchFtsTypes.LYRICS, slot.captured[0].entityType)
        assertEquals("t9", slot.captured[0].entityId)
        assertTrue(slot.captured[0].body.contains("hello world lyrics"))
    }
}
