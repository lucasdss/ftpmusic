package com.lucasdss.ftpmusic.app.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.Player
import com.google.android.gms.cast.framework.CastContext
import com.lucasdss.ftpmusic.app.ui.player.CastButtonState

/**
 * Handles MEDIA_ACTION broadcasts from notification action buttons.
 *
 * When the notification's PendingIntent fires (play/pause, skip, etc.),
 * this receiver dispatches the command to the active [Player].
 * Lock screen / Quick Settings controls go through the MediaSession token
 * path and don't use this receiver.
 */
class MediaActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ftpmusic-notif"
        const val ACTION = "com.lucasdss.ftpmusic.app.MEDIA_ACTION"
        const val EXTRA_COMMAND = "command"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val commandCode = intent.getLongExtra(EXTRA_COMMAND, 0)
        val player = PlayerHolder.player
        if (player == null) {
            Log.w(TAG, "MediaActionReceiver: no player available, dropping command=$commandCode")
            return
        }

        Log.d(TAG, "MediaActionReceiver: dispatching command=$commandCode")
        dispatchCommand(player, commandCode)
    }

    /** Visible for testing: dispatches a command to the given player. */
    internal fun dispatchCommand(player: Player, commandCode: Long) {
        when (commandCode) {
            Player.COMMAND_PLAY_PAUSE.toLong() -> {
                if (player.isPlaying) player.pause() else player.play()
            }

            Player.COMMAND_SEEK_TO_NEXT.toLong() -> {
                player.seekToNextMediaItem()
            }

            Player.COMMAND_SEEK_TO_PREVIOUS.toLong() -> {
                player.seekToPreviousMediaItem()
            }

            else -> {
                Log.w(TAG, "MediaActionReceiver: unknown command=$commandCode")
            }
        }
    }
}
