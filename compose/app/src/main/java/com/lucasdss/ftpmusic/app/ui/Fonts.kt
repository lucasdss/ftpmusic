package com.lucasdss.ftpmusic.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

/**
 * Design system typography per VISUAL-EFFECTS.md:
 * Outfit for headings/body, Inter for captions/monospace.
 */
@Composable
fun outfitFontFamily(): FontFamily = remember {
    val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = com.lucasdss.ftpmusic.app.R.array.com_google_android_gms_fonts_certs,
    )
    FontFamily(
        Font(GoogleFont("Outfit"), provider, FontWeight.Bold),
        Font(GoogleFont("Outfit"), provider, FontWeight.SemiBold),
        Font(GoogleFont("Outfit"), provider, FontWeight.Medium),
    )
}

@Composable
fun interFontFamily(): FontFamily = remember {
    val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = com.lucasdss.ftpmusic.app.R.array.com_google_android_gms_fonts_certs,
    )
    FontFamily(
        Font(GoogleFont("Inter"), provider, FontWeight.Normal),
    )
}
