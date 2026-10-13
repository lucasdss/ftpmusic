package com.lucasdss.ftpmusic.app.playback

import java.time.Instant
import java.util.ArrayDeque

/**
 * Last-N Bluetooth resume outcomes for Share diagnostics (ADR-0119).
 * Process-scoped; Poweramp-style “last commands” without PII (no MAC, no titles).
 */
object BtResumeEvents {
    const val CAP = 20

    private val lock = Any()
    private val events = ArrayDeque<String>(CAP + 1)

    /**
     * Record a connect-path outcome. [reason] is a short token, e.g.
     * `allow`, `skip`, `selected_null_mac`, `Started`, `NotificationFallback`,
     * `notif_disabled`, `warm_a2dp`, `wiu`, `tap_play`, `autoplay`, `empty_queue`,
     * `offline_empty`.
     */
    fun record(reason: String, extras: String = "") {
        val safeReason = reason.replace('\n', ' ').take(48)
        val safeExtras = extras.replace('\n', ' ').take(80)
        val line = buildString {
            append(Instant.now())
            append(" | ")
            append(safeReason)
            if (safeExtras.isNotBlank()) {
                append(" | ")
                append(safeExtras)
            }
        }
        synchronized(lock) {
            events.addLast(line)
            while (events.size > CAP) events.removeFirst()
        }
    }

    fun clear() {
        synchronized(lock) { events.clear() }
    }

    fun size(): Int = synchronized(lock) { events.size }

    /** Newest-last list for tests / export. */
    fun recent(): List<String> = synchronized(lock) { events.toList() }

    /** Section appended under diagnostics snapshot header. */
    fun snapshotSection(): String {
        val body = recent()
        if (body.isEmpty()) return "btResumeEvents=(empty)\n"
        return buildString {
            appendLine("btResumeEvents=${body.size}")
            body.forEach { appendLine(it) }
        }
    }
}
