package com.lucasdss.ftpmusic.app.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Material3 typography wired to curated UI + caption faces and adaptive
 * tokens (ADR-0057 / ADR-0058 / ADR-0099).
 *
 * **No baked [TextStyle.color]** — Unspecified so LocalContentColor wins
 * (nav selected teal, buttons, etc.).
 */
@Composable
fun ftpTypography(): Typography {
    val prefs = LocalTypographyPrefs.current
    val ui = resolveUiFontFamily(prefs.uiFont)
    val caption = resolveCaptionFontFamily(prefs.captionFont)
    val widthFactor = AdaptiveScale.factor()
    return remember(ui, caption, widthFactor, prefs) {
        buildFtpTypography(ui, caption, widthFactor, prefs)
    }
}

/**
 * Pure builder for tests — [widthFactor] mirrors [AdaptiveScale.factor] /
 * [AdaptiveScale.widthFactor]. Role scales from [prefs] multiply sizes.
 * Line heights use the same factor as [asp] so ratios stay stable.
 */
fun buildFtpTypography(
    outfit: FontFamily,
    inter: FontFamily,
    widthFactor: Float,
    prefs: TypographyPrefs = TypographyPrefs.DEFAULT,
): Typography {
    fun sz(base: Float, role: TextRole): TextUnit = (base * widthFactor * prefs.scaleFor(role)).sp

    fun lh(fontBase: Float, role: TextRole): TextUnit =
        (fontBase * TypographyPolicy.LINE_HEIGHT_MULT * widthFactor * prefs.scaleFor(role)).sp

    val primaryWeight = prefs.weightBias.toFontWeight()

    return Typography(
        displayLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = sz(TypographyPolicy.DISPLAY_BASE_SP, TextRole.Heading),
            lineHeight = lh(TypographyPolicy.DISPLAY_BASE_SP, TextRole.Heading),
        ),
        displayMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = sz(20f, TextRole.Heading),
            lineHeight = lh(20f, TextRole.Heading),
        ),
        displaySmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = sz(TypographyPolicy.SECTION_TITLE_BASE_SP, TextRole.Heading),
            lineHeight = lh(TypographyPolicy.SECTION_TITLE_BASE_SP, TextRole.Heading),
        ),
        headlineLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = sz(20f, TextRole.Heading),
            lineHeight = lh(20f, TextRole.Heading),
        ),
        headlineMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = sz(TypographyPolicy.SECTION_TITLE_BASE_SP, TextRole.Heading),
            lineHeight = lh(TypographyPolicy.SECTION_TITLE_BASE_SP, TextRole.Heading),
        ),
        headlineSmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = sz(TypographyPolicy.TRACK_TITLE_BASE_SP, TextRole.Heading),
            lineHeight = lh(TypographyPolicy.TRACK_TITLE_BASE_SP, TextRole.Heading),
        ),
        titleLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = sz(TypographyPolicy.SECTION_TITLE_BASE_SP, TextRole.Heading),
            lineHeight = lh(TypographyPolicy.SECTION_TITLE_BASE_SP, TextRole.Heading),
        ),
        titleMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = primaryWeight,
            fontSize = sz(TypographyPolicy.TRACK_TITLE_BASE_SP, TextRole.Heading),
            lineHeight = lh(TypographyPolicy.TRACK_TITLE_BASE_SP, TextRole.Heading),
        ),
        titleSmall = TextStyle(
            fontFamily = outfit,
            fontWeight = primaryWeight,
            fontSize = sz(15f, TextRole.Body),
            lineHeight = lh(15f, TextRole.Body),
        ),
        bodyLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = sz(15f, TextRole.Body),
            lineHeight = lh(15f, TextRole.Body),
        ),
        bodyMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = sz(14f, TextRole.Body),
            lineHeight = lh(14f, TextRole.Body),
        ),
        bodySmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = sz(13f, TextRole.Body),
            lineHeight = lh(13f, TextRole.Body),
        ),
        labelLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = sz(13f, TextRole.Label),
            lineHeight = lh(13f, TextRole.Label),
        ),
        labelMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = primaryWeight,
            fontSize = sz(TypographyPolicy.TRACK_SUBTITLE_BASE_SP, TextRole.Label),
            lineHeight = lh(TypographyPolicy.TRACK_SUBTITLE_BASE_SP, TextRole.Label),
        ),
        labelSmall = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Normal,
            fontSize = sz(10f, TextRole.Label),
            lineHeight = lh(10f, TextRole.Label),
        ),
    )
}

/** Theme styles must not bake a color — contentColor / explicit call-site color wins. */
fun textStyleOmitsBakedColor(style: TextStyle): Boolean = style.color == Color.Unspecified
