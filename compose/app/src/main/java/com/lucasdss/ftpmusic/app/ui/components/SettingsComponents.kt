package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.DestructiveRed
import com.lucasdss.ftpmusic.app.ui.Dimmed
import com.lucasdss.ftpmusic.app.ui.Muted
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.StatusOk
import com.lucasdss.ftpmusic.app.ui.StatusWarn
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.iconSmall
import com.lucasdss.ftpmusic.app.ui.settings.UpdateCheckUi
import com.lucasdss.ftpmusic.app.ui.spacingL
import com.lucasdss.ftpmusic.app.ui.spacingM
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.spacingXL
import com.lucasdss.ftpmusic.app.ui.spacingXS
import com.lucasdss.ftpmusic.app.ui.textBodyM
import com.lucasdss.ftpmusic.app.ui.textHeadingL
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelM
import com.lucasdss.ftpmusic.app.ui.textLabelS
import com.lucasdss.ftpmusic.app.ui.textMicro

/**
 * Shared Settings UI primitives (extracted from SettingsScreen for IA redesign).
 * Switch/radio use Material semantics; testTags stay label-derived for compat.
 */
@Composable
fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color(0xFF0D0D0D),
    unfocusedContainerColor = Color(0xFF0D0D0D),
    focusedBorderColor = Color(0xFF333333),
    unfocusedBorderColor = Color(0xFF222222),
    cursorColor = BrandTeal,
    focusedLabelColor = BrandTeal,
    unfocusedLabelColor = Muted,
)

@Composable
fun SectionLabel(title: String) {
    Text(
        title,
        color = Dimmed,
        fontSize = textLabelS(),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = spacingS(), start = spacingXS()),
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Surface),
        content = content,
    )
}

@Composable
fun SectionRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingM()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FittingText(
            text = label,
            color = Color.White,
            fontSize = textHeadingS(),
            minFontSize = textMicro(),
            modifier = Modifier.weight(1f),
            fillMaxWidth = false,
        )
        Spacer(Modifier.width(8.dp))
        FittingText(
            text = value,
            color = Muted,
            fontSize = textHeadingS(),
            minFontSize = textMicro(),
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            fillMaxWidth = true,
        )
    }
}

@Composable
fun SectionToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    testTagId: String? = null,
) {
    val tag = testTagId ?: "settings_toggle_${label.replace(" ", "_")}"
    val stateDesc = if (checked) "On" else "Off"
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(
                value = checked,
                onValueChange = onToggle,
                role = Role.Switch,
            )
            .padding(horizontal = spacingL(), vertical = spacingM())
            .semantics(mergeDescendants = true) {
                stateDescription = stateDesc
            }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            FittingText(
                text = label,
                color = Color.White,
                fontSize = textHeadingS(),
                minFontSize = textMicro(),
            )
            FittingText(
                text = subtitle,
                color = Dimmed,
                fontSize = textLabelM(),
                minFontSize = textMicro(),
                maxLines = 2,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // row toggleable owns the interaction + test clicks
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandTeal,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF333333),
            ),
        )
    }
}

@Composable
fun SectionDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222222)))
}

@Composable
fun OverwriteBehaviorOption(label: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = spacingL(), vertical = spacingM())
            .semantics {
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) BrandTeal else Color(0xFF444444), CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(BrandTeal))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FittingText(
                text = label,
                color = if (selected) BrandTeal else Color.White,
                fontSize = textHeadingS(),
                minFontSize = textMicro(),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            FittingText(
                text = subtitle,
                color = Dimmed,
                fontSize = textLabelM(),
                minFontSize = textMicro(),
                maxLines = 2,
            )
        }
    }
    SectionDivider()
}

@Composable
fun MetricRow(label: String, value: String, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = spacingS()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FittingText(
            text = label,
            color = Color.White,
            fontSize = textHeadingS(),
            minFontSize = textMicro(),
            modifier = Modifier.weight(1f),
            fillMaxWidth = false,
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            FittingText(
                text = value,
                color = Muted,
                fontSize = textHeadingS(),
                minFontSize = textMicro(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            if (subtitle != null) {
                FittingText(
                    text = subtitle,
                    color = Dimmed,
                    fontSize = textLabelM(),
                    minFontSize = textMicro(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

@Composable
fun SettingsNavRow(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = spacingL(), vertical = spacingM()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(iconSmall()))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = textHeadingS(), fontWeight = FontWeight.SemiBold)
            Text(summary, color = Muted, fontSize = textLabelM(), maxLines = 2)
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = NavUnselected,
            modifier = Modifier.size(iconSmall()),
        )
    }
}

@Composable
fun SystemStatusRow(
    title: String,
    subtitle: String,
    ok: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val badgeColor = if (ok) StatusOk else StatusWarn
    val badgeLabel = if (ok) "OK" else "Action needed"
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onOpen)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = spacingL(), vertical = spacingM()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = textHeadingS(), fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = textLabelM(), maxLines = 3)
        }
        Text(
            badgeLabel,
            color = badgeColor,
            fontSize = textLabelS(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Open system settings",
            tint = NavUnselected,
            modifier = Modifier.size(iconSmall()),
        )
    }
}

