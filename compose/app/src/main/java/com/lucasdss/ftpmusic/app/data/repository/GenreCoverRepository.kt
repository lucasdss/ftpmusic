package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverKind
import com.lucasdss.ftpmusic.app.data.cover.UserCoverLibrary
import com.lucasdss.ftpmusic.app.data.db.GenreCoverDao
import com.lucasdss.ftpmusic.app.data.db.GenreCoverEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Per-genre fixed covers keyed by genre name (ADR 0151). */
@Singleton
class GenreCoverRepository @Inject constructor(
    private val genreCoverDao: GenreCoverDao,
    private val userCoverLibrary: UserCoverLibrary,
) {
    suspend fun get(genreName: String): GenreCoverEntity? = withContext(Dispatchers.IO) {
        genreCoverDao.get(genreName)
    }

    suspend fun getAll(): List<GenreCoverEntity> = withContext(Dispatchers.IO) {
        genreCoverDao.getAll()
    }

    suspend fun setFixedCover(genreName: String, kind: String, value: String) =
        withContext(Dispatchers.IO) {
            if (!CollectionCoverKind.isValid(kind) || value.isBlank() || genreName.isBlank()) return@withContext
            genreCoverDao.upsert(
                GenreCoverEntity(
                    genreName = genreName,
                    fixedCoverKind = kind,
                    fixedCoverValue = value,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }

    suspend fun clearFixedCover(genreName: String) = withContext(Dispatchers.IO) {
        genreCoverDao.clear(genreName)
    }

    suspend fun setFixedCoverFromCroppedBitmap(
        genreName: String,
        bitmap: android.graphics.Bitmap,
    ): String? = withContext(Dispatchers.IO) {
        val entry = userCoverLibrary.importCroppedBitmap(bitmap) ?: return@withContext null
        setFixedCover(genreName, CollectionCoverKind.LOCAL, entry.relativePath)
        entry.relativePath
    }
}
