package com.lucasdss.ftpmusic.app.data.search

import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.GenreEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.SearchFtsDao
import com.lucasdss.ftpmusic.app.data.db.SearchFtsEntity
import com.lucasdss.ftpmusic.app.data.db.SearchFtsTypes
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalSearchRepositoryTest {

    private val ftsDao = mockk<SearchFtsDao>()
    private val trackDao = mockk<TrackDao>(relaxed = true)
    private val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val genreDao = mockk<GenreDao>(relaxed = true)
    private val rebuilder = mockk<SearchIndexRebuilder>(relaxed = true)
    private val repo = LocalSearchRepository(ftsDao, trackDao, metadataDao, playlistDao, genreDao, rebuilder)

    @Before
    fun setUp() {
        coEvery { rebuilder.ftsCount() } returns 0
    }

    @Test
    fun `empty fts falls back to LIKE`() = runTest {
        coEvery { trackDao.searchAllTracks(any()) } returns listOf(
            TrackEntity(id = "t1", title = "Hello"),
        )
        coEvery { metadataDao.searchAlbums(any()) } returns emptyList()
        coEvery { metadataDao.searchArtists(any()) } returns emptyList()
        coEvery { playlistDao.searchPlaylists(any()) } returns emptyList()
        coEvery { genreDao.searchGenres(any()) } returns emptyList()

        val hit = repo.search("hello")
        assertFalse(hit.usedFts)
        assertEquals(1, hit.tracks.size)
        assertEquals("t1", hit.tracks[0].id)
        coVerify(exactly = 0) { ftsDao.match(any(), any()) }
        coVerify(exactly = 0) { metadataDao.getAllAlbums() }
    }

    @Test
    fun `fts hydrates tracks albums artists playlists genres`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 10
        coEvery { ftsDao.match(any(), any()) } returns listOf(
            SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "t1", body = "song"),
            SearchFtsEntity(entityType = SearchFtsTypes.LYRICS, entityId = "t2", body = "lyric"),
            SearchFtsEntity(entityType = SearchFtsTypes.ALBUM, entityId = "al1", body = "album"),
            SearchFtsEntity(entityType = SearchFtsTypes.ARTIST, entityId = "ar1", body = "artist"),
            SearchFtsEntity(entityType = SearchFtsTypes.PLAYLIST, entityId = "pl1", body = "pl"),
            SearchFtsEntity(entityType = SearchFtsTypes.GENRE, entityId = "Rock", body = "Rock"),
        )
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "t1", title = "Song"),
            TrackEntity(id = "t2", title = "Lyric Song"),
        )
        coEvery { metadataDao.getAlbumsByIds(listOf("al1")) } returns listOf(
            CachedAlbumEntity(id = "al1", name = "Album"),
        )
        coEvery { metadataDao.getArtistsByIds(listOf("ar1")) } returns listOf(
            CachedArtistEntity(id = "ar1", name = "Artist"),
        )
        coEvery { playlistDao.getPlaylistsByIds(listOf("pl1")) } returns listOf(
            PlaylistEntity(id = "pl1", name = "PL", comment = "c"),
        )
        coEvery { genreDao.getGenresByNames(listOf("Rock")) } returns listOf(
            GenreEntity(name = "Rock", songCount = 10, albumCount = 1),
        )

        val hit = repo.search("song")
        assertTrue(hit.usedFts)
        assertEquals(2, hit.tracks.size)
        assertEquals(listOf("t2"), hit.lyricTrackIds)
        assertEquals(1, hit.albums.size)
        assertEquals("al1", hit.albums[0].id)
        assertEquals(1, hit.artists.size)
        assertEquals(1, hit.playlists.size)
        assertEquals(1, hit.genres.size)
        coVerify(exactly = 0) { metadataDao.getAllAlbums() }
    }

    @Test
    fun `playableOnly filters downloaded tracks on fts path`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 1
        coEvery { ftsDao.match(any(), any()) } returns listOf(
            SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "t1", body = "a"),
            SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "t2", body = "b"),
        )
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "t1", title = "A", isDownloaded = true),
            TrackEntity(id = "t2", title = "B", isDownloaded = false),
        )

        val hit = repo.search("ab", playableOnly = true)
        assertTrue(hit.usedFts)
        assertEquals(listOf("t1"), hit.tracks.map { it.id })
    }

    @Test
    fun `fts exception falls back to LIKE`() = runTest {
        coEvery { rebuilder.ftsCount() } throws RuntimeException("no table")
        coEvery { trackDao.searchAllTracks(any()) } returns emptyList()
        coEvery { metadataDao.searchAlbums(any()) } returns emptyList()
        coEvery { metadataDao.searchArtists(any()) } returns emptyList()
        coEvery { playlistDao.searchPlaylists(any()) } returns emptyList()
        coEvery { genreDao.searchGenres(any()) } returns emptyList()

        val hit = repo.search("q")
        assertFalse(hit.usedFts)
    }

    @Test
    fun `empty fts match rows fall back to LIKE`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 5
        coEvery { ftsDao.match(any(), any()) } returns emptyList()
        coEvery { trackDao.searchPlayableTracks(any()) } returns listOf(
            TrackEntity(id = "p1", title = "Playable", isDownloaded = true),
        )
        coEvery { metadataDao.searchPlayableAlbums(any()) } returns emptyList()
        coEvery { metadataDao.searchPlayableArtists(any()) } returns emptyList()
        coEvery { playlistDao.searchPlaylists(any()) } returns emptyList()
        coEvery { genreDao.searchGenres(any()) } returns emptyList()

        val hit = repo.search("play", playableOnly = true)
        assertFalse(hit.usedFts)
        assertEquals("p1", hit.tracks.single().id)
    }

    @Test
    fun `soft typo matches one-edit title when FTS empty`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 3
        coEvery { ftsDao.match(any(), any()) } returns emptyList()
        coEvery { trackDao.searchAllTracks(any()) } returns listOf(
            TrackEntity(id = "t1", title = "Beatles"),
            TrackEntity(id = "t2", title = "Other"),
        )

        val hit = repo.search("beatle")
        assertTrue(hit.usedSoftTypo)
        assertEquals(listOf("t1"), hit.tracks.map { it.id })
        coVerify(exactly = 0) { metadataDao.getAllAlbums() }
    }

    @Test
    fun `year decade filters albums`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 0
        coEvery { metadataDao.searchAlbumsByYearRange(1990, 1999, any()) } returns listOf(
            CachedAlbumEntity(id = "a1", name = "A", year = 1995),
        )
        coEvery { trackDao.getTracksByAlbumIds(any()) } returns listOf(
            TrackEntity(id = "t1", title = "Song", albumId = "a1"),
        )

        val hit = repo.search("90s")
        assertEquals(listOf("a1"), hit.albums.map { it.id })
        assertEquals(1990, hit.yearConstraint.minYear)
        coVerify(exactly = 0) { metadataDao.getAllAlbums() }
    }
}
