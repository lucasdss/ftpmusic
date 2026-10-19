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
fun ServerSettingsScreen(
    onBack: () -> Unit = {},
    onServerSettingsSaved: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsPageScaffold(title = stringResource(R.string.settings_page_server), onBack = onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
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
                                    contentDescription = if (showPassword) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
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
        }
    }
}
