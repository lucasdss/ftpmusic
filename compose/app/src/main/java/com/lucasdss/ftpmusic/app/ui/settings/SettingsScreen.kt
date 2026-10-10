package com.lucasdss.ftpmusic.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.data.cache.CellularMediaPolicy
import com.lucasdss.ftpmusic.app.playback.DefaultMusicRoleHelper
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.BrandBg
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.components.DetailBackButton
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChip

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onResyncLibrary: () -> Unit = {},
    onRebuildMixes: () -> Unit = {},
    onProfile: () -> Unit = {},
    onServerSettingsSaved: () -> Unit = {},
    onCustomMixes: () -> Unit = {},
    onDownloads: () -> Unit = {},
    onTypography: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showResyncCellularWarn by remember { mutableStateOf(false) }
    var showResyncCellularBlocked by remember { mutableStateOf(false) }

    val updateLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { /* Play flexible UI dismissed; state stays Available / UpToDate */ }

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
                // No share target — ignore (export stays in-process only).
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailBackButton(onBack = onBack, inset = false)
            Spacer(Modifier.width(8.dp))
            Text(
                "Settings",
                color = Color.White,
                fontSize = textHeadingL(),
                fontWeight = FontWeight.Bold,
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
            // ═══ Profile card ═══
            SectionCard(Modifier.clickable { onProfile() }.testTag("settings_profile_card")) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(miniPlayerHeight()).clip(RoundedCornerShape(32.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(BrandTeal, BrandPurple),
                                    start = androidx.compose.ui.geometry.Offset.Zero,
                                    end = androidx.compose.ui.geometry.Offset.Infinite,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(iconMedium()))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "Your Profile",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Listening stats & recently played", color = Color(0xFF888888), fontSize = textBodyM())
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Server Connection section ═══
            SectionLabel("SERVER CONNECTION")
            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = state.serverUrl,
                        onValueChange = viewModel::setServerUrl,
                        label = { Text("Server URL", color = Color(0xFF888888)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = settingsTextFieldColors(),
                        shape = RoundedCornerShape(cornerS()),
                        placeholder = { Text("https://your-server.com", color = NavUnselected) },
                    )
                    // Warn about device-local (stale dev proxy) addresses
                    if (com.lucasdss.ftpmusic.app.di.isLoopbackUrl(state.serverUrl)) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "⚠️ This is a device-local address (old phone proxy). " +
                                "Enter your real server URL (e.g. https://your-server.com).",
                            color = Color(0xFFE8C766),
                            fontSize = textLabelS(),
                        )
                    } else if (com.lucasdss.ftpmusic.app.ui.server.isCleartextServerUrl(state.serverUrl)) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "HTTP exposes credentials in transit. Use only with a trusted private-network server.",
                            color = Color(0xFFE8C766),
                            fontSize = textLabelS(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = viewModel::setUsername,
                        label = { Text("Username", color = Color(0xFF888888)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = settingsTextFieldColors(),
                        shape = RoundedCornerShape(cornerS()),
                    )
                    Spacer(Modifier.height(8.dp))
                    var showPassword by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::setPassword,
                        label = { Text("Password", color = Color(0xFF888888)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = settingsTextFieldColors(),
                        shape = RoundedCornerShape(cornerS()),
                        visualTransformation = if (showPassword) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null,
                                    tint = Color(0xFF888888),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingS())) {
                        Button(
                            onClick = {
                                if (viewModel.saveServerSettings()) onServerSettingsSaved()
                            },
                            modifier = Modifier.weight(1f).testTag("settings_server_save"),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                            shape = RoundedCornerShape(cornerS()),
                        ) { Text("Save", color = Color.White) }
                        OutlinedButton(
                            onClick = { viewModel.testConnection() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(cornerS()),
                        ) {
                            if (state.isTesting) {
                                CircularProgressIndicator(
                                    color = BrandTeal,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("Test Connection", color = BrandTeal)
                            }
                        }
                    }
                    state.testResult?.let { result ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            result,
                            color = if (result.startsWith("✓")) BrandTeal else Color(0xFFFF6B6B),
                            fontSize = textLabelM(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

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

            Spacer(Modifier.height(24.dp))

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

            // ═══ Network section ═══
            SectionLabel("NETWORK")
            SectionCard {
                SectionToggleRow(
                    label = "Simulate Offline",
                    subtitle = "Test the app without network access",
                    checked = state.offlineMode,
                    onToggle = { viewModel.setOfflineMode(it) },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Cast section ═══
            SectionLabel("Google Cast")
            SectionCard {
                SectionToggleRow(
                    label = "Cast from Phone",
                    subtitle = "Phone streams to Cast over LAN",
                    checked = state.castFromPhone,
                    onToggle = { viewModel.setCastFromPhone(it) },
                )
                if (state.castFromPhone && state.castDeviceName != null) {
                    SectionDivider()
                    SectionRow("Casting to:", state.castDeviceName ?: "")
                }
                SectionDivider()
                SectionToggleRow(
                    label = "Use HTTP for Cast",
                    subtitle = "For self-hosted servers without SSL",
                    checked = state.useHttpForCast,
                    onToggle = { viewModel.setUseHttpForCast(it) },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Downloads / cellular media (ADR-0105) ═══
            SectionLabel("DOWNLOADS")
            SectionCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "On cellular",
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
                            label = "Auto-cache",
                            selected = state.cellularMediaPolicy == CellularMediaPolicy.AUTO_CACHE,
                            onClick = { viewModel.setCellularMediaPolicy(CellularMediaPolicy.AUTO_CACHE) },
                            modifier = Modifier.weight(1f),
                        )
                        SegmentedChip(
                            label = "Minimal",
                            selected = state.cellularMediaPolicy == CellularMediaPolicy.MINIMAL,
                            onClick = { viewModel.setCellularMediaPolicy(CellularMediaPolicy.MINIMAL) },
                            modifier = Modifier.weight(1f),
                        )
                        SegmentedChip(
                            label = "Local only",
                            selected = state.cellularMediaPolicy == CellularMediaPolicy.LOCAL_ONLY,
                            onClick = { viewModel.setCellularMediaPolicy(CellularMediaPolicy.LOCAL_ONLY) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SectionDivider()
                SectionRow("Storage Used", formatBytes(state.autoCacheBytes + state.downloadBytes))
                SectionDivider()
                SectionToggleRow(
                    label = "Auto-Download Playlists",
                    subtitle = "Download tracks when syncing playlists",
                    checked = state.autoDownloadPlaylists,
                    onToggle = { viewModel.setAutoDownloadPlaylists(it) },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Appearance section (ADR-0103 typography nested page) ═══
            SectionLabel("APPEARANCE")
            SectionCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onTypography() }
                        .padding(16.dp)
                        .testTag("settings_typography_nav"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.TextFields, null, tint = BrandTeal, modifier = Modifier.size(iconSmall()))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Text & display size",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            state.typographyPrefs.density.label +
                                " · fonts, icons, and cover art",
                            color = Color(0xFF888888),
                            fontSize = textLabelM(),
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = NavUnselected, modifier = Modifier.size(iconSmall()))
                }
                SectionDivider()
                SectionToggleRow(
                    label = "Like and dislike on track lists",
                    subtitle = "Hide to give titles more room; Now Playing keeps thumbs",
                    checked = state.listChromePrefs.showListReactions,
                    onToggle = { viewModel.setShowListReactions(it) },
                )
                SectionDivider()
                SectionToggleRow(
                    label = "Duration on track lists",
                    subtitle = "Show track length on the title line",
                    checked = state.listChromePrefs.showListDuration,
                    onToggle = { viewModel.setShowListDuration(it) },
                )
                SectionDivider()
                SectionToggleRow(
                    label = "Prefer iTunes album art",
                    subtitle = "Use iTunes artwork instead of Navidrome when available",
                    checked = state.preferItunesArt,
                    onToggle = { viewModel.setPreferItunesArt(it) },
                )
                SectionDivider()
                SectionToggleRow(
                    label = "Search lyrics",
                    subtitle = "Include cached lyrics in local search (rebuilds within ~500ms)",
                    checked = state.searchLyricsEnabled,
                    onToggle = { viewModel.setSearchLyricsEnabled(it) },
                )
                SectionDivider()
                SectionToggleRow(
                    label = "Hide navigation labels",
                    subtitle = "Show icons only in the bottom bar",
                    checked = state.hideNavLabels,
                    onToggle = { viewModel.setHideNavLabels(it) },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Last.fm section ═══
            SectionLabel("LAST.FM")
            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "API key powers Similar Artists on Artist Detail. " +
                            "Play scrobbling uses your Navidrome server config, not this key.",
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                    )
                    Spacer(Modifier.height(8.dp))
                    var showLastFmKey by remember { mutableStateOf(false) }
                    var lastFmDraft by remember(state.lastFmApiKey, state.lastFmKeySaved) {
                        mutableStateOf(state.lastFmApiKey)
                    }
                    OutlinedTextField(
                        value = lastFmDraft,
                        onValueChange = {
                            lastFmDraft = it
                            viewModel.setLastFmApiKeyDraft(it)
                        },
                        label = { Text("API Key", color = Color(0xFF888888)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_lastfm_api_key"),
                        colors = settingsTextFieldColors(),
                        shape = RoundedCornerShape(cornerS()),
                        visualTransformation = if (showLastFmKey) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showLastFmKey = !showLastFmKey }) {
                                Icon(
                                    if (showLastFmKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null,
                                    tint = Color(0xFF888888),
                                )
                            }
                        },
                        placeholder = { Text("from last.fm/api", color = NavUnselected) },
                    )
                    if (state.lastFmKeySaved) {
                        Spacer(Modifier.height(4.dp))
                        Text("Key saved", color = BrandTeal, fontSize = textLabelS())
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingS())) {
                        Button(
                            onClick = { viewModel.setLastFmApiKey(lastFmDraft) },
                            modifier = Modifier.weight(1f).testTag("settings_lastfm_save"),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                            shape = RoundedCornerShape(cornerS()),
                        ) { Text("Save", color = Color.White) }
                        OutlinedButton(
                            onClick = {
                                lastFmDraft = ""
                                viewModel.clearLastFmApiKey()
                            },
                            modifier = Modifier.weight(1f).testTag("settings_lastfm_clear"),
                            shape = RoundedCornerShape(cornerS()),
                        ) { Text("Clear", color = BrandTeal) }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Bluetooth resume (ADR-0072) ═══
            SectionLabel(stringResource(R.string.bt_resume_section_label))
            SectionCard {
                LaunchedEffect(Unit) { viewModel.refreshBtResumeState() }
                val btPermissionLauncher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    val granted = result.values.any { it } ||
                        com.lucasdss.ftpmusic.app.playback.BluetoothBondedDevices
                            .hasConnectPermission(context)
                    if (granted) {
                        viewModel.setBtResumeEnabled(true)
                        viewModel.refreshBtResumeState()
                    } else {
                        viewModel.setBtResumeEnabled(false)
                        viewModel.refreshBtResumeState()
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.bt_resume_devices_need_permission),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { /* best-effort for FGS fallback notif */ }
                SectionToggleRow(
                    label = stringResource(R.string.bt_resume_toggle),
                    subtitle = stringResource(R.string.bt_resume_subtitle),
                    checked = state.btResumeEnabled,
                    onToggle = { enabled ->
                        if (enabled) {
                            val needsBt = !state.btHasConnectPermission &&
                                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val nm = androidx.core.app.NotificationManagerCompat.from(context)
                                if (!nm.areNotificationsEnabled()) {
                                    notifPermissionLauncher.launch(
                                        android.Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                }
                            }
                            if (needsBt) {
                                // Enable only after CONNECT grant (callback).
                                btPermissionLauncher.launch(
                                    arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                                )
                            } else {
                                viewModel.setBtResumeEnabled(true)
                                viewModel.refreshBtResumeState()
                            }
                        } else {
                            viewModel.setBtResumeEnabled(false)
                        }
                    },
                )
                Text(
                    stringResource(R.string.bt_resume_doze_note),
                    color = Color(0xFF888888),
                    fontSize = textLabelS(),
                    modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
                )
                if (state.btResumeEnabled) {
                    SectionDivider()
                    OverwriteBehaviorOption(
                        label = stringResource(R.string.bt_resume_mode_any),
                        subtitle = stringResource(R.string.bt_resume_mode_any_subtitle),
                        selected = state.btResumeMode ==
                            com.lucasdss.ftpmusic.app.playback.BtResumeMode.ANY,
                        onClick = {
                            viewModel.setBtResumeMode(
                                com.lucasdss.ftpmusic.app.playback.BtResumeMode.ANY,
                            )
                        },
                    )
                    OverwriteBehaviorOption(
                        label = stringResource(R.string.bt_resume_mode_selected),
                        subtitle = stringResource(R.string.bt_resume_mode_selected_subtitle),
                        selected = state.btResumeMode ==
                            com.lucasdss.ftpmusic.app.playback.BtResumeMode.SELECTED,
                        onClick = {
                            viewModel.setBtResumeMode(
                                com.lucasdss.ftpmusic.app.playback.BtResumeMode.SELECTED,
                            )
                        },
                    )
                    if (state.btResumeMode ==
                        com.lucasdss.ftpmusic.app.playback.BtResumeMode.SELECTED
                    ) {
                        SectionDivider()
                        Text(
                            stringResource(R.string.bt_resume_devices_label),
                            color = Color.White,
                            fontSize = textHeadingS(),
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
                        )
                        when {
                            !state.btHasConnectPermission &&
                                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                                Text(
                                    stringResource(R.string.bt_resume_devices_need_permission),
                                    color = Color(0xFF888888),
                                    fontSize = textLabelS(),
                                    modifier = Modifier.padding(
                                        horizontal = spacingL(),
                                        vertical = spacingS(),
                                    ),
                                )
                            }

                            state.btBondedDevices.isEmpty() -> {
                                Text(
                                    stringResource(R.string.bt_resume_devices_empty),
                                    color = Color(0xFF888888),
                                    fontSize = textLabelS(),
                                    modifier = Modifier.padding(
                                        horizontal = spacingL(),
                                        vertical = spacingS(),
                                    ),
                                )
                            }

                            else -> {
                                state.btBondedDevices.forEach { device ->
                                    val selected = device.address in state.btSelectedMacs
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setBtDeviceSelected(
                                                    device.address,
                                                    !selected,
                                                )
                                            }
                                            .padding(
                                                horizontal = spacingL(),
                                                vertical = spacingM(),
                                            )
                                            .testTag("bt_device_${device.address}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                device.name,
                                                color = Color.White,
                                                fontSize = textHeadingS(),
                                            )
                                            Text(
                                                device.address,
                                                color = Color(0xFF666666),
                                                fontSize = textLabelS(),
                                            )
                                        }
                                        Checkbox(
                                            checked = selected,
                                            onCheckedChange = {
                                                viewModel.setBtDeviceSelected(device.address, it)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = BrandTeal,
                                                uncheckedColor = Color(0xFF666666),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                SectionDivider()
                val defaultSubtitle = if (state.isDefaultMusicApp) {
                    stringResource(R.string.bt_resume_default_music_app_held)
                } else {
                    stringResource(R.string.bt_resume_default_music_app_subtitle)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val activity = context as? ComponentActivity
                            if (activity != null) {
                                DefaultMusicRoleHelper.requestDefaultMusicApp(activity)
                            } else {
                                DefaultMusicRoleHelper.openDefaultAppsSettings(context)
                            }
                            viewModel.refreshBtResumeState()
                        }
                        .padding(horizontal = spacingL(), vertical = spacingM())
                        .testTag("settings_default_music_app"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.bt_resume_default_music_app),
                            color = Color.White,
                            fontSize = textHeadingS(),
                        )
                        Text(
                            defaultSubtitle,
                            color = Color(0xFF666666),
                            fontSize = textLabelM(),
                        )
                    }
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = NavUnselected,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Notifications section ═══
            SectionLabel("NOTIFICATIONS")
            SectionCard {
                val systemNotificationsEnabled = androidx.core.app.NotificationManagerCompat
                    .from(context).areNotificationsEnabled()
                val permissionLauncher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (!granted) {
                        android.widget.Toast.makeText(
                            context,
                            "Notifications are disabled — enable them in system settings",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                SectionToggleRow(
                    label = "Playback notifications",
                    subtitle = "Show track info and controls on the lock screen and in the shade",
                    checked = state.playbackNotificationsEnabled,
                    onToggle = { enabled ->
                        viewModel.setPlaybackNotificationsEnabled(enabled)
                        if (enabled) {
                            // Enabling requires the system permission on 13+.
                            if (!systemNotificationsEnabled) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Notifications are always available on this Android version",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        } else {
                            // Disabling keeps a silent FGS-satisfying notification
                            // while playing — Android forbids a fully absent one.
                            android.widget.Toast.makeText(
                                context,
                                "A silent notification still appears while playing (Android requirement)",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Home & Favorites section ═══
            SectionLabel("HOME & FAVORITES")
            SectionCard {
                SectionToggleRow(
                    label = "Playlists on Home",
                    subtitle = "Show synced playlists above Tuned In",
                    checked = state.showPlaylistsOnHome,
                    onToggle = { viewModel.setShowPlaylistsOnHome(it) },
                )
                SectionDivider()
                SectionToggleRow(
                    label = "Favorite Artists",
                    subtitle = "Show liked artists on Home and Favorites",
                    checked = state.showFavArtistsSection,
                    onToggle = { viewModel.setShowFavArtistsSection(it) },
                )
                SectionDivider()
                SectionToggleRow(
                    label = "Favorite Albums",
                    subtitle = "Show liked albums on Home and Favorites",
                    checked = state.showFavAlbumsSection,
                    onToggle = { viewModel.setShowFavAlbumsSection(it) },
                )
                SectionDivider()
                SectionToggleRow(
                    label = "Favorite Radio",
                    subtitle = "Show bookmarked stations on Home and Favorites",
                    checked = state.showFavRadioSection,
                    onToggle = { viewModel.setShowFavRadioSection(it) },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Custom Headers section ═══
            SectionLabel("Custom HTTP Headers")
            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Headers sent with every API request", color = Color(0xFF888888), fontSize = textLabelM())
                    Spacer(Modifier.height(8.dp))
                    state.customHeaders.forEachIndexed { index, (key, value) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = key,
                                onValueChange = { newKey ->
                                    val updated = state.customHeaders.toMutableList()
                                    updated[index] = newKey to value
                                    viewModel.setCustomHeaders(updated)
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("Key", fontSize = textLabelM()) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF0D0D0D),
                                    unfocusedContainerColor = Color(0xFF0D0D0D),
                                    focusedBorderColor = Color(0xFF333333),
                                    unfocusedBorderColor = Color(0xFF222222),
                                ),
                                shape = RoundedCornerShape(cornerS()),
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue ->
                                    val updated = state.customHeaders.toMutableList()
                                    updated[index] = key to newValue
                                    viewModel.setCustomHeaders(updated)
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("Value", fontSize = textLabelM()) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF0D0D0D),
                                    unfocusedContainerColor = Color(0xFF0D0D0D),
                                    focusedBorderColor = Color(0xFF333333),
                                    unfocusedBorderColor = Color(0xFF222222),
                                ),
                                shape = RoundedCornerShape(cornerS()),
                            )
                            IconButton(onClick = {
                                viewModel.setCustomHeaders(
                                    state.customHeaders.toMutableList().also {
                                        it.removeAt(index)
                                    },
                                )
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    null,
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (index < state.customHeaders.size - 1) Spacer(Modifier.height(8.dp))
                    }
                    if (state.customHeaders.size < 5) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            viewModel.setCustomHeaders(state.customHeaders + ("" to ""))
                        }) {
                            Text("+ Add header", color = BrandTeal)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ═══ Library Data section ═══
            SectionLabel("LIBRARY DATA")
            SectionCard {
                // Profile shortcut
                Row(
                    Modifier.clickable { onProfile() }.padding(16.dp).testTag("settings_profile_row"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Person, null, tint = BrandPurple, modifier = Modifier.size(iconSmall()))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Profile",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("My Listening stats & Recently Played", color = Color(0xFF888888), fontSize = textLabelM())
                    }
                }
                Divider(color = Surface, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
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

            Spacer(Modifier.height(24.dp))

            // ═══ Library Metrics section ═══
            SectionLabel("LIBRARY METRICS")
            SectionCard {
                Column(Modifier.padding(horizontal = spacingL(), vertical = spacingM())) {
                    MetricRow(
                        "Albums",
                        "${state.albumCount} albums",
                        if (state.lastMetadataSyncMs >
                            0
                        ) {
                            "${timeAgo(
                                state.lastMetadataSyncMs,
                            )} (took ${formatDuration(state.metadataSyncDurationMs)})"
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

            Spacer(Modifier.height(24.dp))

            // ═══ About / Diagnostics section ═══
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

            Spacer(Modifier.height(adp(40f)))

            // Logo footer
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
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
                "or change the On cellular setting under Downloads.",
            confirmLabel = "OK",
            onConfirm = { showResyncCellularBlocked = false },
            onDismiss = { showResyncCellularBlocked = false },
        )
    }
}

@Composable
private fun UpdateCheckRow(
    updateCheck: UpdateCheckUi,
    onCheck: () -> Unit,
    onStartUpdate: () -> Unit,
    onCompleteUpdate: () -> Unit,
    onOpenPlayStore: () -> Unit,
) {
    when (updateCheck) {
        is UpdateCheckUi.Available -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onStartUpdate)
                    .padding(horizontal = spacingL(), vertical = spacingM())
                    .testTag("settings_start_update"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.SystemUpdate,
                    null,
                    tint = BrandTeal,
                    modifier = Modifier.size(iconSmall()),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Update available",
                        color = Color.White,
                        fontSize = textHeadingS(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Version code ${updateCheck.availableVersionCode} on Play Store",
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                    )
                }
            }
        }

        is UpdateCheckUi.InProgress -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingL(), vertical = spacingM())
                    .testTag("settings_update_in_progress"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSmall()),
                    color = BrandTeal,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Downloading update…",
                        color = Color.White,
                        fontSize = textHeadingS(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Version code ${updateCheck.availableVersionCode}",
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                    )
                }
            }
        }

        UpdateCheckUi.ReadyToInstall -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCompleteUpdate)
                    .padding(horizontal = spacingL(), vertical = spacingM())
                    .testTag("settings_complete_update"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.SystemUpdate,
                    null,
                    tint = BrandTeal,
                    modifier = Modifier.size(iconSmall()),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Restart to install",
                        color = Color.White,
                        fontSize = textHeadingS(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Update downloaded · tap to finish",
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                    )
                }
            }
        }

        UpdateCheckUi.Checking -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingL(), vertical = spacingM())
                    .testTag("settings_check_updates"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSmall()),
                    color = BrandTeal,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Checking for updates…",
                        color = Color.White,
                        fontSize = textHeadingS(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Asking Play Store",
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                    )
                }
            }
        }

        else -> {
            val subtitle = when (updateCheck) {
                UpdateCheckUi.UpToDate -> "You're up to date"
                is UpdateCheckUi.Error -> updateCheck.message
                else -> "Play Store only · tap to check"
            }
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCheck)
                        .padding(horizontal = spacingL(), vertical = spacingM())
                        .testTag("settings_check_updates"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.SystemUpdateAlt,
                        null,
                        tint = BrandTeal,
                        modifier = Modifier.size(iconSmall()),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Check for updates",
                            color = Color.White,
                            fontSize = textHeadingS(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            subtitle,
                            color = Color(0xFF888888),
                            fontSize = textLabelM(),
                        )
                    }
                }
                if (updateCheck is UpdateCheckUi.Error) {
                    TextButton(
                        onClick = onOpenPlayStore,
                        modifier = Modifier
                            .padding(start = spacingL(), bottom = spacingS())
                            .testTag("settings_open_play_store"),
                    ) {
                        Text("Open Play Store", color = BrandTeal, fontSize = textLabelM())
                    }
                }
            }
        }
    }
}

private fun openPlayStoreListing(context: android.content.Context, packageName: String) {
    val market = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("market://details?id=$packageName"),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: android.content.ActivityNotFoundException) {
        try {
            context.startActivity(web)
        } catch (_: android.content.ActivityNotFoundException) {
            // No browser / store — ignore.
        }
    }
}

@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color(0xFF0D0D0D),
    unfocusedContainerColor = Color(0xFF0D0D0D),
    focusedBorderColor = Color(0xFF333333),
    unfocusedBorderColor = Color(0xFF222222),
    cursorColor = BrandTeal,
    focusedLabelColor = BrandTeal,
    unfocusedLabelColor = Color(0xFF888888),
)

