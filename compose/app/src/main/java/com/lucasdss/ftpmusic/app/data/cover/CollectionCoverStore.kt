package com.lucasdss.ftpmusic.app.data.cover

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persists gallery-picked collection covers under app filesDir.
 * Atomic write (temp + rename) so process death never leaves a trusted truncated file.
 */
@Singleton
class CollectionCoverStore @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        const val DIR = "collection_covers"
        private val ALLOWED_EXT = setOf("jpg", "jpeg", "png", "webp", "gif")
    }

    private val root: File
        get() = File(context.filesDir, DIR).also { it.mkdirs() }

    fun absolutePath(relative: String): String? {
        if (relative.isBlank() || relative.contains("..")) return null
        val file = File(root, relative)
        return if (file.isFile && file.length() > 0L) file.absolutePath else null
    }

    fun resolveLocalFile(relative: String?): File? {
        val rel = relative?.takeIf { it.isNotBlank() && !it.contains("..") } ?: return null
        val file = File(root, rel)
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    /**
     * Copy [uri] into `collection_covers/{prefix}_{id}.{ext}`.
     * @return relative path stored in DB, or null on failure.
     */
    suspend fun importFromUri(uri: Uri, prefix: String, id: String): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            else -> "jpg"
        }.lowercase()
        if (ext !in ALLOWED_EXT) return@withContext null
        val safeId = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val relative = "${prefix}_$safeId.$ext"
        val dest = File(root, relative)
        val tmp = File(root, "$relative.tmp")
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            } ?: return@withContext null
            if (tmp.length() <= 0L) {
                tmp.delete()
                return@withContext null
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            relative
        } catch (_: Exception) {
            tmp.delete()
            null
        }
    }

    suspend fun deleteRelative(relative: String?) = withContext(Dispatchers.IO) {
        val file = resolveLocalFile(relative) ?: return@withContext
        file.delete()
    }

    /** Remove every local file for a mix or playlist id prefix. */
    suspend fun deleteForPrefix(prefix: String, id: String) = withContext(Dispatchers.IO) {
        val safeId = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val stem = "${prefix}_$safeId."
        root.listFiles()?.forEach { f ->
            if (f.name.startsWith(stem)) f.delete()
        }
    }
}
