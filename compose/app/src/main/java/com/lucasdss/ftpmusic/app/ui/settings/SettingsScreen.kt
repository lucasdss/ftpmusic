package com.lucasdss.ftpmusic.app.ui.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.ui.*

@Composable
fun SettingsScreen(
    onResyncLibrary: () -> Unit = {},
    onRebuildMixes: () -> Unit = {},
    onProfile: () -> Unit = {},
    onServerSettingsSaved: () -> Unit = {},
    onCustomMixes: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF12121E))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        // ═══ Profile card ═══
        SectionCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(miniPlayerHeight()).clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF00C8B4), Color(0xFFB040E8)),
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
                    Text("Your Profile", color = Color.White, fontSize = textHeadingS(), fontWeight = FontWeight.Bold)
                    Text("Navidrome Account", color = Color(0xFF888888), fontSize = textBodyM())
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
                    placeholder = { Text("https://your-server.com", color = Color(0xFF555555)) },
                )
                // Warn about device-local (stale dev proxy) addresses
                if (com.lucasdss.ftpmusic.app.di.isLoopbackUrl(state.serverUrl)) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "⚠️ This is a device-local address (old phone proxy). Enter your real server URL (e.g. https://your-server.com).",
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
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C8B4)),
                        shape = RoundedCornerShape(cornerS()),
                    ) { Text("Save", color = Color.White) }
                    OutlinedButton(
                        onClick = { viewModel.testConnection() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(cornerS()),
                    ) {
                        if (state.isTesting) {
                            CircularProgressIndicator(
                                color = Color(0xFF00C8B4),
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Test Connection", color = Color(0xFF00C8B4))
                        }
                    }
                }
                state.testResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        result,
                        color = if (result.startsWith("✓")) Color(0xFF00C8B4) else Color(0xFFFF6B6B),
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
                    activeTrackColor = Color(0xFF00C8B4),
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
                    activeTrackColor = Color(0xFF00C8B4),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                ),
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(12.dp))

        // Clear cache buttons with confirmation
        var showClearCacheConfirm by remember { mutableStateOf(false) }
        var showClearDownloadsConfirm by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingS())) {
            SectionCard(Modifier.weight(1f).clickable { showClearCacheConfirm = true }) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storage, null, tint = Color(0xFF00C8B4), modifier = Modifier.size(iconSmall()))
                    Spacer(Modifier.height(4.dp))
                    Text("Clear cache", color = Color.White, fontSize = textLabelL())
                    Text(formatBytes(state.autoCacheBytes), color = Color(0xFF888888), fontSize = textLabelS())
                }
            }
            SectionCard(Modifier.weight(1f).clickable { showClearDownloadsConfirm = true }) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(iconSmall()))
                    Spacer(Modifier.height(4.dp))
                    Text("Clear downloads", color = Color.White, fontSize = textLabelL())
                    Text(formatBytes(state.downloadBytes), color = Color(0xFF888888), fontSize = textLabelS())
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
                    activeTrackColor = Color(0xFF00C8B4),
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
                        color = if (state.journalCap == cap) Color(0xFF00C8B4) else Color(0xFF555555),
                        fontSize = textLabelS(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Stores recently played sources for continuous play suggestions",
                color = Color(0xFF666666),
                fontSize = textLabelS(),
                modifier = Modifier.padding(horizontal = spacingL()),
            )
            Spacer(Modifier.height(4.dp))
            SectionToggleRow(
                label = "Continuous Play",
                subtitle = "Auto-add tracks when queue runs out",
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
                onClick = { viewModel.setOverwriteBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.ASK) },
            )
            OverwriteBehaviorOption(
                label = "Clean",
                subtitle = "Always clear the queue and play the new content",
                selected = state.overwriteBehavior == com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.CLEAN,
                onClick = {
                    viewModel.setOverwriteBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.CLEAN)
                },
            )
            OverwriteBehaviorOption(
                label = "Push",
                subtitle = "Play new content first, keep current queue after",
                selected = state.overwriteBehavior == com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.PUSH,
                onClick = { viewModel.setOverwriteBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.PUSH) },
            )
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

        // ═══ Downloads section ═══
        SectionLabel("DOWNLOADS")
        SectionCard {
            SectionToggleRow(
                label = "Download on Wi-Fi Only",
                subtitle = "Restrict downloads to Wi-Fi networks",
                checked = !state.downloadMobileData,
                onToggle = { viewModel.setDownloadMobileData(!it) },
            )
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

        // ═══ Appearance section ═══
        SectionLabel("APPEARANCE")
        SectionCard {
            SectionToggleRow(
                label = "Dark Mode",
                subtitle = "Use dark theme",
                checked = true,
                onToggle = { /* Dynamic theme switch reserved for future settings work. */ },
            )
            SectionDivider()
            SectionToggleRow(
                label = "Prefer iTunes album art",
                subtitle = "Use iTunes artwork instead of Navidrome when available",
                checked = state.preferItunesArt,
                onToggle = { viewModel.setPreferItunesArt(it) },
            )
            SectionDivider()
            SectionRow("Accent Color", "Teal")
        }

        Spacer(Modifier.height(24.dp))

        // ═══ Notifications section ═══
        SectionLabel("NOTIFICATIONS")
        SectionCard {
            val context = LocalContext.current
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
                            viewModel.setCustomHeaders(state.customHeaders.toMutableList().also { it.removeAt(index) })
                        }) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF666666), modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < state.customHeaders.size - 1) Spacer(Modifier.height(8.dp))
                }
                if (state.customHeaders.size < 5) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        viewModel.setCustomHeaders(state.customHeaders + ("" to ""))
                    }) {
                        Text("+ Add header", color = Color(0xFF00C8B4))
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
                Modifier.clickable { onProfile() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Person, null, tint = Color(0xFFB040E8), modifier = Modifier.size(iconSmall()))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Profile", color = Color.White, fontSize = textHeadingS(), fontWeight = FontWeight.SemiBold)
                    Text("My Listening stats & Recently Played", color = Color(0xFF888888), fontSize = textLabelM())
                }
            }
            Divider(color = Color(0xFF1C1C2E), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier
                    .then(if (!state.isResyncing) Modifier.clickable { onResyncLibrary() } else Modifier)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isResyncing) {
                    CircularProgressIndicator(
                        color = Color(0xFF00C8B4),
                        modifier = Modifier.size(iconSmall()),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Sync, null, tint = Color(0xFF00C8B4), modifier = Modifier.size(iconSmall()))
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
            Divider(color = Color(0xFF1C1C2E), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier.clickable { onCustomMixes() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Tune, null, tint = Color(0xFFB040E8), modifier = Modifier.size(iconSmall()))
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
            Divider(color = Color(0xFF1C1C2E), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
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
                        "Auto-sync every ${state.syncIntervalHours}h",
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                    )
                }
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Text(
                        "${state.syncIntervalHours}h",
                        color = Color(0xFF00C8B4),
                        fontSize = textHeadingS(),
                        modifier = Modifier.clickable { expanded = true }.padding(horizontal = 8.dp, vertical = 4.dp),
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
                    "Artists",
                    "${state.artistCount} artists",
                    if (state.lastMetadataSyncMs > 0) timeAgo(state.lastMetadataSyncMs) else "Never synced",
                )
                SectionDivider()
                MetricRow("Cached Tracks", "${state.cachedTrackCount} tracks meta")
                SectionDivider()
                MetricRow("Playlists", "${state.playlistCount} playlists")
                SectionDivider()
                MetricRow(
                    "Cover Art",
                    "${formatBytes(state.coverArtCacheBytes)} / ${formatBytes(state.coverArtQuotaMb * 1024L * 1024L)}",
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

        // Logo footer
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                painterResource(R.drawable.play_store_icon_512),
                "FTP Music",
                Modifier.size(140.dp).clip(RoundedCornerShape(cornerL())).background(Color(0xFF101018))
                    .border(1.5.dp, Color(0xFF00C8B4).copy(alpha = 0.20f), RoundedCornerShape(cornerL()))
                    .shadow(
                        elevation = spacingS(),
                        shape = RoundedCornerShape(cornerL()),
                        ambientColor = Color(0xFF00C8B4),
                        spotColor = Color(0xFFB040E8),
                    ),
            )
            Spacer(Modifier.height(12.dp))
            Text("FTP Music", color = Color.White, fontSize = textHeadingM(), fontWeight = FontWeight.Bold)
            Text("Flow Tempo Pulse", color = Color(0xFF00C8B4), fontSize = textBodyM())
            Text("v1.0.0 · Navidrome / Subsonic API", color = Color(0xFF666666), fontSize = textLabelM())
        }

        Spacer(Modifier.height(32.dp))
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
    cursorColor = Color(0xFF00C8B4),
    focusedLabelColor = Color(0xFF00C8B4),
    unfocusedLabelColor = Color(0xFF888888),
)

