package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Foreground
import com.lucasdss.ftpmusic.app.ui.Muted
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.issues.AppIssue
import com.lucasdss.ftpmusic.app.ui.issues.AppIssueAction
import com.lucasdss.ftpmusic.app.ui.issues.AppIssueKind
import com.lucasdss.ftpmusic.app.ui.spacingL
import com.lucasdss.ftpmusic.app.ui.spacingM
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelL
import com.lucasdss.ftpmusic.app.ui.textLabelM

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueStatusSheet(issues: List<AppIssue>, onDismiss: () -> Unit, onAction: (AppIssue, AppIssueAction) -> Unit) {
    if (issues.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        modifier = Modifier.testTag("issue_status_sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacingL())
                .padding(bottom = spacingL())
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.issue_status_sheet_title),
                color = Foreground,
                fontSize = textHeadingS(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(spacingM()))
            issues.forEach { issue ->
                IssueStatusRow(
                    issue = issue,
                    onAction = { action -> onAction(issue, action) },
                )
                Spacer(Modifier.height(spacingS()))
            }
        }
    }
}

@Composable
private fun IssueStatusRow(issue: AppIssue, onAction: (AppIssueAction) -> Unit) {
    val actionLabel: String? = actionLabelFor(issue.action, issue.kind)
    val secondary = issue.secondaryAction
    val secondaryLabel: String? = secondary?.let { actionLabelFor(it, issue.kind) }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("issue_status_row_${issue.id}"),
    ) {
        Text(
            text = issue.title,
            color = Foreground,
            fontSize = textLabelL(),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(spacingS()))
        Text(
            text = issue.body,
            color = Muted,
            fontSize = textLabelM(),
        )
        if (actionLabel != null || (secondary != null && secondaryLabel != null)) {
            Row {
                if (actionLabel != null) {
                    TextButton(
                        onClick = { onAction(issue.action) },
                        modifier = Modifier.testTag("issue_status_action_${issue.id}"),
                    ) {
                        Text(actionLabel, color = BrandTeal, fontSize = textLabelM())
                    }
                }
                if (secondary != null && secondaryLabel != null) {
                    TextButton(
                        onClick = { onAction(secondary) },
                        modifier = Modifier.testTag("issue_status_secondary_${issue.id}"),
                    ) {
                        Text(secondaryLabel, color = BrandTeal, fontSize = textLabelM())
                    }
                }
            }
        }
    }
}

@Composable
private fun actionLabelFor(action: AppIssueAction, kind: AppIssueKind): String? = when (action) {
    AppIssueAction.OpenSettings ->
        if (kind == AppIssueKind.UPDATE_AVAILABLE) {
            stringResource(R.string.issue_action_update)
        } else {
            stringResource(R.string.issue_action_settings)
        }

    AppIssueAction.DismissPlaybackError -> stringResource(R.string.issue_action_dismiss)

    is AppIssueAction.DismissUpdateReminder -> stringResource(R.string.issue_action_not_now)

    is AppIssueAction.OpenPlaylist -> stringResource(R.string.issue_action_open_playlist)

    AppIssueAction.None -> null
}
