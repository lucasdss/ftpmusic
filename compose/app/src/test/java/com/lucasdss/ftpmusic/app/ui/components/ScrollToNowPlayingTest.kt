package com.lucasdss.ftpmusic.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollToNowPlayingTest {

    @Test
    fun `indexOfTrackId finds match`() {
        assertEquals(2, indexOfTrackId(listOf("a", "b", "c"), "c"))
    }

    @Test
    fun `indexOfTrackId missing returns -1`() {
        assertEquals(-1, indexOfTrackId(listOf("a", "b"), "z"))
        assertEquals(-1, indexOfTrackId(listOf("a"), null))
        assertEquals(-1, indexOfTrackId(emptyList(), "a"))
        assertEquals(-1, indexOfTrackId(listOf("a"), ""))
    }

    @Test
    fun `resolveLazyIndex adds header count`() {
        assertEquals(5, resolveLazyIndex(headerCount = 3, trackIndex = 2))
        assertEquals(0, resolveLazyIndex(headerCount = 0, trackIndex = 0))
    }

    @Test
    fun `resolveLazyIndex invalid returns -1`() {
        assertEquals(-1, resolveLazyIndex(headerCount = 1, trackIndex = -1))
        assertEquals(-1, resolveLazyIndex(headerCount = -1, trackIndex = 0))
    }

    @Test
    fun `centerScrollOffset centers when sizes known`() {
        // viewport 1000, item 100 -> offset = 50 - 500 = -450
        assertEquals(-450, centerScrollOffset(viewportPx = 1000, itemSizePx = 100))
    }

    @Test
    fun `centerScrollOffset unknown sizes returns 0`() {
        assertEquals(0, centerScrollOffset(viewportPx = 0, itemSizePx = 100))
        assertEquals(0, centerScrollOffset(viewportPx = 1000, itemSizePx = 0))
        assertEquals(0, centerScrollOffset(viewportPx = -1, itemSizePx = 50))
    }

    @Test
    fun `centerScrollOffset never positive`() {
        // item taller than viewport -> coerceAtMost(0)
        assertEquals(0, centerScrollOffset(viewportPx = 100, itemSizePx = 300))
    }

    @Test
    fun `isTargetReasonablyVisible true near middle of window`() {
        assertTrue(
            isTargetReasonablyVisible(
                firstVisibleIndex = 0,
                lastVisibleIndex = 8,
                targetLazyIndex = 4,
                visibleItemCount = 9,
            ),
        )
    }

    @Test
    fun `isTargetReasonablyVisible false when offscreen`() {
        assertFalse(
            isTargetReasonablyVisible(
                firstVisibleIndex = 10,
                lastVisibleIndex = 18,
                targetLazyIndex = 1,
                visibleItemCount = 9,
            ),
        )
    }

    @Test
    fun `isTargetReasonablyVisible false at edge of long window`() {
        // Anchor at top of a tall window should still trigger re-center for queue.
        assertFalse(
            isTargetReasonablyVisible(
                firstVisibleIndex = 0,
                lastVisibleIndex = 11,
                targetLazyIndex = 0,
                visibleItemCount = 12,
            ),
        )
    }

    @Test
    fun `estimateRowSizePx prefers target size`() {
        assertEquals(72, estimateRowSizePx(targetItemSizePx = 72, visibleItemSizesPx = listOf(400, 72, 72)))
    }

    @Test
    fun `estimateRowSizePx skips hero sized outliers`() {
        // Median of row-like sizes (ignore 400 hero)
        assertEquals(72, estimateRowSizePx(targetItemSizePx = null, visibleItemSizesPx = listOf(400, 72, 80, 64)))
    }

    @Test
    fun `estimateRowSizePx empty returns 0`() {
        assertEquals(0, estimateRowSizePx(targetItemSizePx = null, visibleItemSizesPx = emptyList()))
        assertEquals(0, estimateRowSizePx(targetItemSizePx = null, visibleItemSizesPx = listOf(400, 500)))
    }
}
