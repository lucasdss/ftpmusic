package com.lucasdss.ftpmusic.app.ui.components

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoverCropExportTest {

    @Test
    fun `exportCroppedBitmap returns square at fill scale`() {
        val bmp = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF112233.toInt())
        val scale = CoverCropMath.minScale(800f, 800f, 400f)
        val out = exportCroppedBitmap(bmp, 400f, scale, 0f, 0f)
        bmp.recycle()
        assertNotNull(out)
        assertEquals(out!!.width, out.height)
        assertTrue(out.width >= 1)
        out.recycle()
    }

    @Test
    fun `exportCroppedBitmap handles landscape fill`() {
        val bmp = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF445566.toInt())
        val scale = CoverCropMath.minScale(1600f, 800f, 400f)
        val out = exportCroppedBitmap(bmp, 400f, scale, 0f, 0f)
        bmp.recycle()
        assertNotNull(out)
        assertEquals(out!!.width, out.height)
        out.recycle()
    }

    @Test
    fun `exportCroppedBitmap at minScale without extra zoom for portrait`() {
        val bmp = Bitmap.createBitmap(800, 1600, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF778899.toInt())
        val scale = CoverCropMath.minScale(800f, 1600f, 400f)
        // Default framing only — no pinch above minScale.
        assertEquals(scale, CoverCropMath.clampScale(scale, 800f, 1600f, 400f))
        val out = exportCroppedBitmap(bmp, 400f, scale, 0f, 0f)
        bmp.recycle()
        assertNotNull(out)
        assertEquals(out!!.width, out.height)
        out.recycle()
    }
}
