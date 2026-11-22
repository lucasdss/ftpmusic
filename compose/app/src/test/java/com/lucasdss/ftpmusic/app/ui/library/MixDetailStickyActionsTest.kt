package com.lucasdss.ftpmusic.app.ui.library

import com.lucasdss.ftpmusic.app.ui.components.resolveLazyIndex
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Daily Mix detail pins Play/Shuffle/⋮ via stickyHeader after the cover item.
 * Now-playing auto-center must skip both header slots.
 */
class MixDetailStickyActionsTest {

    @Test
    fun `lazy header count is cover plus sticky actions`() {
        assertEquals(2, MIX_DETAIL_LAZY_HEADER_COUNT)
    }

    @Test
    fun `first track lazy index sits after cover and sticky actions`() {
        assertEquals(
            2,
            resolveLazyIndex(headerCount = MIX_DETAIL_LAZY_HEADER_COUNT, trackIndex = 0),
        )
        assertEquals(
            5,
            resolveLazyIndex(headerCount = MIX_DETAIL_LAZY_HEADER_COUNT, trackIndex = 3),
        )
    }
}
