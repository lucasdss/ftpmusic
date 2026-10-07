package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import io.mockk.*
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CoverArtBitmapLoader: disk-cache-first artwork serving for the MediaSession
 * (QuickSettings tile / lock screen). The system MediaDataLoader rejects
 * remote artwork URIs, so media3 loads artwork through this loader and exposes
 * it as an in-memory bitmap.
 */
class CoverArtBitmapLoaderTest {

    private lateinit var loader: CoverArtBitmapLoader
    private lateinit var cacheDir: File
    private val mockContext: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(BitmapFactory::class)
        cacheDir = File(System.getProperty("java.io.tmpdir"), "cover-test-${System.nanoTime()}").apply { mkdirs() }
        every { mockContext.cacheDir } returns cacheDir
        every { mockContext.applicationContext } returns mockContext
        loader = CoverArtBitmapLoader(mockContext)
    }

    @After
    fun tearDown() {
        unmockkStatic(BitmapFactory::class)
        cacheDir.deleteRecursively()
    }

    private fun mockUri(
        hierarchical: Boolean = true,
        idParam: String? = null,
        lastSegment: String? = null,
        text: String = "cover:x",
    ): Uri {
        val uri = mockk<Uri>()
        every { uri.isHierarchical } returns hierarchical
        if (hierarchical) {
            every { uri.getQueryParameter("id") } returns idParam
            every { uri.lastPathSegment } returns lastSegment
        }
        every { uri.toString() } returns text
        return uri
    }

    @Test
    fun `coverArtIdFromUri reads id query parameter from full URL`() {
        val uri = mockUri(idParam = "al-42", lastSegment = "getCoverArt")
        assertEquals("al-42", loader.coverArtIdFromUri(uri))
    }

    @Test
    fun `coverArtIdFromUri falls back to lastPathSegment when no query id`() {
        val uri = mockUri(idParam = null, lastSegment = "al-9")
        assertEquals("al-9", loader.coverArtIdFromUri(uri))
    }

    @Test
    fun `coverArtIdFromUri handles opaque cover scheme`() {
        val uri = mockUri(hierarchical = false, text = "cover:cov-77")
        assertEquals("cov-77", loader.coverArtIdFromUri(uri))
    }

    @Test
    fun `coverArtIdFromUri returns null for pathless hierarchical uri`() {
        val uri = mockUri(idParam = null, lastSegment = null)
        assertNull(loader.coverArtIdFromUri(uri))
    }

    @Test
    fun `supportsMimeType accepts jpeg and png`() {
        assertTrue(loader.supportsMimeType("image/jpeg"))
        assertTrue(loader.supportsMimeType("image/png"))
        assertFalse(loader.supportsMimeType("image/webp"))
        assertFalse(loader.supportsMimeType(""))
    }

    @Test
    fun `decodeSampled downsizes large bitmaps to max dimension`() {
        val big = mockk<Bitmap>()
        val sampledSlot = slot<BitmapFactory.Options>()
        every { BitmapFactory.decodeFile(any(), capture(sampledSlot)) } answers {
            val opts = arg<BitmapFactory.Options>(1)
            if (opts.inJustDecodeBounds) {
                opts.outWidth = 2048
                opts.outHeight = 1024
                null
            } else {
                big
            }
        }

        val decoded = loader.decodeSampled("/fake/large.jpg")

        assertSame(big, decoded)
        assertTrue("Must downsample 2048px source", sampledSlot.captured.inSampleSize >= 4)
    }

    @Test
    fun `decodeSampled returns null when bounds are invalid`() {
        every { BitmapFactory.decodeFile(any(), any()) } returns null

        assertNull(loader.decodeSampled("/fake/empty.jpg"))
    }

    @Test
    fun `loadFromDisk serves cached artwork without network`() {
        val coverArtId = "disk-art-1"
        val serviceCacheDir = CoverArtFallbackService.getInstance(mockContext).cacheDir
        val file = File(serviceCacheDir, "navidrome|${coverArtId.hashCode()}.jpg")
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x01,
        )
        file.writeBytes(jpeg)
        com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta.write(
            file,
            com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta(
                contentSha256 = com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta.sha256Hex(jpeg),
                fetchedAtMs = System.currentTimeMillis(),
                softPlaceholder = false,
            ),
        )

        val cached = mockk<Bitmap>()
        every { BitmapFactory.decodeFile(file.absolutePath, any()) } answers {
            val opts = arg<BitmapFactory.Options>(1)
            if (opts.inJustDecodeBounds) {
                opts.outWidth = 128
                opts.outHeight = 128
                null
            } else {
                cached
            }
        }

        assertSame(cached, loader.loadFromDisk(coverArtId))
    }

    @Test
    fun `loadFromDisk returns null when artwork not cached`() {
        assertNull(loader.loadFromDisk("missing-art"))
    }

    @Test
    fun `loadFromDisk evicts an undecodable cache file`() {
        val coverArtId = "corrupt-art"
        val serviceCacheDir = CoverArtFallbackService.getInstance(mockContext).cacheDir
        val file = File(serviceCacheDir, "navidrome|${coverArtId.hashCode()}.jpg")
        file.writeBytes(ByteArray(16))
        // Undecodable: bounds stay invalid → decode returns null.
        every { BitmapFactory.decodeFile(file.absolutePath, any()) } answers {
            val opts = arg<BitmapFactory.Options>(1)
            if (opts.inJustDecodeBounds) {
                opts.outWidth = -1
                opts.outHeight = -1
            }
            null
        }

        assertNull(loader.loadFromDisk(coverArtId))
        assertFalse("corrupt cache file must be evicted", file.exists())
    }

    @Test
    fun `loadFromNetwork returns null on unreachable server`() {
        // Port 1 on localhost refuses connections immediately.
        val uri = mockUri(
            idParam = null,
            lastSegment = "x",
            text = "http://127.0.0.1:1/rest/getCoverArt?id=x",
        )
        assertNull(loader.loadFromNetwork(uri, coverArtId = null))
    }

    @Test
    fun `loadFromNetwork with null coverArtId does not write disk`() {
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x01,
        )
        val mockClient = mockk<okhttp3.OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>()
        val response = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("http://127.0.0.1:1/art").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(okhttp3.ResponseBody.create("image/jpeg".toMediaType(), bytes))
            .build()
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns response
        loader.httpClient = mockClient

        val bitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeByteArray(any(), any(), any(), any()) } answers {
            val opts = arg<BitmapFactory.Options>(3)
            if (opts.inJustDecodeBounds) {
                opts.outWidth = 40
                opts.outHeight = 40
                null
            } else {
                bitmap
            }
        }

        val cacheDir = CoverArtFallbackService.getInstance(mockContext).cacheDir
        val before = cacheDir.listFiles()?.map { it.name }?.toSet().orEmpty()
        val uri = mockUri(idParam = null, lastSegment = "x", text = "https://example.com/art")
        assertSame(bitmap, loader.loadFromNetwork(uri, coverArtId = null))
        val after = cacheDir.listFiles()?.map { it.name }?.toSet().orEmpty()
        assertEquals("null coverArtId must not create new cache files", before, after)
    }

    @Test
    fun `loadFromDisk rejects non image magic`() {
        val coverArtId = "txt-art"
        val serviceCacheDir = CoverArtFallbackService.getInstance(mockContext).cacheDir
        val file = File(serviceCacheDir, "navidrome|${coverArtId.hashCode()}.jpg")
        file.writeText("not-image")
        assertNull(loader.loadFromDisk(coverArtId))
        assertFalse(file.exists())
    }

    @Test
    fun `loadFromNetwork fetches and caches artwork on success`() {
        val coverArtId = "net-art"
        // Valid JPEG magic so publishNavidromeBytes / writeImageAtomically accepts it.
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x01,
        ) + ByteArray(20) { it.toByte() }
        val mockClient = mockk<okhttp3.OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>()
        val response = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("http://127.0.0.1:1/art").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(okhttp3.ResponseBody.create("image/jpeg".toMediaType(), bytes))
            .build()
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns response
        loader.httpClient = mockClient

        val bitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeByteArray(any(), any(), any(), any()) } answers {
            val opts = arg<BitmapFactory.Options>(3)
            if (opts.inJustDecodeBounds) {
                opts.outWidth = 100
                opts.outHeight = 100
                null
            } else {
                bitmap
            }
        }

        val uri = mockUri(
            idParam = coverArtId,
            lastSegment = "getCoverArt",
            text = "https://music.example.com/rest/getCoverArt?id=$coverArtId",
        )

        assertSame(bitmap, loader.loadFromNetwork(uri, coverArtId))

        val cached = File(
            CoverArtFallbackService.getInstance(mockContext).cacheDir,
            "navidrome|${coverArtId.hashCode()}.jpg",
        )
        assertTrue("Artwork must be cached to disk", cached.exists())
        assertArrayEquals(bytes, cached.readBytes())
    }

    @Test
    fun `loadBitmap falls back to stale disk when network fails`() {
        val coverArtId = "stale-disk"
        val serviceCacheDir = CoverArtFallbackService.getInstance(mockContext).cacheDir
        val file = File(serviceCacheDir, "navidrome|${coverArtId.hashCode()}.jpg")
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x01,
        )
        file.writeBytes(jpeg)
        // No meta → stale → network first, then disk fallback
        val cached = mockk<Bitmap>()
        every { BitmapFactory.decodeFile(file.absolutePath, any()) } answers {
            val opts = arg<BitmapFactory.Options>(1)
            if (opts.inJustDecodeBounds) {
                opts.outWidth = 48
                opts.outHeight = 48
                null
            } else {
                cached
            }
        }

        val uri = mockUri(
            idParam = coverArtId,
            lastSegment = "getCoverArt",
            text = "http://127.0.0.1:1/rest/getCoverArt?id=$coverArtId",
        )
        assertSame(cached, loader.loadBitmap(uri).get())
    }

    @Test
    fun `loadFromNetwork on 304 touches meta and reloads disk`() {
        val coverArtId = "ca-304-loader"
        val serviceCacheDir = CoverArtFallbackService.getInstance(mockContext).cacheDir
        val file = File(serviceCacheDir, "navidrome|${coverArtId.hashCode()}.jpg")
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x01,
        )
        file.writeBytes(jpeg)
        com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta.write(
            file,
            com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta(
                contentSha256 = com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta.sha256Hex(jpeg),
                etag = "\"e\"",
                fetchedAtMs = 50L,
                softPlaceholder = false,
            ),
        )

        val mockClient = mockk<okhttp3.OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>()
        val response = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("http://127.0.0.1:1/art").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(304).message("Not Modified")
            .body(okhttp3.ResponseBody.create("image/jpeg".toMediaType(), ByteArray(0)))
            .build()
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns response
        loader.httpClient = mockClient

        val cached = mockk<Bitmap>()
        every { BitmapFactory.decodeFile(file.absolutePath, any()) } answers {
            val opts = arg<BitmapFactory.Options>(1)
            if (opts.inJustDecodeBounds) {
                opts.outWidth = 32
                opts.outHeight = 32
                null
            } else {
                cached
            }
        }

        val uri = mockUri(
            idParam = coverArtId,
            lastSegment = "getCoverArt",
            text = "https://music.example.com/rest/getCoverArt?id=$coverArtId",
        )
        assertSame(cached, loader.loadFromNetwork(uri, coverArtId))
        val meta = com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta.read(file)
        assertNotNull(meta)
        assertTrue(meta!!.fetchedAtMs > 50L)
    }

    @Test
    fun `loadFromNetwork returns null on unsuccessful response`() {
        val mockClient = mockk<okhttp3.OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>()
        val response = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("http://127.0.0.1:1/art").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(404).message("Not Found")
            .body(okhttp3.ResponseBody.create("text/plain".toMediaType(), ByteArray(0)))
            .build()
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns response
        loader.httpClient = mockClient

        val uri = mockUri(
            idParam = "nf",
            lastSegment = "getCoverArt",
            text = "https://music.example.com/rest/getCoverArt?id=nf",
        )

        assertNull(loader.loadFromNetwork(uri, "nf"))
    }

    @Test
    fun `decodeBitmap decodes bytes via future`() {
        val bitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeByteArray(any(), any(), any(), any()) } answers {
            val opts = arg<BitmapFactory.Options>(3)
            if (opts.inJustDecodeBounds) {
                opts.outWidth = 64
                opts.outHeight = 64
                null
            } else {
                bitmap
            }
        }

        assertSame(bitmap, loader.decodeBitmap(ByteArray(16)).get())
    }

    @Test
    fun `decodeBitmap future fails when bytes undecodable`() {
        every { BitmapFactory.decodeByteArray(any(), any(), any(), any()) } answers {
            val opts = arg<BitmapFactory.Options>(3)
            if (opts.inJustDecodeBounds) {
                opts.outWidth = -1
                opts.outHeight = -1
            }
            null
        }
        try {
            loader.decodeBitmap(ByteArray(4)).get()
            fail("must fail for undecodable bytes")
        } catch (e: java.util.concurrent.ExecutionException) {
            assertTrue(e.cause is IllegalStateException)
        }
    }

    @Test
    fun `decodeSampled from bytes downsamples`() {
        val bitmap = mockk<Bitmap>()
        val sampledSlot = slot<BitmapFactory.Options>()
        every { BitmapFactory.decodeByteArray(any(), any(), any(), capture(sampledSlot)) } answers {
            val opts = arg<BitmapFactory.Options>(3)
            if (opts.inJustDecodeBounds) {
                opts.outWidth = 4096
                opts.outHeight = 4096
                null
            } else {
                bitmap
            }
        }

        assertSame(bitmap, loader.decodeSampled(ByteArray(16)))
        assertTrue("Must downsample 4096px source", sampledSlot.captured.inSampleSize >= 8)
    }

    @Test
    fun `loadBitmap completes with disk-cached bitmap for remote-style uri`() {
        val coverArtId = "future-art"
        val serviceCacheDir = CoverArtFallbackService.getInstance(mockContext).cacheDir
        val file = File(serviceCacheDir, "navidrome|${coverArtId.hashCode()}.jpg")
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x01,
        )
        file.writeBytes(jpeg)
        com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta.write(
            file,
            com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta(
                contentSha256 = com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta.sha256Hex(jpeg),
                fetchedAtMs = System.currentTimeMillis(),
                softPlaceholder = false,
            ),
        )

        val cached = mockk<Bitmap>()
        every { BitmapFactory.decodeFile(file.absolutePath, any()) } answers {
            val opts = arg<BitmapFactory.Options>(1)
            if (opts.inJustDecodeBounds) {
                opts.outWidth = 64
                opts.outHeight = 64
                null
            } else {
                cached
            }
        }

        val uri = mockUri(
            idParam = coverArtId,
            lastSegment = "getCoverArt",
            text = "https://music.example.com/rest/getCoverArt?id=$coverArtId",
        )

        val future = loader.loadBitmap(uri)
        assertSame(cached, future.get())
    }

    @Test
    fun `loadBitmap future fails when artwork missing everywhere`() {
        val uri = mockUri(
            idParam = "nope",
            lastSegment = "getCoverArt",
            text = "http://127.0.0.1:1/rest/getCoverArt?id=nope",
        )

        try {
            loader.loadBitmap(uri).get()
            fail("Future must fail when no artwork is available")
        } catch (_: Exception) {
            // expected
        }
    }
}
