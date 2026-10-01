package com.lucasdss.ftpmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyPolicyTest {

    @Test
    fun `track title base is textHeadingS 16sp`() {
        assertEquals(16f, TypographyPolicy.trackTitleBaseSp(), 0.001f)
        assertEquals(TypographyPolicy.TRACK_TITLE_BASE_SP, TypographyPolicy.trackTitleBaseSp(), 0.001f)
    }

    @Test
    fun `section and display bases match Dimens ramp`() {
        assertEquals(18f, TypographyPolicy.sectionTitleBaseSp(), 0.001f)
        assertEquals(24f, TypographyPolicy.displayBaseSp(), 0.001f)
        assertEquals(12f, TypographyPolicy.trackSubtitleBaseSp(), 0.001f)
    }

    @Test
    fun `font families are Outfit UI and Inter captions`() {
        assertEquals("Outfit", TypographyPolicy.uiFontFamilyName())
        assertEquals("Inter", TypographyPolicy.captionFontFamilyName())
        assertTrue(TypographyPolicy.uiFontFamilyName() != TypographyPolicy.captionFontFamilyName())
    }

    @Test
    fun `track title larger than subtitle`() {
        assertTrue(
            TypographyPolicy.isLargerSp(
                TypographyPolicy.trackTitleBaseSp(),
                TypographyPolicy.trackSubtitleBaseSp(),
            ),
        )
        assertTrue(!TypographyPolicy.isLargerSp(12f, 16f))
    }
}
