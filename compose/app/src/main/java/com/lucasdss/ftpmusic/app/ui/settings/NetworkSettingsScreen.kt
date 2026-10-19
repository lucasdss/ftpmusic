package com.lucasdss.ftpmusic.app.ui.settings

import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.data.cache.CellularMediaPolicy
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.components.ConfirmationSheet
import com.lucasdss.ftpmusic.app.ui.components.SectionCard
import com.lucasdss.ftpmusic.app.ui.components.SectionDivider
import com.lucasdss.ftpmusic.app.ui.components.SectionLabel
import com.lucasdss.ftpmusic.app.ui.components.SectionToggleRow
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChip
import com.lucasdss.ftpmusic.app.ui.components.SettingsPageScaffold
import com.lucasdss.ftpmusic.app.ui.iconSmall
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelM

@Composable
fun NetworkSettingsScreen(
    onBack: () -> Unit = {},
    onResyncLibrary: () -> Unit = {},
    onRebuildMixes: () -> Unit = {},
    onCustomMixes: () -> Unit = {},
    scrollToAnchor: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showResyncCellularWarn by remember { mutableStateOf(false) }
    var showResyncCellularBlocked by remember { mutableStateOf(false) }

    SettingsPageScaffold(title = stringResource(R.string.settings_page_network), onBack = onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            SectionLabel(stringResource(R.string.settings_network_cellular))
            SectionCard(
                Modifier.settingsScrollAnchor(SettingsAnchors.CELLULAR, scrollToAnchor),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.settings_network_cellular),
                        color = Color.White,
                        fontSize = textHeadingS(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when (state.cellularMediaPolicy) {
                            CellularMediaPolicy.AUTO_CACHE ->
                                "Auto-cache albums and downloads on mobile data"

                            CellularMediaPolicy.MINIMAL ->
                                "Only queue and now-playing use mobile data"

                            CellularMediaPolicy.LOCAL_ONLY ->
                                "Use only already cached or downloaded content"
                        },
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingS()),
                    ) {
                        SegmentedChip(
                            label = stringResource(R.string.settings_network_chip_auto),
                            selected = state.cellularMediaPolicy == CellularMediaPolicy.AUTO_CACHE,
                            onClick = { viewModel.setCellularMediaPolicy(CellularMediaPolicy.AUTO_CACHE) },
                            modifier = Modifier.weight(1f),
                        )
                        SegmentedChip(
                            label = stringResource(R.string.settings_network_chip_minimal),
                            selected = state.cellularMediaPolicy == CellularMediaPolicy.MINIMAL,
                            onClick = { viewModel.setCellularMediaPolicy(CellularMediaPolicy.MINIMAL) },
                            modifier = Modifier.weight(1f),
                        )
                        SegmentedChip(
                            label = stringResource(R.string.settings_network_chip_local),
                            selected = state.cellularMediaPolicy == CellularMediaPolicy.LOCAL_ONLY,
                            onClick = { viewModel.setCellularMediaPolicy(CellularMediaPolicy.LOCAL_ONLY) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SectionDivider()
                SectionToggleRow(
                    label = stringResource(R.string.settings_network_auto_download),
                    subtitle = stringResource(R.string.settings_network_auto_download_sub),
                    checked = state.autoDownloadPlaylists,
                    onToggle = { viewModel.setAutoDownloadPlaylists(it) },
                    testTagId = "settings_toggle_auto_download_playlists",
                )
            }

            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.settings_network_library_sync))
            SectionCard {
                Row(
                    Modifier
                        .then(
                            if (!state.isResyncing) {
                                Modifier.clickable {
                                    when {
                                        viewModel.shouldBlockResyncOnCellular() ->
                                            showResyncCellularBlocked = true

                                        viewModel.shouldWarnResyncOnCellular() ->
                                            showResyncCellularWarn = true

                                        else -> onResyncLibrary()
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isResyncing) {
                        CircularProgressIndicator(
                            color = BrandTeal,
                            modifier = Modifier.size(iconSmall()),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Sync, null, tint = BrandTeal, modifier = Modifier.size(iconSmall()))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Resync Library",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Re-fetch albums, artists, playlists, and genres from server",
                            color = Color(0xFF888888),
                            fontSize = textLabelM(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.clickable { onRebuildMixes() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = Color(0xFFFFA726), modifier = Modifier.size(iconSmall()))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Rebuild Daily Mixes",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Regenerate today's Daily Mixes",
                            color = Color(0xFF888888),
                            fontSize = textLabelM(),
                        )
                    }
                }
                Divider(color = Surface, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier.clickable { onCustomMixes() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Tune, null, tint = BrandPurple, modifier = Modifier.size(iconSmall()))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Custom Daily Mixes",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Create named mixes from genres, decades, or favorite artists",
                            color = Color(0xFF888888),
                            fontSize = textLabelM(),
                        )
                    }
                }
                Divider(color = Surface, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Schedule, null, tint = Color(0xFF66BB6A), modifier = Modifier.size(iconSmall()))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Sync Interval",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Auto-check for new music every ${state.syncIntervalHours}h",
                            color = Color(0xFF888888),
                            fontSize = textLabelM(),
                        )
                    }
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Text(
                            "${state.syncIntervalHours}h",
                            color = BrandTeal,
                            fontSize = textHeadingS(),
                            modifier = Modifier.clickable {
                                expanded = true
                            }.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf(1, 2, 4, 6, 12, 24).forEach { h ->
                                DropdownMenuItem(
                                    text = { Text("$h hour${if (h > 1) "s" else ""}") },
                                    onClick = {
                                        viewModel.setSyncIntervalHours(h)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                SectionDivider()
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.NewReleases,
                        null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(iconSmall()),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Recently Added refresh",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Home strip rechecks server after " +
                                com.lucasdss.ftpmusic.app.data.db.HomeRecentCache.formatTtlLabel(
                                    state.homeRecentTtlMinutes,
                                ) +
                                " (max = Sync Interval)",
                            color = Color(0xFF888888),
                            fontSize = textLabelM(),
                        )
                        val maxTtl = state.syncIntervalHours.coerceIn(1, 24) * 60
                        Slider(
                            value = state.homeRecentTtlMinutes.toFloat(),
                            onValueChange = { viewModel.setHomeRecentTtlMinutes(it.toInt()) },
                            valueRange = 1f..maxTtl.toFloat(),
                            steps = (maxTtl - 2).coerceAtLeast(0).coerceAtMost(58),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                    Text(
                        com.lucasdss.ftpmusic.app.data.db.HomeRecentCache.formatTtlLabel(
                            state.homeRecentTtlMinutes,
                        ),
                        color = BrandTeal,
                        fontSize = textHeadingS(),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                SectionDivider()
                SectionToggleRow(
                    label = "Library sync on Wi-Fi only",
                    subtitle = "FULL and DELTA wait for Wi-Fi or Ethernet",
                    checked = state.librarySyncWifiOnly,
                    onToggle = { viewModel.setLibrarySyncWifiOnly(it) },
                )
            }
        }
    }

    if (showResyncCellularWarn) {
        ConfirmationSheet(
            title = "Use mobile data?",
            message = "Library sync on Wi-Fi only is enabled, but you are on a cellular network. " +
                "Continuing will download catalog metadata over mobile data.",
            confirmLabel = "Sync anyway",
            onConfirm = {
                showResyncCellularWarn = false
                com.lucasdss.ftpmusic.app.data.cache.NetworkPolicyState.grantCellularSyncOverride()
                onResyncLibrary()
            },
            onDismiss = { showResyncCellularWarn = false },
        )
    }
    if (showResyncCellularBlocked) {
        ConfirmationSheet(
            title = "Wi-Fi required",
            message = "Cellular local-only is enabled. Connect to Wi-Fi to resync your library, " +
                "or change the On cellular setting under Network & data.",
            confirmLabel = "OK",
            onConfirm = { showResyncCellularBlocked = false },
            onDismiss = { showResyncCellularBlocked = false },
        )
    }
}
