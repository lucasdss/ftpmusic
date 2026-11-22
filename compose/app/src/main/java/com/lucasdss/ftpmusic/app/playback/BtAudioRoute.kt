package com.lucasdss.ftpmusic.app.playback

import android.media.AudioManager
import android.os.Build

/**
 * Media-output helpers for BT Path B rebind (Tesla A2DP).
 *
 * [isA2dpOutputActive] answers “is music already routing to Bluetooth A2DP?”
 * — not merely “is an A2DP device present”. Warm connect adds the device
 * before the OS may have switched the media route; when [isPlaying] but
 * this returns false, Path B must reassert [androidx.media3.common.Player.play]
 * (`autoplay_rebind`).
 *
 * Device presence via [AudioManager.getDevices] is intentionally ignored:
 * the warm A2DP callback always implies presence, which would false-positive
 * `skip_playing` and leave Tesla silent.
 */
object BtAudioRoute {
    fun isA2dpOutputActive(audioManager: AudioManager): Boolean {
        @Suppress("DEPRECATION")
        if (audioManager.isBluetoothA2dpOn) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val comm = audioManager.communicationDevice
            if (comm != null && BtWarmAudioDeviceMonitor.isBluetoothA2dp(comm)) return true
        }
        return false
    }
}
