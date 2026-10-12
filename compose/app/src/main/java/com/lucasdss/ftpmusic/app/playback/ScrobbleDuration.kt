package com.lucasdss.ftpmusic.app.playback

/**
 * Last.fm-aligned scrobble eligibility + honest listen seconds (ADR-0128).
 *
 * Rules ([Last.fm scrobbling](https://www.last.fm/api/scrobbling)):
 * - Track must be longer than 30 seconds.
 * - Accumulated heard playtime ≥ half duration OR ≥ 4 minutes (whichever first).
 */
internal const val LASTFM_MIN_TRACK_MS = 30_000L
internal const val LASTFM_MAX_REQUIRED_HEARD_MS = 240_000L // 4 minutes

/** Required heard ms to scrobble, or null if track is too short. */
internal fun scrobbleThresholdMs(durationMs: Long): Long? {
    if (durationMs <= LASTFM_MIN_TRACK_MS) return null
    return minOf(durationMs / 2L, LASTFM_MAX_REQUIRED_HEARD_MS)
}

internal fun hasMetScrobbleThreshold(durationMs: Long, accumulatedHeardMs: Long): Boolean {
    val threshold = scrobbleThresholdMs(durationMs) ?: return false
    return accumulatedHeardMs >= threshold
}

/**
 * Convert duration extras + accumulated heard time into scrobble seconds.
 * Returns null when [trackId] is blank (Cast EMPTY item).
 */
internal fun computeScrobbleListenedSeconds(
    trackId: String,
    durationMs: Long,
    accumulatedHeardMs: Long,
): Pair<Int, Int>? {
    if (trackId.isBlank()) return null
    val durationSec = (durationMs / 1000L).toInt()
    var listenedSec = when {
        accumulatedHeardMs > 0L -> (accumulatedHeardMs / 1000L).toInt().coerceAtLeast(1)
        durationSec > 0 -> (durationSec / 2).coerceAtLeast(1)
        else -> 1
    }
    if (durationSec > 0) listenedSec = listenedSec.coerceAtMost(durationSec)
    return durationSec to listenedSec
}
