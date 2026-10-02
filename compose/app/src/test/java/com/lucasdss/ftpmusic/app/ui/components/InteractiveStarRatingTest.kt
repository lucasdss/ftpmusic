package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InteractiveStarRatingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders five stars and rates on click`() {
        var rated = 0
        composeRule.setContent {
            InteractiveStarRating(
                rating = 2,
                onRate = { rated = it },
                modifier = Modifier.testTag("stars"),
            )
        }
        composeRule.onNodeWithContentDescription("Rate 3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rate 3").performClick()
        assertEquals(3, rated)
        composeRule.onNodeWithTag("stars").assertIsDisplayed()
    }

    @Test
    fun `compact expandTouchTarget false still rates`() {
        var rated = 0
        composeRule.setContent {
            InteractiveStarRating(
                rating = 0,
                onRate = { rated = it },
                expandTouchTarget = false,
            )
        }
        composeRule.onNodeWithContentDescription("Rate 5").performClick()
        assertEquals(5, rated)
    }
}
