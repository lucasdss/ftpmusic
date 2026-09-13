package com.lucasdss.ftpmusic.app.data.cache

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Plain-JVM tests for [CoverArtFiles]. BitmapFactory is a stub in unit tests,
 * so validation falls back to magic-byte detection (see canDecodeBounds).
 */
class CoverArtFilesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun file(bytes: ByteArray): File {
        val f = tempFolder.newFile()
        f.writeBytes(bytes)
        return f
    }

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A)
    private val gif =
        byteArrayOf(
            'G'.code.toByte(),
            'I'.code.toByte(),
            'F'.code.toByte(),
            '8'.code.toByte(),
            '9'.code.toByte(),
            'a'.code.toByte(),
        )
    private val webp = byteArrayOf(
        'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
        0x00, 0x00, 0x00, 0x00,
        'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
    )

    @Test
    fun `accepts jpeg png gif and webp magic bytes`() {
        assertTrue(CoverArtFiles.isUsableImage(file(jpeg)))
        assertTrue(CoverArtFiles.isUsableImage(file(png)))
        assertTrue(CoverArtFiles.isUsableImage(file(gif)))
        assertTrue(CoverArtFiles.isUsableImage(file(webp)))
    }

    @Test
    fun `rejects json error bodies and html`() {
        assertFalse(CoverArtFiles.isUsableImage(file("""{"subsonic-response":{"status":"failed"}}""".toByteArray())))
        assertFalse(CoverArtFiles.isUsableImage(file("<!DOCTYPE html><html>".toByteArray())))
    }

    @Test
    fun `rejects missing and empty files`() {
        assertFalse(CoverArtFiles.isUsableImage(File(tempFolder.root, "missing.jpg")))
        assertFalse(CoverArtFiles.isUsableImage(file(ByteArray(0))))
        assertFalse(CoverArtFiles.isUsableImage(file(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))))
    }

    @Test
    fun `deleteIfUnusable evicts corrupt files and keeps valid ones`() {
        val corrupt = file("not an image".toByteArray())
        val valid = file(jpeg)
        val missing = File(tempFolder.root, "nope.jpg")

        assertTrue(CoverArtFiles.deleteIfUnusable(corrupt))
        assertFalse(corrupt.exists())

        assertFalse(CoverArtFiles.deleteIfUnusable(valid))
        assertTrue(valid.exists())

        assertFalse(CoverArtFiles.deleteIfUnusable(missing))
    }

    @Test
    fun `looksLikeImage is magic-only and deleteIfNotImage evicts non-images`() {
        val valid = file(jpeg)
        val text = file("not an image".toByteArray())

        assertTrue(CoverArtFiles.looksLikeImage(valid))
        assertFalse(CoverArtFiles.looksLikeImage(text))

        assertTrue(CoverArtFiles.deleteIfNotImage(text))
        assertFalse(text.exists())
        assertFalse(CoverArtFiles.deleteIfNotImage(valid))
        assertTrue(valid.exists())
    }
}
