package com.lucasdss.ftpmusic.app.playback

/**
 * Convert QueueManager duration extras (milliseconds) + tracked position into
 * scrobble seconds. Returns null when [trackId] is blank (Cast EMPTY item).
 */
internal fun computeScrobbleListenedSeconds(
    trackId: String,
    durationMs: Long,
    lastTrackedPositionMs: Long,
): Pair<Int, Int>? {
    if (trackId.isBlank()) return null
    val durationSec = (durationMs / 1000L).toInt()
    var listenedSec = when {
        lastTrackedPositionMs > 0L -> (lastTrackedPositionMs / 1000L).toInt().coerceAtLeast(1)
        durationSec > 0 -> (durationSec * 0.6).toInt().coerceAtLeast(1)
        else -> 1
    }
    if (durationSec > 0) listenedSec = listenedSec.coerceAtMost(durationSec)
    return durationSec to listenedSec
}
