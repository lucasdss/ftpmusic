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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose tests for Settings hub (nav-only root).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsScreenComposeTest {

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

    private fun render(vm: SettingsViewModel, onNavigate: (String) -> Unit = {}) {
        composeRule.setContent {
            SettingsScreen(viewModel = vm, onNavigate = onNavigate)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `remote library management row is absent after feature removal`() {
        val vm = mockViewModel(SettingsUiState())
        render(vm)

        composeRule.onNodeWithText("Manage Remote Library").assertDoesNotExist()
        composeRule.onNodeWithText("Search your remote library and add music").assertDoesNotExist()
    }

    @Test
    fun `dark mode and accent stubs are absent`() {
        val vm = mockViewModel(SettingsUiState())
        render(vm)
        composeRule.onNodeWithText("Dark Mode").assertDoesNotExist()
        composeRule.onNodeWithText("Accent Color").assertDoesNotExist()
    }

    @Test
    fun `typography nav row opens detail callback`() {
        val vm = mockViewModel(SettingsUiState())
        var opened = false
        composeRule.setContent {
            SettingsScreen(viewModel = vm, onTypography = { opened = true })
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_typography_nav").performScrollTo().performClick()
        assert(opened)
        composeRule.onNodeWithText("Text & display size").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_typography_section").assertDoesNotExist()
    }

    @Test
    fun `profile nav row invokes onProfile`() {
        val vm = mockViewModel(SettingsUiState())
        var profileClicked = false
        composeRule.setContent {
            SettingsScreen(viewModel = vm, onProfile = { profileClicked = true })
        }
        composeRule.onNodeWithTag("settings_nav_profile").performScrollTo().performClick()
        assert(profileClicked)
    }

    @Test
    fun `back button invokes onBack`() {
        val vm = mockViewModel(SettingsUiState())
        var invoked = false
        composeRule.setContent {
            SettingsScreen(viewModel = vm, onBack = { invoked = true })
        }
        composeRule.onNodeWithTag("detail_back_button").assertIsDisplayed().performClick()
        assert(invoked)
    }

    @Test
    fun `hub navigates to server route`() {
        val vm = mockViewModel(SettingsUiState())
        var route: String? = null
        render(vm) { route = it }
        composeRule.onNodeWithTag("settings_nav_server").performScrollTo().performClick()
        assert(route == SettingsRoutes.SERVER)
    }

    @Test
    fun `search field is displayed`() {
        val vm = mockViewModel(SettingsUiState())
        render(vm)
        composeRule.onNodeWithTag("settings_search").assertIsDisplayed()
    }
}
