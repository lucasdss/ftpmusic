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

    /**
     * Last player error for Now Playing banner (ADR-0094/0095).
     * Sticky across auto-skip until dismiss, expiry, or successful READY after sticky window.
     */
    @Volatile
    var lastPlaybackError: String? = null

    /** Wall-clock ms until which [lastPlaybackError] must not auto-clear (ADR-0095). */
    @Volatile
    var playbackErrorStickyUntilMs: Long = 0L

    /** True while MediaService is mid auto-skip recovery (suppress clear-on-transition). */
    @Volatile
    var playbackErrorAutoSkipInFlight: Boolean = false

    /**
     * Optional scheduler for sticky auto-clear (wired by MediaService to main Handler).
     * Unit tests may inject a no-op or immediate runner.
     */
    @Volatile
    var stickyClearScheduler: ((delayMs: Long, action: () -> Unit) -> Unit)? = null

    /** Invoked after sticky error is cleared (expiry or dismiss) so providers can republish. */
    @Volatile
    var onPlaybackErrorCleared: (() -> Unit)? = null

    private var stickyClearGeneration: Int = 0

    fun setPlaybackError(message: String?, stickyMs: Long = PLAYBACK_ERROR_STICKY_MS) {
        stickyClearGeneration++
        val generation = stickyClearGeneration
        lastPlaybackError = message
        playbackErrorStickyUntilMs =
            if (message.isNullOrBlank()) 0L else System.currentTimeMillis() + stickyMs
        if (!message.isNullOrBlank() && stickyMs > 0L) {
            stickyClearScheduler?.invoke(stickyMs) {
                if (generation == stickyClearGeneration) {
                    if (clearPlaybackErrorIfSettled()) {
                        onPlaybackErrorCleared?.invoke()
                    }
                }
            }
        }
    }

    fun dismissPlaybackError() {
        stickyClearGeneration++
        lastPlaybackError = null
        playbackErrorStickyUntilMs = 0L
        playbackErrorAutoSkipInFlight = false
        onPlaybackErrorCleared?.invoke()
    }

    /** Clear only when sticky window elapsed and not mid auto-skip. */
    fun clearPlaybackErrorIfSettled(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (lastPlaybackError == null) return false
        if (playbackErrorAutoSkipInFlight) return false
        if (nowMs < playbackErrorStickyUntilMs) return false
        lastPlaybackError = null
        playbackErrorStickyUntilMs = 0L
        return true
    }

    const val PLAYBACK_ERROR_STICKY_MS = 8_000L

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

    /** Sleep timer end timestamp (ms). Persisted across process death via queue_state.
     *  Set by PlaybackViewModel.startSleepTimer(), read by MediaService for persistence. */
    @Volatile
    var sleepTimerEndMs: Long = 0L

    /** True when the last savePlayQueue API call failed (server unreachable, etc.).
     *  Reset to false on next successful save. Used by PlaybackState.isQueueSynced. */
    @Volatile
    var queueSaveFailed: Boolean = false
}
