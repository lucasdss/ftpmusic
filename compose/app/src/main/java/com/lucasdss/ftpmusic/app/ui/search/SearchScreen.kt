package com.lucasdss.ftpmusic.app.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.data.model.Playlist
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.components.AlbumDownloadBadge
import com.lucasdss.ftpmusic.app.ui.components.DownloadDot
import com.lucasdss.ftpmusic.app.ui.components.downloadStatus
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl
import com.lucasdss.ftpmusic.app.ui.library.rememberPreferredCoverArt
import kotlinx.coroutines.flow.*

private val GENRE_COLORS = listOf(
    Color(0xFF00C8B4), // teal
    Color(0xFF7C4DFF), // purple
    Color(0xFF448AFF), // blue
    Color(0xFFFF4081), // pink
    Color(0xFFFF9100), // orange
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    initialQuery: String = "",
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onTrackClick: (com.lucasdss.ftpmusic.app.data.model.Track) -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coverArtFallback = remember { CoverArtFallbackService.getInstance(context) }
    val fallbackVersion by remember { derivedStateOf { coverArtFallback.cacheVersionState.intValue } }
    var artistArtVersion by remember { mutableIntStateOf(0) }
    val artistArtCache = remember { mutableStateMapOf<String, String?>() }
    var isFocused by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }

    // Filtered history for autocomplete dropdown
    val dropdownItems = remember(state.query, state.recentSearches) {
        val items = if (state.query.isEmpty()) {
            state.recentSearches
        } else {
            state.recentSearches.filter {
                it.contains(state.query, ignoreCase = true) &&
                    !it.equals(state.query, ignoreCase = true)
            }
        }
        items.take(5)
    }
    val showDropdown = isFocused && dropdownItems.isNotEmpty()

    LaunchedEffect(Unit) {
        // Delay focus request until after first frame — FocusRequester
        // must be attached to the composition tree before requesting focus.
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Auto-search when navigated with an initial query from Home
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            viewModel.onQueryChanged(initialQuery)
            viewModel.search()
        }
    }

    // Helper to resolve artist image URL — must match CoverArtFallbackService cache key
    fun artistArtUrl(artistName: String): String? {
        val cacheKey = "artist|${artistName.lowercase()}"
        val cachedFile = java.io.File(coverArtFallback.cacheDir, "${cacheKey.hashCode()}.jpg")
        return if (cachedFile.exists() && cachedFile.length() > 0) cachedFile.absolutePath else null
    }

    // Trigger artist art fetch when search results change
    LaunchedEffect(state.artists) {
        state.artists.take(5).forEach { a ->
            coverArtFallback.fetchArtistArt(a.name).collect { /* file cached */ }
            // After fetch completes, resolve local file path
            val cacheKey = "artist|${a.name.lowercase()}"
            val cachedFile = java.io.File(coverArtFallback.cacheDir, "${cacheKey.hashCode()}.jpg")
            artistArtCache[a.name] =
                if (cachedFile.exists() && cachedFile.length() > 0) cachedFile.absolutePath else null
        }
        artistArtVersion++
    }

    Scaffold(
        containerColor = Color(0xFF12121E),
    ) { padding ->
        val listState = rememberLazyListState()
        LaunchedEffect(listState) {
            snapshotFlow {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = listState.layoutInfo.totalItemsCount
                lastVisible >= total - 6
            }.collect { nearEnd ->
                if (nearEnd && state.query.isNotEmpty()) viewModel.loadMoreSearchResults()
            }
        }
        LazyColumn(state = listState, modifier = Modifier.padding(padding)) {
            // Search bar — full width with icon inside
            item {
                Box(Modifier.padding(horizontal = spacingL(), vertical = spacingXS())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChanged,
                            placeholder = {
                                Text(
                                    "Search songs, artists, albums…",
                                    color = Color(0xFF666666),
                                    fontSize = textHeadingS(),
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { isFocused = it.isFocused },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1C1C2E),
                                unfocusedContainerColor = Color(0xFF1C1C2E),
                                focusedBorderColor = Color(0xFF00C8B4).copy(alpha = 0.4f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                cursorColor = Color(0xFF00C8B4),
                            ),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    null,
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailingIcon = {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                        Icon(
                                            Icons.Default.Close,
                                            "Clear",
                                            tint = Color(0xFF666666),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                viewModel.search()
                                focusManager.clearFocus()
                            }),
                        )
                        // Search Options toggle button (design v3)
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(cornerM()))
                                .background(
                                    if (showOptions || state.filterDownloaded) {
                                        Color(0xFF00C8B4).copy(alpha = 0.15f)
                                    } else {
                                        Color(0xFF1C1C2E)
                                    },
                                )
                                .border(
                                    1.dp,
                                    if (showOptions || state.filterDownloaded) {
                                        Color(0xFF00C8B4).copy(alpha = 0.35f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    },
                                    RoundedCornerShape(cornerM()),
                                )
                                .clickable { showOptions = !showOptions },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                null,
                                tint = if (showOptions ||
                                    state.filterDownloaded
                                ) {
                                    Color(0xFF00C8B4)
                                } else {
                                    Color(0xFF666666)
                                },
                                modifier = Modifier.size(iconSmall()),
                            )
                            if (state.filterDownloaded) {
                                Box(
                                    Modifier.size(
                                        8.dp,
                                    ).align(Alignment.TopEnd).background(Color(0xFFB040E8), CircleShape),
                                )
                            }
                        }
                    }
                    // Downloaded only filter panel
                    if (showOptions) {
                        Column(
                            Modifier.padding(top = 48.dp).fillMaxWidth()
                                .clip(RoundedCornerShape(cornerM()))
                                .background(Color(0xFF1C1C2E))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(cornerM())),
                        ) {
                            // "SEARCH OPTIONS" label
                            Text(
                                "SEARCH OPTIONS",
                                color = Color(0xFF555555),
                                fontSize = textMicro(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = spacingL(), top = spacingM(), bottom = spacingXS()),
                            )
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.05f),
                                modifier = Modifier.padding(horizontal = spacingL()),
                            )
                            // Filter by type subsection
                            Column(
                                Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
                            ) {
                                Text(
                                    "Filter by type",
                                    color = Color(0xFF888888),
                                    fontSize = textLabelM(),
                                    modifier = Modifier.padding(bottom = spacingS()),
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(adp(6f))) {
                                    listOf(
                                        SearchFilterType.ALL to "All",
                                        SearchFilterType.ARTISTS to "Artists",
                                        SearchFilterType.ALBUMS to "Albums",
                                        SearchFilterType.SONGS to "Songs",
                                        SearchFilterType.PLAYLISTS to "Playlists",
                                    ).forEach { (type, label) ->
                                        val active = state.filterType == type
                                        Text(
                                            label,
                                            color = if (active) Color(0xFF00C8B4) else Color(0xFF666666),
                                            fontSize = textLabelM(),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(cornerS()))
                                                .background(
                                                    if (active) {
                                                        Color(
                                                            0xFF00C8B4,
                                                        ).copy(alpha = 0.20f)
                                                    } else {
                                                        Color(0xFF252538)
                                                    },
                                                )
                                                .border(
                                                    1.dp,
                                                    if (active) {
                                                        Color(
                                                            0xFF00C8B4,
                                                        ).copy(alpha = 0.4f)
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                    RoundedCornerShape(cornerS()),
                                                )
                                                .clickable { viewModel.setFilterType(type) }
                                                .padding(horizontal = 10.dp, vertical = spacingXS()),
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.05f),
                                modifier = Modifier.padding(horizontal = spacingL()),
                            )
                            // Downloaded only toggle row
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    viewModel.setFilterDownloaded(!state.filterDownloaded)
                                }.padding(horizontal = spacingL(), vertical = spacingM()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = if (state.filterDownloaded) Color(0xFFB040E8) else Color(0xFF555555),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Downloaded only",
                                    color = Color.White,
                                    fontSize = textBodyM(),
                                    modifier = Modifier.weight(1f),
                                )
                                // Toggle switch
                                Box(
                                    Modifier.size(36.dp, spacingXL()).clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (state.filterDownloaded) Color(0xFFB040E8) else Color(0xFF333333),
                                        ),
                                    contentAlignment =
                                        if (state.filterDownloaded) Alignment.CenterEnd else Alignment.CenterStart,
                                ) {
                                    Box(Modifier.size(16.dp).padding(2.dp).clip(CircleShape).background(Color.White))
                                }
                            }
                        }
                    }

                    // ── Autocomplete dropdown ──
                    AnimatedVisibility(
                        visible = showDropdown,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(cornerM()))
                                .background(Color(0xFF1C1C2E))
                                .border(1.dp, Color(0xFF00C8B4).copy(alpha = 0.25f), RoundedCornerShape(cornerM())),
                        ) {
                            if (state.query.isEmpty()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingS()),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Recent searches",
                                        color = Color(0xFF555555),
                                        fontSize = textLabelM(),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Clear all",
                                        color = Color(0xFFE84040),
                                        fontSize = textLabelM(),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { viewModel.clearAllRecent() },
                                    )
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                            }
                            dropdownItems.forEachIndexed { idx, term ->
                                key(term) {
                                    if (idx > 0) {
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.04f))
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            viewModel.onRecentTap(term)
                                            isFocused = false
                                        }.padding(horizontal = spacingL(), vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            null,
                                            tint = Color(0xFF444444),
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        if (state.query.isNotEmpty()) {
                                            val query = state.query
                                            val matchIndex = term.indexOf(query, ignoreCase = true)
                                            if (matchIndex >= 0) {
                                                Row(Modifier.weight(1f)) {
                                                    Text(
                                                        term.substring(0, matchIndex),
                                                        color = Color(0xFFCCCCCC),
                                                        fontSize = textBodyM(),
                                                    )
                                                    Text(
                                                        term.substring(matchIndex, matchIndex + query.length),
                                                        color = Color(0xFF00C8B4),
                                                        fontSize = textBodyM(),
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                    Text(
                                                        term.substring(matchIndex + query.length),
                                                        color = Color(0xFFCCCCCC),
                                                        fontSize = textBodyM(),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    term,
                                                    color = Color(0xFFCCCCCC),
                                                    fontSize = textBodyM(),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        } else {
                                            Text(
                                                term,
                                                color = Color(0xFFCCCCCC),
                                                fontSize = textBodyM(),
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        Icon(
                                            Icons.Default.Close,
                                            "Remove",
                                            tint = Color(0xFF444444),
                                            modifier = Modifier.size(11.dp).clickable { viewModel.clearRecent(term) },
                                        )
                                    }
                                } // key(term)
                            }
                        }
                    }
                }
            }
            // ── Filter type chips (visible after search) ──
            if (state.hasSearched && state.resultCount > 0) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingXS()),
                        horizontalArrangement = Arrangement.spacedBy(spacingS()),
                    ) {
                        listOf(
                            SearchFilterType.ALL to "All",
                            SearchFilterType.ARTISTS to "Artists",
                            SearchFilterType.ALBUMS to "Albums",
                            SearchFilterType.SONGS to "Songs",
                            SearchFilterType.PLAYLISTS to "Playlists",
                        ).forEach { (type, label) ->
                            val active = state.filterType == type
                            Text(
                                label,
                                color = if (active) Color(0xFF00C8B4) else Color(0xFF777777),
                                fontSize = textBodyM(),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (active) Color(0xFF00C8B4).copy(alpha = 0.20f) else Color(0xFF1C1C2E),
                                    )
                                    .border(
                                        1.dp,
                                        if (active) {
                                            Color(
                                                0xFF00C8B4,
                                            ).copy(alpha = 0.5f)
                                        } else {
                                            Color.White.copy(alpha = 0.08f)
                                        },
                                        RoundedCornerShape(50),
                                    )
                                    .clickable { viewModel.setFilterType(type) }
                                    .padding(horizontal = spacingM(), vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            // ── Content area ──
            if (state.query.isEmpty()) {
                // ═══ Idle state: Browse Genres ═══
                if (state.genres.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Browse Genres",
                            color = Color(0xFF888888),
                            fontSize = textLabelL(),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingXS()),
                        )
                    }
                    item {
                        Column(Modifier.padding(horizontal = spacingL())) {
                            val rows = state.genres.chunked(2)
                            rows.forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingM())) {
                                    row.forEach { genre ->
                                        val color = GENRE_COLORS[state.genres.indexOf(genre) % GENRE_COLORS.size]
                                        Column(
                                            Modifier.weight(1f).clip(RoundedCornerShape(cornerM()))
                                                .background(color.copy(alpha = 0.15f))
                                                .then(
                                                    Modifier.border(
                                                        1.dp,
                                                        color.copy(alpha = 0.3f),
                                                        RoundedCornerShape(cornerM()),
                                                    ),
                                                )
                                                .clickable { onGenreClick(genre.name) }
                                                .height(miniPlayerHeight()),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                null,
                                                tint = color.copy(alpha = 0.6f),
                                                modifier = Modifier.size(iconSmall()),
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                genre.name,
                                                color = Color.White,
                                                fontSize = textHeadingS(),
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    if (row.size < 2) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (state.hasSearched) {
                // ═══ Result count ═══
                item {
                    val countLabel = if (state.resultCount == 1) "1 result" else "${state.resultCount} results"
                    val filterLabel = if (state.filterType !=
                        SearchFilterType.ALL
                    ) {
                        " · ${state.filterType.name.lowercase()}"
                    } else {
                        ""
                    }
                    val dlLabel = if (state.filterDownloaded) " · downloaded only" else ""
                    Text(
                        "$countLabel$filterLabel$dlLabel",
                        color = Color(0xFF888888),
                        fontSize = textLabelM(),
                        modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingXS()),
                    )
                }

                val showArtists =
                    state.filterType == SearchFilterType.ALL || state.filterType == SearchFilterType.ARTISTS
                val showAlbums = state.filterType == SearchFilterType.ALL || state.filterType == SearchFilterType.ALBUMS
                val showSongs = state.filterType == SearchFilterType.ALL || state.filterType == SearchFilterType.SONGS
                val showPlaylists =
                    state.filterType == SearchFilterType.ALL || state.filterType == SearchFilterType.PLAYLISTS

                // ── ARTISTS section ──
                if (showArtists && state.artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    if (state.artists.size <= 2) {
                        // YouTube Music-style cards for 1–2 artists
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = spacingL()),
                                horizontalArrangement = Arrangement.spacedBy(spacingL()),
                            ) {
                                state.artists.forEach { a ->
                                    val artUrl = artistArtCache[a.name]
                                    Column(
                                        Modifier.weight(1f).clickable { onArtistClick(a.id) },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Box(
                                            Modifier.size(80.dp).clip(CircleShape)
                                                .border(2.dp, Color(0xFF00C8B4).copy(alpha = 0.3f), CircleShape)
                                                .background(Color(0xFF1E1E1E)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (artUrl != null) {
                                                androidx.compose.foundation.Image(
                                                    painter = rememberAsyncImagePainter(
                                                        ImageRequest.Builder(
                                                            context,
                                                        ).data(artUrl).crossfade(true).build(),
                                                    ),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Person,
                                                    null,
                                                    tint = Color(0xFF555555),
                                                    modifier = Modifier.size(32.dp),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            a.name,
                                            color = Color.White,
                                            fontSize = textBodyM(),
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        a.albumCount?.let {
                                            Text("$it albums", color = Color(0xFF888888), fontSize = textLabelM())
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "View albums",
                                            color = Color(0xFF00C8B4),
                                            fontSize = textLabelM(),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(0xFF00C8B4).copy(alpha = 0.12f))
                                                .border(
                                                    1.dp,
                                                    Color(0xFF00C8B4).copy(alpha = 0.3f),
                                                    RoundedCornerShape(50),
                                                )
                                                .padding(horizontal = spacingM(), vertical = spacingXS()),
                                        )
                                    }
                                }
                                if (state.artists.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        // List rows for 3+ artists
                        items(state.artists, key = { it.id }) { a ->
                            val artUrl = artistArtCache[a.name] ?: artistArtUrl(a.name)
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    onArtistClick(a.id)
                                }.padding(horizontal = spacingL(), vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(iconLarge()).clip(CircleShape).background(Color(0xFF1E1E1E)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (artUrl != null) {
                                        androidx.compose.foundation.Image(
                                            painter = rememberAsyncImagePainter(
                                                ImageRequest.Builder(context).data(artUrl).crossfade(true).build(),
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            null,
                                            tint = Color(0xFF555555),
                                            modifier = Modifier.size(iconSmall()),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        a.name,
                                        color = Color.White,
                                        fontSize = textHeadingS(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    a.albumCount?.let {
                                        Text("$it albums", color = Color(0xFF888888), fontSize = textBodyM())
                                    }
                                }
                                TypeBadge("artist")
                                Icon(
                                    Icons.Default.ChevronRight,
                                    null,
                                    tint = Color(0xFF444444),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.04f),
                                modifier = Modifier.padding(horizontal = spacingL()),
                            )
                        }
                    }
                }

                // ── ALBUMS section (list rows) ──
                if (showAlbums && state.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    items(state.albums, key = { it.id }) { album ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onAlbumClick(album.id)
                            }.padding(horizontal = spacingL(), vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val albumCoverUrl = rememberCoverArtUrl(album.coverArt, 120)
                            Box(
                                Modifier.size(
                                    iconLarge(),
                                ).clip(RoundedCornerShape(cornerS())).background(Color(0xFF1E1E1E)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (albumCoverUrl != null) {
                                    AsyncImage(
                                        model = albumCoverUrl,
                                        contentDescription = album.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Album,
                                        null,
                                        tint = Color(0xFF444444),
                                        modifier = Modifier.size(iconSmall()),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    album.name,
                                    color = Color.White,
                                    fontSize = textHeadingS(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitle = buildString {
                                    album.artist?.let { append(it) }
                                    album.year?.let {
                                        if (isNotEmpty()) append(" · ")
                                        append(it.toString())
                                    }
                                }
                                if (subtitle.isNotEmpty()) {
                                    Text(
                                        subtitle,
                                        color = Color(0xFF888888),
                                        fontSize = textBodyM(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            album.rating?.let { r ->
                                if (r > 0) {
                                    Row(Modifier.padding(end = spacingS())) {
                                        for (i in 1..5) {
                                            Icon(
                                                if (i <= r) Icons.Default.Star else Icons.Default.Star,
                                                null,
                                                tint = if (i <= r) Color(0xFF00C8B4) else Color(0xFF444444),
                                                modifier = Modifier.size(iconMicro()),
                                            )
                                        }
                                    }
                                }
                            }
                            TypeBadge("album")
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.04f),
                            modifier = Modifier.padding(horizontal = spacingL()),
                        )
                    }
                }

                // ── SONGS section ──
                if (showSongs && state.tracks.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    items(state.tracks, key = { it.id }) { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onTrackClick(t)
                            }.padding(horizontal = spacingL(), vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val trackCoverUrl = rememberCoverArtUrl(t.coverArt, 120)
                            if (trackCoverUrl != null) {
                                AsyncImage(
                                    model = trackCoverUrl,
                                    contentDescription = t.title,
                                    modifier = Modifier.size(iconLarge()).clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    Modifier.size(
                                        iconLarge(),
                                    ).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1E1E)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        null,
                                        tint = Color(0xFF555555),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title,
                                    color = Color.White,
                                    fontSize = textHeadingS(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitle = buildString {
                                    t.artist?.let { append(it) }
                                    if (t.album != null) {
                                        if (isNotEmpty()) append(" · ")
                                        append(t.album)
                                    }
                                }
                                if (subtitle.isNotEmpty()) {
                                    Text(
                                        subtitle,
                                        color = Color(0xFF888888),
                                        fontSize = textBodyM(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (t.id in state.localTrackIds) {
                                DownloadDot("downloaded")
                                Spacer(Modifier.width(6.dp))
                            }
                            if (t.formattedDuration.isNotEmpty()) {
                                Text(
                                    t.formattedDuration,
                                    color = Color(0xFF888888),
                                    fontSize = textBodyM(),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            TypeBadge("song")
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.04f),
                            modifier = Modifier.padding(horizontal = spacingL()),
                        )
                    }
                }

                // ── PLAYLISTS section ──
                if (showPlaylists && state.playlists.isNotEmpty()) {
                    item { SectionHeader("Playlists") }
                    items(state.playlists, key = { it.id }) { pl ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onPlaylistClick(pl.id)
                            }.padding(horizontal = spacingL(), vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val plCoverUrl = rememberCoverArtUrl(pl.coverArt, 120)
                            Box(
                                Modifier.size(
                                    iconLarge(),
                                ).clip(RoundedCornerShape(cornerS())).background(Color(0xFF1E1E1E)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (plCoverUrl != null) {
                                    AsyncImage(
                                        model = plCoverUrl,
                                        contentDescription = pl.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.QueueMusic,
                                        null,
                                        tint = Color(0xFF444444),
                                        modifier = Modifier.size(iconSmall()),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    pl.name,
                                    color = Color.White,
                                    fontSize = textHeadingS(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("${pl.songCount} tracks", color = Color(0xFF888888), fontSize = textBodyM())
                            }
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = Color(0xFFB040E8),
                                modifier = Modifier.size(iconMicro()),
                            )
                            TypeBadge("playlist")
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.04f),
                            modifier = Modifier.padding(horizontal = spacingL()),
                        )
                    }
                }

                // ── No results ──
                if (state.resultCount == 0) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No results found", color = Color(0xFF666666), fontSize = textHeadingS())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = Color(0xFF888888),
        fontSize = textLabelL(),
        modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
    )
}
