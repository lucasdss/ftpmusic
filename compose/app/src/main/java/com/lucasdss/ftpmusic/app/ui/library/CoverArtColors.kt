package com.lucasdss.ftpmusic.app.ui.library

import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Process-lifetime palette cache, LRU-capped + synchronized (P9): without a
 *  cap it grew one entry per unique cover URL forever; access-order
 *  LinkedHashMap + removeEldestEntry trims it. */
private val paletteCache: MutableMap<String, CoverArtColors> =
    java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, CoverArtColors>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoverArtColors>?): Boolean =
                size > MAX_PALETTE_CACHE_ENTRIES
        },
    )
private const val MAX_PALETTE_CACHE_ENTRIES = 64

data class CoverArtColors(val vibrant: Color = Color.Unspecified, val darkMuted: Color = Color.Unspecified) {
    val hasColors: Boolean get() = vibrant != Color.Unspecified
}

@Composable
fun rememberCoverArtColors(coverArtUrl: String?): CoverArtColors {
    var colors by remember(coverArtUrl) { mutableStateOf(CoverArtColors()) }

    LaunchedEffect(coverArtUrl) {
        if (coverArtUrl == null) {
            colors = CoverArtColors()
            return@LaunchedEffect
        }
        // Check cache first
        paletteCache[coverArtUrl]?.let {
            colors = it
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) {
            try {
                val connection = URL(coverArtUrl).openConnection().apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                }
                connection.getInputStream().use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val p = Palette.from(bitmap).generate()
                        CoverArtColors(
                            vibrant = Color(p.getVibrantColor(android.graphics.Color.TRANSPARENT)),
                            darkMuted = Color(p.getDarkMutedColor(android.graphics.Color.TRANSPARENT)),
                        )
                    } else {
                        CoverArtColors()
                    }
                }
            } catch (_: Exception) {
                CoverArtColors()
            }
        }
        paletteCache[coverArtUrl] = result
        colors = result
    }
    return colors
}
