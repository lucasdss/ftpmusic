package com.lucasdss.ftpmusic.app.ui

import androidx.compose.ui.unit.Constraints
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

    @Test
    fun `riding extra clamps non-positive max offset to zero and ceils fractions`() {
        assertEquals(0, appHeaderRidingExtraPx(0f))
        assertEquals(0, appHeaderRidingExtraPx(-12f))
        assertEquals(48, appHeaderRidingExtraPx(48f))
        assertEquals(49, appHeaderRidingExtraPx(48.2f))
        assertEquals(49, appHeaderRidingExtraPx(48.6f))
    }

    @Test
    fun `riding measure height adds fixed extra without live offset`() {
        assertEquals(0, appHeaderRidingMeasureHeight(0, 0f))
        assertEquals(100, appHeaderRidingMeasureHeight(100, 0f))
        assertEquals(148, appHeaderRidingMeasureHeight(100, 48f))
        // Pure helper preserves Infinity; Modifier.appHeaderRidingContent guards
        // and never reports Infinity as a layout height (see KDoc).
        assertEquals(Constraints.Infinity, appHeaderRidingMeasureHeight(Constraints.Infinity, 72f))
    }

    @Test
    fun `riding translation plus fixed extra covers bottom strip`() {
        // Visible gap under fillMaxSize translate was ≈ offset; over-measure by maxOffset
        // means after -offset paint, bottom still reaches parent H when offset ≤ maxOffset.
        val maxOffset = 72f
        val offset = 40f
        val parentH = 800
        val childH = appHeaderRidingMeasureHeight(parentH, maxOffset)
        val translationY = appHeaderChromeTranslationY(offset)
        val paintedBottom = childH + translationY
        assertTrue(paintedBottom >= parentH)
        assertEquals(parentH + 72, childH)
        assertEquals(-40f, translationY, 0f)
    }
}
