package com.lucasdss.ftpmusic.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.ui.components.SectionCard
import com.lucasdss.ftpmusic.app.ui.components.SectionDivider
import com.lucasdss.ftpmusic.app.ui.components.SectionToggleRow
import com.lucasdss.ftpmusic.app.ui.components.SettingsPageScaffold
import com.lucasdss.ftpmusic.app.ui.components.SystemStatusRow

@Composable
fun NotificationsSettingsScreen(
    onBack: () -> Unit = {},
    scrollToAnchor: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
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

    SettingsPageScaffold(title = stringResource(R.string.settings_page_notifications), onBack = onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            SectionCard(
                Modifier.settingsScrollAnchor(SettingsAnchors.PLAYBACK_NOTIF, scrollToAnchor),
            ) {
                SystemStatusRow(
                    title = stringResource(R.string.settings_notif_system_title),
                    subtitle = if (systemNotificationsEnabled) {
                        stringResource(R.string.settings_notif_system_ok)
                    } else {
                        stringResource(R.string.settings_notif_system_needed)
                    },
                    ok = systemNotificationsEnabled,
                    onOpen = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                    testTag = "settings_notif_system_status",
                )
                SectionDivider()
                SectionToggleRow(
                    label = stringResource(R.string.settings_notif_playback_title),
                    subtitle = stringResource(R.string.settings_notif_playback_subtitle),
                    checked = state.playbackNotificationsEnabled,
                    onToggle = { enabled ->
                        viewModel.setPlaybackNotificationsEnabled(enabled)
                        if (enabled) {
                            if (!systemNotificationsEnabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                            android.widget.Toast.makeText(
                                context,
                                "A silent notification still appears while playing (Android requirement)",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }
    }
}
