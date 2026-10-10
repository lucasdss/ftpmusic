package com.lucasdss.ftpmusic.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-math coverage for AppHeader enterAlways collapse (ADR 0097). */
class AppHeaderScrollStateTest {

    @Test
    fun `hide scroll increases offset and consumes`() {
        val (next, consumed) = computeHeaderOffset(currentOffset = 0f, headerHeight = 100f, deltaY = -40f)
        assertEquals(40f, next, 0.01f)
        assertEquals(-40f, consumed, 0.01f)
    }

    @Test
    fun `reveal scroll decreases offset enterAlways`() {
        val (next, consumed) = computeHeaderOffset(currentOffset = 60f, headerHeight = 100f, deltaY = 30f)
        assertEquals(30f, next, 0.01f)
        assertEquals(30f, consumed, 0.01f)
    }

    @Test
    fun `fully collapsed further hide consumes zero`() {
        val (next, consumed) = computeHeaderOffset(currentOffset = 100f, headerHeight = 100f, deltaY = -20f)
        assertEquals(100f, next, 0.01f)
        assertEquals(0f, consumed, 0.01f)
    }

    @Test
    fun `fully expanded further reveal consumes zero`() {
        val (next, consumed) = computeHeaderOffset(currentOffset = 0f, headerHeight = 100f, deltaY = 20f)
        assertEquals(0f, next, 0.01f)
        assertEquals(0f, consumed, 0.01f)
    }

    @Test
    fun `snap mid prefers nearer edge`() {
        assertEquals(0f, computeSnapTarget(30f, 100f, 0f), 0.01f)
        assertEquals(100f, computeSnapTarget(60f, 100f, 0f), 0.01f)
    }

    @Test
    fun `snap velocity overrides midpoint`() {
        assertEquals(0f, computeSnapTarget(80f, 100f, velocityY = 900f), 0.01f)
        assertEquals(100f, computeSnapTarget(20f, 100f, velocityY = -900f), 0.01f)
    }

    @Test
    fun `content inset stays full header height when shown`() {
        assertEquals(100f, computeHeaderContentInsetPx(showHeader = true, headerHeightPx = 100f), 0.01f)
        assertEquals(0f, computeHeaderContentInsetPx(showHeader = false, headerHeightPx = 100f), 0.01f)
        assertEquals(0f, computeHeaderContentInsetPx(showHeader = true, headerHeightPx = 0f), 0.01f)
    }
}
