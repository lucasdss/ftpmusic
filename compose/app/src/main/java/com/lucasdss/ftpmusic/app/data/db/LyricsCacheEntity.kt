package com.lucasdss.ftpmusic.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached lyrics for a track, stored in Room for offline access. */
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val trackId: String,
    val artist: String?,
    val title: String?,
    /** Full API response JSON — preserved for re-parsing when parsing logic changes. */
    val rawJson: String? = null,
    /** Line-by-line synced lyrics (JSON-encoded LyricLine list). null = unstructured. */
    val syncedLinesJson: String? = null,
    /** Unstructured lyrics text. null = structured (synced). */
    val unstructuredText: String? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
    /** Schema version of the cached data. Bumped when parsing logic changes.
     *  Old caches (version 0-1) are invalidated and re-fetched. */
    val cacheVersion: Int = 2,
) {
    companion object {
        /** Current cache schema version. Cache entries with lower version are discarded. */
        const val CURRENT_CACHE_VERSION = 2
    }
}
