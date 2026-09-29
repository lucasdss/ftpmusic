package com.lucasdss.ftpmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveScaleTest {

    @Test
    fun `widthFactor downscales below 360dp`() {
        assertEquals(0.85f, AdaptiveScale.widthFactor(300), 0.001f)
        assertEquals(320f / 360f, AdaptiveScale.widthFactor(320), 0.001f)
    }

    @Test
    fun `widthFactor is 1 at reference width`() {
        assertEquals(1.0f, AdaptiveScale.widthFactor(360), 0.001f)
    }

    @Test
    fun `widthFactor caps at 1_25`() {
        assertEquals(1.25f, AdaptiveScale.widthFactor(500), 0.001f)
        assertTrue(AdaptiveScale.widthFactor(440) in 1.0f..1.25f)
    }

    @Test
    fun `fontCompensation is 1 at fontScale 1`() {
        assertEquals(1.0f, AdaptiveScale.fontCompensation(1.0f), 0.001f)
    }

    @Test
    fun `fontCompensation absorbs large fontScale with floor 0_75`() {
        assertEquals(0.75f, AdaptiveScale.fontCompensation(1.4f), 0.001f)
        assertEquals(0.75f, AdaptiveScale.fontCompensation(2.0f), 0.001f)
        assertTrue(AdaptiveScale.fontCompensation(1.2f) < 1.0f)
        assertTrue(AdaptiveScale.fontCompensation(1.2f) > 0.75f)
    }

    @Test
    fun `textFactor multiplies width and font compensation`() {
        val expected = AdaptiveScale.widthFactor(320) * AdaptiveScale.fontCompensation(1.3f)
        assertEquals(expected, AdaptiveScale.textFactor(320, 1.3f), 0.001f)
    }
}