@Composable
fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailBackButton(onBack = onBack, inset = false)
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = textHeadingL(), fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(horizontal = spacingL(), vertical = spacingXL())) {
            Text(title, color = Color.White, fontSize = textHeadingL(), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(message, color = Color(0xFFAAAAAA), fontSize = textBodyM(), lineHeight = 20.sp)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingS())) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                ) { Text("Cancel") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DestructiveRed),
                ) { Text(confirmLabel, color = Color.White) }
            }
            Spacer(Modifier.height(spacingXL()))
        }
    }
}

@Composable
fun UpdateCheckRow(
    updateCheck: UpdateCheckUi,
    onCheck: () -> Unit,
    onStartUpdate: () -> Unit,
    onCompleteUpdate: () -> Unit,
    onOpenPlayStore: () -> Unit,
) {
    when (updateCheck) {
        is UpdateCheckUi.Available -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onStartUpdate)
                    .padding(horizontal = spacingL(), vertical = spacingM())
                    .testTag("settings_start_update"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.SystemUpdate, null, tint = BrandTeal, modifier = Modifier.size(iconSmall()))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Update available", color = Color.White, fontSize = textHeadingS(), fontWeight = FontWeight.SemiBold)
                    Text(
                        "Version code ${updateCheck.availableVersionCode} on Play Store",
                        color = Muted,
                        fontSize = textLabelM(),
                    )
                }
            }
        }
        is UpdateCheckUi.InProgress -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingL(), vertical = spacingM())
                    .testTag("settings_update_in_progress"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(iconSmall()), color = BrandTeal, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Downloading update…", color = Color.White, fontSize = textHeadingS(), fontWeight = FontWeight.SemiBold)
                    Text("Version code ${updateCheck.availableVersionCode}", color = Muted, fontSize = textLabelM())
                }
            }
        }
        UpdateCheckUi.ReadyToInstall -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCompleteUpdate)
                    .padding(horizontal = spacingL(), vertical = spacingM())
                    .testTag("settings_complete_update"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.SystemUpdate, null, tint = BrandTeal, modifier = Modifier.size(iconSmall()))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Restart to install", color = Color.White, fontSize = textHeadingS(), fontWeight = FontWeight.SemiBold)
                    Text("Update downloaded · tap to finish", color = Muted, fontSize = textLabelM())
                }
            }
        }
        UpdateCheckUi.Checking -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingL(), vertical = spacingM())
                    .testTag("settings_check_updates"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(iconSmall()), color = BrandTeal, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Checking for updates…", color = Color.White, fontSize = textHeadingS(), fontWeight = FontWeight.SemiBold)
                    Text("Asking Play Store", color = Muted, fontSize = textLabelM())
                }
            }
        }
        else -> {
            val subtitle = when (updateCheck) {
                UpdateCheckUi.UpToDate -> "You're up to date"
                is UpdateCheckUi.Error -> updateCheck.message
                else -> "Play Store only · tap to check"
            }
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCheck)
                        .padding(horizontal = spacingL(), vertical = spacingM())
                        .testTag("settings_check_updates"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, null, tint = BrandTeal, modifier = Modifier.size(iconSmall()))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Check for updates", color = Color.White, fontSize = textHeadingS(), fontWeight = FontWeight.SemiBold)
                        Text(subtitle, color = Muted, fontSize = textLabelM())
                    }
                }
                if (updateCheck is UpdateCheckUi.Error) {
                    TextButton(
                        onClick = onOpenPlayStore,
                        modifier = Modifier
                            .padding(start = spacingL(), bottom = spacingS())
                            .testTag("settings_open_play_store"),
                    ) {
                        Text("Open Play Store", color = BrandTeal, fontSize = textLabelM())
                    }
                }
            }
        }
    }
}

fun timeAgo(ms: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ms
    if (diff < 0) return "just now"
    val seconds = diff / 1000
    if (seconds < 60) return "${seconds}s ago"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return "${days}d ago"
}

fun formatDurationMs(ms: Long): String {
    val seconds = ms / 1000
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (remainingSeconds > 0) "${minutes}m ${remainingSeconds}s" else "${minutes}m"
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024
    if (mb < 1024) return "$mb MB"
    return "${mb / 1024} GB"
}

fun openPlayStoreListing(context: android.content.Context, packageName: String) {
    val market = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("market://details?id=$packageName"),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: android.content.ActivityNotFoundException) {
        try {
            context.startActivity(web)
        } catch (_: android.content.ActivityNotFoundException) {
            // No browser / store — ignore.
        }
    }
}
