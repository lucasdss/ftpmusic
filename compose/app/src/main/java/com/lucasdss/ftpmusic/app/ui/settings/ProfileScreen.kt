package com.lucasdss.ftpmusic.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lucasdss.ftpmusic.app.data.db.TopCountRow
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.components.DetailBackButton
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import com.lucasdss.ftpmusic.app.ui.components.GenreMixStrip
import com.lucasdss.ftpmusic.app.ui.components.ListeningActivityChart
import com.lucasdss.ftpmusic.app.ui.components.RankShareBar
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChip
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChipRow
import com.lucasdss.ftpmusic.app.ui.components.TopMetricCaption

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

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailBackButton(onBack = onBack, inset = false)
            Text("Profile", color = Foreground, fontSize = textHeadingL(), fontWeight = FontWeight.Bold)
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
                color = Foreground,
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
                        ProfileStatsMath.formatListeningTime(s.listeningSeconds),
                        "listening",
                    )
                    StatCard(Icons.Filled.MusicNote, s.plays.toString(), "plays")
                    StatCard(Icons.Filled.Audiotrack, s.songCount.toString(), "tracks played")
                    StatCard(Icons.Filled.Groups, s.artistCount.toString(), "artists")
                }
            }

            if (state.activity.isNotEmpty()) {
                Text(
                    "Activity",
                    color = Foreground,
                    fontSize = textHeadingM(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                ListeningActivityChart(points = state.activity)
            }

            val streak = state.summary.streakDays
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    if (streak <= 0) "No streak yet" else "$streak-day streak",
                    color = if (streak <= 0) Dimmed else BrandTeal,
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
            TopSection("Top Genres", state.topGenres, showGenreMix = true)

            Text(
                "Recently Played",
                color = Foreground,
                fontSize = textHeadingM(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.recentlyPlayed.isEmpty() && !state.isLoading) {
                Text(
                    "Nothing played yet",
                    color = Dimmed,
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
                            color = Foreground,
                            fontSize = textHeadingS(),
                            minFontSize = textMicro(),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FittingText(
                            text = track.artist ?: "Unknown",
                            color = Muted,
                            fontSize = textLabelM(),
                            minFontSize = textMicro(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    track.durationSeconds?.let { secs ->
                        Text(
                            formatDuration(secs),
                            color = NavUnselected,
                            fontSize = textLabelL(),
                        )
                    }
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
private fun TopSection(title: String, rows: List<TopCountRow>, showGenreMix: Boolean = false) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, color = Foreground, fontSize = textHeadingM(), fontWeight = FontWeight.Bold)
        if (rows.isEmpty()) {
            Text("—", color = NavUnselected, fontSize = textLabelL(), modifier = Modifier.padding(top = 6.dp))
        } else {
            if (showGenreMix) {
                Spacer(Modifier.height(8.dp))
                GenreMixStrip(genres = rows)
                Spacer(Modifier.height(8.dp))
            }
            val maxPlays = rows.maxOf { it.playCount }.coerceAtLeast(1)
            rows.forEachIndexed { index, row ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
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
                            color = Foreground,
                            fontSize = textBodyM(),
                            minFontSize = textMicro(),
                            modifier = Modifier.weight(1f),
                        )
                        TopMetricCaption(playCount = row.playCount, honestSeconds = row.honestSeconds)
                    }
                    Spacer(Modifier.height(4.dp))
                    RankShareBar(playCount = row.playCount, maxPlayCount = maxPlays)
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatCard(icon: ImageVector, value: String, label: String) {
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
            color = Foreground,
            fontSize = textHeadingS(),
            minFontSize = textMicro(),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(label, color = Muted, fontSize = textLabelM())
    }
}

internal fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
