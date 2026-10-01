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

    fun trackTitleBaseSp(): Float = TRACK_TITLE_BASE_SP

    fun sectionTitleBaseSp(): Float = SECTION_TITLE_BASE_SP

    fun displayBaseSp(): Float = DISPLAY_BASE_SP

    fun trackSubtitleBaseSp(): Float = TRACK_SUBTITLE_BASE_SP

    fun uiFontFamilyName(): String = UI_FONT

    fun captionFontFamilyName(): String = CAPTION_FONT

    /** Compare two base sizes (used by tests for hierarchy checks). */
    fun isLargerSp(a: Float, b: Float): Boolean = a > b
}
