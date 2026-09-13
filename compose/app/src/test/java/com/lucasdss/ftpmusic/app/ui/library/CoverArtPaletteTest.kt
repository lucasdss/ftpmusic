package com.lucasdss.ftpmusic.app.ui.library

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class CoverArtPaletteTest {

    @Test
    fun `defaults have hasColors false`() {
        val colors = CoverArtColors()
        assertFalse(colors.hasColors)
    }

    @Test
    fun `with colors has hasColors true`() {
        val colors = CoverArtColors(
            vibrant = Color(0xFF123456.toInt()),
            darkMuted = Color(0xFF654321.toInt()),
        )
        assertTrue(colors.hasColors)
    }

    @Test
    fun `only vibrant set has hasColors true`() {
        val colors = CoverArtColors(vibrant = Color.Red)
        assertTrue(colors.hasColors)
    }

    @Test
    fun `Color Unspecified is not equal to constructed Color`() {
        val unspecified = Color.Unspecified
        val black = Color(0xFF000000.toInt())
        assertNotEquals(unspecified, black)
    }

    @Test
    fun `vibrant and darkMuted store values independently`() {
        val vibrant = Color(0xFF111111.toInt())
        val darkMuted = Color(0xFF222222.toInt())
        val colors = CoverArtColors(vibrant = vibrant, darkMuted = darkMuted)
        assertEquals(vibrant, colors.vibrant)
        assertEquals(darkMuted, colors.darkMuted)
    }
}
