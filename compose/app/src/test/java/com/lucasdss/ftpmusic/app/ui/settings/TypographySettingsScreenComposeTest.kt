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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TypographySettingsScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockViewModel(state: SettingsUiState): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        return vm
    }

    @Test
    fun `density chips and preview render`() {
        val vm = mockViewModel(SettingsUiState())
        composeRule.setContent { TypographySettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_typography_screen").assertExists()
        composeRule.onNodeWithTag("settings_typography_section").assertExists()
        composeRule.onNodeWithTag("settings_typography_density_tiny").assertExists()
        composeRule.onNodeWithTag("settings_typography_preview_heading").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_typography_heading_slider").assertExists()
    }

    @Test
    fun `density chip delegates to view model`() {
        val vm = mockViewModel(SettingsUiState())
        composeRule.setContent { TypographySettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_typography_density_tiny").performClick()
        verify { vm.setDensity(com.lucasdss.ftpmusic.app.ui.UiDensityPreset.Tiny) }
    }

    @Test
    fun `reset and weight option delegate`() {
        val vm = mockViewModel(SettingsUiState())
        composeRule.setContent { TypographySettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_typography_reset").performScrollTo().performClick()
        verify { vm.resetTypographyPrefs() }

        composeRule.onNodeWithText("Bold").performScrollTo().performClick()
        verify {
            vm.setWeightBias(com.lucasdss.ftpmusic.app.ui.PrimaryWeightBias.Bold)
        }
    }
}
