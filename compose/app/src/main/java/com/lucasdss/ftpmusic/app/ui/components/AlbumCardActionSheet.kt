package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasdss.ftpmusic.app.ui.*

/**
 * Bottom sheet shown on long-press of an album card.
 * Actions: Play, Shuffle, Add to Queue, Add to Playlist, Download.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumCardActionSheet(
    albumName: String,
    artistName: String?,
    trackCount: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C2E),
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(bottom = spacing3XL())) {
            // Drag handle
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
            }
            // Header
            Column(Modifier.padding(horizontal = spacingXL(), vertical = spacingM())) {
                Text(albumName, color = Color.White, fontSize = textHeadingM(), fontWeight = FontWeight.Bold)
                Text("$artistName · $trackCount tracks", color = Color(0xFF888888), fontSize = textBodyM())
            }
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(horizontal = spacingL()),
            )
            // Actions
            AlbumSheetAction("Play", Icons.Default.PlayArrow, Color.White, onPlay)
            AlbumSheetAction("Shuffle", Icons.Default.Shuffle, Color(0xFFCCCCCC), onShuffle)
            AlbumSheetAction("Add to Queue", Icons.AutoMirrored.Filled.QueueMusic, Color(0xFF00C8B4), onAddToQueue)
            AlbumSheetAction(
                "Add to Playlist",
                Icons.AutoMirrored.Filled.PlaylistAdd,
                Color(0xFFB040E8),
                onAddToPlaylist,
            )
            AlbumSheetAction("Download", Icons.Default.Download, Color(0xFF888888), onDownload)
        }
    }
}

@Composable
private fun AlbumSheetAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = spacingL(), vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = textBodyM(), fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = spacingL()))
}
