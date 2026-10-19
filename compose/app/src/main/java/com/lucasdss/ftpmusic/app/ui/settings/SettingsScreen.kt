package com.lucasdss.ftpmusic.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.Muted
import com.lucasdss.ftpmusic.app.ui.components.DetailBackButton
import com.lucasdss.ftpmusic.app.ui.components.SectionCard
import com.lucasdss.ftpmusic.app.ui.components.SectionDivider
import com.lucasdss.ftpmusic.app.ui.components.SectionLabel
import com.lucasdss.ftpmusic.app.ui.components.SettingsNavRow
import com.lucasdss.ftpmusic.app.ui.components.settingsTextFieldColors
import com.lucasdss.ftpmusic.app.ui.cornerS
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.textHeadingL
import com.lucasdss.ftpmusic.app.ui.textLabelM

/**
 * Settings hub: search + grouped nav rows (ADR-0149). Leaves live on nested routes.
 * Uses verticalScroll (not LazyColumn) so all nav testTags stay in the semantics tree.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    /** @deprecated Prefer [onNavigate]; retained for call-site compatibility during migrate. */
    onResyncLibrary: () -> Unit = {},
    onRebuildMixes: () -> Unit = {},
    onProfile: () -> Unit = { onNavigate(SettingsRoutes.PROFILE) },
    onServerSettingsSaved: () -> Unit = {},
    onCustomMixes: () -> Unit = { onNavigate(SettingsRoutes.CUSTOM_MIXES) },
    onDownloads: () -> Unit = { onNavigate(SettingsRoutes.DOWNLOADS) },
    onTypography: () -> Unit = { onNavigate(SettingsRoutes.TYPOGRAPHY) },
    viewModel: SettingsViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
    ),
) {
    @Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
    val unusedLegacy = Triple(onResyncLibrary, onRebuildMixes, onServerSettingsSaved)

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    fun navigate(route: String) {
        when (route) {
            SettingsRoutes.PROFILE -> onProfile()
            SettingsRoutes.DOWNLOADS -> onDownloads()
            SettingsRoutes.TYPOGRAPHY -> onTypography()
            else -> onNavigate(route)
        }
    }

    val filtered = remember(query, state) {
        SettingsIndex.filter(query, state) { resId -> context.getString(resId) }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.groupRes } }
    val groupOrder = remember {
        listOf(
            R.string.settings_group_account,
            R.string.settings_group_playback,
            R.string.settings_group_storage,
            R.string.settings_group_network,
            R.string.settings_group_appearance,
            R.string.settings_group_notifications,
            R.string.settings_group_home,
            R.string.settings_group_integrations,
            R.string.settings_group_about,
        )
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
                stringResource(R.string.settings_title),
                color = Color.White,
                fontSize = textHeadingL(),
                fontWeight = FontWeight.Bold,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("settings_search"),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.settings_search_hint)) },
            colors = settingsTextFieldColors(),
            shape = RoundedCornerShape(cornerS()),
        )
        Spacer(Modifier.height(spacingS()))

        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.settings_search_empty),
                color = Muted,
                fontSize = textLabelM(),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
                    .testTag("settings_hub_list"),
            ) {
                groupOrder.forEach { groupRes ->
                    val nodes = grouped[groupRes] ?: return@forEach
                    SectionLabel(stringResource(groupRes))
                    SectionCard {
                        nodes.forEachIndexed { index, node ->
                            SettingsNavRow(
                                title = stringResource(node.titleRes),
                                summary = node.summaryProvider(state),
                                icon = node.icon,
                                onClick = { navigate(node.route) },
                                testTag = node.testTag,
                            )
                            if (index < nodes.lastIndex) {
                                SectionDivider()
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
