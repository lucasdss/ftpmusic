package com.lucasdss.ftpmusic.app.data.search

import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheEntity
import com.lucasdss.ftpmusic.app.data.security.SecureStorage

/**
 * Phase-6: default-on Search lyrics when cache already has rows and preference unset.
 * Explicit `"false"` stays off.
 */
object SearchLyricsDefaults {

    /**
     * @return true if this call enabled lyrics and scheduled a rebuild.
     */
    suspend fun maybeEnable(
        storage: SecureStorage,
        lyricsCacheDao: LyricsCacheDao,
        onEnabled: () -> Unit = {},
    ): Boolean {
        if (storage.get(SecureStorage.KEY_SEARCH_LYRICS) != null) return false
        val count = try {
            lyricsCacheDao.count()
        } catch (_: Exception) {
            0
        }
        if (count <= 0) return false
        storage.put(SecureStorage.KEY_SEARCH_LYRICS, "true")
        onEnabled()
        return true
    }

    /** First line containing query (folded), capped. */
    fun snippet(entity: LyricsCacheEntity?, query: String, maxLen: Int = 80): String? {
        if (entity == null || query.isBlank()) return null
        val q = SearchQueryNormalizer.fold(query)
        if (q.isEmpty()) return null
        val text = entity.unstructuredText
            ?: entity.syncedLinesJson
            ?: return null
        val line = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && SearchQueryNormalizer.fold(it).contains(q) }
            ?: text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: return null
        return if (line.length <= maxLen) line else line.take(maxLen - 1).trimEnd() + "…"
    }
}
