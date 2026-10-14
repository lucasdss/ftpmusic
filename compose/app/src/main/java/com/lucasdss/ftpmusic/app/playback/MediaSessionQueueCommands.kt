package com.lucasdss.ftpmusic.app.playback

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.lucasdss.ftpmusic.app.R

/**
 * Media3 custom session commands + per-item command buttons for Play Next /
 * Add to Queue on Android Auto browse tracks (ADR-0137).
 *
 * Pair with [MediaLibrarySession.Builder.setCommandButtonsForMediaItems] and
 * [androidx.media3.common.MediaMetadata.Builder.setSupportedCommands] on
 * playable music items. Now-playing overflow stays reactions-only (ADR-0118).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object MediaSessionQueueCommands {
    const val ACTION_PLAY_NEXT = "com.lucasdss.ftpmusic.app.action.PLAY_NEXT"
    const val ACTION_ADD_TO_QUEUE = "com.lucasdss.ftpmusic.app.action.ADD_TO_QUEUE"

    /** Lazy — avoids Bundle.EMPTY init on JVM unit tests that only need action strings. */
    val PLAY_NEXT_COMMAND: SessionCommand by lazy { SessionCommand(ACTION_PLAY_NEXT, Bundle.EMPTY) }
    val ADD_TO_QUEUE_COMMAND: SessionCommand by lazy { SessionCommand(ACTION_ADD_TO_QUEUE, Bundle.EMPTY) }

    /** Custom action strings for [MediaMetadata.Builder.setSupportedCommands]. */
    val SUPPORTED_COMMANDS: ImmutableList<String> = ImmutableList.of(ACTION_PLAY_NEXT, ACTION_ADD_TO_QUEUE)

    fun buttons(): ImmutableList<CommandButton> {
        val playNext = CommandButton.Builder(CommandButton.ICON_PLAYLIST_ADD)
            .setDisplayName("Play Next")
            .setCustomIconResId(R.drawable.ic_playlist_add)
            .setSessionCommand(PLAY_NEXT_COMMAND)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        val addToQueue = CommandButton.Builder(CommandButton.ICON_PLUS)
            .setDisplayName("Add to Queue")
            .setCustomIconResId(R.drawable.ic_queue_add)
            .setSessionCommand(ADD_TO_QUEUE_COMMAND)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        return ImmutableList.of(playNext, addToQueue)
    }

    fun isQueueAction(action: String): Boolean = action == ACTION_PLAY_NEXT || action == ACTION_ADD_TO_QUEUE
}
