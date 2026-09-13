package com.lucasdss.ftpmusic.app.playback

import android.service.quicksettings.Tile
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for PlaybackTileService.computeTileState pure function.
 * Full TileService lifecycle needs Robolectric; these tests cover the isolated logic.
 */
class PlaybackTileServiceTest {

    @Test
    fun `playing state shows ACTIVE tile with title and artist`() {
        val state = PlaybackState(
            title = "Bohemian Rhapsody",
            artist = "Queen",
            isPlaying = true,
        )
        val (label, subtitle, tileState) = PlaybackTileService.computeTileState(state)

        assertEquals("Bohemian Rhapsody", label)
        assertEquals("Queen", subtitle)
        assertEquals(Tile.STATE_ACTIVE, tileState)
    }

    @Test
    fun `paused state shows INACTIVE tile with same metadata`() {
        val state = PlaybackState(
            title = "Hotel California",
            artist = "Eagles",
            isPlaying = false,
        )
        val (label, subtitle, tileState) = PlaybackTileService.computeTileState(state)

        assertEquals("Hotel California", label)
        assertEquals("Eagles", subtitle)
        assertEquals(Tile.STATE_INACTIVE, tileState)
    }

    @Test
    fun `null title shows default label`() {
        val state = PlaybackState(
            title = null,
            artist = null,
            isPlaying = false,
        )
        val (label, subtitle, tileState) = PlaybackTileService.computeTileState(state)

        assertEquals("FTP Music", label)
        assertEquals("Tap to open", subtitle)
        assertEquals(Tile.STATE_INACTIVE, tileState)
    }

    @Test
    fun `playing with null artist shows empty subtitle`() {
        val state = PlaybackState(
            title = "Instrumental",
            artist = null,
            isPlaying = true,
        )
        val (label, subtitle, tileState) = PlaybackTileService.computeTileState(state)

        assertEquals("Instrumental", label)
        assertEquals("", subtitle)
        assertEquals(Tile.STATE_ACTIVE, tileState)
    }

    @Test
    fun `casting state shows ACTIVE when playing`() {
        val state = PlaybackState(
            title = "Casting Track",
            artist = "Artist",
            isPlaying = true,
            isCasting = true,
            castDeviceName = "Living Room TV",
        )
        val (label, subtitle, tileState) = PlaybackTileService.computeTileState(state)

        assertEquals("Casting Track", label)
        assertEquals("Artist", subtitle)
        assertEquals(Tile.STATE_ACTIVE, tileState)
    }

    @Test
    fun `casting but paused shows INACTIVE`() {
        val state = PlaybackState(
            title = "Paused Cast",
            artist = "Artist",
            isPlaying = false,
            isCasting = true,
            castDeviceName = "Kitchen Speaker",
        )
        val (label, subtitle, tileState) = PlaybackTileService.computeTileState(state)

        assertEquals("Paused Cast", label)
        assertEquals("Artist", subtitle)
        assertEquals(Tile.STATE_INACTIVE, tileState)
    }

    @Test
    fun `empty title falls back to default label`() {
        val state = PlaybackState(
            title = "",
            artist = "",
            isPlaying = false,
        )
        val (label, subtitle, tileState) = PlaybackTileService.computeTileState(state)
        assertEquals("FTP Music", label)
        assertEquals("Tap to open", subtitle)
        assertEquals(Tile.STATE_INACTIVE, tileState)
    }

    @Test
    fun `blank title falls back to default label`() {
        val state = PlaybackState(
            title = "   ",
            artist = "Some Artist",
            isPlaying = true,
        )
        val (label, subtitle, _) = PlaybackTileService.computeTileState(state)
        assertEquals("FTP Music", label)
        assertEquals("Tap to open", subtitle)
    }
}
