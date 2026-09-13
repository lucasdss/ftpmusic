package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.pow

/**
 * Applies ReplayGain from track metadata.
 * Currently supports track gain with album gain fallback.
 * Future: R128_TRACK_GAIN/R128_ALBUM_GAIN from OpenSubsonic spec
 * Planned: auto/album/track modes, consecutive-track detection for gapless album playback.
 */
object ReplayGainUtil {
    fun applyReplayGain(player: Player?, trackGain: Float?, albumGain: Float?) {
        // Prefer track gain, fall back to album gain
        val gain = trackGain?.takeIf { it != 0f } ?: albumGain?.takeIf { it != 0f } ?: return
        val clamped = gain.coerceIn(-20f, 20f)
        val multiplier = 10.0.pow(clamped.toDouble() / 20.0).toFloat()
        (player as? ExoPlayer)?.let { it.volume = multiplier }
    }
}
