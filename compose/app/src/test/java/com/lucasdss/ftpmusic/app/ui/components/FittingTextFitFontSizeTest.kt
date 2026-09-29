package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FittingTextFitFontSizeTest {

    @Test
    fun `returns start size when empty or zero width`() {
        assertEquals(
            14f,
            fitFontSize("hi", 14f, 10f, null, 1, 0) { _, _, _, _ -> true },
            0.001f,
        )
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
    fun `shrinks toward min when start overflows`() {
        // Overflow for size > 11; fit at 11 or below
        val result = fitFontSize(
            text = "Supercalifragilistic",
            startSp = 16f,
            minSp = 10f,
            fontWeight = null,
            maxLines = 1,
            maxWidthPx = 80,
        ) { sizeSp, _, _, _ -> sizeSp > 11f }
        assertTrue(result in 10f..11.5f)
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
        assertEquals(10f, result, 0.5f)
    }
}
