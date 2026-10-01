package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.SurfaceChip
import com.lucasdss.ftpmusic.app.ui.spacingL
import com.lucasdss.ftpmusic.app.ui.spacingS
import com.lucasdss.ftpmusic.app.ui.textLabelL
import com.lucasdss.ftpmusic.app.ui.textMicro

/**
 * Brand-forward segmented control: teal fill when selected, SurfaceChip when idle.
 * Used by Library tabs and Favorites modes for one visual language.
 */
@Composable
fun SegmentedChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FittingText(
        text = label,
        color = if (selected) Color.Black else Color.White,
        fontSize = textLabelL(),
        minFontSize = textMicro(),
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) BrandTeal else SurfaceChip)
            .clickable(onClick = onClick)
            .padding(vertical = spacingS()),
    )
}

@Composable
fun SegmentedChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = spacingL(), vertical = spacingS()),
        horizontalArrangement = Arrangement.spacedBy(spacingS()),
        content = content,
    )
}
