package com.lucasdss.ftpmusic.app.ui

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `padding uses last known height before first measure`() {
        assertEquals(
            72.dp,
            resolveAppHeaderContentPadding(
                showHeader = true,
                headerHeight = 0.dp,
                lastKnownHeight = 72.dp,
            ),
        )
        assertEquals(
            80.dp,
            resolveAppHeaderContentPadding(
                showHeader = true,
                headerHeight = 80.dp,
                lastKnownHeight = 72.dp,
            ),
        )
    }

    @Test
    fun `chrome translation negates header offset`() {
        assertEquals(0f, appHeaderChromeTranslationY(0f), 0f)
        assertEquals(-48f, appHeaderChromeTranslationY(48f), 0f)
        assertEquals(-120.5f, appHeaderChromeTranslationY(120.5f), 0.0001f)
        assertTrue(abs(appHeaderChromeTranslationY(10f) + 10f) < 0.0001f)
    }
}
