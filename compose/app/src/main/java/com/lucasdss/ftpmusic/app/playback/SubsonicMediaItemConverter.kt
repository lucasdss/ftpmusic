package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts Media3 [MediaItem]s (with direct Subsonic server URLs) to Cast [MediaQueueItem]s
 * and vice-versa.
 *
 * Local playback uses DIRECT Subsonic URLs — ExoPlayer's CacheDataSource handles caching
 * (no local proxy). Legacy proxy-format URLs (http://127.0.0.1:9000/stream?id=<id>&url=<encoded>)
 * from old persisted queues are still recognized and unwrapped.
 * Cast playback uses direct Subsonic URLs (Tier 1) or LAN proxy URLs (Tier 3, user-opt-in).
 *
 * URL resolution respects [CastPreferences.useHttpForCast] (http vs https) and
 * [CastPreferences.castFromPhone] (LAN proxy tier).
 */
@Singleton
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SubsonicMediaItemConverter @Inject constructor(private val castPreferences: CastPreferences) :
    MediaItemConverter {

    override fun toMediaQueueItem(item: MediaItem): MediaQueueItem {
        val mediaId = item.mediaId
        val uriStr = item.localConfiguration?.uri?.toString() ?: ""
        val mimeType = resolveMimeType(item)

        // Extract the remote Subsonic URL from legacy proxy URLs (direct URLs pass through)
        val remoteUrl = extractRemoteUrl(uriStr) ?: uriStr

        // Resolve the final Cast URL based on preferences
        val castUrl = resolveCastUrl(mediaId, remoteUrl)

        // Cast receiver uses streamDuration to calculate remaining time,
        // which drives preload (preloadTime fires when remaining ≤ preloadTime).
        // Without this, preload never triggers and auto-advance stalls.
        val durationMs = item.mediaMetadata.extras?.getLong("duration") ?: 0L

        val mediaInfo = MediaInfo.Builder(castUrl)
            .setContentType(mimeType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setStreamDuration(durationMs)
            .setMetadata(buildCastMetadata(item))
            .build()

        // Embed trackId in customData for reverse-mapping by CastQueueWindow.
        // The receiver preserves customData through the session, allowing us to
        // map receiver itemIds back to full-queue mediaIds on transitions.
        val customData = org.json.JSONObject()
        customData.put("trackId", mediaId)
        val entryId = item.queueEntryId()
        if (entryId > 0) customData.put("entryId", entryId)

        return MediaQueueItem.Builder(mediaInfo)
            .setItemId(if (entryId > 0) entryId else mediaId.hashCode())
            .setAutoplay(true)
            // Receiver preloads the next item 45s before the current ends —
            // gives slower servers/transcodes time to buffer for gapless advance.
            // Without this, auto-advance stalls 5-15s while the receiver
            // connects and buffers from scratch.
            .setPreloadTime(45.0)
            .setCustomData(customData)
            .build()
    }

    override fun toMediaItem(queueItem: MediaQueueItem): MediaItem {
        val mediaInfo = queueItem.media
        val castUrl = mediaInfo?.contentId
        // NEVER return MediaItem.EMPTY here: it has a null localConfiguration,
        // and its null URI crashes DefaultMediaSourceFactory.checkNotNull during
        // the Cast→ExoPlayer state transfer (onSessionEnding / onSessionUnavailable).
        // Build a valid fallback item instead.
        if (mediaInfo == null || castUrl.isNullOrEmpty()) {
            return buildFallbackItem(queueItem)
        }

        // Tier 3 LAN proxy URLs die when the Cast session ends — unwrap the embedded
        // remote URL so local playback doesn't point at a dead proxy.
        val localUrl = if (castUrl.contains(":9000/stream") && castUrl.contains("url=")) {
            extractRemoteUrl(castUrl) ?: castUrl
        } else {
            castUrl
        }

        // Extract track ID — prefer customData (set by CastQueueWindow), fall back to URL
        val trackId = queueItem.customData?.let { cd ->
            val id = cd.optString("trackId", "")
            if (id.isNotEmpty()) id else null
        } ?: extractTrackId(castUrl)

        val castMetadata = mediaInfo.metadata
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(castMetadata?.getString(MediaMetadata.KEY_TITLE))
            .setArtist(castMetadata?.getString(MediaMetadata.KEY_ARTIST))
            .setAlbumTitle(castMetadata?.getString(MediaMetadata.KEY_ALBUM_TITLE))
            .apply {
                castMetadata?.getImages()?.firstOrNull()?.url?.toString()?.let { url ->
                    setArtworkUri(Uri.parse(url))
                }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(trackId ?: "")
            .setUri(localUrl) // Direct URL, not proxy — CacheDataSource handles local caching
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Build a playable MediaItem when the Cast queue item lacks media/contentId.
     * Reconstructs a stream URL from the trackId if available (valid URI, avoids
     * the NPE in DefaultMediaSourceFactory), else uses a non-null placeholder.
     * Internal for regression testing.
     */
    internal fun buildFallbackItem(queueItem: MediaQueueItem): MediaItem {
        val trackId = queueItem.customData?.optString("trackId", "")
            ?.takeIf { it.isNotEmpty() }
        val url = if (trackId != null && com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.isConfigured()) {
            val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
            "$base/rest/stream?id=$trackId"
        } else {
            "https://music.example.com/rest/stream?id=unknown"
        }
        val castMetadata = queueItem.media?.metadata
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(castMetadata?.getString(MediaMetadata.KEY_TITLE))
            .setArtist(castMetadata?.getString(MediaMetadata.KEY_ARTIST))
            .build()
        return MediaItem.Builder()
            .setMediaId(trackId ?: queueItem.itemId.toString())
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
    }

    // ── URL resolution ──────────────────────────────────────────────

    /**
     * Visible for testing: resolves the Cast URL from a proxy URL.
     * In production, [toMediaQueueItem] calls this internally.
     */
    @androidx.annotation.VisibleForTesting
    internal fun resolveCastUrlFromProxy(proxyUrl: String, mediaId: String): String {
        val remoteUrl = extractRemoteUrl(proxyUrl) ?: proxyUrl
        return resolveCastUrl(mediaId, remoteUrl)
    }

    /**
     * Extract the embedded remote URL from legacy proxy URLs; returns null for direct URLs.
     * Legacy proxy format: http://127.0.0.1:9000/stream?id=<id>&url=<encodedRemoteUrl>
     * (also matches Tier 3 LAN proxy URLs, which use the same shape).
     */
    private fun extractRemoteUrl(proxyUrl: String): String? {
        if (!proxyUrl.contains("url=")) return null
        return try {
            // Use manual extraction for reliability in all environments
            val urlIdx = proxyUrl.indexOf("url=")
            if (urlIdx < 0) return null
            val encoded = proxyUrl.substring(urlIdx + 4)
            // Strip trailing params (but url= is the last param in our proxy URLs)
            val ampIdx = encoded.indexOf('&')
            val value = if (ampIdx > 0) encoded.substring(0, ampIdx) else encoded
            // Uri.getQueryParameter automatically decodes, but our manual approach doesn't.
            // Check if the value looks URL-encoded before decoding.
            if (value.contains("%")) {
                java.net.URLDecoder.decode(value, "UTF-8")
            } else {
                value
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolve the Cast-appropriate URL for a given track.
     *
     * Tier 1 (default): Direct Subsonic server URL, optionally downgraded to HTTP.
     * Tier 3 (castFromPhone): LAN proxy URL — phone streams cached file to Cast receiver.
     */
    private fun resolveCastUrl(mediaId: String, remoteUrl: String): String {
        if (castPreferences.castFromPhone) {
            val lanIp = getLanIpAddress() ?: return remoteUrl
            val encoded = URLEncoder.encode(remoteUrl, "UTF-8")
            return "http://$lanIp:9000/stream?id=$mediaId&url=$encoded"
        }

        // Tier 1: Direct server URL — optionally as HTTP
        return if (castPreferences.useHttpForCast && remoteUrl.startsWith("https://")) {
            remoteUrl.replaceFirst("https://", "http://")
        } else {
            remoteUrl
        }
    }

    /** Extract track ID from a Subsonic stream URL (id=<id> or /rest/stream?id=<id>). */
    private fun extractTrackId(castUrl: String): String? = try {
        Uri.parse(castUrl).getQueryParameter("id")
    } catch (_: Exception) {
        null
    }

    /** Get LAN IP address for Tier 3 Cast-from-Phone. */
    private fun getLanIpAddress(): String? = try {
        java.net.NetworkInterface.getNetworkInterfaces()?.asSequence()
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }

    // ── Metadata helpers ────────────────────────────────────────────

    private fun resolveMimeType(item: MediaItem): String {
        val mime = item.localConfiguration?.mimeType
        if (mime != null && mime != "application/octet-stream") return mime
        // Infer from URL extension
        val uri = item.localConfiguration?.uri?.toString() ?: return "audio/mpeg"
        return when {
            uri.contains(".flac", ignoreCase = true) -> "audio/flac"
            uri.contains(".ogg", ignoreCase = true) || uri.contains(".opus", ignoreCase = true) -> "audio/ogg"
            uri.contains(".aac", ignoreCase = true) -> "audio/aac"
            uri.contains(".wav", ignoreCase = true) -> "audio/wav"
            else -> "audio/mpeg"
        }
    }

    private fun buildCastMetadata(item: MediaItem): MediaMetadata {
        val src = item.mediaMetadata
        val cast = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        src.title?.let { cast.putString(MediaMetadata.KEY_TITLE, it.toString()) }
        src.artist?.let { cast.putString(MediaMetadata.KEY_ARTIST, it.toString()) }
        src.albumTitle?.let { cast.putString(MediaMetadata.KEY_ALBUM_TITLE, it.toString()) }
        src.subtitle?.let { cast.putString(MediaMetadata.KEY_SUBTITLE, it.toString()) }
        src.artworkUri?.let { cast.addImage(com.google.android.gms.common.images.WebImage(it)) }
        return cast
    }
}
