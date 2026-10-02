package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RadioBookmarkIconTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `bookmarked shows unbookmark and clicks`() {
        var clicked = false
        composeRule.setContent {
            RadioBookmarkIcon(
                bookmarked = true,
                onClick = { clicked = true },
                modifier = Modifier.testTag("bm"),
            )
        }
        composeRule.onNodeWithContentDescription("Unbookmark station").assertIsDisplayed()
        composeRule.onNodeWithTag("bm").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `not bookmarked shows bookmark cd`() {
        composeRule.setContent {
            RadioBookmarkIcon(bookmarked = false, onClick = {})
        }
        composeRule.onNodeWithContentDescription("Bookmark station").assertIsDisplayed()
    }
}
