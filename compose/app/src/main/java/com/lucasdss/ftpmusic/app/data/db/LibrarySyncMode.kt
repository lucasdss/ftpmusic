package com.lucasdss.ftpmusic.app.data.db

/** Album catalog sync strategy (ADR-0045). */
enum class LibrarySyncMode {
    /** Newest albums upsert + incremental tracks. */
    DELTA,

    /** Full alphabetical replace + incremental or forced tracks. */
    FULL,
}
