package com.lucasdss.ftpmusic.app.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material3 typography wired to Outfit (UI) + Inter (time/meta) and adaptive
 * [textMicro]…[textDisplay] tokens (ADR-0057).
 */
@Composable
fun ftpTypography(): Typography {
    val outfit = outfitFontFamily()
    val inter = interFontFamily()
    return Typography(
        displayLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = textDisplay(),
            lineHeight = 32.sp,
            color = Foreground,
        ),
        displayMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = textHeadingL(),
            lineHeight = 28.sp,
            color = Foreground,
        ),
        displaySmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = textHeadingM(),
            lineHeight = 24.sp,
            color = Foreground,
        ),
        headlineLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = textHeadingL(),
            lineHeight = 28.sp,
            color = Foreground,
        ),
        headlineMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = textHeadingM(),
            lineHeight = 24.sp,
            color = Foreground,
        ),
        headlineSmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = textHeadingS(),
            lineHeight = 22.sp,
            color = Foreground,
        ),
        // titleMedium ≈ track primary (textHeadingS / Medium)
        titleLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = textHeadingM(),
            lineHeight = 24.sp,
            color = Foreground,
        ),
        titleMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Medium,
            fontSize = textHeadingS(),
            lineHeight = 22.sp,
            color = Foreground,
        ),
        titleSmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Medium,
            fontSize = textBodyL(),
            lineHeight = 20.sp,
            color = Foreground,
        ),
        bodyLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = textBodyL(),
            lineHeight = 22.sp,
            color = Foreground,
        ),
        bodyMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = textBodyM(),
            lineHeight = 20.sp,
            color = Foreground,
        ),
        bodySmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = textLabelL(),
            lineHeight = 18.sp,
            color = Muted,
        ),
        labelLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = textLabelL(),
            lineHeight = 18.sp,
            color = Foreground,
        ),
        labelMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Medium,
            fontSize = textLabelM(),
            lineHeight = 16.sp,
            color = Foreground,
        ),
        // Inter for micro time / meta captions
        labelSmall = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Normal,
            fontSize = textMicro(),
            lineHeight = 14.sp,
            color = Muted,
        ),
    )
}
