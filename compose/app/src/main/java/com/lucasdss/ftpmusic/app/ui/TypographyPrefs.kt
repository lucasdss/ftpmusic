package com.lucasdss.ftpmusic.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight

/**
 * User typography preferences (ADR-0099 / ADR-0103). Role scales multiply
 * width-adaptive [asp] / [buildFtpTypography] bases; [UiDensityPreset] also
 * scales [adp] (icons/art/spacing). System fontScale still applies via `.sp`.
 */
enum class TextRole {
    Heading,
    Body,
    Label,
}

/** Master UI density ladder — Tiny…Bigger (ADR-0103). */
enum class UiDensityPreset {
    Tiny,
    Small,
    Medium,
    Big,
    Bigger,
    ;

    /** Multiplier for [adp] / [asp] / M3 sizes (on top of width factor). */
    val scale: Float
        get() = when (this) {
            Tiny -> 0.80f
            Small -> 0.90f
            Medium -> 1.0f
            Big -> 1.15f
            Bigger -> 1.30f
        }

    val label: String
        get() = when (this) {
            Tiny -> "Tiny"
            Small -> "Small"
            Medium -> "Medium"
            Big -> "Big"
            Bigger -> "Bigger"
        }

    companion object {
        fun fromStorage(raw: String?): UiDensityPreset =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Medium
    }
}

enum class UiFontPreset {
    Outfit,
    Inter,
    System,
    ;

    companion object {
        fun fromStorage(raw: String?): UiFontPreset =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Outfit
    }
}

enum class CaptionFontPreset {
    Inter,
    Outfit,
    System,
    ;

    companion object {
        fun fromStorage(raw: String?): CaptionFontPreset =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Inter
    }
}

enum class PrimaryWeightBias {
    Regular,
    Medium,
    Bold,
    ;

    fun toFontWeight(): FontWeight = when (this) {
        Regular -> FontWeight.Normal
        Medium -> FontWeight.Medium
        Bold -> FontWeight.Bold
    }

    companion object {
        fun fromStorage(raw: String?): PrimaryWeightBias =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Medium
    }
}

data class TypographyPrefs(
    val density: UiDensityPreset = UiDensityPreset.Medium,
    val headingScale: Float = DEFAULT_SCALE,
    val bodyScale: Float = DEFAULT_SCALE,
    val labelScale: Float = DEFAULT_SCALE,
    val uiFont: UiFontPreset = UiFontPreset.Outfit,
    val captionFont: CaptionFontPreset = CaptionFontPreset.Inter,
    val weightBias: PrimaryWeightBias = PrimaryWeightBias.Medium,
) {
    fun scaleFor(role: TextRole): Float = when (role) {
        TextRole.Heading -> headingScale
        TextRole.Body -> bodyScale
        TextRole.Label -> labelScale
    }

    fun clamped(): TypographyPrefs = copy(
        headingScale = clampScale(headingScale),
        bodyScale = clampScale(bodyScale),
        labelScale = clampScale(labelScale),
    )

    companion object {
        const val DEFAULT_SCALE = 1.0f
        const val SCALE_MIN = 0.85f
        const val SCALE_MAX = 1.30f
        const val SCALE_STEP = 0.05f

        val DEFAULT = TypographyPrefs()

        fun clampScale(value: Float): Float {
            val clamped = value.coerceIn(SCALE_MIN, SCALE_MAX)
            val nearest = SCALE_MIN +
                kotlin.math.round((clamped - SCALE_MIN) / SCALE_STEP) * SCALE_STEP
            return (kotlin.math.round(nearest.coerceIn(SCALE_MIN, SCALE_MAX) * 100f) / 100f)
        }

        fun parseScale(raw: String?): Float = clampScale(raw?.toFloatOrNull() ?: DEFAULT_SCALE)
    }
}

val LocalTypographyPrefs = staticCompositionLocalOf { TypographyPrefs.DEFAULT }

/** Track / primary title weight driven by Settings weight bias. */
@Composable
fun primaryTextWeight(): FontWeight = LocalTypographyPrefs.current.weightBias.toFontWeight()
