package com.lucasdss.ftpmusic.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Design system color tokens per VISUAL-EFFECTS.md.
 * All components reference these instead of inline Color(0xFF...).
 */

// ── Brand colors ──────────────────────────────────────────────────
val BrandTeal = Color(0xFF00C8B4)
val BrandPurple = Color(0xFFB040E8)
val BrandBg = Color(0xFF101018)

// ── Surface colors ───────────────────────────────────────────────
val Background = Color(0xFF12121E)
val Surface = Color(0xFF1C1C2E)
val Surface2 = Color(0xFF252538)

// ── Text colors ──────────────────────────────────────────────────
val Foreground = Color(0xFFE8E8F0)
val Muted = Color(0xFF7A7A9A)
val Dimmed = Color(0xFF666666)

// ── Semantic colors ──────────────────────────────────────────────
val CastActive = Color(0xFF00C8B4)
val CastIdle = Color(0xFF666666)
val OfflineYellow = Color(0xFFFFC800)
val DestructiveRed = Color(0xFFE84040)

private val DarkColorScheme = darkColorScheme(
    primary = BrandTeal,
    secondary = BrandPurple,
    background = Background,
    surface = Surface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Foreground,
    onSurface = Foreground,
)

@Composable
fun FtpmusicTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme // always dark per design spec

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
