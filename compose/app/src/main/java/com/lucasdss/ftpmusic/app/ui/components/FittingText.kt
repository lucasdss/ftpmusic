package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lucasdss.ftpmusic.app.ui.textMicro

/**
 * Layout-safe text: shrinks font size to fit [maxWidth], then ellipsizes.
 *
 * Protects constrained slots (nav labels, row titles, chips) from one-word
 * unbreakable strings and large fontScale blowouts. System a11y fontScale is
 * honored via token `.sp`; this composable only shrinks when the slot overflows.
 */
@Composable
fun FittingText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = textMicro(),
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    softWrap: Boolean = true,
    textAlign: TextAlign? = null,
    fillMaxWidth: Boolean = true,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = LocalTextStyle.current
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(0)
        val resolvedSize = remember(
            text,
            fontSize,
            minFontSize,
            fontWeight,
            maxLines,
            maxWidthPx,
            softWrap,
            baseStyle,
        ) {
            fitFontSize(
                text = text,
                startSp = fontSize.value,
                minSp = minFontSize.value.coerceAtMost(fontSize.value),
                fontWeight = fontWeight,
                maxLines = maxLines,
                maxWidthPx = maxWidthPx,
                measure = { sizeSp, weight, lines, widthPx ->
                    textMeasurer.measure(
                        text = text,
                        style = baseStyle.merge(
                            TextStyle(fontSize = sizeSp.sp, fontWeight = weight),
                        ),
                        constraints = Constraints(maxWidth = widthPx.coerceAtLeast(0)),
                        maxLines = lines,
                        overflow = TextOverflow.Clip,
                        softWrap = softWrap,
                    ).hasVisualOverflow
                },
            ).sp
        }
        Text(
            text = text,
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            color = color,
            fontSize = resolvedSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            softWrap = softWrap,
            textAlign = textAlign,
        )
    }
}

/**
 * Binary-search largest font size in [minSp .. startSp] that does not overflow.
 * Pure helper for unit tests — [measure] returns true when text overflows.
 *
 * When [maxWidthPx] is <= 0 (first layout / unconstrained), returns [minSp]
 * so callers do not flash oversized text.
 */
internal fun fitFontSize(
    text: String,
    startSp: Float,
    minSp: Float,
    fontWeight: FontWeight?,
    maxLines: Int,
    maxWidthPx: Int,
    measure: (sizeSp: Float, weight: FontWeight?, lines: Int, widthPx: Int) -> Boolean,
): Float {
    val loBound = minSp.coerceAtMost(startSp)
    if (text.isEmpty()) return startSp
    // Not ready to measure — prefer min to avoid first-frame oversize flash.
    if (maxWidthPx <= 0) return loBound
    if (!measure(startSp, fontWeight, maxLines, maxWidthPx)) return startSp
    var low = loBound
    var high = startSp
    var best = loBound
    // ~8 iterations → ~0.25sp precision for typical 10–16sp ranges
    repeat(8) {
        val mid = (low + high) / 2f
        if (measure(mid, fontWeight, maxLines, maxWidthPx)) {
            high = mid
        } else {
            best = mid
            low = mid
        }
    }
    // Guarantee non-overflow when possible: step down if search landed hot.
    var verified = best
    while (verified > loBound && measure(verified, fontWeight, maxLines, maxWidthPx)) {
        verified = (verified - 0.25f).coerceAtLeast(loBound)
    }
    return verified
}
