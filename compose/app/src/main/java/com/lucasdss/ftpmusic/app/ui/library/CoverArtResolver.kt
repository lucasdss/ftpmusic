package com.lucasdss.ftpmusic.app.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFiles
import com.lucasdss.ftpmusic.app.di.ServerConfigState
import java.io.File

/**
 * Diagnostic de-dup for image URL resolution — logs each distinct failure/build
 * once per process so release logcat stays readable (diagnostic builds only).
 */
private val diagLoggedKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())

private fun diagLogOnce(key: String, message: String) {
    if (!com.lucasdss.ftpmusic.app.BuildConfig.IMAGE_DIAGNOSTICS) return
    if (diagLoggedKeys.add(key)) android.util.Log.w("ftpmusic-images", "[diag] $message")
}

/**
 * Resolve a Navidrome cover art ID to a full URL with auth params.
 * Auth is included inline because Coil uses its own HTTP client.
 * Salt and token are memoized per coverArtId so Coil's cache can reuse the URL.
 *
 * Reads [ServerConfigState] (Compose state), so a config that becomes
 * available *after* first composition triggers recomposition and rebuilds the
 * URL — the previous implementation read plain `@Volatile` globals inside
 * `remember(coverArtId, size)` and could stay null for the session.
 */
@Composable
fun rememberCoverArtUrl(coverArtId: String?, size: Int = 300): String? {
    if (coverArtId == null) return null
    val config = ServerConfigState.value
    if (!config.isConfigured) {
        diagLogOnce(
            "null-config:$coverArtId:$size",
            "cover URL NULL (config missing) id=$coverArtId size=$size t=${System.currentTimeMillis()}",
        )
        return null
    }

    // Reuse the same auth params across recompositions — prevents Coil disk
    // cache misses from new salts. Keyed on config too, so a server/credential
    // change rebuilds the URL instead of returning a stale cached one.
    val helper = remember { com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper() }
    return remember(coverArtId, size, config) {
        val url = helper.buildCoverArtUrlCached(
            config.url,
            config.username,
            config.password,
            coverArtId,
            size,
        )
        diagLogOnce(
            "built:$coverArtId:$size",
            "cover URL BUILT id=$coverArtId size=$size t=${System.currentTimeMillis()}",
        )
        url
    }
}

/**
 * Resolves cover art with a fallback chain:
 * 1. Navidrome cover art (via [rememberCoverArtUrl])
 * 2. iTunes Search API and MusicBrainz Cover Art Archive (raced in parallel)
 *
 * @param coverArtId Navidrome cover art ID (null if unavailable)
 * @param artist Track artist, used for fallback searches
 * @param album Track album, used for fallback searches
 * @param size Requested image size for Navidrome art (ignored for fallback)
 * @param fallbackService Service that fetches art from iTunes and MusicBrainz
 * @return Best available cover art URL, or null if none found
 */
@Composable
fun rememberCoverArtWithFallback(
    coverArtId: String?,
    artist: String?,
    album: String?,
    size: Int = 300,
    fallbackService: CoverArtFallbackService? = null,
): String? {
    val service = fallbackService ?: localFallbackService()
    val navidromeUrl = rememberCoverArtUrl(coverArtId, size)
    if (navidromeUrl != null) return navidromeUrl

    // Fallback: fetch from public services when artist/album are known
    if (artist != null && album != null && service != null) {
        var fallbackUrl by remember(artist, album) { mutableStateOf<String?>(null) }
        LaunchedEffect(artist, album) {
            if (fallbackUrl != null) return@LaunchedEffect
            service.fetchArt(artist, album).collect { url ->
                if (url != null) fallbackUrl = url
            }
        }
        return fallbackUrl
    }
    return null
}

