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
 * Material3 typography wired to Outfit (UI) + Inter (time/meta) and adaptive
 * tokens (ADR-0057 / ADR-0058).
 *
 * **No baked [TextStyle.color]** — Unspecified so LocalContentColor wins
 * (nav selected teal, buttons, etc.).
 */
@Composable
fun ftpTypography(): Typography {
    val outfit = outfitFontFamily()
    val inter = interFontFamily()
    val widthFactor = AdaptiveScale.factor()
    return remember(outfit, inter, widthFactor) {
        buildFtpTypography(outfit, inter, widthFactor)
    }
}

/**
 * Pure builder for tests — [widthFactor] mirrors [AdaptiveScale.factor] /
 * [AdaptiveScale.widthFactor].
 * Line heights use the same factor as [asp] so ratios stay stable.
 */
fun buildFtpTypography(outfit: FontFamily, inter: FontFamily, widthFactor: Float): Typography {
    fun sz(base: Float): TextUnit = (base * widthFactor).sp
    fun lh(fontBase: Float): TextUnit = (fontBase * TypographyPolicy.LINE_HEIGHT_MULT * widthFactor).sp

    return Typography(
        displayLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = sz(TypographyPolicy.DISPLAY_BASE_SP),
            lineHeight = lh(TypographyPolicy.DISPLAY_BASE_SP),
        ),
        displayMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = sz(20f),
            lineHeight = lh(20f),
        ),
        displaySmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = sz(TypographyPolicy.SECTION_TITLE_BASE_SP),
            lineHeight = lh(TypographyPolicy.SECTION_TITLE_BASE_SP),
        ),
        headlineLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = sz(20f),
            lineHeight = lh(20f),
        ),
        headlineMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = sz(TypographyPolicy.SECTION_TITLE_BASE_SP),
            lineHeight = lh(TypographyPolicy.SECTION_TITLE_BASE_SP),
        ),
        headlineSmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = sz(TypographyPolicy.TRACK_TITLE_BASE_SP),
            lineHeight = lh(TypographyPolicy.TRACK_TITLE_BASE_SP),
        ),
        titleLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = sz(TypographyPolicy.SECTION_TITLE_BASE_SP),
            lineHeight = lh(TypographyPolicy.SECTION_TITLE_BASE_SP),
        ),
        titleMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Medium,
            fontSize = sz(TypographyPolicy.TRACK_TITLE_BASE_SP),
            lineHeight = lh(TypographyPolicy.TRACK_TITLE_BASE_SP),
        ),
        titleSmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Medium,
            fontSize = sz(15f),
            lineHeight = lh(15f),
        ),
        bodyLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = sz(15f),
            lineHeight = lh(15f),
        ),
        bodyMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = sz(14f),
            lineHeight = lh(14f),
        ),
        bodySmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = sz(13f),
            lineHeight = lh(13f),
        ),
        labelLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = sz(13f),
            lineHeight = lh(13f),
        ),
        labelMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Medium,
            fontSize = sz(TypographyPolicy.TRACK_SUBTITLE_BASE_SP),
            lineHeight = lh(TypographyPolicy.TRACK_SUBTITLE_BASE_SP),
        ),
        labelSmall = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Normal,
            fontSize = sz(10f),
            lineHeight = lh(10f),
        ),
    )
}

/** Theme styles must not bake a color — contentColor / explicit call-site color wins. */
fun textStyleOmitsBakedColor(style: TextStyle): Boolean = style.color == Color.Unspecified
