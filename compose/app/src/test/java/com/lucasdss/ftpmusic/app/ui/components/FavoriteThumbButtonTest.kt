package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** Render tests for the shared album/artist like-dislike thumb button. */
@RunWith(RobolectricTestRunner::class)
class FavoriteThumbButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders with content description and responds to click`() {
        var clicked = false
        composeRule.setContent {
            FavoriteThumbButton(
                icon = Icons.Filled.ThumbUp,
                active = false,
                contentDescription = "Like album",
                onClick = { clicked = true },
                modifier = Modifier.testTag("thumb"),
            )
        }
        composeRule.onNodeWithContentDescription("Like album").assertIsDisplayed()
        composeRule.onNodeWithTag("thumb").performClick()
        assertTrue("click must fire", clicked)
    }

    @Test
    fun `renders in active state with active tint`() {
        composeRule.setContent {
            FavoriteThumbButton(
                icon = Icons.Filled.ThumbUp,
                active = true,
                contentDescription = "Unlike album",
                onClick = {},
                activeTint = Color(0xFFE84040),
            )
        }
        composeRule.onNodeWithContentDescription("Unlike album").assertIsDisplayed()
    }
}
