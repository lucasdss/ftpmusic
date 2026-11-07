package com.lucasdss.ftpmusic.app.data.cover

import android.content.Context
import android.graphics.Bitmap
import com.lucasdss.ftpmusic.app.data.db.CustomMixDao
import com.lucasdss.ftpmusic.app.data.db.GenreCoverDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.UserCoverImageDao
import com.lucasdss.ftpmusic.app.data.db.UserCoverImageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Retained personal cover library (ADR 0151).
 * Files live under `collection_covers/library/{id}.jpg`. Switching/clearing a
 * collection cover only updates entity pointers — never deletes library rows
 * unless [delete] / [deleteAndClearPointers] is called explicitly.
 */
@Singleton
class UserCoverLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coverStore: CollectionCoverStore,
    private val dao: UserCoverImageDao,
    private val customMixDao: CustomMixDao,
    private val playlistDao: PlaylistDao,
    private val genreCoverDao: GenreCoverDao,
) {
    companion object {
        const val LIBRARY_PREFIX = "library"
        const val LIBRARY_DIR = "library"
    }

    data class Entry(
        val id: String,
        val relativePath: String,
        val absolutePath: String?,
        val createdAtMs: Long,
        val lastUsedAtMs: Long,
    )

    suspend fun list(): List<Entry> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toEntry() }
    }

    suspend fun get(id: String): Entry? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toEntry()
    }

    /**
     * Persist a cropped bitmap into the library and return the new entry.
     * Recycles [bitmap].
     */
    suspend fun importCroppedBitmap(bitmap: Bitmap): Entry? = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().replace("-", "")
        try {
            val relative = coverStore.importCroppedBitmap(bitmap, LIBRARY_PREFIX, id)
                ?: return@withContext null
            // importCroppedBitmap writes `{prefix}_{id}.jpg` at root; move under library/.
            val libraryRelative = relocateIntoLibraryDir(relative, id) ?: return@withContext null
            val now = System.currentTimeMillis()
            val row = UserCoverImageEntity(
                id = id,
                relativePath = libraryRelative,
                createdAtMs = now,
                lastUsedAtMs = now,
            )
            dao.upsert(row)
            row.toEntry()
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Adopt a legacy entity-scoped file (`mix_3.jpg` / `pl_xyz.jpg`) into the library
     * without deleting the original until copy succeeds. Returns library relative path.
     */
    suspend fun adoptLegacyRelative(legacyRelative: String): String? = withContext(Dispatchers.IO) {
        if (legacyRelative.startsWith("$LIBRARY_DIR/")) {
            val existing = dao.getByRelativePath(legacyRelative)
            if (existing != null) {
                dao.touch(existing.id)
                return@withContext legacyRelative
            }
        }
        val src = coverStore.resolveLocalFile(legacyRelative) ?: return@withContext null
        val id = UUID.randomUUID().toString().replace("-", "")
        val libraryRelative = "$LIBRARY_DIR/$id.jpg"
        val dest = File(File(context.filesDir, CollectionCoverStore.DIR), libraryRelative)
        dest.parentFile?.mkdirs()
        try {
            src.copyTo(dest, overwrite = true)
            if (!dest.isFile || dest.length() <= 0L) {
                dest.delete()
                return@withContext null
            }
            val now = System.currentTimeMillis()
            dao.upsert(
                UserCoverImageEntity(
                    id = id,
                    relativePath = libraryRelative,
                    createdAtMs = now,
                    lastUsedAtMs = now,
                ),
            )
            libraryRelative
        } catch (_: Exception) {
            dest.delete()
            null
        }
    }

    suspend fun touchByRelativePath(relativePath: String) = withContext(Dispatchers.IO) {
        val row = dao.getByRelativePath(relativePath) ?: return@withContext
        dao.touch(row.id)
    }

    /** How many collections currently pin this library relative path. */
    suspend fun pointerCount(relativePath: String): Int = withContext(Dispatchers.IO) {
        customMixDao.countPointersTo(relativePath) +
            playlistDao.countPointersTo(relativePath) +
            genreCoverDao.countPointersTo(relativePath)
    }

    /**
     * Explicit remove from Saved — deletes file + row.
     * Callers must null any fixed-cover pointers that reference this path first,
     * or use [deleteAndClearPointers].
     */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val row = dao.getById(id) ?: return@withContext
        coverStore.deleteRelative(row.relativePath, allowLibraryDelete = true)
        dao.deleteById(id)
    }

    /**
     * Null LOCAL pointers across mixes/playlists/genres, then delete the Saved entry.
     * @return number of collections that were unpinned.
     */
    suspend fun deleteAndClearPointers(id: String): Int = withContext(Dispatchers.IO) {
        val row = dao.getById(id) ?: return@withContext 0
        val path = row.relativePath
        val count = customMixDao.countPointersTo(path) +
            playlistDao.countPointersTo(path) +
            genreCoverDao.countPointersTo(path)
        customMixDao.clearPointersTo(path)
        playlistDao.clearPointersTo(path)
        genreCoverDao.clearPointersTo(path)
        coverStore.deleteRelative(path, allowLibraryDelete = true)
        dao.deleteById(id)
        count
    }

    private fun relocateIntoLibraryDir(rootRelative: String, id: String): String? {
        val root = File(context.filesDir, CollectionCoverStore.DIR)
        val src = File(root, rootRelative)
        if (!src.isFile || src.length() <= 0L) return null
        val libraryRelative = "$LIBRARY_DIR/$id.jpg"
        val dest = File(root, libraryRelative)
        dest.parentFile?.mkdirs()
        return try {
            if (dest.exists()) dest.delete()
            if (!src.renameTo(dest)) {
                src.copyTo(dest, overwrite = true)
                src.delete()
            }
            if (dest.isFile && dest.length() > 0L) libraryRelative else null
        } catch (_: Exception) {
            null
        }
    }

    private fun UserCoverImageEntity.toEntry(): Entry = Entry(
        id = id,
        relativePath = relativePath,
        absolutePath = coverStore.absolutePath(relativePath),
        createdAtMs = createdAtMs,
        lastUsedAtMs = lastUsedAtMs,
    )
}
