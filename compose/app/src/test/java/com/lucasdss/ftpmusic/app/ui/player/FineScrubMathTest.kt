package com.lucasdss.ftpmusic.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FineScrubMathTest {
    @Test
    fun `sensitivity is one at zero pull`() {
        assertEquals(1f, FineScrubMath.sensitivity(0f), 0.001f)
    }

    @Test
    fun `sensitivity halves near HALF_SPEED_PULL`() {
        val s = FineScrubMath.sensitivity(FineScrubMath.HALF_SPEED_PULL_PX)
        assertEquals(0.5f, s, 0.05f)
    }

    @Test
    fun `applyHorizontalDelta clamps to unit interval`() {
        assertEquals(1f, FineScrubMath.applyHorizontalDelta(0.95f, 1000f, 100f, 0f), 0.001f)
        assertEquals(0f, FineScrubMath.applyHorizontalDelta(0.05f, -1000f, 100f, 0f), 0.001f)
    }

    @Test
    fun `fine pull reduces travel`() {
        val coarse = FineScrubMath.applyHorizontalDelta(0.5f, 50f, 200f, 0f)
        val fine = FineScrubMath.applyHorizontalDelta(0.5f, 50f, 200f, FineScrubMath.HALF_SPEED_PULL_PX)
        assertTrue(fine - 0.5f < coarse - 0.5f)
    }

    @Test
    fun `axis lock yields when vertical dominates past threshold`() {
        assertFalse(FineScrubMath.shouldYieldVertical(10f, 40f))
        // Fine-scrub half-speed zone must remain reachable (no early yield).
        assertFalse(
            FineScrubMath.shouldYieldVertical(FineScrubMath.HALF_SPEED_PULL_PX, 10f),
        )
        assertTrue(
            FineScrubMath.shouldYieldVertical(FineScrubMath.AXIS_LOCK_THRESHOLD_PX, 10f),
        )
    }

    @Test
    fun `fractionFromTapX maps bar index`() {
        assertEquals(0.5f, FineScrubMath.fractionFromTapX(55f, 5f, 10f, 10), 0.001f)
        assertEquals(0f, FineScrubMath.fractionFromTapX(0f, 0f, 10f, 0), 0.001f)
    }
}
