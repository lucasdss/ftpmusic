package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

/**
 * Extract the cover art id from a track's metadata.
 *
 * Priority: the `coverArtId` extra written by QueueManager.buildMediaItem
 * (exact id known at build time) → [coverArtIdFromUri] on the artwork URI.
 *
 * Do NOT use `artworkUri.lastPathSegment` alone for full URLs — the last PATH
 * segment of `…/rest/getCoverArt?id=al-1` is `"getCoverArt"`, not the id.
 */
internal fun extractCoverArtId(metadata: MediaMetadata?): String? {
    metadata?.extras?.getString("coverArtId")?.let { return it }
    return coverArtIdFromUri(metadata?.artworkUri)
}

/**
 * Extract the cover art id from an artwork URI: the `id` query parameter for
 * full `getCoverArt?id=…` URLs, the last path segment for hierarchical URIs,
 * or the value after the scheme for opaque `cover:<id>` URIs.
 */
internal fun coverArtIdFromUri(uri: android.net.Uri?): String? {
    if (uri == null) return null
    if (uri.isHierarchical) {
        uri.getQueryParameter("id")?.let { return it }
        uri.lastPathSegment?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }
    // Opaque URIs (e.g. cover:al-123) — take everything after the scheme.
    val s = uri.toString()
    val idx = s.indexOf(':')
    return if (idx >= 0) s.substring(idx + 1).takeIf { it.isNotBlank() } else null
}

/**
 * Provider of playback state from the MediaSession.
 * Interface allows testing with FakePlaybackStateProvider.
 */
interface PlaybackStateProvider {
    val playbackState: StateFlow<PlaybackState>

    /** High-frequency position (ms). Split from [playbackState] so the UI can
     *  collect metadata and the 200 ms position tick in SEPARATE scopes — a
     *  single merged flow makes every tick recompose the whole tree (P1). */
    val positionMs: StateFlow<Long>
    fun playPause()
    fun skipNext()
    fun skipPrev()
    fun seekTo(fraction: Float)
    fun toggleRepeat()
    fun toggleShuffle()
    fun toggleSpeed()
    fun setVolume(volume: Float)

    /** Arm the sleep timer at [endMs] (epoch ms). Enforcement lives in MediaService
     *  so the pause survives recents-swipe and process death (persisted state
     *  re-arms the service on restart). */
    fun armSleepTimer(endMs: Long)

    /** Cancel any armed sleep timer in the service. */
    fun cancelSleepTimer()

    /** Push non-player state fields so all consumers see the same canonical state. */
    fun updateExtraState(
        isStarred: Boolean,
        sleepTimerEndMs: Long,
        downloadedTrackIds: Set<String>,
        isDisliked: Boolean = false,
        trackRating: Int = 0,
    )
}

