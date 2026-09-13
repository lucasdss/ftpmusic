package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState

/**
 * Filters null-URI [MediaItem]s out of a Cast→ExoPlayer state transfer.
 *
 * media3 1.10.1's `CastPlayer.TransferCallback.DEFAULT` is a plain
 * `PlayerTransferState.fromPlayer(source).setToPlayer(target)` with NO
 * filtering. A null-URI item — `MediaItem.EMPTY`, which CastTimelineTracker
 * produces for the current item when its in-memory contentId map is empty
 * (e.g. after process death / reinstall reconnect where the queue load is
 * skipped) — crashes `DefaultMediaSourceFactory.checkNotNull` during the
 * transfer. This mirrors the upstream `DefaultCastPlayerTransferCallback`
 * filter that was added after 1.10.1.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object CastTransferFilter {

    /**
     * Pure decision logic: computes the filtered playable list and the new
     * current index, plus whether the current item was filtered (position
     * must reset). Split out for unit-testability without a real Player.
     */
    internal data class FilterResult(
        val playable: List<MediaItem>,
        val newCurrentIndex: Int,
        val currentFiltered: Boolean,
    )

    internal fun computeFilter(mediaItems: List<MediaItem>, currentIndex: Int): FilterResult {
        val playable = mediaItems.filter { it.localConfiguration?.uri != null }
        if (playable.size == mediaItems.size) {
            return FilterResult(playable, currentIndex, currentFiltered = false)
        }
        var newIndex = currentIndex
        var currentFiltered = false
        for (i in 0 until mediaItems.size) {
            if (mediaItems[i].localConfiguration?.uri == null) {
                if (i < newIndex) {
                    newIndex--
                } else if (i == newIndex) {
                    currentFiltered = true
                }
            }
        }
        if (playable.isEmpty()) {
            newIndex = C.INDEX_UNSET
        } else {
            newIndex = minOf(newIndex, playable.size - 1)
        }
        return FilterResult(playable, newIndex, currentFiltered)
    }

    /**
     * Applies the filtered transfer state to [targetPlayer]. Items without a
     * localConfiguration URI are dropped and the current index/position are
     * adjusted. Returns whether any items were filtered.
     */
    fun transferFilteredState(sourcePlayer: Player, targetPlayer: Player): Boolean {
        val transferState = PlayerTransferState.fromPlayer(sourcePlayer)
        val mediaItems = transferState.getMediaItems()
        val result = computeFilter(mediaItems, transferState.getCurrentMediaItemIndex())
        if (result.playable.size == mediaItems.size) {
            transferState.setToPlayer(targetPlayer)
            return false
        }
        val builder = transferState.buildUpon()
            .setMediaItems(result.playable)
            .setCurrentMediaItemIndex(result.newCurrentIndex)
        if (result.currentFiltered) builder.setCurrentPosition(0)
        builder.build().setToPlayer(targetPlayer)
        return true
    }

    /** Same logic as upstream: item is playable iff it has a non-null URI. */
    fun isPlayable(mediaItem: MediaItem): Boolean = mediaItem.localConfiguration?.uri != null
}
