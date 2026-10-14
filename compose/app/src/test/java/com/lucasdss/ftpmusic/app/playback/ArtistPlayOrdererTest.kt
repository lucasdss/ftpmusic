package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.model.Track
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPlayOrdererTest {

    private fun track(
        id: String,
        title: String,
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
    )

    @Test
    fun `empty list returns empty`() {
        assertTrue(ArtistPlayOrderer.order(emptyList(), ArtistPlayOrder.SHUFFLE).isEmpty())
    }

    @Test
    fun `title asc sorts by title then id`() {
        val tracks = listOf(
            track("2", "Beta"),
            track("1", "Alpha"),
            track("3", "alpha"), // same lowercase as Alpha → id tiebreak after case fold
        )
        val ordered = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.TITLE_ASC)
        assertEquals(listOf("1", "3", "2"), ordered.map { it.id })
    }

    @Test
    fun `album year asc puts older albums first and null years last`() {
        val tracks = listOf(
            track("n1", "Z", album = "Unknown", albumId = "u"),
            track("b1", "Song", album = "New", albumId = "new", disc = 1, trackNum = 1),
            track("a2", "B", album = "Old", albumId = "old", disc = 1, trackNum = 2),
            track("a1", "A", album = "Old", albumId = "old", disc = 1, trackNum = 1),
        )
        val years = mapOf("old" to 1990, "new" to 2020, "u" to null)
        val ordered = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.ALBUM_YEAR_ASC, years)
        assertEquals(listOf("a1", "a2", "b1", "n1"), ordered.map { it.id })
    }

    @Test
    fun `album year desc puts newer albums first and null years last`() {
        val tracks = listOf(
            track("n1", "Z", album = "Unknown", albumId = "u"),
            track("old1", "A", album = "Old", albumId = "old"),
            track("new1", "B", album = "New", albumId = "new"),
        )
        val years = mapOf("old" to 1990, "new" to 2020)
        val ordered = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.ALBUM_YEAR_DESC, years)
        assertEquals(listOf("new1", "old1", "n1"), ordered.map { it.id })
    }

    @Test
    fun `null years among themselves sort by album name`() {
        val tracks = listOf(
            track("2", "T", album = "Zebra", albumId = "z"),
            track("1", "T", album = "Alpha", albumId = "a"),
        )
        val ordered = ArtistPlayOrderer.order(
            tracks,
            ArtistPlayOrder.ALBUM_YEAR_ASC,
            mapOf("z" to null, "a" to null),
        )
        assertEquals(listOf("1", "2"), ordered.map { it.id })
    }

    @Test
    fun `missing albumId treated as unknown year`() {
        val tracks = listOf(
            track("dated", "A", album = "X", albumId = "x"),
            track("orphan", "B", album = "Y", albumId = null),
        )
        val ordered = ArtistPlayOrderer.order(
            tracks,
            ArtistPlayOrder.ALBUM_YEAR_ASC,
            mapOf("x" to 2000),
        )
        assertEquals(listOf("dated", "orphan"), ordered.map { it.id })
    }

    @Test
    fun `shuffle permutes with seeded random`() {
        val tracks = (1..20).map { track("t$it", "T$it") }
        val a = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.SHUFFLE, random = Random(1))
        val b = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.SHUFFLE, random = Random(1))
        val c = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.SHUFFLE, random = Random(2))
        assertEquals(a.map { it.id }, b.map { it.id })
        assertNotEquals(a.map { it.id }, c.map { it.id })
        assertEquals(tracks.map { it.id }.toSet(), a.map { it.id }.toSet())
    }

    @Test
    fun `fromKey defaults to shuffle`() {
        assertEquals(ArtistPlayOrder.SHUFFLE, ArtistPlayOrder.fromKey(null))
        assertEquals(ArtistPlayOrder.SHUFFLE, ArtistPlayOrder.fromKey("bogus"))
        assertEquals(ArtistPlayOrder.TITLE_ASC, ArtistPlayOrder.fromKey("title_asc"))
    }
}
