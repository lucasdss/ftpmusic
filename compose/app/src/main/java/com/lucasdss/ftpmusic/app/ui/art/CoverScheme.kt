package com.lucasdss.ftpmusic.app.ui.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Foreground
import kotlin.math.max
import kotlin.math.min

/**
 * Spotify-style cover-reactive surface roles derived from a single art seed.
 * Always dark: never flips to dark text. Seed is luminance-clamped so white
 * titles and raised secondary greys stay readable.
 */
data class CoverScheme(
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color = Background,
    val onBg: Color = Foreground,
    val onBgSecondary: Color = DEFAULT_SECONDARY,
    val accent: Color = BrandTeal,
) {
    companion object {
        /** Secondary text that stays ≥4.5:1 against a Y≤0.04 top wash. */
        val DEFAULT_SECONDARY = Color(0xFFAAAAAA)

        /** Max relative luminance for [bgTop] (keeps white ~11:1). */
        const val MAX_TOP_LUMINANCE = 0.04f

        const val ALGORITHM_VERSION = 1

        fun neutral(): CoverScheme = CoverScheme(
            bgTop = Background,
            bgMid = Background,
            bgBottom = Background,
        )

        /**
         * Build a scheme from an optional seed.
         * Null / near-gray / near-black / near-white → neutral (no muddy tint).
         */
        fun fromSeed(seed: Color?): CoverScheme {
            if (seed == null || seed.alpha < 0.05f) return neutral()
            if (!isUsableSeed(seed)) return neutral()
            val top = darkenToMaxLuminance(seed, MAX_TOP_LUMINANCE)
            val mid = lerp(top, Background, 0.55f)
            return CoverScheme(
                bgTop = top,
                bgMid = mid,
                bgBottom = Background,
                onBg = Foreground,
                onBgSecondary = DEFAULT_SECONDARY,
                accent = BrandTeal,
            )
        }

        /** Reject low-chroma / extreme luminance seeds that look like borders or text. */
        fun isUsableSeed(color: Color): Boolean {
            val y = color.luminance()
            if (y < 0.02f || y > 0.85f) return false
            val maxC = max(color.red, max(color.green, color.blue))
            val minC = min(color.red, min(color.green, color.blue))
            val chroma = maxC - minC
            return chroma >= 0.08f
        }

        fun darkenToMaxLuminance(color: Color, maxY: Float): Color {
            var c = color.copy(alpha = 1f)
            var y = c.luminance()
            if (y <= maxY) return c
            // Mix toward black until under the cap (preserves hue better than grey mix).
            var t = 0f
            while (y > maxY && t < 0.98f) {
                t += 0.05f
                c = lerp(color, Color.Black, t).copy(alpha = 1f)
                y = c.luminance()
            }
            return c
        }

        fun contrastRatio(a: Color, b: Color): Float {
            val l1 = a.luminance() + 0.05f
            val l2 = b.luminance() + 0.05f
            return max(l1, l2) / min(l1, l2)
        }

        fun lerp(a: Color, b: Color, t: Float): Color {
            val u = t.coerceIn(0f, 1f)
            return Color(
                red = a.red + (b.red - a.red) * u,
                green = a.green + (b.green - a.green) * u,
                blue = a.blue + (b.blue - a.blue) * u,
                alpha = a.alpha + (b.alpha - a.alpha) * u,
            )
        }

        /** Lettermark seed from name-hash hue (matches CollectionLettermark). */
        fun lettermarkSeed(name: String, hueFromName: (String) -> Float): Color {
            val hue = hueFromName(name)
            return Color.hsl(hue, 0.45f, 0.28f)
        }
    }
}
