package com.lucasdss.ftpmusic.app.ui.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCoverSchemeTest {
    @Test
    fun `null seed yields neutral`() {
        val scheme = PlayerCoverScheme.fromSeed(null)
        assertEquals(Background, scheme.bgTop)
    }

    @Test
    fun `usable seed darkens top`() {
        val seed = Color(0xFF40C8B4)
        val scheme = PlayerCoverScheme.fromSeed(seed)
        assertTrue(scheme.bgTop.luminance() <= CoverScheme.MAX_TOP_LUMINANCE + 0.02f)
    }

    @Test
    fun `low contrast accent falls back to BrandTeal`() {
        val bgTop = Color.Black
        val nearBlack = Color(0xFF101010)
        assertEquals(BrandTeal, PlayerCoverScheme.accentOrFallback(nearBlack, bgTop))
    }

    @Test
    fun `high contrast accent kept`() {
        val bgTop = Color.Black
        val bright = Color(0xFF00FFCC)
        assertEquals(bright, PlayerCoverScheme.accentOrFallback(bright, bgTop))
    }
}
