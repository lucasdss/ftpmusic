package com.lucasdss.ftpmusic.app.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FittingTextComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders long unbreakable string in narrow width without crash`() {
        val longWord = "Supercalifragilisticexpialidocious"
        composeRule.setContent {
            Box(Modifier.width(72.dp)) {
                FittingText(
                    text = longWord,
                    fontSize = 16.sp,
                    minFontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(longWord, substring = true).assertIsDisplayed()
    }

    @Test
    fun `renders short label at start size`() {
        composeRule.setContent {
            Box(Modifier.width(200.dp)) {
                FittingText(
                    text = "Home",
                    fontSize = 12.sp,
                    minFontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
    }
}
