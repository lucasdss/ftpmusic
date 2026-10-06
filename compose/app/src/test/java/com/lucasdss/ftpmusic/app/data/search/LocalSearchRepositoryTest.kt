package com.lucasdss.ftpmusic.app.data.search

import com.lucasdss.ftpmusic.app.data.db.AlbumYearRow
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

    @Test
    fun `fts year filter drops singles with null albumId`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 2
        coEvery { ftsDao.match(any(), any()) } returns listOf(
            SearchFtsEntity(
                entityType = SearchFtsTypes.TRACK,
                entityId = "single",
                body = "Hit 1990s",
                rank = 0.5,
            ),
            SearchFtsEntity(
                entityType = SearchFtsTypes.TRACK,
                entityId = "albumed",
                body = "Other",
                rank = 1.0,
            ),
            SearchFtsEntity(
                entityType = SearchFtsTypes.TRACK,
                entityId = "nineties",
                body = "Hit",
                rank = 0.4,
            ),
        )
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "single", title = "Hit", albumId = null),
            TrackEntity(id = "albumed", title = "Other", albumId = "al-old"),
            TrackEntity(id = "nineties", title = "Hit", albumId = "al-90"),
        )
        coEvery { metadataDao.getAlbumYearRows(listOf("al-old", "al-90")) } returns listOf(
            AlbumYearRow(id = "al-old", year = 1980),
            AlbumYearRow(id = "al-90", year = 1994),
        )

        val hit = repo.search("hit 90s")
        assertTrue(hit.usedFts)
        assertEquals(listOf("nineties"), hit.tracks.map { it.id })
        assertTrue(hit.ftsRanks.containsKey("nineties"))
    }

    @Test
    fun `fts preserves bm25 order when hydrating`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 2
        coEvery { ftsDao.match(any(), any()) } returns listOf(
            SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "best", body = "a", rank = 0.1),
            SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "worse", body = "a", rank = 2.0),
        )
        // DAO returns reverse order — hydrate must re-order by FTS
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "worse", title = "A"),
            TrackEntity(id = "best", title = "A"),
        )

        val hit = repo.search("a")
        assertEquals(listOf("best", "worse"), hit.tracks.map { it.id })
    }

    @Test
    fun `like year filter drops singles with null albumId`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 0
        coEvery { trackDao.searchAllTracks(any()) } returns listOf(
            TrackEntity(id = "s1", title = "Solo", albumId = null),
            TrackEntity(id = "a1", title = "Albumed", albumId = "al1"),
            TrackEntity(id = "a90", title = "Solo", albumId = "al90"),
        )
        coEvery { metadataDao.searchAlbums(any()) } returns emptyList()
        coEvery { metadataDao.searchArtists(any()) } returns emptyList()
        coEvery { playlistDao.searchPlaylists(any()) } returns emptyList()
        coEvery { genreDao.searchGenres(any()) } returns emptyList()
        coEvery { metadataDao.getAlbumYearRows(listOf("al1", "al90")) } returns listOf(
            AlbumYearRow(id = "al1", year = 2005),
            AlbumYearRow(id = "al90", year = 1995),
        )

        val hit = repo.search("solo 90s")
        assertEquals(listOf("a90"), hit.tracks.map { it.id })
    }

    @Test
    fun `soft typo year filter drops singles with null albumId`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 3
        coEvery { ftsDao.match(any(), any()) } returns emptyList()
        coEvery { trackDao.searchAllTracks(any()) } returns listOf(
            TrackEntity(id = "s1", title = "Beatles", albumId = null),
            TrackEntity(id = "a90", title = "Beatles", albumId = "al90"),
        )
        coEvery { metadataDao.getAlbumYearRows(listOf("al90")) } returns listOf(
            AlbumYearRow(id = "al90", year = 1995),
        )
        coEvery { metadataDao.searchAlbums(any()) } returns emptyList()
        coEvery { metadataDao.searchArtists(any()) } returns emptyList()
        coEvery { playlistDao.searchPlaylists(any()) } returns emptyList()
        coEvery { genreDao.searchGenres(any()) } returns emptyList()

        val hit = repo.search("beatle 90s")
        assertTrue(hit.usedSoftTypo)
        assertEquals(listOf("a90"), hit.tracks.map { it.id })
    }

    @Test
    fun `exact year query uses album year lookup`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 0
        coEvery { metadataDao.searchAlbumsByExactYear(1994, any()) } returns listOf(
            CachedAlbumEntity(id = "a1", name = "A", year = 1994),
        )
        coEvery { trackDao.getTracksByAlbumIds(any()) } returns emptyList()

        val hit = repo.search("1994")
        assertEquals(listOf("a1"), hit.albums.map { it.id })
        assertEquals(1994, hit.yearConstraint.exactYear)
    }

    @Test
    fun `playableOnly keeps cachedFilePath tracks`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 1
        coEvery { ftsDao.match(any(), any()) } returns listOf(
            SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "c1", body = "x", rank = 0.1),
        )
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "c1", title = "Cached", cachedFilePath = "/tmp/c1.mp3"),
        )

        val hit = repo.search("cached", playableOnly = true)
        assertEquals(listOf("c1"), hit.tracks.map { it.id })
    }

    @Test
    fun `fts match throw falls back to LIKE`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 5
        coEvery { ftsDao.match(any(), any()) } throws RuntimeException("fts")
        coEvery { trackDao.searchAllTracks(any()) } returns listOf(TrackEntity(id = "t1", title = "Hello"))
        coEvery { metadataDao.searchAlbums(any()) } returns emptyList()
        coEvery { metadataDao.searchArtists(any()) } returns emptyList()
        coEvery { playlistDao.searchPlaylists(any()) } returns emptyList()
        coEvery { genreDao.searchGenres(any()) } returns emptyList()

        val hit = repo.search("hello")
        assertFalse(hit.usedFts)
        assertEquals("t1", hit.tracks.single().id)
    }

    @Test
    fun `ftsRanks keeps min score when track and lyrics share id`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 2
        coEvery { ftsDao.match(any(), any()) } returns listOf(
            SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "t1", body = "a", rank = 2.0),
            SearchFtsEntity(entityType = SearchFtsTypes.LYRICS, entityId = "t1", body = "a", rank = 0.5),
        )
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(TrackEntity(id = "t1", title = "A"))

        val hit = repo.search("a")
        assertEquals(0.5, hit.ftsRanks["t1"]!!, 0.0)
    }

    @Test
    fun `soft typo miss falls through to LIKE`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 3
        coEvery { ftsDao.match(any(), any()) } returns emptyList()
        coEvery { trackDao.searchAllTracks(any()) } returnsMany listOf(
            listOf(TrackEntity(id = "x", title = "zzzz")), // soft typo candidates
            listOf(TrackEntity(id = "like", title = "hello")), // LIKE fallback
        )
        coEvery { metadataDao.searchAlbums(any()) } returns emptyList()
        coEvery { metadataDao.searchArtists(any()) } returns emptyList()
        coEvery { playlistDao.searchPlaylists(any()) } returns emptyList()
        coEvery { genreDao.searchGenres(any()) } returns emptyList()

        val hit = repo.search("hello")
        assertFalse(hit.usedSoftTypo)
        assertEquals("like", hit.tracks.single().id)
    }

    @Test
    fun `ftsCount throw falls back to LIKE`() = runTest {
        coEvery { rebuilder.ftsCount() } throws RuntimeException("boom")
        coEvery { trackDao.searchAllTracks(any()) } returns listOf(TrackEntity(id = "t1", title = "Hi"))
        coEvery { metadataDao.searchAlbums(any()) } returns emptyList()
        coEvery { metadataDao.searchArtists(any()) } returns emptyList()
        coEvery { playlistDao.searchPlaylists(any()) } returns emptyList()
        coEvery { genreDao.searchGenres(any()) } returns emptyList()

        val hit = repo.search("hi")
        assertFalse(hit.usedFts)
        assertEquals("t1", hit.tracks.single().id)
    }

    @Test
    fun `year filter drops singles with null albumId`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 2
        coEvery { ftsDao.match(any(), any()) } returns listOf(
            SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "s1", body = "90s", rank = 0.1),
            SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "a1", body = "90s", rank = 0.2),
            SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "ok", body = "90s", rank = 0.05),
        )
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "s1", title = "Single", albumId = null),
            TrackEntity(id = "a1", title = "AlbumTrack", albumId = "alb-bad"),
            TrackEntity(id = "ok", title = "Good", albumId = "alb-90"),
        )
        coEvery { metadataDao.getAlbumYearRows(any()) } returns listOf(
            AlbumYearRow(id = "alb-bad", year = 2005),
            AlbumYearRow(id = "alb-90", year = 1994),
        )

        val hit = repo.search("beat 90s")
        assertEquals(listOf("ok"), hit.tracks.map { it.id })
    }

    @Test
    fun `playableOnly year-only filters downloaded album tracks`() = runTest {
        coEvery { rebuilder.ftsCount() } returns 0
        coEvery { metadataDao.searchAlbumsByExactYear(1994, any()) } returns listOf(
            CachedAlbumEntity(id = "al1", name = "A", year = 1994),
        )
        coEvery { trackDao.getTracksByAlbumIds(listOf("al1")) } returns listOf(
            TrackEntity(id = "d1", title = "Down", albumId = "al1", isDownloaded = true),
            TrackEntity(id = "n1", title = "Net", albumId = "al1", isDownloaded = false),
        )

        val hit = repo.search("1994", playableOnly = true)
        assertEquals(listOf("d1"), hit.tracks.map { it.id })
    }
}
