package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.cornerM
import com.lucasdss.ftpmusic.app.ui.detailActionHeight
import com.lucasdss.ftpmusic.app.ui.knobSize
import com.lucasdss.ftpmusic.app.ui.spacingL
import com.lucasdss.ftpmusic.app.ui.spacingXL
import com.lucasdss.ftpmusic.app.ui.textBodyM

/**
 * Shared Play | Shuffle | More chrome for Album / Artist detail (ADR-0069).
 *
 * Pad: spacingXL × spacingL. Height: detailActionHeight. Icon–label: 8dp.
 * More hit box ≥ [minTouchTarget].
 */
@Composable
fun DetailActionRow(
    playLabel: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onMore: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = spacingXL(), vertical = spacingL()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(detailActionHeight())
                .clip(RoundedCornerShape(cornerM()))
                .background(
                    Brush.linearGradient(
                        listOf(BrandTeal, BrandPurple),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    ),
                )
                .clickable(enabled = enabled, onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(knobSize()),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    playLabel,
                    color = Color.White,
                    fontSize = textBodyM(),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .height(detailActionHeight())
                .clip(RoundedCornerShape(cornerM()))
                .background(Color(0xFF252538))
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(BrandTeal, BrandPurple)),
                    RoundedCornerShape(cornerM()),
                )
                .clickable(enabled = enabled, onClick = onShuffle),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = null,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(knobSize()),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Shuffle",
                    color = Color(0xFFCCCCCC),
                    fontSize = textBodyM(),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box(
            Modifier
                .size(minTouchTarget())
                .clip(RoundedCornerShape(cornerM()))
                .background(Color(0xFF252538))
                .clickable(enabled = enabled, onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More",
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
