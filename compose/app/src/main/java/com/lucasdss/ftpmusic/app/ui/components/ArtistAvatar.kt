package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFiles
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl

/**
 * Single source of truth for artist avatars — used by the Library artists
 * tab, the Home Favorite Artists row, and the Favorites tab so the same
 * artist always shows the same art.
 *
 * Resolution chain (file-first — the disk cache is the source of truth and
 * is shared across every surface):
 * 1. The cached artist image `artist|<name>.jpg` (populated by
 *    [CoverArtFallbackService.fetchArtistArt] with the best available source:
 *    Navidrome artist art → iTunes/MusicBrainz). Same art everywhere,
 *    offline-capable. Validated: a truncated file is evicted, not trusted.
 * 2. Interim: the Navidrome artist cover art URL while the cache file is
 *    being populated. An [onError] fall-through means a 404 id can never
 *    block the file/icon tiers.
 * 3. Person icon.
 *
 * Observes only the artist cache key so a write here cannot recompose every
 * album/mix cover on Home LazyRows (see ADR 0036).
 */
@Composable
fun ArtistAvatar(artistName: String, coverArtId: String?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coverArtFallback = remember { CoverArtFallbackService.getInstance(context) }
    val artistCacheKey = "artist|${artistName.lowercase()}"
    val fallbackVersion = coverArtFallback.observeVersion(artistCacheKey)

    // Populate (or refresh) the shared cache file for this artist — bumps
    // only this artist's key version on completion.
    LaunchedEffect(artistName, coverArtId) {
        coverArtFallback.fetchArtistArt(artistName, coverArtId).collect { }
    }

    // Tier 1: the shared cached file (validated — corrupt entries evicted).
    var localFailed by remember(artistName) { mutableStateOf(false) }
    val cachedFile = remember(artistName) {
        java.io.File(
            coverArtFallback.cacheDir,
            "${artistCacheKey.hashCode()}.jpg",
        )
    }
    val cachedFileUrl = remember(artistName, fallbackVersion, localFailed) {
        if (localFailed) {
            null
        } else if (CoverArtFiles.looksLikeImage(cachedFile)) {
            cachedFile.absolutePath
        } else {
            CoverArtFiles.deleteIfNotImage(cachedFile)
            null
        }
    }
    // Tier 2: interim Navidrome URL — marked failed on Coil error so it can
    // never shadow the file tier once populated (or the icon tier).
    var navidromeFailed by remember(artistName, coverArtId) { mutableStateOf(false) }
    val navidromeUrl = rememberCoverArtUrl(coverArtId, 200)

    val effectiveUrl = cachedFileUrl
        ?: (if (!navidromeFailed) navidromeUrl else null)

    Box(
        modifier.size(size).clip(CircleShape).background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center,
    ) {
        if (effectiveUrl != null) {
            AsyncImage(
                model = effectiveUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    if (effectiveUrl == navidromeUrl) {
                        navidromeFailed = true
                    } else {
                        // Corrupt/unreadable cache file: evict and fall through
                        // to the Navidrome tier.
                        CoverArtFiles.deleteIfNotImage(cachedFile)
                        localFailed = true
                    }
                },
            )
        } else {
            Icon(Icons.Default.Person, null, tint = Color(0xFF555555), modifier = Modifier.size(size / 2.5f))
        }
    }
}
