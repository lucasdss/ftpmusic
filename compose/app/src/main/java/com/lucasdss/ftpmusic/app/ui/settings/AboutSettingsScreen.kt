package com.lucasdss.ftpmusic.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.lucasdss.ftpmusic.app.ui.BrandBg
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.adp
import com.lucasdss.ftpmusic.app.ui.components.MetricRow
import com.lucasdss.ftpmusic.app.ui.components.SectionCard
import com.lucasdss.ftpmusic.app.ui.components.SectionDivider
import com.lucasdss.ftpmusic.app.ui.components.SectionLabel
import com.lucasdss.ftpmusic.app.ui.components.SectionRow
import com.lucasdss.ftpmusic.app.ui.components.SectionToggleRow
import com.lucasdss.ftpmusic.app.ui.components.SettingsPageScaffold
import com.lucasdss.ftpmusic.app.ui.components.UpdateCheckRow
import com.lucasdss.ftpmusic.app.ui.components.formatBytes
import com.lucasdss.ftpmusic.app.ui.components.formatDurationMs
import com.lucasdss.ftpmusic.app.ui.components.openPlayStoreListing
import com.lucasdss.ftpmusic.app.ui.components.timeAgo
import com.lucasdss.ftpmusic.app.ui.cornerL
import com.lucasdss.ftpmusic.app.ui.iconSmall
import com.lucasdss.ftpmusic.app.ui.spacingL
import com.lucasdss.ftpmusic.app.ui.spacingM
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.textBodyM
import com.lucasdss.ftpmusic.app.ui.textHeadingM
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelM

