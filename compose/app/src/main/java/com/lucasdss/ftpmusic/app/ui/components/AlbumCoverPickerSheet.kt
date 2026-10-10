package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.search.SearchQueryNormalizer
import com.lucasdss.ftpmusic.app.ui.BrandBg
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.cornerM
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl
import com.lucasdss.ftpmusic.app.ui.textBodyM
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelM
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlbumCoverPickerEntryPoint {
    fun cachedMetadataDao(): CachedMetadataDao
}

/**
 * Modal album grid for picking a Navidrome coverArt id as a fixed collection cover.
 */
@Composable
fun AlbumCoverPickerSheet(onPicked: (coverArtId: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dao = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AlbumCoverPickerEntryPoint::class.java,
        ).cachedMetadataDao()
    }
    var query by remember { mutableStateOf("") }
    var albums by remember { mutableStateOf<List<CachedAlbumEntity>>(emptyList()) }

    LaunchedEffect(query) {
        delay(200)
        albums = if (query.isBlank()) {
            dao.getAlbumsPaged(60, 0)
        } else {
            dao.searchAlbums(SearchQueryNormalizer.escapeLike(query.trim())).take(60)
        }.filter { !it.coverArt.isNullOrBlank() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BrandBg)
                .padding(16.dp)
                .semantics { testTag = "album_cover_picker" },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Choose album art",
                    color = Color.White,
                    fontSize = textHeadingS(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = NavUnselected)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search albums", color = NavUnselected) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = NavUnselected) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BrandTeal,
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = BrandTeal,
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                ),
                modifier = Modifier.fillMaxWidth().semantics { testTag = "album_cover_search" },
            )
            Spacer(Modifier.height(12.dp))
            if (albums.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().weight(1f, fill = true).heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No albums with cover art", color = NavUnselected, fontSize = textBodyM())
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = true).heightIn(min = 200.dp, max = 420.dp),
                ) {
                    items(albums, key = { it.id }) { album ->
                        val coverId = album.coverArt ?: return@items
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(cornerM()))
                                .clickable { onPicked(coverId) }
                                .semantics { testTag = "album_cover_pick_${album.id}" },
                        ) {
                            val url = rememberCoverArtUrl(coverId, 200)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(cornerM()))
                                    .background(Color(0xFF1E1E1E)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (url != null) {
                                    CoverArtImage(
                                        url = url,
                                        contentDescription = album.name,
                                        modifier = Modifier.matchParentSize(),
                                        decodeSize = 96.dp,
                                    )
                                }
                            }
                            Text(
                                album.name,
                                color = Color.White,
                                fontSize = textLabelM(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
