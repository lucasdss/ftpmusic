package com.lucasdss.ftpmusic.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    // ── Track.formattedDuration ────────────────────────────────────────────

    @Test
    fun `formattedDuration returns placeholder when duration is null`() {
        assertEquals("--:--", Track(id = "t", title = "x").formattedDuration)
    }

    @Test
    fun `formattedDuration zero`() {
        assertEquals("0:00", Track(id = "t", title = "x", duration = 0).formattedDuration)
    }

    @Test
    fun `formattedDuration pads seconds`() {
        assertEquals("1:05", Track(id = "t", title = "x", duration = 65).formattedDuration)
        assertEquals("10:09", Track(id = "t", title = "x", duration = 609).formattedDuration)
    }

    @Test
    fun `formattedDuration handles large durations`() {
        assertEquals("3661:00", Track(id = "t", title = "x", duration = 219660).formattedDuration)
    }

    // ── SearchResults.isEmpty / totalCount ─────────────────────────────────

    @Test
    fun `isEmpty is true for empty results`() {
        assertTrue(SearchResults().isEmpty)
    }

    @Test
    fun `isEmpty is false when any category is populated`() {
        assertFalse(SearchResults(artists = listOf(Artist(id = "a", name = "A"))).isEmpty)
        assertFalse(SearchResults(albums = listOf(Album(id = "a", name = "A"))).isEmpty)
        assertFalse(SearchResults(tracks = listOf(Track(id = "t", title = "T"))).isEmpty)
        assertFalse(SearchResults(playlists = listOf(Playlist(id = "p", name = "P"))).isEmpty)
    }

    @Test
    fun `totalCount sums all categories`() {
        val results = SearchResults(
            artists = listOf(Artist(id = "a1", name = "A1"), Artist(id = "a2", name = "A2")),
            albums = listOf(Album(id = "b1", name = "B1")),
            tracks = listOf(
                Track(id = "t1", title = "T1"),
                Track(id = "t2", title = "T2"),
                Track(id = "t3", title = "T3"),
            ),
            playlists = listOf(Playlist(id = "p1", name = "P1")),
        )
        assertEquals(7, results.totalCount)
        assertEquals(0, SearchResults().totalCount)
    }

    // ── Construction / default-value coverage for remaining models ─────────

    @Test
    fun `models construct with defaults and expose fields`() {
        val album = Album(id = "a", name = "Name", artist = "Artist", year = 1999, songCount = 10)
        assertEquals("a", album.id)
        assertEquals(1999, album.year)
        assertEquals(10, album.songCount)

        val track = Track(
            id = "t", title = "T", artist = "A", albumId = "a", duration = 100,
            trackNumber = 3, bitrate = 320, suffix = "mp3", sizeBytes = 1024, userRating = 5,
        )
        assertEquals(3, track.trackNumber)
        assertEquals(320, track.bitrate)
        assertEquals("mp3", track.suffix)
        assertEquals(1024, track.sizeBytes)
        assertEquals(5, track.userRating)

        val withTracks = AlbumWithTracks(album = album, tracks = listOf(track))
        assertEquals(1, withTracks.tracks.size)

        val artist = Artist(id = "ar", name = "Artist", coverArt = "ca", albumCount = 3)
        assertEquals(3, artist.albumCount)

        val playlist = Playlist(id = "p", name = "P", comment = "c", songCount = 5, duration = 300, coverArt = "ca")
        assertEquals(5, playlist.songCount)
        assertEquals(300, playlist.duration)
    }
}
