package com.lucasdss.ftpmusic.app.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.android.gms.cast.CastDevice
import com.lucasdss.ftpmusic.app.ui.player.CastButtonState
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppHeaderComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        CastButtonState.showDialog.value = false
        CastButtonState.isCasting.value = false
        CastButtonState.discoveredDevices.value = listOf(mockk<CastDevice>(relaxed = true))
    }

    @After
    fun tearDown() {
        CastButtonState.showDialog.value = false
        CastButtonState.isCasting.value = false
        CastButtonState.discoveredDevices.value = emptyList()
    }

    @Test
    fun `settings gear invokes callback when provided`() {
        var clicked = false
        composeRule.setContent {
            AppHeader(onSettingsClick = { clicked = true })
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_header_settings").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun `settings gear hidden when callback null`() {
        composeRule.setContent {
            AppHeader(onSettingsClick = null)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_header_settings").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("FTP Music").assertIsDisplayed()
    }

    @Test
    fun `cast button ignores taps when interactive false`() {
        composeRule.setContent {
            AppHeader(interactive = false, onSettingsClick = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Cast to device").performClick()
        assertFalse(CastButtonState.showDialog.value)
    }

    @Test
    fun `cast button opens dialog when interactive`() {
        composeRule.setContent {
            AppHeader(interactive = true, onSettingsClick = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Cast to device").performClick()
        assertTrue(CastButtonState.showDialog.value)
    }
}
