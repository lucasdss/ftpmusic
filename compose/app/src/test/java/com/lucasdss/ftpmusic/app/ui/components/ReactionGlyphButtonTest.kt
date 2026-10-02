package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReactionGlyphButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders and clicks`() {
        var clicked = false
        composeRule.setContent {
            ReactionGlyphButton(
                icon = Icons.Filled.ThumbUp,
                contentDescription = "Like track",
                tint = Color.White,
                onClick = { clicked = true },
            )
        }
        composeRule.onNodeWithContentDescription("Like track").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Like track").performClick()
        assertTrue(clicked)
    }
}
