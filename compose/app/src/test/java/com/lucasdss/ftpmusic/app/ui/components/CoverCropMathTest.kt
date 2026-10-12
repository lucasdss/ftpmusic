package com.lucasdss.ftpmusic.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverCropMathTest {

    @Test
    fun `minScale fills square for landscape`() {
        val scale = CoverCropMath.minScale(2000f, 1000f, 400f)
        assertEquals(0.4f, scale, 0.001f) // 400/1000
    }

    @Test
    fun `minScale fills square for portrait`() {
        val scale = CoverCropMath.minScale(1000f, 2000f, 400f)
        assertEquals(0.4f, scale, 0.001f) // 400/1000
    }

    @Test
    fun `clampScale respects max zoom`() {
        val min = CoverCropMath.minScale(1000f, 1000f, 400f)
        val max = CoverCropMath.maxScale(1000f, 1000f, 400f)
        assertEquals(min * CoverCropMath.MAX_ZOOM, max, 0.001f)
        assertEquals(max, CoverCropMath.clampScale(999f, 1000f, 1000f, 400f), 0.001f)
        assertEquals(min, CoverCropMath.clampScale(0.01f, 1000f, 1000f, 400f), 0.001f)
    }

    @Test
    fun `clampOffset zero when image exactly fills`() {
        val scale = CoverCropMath.minScale(400f, 400f, 400f)
        val (ox, oy) = CoverCropMath.clampOffset(scale, 50f, -30f, 400f, 400f, 400f)
        assertEquals(0f, ox, 0.001f)
        assertEquals(0f, oy, 0.001f)
    }

    @Test
    fun `clampOffset allows pan when zoomed`() {
        val scale = CoverCropMath.minScale(1000f, 1000f, 400f) * 2f
        val (ox, oy) = CoverCropMath.clampOffset(scale, 10_000f, -10_000f, 1000f, 1000f, 400f)
        val maxO = (1000f * scale - 400f) / 2f
        assertEquals(maxO, ox, 0.5f)
        assertEquals(-maxO, oy, 0.5f)
    }

    @Test
    fun `sourceCropRect centered at default transform`() {
        val scale = CoverCropMath.minScale(1000f, 1000f, 400f) // 0.4
        val rect = CoverCropMath.sourceCropRect(1000, 1000, 400f, scale, 0f, 0f)
        // viewport/scale = full image when fill-min on square source
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(1000, rect.size)
    }

    @Test
    fun `sourceCropRect stays inside bounds when panned`() {
        val scale = CoverCropMath.minScale(2000f, 1000f, 400f) * 2f
        val (ox, oy) = CoverCropMath.clampOffset(scale, 200f, 0f, 2000f, 1000f, 400f)
        val rect = CoverCropMath.sourceCropRect(2000, 1000, 400f, scale, ox, oy)
        assertTrue(rect.left >= 0)
        assertTrue(rect.top >= 0)
        assertTrue(rect.left + rect.size <= 2000)
        assertTrue(rect.top + rect.size <= 1000)
        assertTrue(rect.size > 0)
    }
}