/** Resolve the process-wide fallback service from the composition context. */
@Composable
private fun localFallbackService(): CoverArtFallbackService? {
    val context = LocalContext.current
    return remember(context) {
        try {
            CoverArtFallbackService.getInstance(context)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Resolves cover art preferring locally cached files over remote Navidrome URLs.
 * Falls back to external services if nothing is cached.
 *
 * Priority: local cache > Navidrome > external (iTunes/MusicBrainz)
 */
@Composable
fun rememberPreferredCoverArt(
    coverArtId: String?,
    artist: String?,
    album: String?,
    size: Int = 300,
    fallbackService: CoverArtFallbackService? = null,
): String? {
    val service = fallbackService ?: localFallbackService()

    // Subscribe only to this cell's disk keys — artist writes must not
    // invalidate every album/mix cover on Home LazyRows.
    val albumKey = if (!artist.isNullOrBlank() && !album.isNullOrBlank()) {
        "$artist|$album".lowercase()
    } else {
        null
    }
    val artistKey = if (!artist.isNullOrBlank()) "artist|$artist".lowercase() else null
    val navidromeKey = coverArtId?.takeIf { it.isNotBlank() }?.let { "navidrome|$it" }
    val cacheVersion = if (service == null) {
        0
    } else {
        (albumKey?.let { service.observeVersion(it) } ?: 0) +
            (if (albumKey == null) artistKey?.let { service.observeVersion(it) } ?: 0 else 0) +
            (navidromeKey?.let { service.observeVersion(it) } ?: 0)
    }

    // Check preference: prefer iTunes art when user enabled it
    val context = LocalContext.current
    val preferItunes = remember {
        try {
            com.lucasdss.ftpmusic.app.data.security.SecureStorage(
                context.applicationContext,
            ).get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_PREFER_ITUNES_ART)
                ?.toBooleanStrictOrNull() ?: false
        } catch (_: Exception) {
            false
        }
    }

    // When preferring iTunes, check external fallback FIRST (cached or fetch),
    // then fall back to Navidrome.
    if (preferItunes && artist != null && album != null && service != null) {
        var itunesUrl by remember(artist, album) { mutableStateOf<String?>(null) }
        LaunchedEffect(artist, album) {
            if (itunesUrl != null) return@LaunchedEffect
            service.fetchArt(artist, album).collect { url ->
                if (url != null) itunesUrl = url
            }
        }
        if (itunesUrl != null) return itunesUrl
    }

    // 1. Check local disk cache first (fastest, no network). Keyed on the art
    //    identity + per-key cache version so file syscalls/validation run ONLY
    //    when THIS cell's file lands — not when unrelated art is cached.
    val diskCacheUrl = remember(artist, album, coverArtId, cacheVersion) {
        if (service == null) {
            null
        } else {
            val cacheDir = service.cacheDir
            if (album != null && artist != null) {
                val cacheKey = "$artist|$album".lowercase()
                val localFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")
                if (CoverArtFiles.looksLikeImage(localFile)) {
                    "file://${localFile.absolutePath}"
                } else {
                    CoverArtFiles.deleteIfNotImage(localFile)
                    checkNavidromeDiskCache(service, coverArtId)
                }
            } else if (artist != null) {
                val cacheKey = "artist|$artist".lowercase()
                val localFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")
                if (CoverArtFiles.looksLikeImage(localFile)) {
                    "file://${localFile.absolutePath}"
                } else {
                    CoverArtFiles.deleteIfNotImage(localFile)
                    checkNavidromeDiskCache(service, coverArtId)
                }
            } else {
                checkNavidromeDiskCache(service, coverArtId)
            }
        }
    }
    if (diskCacheUrl != null) return diskCacheUrl

    // 2. Fall back to Navidrome URL
    if (coverArtId != null) {
        val navidromeUrl = rememberCoverArtUrl(coverArtId, size)
        if (navidromeUrl != null) return navidromeUrl
    }

    // 3. External services via fallback (same as rememberCoverArtWithFallback)
    if (artist != null && album != null && service != null) {
        var fallbackUrl by remember(artist, album) { mutableStateOf<String?>(null) }
        LaunchedEffect(artist, album) {
            if (fallbackUrl != null) return@LaunchedEffect
            service.fetchArt(artist, album).collect { url ->
                if (url != null) fallbackUrl = url
            }
        }
        return fallbackUrl
    }

    // Artist-only fallback
    if (artist != null && service != null) {
        var artistUrl by remember(artist) { mutableStateOf<String?>(null) }
        LaunchedEffect(artist) {
            if (artistUrl != null) return@LaunchedEffect
            service.fetchArtistArt(artist).collect { url ->
                if (url != null) artistUrl = url
            }
        }
        return artistUrl
    }

    return null
}

/**
 * Navidrome cover art cached to disk by [CoverArtFallbackService]. Validates
 * the file (not just `exists && length > 0`) and evicts a truncated/corrupt
 * entry so it can be re-fetched instead of rendering blank forever.
 */
private fun checkNavidromeDiskCache(fallbackService: CoverArtFallbackService, coverArtId: String?): String? {
    if (coverArtId == null) return null
    val navFile = File(fallbackService.cacheDir, "navidrome|${coverArtId.hashCode()}.jpg")
    if (CoverArtFiles.looksLikeImage(navFile)) {
        return "file://${navFile.absolutePath}"
    }
    CoverArtFiles.deleteIfNotImage(navFile)
    return null
}
