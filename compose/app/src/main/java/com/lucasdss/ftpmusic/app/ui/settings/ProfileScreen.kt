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
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.SurfaceChip
import com.lucasdss.ftpmusic.app.ui.components.DetailBackButton
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChip
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChipRow

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
            DetailBackButton(onBack = onBack, inset = false)
            Text("Profile", color = Color.White, fontSize = textHeadingL(), fontWeight = FontWeight.Bold)
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
                fontSize = textHeadingM(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.isLoading && state.summary.plays == 0) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandTeal, modifier = Modifier.size(32.dp))
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
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    if (streak <= 0) "No streak yet" else "$streak-day streak",
                    color = if (streak <= 0) Color(0xFF666666) else BrandTeal,
                    fontSize = textBodyM(),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "All time",
                    color = NavUnselected,
                    fontSize = textLabelM(),
                )
            }

            TopSection("Top Songs", state.topTracks)
            TopSection("Top Artists", state.topArtists)
            TopSection("Top Albums", state.topAlbums)
            TopSection("Top Genres", state.topGenres)

            Text(
                "Recently Played",
                color = Color.White,
                fontSize = textHeadingM(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.recentlyPlayed.isEmpty() && !state.isLoading) {
                Text(
                    "Nothing played yet",
                    color = Color(0xFF666666),
                    fontSize = textBodyM(),
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
                        tint = NavUnselected,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        FittingText(
                            text = track.title,
                            color = Color.White,
                            fontSize = textBodyL(),
                            minFontSize = textMicro(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FittingText(
                            text = track.artist ?: "Unknown",
                            color = Color(0xFF888888),
                            fontSize = textLabelL(),
                            minFontSize = textMicro(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        formatDuration(track.durationSeconds ?: 0),
                        color = NavUnselected,
                        fontSize = textLabelL(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodChips(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    SegmentedChipRow {
        SegmentedChip(
            label = "Week",
            selected = selected == StatsPeriod.WEEK,
            onClick = { onSelect(StatsPeriod.WEEK) },
            modifier = Modifier.weight(1f),
        )
        SegmentedChip(
            label = "Month",
            selected = selected == StatsPeriod.MONTH,
            onClick = { onSelect(StatsPeriod.MONTH) },
            modifier = Modifier.weight(1f),
        )
        SegmentedChip(
            label = "Year",
            selected = selected == StatsPeriod.YEAR,
            onClick = { onSelect(StatsPeriod.YEAR) },
            modifier = Modifier.weight(1f),
        )
        SegmentedChip(
            label = "All time",
            selected = selected == StatsPeriod.ALL_TIME,
            onClick = { onSelect(StatsPeriod.ALL_TIME) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TopSection(title: String, rows: List<TopCountRow>) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, color = Color.White, fontSize = textHeadingM(), fontWeight = FontWeight.Bold)
        if (rows.isEmpty()) {
            Text("—", color = NavUnselected, fontSize = textLabelL(), modifier = Modifier.padding(top = 6.dp))
        } else {
            rows.forEachIndexed { index, row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        color = BrandTeal,
                        fontSize = textBodyM(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp),
                    )
                    FittingText(
                        text = row.label?.takeIf { it.isNotBlank() } ?: row.itemKey,
                        color = Color.White,
                        fontSize = textBodyM(),
                        minFontSize = textMicro(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${row.playCount}",
                        color = Color(0xFF888888),
                        fontSize = textLabelL(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatCard(icon: ImageVector, value: String, label: String) {
    // weight(1f) bounds width so FittingText never measures against Infinity
    // (unbounded fillMaxWidth in a Row collapses sibling labels in Robolectric).
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(BrandTeal.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = BrandTeal, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        FittingText(
            text = value,
            color = Color.White,
            fontSize = textHeadingS(),
            minFontSize = textMicro(),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(label, color = Color(0xFF888888), fontSize = textLabelM())
    }
}

internal fun formatListeningTime(minutes: Int): String = ProfileStatsMath.formatListeningMinutes(minutes)

internal fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
