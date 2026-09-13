package com.lucasdss.ftpmusic.app.playback

import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class ReplayGainTest {

    // ── Gain selection logic (extracted for testing) ────────────────────────

    private fun selectGain(trackGain: Float?, albumGain: Float?): Float? = trackGain?.takeIf {
        it != 0f
    } ?: albumGain?.takeIf { it != 0f }

    private fun clampGain(gain: Float): Float = gain.coerceIn(-20f, 20f)

    private fun gainToMultiplier(gain: Float): Float = 10.0.pow(gain.toDouble() / 20.0).toFloat()

    // ── Gain selection ─────────────────────────────────────────────────────

    @Test
    fun `prefers track gain over album gain`() {
        val gain = selectGain(trackGain = -3f, albumGain = -6f)
        assertEquals(-3f, gain)
    }

    @Test
    fun `falls back to album gain when track gain is zero`() {
        val gain = selectGain(trackGain = 0f, albumGain = -6f)
        assertEquals(-6f, gain)
    }

    @Test
    fun `falls back to album gain when track gain is null`() {
        val gain = selectGain(trackGain = null, albumGain = -6f)
        assertEquals(-6f, gain)
    }

    @Test
    fun `returns null when both gains are zero`() {
        val gain = selectGain(trackGain = 0f, albumGain = 0f)
        assertNull(gain)
    }

    @Test
    fun `returns null when both gains are null`() {
        val gain = selectGain(trackGain = null, albumGain = null)
        assertNull(gain)
    }

    // ── Clamping ───────────────────────────────────────────────────────────

    @Test
    fun `clamps gain to minus20 plus20 range`() {
        assertEquals(-20f, clampGain(-30f))
        assertEquals(20f, clampGain(30f))
        assertEquals(5f, clampGain(5f))
    }

    // ── dB to multiplier conversion ────────────────────────────────────────

    @Test
    fun `dB to multiplier is accurate`() {
        val multiplier = gainToMultiplier(6.0f)
        assertTrue(multiplier in 1.9f..2.1f)
    }

    @Test
    fun `zero gain returns multiplier of 1`() {
        val multiplier = gainToMultiplier(0f)
        assertEquals(1.0f, multiplier)
    }

    @Test
    fun `negative gain reduces volume`() {
        val multiplier = gainToMultiplier(-6f)
        assertTrue(multiplier in 0.49f..0.51f)
    }

    @Test
    fun `clamped minus20dB returns 0point1 multiplier`() {
        val multiplier = gainToMultiplier(-20f)
        assertTrue(multiplier in 0.09f..0.11f)
    }

    @Test
    fun `clamped plus20dB returns 10x multiplier`() {
        val multiplier = gainToMultiplier(20f)
        assertTrue(multiplier in 9.9f..10.1f)
    }

    // ── applyReplayGain with null player (smoke test) ──────────────────────

    @Test
    fun `applyReplayGain with null player does not throw`() {
        ReplayGainUtil.applyReplayGain(null, trackGain = -3f, albumGain = null)
    }
}
