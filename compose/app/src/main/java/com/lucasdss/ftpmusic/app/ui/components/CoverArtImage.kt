package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService

/**
 * Cover art with an error-driven fallback chain.
 *
 * `AsyncImage` silently renders nothing when a URL fails (404, Navidrome's
 * HTTP 200 JSON error bodies, truncated cache files), leaving a blank slot.
 * This wrapper retries once via the external fallback service (iTunes /
 * MusicBrainz) and shows a neutral placeholder when everything fails.
 *
 * Loads via a sized [ImageRequest] so Coil decodes near display resolution
 * (Library grid / Home cards stay cheaper during scroll).
 */
@Composable
fun CoverArtImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackArtist: String? = null,
    fallbackAlbum: String? = null,
    fallbackService: CoverArtFallbackService? = null,
    /** Decode size hint; defaults to 300dp for album tiles. */
    decodeSize: Dp = 300.dp,
) {
    var primaryFailed by remember(url) { mutableStateOf(false) }
    var fallbackUrl by remember(url) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = remember(decodeSize, density) {
        with(density) { decodeSize.roundToPx().coerceAtLeast(1) }
    }
    val service = remember(context, fallbackService) {
        fallbackService ?: try {
            CoverArtFallbackService.getInstance(context)
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(primaryFailed, url) {
        if (primaryFailed && fallbackUrl == null && service != null &&
            fallbackArtist != null && fallbackAlbum != null
        ) {
            service.fetchArt(fallbackArtist, fallbackAlbum).collect { candidate ->
                if (!candidate.isNullOrBlank()) fallbackUrl = candidate
            }
        }
    }

    val modelUrl = if (!primaryFailed) url else fallbackUrl
    if (modelUrl != null) {
        val request = remember(modelUrl, sizePx) {
            ImageRequest.Builder(context)
                .data(modelUrl)
                .size(sizePx)
                .memoryCacheKey("$modelUrl@$sizePx")
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            onError = { if (!primaryFailed) primaryFailed = true },
        )
    } else {
        Box(modifier.background(Color(0xFF1A1A1A)))
    }
}
