package com.lucasdss.ftpmusic.app.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.ThumbRating
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.lucasdss.ftpmusic.app.R

/**
 * Media3 custom session commands + command buttons for like/dislike.
 * Thumbs go in [CommandButton.SLOT_OVERFLOW] so compact transport stays play/pause/prev/next.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object MediaSessionReactionCommands {
    const val ACTION_LIKE = "com.lucasdss.ftpmusic.app.action.LIKE"
    const val ACTION_DISLIKE = "com.lucasdss.ftpmusic.app.action.DISLIKE"

    /** Lazy — avoids Bundle.EMPTY init on JVM unit tests that only need action strings. */
    val LIKE_COMMAND: SessionCommand by lazy { SessionCommand(ACTION_LIKE, Bundle.EMPTY) }
    val DISLIKE_COMMAND: SessionCommand by lazy { SessionCommand(ACTION_DISLIKE, Bundle.EMPTY) }

    fun buttons(isStarred: Boolean, isDisliked: Boolean): ImmutableList<CommandButton> {
        val like = CommandButton.Builder(
            if (isStarred) CommandButton.ICON_THUMB_UP_FILLED else CommandButton.ICON_THUMB_UP_UNFILLED,
        )
            .setDisplayName(if (isStarred) "Unlike" else "Like")
            .setCustomIconResId(R.drawable.ic_thumb_up)
            .setSessionCommand(LIKE_COMMAND)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        val dislike = CommandButton.Builder(
            if (isDisliked) {
                CommandButton.ICON_THUMB_DOWN_FILLED
            } else {
                CommandButton.ICON_THUMB_DOWN_UNFILLED
            },
        )
            .setDisplayName(if (isDisliked) "Remove dislike" else "Dislike")
            .setCustomIconResId(R.drawable.ic_thumb_down)
            .setSessionCommand(DISLIKE_COMMAND)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        return ImmutableList.of(like, dislike)
    }

    /** ThumbRating: up=like, down=dislike, unrated=neither (Auto / rating controllers). */
    fun thumbRating(isStarred: Boolean, isDisliked: Boolean): ThumbRating = when {
        isStarred -> ThumbRating(true)
        isDisliked -> ThumbRating(false)
        else -> ThumbRating()
    }

    fun applyThumbRatingToCurrentItem(player: Player, isStarred: Boolean, isDisliked: Boolean) {
        val index = player.currentMediaItemIndex
        if (index < 0) return
        val item = player.currentMediaItem ?: return
        val updated = mediaItemWithThumb(item, isStarred, isDisliked)
        try {
            player.replaceMediaItem(index, updated)
        } catch (_: Exception) {
            // Cast / transient players may reject replace — ignore.
        }
    }

    fun metadataWithThumb(meta: MediaMetadata, isStarred: Boolean, isDisliked: Boolean): MediaMetadata =
        meta.buildUpon()
            .setUserRating(thumbRating(isStarred, isDisliked))
            .build()

    fun mediaItemWithThumb(item: MediaItem, isStarred: Boolean, isDisliked: Boolean): MediaItem = item.buildUpon()
        .setMediaMetadata(metadataWithThumb(item.mediaMetadata, isStarred, isDisliked))
        .build()
}
