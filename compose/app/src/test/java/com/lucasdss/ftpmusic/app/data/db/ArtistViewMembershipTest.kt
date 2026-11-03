package com.lucasdss.ftpmusic.app.data.db

import com.lucasdss.ftpmusic.app.data.sort.ArtistSongsCursor
import com.lucasdss.ftpmusic.app.data.sort.ArtistSongsSort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistViewMembershipTest {

    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)

    @Test
    fun `loadAlbums merges id name and track-artist sources distinct by id`() = runTest {
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns listOf(
            CachedAlbumEntity(id = "a1", name = "Own", artist = "X"),
            CachedAlbumEntity(id = "shared", name = "Shared", artist = "X"),
        )
        coEvery { metadataDao.getAlbumsByArtistName("X") } returns listOf(
            CachedAlbumEntity(id = "shared", name = "Shared", artist = "X"),
            CachedAlbumEntity(id = "a2", name = "ByName", artist = "X"),
        )
        coEvery { metadataDao.getAlbumsByTrackArtistId("ar-1") } returns listOf(
            CachedAlbumEntity(id = "ost", name = "OST", artist = "Various"),
        )

        val albums = ArtistViewMembership.loadAlbums(metadataDao, "ar-1", "X")
        assertEquals(listOf("a1", "shared", "a2", "ost"), albums.map { it.id })
    }

    @Test
    fun `loadAllTracks delegates to artist-view DAO with empty name when null`() = runTest {
        coEvery { trackDao.getAllTracksForArtistView("ar-1", "") } returns listOf(
            TrackEntity(id = "t1", title = "One", artistId = "ar-1"),
        )
        val tracks = ArtistViewMembership.loadAllTracks(trackDao, "ar-1", null)
        assertEquals(1, tracks.size)
        coVerify { trackDao.getAllTracksForArtistView("ar-1", "") }
    }

    @Test
    fun `loadTracksPage uses after-cursor variant when title and id cursors set`() = runTest {
        coEvery {
            trackDao.getTracksForArtistViewAfter("ar-1", "X", "M", "id-m", 50)
        } returns emptyList()
        ArtistViewMembership.loadTracksPage(
            trackDao,
            "ar-1",
            "X",
            50,
            titleCursor = "M",
            idCursor = "id-m",
        )
        coVerify { trackDao.getTracksForArtistViewAfter("ar-1", "X", "M", "id-m", 50) }
    }

    @Test
    fun `loadTracksPage falls back to first page when id cursor missing`() = runTest {
        coEvery { trackDao.getTracksForArtistView("ar-1", "X", 50) } returns emptyList()
        ArtistViewMembership.loadTracksPage(trackDao, "ar-1", "X", 50, titleCursor = "M")
        coVerify { trackDao.getTracksForArtistView("ar-1", "X", 50) }
        coVerify(exactly = 0) {
            trackDao.getTracksForArtistViewAfter(any(), any(), any(), any(), any())
        }
    }

    // ── ADR-0152: Songs keyset dispatch ───────────────────────────────────

    @Test
    fun `loadSongsKeysetPage returns empty for in-memory sorts without touching the DAO`() = runTest {
        val random = ArtistViewMembership.loadSongsKeysetPage(
            trackDao,
            "ar-1",
            "X",
            ArtistSongsSort.RANDOM,
            50,
        )
        val albumName = ArtistViewMembership.loadSongsKeysetPage(
            trackDao,
            "ar-1",
            "X",
            ArtistSongsSort.ALBUM_NAME_DESC,
            50,
        )
        val albumYear = ArtistViewMembership.loadSongsKeysetPage(
            trackDao,
            "ar-1",
            "X",
            ArtistSongsSort.ALBUM_YEAR_ASC,
            50,
        )

        assertEquals(emptyList<TrackEntity>(), random)
        assertEquals(emptyList<TrackEntity>(), albumName)
        assertEquals(emptyList<TrackEntity>(), albumYear)
        coVerify(exactly = 0) { trackDao.getTracksForArtistView(any(), any(), any()) }
        coVerify(exactly = 0) {
            trackDao.getTracksForArtistViewByPlayCountDesc(any(), any(), any())
        }
    }

    @Test
    fun `loadSongsKeysetPage routes play-count direction and cursors`() = runTest {
        coEvery { trackDao.getTracksForArtistViewByPlayCountDesc("ar-1", "X", 50) } returns emptyList()
        coEvery { trackDao.getTracksForArtistViewByPlayCountAsc("ar-1", "X", 50) } returns emptyList()
        coEvery {
            trackDao.getTracksForArtistViewAfterByPlayCountAsc("ar-1", "X", 3, "M", "id-m", 50)
        } returns emptyList()
        coEvery {
            trackDao.getTracksForArtistViewAfterByPlayCountDesc("ar-1", "X", 9, "N", "id-n", 50)
        } returns emptyList()

        ArtistViewMembership.loadSongsKeysetPage(
            trackDao,
            "ar-1",
            "X",
            ArtistSongsSort.PLAY_COUNT_DESC,
            50,
        )
        ArtistViewMembership.loadSongsKeysetPage(
            trackDao,
            "ar-1",
            "X",
            ArtistSongsSort.PLAY_COUNT_ASC,
            50,
        )
        ArtistViewMembership.loadSongsKeysetPage(
            trackDao,
            "ar-1",
            "X",
            ArtistSongsSort.PLAY_COUNT_ASC,
            50,
            ArtistSongsCursor.PlayCount(playCount = 3, title = "M", id = "id-m"),
        )
        ArtistViewMembership.loadSongsKeysetPage(
            trackDao,
            "ar-1",
            "X",
            ArtistSongsSort.PLAY_COUNT_DESC,
            50,
            ArtistSongsCursor.PlayCount(playCount = 9, title = "N", id = "id-n"),
        )

        coVerify { trackDao.getTracksForArtistViewByPlayCountDesc("ar-1", "X", 50) }
        coVerify { trackDao.getTracksForArtistViewByPlayCountAsc("ar-1", "X", 50) }
        coVerify { trackDao.getTracksForArtistViewAfterByPlayCountAsc("ar-1", "X", 3, "M", "id-m", 50) }
        coVerify { trackDao.getTracksForArtistViewAfterByPlayCountDesc("ar-1", "X", 9, "N", "id-n", 50) }
    }

    @Test
    fun `loadSongsKeysetPage routes title directions and rejects a mismatched cursor`() = runTest {
        coEvery { trackDao.getTracksForArtistView("ar-1", "X", 50) } returns emptyList()
        coEvery { trackDao.getTracksForArtistViewByTitleDesc("ar-1", "X", 50) } returns emptyList()
        coEvery { trackDao.getTracksForArtistViewAfter("ar-1", "X", "M", "id-m", 50) } returns emptyList()
        coEvery {
            trackDao.getTracksForArtistViewAfterByTitleDesc("ar-1", "X", "N", "id-n", 50)
        } returns emptyList()

        ArtistViewMembership.loadSongsKeysetPage(trackDao, "ar-1", "X", ArtistSongsSort.TRACK_NAME_ASC, 50)
        ArtistViewMembership.loadSongsKeysetPage(trackDao, "ar-1", "X", ArtistSongsSort.TRACK_NAME_DESC, 50)
        ArtistViewMembership.loadSongsKeysetPage(
            trackDao,
            "ar-1",
            "X",
            ArtistSongsSort.TRACK_NAME_ASC,
            50,
            ArtistSongsCursor.Title(title = "M", id = "id-m"),
        )
        ArtistViewMembership.loadSongsKeysetPage(
            trackDao,
            "ar-1",
            "X",
            ArtistSongsSort.TRACK_NAME_DESC,
            50,
            ArtistSongsCursor.Title(title = "N", id = "id-n"),
        )
        // A play-count cursor must never be applied to a title keyset.
        val mismatched = ArtistViewMembership.loadSongsKeysetPage(
            trackDao,
            "ar-1",
            "X",
            ArtistSongsSort.TRACK_NAME_ASC,
            50,
            ArtistSongsCursor.PlayCount(playCount = 0, title = "M", id = "id-m"),
        )

        assertEquals(emptyList<TrackEntity>(), mismatched)
        coVerify { trackDao.getTracksForArtistView("ar-1", "X", 50) }
        coVerify { trackDao.getTracksForArtistViewByTitleDesc("ar-1", "X", 50) }
        coVerify { trackDao.getTracksForArtistViewAfter("ar-1", "X", "M", "id-m", 50) }
        coVerify { trackDao.getTracksForArtistViewAfterByTitleDesc("ar-1", "X", "N", "id-n", 50) }
    }

    @Test
    fun `loadAlbumYears chunks ids and short-circuits an empty request`() = runTest {
        coEvery { metadataDao.getAlbumYearRows(any()) } returns emptyList()
        val ids = (1..1_001).map { "al-$it" }

        ArtistViewMembership.loadAlbumYears(metadataDao, ids)

        coVerify(exactly = 3) { metadataDao.getAlbumYearRows(any()) }
        assertEquals(emptyMap<String, Int?>(), ArtistViewMembership.loadAlbumYears(metadataDao, emptyList()))
        // The empty call must not issue another query.
        coVerify(exactly = 3) { metadataDao.getAlbumYearRows(any()) }
    }
}
