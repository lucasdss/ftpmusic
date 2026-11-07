package com.lucasdss.ftpmusic.app.data.cover

import com.lucasdss.ftpmusic.app.data.db.ArtPaletteCacheDao
import com.lucasdss.ftpmusic.app.data.db.ArtPaletteCacheEntity
import com.lucasdss.ftpmusic.app.ui.art.CoverScheme
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed art seed persistence for process-death / cold start.
 * Memory LRU in [ArtSeedExtractor] is still the hot path.
 */
@Singleton
class ArtPaletteStore @Inject constructor(
    private val dao: ArtPaletteCacheDao,
) {
    suspend fun get(serverScope: String, artKey: String, sourceVersion: String): Int? {
        val row = dao.get(serverScope, artKey) ?: return null
        if (row.sourceVersion != sourceVersion) return null
        if (row.algorithmVersion != CoverScheme.ALGORITHM_VERSION) return null
        return row.argb
    }

    suspend fun put(serverScope: String, artKey: String, sourceVersion: String, argb: Int?) {
        dao.upsert(
            ArtPaletteCacheEntity(
                serverScope = serverScope,
                artKey = artKey,
                sourceVersion = sourceVersion,
                algorithmVersion = CoverScheme.ALGORITHM_VERSION,
                argb = argb,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun invalidate(serverScope: String, artKey: String) {
        dao.delete(serverScope, artKey)
        ArtSeedExtractor.clearMemory()
    }

    suspend fun clearScope(serverScope: String) {
        dao.deleteScope(serverScope)
    }

    suspend fun clearAll() {
        dao.deleteAll()
        ArtSeedExtractor.clearMemory()
    }
}
