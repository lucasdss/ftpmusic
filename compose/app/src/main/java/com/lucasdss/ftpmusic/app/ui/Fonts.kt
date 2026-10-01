package com.lucasdss.ftpmusic.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

/**
 * Design system typography (ADR-0057):
 * Outfit for UI (headings/body/labels), Inter for time/meta captions.
 */
@Composable
fun outfitFontFamily(): FontFamily = remember {
    val provider = googleFontsProvider()
    FontFamily(
        Font(GoogleFont(TypographyPolicy.UI_FONT), provider, FontWeight.Bold),
        Font(GoogleFont(TypographyPolicy.UI_FONT), provider, FontWeight.SemiBold),
        Font(GoogleFont(TypographyPolicy.UI_FONT), provider, FontWeight.Medium),
        Font(GoogleFont(TypographyPolicy.UI_FONT), provider, FontWeight.Normal),
    )
}

@Composable
fun interFontFamily(): FontFamily = remember {
    val provider = googleFontsProvider()
    FontFamily(
        Font(GoogleFont(TypographyPolicy.CAPTION_FONT), provider, FontWeight.Medium),
        Font(GoogleFont(TypographyPolicy.CAPTION_FONT), provider, FontWeight.Normal),
    )
}

private fun googleFontsProvider() = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.lucasdss.ftpmusic.app.R.array.com_google_android_gms_fonts_certs,
)
