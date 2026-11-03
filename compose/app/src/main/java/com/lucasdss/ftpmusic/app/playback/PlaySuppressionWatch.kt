package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.Player

/**
 * Diagnostics-only detector for silent play after a play request (ADR-0148 V4 /
 * AudioHardening). Records [BtResumeEvents] `play_suppressed` — never posts UI.
 *
 * Arm after Path B `player.play()` or user-request `playWhenReady=true`. Evaluate
 * only when the player is [Player.STATE_READY] so buffering/network open is not
 * mistaken for OS mute. Uses an injected one-shot scheduler (not MediaService
 * positionPoller, which is cleared while not playing).
 */
class PlaySuppressionWatch(
    private val graceMs: Long = DEFAULT_GRACE_MS,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val schedule: (delayMs: Long, token: Any, runnable: Runnable) -> Unit,
    private val cancelScheduled: (token: Any) -> Unit,
    private val record: (reason: String, extras: String) -> Unit =
        { reason, extras -> BtResumeEvents.record(reason, extras) },
) {
    private val token = Any()
    private var armedSrc: String? = null
    private var sdkInt: Int = 0
    private var lastRecordedAt: Long = 0L
    private var onGrace: (() -> Unit)? = null

    /**
     * [onGraceFire] is invoked when the grace timer elapses so the host can
     * supply a fresh player snapshot into [evaluate].
     */
    fun arm(src: String, sdk: Int, onGraceFire: () -> Unit) {
        armedSrc = src.take(24)
        sdkInt = sdk
        onGrace = onGraceFire
        cancelScheduled(token)
        schedule(
            graceMs,
            token,
            Runnable {
                onGrace?.invoke()
            },
        )
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") reason: String = "cancel") {
        armedSrc = null
        onGrace = null
        cancelScheduled(token)
    }

    fun isArmed(): Boolean = armedSrc != null

    /**
     * Forward from [Player.Listener]. Cancels when audible or user paused;
     * ignores snapshots while not armed.
     */
    fun onPlayerSnapshot(playWhenReady: Boolean, isPlaying: Boolean, playbackState: Int, casting: Boolean) {
        if (armedSrc == null) return
        if (casting) {
            cancel("casting")
            return
        }
        if (!playWhenReady) {
            cancel("pwy_false")
            return
        }
        if (isPlaying) {
            cancel("playing")
        }
    }

    /**
     * Grace-timer / test entry. Records only when READY + playWhenReady + !isPlaying.
     * Buffering/idle cancels without recording (not AudioHardening).
     */
    fun evaluate(playWhenReady: Boolean, isPlaying: Boolean, playbackState: Int, casting: Boolean) {
        if (armedSrc == null) return
        onPlayerSnapshot(playWhenReady, isPlaying, playbackState, casting)
        if (armedSrc == null) return
        if (casting || !playWhenReady || isPlaying) return
        if (playbackState != Player.STATE_READY) {
            cancel("not_ready")
            return
        }
        maybeRecord()
    }

    private fun maybeRecord() {
        val src = armedSrc ?: return
        val now = nowMs()
        if (now - lastRecordedAt < debounceMs) {
            cancel("debounce")
            return
        }
        lastRecordedAt = now
        record("play_suppressed", "sdk=$sdkInt src=$src")
        armedSrc = null
        onGrace = null
        cancelScheduled(token)
    }

    companion object {
        const val DEFAULT_GRACE_MS = 5_000L
        const val DEFAULT_DEBOUNCE_MS = 10_000L
    }
}
