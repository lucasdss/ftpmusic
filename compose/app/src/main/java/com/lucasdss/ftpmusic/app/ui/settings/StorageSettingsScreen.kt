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
fun StorageSettingsScreen(
    onBack: () -> Unit = {},
    onDownloads: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsPageScaffold(title = stringResource(R.string.settings_page_storage), onBack = onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            // ═══ Cache section ═══
            SectionLabel("Cache")
            SectionCard {
                // Storage breakdown
                SectionRow("Auto-cache", formatBytes(state.autoCacheBytes))
                SectionDivider()
                SectionRow("Downloads", formatBytes(state.downloadBytes))
                SectionDivider()
                SectionRow("Total", formatBytes(state.autoCacheBytes + state.downloadBytes))
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(
                Modifier
                    .clickable { onDownloads() }
                    .testTag("settings_downloads_manage"),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.DownloadDone, null, tint = BrandTeal, modifier = Modifier.size(iconSmall()))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Manage downloads",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Browse, play, or remove pinned offline tracks",
                            color = Color(0xFF888888),
                            fontSize = textLabelM(),
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = NavUnselected, modifier = Modifier.size(iconSmall()))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Unified audio cache size (shared by streaming, auto-cache; downloads are pinned)
            SectionLabel("Audio cache size")
            var quota by remember(state.audioCacheQuotaMb) { mutableStateOf(state.audioCacheQuotaMb.toFloat()) }
            SectionCard {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingS()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${(quota.toInt() / 1024f).let {
                            if (it >= 1f) "${"%.1f".format(it)} GB" else "${quota.toInt()} MB"
                        }}",
                        color = Color.White,
                        fontSize = textHeadingS(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Slider(
                    value = quota,
                    onValueChange = { quota = (it / 100).toInt() * 100f },
                    onValueChangeFinished = { viewModel.setAudioCacheQuota(quota.toInt()) },
                    valueRange = 500f..10240f,
                    steps = 0,
                    modifier = Modifier.padding(horizontal = spacingM()),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = BrandTeal,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                )
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(12.dp))

            // Cover Art quota
            SectionLabel("Cover Art quota")
            var artQuota by remember(state.coverArtQuotaMb) { mutableStateOf(state.coverArtQuotaMb.toFloat()) }
            SectionCard {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingS()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${artQuota.toInt()} MB",
                        color = Color.White,
                        fontSize = textHeadingS(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Slider(
                    value = artQuota,
                    onValueChange = { artQuota = (it / 50).toInt() * 50f },
                    onValueChangeFinished = { viewModel.setCoverArtQuota(artQuota.toInt()) },
                    valueRange = 50f..1000f,
                    steps = 0,
                    modifier = Modifier.padding(horizontal = spacingM()),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = BrandTeal,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                )
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(12.dp))

            // Clear cache buttons with confirmation
            var showClearCacheConfirm by remember { mutableStateOf(false) }
            var showClearDownloadsConfirm by remember { mutableStateOf(false) }
            var showClearCoverArtConfirm by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingS())) {
                SectionCard(Modifier.weight(1f).clickable { showClearCacheConfirm = true }) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Storage, null, tint = BrandTeal, modifier = Modifier.size(iconSmall()))
                        Spacer(Modifier.height(4.dp))
                        Text("Clear cache", color = Color.White, fontSize = textLabelL())
                        Text(formatBytes(state.autoCacheBytes), color = Color(0xFF888888), fontSize = textLabelS())
                    }
                }
                SectionCard(Modifier.weight(1f).clickable { showClearDownloadsConfirm = true }) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(iconSmall()),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Clear downloads", color = Color.White, fontSize = textLabelL())
                        Text(formatBytes(state.downloadBytes), color = Color(0xFF888888), fontSize = textLabelS())
                    }
                }
            }
            Spacer(Modifier.height(spacingS()))
            SectionCard(
                Modifier
                    .fillMaxWidth()
                    .clickable { showClearCoverArtConfirm = true },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacingL(), vertical = spacingM()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Image,
                        null,
                        tint = BrandTeal,
                        modifier = Modifier.size(iconSmall()),
                    )
                    Spacer(Modifier.width(spacingM()))
                    Column(Modifier.weight(1f)) {
                        Text("Clear cover art", color = Color.White, fontSize = textLabelL())
                        Text(
                            "Album & artist art cache · ${formatBytes(state.coverArtCacheBytes)}",
                            color = Color(0xFF888888),
                            fontSize = textLabelS(),
                        )
                    }
                }
            }

            // Confirm dialogs
            if (showClearCacheConfirm) {
                ConfirmationSheet(
                    title = "Clear Auto-Cache",
                    message = "This will remove ${formatBytes(
                        state.autoCacheBytes,
                    )} of automatically cached music. Downloaded tracks will not be affected.",
                    confirmLabel = "Clear ${formatBytes(state.autoCacheBytes)}",
                    onConfirm = {
                        viewModel.clearAutoCache()
                        showClearCacheConfirm = false
                    },
                    onDismiss = { showClearCacheConfirm = false },
                )
            }
            if (showClearDownloadsConfirm) {
                ConfirmationSheet(
                    title = "Clear Downloads",
                    message = "This will permanently remove ${formatBytes(
                        state.downloadBytes,
                    )} of downloaded music. Downloaded tracks will need to be re-downloaded.",
                    confirmLabel = "Clear ${formatBytes(state.downloadBytes)}",
                    onConfirm = {
                        viewModel.clearDownloads()
                        showClearDownloadsConfirm = false
                    },
                    onDismiss = { showClearDownloadsConfirm = false },
                )
            }
            if (showClearCoverArtConfirm) {
                ConfirmationSheet(
                    title = "Clear Cover Art",
                    message = buildString {
                        append("This will remove ")
                        append(formatBytes(state.coverArtCacheBytes))
                        append(" of cached album and artist art. ")
                        append("Audio cache and downloads are not affected. ")
                        append("Art will re-download as you browse.")
                    },
                    confirmLabel = "Clear ${formatBytes(state.coverArtCacheBytes)}",
                    onConfirm = {
                        viewModel.clearCoverArtCache()
                        showClearCoverArtConfirm = false
                    },
                    onDismiss = { showClearCoverArtConfirm = false },
                )
            }
        }
    }
}
