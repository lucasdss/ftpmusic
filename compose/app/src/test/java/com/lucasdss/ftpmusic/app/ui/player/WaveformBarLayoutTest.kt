package com.lucasdss.ftpmusic.app.ui.player

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformBarLayoutTest {

    private val teal = Color(0xFF00C8B4)
    private val purple = Color(0xFFB040E8)

    private fun specs(
        bars: List<Float>,
        fraction: Float = 0f,
        width: Float = 348.5f,
        height: Float = 48f,
        step: Float = 3.5f,
        barWidth: Float = 2f,
        minH: Float = 2f,
        maxH: Float = 40f,
    ) = computeWaveformBarSpecs(bars, fraction, width, height, step, barWidth, minH, maxH)

    // ── Shape / layout ─────────────────────────────────────────────────────

    @Test fun `empty bars produce no specs`() {
        assertEquals(emptyList<WaveformBarSpec>(), specs(emptyList()))
    }

    @Test fun `specs count matches bars`() {
        val out = specs(List(100) { 0.5f })
        assertEquals(100, out.size)
    }

    @Test fun `bars are centered and evenly spaced`() {
        val count = 100
        val step = 3.5f
        val barWidth = 2f
        val totalWidth = step * count - (step - barWidth)
        val width = 400f
        val out = specs(List(count) { 0.5f }, width = width, step = step, barWidth = barWidth)
        val expectedStart = (width - totalWidth) / 2f
        out.forEachIndexed { i, spec ->
            assertEquals(expectedStart + i * step, spec.x, 0.001f)
        }
    }

    @Test fun `bar height scales with amplitude and clamps`() {
        val out = specs(listOf(0f, 0.5f, 1f, 1.5f))
        assertEquals(2f, out[0].height, 0.001f) // min floor
        assertEquals(20f, out[1].height, 0.001f) // 0.5 * 40
        assertEquals(40f, out[2].height, 0.001f) // max
        assertEquals(40f, out[3].height, 0.001f) // clamped 1.5 -> 1
    }

    @Test fun `bar top centers vertically`() {
        val out = specs(listOf(1f), height = 48f)
        assertEquals((48f - 40f) / 2f, out[0].top, 0.001f)
    }

    // ── Colors ─────────────────────────────────────────────────────────────

    @Test fun `played bars use teal to purple gradient`() {
        val out = specs(List(10) { 0.5f }, fraction = 0.5f) // playedIndex = 5
        assertEquals(lerpColor(teal, purple, 0f / 10f), out[0].color)
        assertEquals(lerpColor(teal, purple, 4f / 10f), out[4].color)
    }

    @Test fun `playhead bar is white at 70 percent`() {
        val out = specs(List(10) { 0.5f }, fraction = 0.5f) // playedIndex = 5
        assertEquals(Color.White.copy(alpha = 0.7f), out[5].color)
    }

    @Test fun `unplayed bars dim with amplitude`() {
        // fraction 0 -> playedIndex 0 -> bar[0] is the playhead; bars[1..2] unplayed.
        // Alpha is 8-bit quantized in Compose Color, hence 0.005 tolerance.
        val out = specs(listOf(0f, 0.5f, 1f), fraction = 0f)
        assertEquals(0.14f, out[1].color.alpha, 0.005f) // amp 0.5 -> 0.1 + 0.04
        assertEquals(0.18f, out[2].color.alpha, 0.005f) // amp 1.0 -> 0.1 + 0.08
    }

    @Test fun `fraction one marks every bar played`() {
        val out = specs(List(10) { 0.5f }, fraction = 1f) // playedIndex = 10
        out.forEachIndexed { i, spec ->
            assertEquals(lerpColor(teal, purple, i.toFloat() / 10f), spec.color)
        }
    }

    @Test fun `fraction zero marks first bar as playhead`() {
        val out = specs(List(10) { 0.5f }, fraction = 0f)
        assertEquals(Color.White.copy(alpha = 0.7f), out[0].color)
        assertTrue(out.drop(1).all { it.color.alpha < 0.2f })
    }

    // ── lerpColor (moved from WaveformScrubberTest) ────────────────────────

    @Test fun `lerpColor returns start at zero and end at one`() {
        assertEquals(1f, lerpColor(Color(0xFFFF0000), Color(0xFF0000FF), 0f).red)
        assertEquals(1f, lerpColor(Color(0xFFFF0000), Color(0xFF0000FF), 1f).blue)
    }
}
