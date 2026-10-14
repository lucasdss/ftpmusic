package com.lucasdss.ftpmusic.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.ui.NavUnselected

@Composable
fun SearchCoverThumb(url: String?, contentDesc: String, circle: Boolean = false) {
    val shape = if (circle) CircleShape else RoundedCornerShape(6.dp)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = contentDesc,
            modifier = Modifier.size(56.dp).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(56.dp).clip(shape).background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (circle) Icons.Default.Person else Icons.Default.MusicNote,
                null,
                tint = NavUnselected,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
