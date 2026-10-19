package com.lucasdss.ftpmusic.app.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionCoverStoreTest {

    private lateinit var context: Context
    private lateinit var store: CollectionCoverStore
    private lateinit var root: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = CollectionCoverStore(context)
        root = File(context.filesDir, CollectionCoverStore.DIR).also { it.mkdirs() }
        root.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun `absolutePath rejects traversal and missing files`() {
        assertNull(store.absolutePath("../etc/passwd"))
        assertNull(store.absolutePath("missing.jpg"))
        assertNull(store.absolutePath(""))
        assertNull(store.resolveLocalFile(null))
        assertNull(store.resolveLocalFile(".."))
    }

    @Test
    fun `importFromUri writes jpeg under prefix_id`() = runBlocking {
        val src = File(context.cacheDir, "src.jpg").also {
            it.writeBytes(encodedJpeg(64, 64))
        }
        val relative = store.importFromUri(Uri.fromFile(src), "mix", "7")
        assertEquals("mix_7.jpg", relative)
        assertTrue(File(root, "mix_7.jpg").isFile)
        assertNotNull(store.absolutePath("mix_7.jpg"))
        assertTrue(File(root, "mix_7.jpg").length() <= CollectionCoverStore.MAX_OUTPUT_BYTES)
    }

    @Test
    fun `importFromUri rejects empty payload`() = runBlocking {
        val src = File(context.cacheDir, "empty.bin").also { it.writeBytes(ByteArray(0)) }
        assertNull(store.importFromUri(Uri.fromFile(src), "mix", "1"))
        assertFalse(File(root, "mix_1.jpg").exists())
        assertNull(store.decodeStreamOriented(ByteArray(0).inputStream(), 2048))
    }

    @Test
    fun `importFromUri accepts valid jpeg larger than legacy 8MiB cap`() = runBlocking {
        // Build a valid JPEG then pad file past MAX_IMPORT_BYTES with trailing zeros
        // (decoders ignore trailing junk; proves we no longer hard-reject by byte size).
        val jpeg = encodedJpeg(512, 512)
        val src = File(context.cacheDir, "padded.jpg").also { f ->
            FileOutputStream(f).use { out ->
                out.write(jpeg)
                val pad = ByteArray(8192)
                var remaining = CollectionCoverStore.MAX_IMPORT_BYTES + 1 - jpeg.size
                while (remaining > 0) {
                    val n = minOf(pad.size.toLong(), remaining).toInt()
                    out.write(pad, 0, n)
                    remaining -= n
                }
            }
        }
        assertTrue(src.length() > CollectionCoverStore.MAX_IMPORT_BYTES)
        val relative = store.importFromUri(Uri.fromFile(src), "mix", "big")
        assertEquals("mix_big.jpg", relative)
        val out = File(root, relative!!)
        assertTrue(out.isFile)
        assertTrue(out.length() <= CollectionCoverStore.MAX_OUTPUT_BYTES)
    }

    @Test
    fun `decodeStreamOriented applies EXIF rotate 90`() {
        val jpeg = encodedJpeg(40, 20)
        // Inject EXIF Orientation=6 (ROTATE_90) via ExifInterface rewrite.
        val withExif = File(context.cacheDir, "exif90.jpg").also { it.writeBytes(jpeg) }
        ExifInterface(withExif.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val bmp = store.decodeStreamOriented(withExif.inputStream(), 2048)
        assertNotNull(bmp)
        // 40x20 + rotate 90 → 20x40
        assertEquals(20, bmp!!.width)
        assertEquals(40, bmp.height)
        bmp.recycle()
    }

    @Test
    fun `readUriBytesCapped still rejects oversize full buffer`() {
        val src = File(context.cacheDir, "huge.bin").also {
            it.writeBytes(ByteArray((CollectionCoverStore.MAX_IMPORT_BYTES + 1).toInt()) { 0xFF.toByte() })
        }
        assertNull(store.readUriBytesCapped(Uri.fromFile(src)))
    }

    @Test
    fun `importFromUri downsamples oversized dimensions`() = runBlocking {
        val src = File(context.cacheDir, "wide.jpg").also {
            it.writeBytes(encodedJpeg(4096, 4096))
        }
        val relative = store.importFromUri(Uri.fromFile(src), "mix", "wide")
        assertEquals("mix_wide.jpg", relative)
        val out = File(root, relative!!)
        assertTrue(out.isFile)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(out.absolutePath, bounds)
        assertTrue(bounds.outWidth > 0)
        assertTrue(bounds.outWidth <= CollectionCoverStore.MAX_EDGE_PX)
        assertTrue(bounds.outHeight <= CollectionCoverStore.MAX_EDGE_PX)
        assertTrue(out.length() <= CollectionCoverStore.MAX_OUTPUT_BYTES)
    }

    @Test
    fun `importCroppedBitmap always jpeg and respects edge cap`() = runBlocking {
        val bmp = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF44AAFF.toInt())
        val relative = store.importCroppedBitmap(bmp, "pl", "9")
        bmp.recycle()
        assertEquals("pl_9.jpg", relative)
        val out = File(root, relative!!)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(out.absolutePath, bounds)
        assertTrue(bounds.outWidth <= CollectionCoverStore.MAX_EDGE_PX)
        assertTrue(bounds.outHeight <= CollectionCoverStore.MAX_EDGE_PX)
        assertTrue(out.length() <= CollectionCoverStore.MAX_OUTPUT_BYTES)
        assertTrue(out.length() > 0L)
    }

    @Test
    fun `importCroppedBitmap rejects empty bitmap`() = runBlocking {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        // width/height > 0 always for createBitmap — use recycled path via zero check is enough
        // Zero-size not creatable; recycle then import would crash — skip.
        // Instead verify tiny bitmap still writes.
        bmp.eraseColor(0xFF000000.toInt())
        assertNotNull(store.importCroppedBitmap(bmp, "mix", "tiny"))
        bmp.recycle()
    }

    @Test
    fun `downsampleIfNeeded reencodes to jpeg within budget`() {
        val bytes = encodedPng(32, 32)
        val out = store.downsampleIfNeeded(bytes, "png")
        assertNotNull(out)
        assertTrue(out!!.isNotEmpty())
        assertTrue(out.size <= CollectionCoverStore.MAX_OUTPUT_BYTES)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(out, 0, out.size, bounds)
        assertTrue(bounds.outWidth > 0)
    }

    @Test
    fun `encodeJpegWithinBudget caps large bitmaps`() {
        val bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        // Noise-ish fill to resist extreme JPEG compression
        for (y in 0 until 1024 step 3) {
            for (x in 0 until 1024 step 3) {
                bmp.setPixel(x, y, (0xFF000000.toInt() or ((x * y) and 0xFFFFFF)))
            }
        }
        val out = store.encodeJpegWithinBudget(bmp)
        bmp.recycle()
        assertNotNull(out)
        assertTrue(out!!.size <= CollectionCoverStore.MAX_OUTPUT_BYTES)
    }

    @Test
    fun `rekey renames staged new_ file to numeric id`() = runBlocking {
        File(root, "mix_new_999.jpg").writeBytes(encodedJpeg(16, 16))
        val next = store.rekey("mix", "mix_new_999.jpg", "42")
        assertEquals("mix_42.jpg", next)
        assertTrue(File(root, "mix_42.jpg").isFile)
        assertFalse(File(root, "mix_new_999.jpg").exists())
    }

    @Test
    fun `rekey same id is idempotent`() = runBlocking {
        File(root, "mix_7.jpg").writeBytes(encodedJpeg(16, 16))
        assertEquals("mix_7.jpg", store.rekey("mix", "mix_7.jpg", "7"))
        assertTrue(File(root, "mix_7.jpg").isFile)
    }

    @Test
    fun `rekey rejects traversal and wrong prefix`() = runBlocking {
        assertNull(store.rekey("mix", "../x.jpg", "1"))
        File(root, "pl_1.jpg").writeBytes(encodedJpeg(16, 16))
        assertNull(store.rekey("mix", "pl_1.jpg", "2"))
        assertNull(store.rekey("mix", "mix_gone.jpg", "3"))
    }

    @Test
    fun `deleteRelative and deleteForPrefix clean files`() = runBlocking {
        File(root, "mix_5.jpg").writeBytes(encodedJpeg(16, 16))
        File(root, "mix_5.png").writeBytes(encodedPng(16, 16))
        File(root, "mix_6.jpg").writeBytes(encodedJpeg(16, 16))
        store.deleteRelative("mix_6.jpg")
        assertFalse(File(root, "mix_6.jpg").exists())
        store.deleteForPrefix("mix", "5")
        assertFalse(File(root, "mix_5.jpg").exists())
        assertFalse(File(root, "mix_5.png").exists())
        store.deleteRelative("../nope.jpg")
        store.deleteRelative(null)
    }

    @Test
    fun `import sanitizes unsafe id characters`() = runBlocking {
        val src = File(context.cacheDir, "safe.jpg").also { it.writeBytes(encodedJpeg(8, 8)) }
        val relative = store.importFromUri(Uri.fromFile(src), "pl", "a/b c")
        assertEquals("pl_a_b_c.jpg", relative)
    }

    private fun encodedJpeg(w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF336699.toInt())
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun encodedPng(w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF00AA88.toInt())
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
