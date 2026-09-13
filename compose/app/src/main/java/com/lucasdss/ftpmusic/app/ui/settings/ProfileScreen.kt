package com.lucasdss.ftpmusic.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasdss.ftpmusic.app.ui.library.LibraryViewModel

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onTrackClick: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadStats()
        viewModel.refreshRecentlyPlayed()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0D0D14))) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text("Profile", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn {
            // ── My Listening stats ──
            item {
                Text(
                    "My Listening",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                val s = state.stats
                if (state.isLoading && s.totalPlays == 0) {
                    CircularProgressIndicator(
                        color = Color(0xFF00C8B4),
                        modifier = Modifier.size(32.dp).align(Alignment.CenterHorizontally),
                    )
                } else {
                    val listeningText = formatListeningTime(s.listeningMinutes)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatCard(Icons.Filled.MusicNote, s.totalPlays.toString(), "plays")
                        StatCard(Icons.Filled.Schedule, listeningText, "listening")
                        StatCard(Icons.Filled.Groups, s.artistCount.toString(), "artists")
                        StatCard(Icons.Filled.LocalFireDepartment, s.trackCount.toString(), "streak")
                    }
                }
            }

            // ── Recently Played ──
            item {
                Text(
                    "Recently Played",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.recentlyPlayed.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        "Nothing played yet",
                        color = Color(0xFF666666),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.recentlyPlayed, key = { it.id }) { track ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onTrackClick(track.id)
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF555555), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track.artist ?: "Unknown",
                            color = Color(0xFF888888),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(formatDuration(track.durationSeconds ?: 0), color = Color(0xFF555555), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF00C8B4).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color(0xFF00C8B4), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFF888888), fontSize = 12.sp)
    }
}

internal fun formatListeningTime(minutes: Int): String = when {
    minutes < 1 -> "0h"
    minutes < 60 -> "${minutes}m"
    else -> "${minutes / 60}h${if (minutes % 60 > 0) " ${minutes % 60}m" else ""}"
}

internal fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
