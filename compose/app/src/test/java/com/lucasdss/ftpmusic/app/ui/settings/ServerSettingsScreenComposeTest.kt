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
class ServerSettingsScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockViewModel(state: SettingsUiState): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        every { vm.shareDiagnosticsEvents } returns emptyFlow()
        every { vm.appUpdateEvents } returns emptyFlow()
        return vm
    }

    @Test
    fun `private HTTP server displays credential warning`() {
        val vm = mockViewModel(SettingsUiState(serverUrl = "http://192.168.1.20"))
        composeRule.setContent { ServerSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            "HTTP exposes credentials in transit. Use only with a trusted private-network server.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `failed server settings validation does not navigate`() {
        val vm = mockViewModel(SettingsUiState())
        every { vm.saveServerSettings() } returns false
        var saved = false
        composeRule.setContent {
            ServerSettingsScreen(viewModel = vm, onServerSettingsSaved = { saved = true })
        }

        composeRule.onNodeWithTag("settings_server_save").performScrollTo().performClick()

        assert(!saved)
        verify { vm.saveServerSettings() }
    }
}
