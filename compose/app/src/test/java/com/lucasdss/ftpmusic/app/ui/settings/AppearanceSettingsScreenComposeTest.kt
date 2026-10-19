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
class AppearanceSettingsScreenComposeTest {

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
    fun `hide navigation labels toggle delegates to the view model`() {
        val vm = mockViewModel(SettingsUiState(hideNavLabels = false))
        composeRule.setContent { AppearanceSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Hide navigation labels").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_toggle_Hide_navigation_labels").performClick()
        verify { vm.setHideNavLabels(true) }
    }

    @Test
    fun `list reactions toggle delegates to the view model`() {
        val vm = mockViewModel(SettingsUiState())
        composeRule.setContent { AppearanceSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Like and dislike on track lists").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_toggle_Like_and_dislike_on_track_lists").performClick()
        verify { vm.setShowListReactions(false) }
    }

    @Test
    fun `list duration toggle delegates to the view model`() {
        val vm = mockViewModel(SettingsUiState())
        composeRule.setContent { AppearanceSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Duration on track lists").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_toggle_Duration_on_track_lists").performClick()
        verify { vm.setShowListDuration(false) }
    }
}
