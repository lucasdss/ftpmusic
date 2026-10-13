package com.lucasdss.ftpmusic.app.playback

/**
 * Where [PlaybackManager.addToQueue] / [PlaybackManager.addAllToQueue] insert.
 *
 * - [MANUAL_END] (default): end of PRIORITY — Apple Music / Spotify.
 * - [SESSION_END]: LATE band after full CONTEXT — YouTube Music-like.
 *
 * Play Next always uses PRIORITY front (ADR-0135).
 */
enum class AddToQueuePlacement(val key: String) {
    MANUAL_END("manual_end"),
    SESSION_END("session_end"),
    ;

    companion object {
        fun fromKey(key: String?): AddToQueuePlacement = entries.firstOrNull { it.key == key } ?: MANUAL_END
    }
}
