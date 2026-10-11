package com.lucasdss.ftpmusic.app.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.Player

/**
 * Handles MEDIA_ACTION broadcasts from notification action buttons.
 *
 * When the notification's PendingIntent fires (play/pause, skip, custom like/dislike),
 * this receiver dispatches the command to the active [Player] or
 * [ReactionCommandBridge] for reactions.
 * Lock screen / Quick Settings transport goes through the MediaSession token
 * path and doesn't use this receiver; custom session commands use
 * [MediaSessionCallback.onCustomCommand].
 */
class MediaActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ftpmusic-notif"
        const val ACTION = "com.lucasdss.ftpmusic.app.MEDIA_ACTION"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_CUSTOM_ACTION = "custom_action"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val customAction = intent.getStringExtra(EXTRA_CUSTOM_ACTION)
        if (customAction != null) {
            Log.d(TAG, "MediaActionReceiver: custom action=$customAction")
            ReactionCommandBridge.dispatch(customAction)
            return
        }

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

/**
 * Process-local bridge so [MediaActionReceiver] can reach the service-scoped
 * reaction handler without Hilt in a manifest receiver.
 */
object ReactionCommandBridge {
    @Volatile
    var handler: ((String) -> Unit)? = null

    fun dispatch(action: String) {
        handler?.invoke(action) ?: Log.w("ftpmusic-notif", "ReactionCommandBridge: no handler for $action")
    }
}
