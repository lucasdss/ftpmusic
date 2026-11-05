package com.lucasdss.ftpmusic.app.playback

import android.view.KeyEvent
import androidx.media3.common.Player

/**
 * ADR-0154 / Symfonium-class: when enabled, external Stop must not wipe the queue —
 * treat as Pause so car reconnect can autoplay the last seat.
 */
object IgnoreRemoteStop {
    /** True when a Media3 player command should be remapped Stop → Pause. */
    fun shouldRemapPlayerStop(ignoreEnabled: Boolean, playerCommand: Int): Boolean =
        ignoreEnabled && playerCommand == Player.COMMAND_STOP

    /** True when a media-button keycode should be remapped Stop → Pause. */
    fun shouldRemapMediaStop(ignoreEnabled: Boolean, keyCode: Int): Boolean =
        ignoreEnabled && keyCode == KeyEvent.KEYCODE_MEDIA_STOP
}
