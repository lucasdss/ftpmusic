package com.lucasdss.ftpmusic.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lucasdss.ftpmusic.app.data.db.TopCountRow
import com.lucasdss.ftpmusic.app.data.db.TrackEntity

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onTrackClick: (TrackEntity) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0D0D14))) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text("Profile", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            PeriodChips(
                selected = state.period,
                onSelect = viewModel::setPeriod,
            )

            Text(
                "My Listening",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.isLoading && state.summary.plays == 0) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00C8B4), modifier = Modifier.size(32.dp))
                }
            } else {
                val s = state.summary
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatCard(
                        Icons.Filled.Schedule,
                        ProfileStatsMath.formatListeningMinutes(s.listeningMinutes),
                        "listening",
                    )
                    StatCard(Icons.Filled.MusicNote, s.plays.toString(), "plays")
                    StatCard(Icons.Filled.Audiotrack, s.songCount.toString(), "songs")
                    StatCard(Icons.Filled.Groups, s.artistCount.toString(), "artists")
                }
            }

            val streak = state.summary.streakDays
            Text(
                if (streak <= 0) "No listening streak yet" else "$streak-day streak",
                color = if (streak <= 0) Color(0xFF666666) else Color(0xFF00C8B4),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            TopSection("Top Songs", state.topTracks)
            TopSection("Top Artists", state.topArtists)
            TopSection("Top Albums", state.topAlbums)
            TopSection("Top Genres", state.topGenres)

            Text(
                "Recently Played",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.recentlyPlayed.isEmpty() && !state.isLoading) {
                Text(
                    "Nothing played yet",
                    color = Color(0xFF666666),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            state.recentlyPlayed.forEach { track ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onTrackClick(track) }
                        .testTag("profile_recent_${track.id}")
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        null,
                        tint = Color(0xFF555555),
                        modifier = Modifier.size(24.dp),
                    )
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
                    Text(
                        formatDuration(track.durationSeconds ?: 0),
                        color = Color(0xFF555555),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodChips(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PeriodChip("Week", StatsPeriod.WEEK, selected, onSelect)
        PeriodChip("Month", StatsPeriod.MONTH, selected, onSelect)
        PeriodChip("Year", StatsPeriod.YEAR, selected, onSelect)
        PeriodChip("All time", StatsPeriod.ALL_TIME, selected, onSelect)
    }
}

@Composable
private fun RowScope.PeriodChip(
    label: String,
    period: StatsPeriod,
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
) {
    val active = selected == period
    Text(
        label,
        color = if (active) Color.Black else Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Color(0xFF00C8B4) else Color(0xFF1A1A24))
            .clickable { onSelect(period) }
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TopSection(title: String, rows: List<TopCountRow>) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (rows.isEmpty()) {
            Text("—", color = Color(0xFF555555), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            rows.forEachIndexed { index, row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        color = Color(0xFF00C8B4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        row.label?.takeIf { it.isNotBlank() } ?: row.itemKey,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${row.playCount}",
                        color = Color(0xFF888888),
                        fontSize = 13.sp,
                    )
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

internal fun formatListeningTime(minutes: Int): String = ProfileStatsMath.formatListeningMinutes(minutes)

internal fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
