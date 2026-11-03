package com.lucasdss.ftpmusic.app.data.sort

import com.lucasdss.ftpmusic.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumTrackOrderingTest {

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
    fun `within album keeps null disc and track numbers last`() {
        val ordered = listOf(
            track("noTags", "No tags"),
            track("noTrack", "No track", disc = 1, trackNum = null),
            track("b", "B", disc = 1, trackNum = 2),
            track("a", "A", disc = 1, trackNum = 1),
        ).sortedWith(AlbumTrackOrdering.withinAlbumComparator)

        assertEquals(listOf("a", "b", "noTrack", "noTags"), ordered.map { it.id })
    }

    @Test
    fun `group by album keeps first seen block order and sorts inside`() {
        val groups = AlbumTrackOrdering.groupByAlbum(
            listOf(
                track("z2", "Two", album = "Zebra", albumId = "z", disc = 1, trackNum = 2),
                track("a2", "Two", album = "Alpha", albumId = "a", disc = 1, trackNum = 2),
                track("z1", "One", album = "Zebra", albumId = "z", disc = 1, trackNum = 1),
                track("a1", "One", album = "Alpha", albumId = "a", disc = 1, trackNum = 1),
            ),
        )

        assertEquals(2, groups.size)
        // First-seen album order (Zebra then Alpha), sorted inside each block.
        assertEquals(listOf("z1", "z2"), groups[0].map { it.id })
        assertEquals(listOf("a1", "a2"), groups[1].map { it.id })
    }

    @Test
    fun `blank album ids never merge into one block`() {
        val groups = AlbumTrackOrdering.groupByAlbum(
            listOf(
                track("a", "Alpha"),
                track("b", "Beta"),
                track("c", "Gamma", albumId = "  "),
            ),
        )
        assertEquals(3, groups.size)
        assertEquals(listOf("a"), groups[0].map { it.id })
        assertEquals(listOf("b"), groups[1].map { it.id })
        assertEquals(listOf("c"), groups[2].map { it.id })
    }

    @Test
    fun `album name comparator lowercases and respects direction`() {
        val alpha = listOf(track("a", "One", album = "alpha", albumId = "a"))
        val zebra = listOf(track("z", "One", album = "Zebra", albumId = "z"))

        assertEquals(
            true,
            AlbumTrackOrdering.albumNameComparator(ascending = true).compare(alpha, zebra) < 0,
        )
        assertEquals(
            true,
            AlbumTrackOrdering.albumNameComparator(ascending = false).compare(alpha, zebra) > 0,
        )
    }

    @Test
    fun `album year comparator puts null years last in both directions`() {
        val years = mapOf("old" to 1990, "new" to 2020, "unknown" to null)
        val old = listOf(track("old", "O", albumId = "old"))
        val new = listOf(track("new", "N", albumId = "new"))
        val unknown = listOf(track("unknown", "U", albumId = "unknown"))

        val asc = AlbumTrackOrdering.albumYearComparator(years, ascending = true)
        assertEquals(true, asc.compare(old, new) < 0)
        assertEquals(true, asc.compare(old, unknown) < 0)
        assertEquals(true, asc.compare(new, unknown) < 0)

        val desc = AlbumTrackOrdering.albumYearComparator(years, ascending = false)
        assertEquals(true, desc.compare(new, old) < 0)
        assertEquals(true, desc.compare(old, unknown) < 0)
    }

    @Test
    fun `orphan tracks have no resolved year`() {
        val orphan = track("x", "X")
        assertEquals(null, AlbumTrackOrdering.resolveYear(orphan, mapOf("a" to 2000)))
        assertEquals(null, AlbumTrackOrdering.resolveYear(track("y", "Y", albumId = "a"), emptyMap()))
    }
}
