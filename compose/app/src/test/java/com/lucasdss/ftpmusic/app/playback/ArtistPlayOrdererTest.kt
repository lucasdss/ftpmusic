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
        assertTrue(ArtistPlayOrderer.order(emptyList(), ArtistPlayOrder.SHUFFLE_ALBUMS).isEmpty())
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
    fun `album name asc and desc order album blocks`() {
        val tracks = listOf(
            track("z2", "B", album = "Zebra", albumId = "z", trackNum = 2),
            track("z1", "A", album = "Zebra", albumId = "z", trackNum = 1),
            track("a1", "X", album = "Alpha", albumId = "a", trackNum = 1),
        )
        val asc = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.ALBUM_NAME_ASC)
        assertEquals(listOf("a1", "z1", "z2"), asc.map { it.id })
        val desc = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.ALBUM_NAME_DESC)
        assertEquals(listOf("z1", "z2", "a1"), desc.map { it.id })
    }

    @Test
    fun `shuffle albums keeps within-album track order`() {
        val tracks = listOf(
            track("a2", "A2", album = "A", albumId = "a", disc = 1, trackNum = 2),
            track("a1", "A1", album = "A", albumId = "a", disc = 1, trackNum = 1),
            track("b2", "B2", album = "B", albumId = "b", disc = 1, trackNum = 2),
            track("b1", "B1", album = "B", albumId = "b", disc = 1, trackNum = 1),
        )
        val ordered = ArtistPlayOrderer.order(
            tracks,
            ArtistPlayOrder.SHUFFLE_ALBUMS,
            random = Random(42),
        )
        val aBlock = ordered.filter { it.albumId == "a" }.map { it.id }
        val bBlock = ordered.filter { it.albumId == "b" }.map { it.id }
        assertEquals(listOf("a1", "a2"), aBlock)
        assertEquals(listOf("b1", "b2"), bBlock)
        // Album blocks stay contiguous
        val ids = ordered.map { it.id }
        val aStart = ids.indexOf("a1")
        val bStart = ids.indexOf("b1")
        assertEquals(listOf("a1", "a2"), ids.subList(aStart, aStart + 2))
        assertEquals(listOf("b1", "b2"), ids.subList(bStart, bStart + 2))
    }

    @Test
    fun `shuffle albums permutes album blocks with seeded random`() {
        val tracks = listOf(
            track("a1", "A1", album = "A", albumId = "a", trackNum = 1),
            track("b1", "B1", album = "B", albumId = "b", trackNum = 1),
            track("c1", "C1", album = "C", albumId = "c", trackNum = 1),
            track("d1", "D1", album = "D", albumId = "d", trackNum = 1),
            track("e1", "E1", album = "E", albumId = "e", trackNum = 1),
        )
        val a = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.SHUFFLE_ALBUMS, random = Random(1))
        val b = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.SHUFFLE_ALBUMS, random = Random(1))
        val c = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.SHUFFLE_ALBUMS, random = Random(2))
        assertEquals(a.map { it.id }, b.map { it.id })
        assertNotEquals(a.map { it.id }, c.map { it.id })
        assertEquals(tracks.map { it.id }.toSet(), a.map { it.id }.toSet())
    }

    @Test
    fun `shuffle songs permutes with seeded random`() {
        val tracks = (1..20).map { track("t$it", "T$it") }
        val a = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.SHUFFLE_SONGS, random = Random(1))
        val b = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.SHUFFLE_SONGS, random = Random(1))
        val c = ArtistPlayOrderer.order(tracks, ArtistPlayOrder.SHUFFLE_SONGS, random = Random(2))
        assertEquals(a.map { it.id }, b.map { it.id })
        assertNotEquals(a.map { it.id }, c.map { it.id })
        assertEquals(tracks.map { it.id }.toSet(), a.map { it.id }.toSet())
    }

    @Test
    fun `fromKey defaults to shuffle albums and migrates legacy title_asc`() {
        assertEquals(ArtistPlayOrder.SHUFFLE_ALBUMS, ArtistPlayOrder.fromKey(null))
        assertEquals(ArtistPlayOrder.SHUFFLE_ALBUMS, ArtistPlayOrder.fromKey("bogus"))
        assertEquals(ArtistPlayOrder.SHUFFLE_SONGS, ArtistPlayOrder.fromKey("shuffle"))
        assertEquals(ArtistPlayOrder.ALBUM_NAME_ASC, ArtistPlayOrder.fromKey("title_asc"))
        assertEquals(ArtistPlayOrder.ALBUM_NAME_DESC, ArtistPlayOrder.fromKey("album_name_desc"))
        assertTrue(ArtistPlayOrder.ALBUM_YEAR_ASC.withAscending(false) == ArtistPlayOrder.ALBUM_YEAR_DESC)
        assertTrue(ArtistPlayOrder.ALBUM_NAME_DESC.withAscending(true) == ArtistPlayOrder.ALBUM_NAME_ASC)
    }
}
