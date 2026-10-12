package com.lucasdss.ftpmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellChromeDimensTest {

    @Test
    fun `interactiveFloorDp never below 48`() {
        assertEquals(48f, interactiveFloorDp(44.8f), 0.001f)
        assertEquals(48f, interactiveFloorDp(48f), 0.001f)
        assertEquals(56f, interactiveFloorDp(56f), 0.001f)
    }

    @Test
    fun `navBarBaseDp labeled vs icon-only`() {
        assertEquals(NAV_BAR_LABELED_BASE_DP, navBarBaseDp(hideLabels = false), 0.001f)
        assertEquals(NAV_BAR_ICON_ONLY_BASE_DP, navBarBaseDp(hideLabels = true), 0.001f)
    }

    @Test
    fun `navBarHeightDp Medium icon-only smaller than labeled`() {
        val labeled = navBarHeightDp(hideLabels = false, factor = 1.0f)
        val iconOnly = navBarHeightDp(hideLabels = true, factor = 1.0f)
        assertEquals(80f, labeled, 0.001f)
        assertEquals(56f, iconOnly, 0.001f)
        assertTrue(labeled > iconOnly)
    }

    @Test
    fun `navBarHeightDp Tiny icon-only floors at 48`() {
        // 56 * 0.80 = 44.8 → floor 48
        assertEquals(48f, navBarHeightDp(hideLabels = true, factor = 0.80f), 0.001f)
        // 80 * 0.80 = 64 → above floor
        assertEquals(64f, navBarHeightDp(hideLabels = false, factor = 0.80f), 0.001f)
    }

    @Test
    fun `navBarHeightDp Bigger scales up`() {
        assertEquals(80f * 1.30f, navBarHeightDp(hideLabels = false, factor = 1.30f), 0.001f)
        assertEquals(56f * 1.30f, navBarHeightDp(hideLabels = true, factor = 1.30f), 0.001f)
    }

    @Test
    fun `labeled always at least icon-only at same factor`() {
        for (factor in listOf(0.80f, 0.90f, 1.0f, 1.15f, 1.30f)) {
            assertTrue(
                "factor=$factor",
                navBarHeightDp(hideLabels = false, factor = factor) >=
                    navBarHeightDp(hideLabels = true, factor = factor),
            )
        }
    }

    @Test
    fun `combinedFactor Tiny at 360dp is 0_80`() {
        assertEquals(0.80f, AdaptiveScale.combinedFactor(1.0f, UiDensityPreset.Tiny.scale), 0.001f)
    }
}
