package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FittingTextFitFontSizeTest {

    @Test
    fun `zero width returns min size to avoid flash`() {
        assertEquals(
            10f,
            fitFontSize("hi", 14f, 10f, null, 1, 0) { _, _, _, _ -> true },
            0.001f,
        )
    }

    @Test
    fun `empty text returns start size`() {
        assertEquals(
            14f,
            fitFontSize("", 14f, 10f, null, 1, 100) { _, _, _, _ -> true },
            0.001f,
        )
    }

    @Test
    fun `returns start size when no overflow`() {
        assertEquals(
            14f,
            fitFontSize("Home", 14f, 10f, FontWeight.Medium, 1, 200) { _, _, _, _ -> false },
            0.001f,
        )
    }

    @Test
    fun `shrinks to largest non-overflow size`() {
        // Overflow for size > 12; expect ~12
        val result = fitFontSize(
            text = "Supercalifragilistic",
            startSp = 16f,
            minSp = 10f,
            fontWeight = null,
            maxLines = 1,
            maxWidthPx = 80,
        ) { sizeSp, _, _, _ -> sizeSp > 12f }
        assertEquals(12f, result, 0.3f)
        assertTrue(result <= 12f)
    }

    @Test
    fun `floors at min when even min overflows`() {
        val result = fitFontSize(
            text = "X".repeat(40),
            startSp = 16f,
            minSp = 10f,
            fontWeight = null,
            maxLines = 1,
            maxWidthPx = 40,
        ) { _, _, _, _ -> true }
        assertEquals(10f, result, 0.001f)
    }

    @Test
    fun `minSp greater than startSp uses start as floor`() {
        val result = fitFontSize(
            text = "Hi",
            startSp = 12f,
            minSp = 16f,
            fontWeight = null,
            maxLines = 1,
            maxWidthPx = 200,
        ) { _, _, _, _ -> false }
        assertEquals(12f, result, 0.001f)
    }

    @Test
    fun `post search verify steps down when mid still overflows`() {
        // Non-monotonic-ish: sizes above 11.5 overflow; binary search may land slightly hot
        val result = fitFontSize(
            text = "LongWordWithoutSpaces",
            startSp = 16f,
            minSp = 10f,
            fontWeight = null,
            maxLines = 1,
            maxWidthPx = 60,
        ) { sizeSp, _, _, _ -> sizeSp > 11.5f }
        assertTrue(result <= 11.5f)
        assertTrue(result >= 10f)
    }
}
