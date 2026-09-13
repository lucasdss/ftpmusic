package com.lucasdss.ftpmusic.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.cast.CastDevice
import com.lucasdss.ftpmusic.app.ui.*

/**
 * Cast device picker — ModalBottomSheet per design spec.
 *
 * Design-v4 from ftpmusic-design:
 * - BottomSheet with drag handle
 * - Header: 36dp teal circle + Cast icon + "Cast to device" + subtitle
 * - Device rows: 36dp teal circle + Cast icon + name + Wifi icon
 * - Connected device first with "connected" label
 * - Disconnect + Cancel buttons at bottom when casting
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDevicePickerDialog(
    devices: List<CastDevice>,
    isCasting: Boolean = false,
    connectedDeviceName: String? = null,
    connectingDeviceName: String? = null,
    onDismiss: () -> Unit,
    onDeviceSelected: (CastDevice) -> Unit,
    onDisconnect: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C2E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF333333)) },
    ) {
        Column(modifier = Modifier.padding(bottom = spacing3XL())) {
            // ── Header ──
            Row(
                Modifier.padding(horizontal = spacingXL()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(adp(36f)).clip(CircleShape)
                        .background(Color(0xFF00C8B4).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Cast, null, tint = Color(0xFF00C8B4), modifier = Modifier.size(adp(18f)))
                }
                Spacer(Modifier.width(spacingM()))
                Column {
                    Text(
                        "Cast to device",
                        color = Color.White,
                        fontSize = textHeadingS(),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (devices.isEmpty()) "Scanning for devices\u2026" else "Available on your network",
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                    )
                }
            }

            Spacer(Modifier.height(spacingL()))

            // ── Connecting state ──
            if (connectingDeviceName != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacingXL(), vertical = spacingM()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00C8B4),
                        modifier = Modifier.size(adp(20f)),
                        strokeWidth = adp(2f),
                    )
                    Spacer(Modifier.width(spacingM()))
                    Text(
                        "Connecting to $connectingDeviceName\u2026",
                        color = Color(0xFFCCCCCC),
                        fontSize = textBodyM(),
                    )
                }
                Spacer(Modifier.height(spacingS()))
            }

            // ── Device list ──
            if (devices.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacingXL(), vertical = spacing3XL()),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00C8B4),
                        modifier = Modifier.size(adp(32f)),
                        strokeWidth = adp(2f),
                    )
                    Spacer(Modifier.width(spacingM()))
                    Text("Looking for devices\u2026", color = Color(0xFF666666), fontSize = textBodyM())
                }
            } else {
                val sortedDevices = if (isCasting && connectedDeviceName != null) {
                    val connected = devices.filter { it.friendlyName == connectedDeviceName }
                    val others = devices.filter { it.friendlyName != connectedDeviceName }
                    connected + others
                } else {
                    devices
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = adp(320f)),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(sortedDevices) { device ->
                        val isConnected = isCasting && device.friendlyName == connectedDeviceName
                        DeviceRow(
                            device = device,
                            isConnected = isConnected,
                            onClick = {
                                if (isConnected) {
                                    onDisconnect()
                                    onDismiss()
                                } else {
                                    onDeviceSelected(device) /* don't dismiss — wait for success/failure */
                                }
                            },
                        )
                    }
                }
            }

            // ── Bottom buttons ──
            Spacer(Modifier.height(spacingL()))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacingXL()),
                horizontalArrangement = Arrangement.spacedBy(spacingM()),
            ) {
                if (isCasting) {
                    OutlinedButton(
                        onClick = {
                            onDisconnect()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(adp(48f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE84040)),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = SolidColor(Color(0xFFE84040).copy(alpha = 0.3f)),
                        ),
                    ) {
                        Text("Disconnect", fontSize = textBodyM(), fontWeight = FontWeight.Medium)
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(adp(48f)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Cancel", color = Color(0xFFCCCCCC), fontSize = textBodyM(), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Single device row per design: 36dp teal circle + device-type icon + name + Wifi/connected. */
@Composable
private fun DeviceRow(device: CastDevice, isConnected: Boolean, onClick: () -> Unit) {
    val deviceIcon = getDeviceIcon(device.modelName)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = spacingXL(), vertical = spacingM()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(adp(36f)).clip(CircleShape)
                .background(Color(0xFF00C8B4).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.CastConnected else deviceIcon,
                contentDescription = null,
                modifier = Modifier.size(adp(16f)),
                tint = Color(0xFF00C8B4),
            )
        }
        Spacer(Modifier.width(spacingM()))
        Column(Modifier.weight(1f)) {
            Text(
                text = device.friendlyName ?: "Unknown Device",
                color = Color.White,
                fontSize = textBodyM(),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            device.modelName?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    color = Color(0xFF666666),
                    fontSize = textLabelM(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isConnected) {
            Text(
                "connected",
                color = Color(0xFF00C8B4),
                fontSize = textLabelM(),
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Icon(Icons.Default.Wifi, null, tint = Color(0xFF444444), modifier = Modifier.size(adp(14f)))
        }
    }
}

/** Detect device type icon from modelName — all tinted teal per design palette. */
private fun getDeviceIcon(modelName: String?) = when {
    modelName == null -> Icons.Default.Cast

    modelName.contains("Speaker", ignoreCase = true) ||
        modelName.contains("Mini", ignoreCase = true) -> Icons.Default.Speaker

    modelName.contains("display", ignoreCase = true) ||
        modelName.contains("Nest Hub", ignoreCase = true) -> Icons.Default.DesktopWindows

    modelName.contains("TV", ignoreCase = true) ||
        modelName.contains("webOS", ignoreCase = true) ||
        modelName.contains("Android TV", ignoreCase = true) -> Icons.Default.Tv

    modelName.contains("Soundbar", ignoreCase = true) -> Icons.Default.SurroundSound

    modelName.contains("Chromecast", ignoreCase = true) -> Icons.Default.Cast

    else -> Icons.Default.Cast
}
