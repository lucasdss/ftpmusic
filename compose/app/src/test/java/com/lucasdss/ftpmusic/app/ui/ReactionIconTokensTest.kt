package com.lucasdss.ftpmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionIconTokensTest {
    @Test
    fun marketBaseSizesMatchAdr0059() {
        assertEquals(20f, ReactionIconTokens.GLYPH_BASE_DP)
        assertEquals(40f, ReactionIconTokens.HIT_BASE_DP)
        assertEquals(20f, ReactionIconTokens.STAR_BASE_DP)
    }
}
