package com.lucasdss.ftpmusic.app.ui.components

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `section toggle uses label-derived test tag and switch`() {
        var checked = true
        composeRule.setContent {
            var value by remember { mutableStateOf(true) }
            SectionToggleRow(
                label = "Continuous Play",
                subtitle = "subtitle",
                checked = value,
                onToggle = {
                    value = it
                    checked = it
                },
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_toggle_Continuous_Play").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_toggle_Continuous_Play").assertIsOn()
        composeRule.onNodeWithTag("settings_toggle_Continuous_Play").performClick()
        composeRule.waitForIdle()
        assertEquals(false, checked)
        composeRule.onNodeWithTag("settings_toggle_Continuous_Play").assertIsOff()
    }

    @Test
    fun `explicit testTagId overrides label tag`() {
        composeRule.setContent {
            SectionToggleRow(
                label = "Foo Bar",
                subtitle = "s",
                checked = false,
                onToggle = {},
                testTagId = "settings_toggle_custom",
            )
        }
        composeRule.onNodeWithTag("settings_toggle_custom").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_toggle_Foo_Bar").assertDoesNotExist()
    }

    @Test
    fun `formatDurationMs and formatBytes helpers`() {
        assertEquals("1m 30s", formatDurationMs(90_000L))
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("just now", timeAgo(System.currentTimeMillis() + 1000))
    }

    @Test
    fun `settings page scaffold shows title`() {
        composeRule.setContent {
            SettingsPageScaffold(title = "Playback", onBack = {}) {
                Text("body")
            }
        }
        composeRule.onNodeWithText("Playback").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_back_button").assertIsDisplayed()
    }
}