@Composable
private fun SectionLabel(title: String) {
    Text(
        title.uppercase(),
        color = Color(0xFF666666),
        fontSize = textLabelS(),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = spacingS(), start = spacingXS()),
    )
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF1C1C2E)),
        content = content,
    )
}

@Composable
private fun SectionRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingM()),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontSize = textHeadingS())
        Text(value, color = Color(0xFF888888), fontSize = textHeadingS())
    }
}

@Composable
private fun SectionToggleRow(label: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingM()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = textHeadingS())
            Text(subtitle, color = Color(0xFF666666), fontSize = textLabelM())
        }
        // Custom teal toggle
        Box(
            Modifier
                .size(44.dp, spacing2XL())
                .clip(RoundedCornerShape(cornerM()))
                .background(if (checked) Color(0xFF00C8B4) else Color(0xFF333333))
                .clickable { onToggle(!checked) }
                .testTag("settings_toggle_${label.replace(" ", "_")}"),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(iconSmall()).padding(2.dp).clip(RoundedCornerShape(10.dp)).background(Color.White))
        }
    }
}

@Composable
private fun SectionDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222222)))
}

@Composable
private fun OverwriteBehaviorOption(label: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = spacingL(), vertical = spacingM()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Radio circle
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .border(2.dp, if (selected) Color(0xFF00C8B4) else Color(0xFF444444), CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF00C8B4)))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (selected) Color(0xFF00C8B4) else Color.White,
                fontSize = textHeadingS(),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(subtitle, color = Color(0xFF666666), fontSize = textLabelM())
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
        Text(label, color = Color.White, fontSize = textHeadingS())
        Column(horizontalAlignment = Alignment.End) {
            Text(value, color = Color(0xFF888888), fontSize = textHeadingS())
            if (subtitle != null) {
                Text(subtitle, color = Color(0xFF666666), fontSize = textLabelM())
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
        containerColor = Color(0xFF1C1C2E),
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
