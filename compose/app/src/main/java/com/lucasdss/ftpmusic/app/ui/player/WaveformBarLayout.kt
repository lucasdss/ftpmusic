package com.lucasdss.ftpmusic.app.ui.player

import androidx.compose.ui.graphics.Color

/** One rendered waveform bar: position, height, color. */
internal data class WaveformBarSpec(val x: Float, val top: Float, val height: Float, val color: Color)

/**
 * Pure layout math for the waveform scrubber draw phase: maps decimated bars
 * plus play fraction to per-bar rects (x, top, height) and colors (played
 * teal→purple gradient, playhead white, unplayed dim). Extracted from the
 * Canvas draw lambda so it is unit-testable — Robolectric does not execute
 * Compose draw lambdas under jacoco (repo-wide UI files report ~0%).
 */
internal fun computeWaveformBarSpecs(
    bars: List<Float>,
    fraction: Float,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    stepPx: Float,
    barWidthPx: Float,
    minBarHeightPx: Float,
    maxBarHeightPx: Float,
): List<WaveformBarSpec> {
    if (bars.isEmpty()) return emptyList()
    val count = bars.size
    val playedIndex = (fraction * count).toInt().coerceIn(0, count)
    val gapPx = stepPx - barWidthPx
    val totalWidthPx = stepPx * count - gapPx
    val startX = (canvasWidthPx - totalWidthPx) / 2f
    return bars.mapIndexed { i, amp ->
        val safeAmp = amp.coerceIn(0f, 1f)
        val height = maxOf(minBarHeightPx, safeAmp * maxBarHeightPx)
        val color = when {
            i < playedIndex ->
                lerpColor(Color(0xFF00C8B4), Color(0xFFB040E8), i.toFloat() / count)

            i == playedIndex -> Color.White.copy(alpha = 0.7f)

            else -> Color.White.copy(alpha = (0.1f + safeAmp * 0.08f).coerceIn(0f, 1f))
        }
        WaveformBarSpec(
            x = startX + i * stepPx,
            top = (canvasHeightPx - height) / 2f,
            height = height,
            color = color,
        )
    }
}

internal fun lerpColor(start: Color, end: Color, fraction: Float): Color = Color(
    red = start.red + (end.red - start.red) * fraction,
    green = start.green + (end.green - start.green) * fraction,
    blue = start.blue + (end.blue - start.blue) * fraction,
    alpha = start.alpha + (end.alpha - start.alpha) * fraction,
)
