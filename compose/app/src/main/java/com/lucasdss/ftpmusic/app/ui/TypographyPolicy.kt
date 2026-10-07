package com.lucasdss.ftpmusic.app.ui

/**
 * Typography role policy (ADR-0057). Pure helpers for tests + call sites.
 *
 * Track list primary uses [trackTitleBaseSp] via [textHeadingS] (asp).
 * Outfit = UI; Inter = time/meta captions.
 */
object TypographyPolicy {
    /** Base sp for list/track primary title before width scale. */
    const val TRACK_TITLE_BASE_SP = 16f

    /** Base sp for section titles (textHeadingM). */
    const val SECTION_TITLE_BASE_SP = 18f

    /** Base sp for display/hero (textDisplay). */
    const val DISPLAY_BASE_SP = 24f

    /** Base sp for track subtitle / meta (textLabelM). */
    const val TRACK_SUBTITLE_BASE_SP = 12f

    const val UI_FONT = "Outfit"
    const val CAPTION_FONT = "Inter"

    /** User role-scale clamps (ADR-0099). */
    const val USER_SCALE_MIN = TypographyPrefs.SCALE_MIN
    const val USER_SCALE_MAX = TypographyPrefs.SCALE_MAX
    const val USER_SCALE_STEP = TypographyPrefs.SCALE_STEP

    /** lineHeight = fontBase * LINE_HEIGHT_MULT * widthFactor (pairs with asp). */
    const val LINE_HEIGHT_MULT = 1.35f

    fun trackTitleBaseSp(): Float = TRACK_TITLE_BASE_SP

    fun sectionTitleBaseSp(): Float = SECTION_TITLE_BASE_SP

    fun displayBaseSp(): Float = DISPLAY_BASE_SP

    fun trackSubtitleBaseSp(): Float = TRACK_SUBTITLE_BASE_SP

    fun lineHeightMult(): Float = LINE_HEIGHT_MULT

    fun scaledLineHeightBase(fontBaseSp: Float): Float = fontBaseSp * LINE_HEIGHT_MULT

    fun uiFontFamilyName(): String = UI_FONT

    fun captionFontFamilyName(): String = CAPTION_FONT

    /** Compare two base sizes (used by tests for hierarchy checks). */
    fun isLargerSp(a: Float, b: Float): Boolean = a > b

    /** ADR-0058: theme TextStyles must not set color. */
    fun themeStylesOmitColor(): Boolean = true
}
