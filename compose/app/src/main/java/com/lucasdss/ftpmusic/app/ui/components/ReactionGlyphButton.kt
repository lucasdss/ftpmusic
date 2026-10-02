package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.lucasdss.ftpmusic.app.ui.reactionGlyphSize

/** Dense-row reaction glyph (thumbs) — market size + min touch, no circle chrome. */
@Composable
fun ReactionGlyphButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Icon(
        icon,
        contentDescription,
        tint = tint,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(reactionGlyphSize())
            .clickable(onClick = onClick),
    )
}
