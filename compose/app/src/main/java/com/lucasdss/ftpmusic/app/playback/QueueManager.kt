package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queue management for ExoPlayer / CastPlayer.
 * Methods mirror Flutter MusicPlayerHandler.
 * Uses PlayerHolder.player instead of taking Player as a parameter.
 */
@Singleton
class QueueManager @Inject constructor(private val authHelper: SubsonicAuthHelper) {

    private val player: androidx.media3.common.Player?
        get() = PlayerHolder.player

    /** Canonical queue size — reads directly from Player to stay in sync. */
    val size: Int get() = PlayerHolder.exoPlayer?.mediaItemCount ?: PlayerHolder.player?.mediaItemCount ?: 0

    /** Build an authenticated cover art URL for the lock screen. */
    internal fun resolveCoverArtUrl(coverArtId: String): String? {
        val base = DynamicBaseUrl.url.trimEnd('/')
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isEmpty() || base.isEmpty()) return null
        val authParams = authHelper.buildAuthParams(username, password)
        val params = authParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$base/rest/getCoverArt?id=$coverArtId&$params&size=300"
    }

    fun buildMediaItem(
        id: String,
        title: String,
        url: String,
        artist: String? = null,
        album: String? = null,
        durationMs: Long = 0,
        coverArt: String? = null,
        artworkUrl: String? = null,
        mediaType: String = "music",
        mimeType: String? = null,
        artistId: String? = null,
        albumId: String? = null,
    ): MediaItem {
        // v46 guard: never build a cover URL from the literal "getCoverArt"
        // (stale persisted rows from the artwork bug).
        val safeCoverArt = coverArt?.takeUnless { it == "getCoverArt" }
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
        // Resolve artwork URI for lock screen display
        val resolvedArtwork = artworkUrl ?: safeCoverArt?.let { resolveCoverArtUrl(it) }
        if (resolvedArtwork != null) {
            metadataBuilder.setArtworkUri(Uri.parse(resolvedArtwork))
        } else if (safeCoverArt != null) {
            metadataBuilder.setArtworkUri(Uri.parse("cover:$safeCoverArt"))
        }
        metadataBuilder.setExtras(
            Bundle().apply {
                putLong("duration", durationMs)
                putString("type", mediaType)
                if (artistId != null) putString("artistId", artistId)
                if (albumId != null) putString("albumId", albumId)
                // Source of truth for cover art id — avoids URI parsing at read time
                // (artworkUri.lastPathSegment returns "getCoverArt" for full URLs).
                if (safeCoverArt != null) putString("coverArtId", safeCoverArt)
            },
        )
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(url)
            .setMimeType(mimeType)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    /** Replace queue with given tracks, start playing from startIndex (default 0). */
    fun playAll(items: List<MediaItem>, startIndex: Int = 0) {
        val p = player ?: run {
            android.util.Log.e("ftpmusic-playback", "[DEBUG-q] playAll: PlayerHolder.player is NULL")
            return
        }
        if (items.isEmpty()) return
        android.util.Log.d("ftpmusic-playback", "[DEBUG-q] playAll: ${items.size} items, startIndex=$startIndex")
        p.setMediaItems(items)
        p.prepare()
        if (startIndex > 0) p.seekToDefaultPosition(startIndex)
        p.play()
    }

    fun shuffleAndPlay(items: List<MediaItem>) {
        val p = player ?: return
        if (items.isEmpty()) return
        p.setMediaItems(items.shuffled())
        p.prepare()
        p.play()
    }

    fun addToQueue(item: MediaItem) {
        val p = player ?: return
        p.addMediaItem(item)
    }

    fun playNext(item: MediaItem) {
        val p = player ?: return
        val idx = p.currentMediaItemIndex + 1
        p.addMediaItem(idx, item)
    }

    fun addAllToQueue(items: List<MediaItem>) {
        val p = player ?: return
        val wasEmpty = p.mediaItemCount == 0
        p.addMediaItems(items)
        if (wasEmpty && items.isNotEmpty()) {
            p.prepare()
            p.play()
        }
    }

    fun playNextAll(items: List<MediaItem>) {
        val p = player ?: return
        val idx = p.currentMediaItemIndex + 1
        for ((i, item) in items.withIndex()) {
            p.addMediaItem(idx + i, item)
        }
    }

    fun remove(index: Int) {
        val p = player ?: return
        if (index < 0 || index >= p.mediaItemCount) return
        p.removeMediaItem(index)
    }

    /** Move track from one position to another in queue. */
    fun move(from: Int, to: Int) {
        val p = player ?: return
        if (from < 0 || from >= p.mediaItemCount) return
        if (to < 0 || to >= p.mediaItemCount) return
        p.moveMediaItem(from, to)
    }

    /** Jump playback to queue item at given index. */
    fun playFromIndex(index: Int) {
        val p = player ?: return
        if (index < 0 || index >= p.mediaItemCount) return
        p.seekToDefaultPosition(index)
        p.playWhenReady = true
    }

    /** Clear all items from queue and playback state. */
    fun clear() {
        player?.clearMediaItems()
    }
}