@Composable
internal fun SectionLabel(title: String) {
    Text(
        title.uppercase(),
        color = Color(0xFF666666),
        fontSize = textLabelS(),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = spacingS(), start = spacingXS()),
    )
}

@Composable
internal fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Surface),
        content = content,
    )
}

@Composable
private fun SectionRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingM()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FittingText(
            text = label,
            color = Color.White,
            fontSize = textHeadingS(),
            minFontSize = textMicro(),
            modifier = Modifier.weight(1f),
            fillMaxWidth = false,
        )
        Spacer(Modifier.width(8.dp))
        FittingText(
            text = value,
            color = Color(0xFF888888),
            fontSize = textHeadingS(),
            minFontSize = textMicro(),
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            fillMaxWidth = true,
        )
    }
}

@Composable
private fun SectionToggleRow(label: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingM()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            FittingText(
                text = label,
                color = Color.White,
                fontSize = textHeadingS(),
                minFontSize = textMicro(),
            )
            FittingText(
                text = subtitle,
                color = Color(0xFF666666),
                fontSize = textLabelM(),
                minFontSize = textMicro(),
                maxLines = 2,
            )
        }
        // Custom teal toggle
        Box(
            Modifier
                .size(44.dp, spacing2XL())
                .clip(RoundedCornerShape(cornerM()))
                .background(if (checked) BrandTeal else Color(0xFF333333))
                .clickable { onToggle(!checked) }
                .testTag("settings_toggle_${label.replace(" ", "_")}"),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(iconSmall()).padding(2.dp).clip(RoundedCornerShape(10.dp)).background(Color.White))
        }
    }
}

