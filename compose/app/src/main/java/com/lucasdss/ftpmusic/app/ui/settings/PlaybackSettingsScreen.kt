package com.lucasdss.ftpmusic.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.data.cache.CellularMediaPolicy
import com.lucasdss.ftpmusic.app.playback.BatteryOptimizationHelper
import com.lucasdss.ftpmusic.app.playback.DefaultMusicRoleHelper
import com.lucasdss.ftpmusic.app.ui.BrandBg
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Muted
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.adp
import com.lucasdss.ftpmusic.app.ui.components.ConfirmationSheet
import com.lucasdss.ftpmusic.app.ui.components.MetricRow
import com.lucasdss.ftpmusic.app.ui.components.OverwriteBehaviorOption
import com.lucasdss.ftpmusic.app.ui.components.SectionCard
import com.lucasdss.ftpmusic.app.ui.components.SectionDivider
import com.lucasdss.ftpmusic.app.ui.components.SectionLabel
import com.lucasdss.ftpmusic.app.ui.components.SectionRow
import com.lucasdss.ftpmusic.app.ui.components.SectionToggleRow
import com.lucasdss.ftpmusic.app.ui.components.SettingsPageScaffold
import com.lucasdss.ftpmusic.app.ui.components.SystemStatusRow
import com.lucasdss.ftpmusic.app.ui.components.UpdateCheckRow
import com.lucasdss.ftpmusic.app.ui.components.formatBytes
import com.lucasdss.ftpmusic.app.ui.components.formatDurationMs
import com.lucasdss.ftpmusic.app.ui.components.openPlayStoreListing
import com.lucasdss.ftpmusic.app.ui.components.settingsTextFieldColors
import com.lucasdss.ftpmusic.app.ui.components.timeAgo
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChip
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChipRow
import com.lucasdss.ftpmusic.app.ui.cornerL
import com.lucasdss.ftpmusic.app.ui.cornerS
import com.lucasdss.ftpmusic.app.ui.iconSmall
import com.lucasdss.ftpmusic.app.ui.spacingL
import com.lucasdss.ftpmusic.app.ui.spacingM
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.spacingXL
import com.lucasdss.ftpmusic.app.ui.spacingXS
import com.lucasdss.ftpmusic.app.ui.textBodyM
import com.lucasdss.ftpmusic.app.ui.textHeadingL
import com.lucasdss.ftpmusic.app.ui.textHeadingM
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelL
import com.lucasdss.ftpmusic.app.ui.textLabelM
import com.lucasdss.ftpmusic.app.ui.textLabelS

