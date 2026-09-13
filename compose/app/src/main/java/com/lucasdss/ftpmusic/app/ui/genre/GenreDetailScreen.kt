package com.lucasdss.ftpmusic.app.ui.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(
    genre: String = "",
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: GenreDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(genre) { viewModel.loadGenre(genre) }

    val context = LocalContext.current
    val coverArtFallback = remember { CoverArtFallbackService.getInstance(context) }
    val fallbackVersion by remember { derivedStateOf { coverArtFallback.cacheVersionState.intValue } }
    fun artistArtUrl(name: String): String? {
        val file = java.io.File(coverArtFallback.cacheDir, "artist|${name.lowercase()}".hashCode().toString() + ".jpg")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(genre.ifEmpty { "Genre" }, color = Color.White, fontSize = textHeadingM()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF12121E)),
            )
        },
        containerColor = Color(0xFF12121E),
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00C8B4))
            }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Column(Modifier.padding(padding)) {
                // Tabs — matching Library design
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingS()),
                    horizontalArrangement = Arrangement.spacedBy(spacingXS()),
                ) {
                    listOf(
                        GenreTab.ALBUMS to "Albums",
                        GenreTab.ARTISTS to "Artists",
                    ).forEach { (tab, label) ->
                        val selected = state.tab == tab
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(0xFF2A2A2A) else Color(0xFF1C1C1C))
                                .clickable { viewModel.setTab(tab) }
                                .padding(vertical = spacingS()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) Color.White else Color(0xFF666666),
                                fontSize = textBodyM(),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Content
                if (state.tab == GenreTab.ALBUMS) {
                    if (state.albums.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No albums found", color = Color(0xFF666666), fontSize = textBodyM())
                        }
                    } else {
                        val gridState = rememberLazyGridState()
                        val hasMore = state.hasMore
                        LaunchedEffect(gridState) {
                            snapshotFlow {
                                val total = gridState.layoutInfo.totalItemsCount
                                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisible >= total - 4
                            }.collect { nearEnd ->
                                if (nearEnd && hasMore) viewModel.loadMore()
                            }
                        }
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.padding(horizontal = spacingM()),
                            contentPadding = PaddingValues(vertical = spacingS()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(state.albums) { album ->
                                Column(Modifier.clickable { onAlbumClick(album.id) }) {
                                    Box(
                                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(cornerM())),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        val url = rememberCoverArtUrl(album.coverArt, 300)
                                        if (url != null) {
                                            AsyncImage(
                                                model = url,
                                                contentDescription = album.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Box(
                                                Modifier.fillMaxSize().background(Color(0xFF1E1E1E)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Default.Album,
                                                    null,
                                                    tint = Color(0xFF444444),
                                                    modifier = Modifier.size(32.dp),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        album.name,
                                        color = Color.White,
                                        fontSize = textBodyM(),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    album.artist?.let {
                                        Text(
                                            it,
                                            color = Color(0xFF888888),
                                            fontSize = textLabelM(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (state.isLoadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF00C8B4),
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (state.artists.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No artists found", color = Color(0xFF666666), fontSize = textBodyM())
                        }
                    } else {
                        val listState = rememberLazyListState()
                        val hasMore = state.hasMore
                        LaunchedEffect(listState) {
                            snapshotFlow {
                                val total = listState.layoutInfo.totalItemsCount
                                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisible >= total - 4
                            }.collect { nearEnd ->
                                if (nearEnd && hasMore) viewModel.loadMore()
                            }
                        }
                        LazyColumn(state = listState) {
                            items(state.artists) { artist ->
                                LaunchedEffect(artist.name) { coverArtFallback.fetchArtistArt(artist.name).collect {} }
                                val artUrl = remember(artist.name, fallbackVersion) { artistArtUrl(artist.name) }
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        onArtistClick(artist.id)
                                    }.padding(horizontal = spacingL(), vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(iconLarge()).clip(RoundedCornerShape(cornerS())),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (artUrl != null) {
                                            AsyncImage(
                                                model = artUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Box(
                                                Modifier.fillMaxSize().background(Color(0xFF1E1E1E)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Default.Person,
                                                    null,
                                                    tint = Color(0xFF555555),
                                                    modifier = Modifier.size(iconSmall()),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            artist.name,
                                            color = Color.White,
                                            fontSize = textHeadingS(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        null,
                                        tint = Color(0xFF444444),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            if (state.isLoadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF00C8B4),
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
