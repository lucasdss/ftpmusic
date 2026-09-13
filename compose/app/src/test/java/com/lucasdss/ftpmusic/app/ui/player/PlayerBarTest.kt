package com.lucasdss.ftpmusic.app.ui.player

import com.lucasdss.ftpmusic.app.playback.PlaybackState
import org.junit.Assert.*
import org.junit.Test

class PlayerBarTest {

    // ── Gradient colors ────────────────────────────────────────────

    @Test
    fun `PlayerBarColors defaults to null darkMuted and vibrant`() {
        val colors = PlayerBarColors()
        assertNull(colors.darkMuted)
        assertNull(colors.vibrant)
    }

    @Test
    fun `PlayerBarColors preserves darkMuted and vibrant values`() {
        val dark = androidx.compose.ui.graphics.Color.Red
        val vibrant = androidx.compose.ui.graphics.Color.Blue
        val colors = PlayerBarColors(darkMuted = dark, vibrant = vibrant)
        assertEquals(dark, colors.darkMuted)
        assertEquals(vibrant, colors.vibrant)
    }

    // ── formatPlayerBarTime ─────────────────────────────────────────

    @Test
    fun `formatPlayerBarTime formats zero`() {
        assertEquals("0:00", formatPlayerBarTime(0L))
    }

    @Test
    fun `formatPlayerBarTime formats one minute thirty`() {
        assertEquals("1:30", formatPlayerBarTime(90_000L))
    }

    @Test
    fun `formatPlayerBarTime formats ten minutes`() {
        assertEquals("10:00", formatPlayerBarTime(600_000L))
    }

    @Test
    fun `formatPlayerBarTime handles hour duration`() {
        assertEquals("60:00", formatPlayerBarTime(3_600_000L))
    }

    @Test
    fun `formatPlayerBarTime formats single digit seconds with zero pad`() {
        assertEquals("2:07", formatPlayerBarTime(127_000L))
    }

    @Test
    fun `formatPlayerBarTime handles negative time as zero`() {
        // Negative time can occur briefly before duration is known
        val result = formatPlayerBarTime(-100L)
        assertEquals("0:00", result)
    }

    @Test
    fun `formatPlayerBarTime handles max long safely`() {
        val result = formatPlayerBarTime(Long.MAX_VALUE)
        assertTrue(result.isNotEmpty())
        assertTrue(result.contains(":"))
    }

    // ── Progress fraction edge cases ──────────────────────────────

    @Test
    fun `progress fraction is zero when duration is zero`() {
        val fraction = if (0L > 0) 5000L.toFloat() / 0L else 0f
        assertEquals(0f, fraction)
    }

    @Test
    fun `progress fraction is one when position equals duration`() {
        val position = 10000L
        val duration = 10000L
        val fraction = position.toFloat() / duration
        assertEquals(1f, fraction)
    }

    @Test
    fun `progress fraction clamped to 0-1 range`() {
        // Simulates what coerceIn does for seek drag
        val dragFrac = (-0.5f).coerceIn(0f, 1f)
        assertEquals(0f, dragFrac)
        val overDrag = (1.5f).coerceIn(0f, 1f)
        assertEquals(1f, overDrag)
    }

    // ── isCasting state in PlaybackState ───────────────────────────

    @Test
    fun `isCasting defaults to false in PlaybackState`() {
        val state = PlaybackState()
        assertFalse(state.isCasting)
        assertNull(state.castDeviceName)
    }

    @Test
    fun `PlaybackState reflects Cast connection`() {
        val state = PlaybackState(isCasting = true, castDeviceName = "Mini Speaker")
        assertTrue(state.isCasting)
        assertEquals("Mini Speaker", state.castDeviceName)
    }

    // ── Volume and mute ────────────────────────────────────────────

    @Test
    fun `volume defaults to 1f in PlaybackState`() {
        val state = PlaybackState()
        assertEquals(1.0f, state.volume)
    }

    @Test
    fun `muted defaults to false in PlaybackState`() {
        val state = PlaybackState()
        assertFalse(state.muted)
    }

    // ── Track info defaults ────────────────────────────────────────

    @Test
    fun `PlaybackState isVisible is true when title is set`() {
        val state = PlaybackState(title = "Song")
        assertTrue(state.isVisible)
    }

    @Test
    fun `PlaybackState isVisible is false when title is null`() {
        val state = PlaybackState(title = null)
        assertFalse(state.isVisible)
    }

    // ── artistId + albumId in PlaybackState ────────────────────────

    @Test
    fun `PlaybackState artistId defaults to null`() {
        assertNull(PlaybackState().artistId)
    }

    @Test
    fun `PlaybackState albumId defaults to null`() {
        assertNull(PlaybackState().albumId)
    }

    @Test
    fun `PlaybackState preserves artistId and albumId on copy`() {
        val state = PlaybackState(artistId = "art-1", albumId = "alb-2", title = "T")
        val copied = state.copy(title = "T2")
        assertEquals("art-1", copied.artistId)
        assertEquals("alb-2", copied.albumId)
    }

    // ── Cast state in PlaybackState ────────────────────────────────

    @Test
    fun `PlaybackState isCasting can be set to true with device name`() {
        val state = PlaybackState(isCasting = true, castDeviceName = "Living Room TV")
        assertTrue(state.isCasting)
        assertEquals("Living Room TV", state.castDeviceName)
    }

    @Test
    fun `PlaybackState isCasting toggles back to false`() {
        val casting = PlaybackState(isCasting = true, castDeviceName = "Speaker")
        val disconnected = casting.copy(isCasting = false, castDeviceName = null)
        assertFalse(disconnected.isCasting)
        assertNull(disconnected.castDeviceName)
    }
}
