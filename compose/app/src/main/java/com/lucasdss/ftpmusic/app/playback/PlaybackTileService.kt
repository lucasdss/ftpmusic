package com.lucasdss.ftpmusic.app.playback

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/**
 * Quick Settings tile for media playback control.
 * Shows current track info and allows play/pause toggle directly from the notification shade.
 * Registers a Player.Listener for real-time updates while the tile is visible.
 */
@RequiresApi(Build.VERSION_CODES.N)
class PlaybackTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Player the tile listener is currently registered on. Kept separate from
     *  [PlayerHolder.player] because the active player can switch (local↔Cast)
     *  while the tile is listening — removal must target the REGISTERED player,
     *  otherwise the listener leaks on the old one and the tile freezes. */
    private var registeredPlayer: Player? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // qsTile access must happen on the main thread; media3 player events
            // arrive on the application thread but posting through the handler
            // keeps ordering deterministic.
            mainHandler.post { updateTileFromState(PlaybackState.fromPlayer(player)) }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val player = PlayerHolder.player
        if (player != null) {
            registerOn(player)
            updateTileFromState(PlaybackState.fromPlayer(player))
        } else {
            tryConnectAndGetState()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        registeredPlayer?.removeListener(playerListener)
        registeredPlayer = null
    }

    private fun registerOn(player: Player) {
        if (registeredPlayer === player) return
        registeredPlayer?.removeListener(playerListener)
        registeredPlayer = player
        player.addListener(playerListener)
    }

    override fun onClick() {
        super.onClick()
        val player = PlayerHolder.player
        if (player != null && player.mediaItemCount > 0) {
            // Compute the target state explicitly instead of re-reading
            // isPlaying right after the (async) toggle — avoids a momentary
            // wrong tile state on the transition frame. The listener update on
            // the next player event keeps the tile honest afterwards.
            val wasPlaying = player.isPlaying
            if (wasPlaying) player.pause() else player.play()
            qsTile?.state = if (wasPlaying) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            qsTile?.updateTile()
        } else {
            // No playable queue (null player, or mid-restore with 0 items):
            // play() would be a silent no-op — launch the app instead.
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                val pending = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(pending)
                } else {
                    // Lint's StartActivityAndCollapseDeprecated check does not
                    // honor @Suppress("DEPRECATION") — it needs its own id.
                    @SuppressLint("StartActivityAndCollapseDeprecated")
                    startActivityAndCollapse(intent)
                }
            }
        }
    }

    private fun tryConnectAndGetState() {
        try {
            val token = SessionToken(this, ComponentName(this, MediaService::class.java))
            val future = MediaController.Builder(this, token).buildAsync()
            // Dedicated executor, shut down after the one-shot resolution so
            // tile opens never leak threads. Non-blocking: resolving on the
            // main thread would risk an ANR if the service is slow to connect.
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            future.addListener({
                try {
                    val controller = future.get()
                    mainHandler.post { updateTileFromState(PlaybackState.fromPlayer(controller)) }
                    controller.release()
                } catch (_: Exception) {
                    mainHandler.post { updateTileFromState(PlaybackState()) }
                } finally {
                    executor.shutdown()
                }
            }, executor)
        } catch (_: Exception) {
            updateTileFromState(PlaybackState())
        }
    }

    private fun updateTileFromState(state: PlaybackState) {
        val tile = qsTile ?: return
        val (label, subtitle, tileState) = computeTileState(state)
        // Tile label/subtitle setters require API 29; on API 26-28 only the
        // state (icon tint) is available.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = label
            tile.subtitle = subtitle
        }
        tile.state = tileState
        tile.updateTile()
    }

    companion object {
        /** Pure function: maps playback state to tile display values. Testable. */
        fun computeTileState(state: PlaybackState): Triple<String, String, Int> {
            val title = state.title
            return if (!title.isNullOrBlank()) {
                Triple(
                    title,
                    state.artist ?: "",
                    if (state.isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
                )
            } else {
                Triple("FTP Music", "Tap to open", Tile.STATE_INACTIVE)
            }
        }
    }
}
