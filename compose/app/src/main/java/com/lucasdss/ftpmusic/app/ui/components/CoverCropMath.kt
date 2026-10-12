package com.lucasdss.ftpmusic.app.ui.components

import kotlin.math.max
import kotlin.math.min

/**
 * Pure math for square cover crop: fill-min scale, pan clamp, source crop rect.
 * Unit-tested; Compose gestures feed [scale]/[offsetX]/[offsetY].
 */
object CoverCropMath {
    const val MAX_ZOOM = 4f

    /** Scale so the shorter image edge fills [viewport] (cover / fill). */
    fun minScale(imageW: Float, imageH: Float, viewport: Float): Float {
        if (imageW <= 0f || imageH <= 0f || viewport <= 0f) return 1f
        return max(viewport / imageW, viewport / imageH)
    }

    fun maxScale(imageW: Float, imageH: Float, viewport: Float): Float = minScale(imageW, imageH, viewport) * MAX_ZOOM

    fun clampScale(scale: Float, imageW: Float, imageH: Float, viewport: Float): Float {
        val min = minScale(imageW, imageH, viewport)
        val max = maxScale(imageW, imageH, viewport)
        return scale.coerceIn(min, max)
    }

    /**
     * Clamp pan so the scaled image always covers the square viewport.
     * Image is drawn centered in the viewport, then translated by [offsetX]/[offsetY].
     */
    fun clampOffset(
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        imageW: Float,
        imageH: Float,
        viewport: Float,
    ): Pair<Float, Float> {
        val dispW = imageW * scale
        val dispH = imageH * scale
        val maxOx = max(0f, (dispW - viewport) / 2f)
        val maxOy = max(0f, (dispH - viewport) / 2f)
        return offsetX.coerceIn(-maxOx, maxOx) to offsetY.coerceIn(-maxOy, maxOy)
    }

    /**
     * Visible square in source-bitmap coordinates for the current transform.
     * Returns left/top/size; size is square in source space.
     */
    fun sourceCropRect(
        imageW: Int,
        imageH: Int,
        viewport: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): CropRect {
        if (imageW <= 0 || imageH <= 0 || viewport <= 0f || scale <= 0f) {
            return CropRect(0, 0, min(imageW, imageH).coerceAtLeast(0))
        }
        val halfSrc = (viewport / scale) / 2f
        val cx = imageW / 2f - offsetX / scale
        val cy = imageH / 2f - offsetY / scale
        var left = cx - halfSrc
        var top = cy - halfSrc
        var size = viewport / scale
        // Clamp into bitmap bounds while keeping square as much as possible.
        if (left < 0f) left = 0f
        if (top < 0f) top = 0f
        if (left + size > imageW) left = (imageW - size).coerceAtLeast(0f)
        if (top + size > imageH) top = (imageH - size).coerceAtLeast(0f)
        size = min(size, min(imageW - left, imageH - top))
        val iLeft = left.toInt().coerceIn(0, imageW - 1)
        val iTop = top.toInt().coerceIn(0, imageH - 1)
        val iSize = size.toInt().coerceIn(1, min(imageW - iLeft, imageH - iTop))
        return CropRect(iLeft, iTop, iSize)
    }

    data class CropRect(val left: Int, val top: Int, val size: Int)
}
