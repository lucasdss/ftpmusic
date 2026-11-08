package com.lucasdss.ftpmusic.app.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.data.waveform.WaveformDecimator
import com.lucasdss.ftpmusic.app.ui.interFontFamily
import com.lucasdss.ftpmusic.app.ui.textLabelM
import com.lucasdss.ftpmusic.app.ui.theme.LocalReduceMotion
import kotlin.math.roundToInt

@Composable
fun WaveformScrubber(
    bars: List<Float>,
    fraction: Float,
    position: Long,
    duration: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    playedStart: Color = com.lucasdss.ftpmusic.app.ui.BrandTeal,
    playedEnd: Color = com.lucasdss.ftpmusic.app.ui.BrandPurple,
    hapticsEnabled: Boolean = true,
) {
    val scrubberHeight = 48.dp
    val barWidth = 2.dp
    val gap = 1.5f.dp
    val step = barWidth + gap
    val reduceMotion = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val hasVibrator = true

    var scrubbing by remember { mutableStateOf(false) }
    var previewFraction by remember { mutableFloatStateOf(fraction) }
    LaunchedEffect(fraction, scrubbing) {
        if (!scrubbing) previewFraction = fraction
    }

    val displayFraction = if (scrubbing) previewFraction else fraction
    val smoothFraction by animateFloatAsState(
        displayFraction,
        if (reduceMotion || scrubbing) tween(0) else tween(200, easing = LinearEasing),
        label = "wfFrac",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(scrubberHeight),
        ) {
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
                playedStart,
                playedEnd,
            ) {
                with(density) {
                    computeWaveformBarSpecs(
                        bars = displayBars,
                        fraction = if (displayCount > 0) (playedIndex + 0.5f) / displayCount else 0f,
                        canvasWidthPx = maxWidth.toPx(),
                        canvasHeightPx = maxHeight.toPx(),
                        stepPx = step.toPx(),
                        barWidthPx = barWidth.toPx(),
                        minBarHeightPx = 2.dp.toPx(),
                        maxBarHeightPx = 40.dp.toPx(),
                        playedStart = playedStart,
                        playedEnd = playedEnd,
                    )
                }
            }
            val trackWidthPx = with(density) { totalWidth.toPx().coerceAtLeast(1f) }
            val stepPx = with(density) { step.toPx() }
            val startXPx = with(density) {
                (maxWidth.toPx() - totalWidth.toPx()) / 2f
            }

            Box(Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(
                                current = displayFraction.coerceIn(0f, 1f),
                                range = 0f..1f,
                                steps = 0,
                            )
                            setProgress { value ->
                                onSeek(value.coerceIn(0f, 1f))
                                true
                            }
                        }
                        .pointerInput(displayBars, duration, hapticsEnabled, reduceMotion) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                scrubbing = true
                                var frac = FineScrubMath.fractionFromTapX(
                                    down.position.x,
                                    startXPx,
                                    stepPx,
                                    displayCount,
                                )
                                var prevFrac = previewFraction
                                previewFraction = frac
                                var verticalPull = 0f
                                var horizontalAccum = 0f
                                val pointerId = down.id
                                drag(pointerId) { change ->
                                    val dx = change.positionChange().x
                                    val dy = change.positionChange().y
                                    change.consume()
                                    horizontalAccum += dx
                                    if (dy > 0f) verticalPull += dy
                                    if (FineScrubMath.shouldYieldVertical(verticalPull, horizontalAccum)) {
                                        // Axis-lock: stop claiming after vertical threshold so
                                        // parent queue sheet can take over on a later gesture.
                                        return@drag
                                    }
                                    frac = FineScrubMath.applyHorizontalDelta(
                                        currentFraction = frac,
                                        deltaXPx = dx,
                                        trackWidthPx = trackWidthPx,
                                        verticalPullPx = verticalPull,
                                    )
                                    val minuteKind = PlayerHapticsPolicy.minuteCrossingKind(
                                        prevFrac,
                                        frac,
                                        duration,
                                    )
                                    if (PlayerHapticsPolicy.shouldFire(
                                            hapticsEnabled,
                                            reduceMotion,
                                            hasVibrator,
                                            minuteKind,
                                        )
                                    ) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    prevFrac = frac
                                    previewFraction = frac
                                }
                                val boundary = PlayerHapticsPolicy.boundaryKind(frac)
                                if (PlayerHapticsPolicy.shouldFire(
                                        hapticsEnabled,
                                        reduceMotion,
                                        hasVibrator,
                                        boundary,
                                    )
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onSeek(frac.coerceIn(0f, 1f))
                                scrubbing = false
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

                if (scrubbing) {
                    val tipMs = (previewFraction.coerceIn(0f, 1f) * duration).toLong()
                    val tipX = startXPx + previewFraction.coerceIn(0f, 1f) * trackWidthPx
                    Text(
                        text = formatWaveformTime(tipMs),
                        color = Color.White,
                        fontSize = textLabelM(),
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = interFontFamily(),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    (tipX - 28.dp.toPx()).roundToInt().coerceAtLeast(0),
                                    (-22.dp.toPx()).roundToInt(),
                                )
                            }
                            .background(Color(0xEE1C1C2E), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val shownPos = if (scrubbing) {
                (previewFraction.coerceIn(0f, 1f) * duration).toLong()
            } else {
                position
            }
            Text(
                formatWaveformTime(shownPos),
                color = Color(0xFF666666),
                fontSize = textLabelM(),
                fontWeight = FontWeight.Medium,
                fontFamily = interFontFamily(),
                modifier = Modifier.padding(start = 4.dp),
            )
            Text(
                "-${formatWaveformTime((duration - shownPos).coerceAtLeast(0))}",
                color = Color(0xFF666666),
                fontSize = textLabelM(),
                fontWeight = FontWeight.Medium,
                fontFamily = interFontFamily(),
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
