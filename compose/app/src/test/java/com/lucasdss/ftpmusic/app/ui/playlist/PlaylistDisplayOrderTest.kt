package com.lucasdss.ftpmusic.app.ui.playlist

import com.lucasdss.ftpmusic.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistDisplayOrderTest {

    private val tracks = listOf(
        Track("t1", "Zebra", artist = "B Artist", album = "Y Album", duration = 1),
        Track("t2", "Apple", artist = "A Artist", album = "Z Album", duration = 1),
        Track("t3", "Mango", artist = "A Artist", album = "X Album", duration = 1),
    )

    @Test
    fun `CUSTOM keeps source order and indices`() {
        val display = playlistDisplayTracks(tracks, PlaylistSortMode.CUSTOM, "")
        assertEquals(listOf(0, 1, 2), display.map { it.first })
        assertEquals(listOf("t1", "t2", "t3"), display.map { it.second.id })
    }

    @Test
    fun `TITLE sorts without rewriting source indices`() {
        val display = playlistDisplayTracks(tracks, PlaylistSortMode.TITLE, "")
        assertEquals(listOf("t2", "t3", "t1"), display.map { it.second.id })
        assertEquals(listOf(1, 2, 0), display.map { it.first })
    }

    @Test
    fun `query filters by title artist album`() {
        val display = playlistDisplayTracks(tracks, PlaylistSortMode.CUSTOM, "A Artist")
        assertEquals(listOf("t2", "t3"), display.map { it.second.id })
    }

    @Test
    fun `reorder enabled only for custom empty query`() {
        assertTrue(playlistReorderEnabled(PlaylistSortMode.CUSTOM, ""))
        assertFalse(playlistReorderEnabled(PlaylistSortMode.TITLE, ""))
        assertFalse(playlistReorderEnabled(PlaylistSortMode.CUSTOM, "a"))
    }
}
