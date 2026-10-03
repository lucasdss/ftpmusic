package com.lucasdss.ftpmusic.app.playback

/**
 * Sticky `sessionWasResumed` transitions (EDGE-03 / ADR-0016).
 *
 * Pure so Cast reconnect queue-load policy stays unit-tested without
 * spinning up [MediaService]. A NEW [onSessionStarted] must never inherit a
 * prior resume flag — otherwise every later session skips queue (re)load.
 */
internal object CastSessionResumePolicy {
    fun onSessionStarted(): Boolean = false

    fun onSessionResumed(): Boolean = true

    fun onSessionEnded(): Boolean = false

    fun onManualDisconnect(): Boolean = false

    fun onDeviceIdChanged(previousId: String?, newId: String, current: Boolean): Boolean {
        if (previousId != null && previousId != newId) return false
        return current
    }
}
