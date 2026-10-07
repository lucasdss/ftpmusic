package com.lucasdss.ftpmusic.app.ui

import androidx.compose.ui.text.font.FontFamily
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

    @Test
    fun `lineHeight scales with font base and width factor`() {
        assertEquals(1.35f, TypographyPolicy.lineHeightMult(), 0.001f)
        assertEquals(
            TypographyPolicy.TRACK_TITLE_BASE_SP * TypographyPolicy.LINE_HEIGHT_MULT,
            TypographyPolicy.scaledLineHeightBase(TypographyPolicy.TRACK_TITLE_BASE_SP),
            0.001f,
        )
        assertTrue(TypographyPolicy.themeStylesOmitColor())
    }

    @Test
    fun `buildFtpTypography omits baked colors and scales lineHeight`() {
        val factor = 1.1f
        val typography = buildFtpTypography(FontFamily.Default, FontFamily.Default, factor)
        val roles = listOf(
            typography.displayLarge,
            typography.titleMedium,
            typography.bodyMedium,
            typography.labelMedium,
            typography.labelSmall,
        )
        roles.forEach { style ->
            assertTrue(textStyleOmitsBakedColor(style))
            assertEquals(
                TypographyPolicy.LINE_HEIGHT_MULT,
                style.lineHeight.value / style.fontSize.value,
                0.02f,
            )
        }
        assertEquals(
            TypographyPolicy.TRACK_TITLE_BASE_SP * factor,
            typography.titleMedium.fontSize.value,
            0.001f,
        )
    }

    @Test
    fun `user scale clamps snap to step`() {
        assertEquals(0.85f, TypographyPrefs.clampScale(0.5f), 0.001f)
        assertEquals(1.30f, TypographyPrefs.clampScale(2f), 0.001f)
        assertEquals(1.0f, TypographyPrefs.clampScale(1.02f), 0.001f)
        assertEquals(1.05f, TypographyPrefs.clampScale(1.04f), 0.001f)
        assertEquals(TypographyPolicy.USER_SCALE_MIN, TypographyPrefs.SCALE_MIN, 0.001f)
        assertEquals(TypographyPolicy.USER_SCALE_MAX, TypographyPrefs.SCALE_MAX, 0.001f)
    }

    @Test
    fun `buildFtpTypography applies role scales and weight bias`() {
        val prefs = TypographyPrefs(
            headingScale = 1.2f,
            bodyScale = 0.9f,
            labelScale = 1.1f,
            weightBias = PrimaryWeightBias.Bold,
        )
        val factor = 1.0f
        val typography = buildFtpTypography(FontFamily.Default, FontFamily.Default, factor, prefs)
        assertEquals(
            TypographyPolicy.TRACK_TITLE_BASE_SP * factor * 1.2f,
            typography.titleMedium.fontSize.value,
            0.001f,
        )
        assertEquals(
            14f * factor * 0.9f,
            typography.bodyMedium.fontSize.value,
            0.001f,
        )
        assertEquals(
            TypographyPolicy.TRACK_SUBTITLE_BASE_SP * factor * 1.1f,
            typography.labelMedium.fontSize.value,
            0.001f,
        )
        assertEquals(
            PrimaryWeightBias.Bold.toFontWeight(),
            typography.titleMedium.fontWeight,
        )
    }

    @Test
    fun `font preset storage parsers default safely`() {
        assertEquals(UiFontPreset.Outfit, UiFontPreset.fromStorage(null))
        assertEquals(UiFontPreset.Inter, UiFontPreset.fromStorage("inter"))
        assertEquals(UiFontPreset.Outfit, UiFontPreset.fromStorage("nope"))
        assertEquals(CaptionFontPreset.Inter, CaptionFontPreset.fromStorage(null))
        assertEquals(CaptionFontPreset.System, CaptionFontPreset.fromStorage("System"))
        assertEquals(PrimaryWeightBias.Medium, PrimaryWeightBias.fromStorage(null))
        assertEquals(PrimaryWeightBias.Regular, PrimaryWeightBias.fromStorage("regular"))
    }
}
