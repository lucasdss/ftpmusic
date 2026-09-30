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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `label centerX matches icon centerX when labels shown`() {
        composeRule.setContent {
            TestBottomBar(hideNavLabels = false)
        }
        composeRule.waitForIdle()

        val iconBounds = composeRule.onNodeWithTag("nav_icon_Home", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val labelBounds = composeRule.onNodeWithTag("nav_label_Home", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        val iconCenterX = (iconBounds.left + iconBounds.right) / 2f
        val labelCenterX = (labelBounds.left + labelBounds.right) / 2f
        val density = composeRule.density
        val tolerancePx = with(density) { 2.dp.toPx() }

        assertTrue(
            "nav label centerX ($labelCenterX) must align with icon centerX ($iconCenterX) " +
                "within ${tolerancePx}px; delta=${abs(iconCenterX - labelCenterX)}",
            abs(iconCenterX - labelCenterX) < tolerancePx,
        )
        // Sanity: both nodes laid out (non-zero width).
        assertTrue(iconBounds.width > 0f)
        assertTrue(labelBounds.width > 0f)
    }
}

/**
 * Mirrors prod NavigationBarItem label wiring from FtpmusicNavHost
 * (fillMaxWidth + TextAlign.Center + Medium weight). Tags are harness-only.
 */
@Composable
private fun TestBottomBar(hideNavLabels: Boolean) {
    val label = "Home"
    NavigationBar {
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = navTabIconContentDescription(hideNavLabels, label),
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("nav_icon_Home"),
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
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        fillMaxWidth = true,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("nav_label_Home"),
                    )
                }
            },
            selected = true,
            onClick = {},
        )
    }
}
