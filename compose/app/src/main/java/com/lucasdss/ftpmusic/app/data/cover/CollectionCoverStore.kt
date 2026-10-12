package com.lucasdss.ftpmusic.app.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persists gallery-picked collection covers under app filesDir.
 * Atomic write (temp + rename) so process death never leaves a trusted truncated file.
 *
 * Device UX (ADR 0125): crop UI → [importCroppedBitmap] → JPEG ≤[MAX_EDGE_PX] / ≤[MAX_OUTPUT_BYTES].
 */
@Singleton
class CollectionCoverStore @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        const val DIR = "collection_covers"

        /** Reject / budget raw URI reads above this size. */
        const val MAX_IMPORT_BYTES = 8L * 1024L * 1024L

        /** Longest edge after crop export (ADR 0125). */
        const val MAX_EDGE_PX = 1024

        /** Hard cap on encoded JPEG size. */
        const val MAX_OUTPUT_BYTES = 512L * 1024L

        /** Subsampled decode ceiling for crop preview. */
        const val CROP_PREVIEW_MAX_EDGE = 2048

        private val ALLOWED_EXT = setOf("jpg", "jpeg", "png", "webp", "gif")
        private val JPEG_QUALITIES = intArrayOf(85, 75, 65)
        private const val FALLBACK_EDGE_PX = 768
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
     * Decode [uri] for crop preview: EXIF-aware, subsampled to [CROP_PREVIEW_MAX_EDGE].
     * Returns null on oversize stream, corrupt, or undecodable.
     */
    suspend fun decodeForCrop(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = readUriBytesCapped(uri) ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null
        decodeSampledOriented(bytes, CROP_PREVIEW_MAX_EDGE)
    }

    /**
     * Persist a user-cropped square (or any) bitmap as JPEG under
     * `collection_covers/{prefix}_{id}.jpg`. Scales to [MAX_EDGE_PX], encodes
     * with quality ladder to fit [MAX_OUTPUT_BYTES]. Caller may recycle [bitmap].
     */
    suspend fun importCroppedBitmap(bitmap: Bitmap, prefix: String, id: String): String? = withContext(Dispatchers.IO) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
        val safeId = sanitizeId(id)
        val relative = "${prefix}_$safeId.jpg"
        val dest = File(root, relative)
        val tmp = File(root, "$relative.tmp")
        try {
            val scaled = scaleToMaxEdge(bitmap, MAX_EDGE_PX)
            try {
                val jpeg = encodeJpegWithinBudget(scaled) ?: return@withContext null
                if (jpeg.isEmpty()) return@withContext null
                FileOutputStream(tmp).use { it.write(jpeg) }
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
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        } catch (_: Exception) {
            tmp.delete()
            null
        }
    }

    /**
     * Legacy direct URI import (tests / fallback). Caps raw bytes at
     * [MAX_IMPORT_BYTES]; always writes JPEG with [MAX_EDGE_PX] / budget.
     */
    suspend fun importFromUri(uri: Uri, prefix: String, id: String): String? = withContext(Dispatchers.IO) {
        val bytes = readUriBytesCapped(uri) ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null
        val bitmap = decodeSampledOriented(bytes, MAX_EDGE_PX) ?: return@withContext null
        try {
            importCroppedBitmap(bitmap, prefix, id)
        } finally {
            bitmap.recycle()
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

    private fun readUriBytesCapped(uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        return try {
            resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) return@use null
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Subsample decode + EXIF rotate/flip. Returns ARGB bitmap or null.
     */
    internal fun decodeSampledOriented(bytes: ByteArray, maxEdge: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (w / (sample * 2) >= maxEdge || h / (sample * 2) >= maxEdge) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            applyExifOrientation(raw, bytes)
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)

            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)

            else -> return bitmap
        }
        return try {
            val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (oriented !== bitmap) bitmap.recycle()
            oriented
        } catch (_: Exception) {
            bitmap
        }
    }

    internal fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = max(w, h)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest
        val nw = (w * scale).roundToInt().coerceAtLeast(1)
        val nh = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }

    /**
     * Flatten alpha on black, JPEG quality ladder, optional downscale to
     * [FALLBACK_EDGE_PX] if still over [MAX_OUTPUT_BYTES].
     * Never recycles [bitmap]; only recycles intermediates it allocates.
     */
    internal fun encodeJpegWithinBudget(bitmap: Bitmap): ByteArray? {
        val flat = flattenToRgb(bitmap)
        val ownedFlat = flat !== bitmap
        var working: Bitmap = flat
        var ownedWorking = ownedFlat
        try {
            for (q in JPEG_QUALITIES) {
                val out = compressJpeg(working, q) ?: continue
                if (out.size <= MAX_OUTPUT_BYTES) return out
            }
            val smaller = scaleToMaxEdge(working, FALLBACK_EDGE_PX)
            if (smaller !== working) {
                if (ownedWorking) working.recycle()
                working = smaller
                ownedWorking = true
            }
            for (q in JPEG_QUALITIES) {
                val out = compressJpeg(working, q) ?: continue
                if (out.size <= MAX_OUTPUT_BYTES) return out
            }
            // Last resort: return lowest-quality encode even if slightly over budget.
            return compressJpeg(working, 65)
        } finally {
            if (ownedWorking) working.recycle()
        }
    }

    private fun flattenToRgb(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.RGB_565 || !bitmap.hasAlpha()) return bitmap
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(0xFF000000.toInt())
        canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return flat
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null
        return out.toByteArray()
    }

    /**
     * Legacy helper kept for tests that call downsample on raw bytes.
     * Always re-encodes to JPEG within [MAX_EDGE_PX] / budget when decodable.
     */
    internal fun downsampleIfNeeded(bytes: ByteArray, ext: String): ByteArray? {
        val bitmap = decodeSampledOriented(bytes, MAX_EDGE_PX) ?: return bytes.takeIf { it.isNotEmpty() }
        return try {
            encodeJpegWithinBudget(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
