package com.lucasdss.ftpmusic.app.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** ADR-0107 hard-fix — fixed Lazy contentPadding under overlay header. */
class AppHeaderContentPaddingTest {

    @Test
    fun `padding equals header height when shown`() {
        assertEquals(72.dp, resolveAppHeaderContentPadding(showHeader = true, headerHeight = 72.dp))
    }

    @Test
    fun `padding zero when header hidden or unmeasured`() {
        assertEquals(0.dp, resolveAppHeaderContentPadding(showHeader = false, headerHeight = 72.dp))
        assertEquals(0.dp, resolveAppHeaderContentPadding(showHeader = true, headerHeight = 0.dp))
    }
}
