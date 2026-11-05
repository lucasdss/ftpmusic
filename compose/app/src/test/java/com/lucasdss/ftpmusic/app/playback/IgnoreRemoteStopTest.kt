package com.lucasdss.ftpmusic.app.playback

import android.view.KeyEvent
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgnoreRemoteStopTest {

    @Test
    fun `player STOP remapped only when enabled`() {
        assertFalse(
            IgnoreRemoteStop.shouldRemapPlayerStop(
                ignoreEnabled = false,
                playerCommand = Player.COMMAND_STOP,
            ),
        )
        assertTrue(
            IgnoreRemoteStop.shouldRemapPlayerStop(
                ignoreEnabled = true,
                playerCommand = Player.COMMAND_STOP,
            ),
        )
        assertFalse(
            IgnoreRemoteStop.shouldRemapPlayerStop(
                ignoreEnabled = true,
                playerCommand = Player.COMMAND_PLAY_PAUSE,
            ),
        )
    }

    @Test
    fun `media STOP remapped only when enabled`() {
        assertFalse(
            IgnoreRemoteStop.shouldRemapMediaStop(
                ignoreEnabled = false,
                keyCode = KeyEvent.KEYCODE_MEDIA_STOP,
            ),
        )
        assertTrue(
            IgnoreRemoteStop.shouldRemapMediaStop(
                ignoreEnabled = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_STOP,
            ),
        )
        assertFalse(
            IgnoreRemoteStop.shouldRemapMediaStop(
                ignoreEnabled = true,
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY,
            ),
        )
    }
}
