package com.lucasdss.ftpmusic.app.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
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

        /** Reject / downsample gallery imports above this raw size. */
        const val MAX_IMPORT_BYTES = 8L * 1024L * 1024L

        /** Longest edge after downsample. */
        const val MAX_EDGE_PX = 2048
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
     * Caps raw bytes at [MAX_IMPORT_BYTES]; downsamples to [MAX_EDGE_PX] when
     * BitmapFactory can decode (Android runtime). Returns relative path or null.
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
        val safeId = sanitizeId(id)
        val relative = "${prefix}_$safeId.$ext"
        val dest = File(root, relative)
        val tmp = File(root, "$relative.tmp")
        try {
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) {
                        // Oversized: still try downsample path if we keep reading
                        // into a capped buffer — reject hard oversize without decode.
                        return@use null
                    }
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: return@withContext null
            if (bytes.isEmpty()) return@withContext null

            val toWrite = downsampleIfNeeded(bytes, ext) ?: return@withContext null
            if (toWrite.isEmpty()) return@withContext null

            FileOutputStream(tmp).use { it.write(toWrite) }
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

    /**
     * Rename a staged LOCAL cover (`prefix_oldId.ext`) to `prefix_newId.ext`.
     * Used when a new Daily Mix was imported under `new_<ts>` then saved with a
     * numeric id so [deleteForPrefix] can find the file later.
     *
     * @return new relative path, or null if source missing / invalid.
     */
    suspend fun rekey(prefix: String, fromRelative: String, toId: String): String? = withContext(Dispatchers.IO) {
        if (fromRelative.contains("..") || fromRelative.isBlank()) return@withContext null
        val src = File(root, fromRelative)
        if (!src.isFile || src.length() <= 0L) return@withContext null
        val ext = fromRelative.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext !in ALLOWED_EXT) return@withContext null
        val expectedPrefix = "${prefix}_"
        if (!fromRelative.startsWith(expectedPrefix)) return@withContext null
        val safeId = sanitizeId(toId)
        val relative = "$expectedPrefix$safeId.$ext"
        if (relative == fromRelative) return@withContext relative
        val dest = File(root, relative)
        try {
            if (dest.exists()) dest.delete()
            if (!src.renameTo(dest)) {
                src.copyTo(dest, overwrite = true)
                src.delete()
            }
            relative
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteRelative(relative: String?) = withContext(Dispatchers.IO) {
        val file = resolveLocalFile(relative) ?: return@withContext
        file.delete()
    }

    /** Remove every local file for a mix or playlist id prefix. */
    suspend fun deleteForPrefix(prefix: String, id: String) = withContext(Dispatchers.IO) {
        val safeId = sanitizeId(id)
        val stem = "${prefix}_$safeId."
        root.listFiles()?.forEach { f ->
            if (f.name.startsWith(stem)) f.delete()
        }
    }

    private fun sanitizeId(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /**
     * When BitmapFactory is real Android, downsample oversized images to
     * [MAX_EDGE_PX] and re-encode as JPEG. On plain-JVM stubs (unit tests
     * without Robolectric decode), pass through bytes that already fit the
     * size cap (caller already enforced [MAX_IMPORT_BYTES]).
     */
    internal fun downsampleIfNeeded(bytes: ByteArray, ext: String): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) {
                // Stub / undecodable — accept raw if within byte cap (already checked).
                return bytes
            }
            var sample = 1
            while (w / (sample * 2) >= MAX_EDGE_PX || h / (sample * 2) >= MAX_EDGE_PX) {
                sample *= 2
            }
            if (sample == 1 && w <= MAX_EDGE_PX && h <= MAX_EDGE_PX) {
                return bytes
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            try {
                val out = ByteArrayOutputStream()
                val format = if (ext == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 85
                if (!bitmap.compress(format, quality, out)) return null
                out.toByteArray()
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            bytes
        }
    }
}
