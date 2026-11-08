package com.lucasdss.ftpmusic.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutWatermarkTest {
    @Test
    fun `B1-2 full sync stamp avoids Never synced when metadata stamp missing`() {
        assertFalse(AboutWatermark.neverSynced(0L, 1_700L))
        assertEquals(1_700L, AboutWatermark.displayMs(0L, 1_700L))
    }

    @Test
    fun `never synced when both zero`() {
        assertTrue(AboutWatermark.neverSynced(0L, 0L))
    }

    @Test
    fun `prefers newer stamp`() {
        assertEquals(200L, AboutWatermark.displayMs(100L, 200L))
        assertEquals(300L, AboutWatermark.displayMs(300L, 200L))
    }
}
