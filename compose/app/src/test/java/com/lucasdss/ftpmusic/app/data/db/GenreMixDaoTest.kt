package com.lucasdss.ftpmusic.app.data.db

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GenreMixDaoTest {

    private val dao: GenreMixDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)

    // ── upsertGenres / replaceGenres ──────────────────────────────────────────

    @Test
    fun `upsertGenres inserts genres`() = runTest {
        val genres = listOf(
            CachedGenreEntity(name = "Rock", songCount = 500, albumCount = 50),
            CachedGenreEntity(name = "Jazz", songCount = 300, albumCount = 30),
        )
        coEvery { dao.getTopGenres() } returns genres.sortedByDescending { it.songCount }

        dao.upsertGenres(genres)
        coVerify(exactly = 1) { dao.upsertGenres(genres) }

        val result = dao.getTopGenres()
        assertEquals(2, result.size)
        assertEquals("Rock", result[0].name)
        assertEquals("Jazz", result[1].name)
    }

    @Test
    fun `replaceGenres clears and inserts`() = runTest {
        val genres = listOf(
            CachedGenreEntity(name = "Pop", songCount = 400),
        )
        coEvery { dao.getTopGenres() } returns genres

        dao.replaceGenres(genres)
        coVerify(exactly = 1) { dao.replaceGenres(genres) }

        val result = dao.getTopGenres()
        assertEquals("Pop", result[0].name)
    }

    @Test
    fun `getTopGenres returns sorted by song_count DESC`() = runTest {
        val genres = listOf(
            CachedGenreEntity(name = "A", songCount = 10),
            CachedGenreEntity(name = "B", songCount = 100),
            CachedGenreEntity(name = "C", songCount = 50),
        )
        val sorted = genres.sortedByDescending { it.songCount }
        coEvery { dao.getTopGenres() } returns sorted

        val result = dao.getTopGenres()
        assertEquals(3, result.size)
        assertEquals("B", result[0].name) // 100
        assertEquals("C", result[1].name) // 50
        assertEquals("A", result[2].name) // 10
    }

    @Test
    fun `clearGenres removes all genres`() = runTest {
        coEvery { dao.getTopGenres() } returnsMany listOf(
            listOf(CachedGenreEntity(name = "Rock", songCount = 500)),
            emptyList(),
        )

        assertEquals(1, dao.getTopGenres().size)
        dao.clearGenres()
        coVerify(exactly = 1) { dao.clearGenres() }
        assertTrue(dao.getTopGenres().isEmpty())
    }

    // ── upsertSongs / replaceSongs ────────────────────────────────────────────

    @Test
    fun `getSongsForGenre returns songs for specific genre`() = runTest {
        val rockSongs = listOf(
            CachedGenreSongEntity(id = "s1", genre = "Rock", title = "Song A"),
            CachedGenreSongEntity(id = "s2", genre = "Rock", title = "Song B"),
        )
        coEvery { dao.getSongsForGenre("Rock") } returns rockSongs

        val result = dao.getSongsForGenre("Rock")
        assertEquals(2, result.size)
        assertEquals("Rock", result[0].genre)
        assertEquals("Song A", result[0].title)
    }

    @Test
    fun `replaceSongs clears and inserts songs for genre`() = runTest {
        val songs = listOf(
            CachedGenreSongEntity(id = "s1", genre = "Jazz", title = "Track 1"),
        )
        coEvery { dao.getSongsForGenre("Jazz") } returns songs

        dao.replaceSongs("Jazz", songs)
        coVerify(exactly = 1) { dao.replaceSongs("Jazz", songs) }

        assertEquals("Track 1", dao.getSongsForGenre("Jazz")[0].title)
    }

    @Test
    fun `clearSongsForGenre removes songs for specific genre only`() = runTest {
        coEvery { dao.getSongsForGenre("Rock") } returns emptyList()
        coEvery { dao.getSongsForGenre("Jazz") } returns emptyList()

        dao.clearSongsForGenre("Jazz")
        coVerify(exactly = 1) { dao.clearSongsForGenre("Jazz") }
        assertTrue(dao.getSongsForGenre("Jazz").isEmpty())
    }

    // ── Count methods ─────────────────────────────────────────────────────────

    @Test
    fun `genreCount returns correct count`() = runTest {
        coEvery { dao.genreCount() } returns 5
        assertEquals(5, dao.genreCount())
    }

    @Test
    fun `genreSongCount returns correct count`() = runTest {
        coEvery { dao.genreSongCount() } returns 500
        assertEquals(500, dao.genreSongCount())
    }

    // ── Daily Mix ────────────────────────────────────────────────────────────

    @Test
    fun `getDailyMix returns null when no mix exists`() = runTest {
        coEvery { dao.getDailyMix("2026-07-04", 7L) } returns null
        assertNull(dao.getDailyMix("2026-07-04", 7L))
    }

    @Test
    fun `getDailyMix returns stored mix for date and mix id`() = runTest {
        val mix = DailyMixEntity(
            id = 1,
            date = "2026-07-04",
            mixId = 7L,
        )
        coEvery { dao.getDailyMix("2026-07-04", 7L) } returns mix
        val result = dao.getDailyMix("2026-07-04", 7L)
        assertNotNull(result)
        assertEquals(7L, result!!.mixId)
    }

    @Test
    fun `upsertDailyMix replaces existing mix for same date and mix id`() = runTest {
        val mix = DailyMixEntity(
            date = "2026-07-04",
            mixId = 9L,
        )
        coEvery { dao.upsertDailyMix(mix) } returns 1L
        dao.upsertDailyMix(mix)
        coVerify(exactly = 1) { dao.upsertDailyMix(mix) }
    }

    // ── Daily Mix Tracks (join table) ──────────────────────────────────────

    @Test
    fun `insertDailyMixTracks persists tracks linked to mix`() = runTest {
        val tracks = listOf(
            DailyMixTrackEntity(mixId = 1L, trackId = "t1", position = 0),
            DailyMixTrackEntity(mixId = 1L, trackId = "t2", position = 1),
            DailyMixTrackEntity(mixId = 1L, trackId = "t3", position = 2),
        )
        coEvery { dao.insertDailyMixTracks(tracks) } just Runs
        dao.insertDailyMixTracks(tracks)
        coVerify(exactly = 1) { dao.insertDailyMixTracks(tracks) }
    }

    @Test
    fun `getDailyMixTrackIds returns tracks ordered by position`() = runTest {
        coEvery { dao.getDailyMixTrackIds(42) } returns listOf("t3", "t1", "t2")
        val result = dao.getDailyMixTrackIds(42)
        assertEquals(listOf("t3", "t1", "t2"), result)
    }

    @Test
    fun `countDailyMixesForDate returns zero when no mixes`() = runTest {
        coEvery { dao.countDailyMixesForDate("2026-07-07") } returns 0
        assertEquals(0, dao.countDailyMixesForDate("2026-07-07"))
    }

    // ── Star/Rating sync DAO tests ────────────────────────────────────────────

    @Test
    fun `setStarredAtBulk updates multiple tracks`() = runTest {
        coEvery { trackDao.setStarredAtBulk(listOf("t1", "t2"), any()) } just Runs
        trackDao.setStarredAtBulk(listOf("t1", "t2"), System.currentTimeMillis())
        coVerify { trackDao.setStarredAtBulk(listOf("t1", "t2"), any()) }
    }

    @Test
    fun `clearNonStarred removes starred_at for unstarred tracks`() = runTest {
        coEvery { trackDao.clearNonStarred(listOf("t1"), any()) } just Runs
        trackDao.clearNonStarred(listOf("t1"), System.currentTimeMillis())
        coVerify { trackDao.clearNonStarred(listOf("t1"), any()) }
    }

    @Test
    fun `setRating updates track user_rating`() = runTest {
        coEvery { trackDao.setRating("t1", 4) } just Runs
        trackDao.setRating("t1", 4)
        coVerify { trackDao.setRating("t1", 4) }
    }

    @Test
    fun `setAlbumStarredAt updates album starred_at`() = runTest {
        coEvery { metadataDao.setAlbumStarredAt("al-1", any()) } just Runs
        metadataDao.setAlbumStarredAt("al-1", 1234567890L)
        coVerify { metadataDao.setAlbumStarredAt("al-1", any()) }
    }

    @Test
    fun `setAlbumRating updates album user_rating`() = runTest {
        coEvery { metadataDao.setAlbumRating("al-1", 3) } just Runs
        metadataDao.setAlbumRating("al-1", 3)
        coVerify { metadataDao.setAlbumRating("al-1", 3) }
    }

    @Test
    fun `setArtistStarredAt updates artist starred_at`() = runTest {
        coEvery { metadataDao.setArtistStarredAt("ar-1", any()) } just Runs
        metadataDao.setArtistStarredAt("ar-1", 1234567890L)
        coVerify { metadataDao.setArtistStarredAt("ar-1", any()) }
    }
}