@androidx.compose.runtime.Stable
data class PlaybackState(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val coverArtId: String? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleModeEnabled: Boolean = false,
    val nextTrackTitle: String? = null,
    val nextTrackArtist: String? = null,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null,
    val volume: Float = 1.0f,
    val muted: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerEndMs: Long = 0L,
    val currentTrackId: String? = null,
    val isStarred: Boolean = false,
    /** v41: local thumbs-down. Mutually exclusive with isStarred (like). */
    val isDisliked: Boolean = false,
    /** 0-5 user rating (how much you enjoy the track). Local-first + server best-effort. */
    val trackRating: Int = 0,
    val mediaType: String = "music",
    val trackIndex: Int = 0,
    val queueSize: Int = 0,
    val artistId: String? = null,
    val albumId: String? = null,
    val isOffline: Boolean = false,
    val isQueueSynced: Boolean = true,
    val downloadedTrackIds: Set<String> = emptySet(),
    val contextSource: String? = null,
    val priorityQueueSize: Int = 0,
) {
    /** MiniPlayer should be visible when a track is loaded. */
    val isVisible: Boolean get() = title != null

    companion object {
        fun fromPlayer(player: Player, prevState: PlaybackState = PlaybackState()): PlaybackState {
            val mediaItem = player.currentMediaItem
            val metadata = mediaItem?.mediaMetadata
            val extras = metadata?.extras
            val nextIndex = player.currentMediaItemIndex + 1
            val nextItem = if (nextIndex < player.mediaItemCount) player.getMediaItemAt(nextIndex) else null
            return buildPlaybackState(
                mediaItem = mediaItem,
                nextItem = nextItem,
                isPlaying = when {
                    // During Cast, the receiver reports isPlaying=false while buffering the
                    // next track. Treat STATE_BUFFERING as playing so Now Playing / notification /
                    // Quick Settings don't show "stopped" during track transitions.
                    PlayerHolder.isCasting -> player.isPlaying || player.playbackState == Player.STATE_BUFFERING

                    else -> player.isPlaying
                },
                position = player.currentPosition,
                duration = player.duration,
                repeatMode = player.repeatMode,
                shuffleModeEnabled = player.shuffleModeEnabled,
                playbackSpeed = player.playbackParameters.speed,
                prevState = prevState,
                localVolumeFallback = player.volume,
                trackIndex = player.currentMediaItemIndex,
                queueSize = player.mediaItemCount,
            )
        }

        /**
         * Build a [PlaybackState] from the Cast receiver's reported status plus the
         * LOCAL queue item (resolved via `resolveLocalIndexFromReceiverId`). This is
         * the receiver-state mirror: it keeps UI/notification live even when media3's
         * CastPlayer timeline is empty/stale (e.g. queue loaded via raw SDK APIs).
         */
        fun fromCastState(
            item: MediaItem,
            nextItem: MediaItem?,
            prevState: PlaybackState,
            isPlaying: Boolean,
            positionMs: Long,
            index: Int,
            queueSize: Int,
        ): PlaybackState {
            val extras = item.mediaMetadata.extras
            val durationMs = extras?.getLong("duration") ?: item.mediaMetadata.durationMs
            return buildPlaybackState(
                mediaItem = item,
                nextItem = nextItem,
                isPlaying = isPlaying,
                position = positionMs,
                duration = durationMs ?: 0L,
                repeatMode = prevState.repeatMode,
                shuffleModeEnabled = prevState.shuffleModeEnabled,
                playbackSpeed = prevState.playbackSpeed,
                prevState = prevState,
                localVolumeFallback = prevState.volume,
                trackIndex = index,
                queueSize = queueSize,
            )
        }

        /** Shared field mapping for [fromPlayer] and [fromCastState]. */
        private fun buildPlaybackState(
            mediaItem: MediaItem?,
            nextItem: MediaItem?,
            isPlaying: Boolean,
            position: Long,
            duration: Long,
            repeatMode: Int,
            shuffleModeEnabled: Boolean,
            playbackSpeed: Float,
            prevState: PlaybackState,
            localVolumeFallback: Float,
            trackIndex: Int,
            queueSize: Int,
        ): PlaybackState {
            val metadata = mediaItem?.mediaMetadata
            val extras = metadata?.extras
            return PlaybackState(
                title = metadata?.title?.toString(),
                artist = metadata?.artist?.toString(),
                album = metadata?.albumTitle?.toString(),
                coverArtId = extractCoverArtId(metadata),
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                repeatMode = repeatMode,
                shuffleModeEnabled = shuffleModeEnabled,
                nextTrackTitle = nextItem?.mediaMetadata?.title?.toString(),
                nextTrackArtist = nextItem?.mediaMetadata?.artist?.toString(),
                isCasting = PlayerHolder.isCasting,
                castDeviceName = PlayerHolder.castDeviceName,
                volume = when {
                    PlayerHolder.isCasting && PlayerHolder.pendingCastVolume != null -> PlayerHolder.pendingCastVolume!!

                    PlayerHolder.isCasting -> {
                        val v = PlayerHolder.castVolume
                        if (v > 0.001f) {
                            v
                        } else {
                            // Fall back to the Cast session's actual device volume
                            (PlayerHolder.castDeviceVolume ?: 0.5f)
                        }
                    }

                    else -> localVolumeFallback
                },
                muted = if (PlayerHolder.isCasting) PlayerHolder.castDeviceMuted else false,
                playbackSpeed = playbackSpeed,
                sleepTimerEndMs = prevState.sleepTimerEndMs,
                currentTrackId = mediaItem?.mediaId,
                isStarred = prevState.isStarred,
                isDisliked = prevState.isDisliked,
                trackRating = prevState.trackRating,
                mediaType = extras?.getString("type") ?: "music",
                trackIndex = trackIndex,
                queueSize = queueSize,
                artistId = extras?.getString("artistId"),
                albumId = extras?.getString("albumId"),
                isOffline = prevState.isOffline,
                isQueueSynced = !PlayerHolder.queueSaveFailed,
                downloadedTrackIds = prevState.downloadedTrackIds,
                contextSource = prevState.contextSource,
            )
        }
    }
}
