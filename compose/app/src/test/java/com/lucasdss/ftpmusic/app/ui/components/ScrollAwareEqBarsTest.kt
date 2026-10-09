package com.lucasdss.ftpmusic.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADR-0102: infinite EQ must not run during Lazy fling. */
class ScrollAwareEqBarsTest {

    @Test
    fun `shouldAnimateEqBars false while scrolling`() {
        assertFalse(shouldAnimateEqBars(scrollInProgress = true))
    }

    @Test
    fun `shouldAnimateEqBars true when idle`() {
        assertTrue(shouldAnimateEqBars(scrollInProgress = false))
    }
}
