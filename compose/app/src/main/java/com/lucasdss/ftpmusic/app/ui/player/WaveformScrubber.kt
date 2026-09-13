package com.lucasdss.ftpmusic.app.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasdss.ftpmusic.app.data.waveform.WaveformDecimator

@Composable
fun WaveformScrubber(
    bars: List<Float>,
    fraction: Float,
    position: Long,
    duration: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrubberHeight = 48.dp
    val barWidth = 2.dp
    val gap = 1.5f.dp
    val step = barWidth + gap
    // Smooth the 5 Hz position poll into a 200 ms tween — scoped here so the
    // parent PlayerBar body does not recompose at animation frame rate.
    val smoothFraction by animateFloatAsState(
        fraction,
        tween(200, easing = LinearEasing),
        label = "wfFrac",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(scrubberHeight),
        ) {
            // Fit as many bars as the usable width allows; never upsample
            // beyond the stored data. Re-decimates only when bars or the
            // available width change (rotation, track change).
            val targetCount = if (bars.isEmpty()) {
                0
            } else {
                ((maxWidth - gap) / step).toInt().coerceIn(1, bars.size)
            }
            val displayBars = remember(bars, targetCount) {
                WaveformDecimator.decimate(bars, targetCount)
            }
            val displayCount = displayBars.size
            val totalWidth = step * displayCount - gap

            // P7: hoist the bar-spec computation OUT of the Canvas draw lambda
            // and memoize on the integer playhead (playedIndex). The previous
            // version recomputed every bar's geometry+color on EVERY 200 ms
            // tick; the specs only change when the playhead crosses a bar.
            val playedIndex = (smoothFraction * displayCount).toInt().coerceIn(0, displayCount)
            val density = LocalDensity.current
            val specs = remember(
                displayBars,
                displayCount,
                maxWidth,
                maxHeight,
                step,
                barWidth,
                gap,
                playedIndex,
            ) {
                with(density) {
                    computeWaveformBarSpecs(
                        bars = displayBars,
                        // (playedIndex + 0.5)/count floors back to the same integer
                        // playhead — byte-identical to the continuous-fraction output,
                        // but stable across ticks between bar boundaries.
                        fraction = if (displayCount > 0) (playedIndex + 0.5f) / displayCount else 0f,
                        canvasWidthPx = maxWidth.toPx(),
                        canvasHeightPx = maxHeight.toPx(),
                        stepPx = step.toPx(),
                        barWidthPx = barWidth.toPx(),
                        minBarHeightPx = 2.dp.toPx(),
                        maxBarHeightPx = 40.dp.toPx(),
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(displayBars) {
                        detectTapGestures { offset ->
                            val canvasWidth = size.width.toFloat()
                            val startX = (canvasWidth - totalWidth.toPx()) / 2f
                            val clickedBar = ((offset.x - startX) / step.toPx()).toInt()
                            if (clickedBar in 0 until displayCount) {
                                val newFraction = clickedBar.toFloat() / displayCount
                                onSeek(newFraction.coerceIn(0f, 1f))
                            }
                        }
                    },
            ) {
                val barWidthPx = barWidth.toPx()
                val cornerRadiusPx = CornerRadius(1.dp.toPx())
                specs.forEach { spec ->
                    drawRoundRect(
                        color = spec.color,
                        topLeft = Offset(spec.x, spec.top),
                        size = Size(barWidthPx, spec.height),
                        cornerRadius = cornerRadiusPx,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Time labels
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatWaveformTime(position),
                color = Color(0xFF666666),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp),
            )
            Text(
                "-${formatWaveformTime(duration - position)}",
                color = Color(0xFF666666),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

internal fun formatWaveformTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
