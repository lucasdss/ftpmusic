package com.lucasdss.ftpmusic.app.data.cache

import android.graphics.BitmapFactory
import java.io.File

/**
 * Shared validity checks for on-disk cover art files.
 *
 * Cache trust was previously `exists() && length() > 0`, which accepts a
 * truncated/partial download forever — Coil then fails to decode and the UI
 * shows a blank even though the app "has it cached". These helpers reject
 * non-image payloads (e.g. Navidrome's HTTP 200 JSON error bodies) and, on
 * Android, verify the image header actually decodes.
 */
object CoverArtFiles {

    /** 1×1 transparent PNG — used only to probe whether BitmapFactory decodes. */
    private val PROBE_PNG: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
        0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
        0x54, 0x78.toByte(), 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )

    /**
     * True when BitmapFactory is the real Android implementation. Plain-JVM
     * unit tests stub it out (returns null / leaves dimensions at 0), in which
     * case [isUsableImage] falls back to magic-byte validation.
     */
    private val canDecodeBounds: Boolean by lazy {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(PROBE_PNG, 0, PROBE_PNG.size, options)
            options.outWidth > 0 && options.outHeight > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Cheap check safe for UI composition: exists, non-empty, and starts with
     * a known image magic. Does NOT decode — use [isUsableImage] off the main
     * thread when a full header decode is affordable.
     */
    fun looksLikeImage(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return hasImageMagic(file)
    }

    /** True when [file] exists and decodes to non-zero dimensions. */
    fun isUsableImage(file: File): Boolean {
        if (!looksLikeImage(file)) return false
        if (!canDecodeBounds) return true
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth > 0 && options.outHeight > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Delete [file] when it exists but cannot be decoded. Returns true when a
     * file was deleted (caller may want to invalidate a URL that pointed at it).
     */
    fun deleteIfUnusable(file: File): Boolean {
        if (!file.exists()) return false
        if (isUsableImage(file)) return false
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    /** Delete [file] when it does not even look like an image. */
    fun deleteIfNotImage(file: File): Boolean {
        if (!file.exists()) return false
        if (looksLikeImage(file)) return false
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    /** JPEG / PNG / GIF / WebP magic-byte check. */
    private fun hasImageMagic(file: File): Boolean {
        return try {
            val header = ByteArray(12)
            val read = file.inputStream().use { it.read(header) }
            if (read < 4) return false
            when {
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> true

                // JPEG
                header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() &&
                    header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte() -> true

                // PNG
                header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() -> true

                // GIF
                read >= 12 &&
                    header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
                    header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte() -> true

                // WebP
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}
