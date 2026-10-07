package com.lucasdss.ftpmusic.app.data.cache

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoverArtCacheMetaTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @After
    fun tearDown() {
        CoverArtPlaceholders.clearKnownForTests()
    }

    private fun jpegBytes(seed: Byte = 0x42): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
        0x00, 0x01, seed,
    )

    @Test
    fun `round trip json and sidecar files`() {
        val image = tempFolder.newFile("art.jpg")
        image.writeBytes(jpegBytes())
        val meta = CoverArtCacheMeta(
            contentSha256 = CoverArtCacheMeta.sha256Hex(image.readBytes()),
            etag = "\"abc\"",
            lastModified = "Wed, 01 Jan 2020 00:00:00 GMT",
            fetchedAtMs = 1_700_000_000_000L,
            softPlaceholder = true,
        )
        CoverArtCacheMeta.write(image, meta)
        val read = CoverArtCacheMeta.read(image)
        assertNotNull(read)
        assertEquals(meta.contentSha256, read!!.contentSha256)
        assertEquals(meta.etag, read.etag)
        assertEquals(meta.lastModified, read.lastModified)
        assertEquals(meta.fetchedAtMs, read.fetchedAtMs)
        assertTrue(read.softPlaceholder)
    }

    @Test
    fun `isFresh respects soft and real TTL`() {
        val image = tempFolder.newFile("fresh.jpg")
        image.writeBytes(jpegBytes())
        val sha = CoverArtCacheMeta.sha256Hex(image.readBytes())
        val now = 1_700_000_000_000L

        CoverArtCacheMeta.write(
            image,
            CoverArtCacheMeta(sha, fetchedAtMs = now - 1_000, softPlaceholder = false),
        )
        assertTrue(CoverArtCacheMeta.isFresh(image, now))

        CoverArtCacheMeta.write(
            image,
            CoverArtCacheMeta(
                sha,
                fetchedAtMs = now - CoverArtCacheMeta.REAL_TTL_MS - 1,
                softPlaceholder = false,
            ),
        )
        assertFalse(CoverArtCacheMeta.isFresh(image, now))

        CoverArtCacheMeta.write(
            image,
            CoverArtCacheMeta(
                sha,
                fetchedAtMs = now - CoverArtCacheMeta.SOFT_TTL_MS - 1,
                softPlaceholder = true,
            ),
        )
        assertFalse(CoverArtCacheMeta.isFresh(image, now))

        assertFalse(CoverArtCacheMeta.isFresh(tempFolder.newFile("legacy.jpg"), now))
    }

    @Test
    fun `touchFetchedAt updates age`() {
        val image = tempFolder.newFile("touch.jpg")
        image.writeBytes(jpegBytes())
        val sha = CoverArtCacheMeta.sha256Hex(image.readBytes())
        CoverArtCacheMeta.write(
            image,
            CoverArtCacheMeta(sha, etag = "e1", fetchedAtMs = 100L, softPlaceholder = false),
        )
        val updated = CoverArtCacheMeta.touchFetchedAt(image, nowMs = 999L)
        assertNotNull(updated)
        assertEquals(999L, updated!!.fetchedAtMs)
        assertEquals("e1", updated.etag)
    }

    @Test
    fun `known placeholder registry`() {
        val sha = CoverArtCacheMeta.sha256Hex(jpegBytes(0x11))
        assertFalse(CoverArtPlaceholders.isKnownPlaceholder(sha))
        CoverArtPlaceholders.addKnownPlaceholderSha(sha)
        assertTrue(CoverArtPlaceholders.isKnownPlaceholder(sha))
    }

    @Test
    fun `fromJson returns null on garbage`() {
        assertNull(CoverArtCacheMeta.fromJson("not-json"))
    }

    @Test
    fun `fromJson treats null etag and lastModified as absent`() {
        val raw = """
            {"contentSha256":"abc","etag":null,"lastModified":null,"fetchedAtMs":1,"softPlaceholder":false}
        """.trimIndent()
        val meta = CoverArtCacheMeta.fromJson(raw)
        assertNotNull(meta)
        assertNull(meta!!.etag)
        assertNull(meta.lastModified)
        assertEquals("abc", meta.contentSha256)
    }

    @Test
    fun `delete removes sidecar`() {
        val image = tempFolder.newFile("del.jpg")
        image.writeBytes(jpegBytes())
        CoverArtCacheMeta.write(
            image,
            CoverArtCacheMeta(
                contentSha256 = "x",
                fetchedAtMs = 1L,
                softPlaceholder = false,
            ),
        )
        assertTrue(CoverArtCacheMeta.metaFileFor(image).exists())
        CoverArtCacheMeta.delete(image)
        assertFalse(CoverArtCacheMeta.metaFileFor(image).exists())
    }

    @Test
    fun `touchFetchedAt returns null when meta missing`() {
        val image = tempFolder.newFile("nometa.jpg")
        image.writeBytes(jpegBytes())
        assertNull(CoverArtCacheMeta.touchFetchedAt(image))
    }
}
