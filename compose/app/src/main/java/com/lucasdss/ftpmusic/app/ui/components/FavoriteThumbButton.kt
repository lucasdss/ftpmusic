package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Round 24dp favorite thumb (like/dislike) used on album cards and artist
 *  rows. The whole circle is the touch target (a11y); the icon carries
 *  semantics. Active tint teal by default, red for dislike. */
@Composable
fun FavoriteThumbButton(
    icon: ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    activeTint: Color = Color(0xFF00C8B4),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            tint = if (active) activeTint else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(12.dp),
        )
    }
}