@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsPageScaffold(title = stringResource(R.string.settings_page_playback), onBack = onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            // ═══ Queue Journal section ═══
            SectionLabel("Queue Journal")
            SectionCard {
                val journalCapOptions = (100..500 step 50).toList()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingM()),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("History Size", color = Color.White, fontSize = textHeadingS())
                    Text(
                        "${state.journalCap} entries",
                        color = Color(0xFF888888),
                        fontSize = textHeadingS(),
                    )
                }
                Slider(
                    value = state.journalCap.toFloat(),
                    onValueChange = { viewModel.setJournalCap((it / 50).toInt() * 50) },
                    valueRange = 100f..500f,
                    steps = 0,
                    modifier = Modifier.padding(horizontal = spacingM()),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = BrandTeal,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacingL()),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    journalCapOptions.forEach { cap ->
                        Text(
                            "$cap",
                            color = if (state.journalCap == cap) BrandTeal else NavUnselected,
                            fontSize = textLabelS(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Stores recently played sources (albums, playlists, mixes, Surprise Me) for Continuous Play",
                    color = Color(0xFF666666),
                    fontSize = textLabelS(),
                    modifier = Modifier.padding(horizontal = spacingL()),
                )
                Spacer(Modifier.height(4.dp))
                SectionToggleRow(
                    label = "Continuous Play",
                    subtitle = "Auto-append journal picks to context when queue runs out (also in Queue · Autoplay)",
                    checked = state.continuousPlayEnabled,
                    onToggle = { viewModel.setContinuousPlayEnabled(it) },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Queue Overwrite Behavior section ═══
            SectionLabel("Queue Overwrite")
            SectionCard {
                Text(
                    "When you start a new album/mix while tracks are in your queue:",
                    color = Color(0xFF888888),
                    fontSize = textLabelS(),
                    modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                )
                OverwriteBehaviorOption(
                    label = "Ask",
                    subtitle = "Show a dialog every time",
                    selected = state.overwriteBehavior == com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.ASK,
                    onClick = {
                        viewModel.setOverwriteBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.ASK)
                    },
                )
                OverwriteBehaviorOption(
                    label = "Clean",
                    subtitle = "Always clear the queue and play the new content",
                    selected = state.overwriteBehavior == com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.CLEAN,
                    onClick = {
                        viewModel.setOverwriteBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.CLEAN)
                    },
                )
                // PUSH pruned from Settings triad (ADR-0094) — stored "push" maps to ASK.
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Add to Queue placement (ADR-0135) ═══
            SectionLabel("Add to Queue")
            SectionCard {
                Text(
                    "Where new tracks go when you tap Add to Queue (Play Next is unchanged):",
                    color = Color(0xFF888888),
                    fontSize = textLabelS(),
                    modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                )
                OverwriteBehaviorOption(
                    label = "After your queued songs",
                    subtitle = "Like Apple Music & Spotify — before the rest of the album",
                    selected = state.addToQueuePlacement ==
                        com.lucasdss.ftpmusic.app.playback.AddToQueuePlacement.MANUAL_END,
                    onClick = {
                        viewModel.setAddToQueuePlacement(
                            com.lucasdss.ftpmusic.app.playback.AddToQueuePlacement.MANUAL_END,
                        )
                    },
                )
                OverwriteBehaviorOption(
                    label = "At the end of what’s playing",
                    subtitle = "Like YouTube Music — after the album or playlist",
                    selected = state.addToQueuePlacement ==
                        com.lucasdss.ftpmusic.app.playback.AddToQueuePlacement.SESSION_END,
                    onClick = {
                        viewModel.setAddToQueuePlacement(
                            com.lucasdss.ftpmusic.app.playback.AddToQueuePlacement.SESSION_END,
                        )
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Artist Play All order (ADR-0145) ═══
            SectionLabel("Artist Play All")
            SectionCard {
                Text(
                    "Order when you tap Play All on an artist. Default matches Apple Music “Shuffle Albums”. " +
                        "Within albums, tracks always play in track order (1…N), except Shuffle all songs.",
                    color = Color(0xFF888888),
                    fontSize = textLabelS(),
                    modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                )
                val playOrder = state.artistPlayOrder
                OverwriteBehaviorOption(
                    label = "Shuffle albums",
                    subtitle = "Random album order; tracks stay in album order",
                    selected = playOrder ==
                        com.lucasdss.ftpmusic.app.playback.ArtistPlayOrder.SHUFFLE_ALBUMS,
                    onClick = {
                        viewModel.setArtistPlayOrder(
                            com.lucasdss.ftpmusic.app.playback.ArtistPlayOrder.SHUFFLE_ALBUMS,
                        )
                    },
                )
                OverwriteBehaviorOption(
                    label = "Shuffle all songs",
                    subtitle = "Fully random track order across the catalogue",
                    selected = playOrder ==
                        com.lucasdss.ftpmusic.app.playback.ArtistPlayOrder.SHUFFLE_SONGS,
                    onClick = {
                        viewModel.setArtistPlayOrder(
                            com.lucasdss.ftpmusic.app.playback.ArtistPlayOrder.SHUFFLE_SONGS,
                        )
                    },
                )
                OverwriteBehaviorOption(
                    label = "Albums by year",
                    subtitle = "Chronological by release year; unknown years last",
                    selected = playOrder.usesYear,
                    onClick = {
                        if (!playOrder.usesYear) {
                            viewModel.setArtistPlayOrder(
                                com.lucasdss.ftpmusic.app.playback.ArtistPlayOrder.ALBUM_YEAR_ASC,
                            )
                        }
                    },
                )
                if (playOrder.usesYear) {
                    SegmentedChipRow {
                        SegmentedChip(
                            label = "Oldest first",
                            selected = playOrder.isAscending,
                            onClick = {
                                viewModel.setArtistPlayOrder(
                                    playOrder.withAscending(true),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SegmentedChip(
                            label = "Newest first",
                            selected = !playOrder.isAscending,
                            onClick = {
                                viewModel.setArtistPlayOrder(
                                    playOrder.withAscending(false),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SectionDivider()
                }
                OverwriteBehaviorOption(
                    label = "Albums by name",
                    subtitle = "Alphabetical by album title",
                    selected = playOrder.usesName,
                    onClick = {
                        if (!playOrder.usesName) {
                            viewModel.setArtistPlayOrder(
                                com.lucasdss.ftpmusic.app.playback.ArtistPlayOrder.ALBUM_NAME_ASC,
                            )
                        }
                    },
                )
                if (playOrder.usesName) {
                    SegmentedChipRow {
                        SegmentedChip(
                            label = "A–Z",
                            selected = playOrder.isAscending,
                            onClick = {
                                viewModel.setArtistPlayOrder(
                                    playOrder.withAscending(true),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SegmentedChip(
                            label = "Z–A",
                            selected = !playOrder.isAscending,
                            onClick = {
                                viewModel.setArtistPlayOrder(
                                    playOrder.withAscending(false),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SectionDivider()
                }
            }
        }
    }
}
