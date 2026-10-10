package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverResolver
import com.lucasdss.ftpmusic.app.ui.textHeadingL

/**
 * Last-resort collection art: gradient tile + initials from [name].
 * Replaces MusicNote placeholders on Home / Library / detail headers.
 */
@Composable
fun CollectionLettermark(name: String, modifier: Modifier = Modifier, fontSize: TextUnit = textHeadingL()) {
    val initials = remember(name) { CollectionCoverResolver.initials(name) }
    val hue = remember(name) { CollectionCoverResolver.hueFromName(name) }
    val brush = remember(hue) {
        Brush.linearGradient(
            colors = listOf(
                Color.hsl(hue, 0.45f, 0.28f),
                Color.hsl((hue + 40f) % 360f, 0.50f, 0.18f),
            ),
        )
    }
    Box(
        modifier = modifier.background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
