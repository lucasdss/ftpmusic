package com.lucasdss.ftpmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveScaleTest {

    @Test
    fun `widthFactor floors at 0_85 for very narrow widths`() {
        assertEquals(0.85f, AdaptiveScale.widthFactor(300), 0.001f)
        assertEquals(0.85f, AdaptiveScale.widthFactor(306), 0.001f)
    }

    @Test
    fun `widthFactor downscales between floor and reference`() {
        assertEquals(320f / 360f, AdaptiveScale.widthFactor(320), 0.001f)
    }

    @Test
    fun `widthFactor is 1 at reference width`() {
        assertEquals(1.0f, AdaptiveScale.widthFactor(360), 0.001f)
    }

    @Test
    fun `widthFactor scales between reference and cap`() {
        assertEquals(440f / 360f, AdaptiveScale.widthFactor(440), 0.001f)
    }

    @Test
    fun `widthFactor caps at 1_25`() {
        assertEquals(1.25f, AdaptiveScale.widthFactor(450), 0.001f)
        assertEquals(1.25f, AdaptiveScale.widthFactor(500), 0.001f)
    }
}
