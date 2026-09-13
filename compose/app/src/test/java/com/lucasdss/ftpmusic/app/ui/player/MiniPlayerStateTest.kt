package com.lucasdss.ftpmusic.app.ui.player

import org.junit.Assert.*
import org.junit.Test

class MiniPlayerStateTest {

    @Test
    fun `progressFraction is zero when duration is zero`() {
        val state = MiniPlayerUiState(position = 0L, duration = 0L)
        assertEquals(0f, state.progressFraction)
    }

    @Test
    fun `progressFraction is half when position is half of duration`() {
        val state = MiniPlayerUiState(position = 30000L, duration = 60000L)
        assertEquals(0.5f, state.progressFraction)
    }

    @Test
    fun `progressFraction is clamped when position exceeds duration`() {
        val state = MiniPlayerUiState(position = 90000L, duration = 60000L)
        assertEquals(1f, state.progressFraction)
    }

    @Test
    fun `formatTimeMs formats zero correctly`() {
        assertEquals("0:00", formatTimeMs(0L))
    }

    @Test
    fun `formatTimeMs formats one minute`() {
        assertEquals("1:00", formatTimeMs(60_000L))
    }

    @Test
    fun `formatTimeMs formats minute thirty`() {
        assertEquals("1:30", formatTimeMs(90_000L))
    }

    @Test
    fun `formatTimeMs formats ten minutes five seconds`() {
        assertEquals("10:05", formatTimeMs(605_000L))
    }
}
