package com.lucasdss.ftpmusic.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.CaptionFontPreset
import com.lucasdss.ftpmusic.app.ui.PrimaryWeightBias
import com.lucasdss.ftpmusic.app.ui.TypographyPrefs
import com.lucasdss.ftpmusic.app.ui.UiDensityPreset
import com.lucasdss.ftpmusic.app.ui.UiFontPreset
import com.lucasdss.ftpmusic.app.ui.albumCardWidth
import com.lucasdss.ftpmusic.app.ui.components.DetailBackButton
import com.lucasdss.ftpmusic.app.ui.iconMedium
import com.lucasdss.ftpmusic.app.ui.primaryTextWeight
import com.lucasdss.ftpmusic.app.ui.spacingL
import com.lucasdss.ftpmusic.app.ui.spacingM
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.textBodyM
import com.lucasdss.ftpmusic.app.ui.textHeadingL
import com.lucasdss.ftpmusic.app.ui.textHeadingM
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelL
import com.lucasdss.ftpmusic.app.ui.textLabelM

/**
 * Nested Settings page for UI density + typography fine-tune (ADR-0103).
 */
@Composable
fun TypographySettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs = state.typographyPrefs

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .testTag("settings_typography_screen"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailBackButton(onBack = onBack, inset = false)
            Text(
                "Text & display size",
                color = Color.White,
                fontSize = textHeadingL(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            SectionLabel("DISPLAY SIZE")
            SectionCard(Modifier.testTag("settings_typography_section")) {
                Column(Modifier.padding(horizontal = spacingL(), vertical = spacingM())) {
                    Text(
                        "Scales text, icons, and cover art across the app",
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                    )
                    Spacer(Modifier.height(spacingM()))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("settings_typography_density_row"),
                        horizontalArrangement = Arrangement.spacedBy(spacingS()),
                    ) {
                        UiDensityPreset.entries.forEach { preset ->
                            DensityChip(
                                label = preset.label,
                                selected = prefs.density == preset,
                                onClick = { viewModel.setDensity(preset) },
                                testTag = "settings_typography_density_${preset.name.lowercase()}",
                            )
                        }
                    }
                    Spacer(Modifier.height(spacingM()))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(albumCardWidth().times(0.45f))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E1E))
                                .testTag("settings_typography_preview_art"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Album,
                                contentDescription = null,
                                tint = BrandTeal,
                                modifier = Modifier.size(iconMedium()),
                            )
                        }
                        Spacer(Modifier.width(spacingM()))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Heading sample",
                                color = Color.White,
                                fontSize = textHeadingM(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("settings_typography_preview_heading"),
                            )
                            Text(
                                "Body sample — track and paragraph text",
                                color = Color.White,
                                fontSize = textBodyM(),
                                fontWeight = primaryTextWeight(),
                                modifier = Modifier.testTag("settings_typography_preview_body"),
                            )
                            Text(
                                "Label sample — meta and captions",
                                color = Color(0xFF888888),
                                fontSize = textLabelM(),
                                modifier = Modifier.testTag("settings_typography_preview_label"),
                            )
                        }
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier
                                .size(iconMedium())
                                .testTag("settings_typography_preview_icon"),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("FINE TUNE")
            SectionCard {
                TypographyScaleSlider(
                    label = "Heading size",
                    scale = prefs.headingScale,
                    testTag = "settings_typography_heading_slider",
                    onScale = { viewModel.setHeadingScale(it) },
                )
                SectionDivider()
                TypographyScaleSlider(
                    label = "Body size",
                    scale = prefs.bodyScale,
                    testTag = "settings_typography_body_slider",
                    onScale = { viewModel.setBodyScale(it) },
                )
                SectionDivider()
                TypographyScaleSlider(
                    label = "Label size",
                    scale = prefs.labelScale,
                    testTag = "settings_typography_label_slider",
                    onScale = { viewModel.setLabelScale(it) },
                )
                SectionDivider()
                Text(
                    "UI font",
                    color = Color.White,
                    fontSize = textHeadingS(),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                )
                UiFontPreset.entries.forEach { preset ->
                    OverwriteBehaviorOption(
                        label = preset.name,
                        subtitle = when (preset) {
                            UiFontPreset.Outfit -> "Default brand UI face"
                            UiFontPreset.Inter -> "Clean geometric sans"
                            UiFontPreset.System -> "Device default sans-serif"
                        },
                        selected = prefs.uiFont == preset,
                        onClick = { viewModel.setUiFont(preset) },
                    )
                }
                Text(
                    "Caption font",
                    color = Color.White,
                    fontSize = textHeadingS(),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                )
                CaptionFontPreset.entries.forEach { preset ->
                    OverwriteBehaviorOption(
                        label = preset.name,
                        subtitle = when (preset) {
                            CaptionFontPreset.Inter -> "Default time / meta face"
                            CaptionFontPreset.Outfit -> "Match UI face"
                            CaptionFontPreset.System -> "Device default sans-serif"
                        },
                        selected = prefs.captionFont == preset,
                        onClick = { viewModel.setCaptionFont(preset) },
                    )
                }
                Text(
                    "Primary weight",
                    color = Color.White,
                    fontSize = textHeadingS(),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                )
                PrimaryWeightBias.entries.forEach { bias ->
                    OverwriteBehaviorOption(
                        label = bias.name,
                        subtitle = when (bias) {
                            PrimaryWeightBias.Regular -> "Lighter track titles"
                            PrimaryWeightBias.Medium -> "Default track title weight"
                            PrimaryWeightBias.Bold -> "Heavier track titles"
                        },
                        selected = prefs.weightBias == bias,
                        onClick = { viewModel.setWeightBias(bias) },
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { viewModel.resetTypographyPrefs() }
                        .padding(horizontal = spacingL(), vertical = spacingM())
                        .testTag("settings_typography_reset"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Reset typography defaults",
                        color = BrandTeal,
                        fontSize = textHeadingS(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DensityChip(label: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
    Text(
        text = label,
        color = if (selected) Color.Black else Color.White,
        fontSize = textLabelL(),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) BrandTeal else Color(0xFF252538))
            .clickable(onClick = onClick)
            .padding(horizontal = spacingM(), vertical = spacingS())
            .testTag(testTag),
    )
}

@Composable
private fun TypographyScaleSlider(label: String, scale: Float, testTag: String, onScale: (Float) -> Unit) {
    var local by remember(scale) { mutableStateOf(scale) }
    val percent = (local * 100f).toInt()
    Column(Modifier.padding(horizontal = spacingL(), vertical = spacingS())) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = textHeadingS(),
                modifier = Modifier.weight(1f),
            )
            Text(
                "$percent%",
                color = Color(0xFF888888),
                fontSize = textLabelM(),
            )
        }
        Slider(
            value = local,
            onValueChange = { local = TypographyPrefs.clampScale(it) },
            onValueChangeFinished = { onScale(local) },
            valueRange = TypographyPrefs.SCALE_MIN..TypographyPrefs.SCALE_MAX,
            steps = (
                ((TypographyPrefs.SCALE_MAX - TypographyPrefs.SCALE_MIN) / TypographyPrefs.SCALE_STEP).toInt() - 1
                ).coerceAtLeast(0),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = BrandTeal,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
        )
    }
}
