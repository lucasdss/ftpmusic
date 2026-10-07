package com.lucasdss.ftpmusic.app.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose smoke for SongListRow layout (ADR 0096). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class SongListRowComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `formatSongDuration pads seconds`() {
        assertEquals("3:05", formatSongDuration(185))
        assertEquals("0:09", formatSongDuration(9))
    }

    @Test
    fun `renders title duration and more affordance`() {
        composeRule.setContent {
            SongListRow(
                title = "Fixed Size Title",
                subtitle = "Artist Name",
                downloadStatus = "cached",
                durationLabel = "3:05",
                isLiked = false,
                isDisliked = false,
                onLike = {},
                onDislike = {},
                onMore = {},
            )
        }

        composeRule.onNodeWithText("Fixed Size Title").assertIsDisplayed()
        composeRule.onNodeWithText("Artist Name").assertIsDisplayed()
        composeRule.onNodeWithText("3:05").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Track menu").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("cached").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Like").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Dislike").assertIsDisplayed()
    }
}
