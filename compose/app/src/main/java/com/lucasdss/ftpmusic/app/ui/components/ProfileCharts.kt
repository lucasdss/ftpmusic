package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.data.db.TopCountRow
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Muted
import com.lucasdss.ftpmusic.app.ui.SurfaceChip
import com.lucasdss.ftpmusic.app.ui.settings.ProfileStatsMath
import com.lucasdss.ftpmusic.app.ui.textLabelM
import com.lucasdss.ftpmusic.app.ui.textMicro

@Composable
fun ListeningActivityChart(points: List<ProfileStatsMath.ActivityPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val maxSec = points.maxOf { it.seconds }.coerceAtLeast(1L)
    val summary = points.joinToString(", ") { p ->
        val mins = (p.seconds / 60L).toInt()
        "${ProfileStatsMath.formatListeningMinutes(mins)} ${p.label}"
    }
    Column(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Listening activity: $summary" }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
                val frac = (point.seconds.toFloat() / maxSec.toFloat()).coerceIn(0.04f, 1f)
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(frac)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (point.seconds > 0L) BrandTeal else SurfaceChip,
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val showEvery = when {
                points.size <= 7 -> 1
                points.size <= 16 -> 2
                else -> 3
            }
            points.forEachIndexed { index, point ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (index % showEvery == 0 || index == points.lastIndex) {
                        Text(
                            point.label,
                            color = Muted,
                            fontSize = textMicro(),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RankShareBar(playCount: Int, maxPlayCount: Int, modifier: Modifier = Modifier) {
    val frac = if (maxPlayCount <= 0) 0f else (playCount.toFloat() / maxPlayCount.toFloat()).coerceIn(0.06f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(SurfaceChip),
    ) {
        Box(
            Modifier
                .fillMaxWidth(frac)
                .fillMaxHeight()
                .background(BrandTeal.copy(alpha = 0.85f)),
        )
    }
}

@Composable
fun GenreMixStrip(genres: List<TopCountRow>, modifier: Modifier = Modifier) {
    if (genres.isEmpty()) return
    val alphas = listOf(1f, 0.75f, 0.55f, 0.4f, 0.28f)
    val summary = genres.joinToString(", ") { "${it.label ?: it.itemKey} ${it.playCount}" }
    Row(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .semantics { contentDescription = "Genre mix: $summary" },
    ) {
        genres.forEachIndexed { index, row ->
            val weight = row.playCount.toFloat().coerceAtLeast(0.01f)
            Box(
                Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(BrandTeal.copy(alpha = alphas.getOrElse(index) { 0.25f })),
            )
        }
    }
}

@Composable
fun TopMetricCaption(playCount: Int, honestSeconds: Long) {
    val minutes = (honestSeconds / 60L).toInt()
    val time = ProfileStatsMath.formatListeningMinutes(minutes)
    Text(
        "$playCount · $time",
        color = Muted,
        fontSize = textLabelM(),
        modifier = Modifier.padding(start = 4.dp),
    )
}
