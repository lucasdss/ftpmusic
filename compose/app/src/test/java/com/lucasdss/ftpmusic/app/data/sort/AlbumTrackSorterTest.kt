package com.lucasdss.ftpmusic.app.data.sort

import com.lucasdss.ftpmusic.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumTrackSorterTest {

    private fun track(id: String, title: String, disc: Int? = null, trackNum: Int? = null, playCount: Int = 0) = Track(
        id = id,
        title = title,
        discNumber = disc,
        trackNumber = trackNum,
        playCount = playCount,
    )

    @Test
    fun `disc track default matches the historical comparator`() {
        val tracks = listOf(
            track("b", "B", disc = 1, trackNum = 2),
            track("noTag", "No tag"),
            track("a", "A", disc = 1, trackNum = 1),
            track("disc2", "First on two", disc = 2, trackNum = 1),
        )
        val ordered = AlbumTrackSorter.sort(tracks, AlbumTrackSort.DISC_TRACK)
        // Unknown disc sorts after numbered discs; within disc, track number wins.
        assertEquals(listOf("a", "b", "disc2", "noTag"), ordered.map { it.id })
    }

    @Test
    fun `play count desc puts most played first then disc track tie-breakers`() {
        val tracks = listOf(
            track("a1", "One", disc = 1, trackNum = 1, playCount = 1),
            track("a2", "Two", disc = 1, trackNum = 2, playCount = 9),
            track("a3", "Three", disc = 1, trackNum = 3, playCount = 1),
        )
        val ordered = AlbumTrackSorter.sort(tracks, AlbumTrackSort.PLAY_COUNT_DESC)
        assertEquals(listOf("a2", "a1", "a3"), ordered.map { it.id })
    }

    @Test
    fun `play count asc puts least played first`() {
        val tracks = listOf(
            track("a1", "One", disc = 1, trackNum = 1, playCount = 4),
            track("a2", "Two", disc = 1, trackNum = 2, playCount = 0),
            track("a3", "Three", disc = 1, trackNum = 3, playCount = 2),
        )
        val ordered = AlbumTrackSorter.sort(tracks, AlbumTrackSort.PLAY_COUNT_ASC)
        assertEquals(listOf("a2", "a3", "a1"), ordered.map { it.id })
    }

    @Test
    fun `all zero play counts fall back to normal track order`() {
        val tracks = listOf(
            track("a2", "Two", disc = 1, trackNum = 2),
            track("a1", "One", disc = 1, trackNum = 1),
            track("a3", "Three", disc = 1, trackNum = 3),
        )
        assertEquals(
            listOf("a1", "a2", "a3"),
            AlbumTrackSorter.sort(tracks, AlbumTrackSort.PLAY_COUNT_DESC).map { it.id },
        )
        assertEquals(
            listOf("a1", "a2", "a3"),
            AlbumTrackSorter.sort(tracks, AlbumTrackSort.PLAY_COUNT_ASC).map { it.id },
        )
    }
}
