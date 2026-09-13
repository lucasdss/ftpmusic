package com.lucasdss.ftpmusic.app.ui.player

import androidx.compose.ui.graphics.Color
import com.lucasdss.ftpmusic.app.playback.PlaybackState
import com.lucasdss.ftpmusic.app.playback.PlaybackTileService
import org.junit.Assert.*
import org.junit.Test

class WaveformScrubberTest {

    @Test
    fun `formatWaveformTime formats seconds correctly`() {
        assertEquals("0:00", formatWaveformTime(0L))
        assertEquals("0:05", formatWaveformTime(5000L))
        assertEquals("1:00", formatWaveformTime(60000L))
        assertEquals("1:30", formatWaveformTime(90000L))
        assertEquals("10:59", formatWaveformTime(659000L))
        assertEquals("0:00", formatWaveformTime(-5000L))
    }

    @Test
    fun `lerpColor returns start at fraction zero`() {
        val start = Color(0xFFFF0000) // red
        val end = Color(0xFF0000FF) // blue
        val result = lerpColor(start, end, 0f)
        assertEquals(1f, result.red)
        assertEquals(0f, result.green)
        assertEquals(0f, result.blue, 0.01f)
    }

    @Test
    fun `lerpColor returns end at fraction one`() {
        val start = Color(0xFFFF0000)
        val end = Color(0xFF0000FF)
        val result = lerpColor(start, end, 1f)
        assertEquals(0f, result.red, 0.01f)
        assertEquals(0f, result.green)
        assertEquals(1f, result.blue)
    }

    @Test
    fun `lerpColor returns midpoint at fraction half`() {
        val start = Color(0xFFFF0000)
        val end = Color(0xFF0000FF)
        val result = lerpColor(start, end, 0.5f)
        assertEquals(0.5f, result.red, 0.01f)
        assertEquals(0f, result.green)
        assertEquals(0.5f, result.blue, 0.01f)
    }

    @Test
    fun `lerpColor handles teal to purple gradient at third point`() {
        val teal = Color(0xFF00C8B4)
        val purple = Color(0xFFB040E8)
        val result = lerpColor(teal, purple, 0.33f)
        assertTrue(result.red in 0f..purple.red)
        assertTrue(result.blue > teal.blue)
    }

    @Test
    fun `computeTileState returns track info when title present`() {
        val state = PlaybackState(title = "Test Track", artist = "Test Artist", isPlaying = true)
        val (label, subtitle, tileState) = PlaybackTileService.computeTileState(state)
        assertEquals("Test Track", label)
        assertEquals("Test Artist", subtitle)
        assertEquals(android.service.quicksettings.Tile.STATE_ACTIVE, tileState)
    }

    // formatWaveformTime is private but tested via the companion object approach
    // or we can make it internal for testing. For now, tested indirectly.
}
