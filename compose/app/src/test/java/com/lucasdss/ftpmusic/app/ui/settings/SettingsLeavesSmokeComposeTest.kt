package com.lucasdss.ftpmusic.app.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.lucasdss.ftpmusic.app.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsLeavesSmokeComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun mockViewModel(state: SettingsUiState = SettingsUiState()): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        every { vm.shareDiagnosticsEvents } returns emptyFlow()
        every { vm.appUpdateEvents } returns emptyFlow()
        every { vm.appVersionLabel() } returns "1.0"
        return vm
    }

    @Test
    fun `playback leaf shows continuous play toggle`() {
        val vm = mockViewModel(SettingsUiState(continuousPlayEnabled = true))
        composeRule.setContent { PlaybackSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_page_playback)).assertIsDisplayed()
        composeRule.onNodeWithTag("settings_toggle_continuous_play").performScrollTo().performClick()
        verify { vm.setContinuousPlayEnabled(false) }
    }

    @Test
    fun `bluetooth leaf shows resume toggle`() {
        val vm = mockViewModel()
        composeRule.setContent { BluetoothSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_page_bluetooth)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.bt_resume_toggle))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `cast leaf toggles cast from phone`() {
        val vm = mockViewModel(SettingsUiState(castFromPhone = false))
        composeRule.setContent { CastSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_page_cast)).assertIsDisplayed()
        composeRule.onNodeWithTag("settings_toggle_cast_from_phone").performScrollTo().performClick()
        verify { vm.setCastFromPhone(true) }
    }

    @Test
    fun `storage leaf shows clear cache anchor`() {
        val vm = mockViewModel()
        composeRule.setContent {
            StorageSettingsScreen(
                viewModel = vm,
                scrollToAnchor = SettingsAnchors.CLEAR_CACHE,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_page_storage)).assertIsDisplayed()
        composeRule.onNodeWithTag("settings_anchor_clear_cache").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_storage_clear_cache))
            .assertIsDisplayed()
    }

    @Test
    fun `network leaf toggles auto download playlists`() {
        val vm = mockViewModel(SettingsUiState(autoDownloadPlaylists = false))
        composeRule.setContent { NetworkSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_page_network)).assertIsDisplayed()
        composeRule.onNodeWithTag("settings_toggle_auto_download_playlists")
            .performScrollTo()
            .performClick()
        verify { vm.setAutoDownloadPlaylists(true) }
    }

    @Test
    fun `notifications leaf shows system status row`() {
        val vm = mockViewModel()
        composeRule.setContent { NotificationsSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_page_notifications))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings_notif_system_status").assertIsDisplayed()
    }

    @Test
    fun `advanced leaf shows custom headers section`() {
        val vm = mockViewModel()
        composeRule.setContent { AdvancedSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_page_advanced)).assertIsDisplayed()
        composeRule.onNodeWithTag("settings_anchor_custom_headers").performScrollTo().assertIsDisplayed()
        // SectionLabel uppercases section titles (A5).
        composeRule.onNodeWithText(
            context.getString(R.string.settings_advanced_headers).uppercase(),
        ).assertIsDisplayed()
    }
}
