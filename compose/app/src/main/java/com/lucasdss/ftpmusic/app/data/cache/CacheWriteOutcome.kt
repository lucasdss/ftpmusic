package com.lucasdss.ftpmusic.app.data.cache

/**
 * Three-valued cache state used by the Source-error classifier (N11-2).
 *
 * A boolean is not enough: [NONE] means the bytes came from upstream (a 200
 * error body served as audio), [UNPLAYABLE] means a local span exists and is
 * poison, and [PLAYABLE] means the failure could still be remote because the
 * missing range was fetched from the server.
 */
enum class CacheSpanState {
    NONE,
    PLAYABLE,
    UNPLAYABLE,
}

/** Typed outcome of a cache import (N3) — the Boolean API collapsed five causes. */
enum class CacheWriteOutcome {
    OK,
    OK_ALREADY_CACHED,
    ZERO_LENGTH,
    NON_AUDIO,
    LOCKED,
    BUSY,
    IO,
    ;

    fun isSuccess(): Boolean = this == OK || this == OK_ALREADY_CACHED

    /** Stable, bounded field for diagnostics lines. */
    fun reason(): String = when (this) {
        OK -> "ok"
        OK_ALREADY_CACHED -> "already_cached"
        ZERO_LENGTH -> "zero_length"
        NON_AUDIO -> "non_audio"
        LOCKED -> "locked"
        BUSY -> "busy"
        IO -> "io"
    }
}
