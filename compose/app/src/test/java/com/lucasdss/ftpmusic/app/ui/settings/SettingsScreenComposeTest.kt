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

/**
 * Compose tests for the Settings "Home & Favorites" section: all four
 * visibility toggles render with their labels and delegate to the ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockViewModel(state: SettingsUiState): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        return vm
    }

    private fun render(vm: SettingsViewModel) {
        composeRule.setContent {
            SettingsScreen(viewModel = vm)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `home and favorites section renders all four toggles`() {
        val vm = mockViewModel(SettingsUiState())
        render(vm)

        composeRule.onNodeWithText("HOME & FAVORITES").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Playlists on Home").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Favorite Artists").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Favorite Albums").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Favorite Radio").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `toggling playlists on home delegates to the view model`() {
        val vm = mockViewModel(SettingsUiState(showPlaylistsOnHome = true))
        render(vm)

        composeRule.onNodeWithText("Playlists on Home").performScrollTo()
        composeRule.onNodeWithTag("settings_toggle_Playlists_on_Home").performClick()
        verify { vm.setShowPlaylistsOnHome(false) }
    }

    @Test
    fun `toggling favorite radio delegates to the view model`() {
        val vm = mockViewModel(SettingsUiState(showFavRadioSection = true))
        render(vm)

        composeRule.onNodeWithText("Favorite Radio").performScrollTo()
        composeRule.onNodeWithTag("settings_toggle_Favorite_Radio").performClick()
        verify { vm.setShowFavRadioSection(false) }
    }

    @Test
    fun `remote library management row is absent after feature removal`() {
        val vm = mockViewModel(SettingsUiState())
        render(vm)

        // Regression guard for the v1.0.0 removal of the yt-dlp remote library
        // feature: the entry point must not resurface in Settings.
        composeRule.onNodeWithText("Manage Remote Library").assertDoesNotExist()
        composeRule.onNodeWithText("Search your remote library and add music").assertDoesNotExist()
    }

    @Test
    fun `private HTTP server displays credential warning`() {
        val vm = mockViewModel(SettingsUiState(serverUrl = "http://192.168.1.20"))
        render(vm)

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
            SettingsScreen(viewModel = vm, onServerSettingsSaved = { saved = true })
        }

        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        assert(!saved)
        verify { vm.saveServerSettings() }
    }
}