@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val updateLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { /* Play flexible UI dismissed */ }

    LaunchedEffect(viewModel) {
        viewModel.shareDiagnosticsEvents.collect { text ->
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "ftpmusic diagnostics")
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            try {
                context.startActivity(
                    android.content.Intent.createChooser(send, "Share diagnostics"),
                )
            } catch (_: android.content.ActivityNotFoundException) {
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.appUpdateEvents.collect { event ->
            when (event) {
                is AppUpdateEvent.StartFlexibleUpdate -> {
                    val activity = context as? ComponentActivity ?: return@collect
                    viewModel.launchFlexibleUpdate(activity, updateLauncher)
                }

                is AppUpdateEvent.OpenPlayStore -> openPlayStoreListing(context, event.packageName)
            }
        }
    }

    SettingsPageScaffold(title = stringResource(R.string.settings_page_about), onBack = onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            SectionLabel("ABOUT / DIAGNOSTICS")
            SectionCard {
                SectionRow("Version", viewModel.appVersionLabel())
                SectionDivider()
                UpdateCheckRow(
                    updateCheck = state.updateCheck,
                    onCheck = viewModel::checkForUpdates,
                    onStartUpdate = viewModel::startUpdate,
                    onCompleteUpdate = viewModel::completeFlexibleUpdate,
                    onOpenPlayStore = viewModel::openPlayStoreListing,
                )
                SectionDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.shareDiagnostics() }
                        .padding(horizontal = spacingL(), vertical = spacingM())
                        .testTag("settings_share_diagnostics"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Share, null, tint = BrandTeal, modifier = Modifier.size(iconSmall()))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Share diagnostics",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Export recent sync / playback breadcrumbs (no upload)",
                            color = Color(0xFF888888),
                            fontSize = textLabelM(),
                        )
                    }
                }
                SectionDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.clearDiagnostics() }
                        .padding(horizontal = spacingL(), vertical = spacingM())
                        .testTag("settings_clear_diagnostics"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(iconSmall()),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Clear log",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Wipe the in-app diagnostic ring buffer",
                            color = Color(0xFF888888),
                            fontSize = textLabelM(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionLabel("Advanced / Developer")
            SectionCard {
                SectionToggleRow(
                    label = "Simulate Offline",
                    subtitle = "Test the app without network access",
                    checked = state.offlineMode,
                    onToggle = { viewModel.setOfflineMode(it) },
                )
            }

            Spacer(Modifier.height(24.dp))

            SectionLabel("LIBRARY METRICS")
            SectionCard {
                Column(Modifier.padding(horizontal = spacingL(), vertical = spacingM())) {
                    MetricRow(
                        "Albums",
                        "${state.albumCount} albums",
                        if (state.lastMetadataSyncMs > 0) {
                            "${timeAgo(
                                state.lastMetadataSyncMs,
                            )} (took ${formatDurationMs(state.metadataSyncDurationMs)})"
                        } else {
                            "Never synced"
                        },
                    )
                    SectionDivider()
                    MetricRow(
                        "Last full sync",
                        if (state.lastFullSyncMs > 0) timeAgo(state.lastFullSyncMs) else "Never",
                        "Resync Library is always FULL",
                    )
                    SectionDivider()
                    MetricRow(
                        "Last delta sync",
                        if (state.lastDeltaSyncMs > 0) timeAgo(state.lastDeltaSyncMs) else "Never",
                        "Runs on Sync Interval only (not Resync)",
                    )
                    SectionDivider()
                    MetricRow(
                        "Last sync mode",
                        state.lastSyncMode.ifBlank { "—" },
                        state.lastSyncSkipReason.takeIf { it.isNotBlank() }?.let { "Last skip: $it" },
                    )
                    SectionDivider()
                    MetricRow(
                        "Artists",
                        "${state.artistCount} artists",
                        if (state.lastMetadataSyncMs > 0) timeAgo(state.lastMetadataSyncMs) else "Never synced",
                    )
                    SectionDivider()
                    MetricRow(
                        "Album track meta",
                        "${state.cachedTrackCount} unique ids",
                        "cached_album_tracks from getAlbum",
                    )
                    SectionDivider()
                    MetricRow(
                        "Search corpus",
                        "${state.searchCorpusTrackCount} tracks",
                        "tracks table (Syncing Tracks row)",
                    )
                    SectionDivider()
                    MetricRow(
                        "Album song_count sum",
                        "${state.albumSongCountSum}",
                        "SUM(cached_albums.song_count)",
                    )
                    SectionDivider()
                    MetricRow("Playlists", "${state.playlistCount} playlists")
                    SectionDivider()
                    MetricRow(
                        "Cover Art",
                        "${formatBytes(
                            state.coverArtCacheBytes,
                        )} / ${formatBytes(state.coverArtQuotaMb * 1024L * 1024L)}",
                    )
                    SectionDivider()
                    MetricRow("Cached Music", "${formatBytes(state.autoCacheBytes)} used")
                    SectionDivider()
                    MetricRow(
                        "Downloaded Music",
                        "${state.downloadedTrackCount} tracks · ${formatBytes(state.downloadBytes)}",
                    )
                    SectionDivider()
                    MetricRow(
                        "Lyrics",
                        "${state.lyricsCount} cached",
                        if (state.lastLyricsFetchMs > 0) timeAgo(state.lastLyricsFetchMs) else "Never",
                    )
                }
            }

            Spacer(Modifier.height(adp(40f)))

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.play_store_icon_512),
                    "FTP Music",
                    Modifier.size(140.dp).clip(RoundedCornerShape(cornerL())).background(BrandBg)
                        .border(1.5.dp, BrandTeal.copy(alpha = 0.20f), RoundedCornerShape(cornerL()))
                        .shadow(
                            elevation = spacingS(),
                            shape = RoundedCornerShape(cornerL()),
                            ambientColor = BrandTeal,
                            spotColor = BrandPurple,
                        ),
                )
                Spacer(Modifier.height(12.dp))
                Text("FTP Music", color = Color.White, fontSize = textHeadingM(), fontWeight = FontWeight.Bold)
                Text("Flow Tempo Pulse", color = BrandTeal, fontSize = textBodyM())
                Text(
                    "v${com.lucasdss.ftpmusic.app.BuildConfig.VERSION_NAME} · Navidrome / Subsonic API",
                    color = Color(0xFF666666),
                    fontSize = textLabelM(),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
