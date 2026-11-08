package com.lucasdss.ftpmusic.app.ui.art

import androidx.compose.ui.graphics.Color
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Foreground

/**
 * Builds the Now Playing [CoverScheme] from an extracted seed.
 * Uses standard detail darkening (Y≤0.04). Mini bar keeps its own brighter mid separately.
 */
object PlayerCoverScheme {
    fun fromSeed(seed: Color?): CoverScheme = CoverScheme.fromSeed(seed)

    /**
     * Accent for waveform / lyric chrome. Mini-palette [vibrant] seeds are often
     * darkened to Y≤0.15 (never clears 4.5:1 on dark [bgTop]); lift then re-check.
     * Falls back to [BrandTeal] when still below WCAG AA (4.5:1).
     */
    fun accentOrFallback(seed: Color?, bgTop: Color): Color {
        val raw = seed?.takeIf { it.alpha > 0.01f } ?: return BrandTeal
        if (CoverScheme.contrastRatio(raw, bgTop) >= 4.5f) return raw
        val lifted = CoverScheme.lightenToMinLuminance(raw, 0.45f)
        return if (CoverScheme.contrastRatio(lifted, bgTop) >= 4.5f) lifted else BrandTeal
    }

    fun onBgOrFallback(bgTop: Color): Color = Foreground
}
