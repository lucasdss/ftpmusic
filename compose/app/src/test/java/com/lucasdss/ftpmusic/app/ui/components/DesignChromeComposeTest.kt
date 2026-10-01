package com.lucasdss.ftpmusic.app.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DetailBackButtonComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `inset back button invokes onBack`() {
        var clicked = false
        composeRule.setContent {
            DetailBackButton(onBack = { clicked = true }, inset = true)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun `non-inset back button invokes onBack`() {
        var clicked = false
        composeRule.setContent {
            DetailBackButton(onBack = { clicked = true }, inset = false)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SegmentedChipComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `selected and idle chips render and click`() {
        var selected = "Liked"
        composeRule.setContent {
            SegmentedChipRow {
                SegmentedChip(
                    label = "Liked",
                    selected = selected == "Liked",
                    onClick = { selected = "Liked" },
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                )
                SegmentedChip(
                    label = "Disliked",
                    selected = selected == "Disliked",
                    onClick = { selected = "Disliked" },
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Liked").assertIsDisplayed()
        composeRule.onNodeWithText("Disliked").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }
}
