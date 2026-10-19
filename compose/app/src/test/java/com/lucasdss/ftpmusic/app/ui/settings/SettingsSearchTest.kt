package com.lucasdss.ftpmusic.app.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsSearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockViewModel(state: SettingsUiState = SettingsUiState()): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        every { vm.shareDiagnosticsEvents } returns emptyFlow()
        every { vm.appUpdateEvents } returns emptyFlow()
        every { vm.appVersionLabel() } returns "1.0"
        return vm
    }

    @Test
    fun `empty query shows hub groups`() {
        composeRule.setContent { SettingsScreen(viewModel = mockViewModel()) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Account & server").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_hub_list").assertIsDisplayed()
    }

    @Test
    fun `keyword search navigates to matched route`() {
        var route: String? = null
        composeRule.setContent {
            SettingsScreen(
                viewModel = mockViewModel(),
                onNavigate = { route = it },
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_search").performTextInput("resync")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_nav_network").assertIsDisplayed().performClick()
        assert(route == SettingsRoutes.NETWORK)
    }

    @Test
    fun `no match shows empty state`() {
        composeRule.setContent { SettingsScreen(viewModel = mockViewModel()) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_search").performTextInput("zzzz-not-a-setting")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No settings match").assertIsDisplayed()
    }
}
