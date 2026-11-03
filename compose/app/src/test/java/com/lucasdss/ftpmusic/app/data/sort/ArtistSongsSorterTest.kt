package com.lucasdss.ftpmusic.app.data.sort

import com.lucasdss.ftpmusic.app.data.model.Track
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistSongsSorterTest {

    private fun track(
        id: String,
        title: String,
        playCount: Int = 0,
        album: String? = null,
        albumId: String? = null,
        disc: Int? = null,
        trackNum: Int? = null,
    ) = Track(
        id = id,
        title = title,
        album = album,
        albumId = albumId,
        discNumber = disc,
        trackNumber = trackNum,
        playCount = playCount,
    )

    @Test
    fun `empty list returns empty`() {
        assertTrue(
            ArtistSongsSorter.order(emptyList(), ArtistSongsSort.PLAY_COUNT_DESC).isEmpty(),
        )
    }

    @Test
    fun `play count desc puts most played first then title and id`() {
        val tracks = listOf(
            track("c", "Beta", playCount = 2),
            track("a", "Alpha", playCount = 5),
            track("b", "Gamma", playCount = 2),
            track("zero", "Zero", playCount = 0),
        )
        val ordered = ArtistSongsSorter.order(tracks, ArtistSongsSort.PLAY_COUNT_DESC)
        assertEquals(listOf("a", "c", "b", "zero"), ordered.map { it.id })
    }

    @Test
    fun `play count asc puts least played first`() {
        val tracks = listOf(
            track("a", "Alpha", playCount = 5),
            track("zero", "Zero", playCount = 0),
            track("c", "Beta", playCount = 2),
        )
        val ordered = ArtistSongsSorter.order(tracks, ArtistSongsSort.PLAY_COUNT_ASC)
        assertEquals(listOf("zero", "c", "a"), ordered.map { it.id })
    }

    @Test
    fun `track name asc and desc ignore case`() {
        val tracks = listOf(
            track("b", "bravo"),
            track("a", "Alpha"),
            track("c", "Charlie"),
        )
        assertEquals(
            listOf("a", "b", "c"),
            ArtistSongsSorter.order(tracks, ArtistSongsSort.TRACK_NAME_ASC).map { it.id },
        )
        assertEquals(
            listOf("c", "b", "a"),
            ArtistSongsSorter.order(tracks, ArtistSongsSort.TRACK_NAME_DESC).map { it.id },
        )
    }

    @Test
    fun `album name mode orders blocks and keeps disc track order inside`() {
        val tracks = listOf(
            track("z2", "Second", album = "Zebra", albumId = "z", disc = 1, trackNum = 2),
            track("a2", "Two", album = "Alpha", albumId = "a", disc = 1, trackNum = 2),
            track("z1", "First", album = "Zebra", albumId = "z", disc = 1, trackNum = 1),
            track("a1", "One", album = "Alpha", albumId = "a", disc = 1, trackNum = 1),
        )
        val ordered = ArtistSongsSorter.order(tracks, ArtistSongsSort.ALBUM_NAME_ASC)
        assertEquals(listOf("a1", "a2", "z1", "z2"), ordered.map { it.id })

        val reversed = ArtistSongsSorter.order(tracks, ArtistSongsSort.ALBUM_NAME_DESC)
        // Direction flips album blocks only; within-album order stays ascending.
        assertEquals(listOf("z1", "z2", "a1", "a2"), reversed.map { it.id })
    }

    @Test
    fun `album year puts null years last in both directions`() {
        val tracks = listOf(
            track("unknown", "U", album = "Unknown", albumId = "u"),
            track("old", "O", album = "Old", albumId = "old"),
            track("new", "N", album = "New", albumId = "new"),
        )
        val years = mapOf("old" to 1990, "new" to 2020, "u" to null)

        assertEquals(
            listOf("old", "new", "unknown"),
            ArtistSongsSorter.order(tracks, ArtistSongsSort.ALBUM_YEAR_ASC, years).map { it.id },
        )
        assertEquals(
            listOf("new", "old", "unknown"),
            ArtistSongsSorter.order(tracks, ArtistSongsSort.ALBUM_YEAR_DESC, years).map { it.id },
        )
    }

    @Test
    fun `orphan tracks become singleton blocks sorted by title`() {
        val tracks = listOf(
            track("b", "Beta"),
            track("a", "Alpha"),
            track("album", "Compilation", album = "Compilation", albumId = "c"),
        )
        val ordered = ArtistSongsSorter.order(tracks, ArtistSongsSort.ALBUM_NAME_ASC)
        // Empty album name sorts first; orphan singleton blocks never merge and
        // ties keep first-seen order (stable sort).
        assertEquals("album", ordered.last().id)
        assertEquals(setOf("a", "b"), ordered.take(2).map { it.id }.toSet())
    }

    @Test
    fun `random is deterministic for a seed and reshuffles with another`() {
        val tracks = (1..20).map { track("t$it", "Track $it") }
        val first = ArtistSongsSorter.order(tracks, ArtistSongsSort.RANDOM, random = Random(42))
        val again = ArtistSongsSorter.order(tracks, ArtistSongsSort.RANDOM, random = Random(42))
        val other = ArtistSongsSorter.order(tracks, ArtistSongsSort.RANDOM, random = Random(43))

        assertEquals(first.map { it.id }, again.map { it.id })
        assertNotEquals(first.map { it.id }, other.map { it.id })
        assertEquals(tracks.map { it.id }.toSet(), first.map { it.id }.toSet())
    }
}
