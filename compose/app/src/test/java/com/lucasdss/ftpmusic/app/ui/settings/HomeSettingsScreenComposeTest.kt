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
class HomeSettingsScreenComposeTest {

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
    fun `home and favorites section renders all four toggles`() {
        val vm = mockViewModel(SettingsUiState())
        composeRule.setContent { HomeSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("HOME & FAVORITES").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Playlists on Home").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Favorite Artists").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Favorite Albums").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Favorite Radio").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `toggling playlists on home delegates to the view model`() {
        val vm = mockViewModel(SettingsUiState(showPlaylistsOnHome = true))
        composeRule.setContent { HomeSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Playlists on Home").performScrollTo()
        composeRule.onNodeWithTag("settings_toggle_Playlists_on_Home").performClick()
        verify { vm.setShowPlaylistsOnHome(false) }
    }

    @Test
    fun `toggling favorite radio delegates to the view model`() {
        val vm = mockViewModel(SettingsUiState(showFavRadioSection = true))
        composeRule.setContent { HomeSettingsScreen(viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Favorite Radio").performScrollTo()
        composeRule.onNodeWithTag("settings_toggle_Favorite_Radio").performClick()
        verify { vm.setShowFavRadioSection(false) }
    }
}
