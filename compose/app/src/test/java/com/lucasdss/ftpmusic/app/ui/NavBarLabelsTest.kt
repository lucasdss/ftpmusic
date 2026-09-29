package com.lucasdss.ftpmusic.app.ui

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavBarLabelsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `icon contentDescription only when labels hidden`() {
        assertEquals("Home", navTabIconContentDescription(hideNavLabels = true, label = "Home"))
        assertNull(navTabIconContentDescription(hideNavLabels = false, label = "Home"))
    }

    @Test
    fun `hide labels removes tab text keeps icon contentDescription`() {
        composeRule.setContent {
            TestBottomBar(hideNavLabels = true)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Home").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Home").assertIsDisplayed()
    }

    @Test
    fun `show labels renders tab text`() {
        composeRule.setContent {
            TestBottomBar(hideNavLabels = false)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Home").assertDoesNotExist()
    }
}

@Composable
private fun TestBottomBar(hideNavLabels: Boolean) {
    val label = "Home"
    NavigationBar {
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = navTabIconContentDescription(hideNavLabels, label),
                    modifier = Modifier.size(22.dp),
                )
            },
            label = if (hideNavLabels) {
                null
            } else {
                {
                    FittingText(
                        text = label,
                        fontSize = textLabelM(),
                        minFontSize = textMicro(),
                        maxLines = 1,
                    )
                }
            },
            selected = true,
            onClick = {},
        )
    }
}
