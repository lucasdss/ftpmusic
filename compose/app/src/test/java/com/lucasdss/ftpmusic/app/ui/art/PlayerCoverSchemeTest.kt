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
    fun `null seed falls back to BrandTeal`() {
        assertEquals(BrandTeal, PlayerCoverScheme.accentOrFallback(null, Color.Black))
    }

    @Test
    fun `darkened vibrant seed is lifted for chrome contrast`() {
        val bgTop = Color.Black
        // Production mini vibrant is darkened to Y≤0.15 — must lift, not always BrandTeal.
        val darkened = CoverScheme.darkenToMaxLuminance(Color(0xFF40C8B4), 0.15f)
        assertTrue(CoverScheme.contrastRatio(darkened, bgTop) < 4.5f)
        val accent = PlayerCoverScheme.accentOrFallback(darkened, bgTop)
        assertTrue(CoverScheme.contrastRatio(accent, bgTop) >= 4.5f)
    }

    @Test
    fun `high contrast accent kept`() {
        val bgTop = Color.Black
        val bright = Color(0xFF00FFCC)
        assertEquals(bright, PlayerCoverScheme.accentOrFallback(bright, bgTop))
    }
}
