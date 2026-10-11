package com.lucasdss.ftpmusic.app.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.lucasdss.ftpmusic.app.ui.issues.AppIssue
import com.lucasdss.ftpmusic.app.ui.issues.AppIssueHeaderState
import com.lucasdss.ftpmusic.app.ui.issues.AppIssueIconKind
import com.lucasdss.ftpmusic.app.ui.issues.AppIssueKind
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class IssueStatusIconComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleState(count: Int = 1, icon: AppIssueIconKind = AppIssueIconKind.CLOUD_OFF): AppIssueHeaderState {
        val issues = (1..count).map {
            AppIssue(
                id = "i$it",
                kind = if (count == 1) AppIssueKind.SERVER_UNREACHABLE else AppIssueKind.NO_OS_NETWORK,
                title = "Issue $it",
                body = "Body $it",
            )
        }
        return AppIssueHeaderState(
            issues = issues,
            iconKind = if (count >= 2) AppIssueIconKind.WARNING else icon,
            showBadge = count >= 2,
            badgeCount = count,
            contentDescription = if (count == 1) "Alert: Issue 1" else "$count alerts. Primary: Issue 1",
        )
    }

    @Test
    fun `hidden when no issues`() {
        composeRule.setContent {
            IssueStatusIcon(
                state = AppIssueHeaderState(
                    issues = emptyList(),
                    iconKind = AppIssueIconKind.WARNING,
                    showBadge = false,
                    badgeCount = 0,
                    contentDescription = "No alerts",
                ),
                onClick = {},
            )
        }
        composeRule.onNodeWithTag("app_header_issue_status").assertDoesNotExist()
    }

    @Test
    fun `visible and clickable when issues present`() {
        var clicked = false
        composeRule.setContent {
            IssueStatusIcon(state = sampleState(), onClick = { clicked = true })
        }
        composeRule.onNodeWithTag("app_header_issue_status").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun `badge shown for multiple issues`() {
        composeRule.setContent {
            IssueStatusIcon(state = sampleState(count = 3), onClick = {})
        }
        composeRule.onNodeWithTag("app_header_issue_badge", useUnmergedTree = true).assertExists()
    }
}
