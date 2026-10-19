package com.lucasdss.ftpmusic.app.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class AboutSettingsScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockViewModel(state: SettingsUiState): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        every { vm.shareDiagnosticsEvents } returns emptyFlow()
        every { vm.appUpdateEvents } returns emptyFlow()
        every { vm.appVersionLabel() } returns "1.9.0 (13)"
        return vm
    }

    @Test
    fun `check for updates row visible and delegates`() {
        val vm = mockViewModel(SettingsUiState())
        composeRule.setContent { AboutSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_check_updates").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_check_updates").performClick()
        verify { vm.checkForUpdates() }
    }

    @Test
    fun `update available row shows start update CTA`() {
        val vm = mockViewModel(
            SettingsUiState(
                updateCheck = UpdateCheckUi.Available(
                    availableVersionCode = 20,
                    flexibleAllowed = true,
                ),
            ),
        )
        composeRule.setContent { AboutSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_start_update").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Update available").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_start_update").performClick()
        verify { vm.startUpdate() }
    }

    @Test
    fun `update check error shows open play store`() {
        val vm = mockViewModel(
            SettingsUiState(updateCheck = UpdateCheckUi.Error("No network")),
        )
        composeRule.setContent { AboutSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_open_play_store").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_open_play_store").performClick()
        verify { vm.openPlayStoreListing() }
    }

    @Test
    fun `update in progress row shows downloading state`() {
        val vm = mockViewModel(
            SettingsUiState(updateCheck = UpdateCheckUi.InProgress(availableVersionCode = 21)),
        )
        composeRule.setContent { AboutSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_update_in_progress").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Downloading update…").assertIsDisplayed()
    }

    @Test
    fun `ready to install row delegates to completeFlexibleUpdate`() {
        val vm = mockViewModel(
            SettingsUiState(updateCheck = UpdateCheckUi.ReadyToInstall),
        )
        composeRule.setContent { AboutSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_complete_update").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_complete_update").performClick()
        verify { vm.completeFlexibleUpdate() }
    }
}
