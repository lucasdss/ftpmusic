package com.lucasdss.ftpmusic.app.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lucasdss.ftpmusic.app.data.cover.ArtSeedExtractor
import com.lucasdss.ftpmusic.app.ui.art.CoverScheme

/**
 * Player mini / now-playing palette derived from cover art via Coil + Palette.
 * Detail screens use [com.lucasdss.ftpmusic.app.ui.art.rememberCoverScheme] instead.
 */
data class CoverArtColors(
    val vibrant: Color = Color.Unspecified,
    val darkMuted: Color = Color.Unspecified,
) {
    /** True only when a usable (non-transparent) vibrant seed was extracted. */
    val hasColors: Boolean
        get() = vibrant != Color.Unspecified && vibrant.alpha > 0.01f
}

@Composable
fun rememberCoverArtColors(coverArtUrl: String?): CoverArtColors {
    var colors by remember(coverArtUrl) { mutableStateOf(CoverArtColors()) }
    val context = LocalContext.current

    LaunchedEffect(coverArtUrl) {
        if (coverArtUrl.isNullOrBlank()) {
            colors = CoverArtColors()
            return@LaunchedEffect
        }
        val artKey = "player|$coverArtUrl"
        val sourceVersion = coverArtUrl
        ArtSeedExtractor.peekMemory(artKey, sourceVersion)?.let { hit ->
            if (!hit.softFailure) {
                colors = hit.toCoverArtColors()
                return@LaunchedEffect
            }
        }
        val result = ArtSeedExtractor.extractFromModel(
            context = context,
            imageModel = coverArtUrl,
            artKey = artKey,
            sourceVersion = sourceVersion,
        )
        colors = result.toCoverArtColors()
    }
    return colors
}

private fun ArtSeedExtractor.SeedResult.toCoverArtColors(): CoverArtColors {
    val seed = color ?: return CoverArtColors()
    if (!CoverScheme.isUsableSeed(seed)) return CoverArtColors()
    val scheme = CoverScheme.fromSeed(seed)
    // Player keeps a brighter mid stop than detail backdrops (Y≤0.15) so the
    // mini/now-playing gradient stays colorful while darkMuted stays readable.
    return CoverArtColors(
        vibrant = CoverScheme.darkenToMaxLuminance(seed, 0.15f),
        darkMuted = scheme.bgTop,
    )
}