@Composable
internal fun SectionDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222222)))
}

@Composable
internal fun OverwriteBehaviorOption(label: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = spacingL(), vertical = spacingM()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Radio circle
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .border(2.dp, if (selected) BrandTeal else Color(0xFF444444), CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(BrandTeal))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FittingText(
                text = label,
                color = if (selected) BrandTeal else Color.White,
                fontSize = textHeadingS(),
                minFontSize = textMicro(),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            FittingText(
                text = subtitle,
                color = Color(0xFF666666),
                fontSize = textLabelM(),
                minFontSize = textMicro(),
                maxLines = 2,
            )
        }
    }
    SectionDivider()
}

@Composable
private fun MetricRow(label: String, value: String, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = spacingS()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FittingText(
            text = label,
            color = Color.White,
            fontSize = textHeadingS(),
            minFontSize = textMicro(),
            modifier = Modifier.weight(1f),
            fillMaxWidth = false,
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            FittingText(
                text = value,
                color = Color(0xFF888888),
                fontSize = textHeadingS(),
                minFontSize = textMicro(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            if (subtitle != null) {
                FittingText(
                    text = subtitle,
                    color = Color(0xFF666666),
                    fontSize = textLabelM(),
                    minFontSize = textMicro(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

internal fun timeAgo(ms: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ms
    if (diff < 0) return "just now"
    val seconds = diff / 1000
    if (seconds < 60) return "${seconds}s ago"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return "${days}d ago"
}

internal fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (remainingSeconds > 0) "${minutes}m ${remainingSeconds}s" else "${minutes}m"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmationSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(horizontal = spacingL(), vertical = spacingXL())) {
            Text(title, color = Color.White, fontSize = textHeadingL(), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(message, color = Color(0xFFAAAAAA), fontSize = textBodyM(), lineHeight = 20.sp)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingS())) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF888888)),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                ) { Text("Cancel") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                ) { Text(confirmLabel, color = Color.White) }
            }
            Spacer(Modifier.height(spacingXL()))
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024
    if (mb < 1024) return "$mb MB"
    return "${mb / 1024} GB"
}
