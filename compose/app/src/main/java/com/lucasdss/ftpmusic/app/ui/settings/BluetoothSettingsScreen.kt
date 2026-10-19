package com.lucasdss.ftpmusic.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.playback.BatteryOptimizationHelper
import com.lucasdss.ftpmusic.app.playback.DefaultMusicRoleHelper
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.components.OverwriteBehaviorOption
import com.lucasdss.ftpmusic.app.ui.components.SectionCard
import com.lucasdss.ftpmusic.app.ui.components.SectionDivider
import com.lucasdss.ftpmusic.app.ui.components.SectionToggleRow
import com.lucasdss.ftpmusic.app.ui.components.SettingsPageScaffold
import com.lucasdss.ftpmusic.app.ui.components.SystemStatusRow
import com.lucasdss.ftpmusic.app.ui.spacingL
import com.lucasdss.ftpmusic.app.ui.spacingM
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelM
import com.lucasdss.ftpmusic.app.ui.textLabelS

@Composable
fun BluetoothSettingsScreen(
    onBack: () -> Unit = {},
    scrollToAnchor: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsPageScaffold(title = stringResource(R.string.settings_page_bluetooth), onBack = onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            SectionCard(
                Modifier.settingsScrollAnchor(SettingsAnchors.BT_RESUME, scrollToAnchor),
            ) {
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
                ) { granted ->
                    viewModel.refreshBtResumeState()
                    if (!granted) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.bt_resume_notif_denied_toast),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                SectionToggleRow(
                    label = stringResource(R.string.bt_resume_toggle),
                    subtitle = stringResource(R.string.bt_resume_subtitle),
                    checked = state.btResumeEnabled,
                    onToggle = { enabled ->
                        if (enabled) {
                            val needsBt = !state.btHasConnectPermission &&
                                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                            val needsNotif =
                                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                    !androidx.core.app.NotificationManagerCompat
                                        .from(context).areNotificationsEnabled()
                            if (needsNotif) {
                                notifPermissionLauncher.launch(
                                    android.Manifest.permission.POST_NOTIFICATIONS,
                                )
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
                if (state.btResumeEnabled && !state.btNotificationsEnabled) {
                    Text(
                        stringResource(R.string.bt_resume_notif_warning),
                        color = Color(0xFFFFB74D),
                        fontSize = textLabelS(),
                        modifier = Modifier
                            .padding(horizontal = spacingL(), vertical = spacingS())
                            .testTag("settings_bt_notif_warning"),
                    )
                }
                Text(
                    stringResource(R.string.bt_resume_doze_note),
                    color = Color(0xFF888888),
                    fontSize = textLabelS(),
                    modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
                )
                SystemStatusRow(
                    title = stringResource(R.string.bt_resume_battery_cta),
                    subtitle = if (state.btBatteryUnrestricted) {
                        stringResource(R.string.bt_resume_battery_cta_ok)
                    } else {
                        stringResource(R.string.bt_resume_battery_cta_needed)
                    },
                    ok = state.btBatteryUnrestricted,
                    onOpen = {
                        BatteryOptimizationHelper.openBatterySettings(context)
                        viewModel.refreshBtResumeState()
                    },
                    testTag = "settings_bt_battery_cta",
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
                SystemStatusRow(
                    title = stringResource(R.string.bt_resume_default_music_app),
                    subtitle = if (state.isDefaultMusicApp) {
                        stringResource(R.string.bt_resume_default_music_app_held)
                    } else {
                        stringResource(R.string.bt_resume_default_music_app_subtitle)
                    },
                    ok = state.isDefaultMusicApp,
                    onOpen = {
                        val activity = context as? ComponentActivity
                        if (activity != null) {
                            DefaultMusicRoleHelper.requestDefaultMusicApp(activity)
                        } else {
                            DefaultMusicRoleHelper.openDefaultAppsSettings(context)
                        }
                        viewModel.refreshBtResumeState()
                    },
                    testTag = "settings_default_music_app",
                )
            }
        }
    }
}
