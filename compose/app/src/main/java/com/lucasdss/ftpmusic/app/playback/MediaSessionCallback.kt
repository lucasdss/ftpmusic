package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * Implements the Android SDK-recommended pattern for MediaLibrarySession:
 * overrides onAddMediaItems to expand single-track external play requests
 * (from Bluetooth, Android Auto, notification, Wear OS) to the full
 * parent album context.
 *
 * Also implements [onPlaybackResumption] (ADR-0087) so MediaButtonReceiver /
 * System UI can rebuild the last queue after process death.
 *
 * The requested track is placed first in the returned list so playback
 * starts at the correct position. The remaining album tracks follow in
 * their original order.
 *
 * DB queries run asynchronously via coroutines — the callback's
 * ListenableFuture resolves once expansion completes, keeping the
 * binder thread unblocked.
 */
class MediaSessionCallback(
    private val trackDao: TrackDao,
    private val metadataDao: CachedMetadataDao,
    private val authHelper: SubsonicAuthHelper,
    private val api: SubsonicApi,
    private val localSearch: com.lucasdss.ftpmusic.app.data.search.LocalSearchRepository? = null,
    private val persistenceManager: QueuePersistenceManager? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : MediaLibraryService.MediaLibrarySession.Callback {

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        if (mediaItems.size != 1) return Futures.immediateFuture(mediaItems)
        val mediaId = mediaItems.first().mediaId ?: return Futures.immediateFuture(mediaItems)
        return scope.future {
            try {
                val expanded = expandToAlbum(mediaId)
                if (expanded != null && expanded.size > 1) expanded else mediaItems
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[MediaSessionCallback] expand failed for $mediaId", e)
                mediaItems
            }
        }
    }

    /**
     * ADR-0087: rebuild last queue from Room for BT / System UI resumption.
     * Never returns an empty playlist when a saved queue exists (Media3 FGS hazard).
     * When no queue: fail like the Media3 default (UnsupportedOperationException).
     */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        val persistence = persistenceManager
            ?: throw UnsupportedOperationException("Queue persistence unavailable")
        val saved = persistence.restore()
            ?: throw UnsupportedOperationException("No saved queue for resumption")
        val playlist = PlaybackResumptionMapper.toMediaItemsWithStartPosition(
            saved,
            artworkUriFor = { track -> artworkUriForTrack(track.coverArt) },
        ) ?: throw UnsupportedOperationException("Empty saved queue")
        if (isForPlayback) {
            try {
                PlaybackResumptionMapper.applyTransportExtras(
                    mediaSession.player,
                    saved.repeatMode,
                    saved.shuffleEnabled,
                )
            } catch (e: Exception) {
                android.util.Log.w(
                    "ftpmusic",
                    "[MediaSessionCallback] applyTransportExtras failed: ${e.message}",
                )
            }
        }
        android.util.Log.i(
            "ftpmusic",
            "[MediaSessionCallback] onPlaybackResumption items=${playlist.mediaItems.size} " +
                "index=${playlist.startIndex} isForPlayback=$isForPlayback",
        )
        playlist
    }

    private fun artworkUriForTrack(coverArtId: String?): Uri? {
        if (coverArtId.isNullOrBlank() || coverArtId == "getCoverArt") return null
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        val baseUrl = DynamicBaseUrl.url.trimEnd('/')
        if (username.isEmpty() || password.isEmpty() || !DynamicBaseUrl.isConfigured()) return null
        return try {
            val authParams = authHelper.buildAuthParams(username, password)
            val authQs = authParams.entries.joinToString("&") {
                "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
            Uri.parse("$baseUrl/rest/getCoverArt?id=$coverArtId&$authQs")
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun expandToAlbum(mediaId: String, prebuiltAuth: Map<String, String>? = null): List<MediaItem>? {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        val baseUrl = DynamicBaseUrl.url.trimEnd('/')
        if (username.isEmpty() || password.isEmpty() || !DynamicBaseUrl.isConfigured()) return null

        val track = trackDao.getTrack(mediaId) ?: return null
        val albumId = track.albumId ?: return null

        val albumTracks = metadataDao.getAlbumTracks(albumId)
        if (albumTracks.size <= 1) return null

        // If requested track not in album, fall back to single item
        if (albumTracks.none { it.id == mediaId }) return null

        // Look up album name for setAlbumTitle
        val albumName = metadataDao.getAlbumName(albumId)

        // Build auth params once for all cover art URLs — reuse if provided by caller
        val authParams = prebuiltAuth ?: authHelper.buildAuthParams(username, password)
        val authQs = authParams.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

        // Build MediaItems with the requested track FIRST, then remaining in order
        val allItems = albumTracks.map { t ->
            val url = authHelper.buildStreamUrl(baseUrl, t.id, username, password)
            val metadata = MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(albumName ?: "")
                .apply {
                    if (t.coverArt != null) {
                        setArtworkUri(android.net.Uri.parse("$baseUrl/rest/getCoverArt?id=${t.coverArt}&$authQs"))
                    }
                    setExtras(
                        Bundle().apply {
                            putLong("duration", (t.duration ?: 0) * 1000L)
                            putString("type", "music")
                            putString("artistId", t.artistId)
                            putString("albumId", t.albumId)
                        },
                    )
                }
                .build()

            MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(url)
                .setMediaMetadata(metadata)
                .build()
        }

        // Reorder: requested track first, then rest in album order (single-pass)
        val (requested, rest) = allItems.partition { it.mediaId == mediaId }
        return requested + rest
    }

    /**
     * Search Navidrome for [query], take the top song match, and expand
     * it to full album context. Returns empty list if no match found
     * or credentials are missing.
     *
     * Called from MediaService when a MEDIA_PLAY_FROM_SEARCH intent is
     * received (Assistant / Gemini voice commands). The caller is
     * responsible for setting items on the player and starting playback.
     *
     * Public visibility for use by MediaService; not part of the
     * Callback interface.
     */
    suspend fun searchAndNavidromeExpand(query: String): List<MediaItem> {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        val baseUrl = DynamicBaseUrl.url.trimEnd('/')
        if (username.isEmpty() || password.isEmpty() || !DynamicBaseUrl.isConfigured()) {
            android.util.Log.d("ftpmusic", "[MediaSessionCallback] searchAndNavidromeExpand: credentials not available")
            return emptyList()
        }

        val auth = authHelper.buildAuthParams(username, password)

        // Prefer local FTS/LIKE corpus (same as Search tab), then search3.
        try {
            val local = localSearch?.search(query, limit = 10, playableOnly = false)
            val localTrack = local?.tracks?.firstOrNull()
            if (localTrack != null) {
                android.util.Log.d(
                    "ftpmusic",
                    "[MediaSessionCallback] searchAndNavidromeExpand: local hit '$query' → ${localTrack.id}",
                )
                return expandToAlbum(localTrack.id, auth) ?: emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic", "[MediaSessionCallback] local search failed: ${e.message}")
        }

        val response = api.search3(query, songCount = 10, artistCount = 20, albumCount = 10, auth = auth)
        val sr = response["subsonic-response"] as? Map<*, *> ?: run {
            android.util.Log.d(
                "ftpmusic",
                "[MediaSessionCallback] searchAndNavidromeExpand: no subsonic-response in result",
            )
            return emptyList()
        }
        val searchResult = sr["searchResult3"] as? Map<*, *> ?: run {
            android.util.Log.d("ftpmusic", "[MediaSessionCallback] searchAndNavidromeExpand: no searchResult3")
            return emptyList()
        }
        val songs: List<*> = when (val raw = searchResult["song"]) {
            is List<*> -> raw

            is Map<*, *> -> listOf(raw)

            // Subsonic API returns single object, not list, for 1 result
            else -> null
        } ?: run {
            android.util.Log.d("ftpmusic", "[MediaSessionCallback] searchAndNavidromeExpand: no songs in result")
            return emptyList()
        }
        if (songs.isEmpty()) return emptyList()

        val firstSong = songs.first() as? Map<*, *> ?: return emptyList()
        val songId = firstSong["id"] as? String ?: return emptyList()
        android.util.Log.d(
            "ftpmusic",
            "[MediaSessionCallback] searchAndNavidromeExpand: matched '$query' → songId=$songId",
        )

        return expandToAlbum(songId, auth) ?: emptyList()
    }

    /** Cancel all pending coroutines. Call from onDestroy. */
    fun destroy() {
        scope.cancel()
    }
}
