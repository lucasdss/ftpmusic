package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.OfflineYellow
import com.lucasdss.ftpmusic.app.ui.adp
import com.lucasdss.ftpmusic.app.ui.issues.AppIssueHeaderState
import com.lucasdss.ftpmusic.app.ui.issues.AppIssueIconKind
import com.lucasdss.ftpmusic.app.ui.textMicro

private val ConflictOrange = Color(0xFFFF8C00)

/**
 * Compact AppHeader status glyph (ADR-0120). Hidden when [state].issues empty.
 */
@Composable
fun IssueStatusIcon(
    state: AppIssueHeaderState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    if (!state.visible) return
    val tint = when (state.iconKind) {
        AppIssueIconKind.CLOUD_OFF_MUTED -> NavUnselected
        AppIssueIconKind.SYNC_PROBLEM -> ConflictOrange
        else -> OfflineYellow
    }
    val image = when (state.iconKind) {
        AppIssueIconKind.WIFI_OFF -> Icons.Default.WifiOff
        AppIssueIconKind.CLOUD_OFF, AppIssueIconKind.CLOUD_OFF_MUTED -> Icons.Default.CloudOff
        AppIssueIconKind.WARNING -> Icons.Default.Warning
        AppIssueIconKind.ERROR_OUTLINE -> Icons.Default.ErrorOutline
        AppIssueIconKind.SYNC_PROBLEM -> Icons.Default.SyncProblem
        AppIssueIconKind.SYSTEM_UPDATE -> Icons.Default.SystemUpdate
    }
    Box(
        modifier = modifier
            .size(minTouchTarget())
            .clip(CircleShape)
            .testTag("app_header_issue_status")
            .semantics { contentDescription = state.contentDescription }
            .clickable(enabled = interactive, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = image,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(adp(22f)),
        )
        if (state.showBadge) {
            val label = if (state.badgeCount > 9) "9+" else state.badgeCount.toString()
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(OfflineYellow)
                    .testTag("app_header_issue_badge"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = Color.Black,
                    fontSize = textMicro(),
                    fontWeight = FontWeight.Bold,
                    lineHeight = 10.sp,
                )
            }
        }
    }
}
