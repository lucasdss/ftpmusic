package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.reactionGlyphSize

/** Interactive radio bookmark — filled when bookmarked, outline otherwise. */
@Composable
fun RadioBookmarkIcon(
    bookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inactiveTint: Color = Color(0xFF888888),
) {
    Icon(
        imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
        contentDescription = if (bookmarked) "Unbookmark station" else "Bookmark station",
        tint = if (bookmarked) BrandTeal else inactiveTint,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(reactionGlyphSize())
            .clickable(onClick = onClick),
    )
}
