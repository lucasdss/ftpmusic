package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import com.lucasdss.ftpmusic.app.data.model.Track

/**
 * Pure helpers for Media3 [MediaSession.Callback.onPlaybackResumption] (ADR-0087).
 * Builds local-metadata MediaItems from Room-saved queue — no network on critical path.
 */
object PlaybackResumptionMapper {

    /** True when MediaService should skip Room restore overwrite. */
    fun shouldSkipQueueRestore(mediaItemCount: Int, isCasting: Boolean): Boolean =
        isCasting || mediaItemCount > 0

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
            val url = urls.getOrNull(index).orEmpty()
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

    fun applyTransportExtras(
        player: androidx.media3.common.Player,
        repeatMode: Int,
        shuffleEnabled: Boolean,
    ) {
        player.repeatMode = repeatMode
        player.shuffleModeEnabled = shuffleEnabled
    }
}
