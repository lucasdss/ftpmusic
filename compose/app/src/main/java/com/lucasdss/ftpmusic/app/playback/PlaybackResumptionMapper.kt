package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.lucasdss.ftpmusic.app.data.model.Track

/**
 * Pure helpers for Media3 [MediaSession.Callback.onPlaybackResumption] (ADR-0087/0088).
 * Builds local-metadata MediaItems from Room-saved queue — no network on critical path.
 */
object PlaybackResumptionMapper {

    /** Hard cap for onGetSession wait while BT restore seats (ADR-0088). */
    const val ON_GET_SESSION_WAIT_MS = 400L

    /** Android 17 = API 37 background-audio WIU gate. */
    const val ANDROID_17_API = 37

    /** True when MediaService should skip Room restore overwrite. */
    fun shouldSkipQueueRestore(mediaItemCount: Int, isCasting: Boolean): Boolean = isCasting || mediaItemCount > 0

    /**
     * API ≤36: background A2DP `play()` is OK.
     * API ≥37: BFSL FGS often lacks WIU — rely on media-key / Resume notif instead.
     */
    fun shouldBackgroundAutoplay(sdkInt: Int): Boolean = sdkInt < ANDROID_17_API

    /**
     * Migrate persisted stream URLs (legacy pipe / 127.0.0.1:9000 proxy) to a
     * playable remote URL aligned with [trackId] — same rules as PlaybackManager.
     */
    fun migrateStreamUrl(trackId: String, serverUrl: String): String {
        val cleanUrl = serverUrl.substringBefore("|")
        val migrated = if (cleanUrl.contains("127.0.0.1:9000/stream") && cleanUrl.contains("url=")) {
            val urlIdx = cleanUrl.indexOf("url=")
            val encoded = cleanUrl.substring(urlIdx + 4).substringBefore('&')
            try {
                java.net.URLDecoder.decode(encoded, "UTF-8")
            } catch (_: Exception) {
                cleanUrl
            }
        } else {
            cleanUrl
        }
        return alignStreamUrlToTrackId(trackId, migrated)
    }

    /**
     * Build resumption playlist from [saved]. Returns null when queue empty
     * (caller should fail the future — never return empty MediaItemsWithStartPosition).
     */
    fun toMediaItemsWithStartPosition(
        saved: SavedQueueState,
        artworkUriFor: (Track) -> Uri? = { null },
    ): MediaSession.MediaItemsWithStartPosition? {
        if (saved.tracks.isEmpty()) return null
        val urls = saved.urls
        val items = saved.tracks.mapIndexed { index, track ->
            val rawUrl = urls.getOrNull(index).orEmpty()
            val url = if (rawUrl.isNotBlank()) migrateStreamUrl(track.id, rawUrl) else ""
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album ?: "")
                .apply {
                    artworkUriFor(track)?.let { setArtworkUri(it) }
                    setExtras(
                        Bundle().apply {
                            putLong("duration", (track.duration ?: 0) * 1000L)
                            putString("type", "music")
                            putString("artistId", track.artistId)
                            putString("albumId", track.albumId)
                        },
                    )
                }
                .build()
            val builder = MediaItem.Builder()
                .setMediaId(track.id)
                .setMediaMetadata(metadata)
            if (url.isNotBlank()) {
                builder.setUri(url)
            }
            builder.build()
        }
        val startIndex = saved.currentIndex.coerceIn(0, items.lastIndex)
        return MediaSession.MediaItemsWithStartPosition(
            items,
            startIndex,
            saved.positionMs.coerceAtLeast(0L),
        )
    }

    /** Snapshot already-seated player as resumption playlist (skip overwrite). */
    fun fromPlayer(player: Player): MediaSession.MediaItemsWithStartPosition? {
        val count = player.mediaItemCount
        if (count <= 0) return null
        val items = (0 until count).map { player.getMediaItemAt(it) }
        val index = player.currentMediaItemIndex.coerceIn(0, items.lastIndex)
        return MediaSession.MediaItemsWithStartPosition(
            items,
            index,
            player.currentPosition.coerceAtLeast(0L),
        )
    }

    fun applyTransportExtras(player: Player, repeatMode: Int, shuffleEnabled: Boolean) {
        player.repeatMode = repeatMode
        player.shuffleModeEnabled = shuffleEnabled
    }

    /** Pure gate: binder may wait only while restore in flight and player empty. */
    fun shouldWaitForRestoreOnGetSession(playerEmpty: Boolean, restoreInFlight: Boolean): Boolean =
        playerEmpty && restoreInFlight
}
