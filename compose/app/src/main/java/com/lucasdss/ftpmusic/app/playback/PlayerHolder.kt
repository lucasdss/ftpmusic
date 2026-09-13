package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.Player

/**
 * Holds a reference to the ExoPlayer so UI-layer code (QueueManager,
 * PlaybackManager) can control playback without passing the Player
 * through every method call.
 *
 * Set by MediaService.onCreate(). Cleared in onDestroy().
 */
object PlayerHolder {
    @Volatile var player: Player? = null

    /** ExoPlayer reference — used for full queue access during Cast */
    @Volatile var exoPlayer: Player? = null

    @Volatile
    var isCasting: Boolean = false

    @Volatile
    var castDeviceName: String? = null

    @Volatile
    var castVolume: Float = 0f

    /** Last known Cast device volume read from CastSession (0.0–1.0). */
    @Volatile
    var castDeviceVolume: Float = 0.5f

    /** Last device volume sent via Cast SDK — not yet confirmed. */
    @Volatile
    var pendingCastVolume: Float? = null

    /** Timestamp (ms) when pendingCastVolume was last written. Used by the
     *  position poller to expire stale pending values (3s) when the Cast
     *  device never confirms (network drop, receiver off). */
    @Volatile
    var pendingCastVolumeTimestamp: Long = 0L

    /** Whether the Cast receiver device is muted. Updated from onDeviceVolumeChanged. */
    @Volatile
    var castDeviceMuted: Boolean = false

    /** Seed both confirmed-volume fields from a CastSession.volume (0.0–1.0).
     *  Ignores null / negative (session not ready). */
    fun seedFromSessionVolume(vol: Double?) {
        if (vol != null && vol >= 0.0) {
            castVolume = vol.toFloat()
            castDeviceVolume = vol.toFloat()
        }
    }

    /** Sleep timer end timestamp (ms). Persisted across process death via playback_state.
     *  Set by PlaybackViewModel.startSleepTimer(), read by MediaService for persistence. */
    @Volatile
    var sleepTimerEndMs: Long = 0L

    /** True when the last savePlayQueue API call failed (server unreachable, etc.).
     *  Reset to false on next successful save. Used by PlaybackState.isQueueSynced. */
    @Volatile
    var queueSaveFailed: Boolean = false
}
