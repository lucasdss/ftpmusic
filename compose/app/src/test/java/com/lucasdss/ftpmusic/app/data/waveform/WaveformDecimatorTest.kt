package com.lucasdss.ftpmusic.app.data.waveform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformDecimatorTest {

    private fun barsOf(vararg values: Float): List<Float> = values.toList()

    private fun barsOf(count: Int, value: Float): List<Float> = List(count) { value }

    // ── No-op paths ────────────────────────────────────────────────────────

    @Test fun `empty input returns empty`() {
        assertEquals(emptyList<Float>(), WaveformDecimator.decimate(emptyList(), 50))
    }

    @Test fun `target at or above source size returns source unchanged`() {
        val bars = barsOf(1f, 0.2f, 0.8f)
        assertEquals(bars, WaveformDecimator.decimate(bars, 3))
        assertEquals(bars, WaveformDecimator.decimate(bars, 100))
    }

    // ── Core decimation ────────────────────────────────────────────────────

    @Test fun `reduces to exact target count`() {
        val bars = barsOf(400) { i -> (i % 100) / 100f }
        val out = WaveformDecimator.decimate(bars, 100)
        assertEquals(100, out.size)
    }

    @Test fun `bucket blend is mean-max 65-35`() {
        // [0.1, 0.2] -> mean 0.15, max 0.2 -> 0.15*0.65 + 0.2*0.35 = 0.1675
        // [0.3, 0.4] -> mean 0.35, max 0.4 -> 0.35*0.65 + 0.4*0.35 = 0.3675
        val out = WaveformDecimator.decimate(barsOf(0.1f, 0.2f, 0.3f, 0.4f), 2)
        assertEquals(2, out.size)
        assertEquals(0.1675f, out[0], 0.001f)
        assertEquals(0.3675f, out[1], 0.001f)
    }

    @Test fun `odd source size bucket boundaries`() {
        // 3 -> 2: bucket = 1.5
        // i=0: lo=0, hi=max(1, 1)=1 -> [0.1]
        // i=1: lo=1, hi=max(2, 3)=3 -> [0.2, 0.3] -> mean 0.25, max 0.3 -> 0.2675
        val out = WaveformDecimator.decimate(barsOf(0.1f, 0.2f, 0.3f), 2)
        assertEquals(2, out.size)
        assertEquals(0.1f, out[0], 0.001f)
        assertEquals(0.2675f, out[1], 0.001f)
    }

    @Test fun `target one blends the whole array`() {
        val out = WaveformDecimator.decimate(barsOf(0.1f, 0.5f, 0.9f), 1)
        assertEquals(1, out.size)
        // mean 0.5, max 0.9 -> 0.5*0.65 + 0.9*0.35 = 0.64
        assertEquals(0.64f, out[0], 0.001f)
    }

    @Test fun `target zero or negative coerces to one bar`() {
        val bars = barsOf(0.2f, 0.4f)
        assertEquals(1, WaveformDecimator.decimate(bars, 0).size)
        assertEquals(1, WaveformDecimator.decimate(bars, -5).size)
    }

    // ── Invariants ─────────────────────────────────────────────────────────

    @Test fun `output stays within zero-one range for arbitrary input`() {
        val bars = barsOf(400) { i -> ((i * 37) % 101) / 100f }
        val out = WaveformDecimator.decimate(bars, 83)
        assertEquals(83, out.size)
        out.forEach { assertTrue("bar $it in range", it in 0f..1f) }
    }

    @Test fun `decimation is deterministic`() {
        val bars = barsOf(400) { i -> ((i * 37) % 101) / 100f }
        assertEquals(
            WaveformDecimator.decimate(bars, 77),
            WaveformDecimator.decimate(bars, 77),
        )
    }

    @Test fun `silence and peaks are preserved`() {
        // All-zero input stays all-zero; all-one input stays all-one.
        assertTrue(WaveformDecimator.decimate(barsOf(100, 0f), 50).all { it == 0f })
        assertTrue(WaveformDecimator.decimate(barsOf(100, 1f), 50).all { it == 1f })
    }

    private fun barsOf(count: Int, factory: (Int) -> Float): List<Float> = List(count) { factory(it) }
}
