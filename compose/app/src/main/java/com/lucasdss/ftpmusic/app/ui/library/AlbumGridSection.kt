package com.lucasdss.ftpmusic.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.ui.*

@Composable
fun AlbumGridSection(albums: List<Album>, onAlbumClick: (String) -> Unit) {
    val rows = (albums.size + 1) / 2
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .padding(horizontal = spacingS())
            .height((rows * 220).dp),
        horizontalArrangement = Arrangement.spacedBy(spacingS()),
        verticalArrangement = Arrangement.spacedBy(spacingS()),
        userScrollEnabled = false,
    ) {
        items(albums, key = { it.id }) { album ->
            Card(onClick = { onAlbumClick(album.id) }) {
                Column {
                    if (album.coverArt != null) {
                        val coverUrl = rememberCoverArtUrl(album.coverArt)
                        if (coverUrl != null) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = album.name,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Album, null, Modifier.size(iconLarge()))
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Album, null, Modifier.size(iconLarge()))
                        }
                    }
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            album.name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            album.artist ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
