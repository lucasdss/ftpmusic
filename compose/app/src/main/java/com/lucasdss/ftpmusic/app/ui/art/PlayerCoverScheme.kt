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
     * Accent for waveform / lyric chrome. Falls back to [BrandTeal] when contrast
     * against [bgTop] would be below WCAG AA (4.5:1).
     */
    fun accentOrFallback(seed: Color?, bgTop: Color): Color {
        val candidate = seed?.takeIf { it.alpha > 0.01f } ?: BrandTeal
        return if (CoverScheme.contrastRatio(candidate, bgTop) >= 4.5f) {
            candidate
        } else {
            BrandTeal
        }
    }

    fun onBgOrFallback(bgTop: Color): Color =
        if (CoverScheme.contrastRatio(Foreground, bgTop) >= 4.5f) Foreground else Foreground
}
