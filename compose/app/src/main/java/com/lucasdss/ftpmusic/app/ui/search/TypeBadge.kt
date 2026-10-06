package com.lucasdss.ftpmusic.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.components.FittingText

private val TYPE_COLORS = mapOf(
    "artist" to BrandTeal,
    "album" to BrandPurple,
    "song" to Color(0xFF5B8DEE),
    "playlist" to Color(0xFFF0A040),
    "genre" to Color(0xFF26A69A),
)

@Composable
fun TypeBadge(type: String, modifier: Modifier = Modifier) {
    val color = TYPE_COLORS[type] ?: Color(0xFF888888)
    FittingText(
        text = type,
        color = color,
        fontSize = textMicro(),
        minFontSize = textMicro(),
        fontWeight = FontWeight.Bold,
        fillMaxWidth = false,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
