package com.lucasdss.ftpmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchTargetTest {

    @Test
    fun `min touch target is Material 48dp`() {
        assertEquals(48f, minTouchTargetDp(), 0.001f)
        assertTrue(minTouchTargetDp() >= 48f)
    }
}
