package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.ratingStarInteractiveSize

/** Interactive 0–5★ rating row — market glyph.
 *  [expandTouchTarget]=true (player / album under art) adds Material min touch;
 *  false kept for dense layouts if stars return beside title. */
@Composable
fun InteractiveStarRating(
    rating: Int,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptionPrefix: String = "Rate",
    expandTouchTarget: Boolean = true,
) {
    val clamped = rating.coerceIn(0, 5)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val starMod = Modifier
                .then(if (expandTouchTarget) Modifier.minimumInteractiveComponentSize() else Modifier)
                .size(ratingStarInteractiveSize())
                .clickable { onRate(i) }
            Icon(
                imageVector = if (i <= clamped) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "$contentDescriptionPrefix $i",
                tint = if (i <= clamped) BrandTeal else Color(0xFF444444),
                modifier = starMod,
            )
        }
    }
}
