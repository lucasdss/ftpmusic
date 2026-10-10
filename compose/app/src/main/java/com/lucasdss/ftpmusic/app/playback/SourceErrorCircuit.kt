package com.lucasdss.ftpmusic.app.playback

/**
 * Cross-track Source-error circuit (ADR-0111).
 * Per-track [playerErrorCount] resets on every media transition, so a systemic
 * stream failure previously SKIP_NEXT + removeCached through the whole queue.
 */
internal data class SourceErrorCircuitState(
    val consecutiveErrors: Int = 0,
    val windowStartMs: Long = 0L,
)

internal const val SOURCE_ERROR_CIRCUIT_THRESHOLD = 3
internal const val SOURCE_ERROR_CIRCUIT_WINDOW_MS = 15_000L
internal const val SOURCE_ERROR_CIRCUIT_SUCCESS_PLAY_MS = 2_000L
internal const val SCROBBLE_STORM_MIN_LISTEN_MS = 5_000L

/**
 * Record a Source/playback error. Returns updated state and whether the circuit
 * should trip ([CIRCUIT_STOP]) — caller must not removeCached further tracks.
 */
internal fun SourceErrorCircuitState.onSourceError(
    nowMs: Long,
    windowMs: Long = SOURCE_ERROR_CIRCUIT_WINDOW_MS,
    threshold: Int = SOURCE_ERROR_CIRCUIT_THRESHOLD,
): Pair<SourceErrorCircuitState, Boolean> {
    val inWindow = consecutiveErrors > 0 && nowMs - windowStartMs <= windowMs
    val next = if (inWindow) {
        copy(consecutiveErrors = consecutiveErrors + 1)
    } else {
        SourceErrorCircuitState(consecutiveErrors = 1, windowStartMs = nowMs)
    }
    return next to (next.consecutiveErrors >= threshold)
}

/** Reset after sustained successful playback (≥ [SOURCE_ERROR_CIRCUIT_SUCCESS_PLAY_MS]). */
internal fun SourceErrorCircuitState.onSuccessfulPlay(): SourceErrorCircuitState =
    SourceErrorCircuitState()

/** Reset on explicit user seek / queue replace. */
internal fun SourceErrorCircuitState.onUserSeek(): SourceErrorCircuitState =
    SourceErrorCircuitState()

/**
 * Ghost Cast AUTO transitions leave [lastTrackedPositionMs] near zero.
 * Skip scrobble when listen under [SCROBBLE_STORM_MIN_LISTEN_MS].
 */
internal fun shouldScrobbleAfterListen(lastTrackedPositionMs: Long): Boolean =
    lastTrackedPositionMs >= SCROBBLE_STORM_MIN_LISTEN_MS
