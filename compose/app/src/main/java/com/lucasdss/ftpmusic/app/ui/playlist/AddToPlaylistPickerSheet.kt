package com.lucasdss.ftpmusic.app.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.cornerM
import com.lucasdss.ftpmusic.app.ui.iconSmall
import com.lucasdss.ftpmusic.app.ui.spacing3XL
import com.lucasdss.ftpmusic.app.ui.spacingL
import com.lucasdss.ftpmusic.app.ui.spacingM
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.spacingXL
import com.lucasdss.ftpmusic.app.ui.textBodyM
import com.lucasdss.ftpmusic.app.ui.textHeadingM
import com.lucasdss.ftpmusic.app.ui.textLabelM

/** Room playlist row for the shared Save-to-playlist sheet (never Daily Mixes). */
data class PlaylistPickerItem(
    val id: String,
    val name: String,
    val trackCount: Int = 0,
)

/**
 * Shared Add/Save-to-playlist sheet used by Album, Artist, Home, Search, Now Playing.
 * Optional [onCreateNew] / [onAddFromServer] keep Album-parity without forcing every host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistPickerSheet(
    playlists: List<PlaylistPickerItem>,
    onPick: (playlistId: String) -> Unit,
    onDismiss: () -> Unit,
    onCreateNew: (() -> Unit)? = null,
    onAddFromServer: (() -> Unit)? = null,
    title: String = "Add to Playlist",
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(bottom = spacing3XL())) {
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
            }
            Text(
                title,
                color = Color.White,
                fontSize = textHeadingM(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingM()),
            )
            if (onCreateNew != null) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onCreateNew)
                        .padding(horizontal = spacingL(), vertical = spacingM())
                        .clip(RoundedCornerShape(cornerM()))
                        .border(1.dp, BrandTeal.copy(alpha = 0.3f), RoundedCornerShape(cornerM()))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, null, tint = BrandTeal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "New Playlist…",
                        color = BrandTeal,
                        fontSize = textBodyM(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (onAddFromServer != null) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onAddFromServer)
                        .padding(horizontal = spacingL(), vertical = spacingM()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CloudDownload, null, tint = BrandTeal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Add from Server", color = Color.White, fontSize = textBodyM(), fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (playlists.isEmpty()) {
                Text(
                    "No playlists yet",
                    color = Color(0xFF888888),
                    fontSize = textBodyM(),
                    modifier = Modifier.padding(horizontal = spacingL()),
                )
            } else {
                playlists.forEach { pl ->
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.04f),
                        modifier = Modifier.padding(horizontal = spacingL()),
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(pl.id) }
                            .padding(horizontal = spacingL(), vertical = spacingM()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.QueueMusic,
                            null,
                            tint = Color(0xFF444444),
                            modifier = Modifier.size(iconSmall()),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            pl.name,
                            color = Color.White,
                            fontSize = textBodyM(),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${pl.trackCount} tracks", color = Color(0xFF888888), fontSize = textLabelM())
                    }
                }
            }
        }
    }
}
