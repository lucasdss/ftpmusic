package com.lucasdss.ftpmusic.app.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.components.AlbumDownloadBadge
import com.lucasdss.ftpmusic.app.ui.components.CoverArtImage
import com.lucasdss.ftpmusic.app.ui.components.DownloadDot
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import com.lucasdss.ftpmusic.app.ui.components.SongListRow
import com.lucasdss.ftpmusic.app.ui.components.downloadStatus
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl
import com.lucasdss.ftpmusic.app.ui.library.rememberPreferredCoverArt
import kotlinx.coroutines.flow.*

private val GENRE_COLORS = listOf(
    BrandTeal, // teal
    BrandPurple, // purple
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

    // Trigger artist art fetch when artist *ids* change (avoid thrash on same set)
    val artistIdsKey = remember(state.artists) { state.artists.take(5).joinToString { it.id } }
    LaunchedEffect(artistIdsKey) {
        state.artists.take(5).forEach { a ->
            coverArtFallback.fetchArtistArt(a.name).collect { /* file cached */ }
            val cacheKey = "artist|${a.name.lowercase()}"
            val cachedFile = java.io.File(coverArtFallback.cacheDir, "${cacheKey.hashCode()}.jpg")
            artistArtCache[a.name] =
                if (cachedFile.exists() && cachedFile.length() > 0) cachedFile.absolutePath else null
        }
        if (artistIdsKey.isNotEmpty()) artistArtVersion++
    }

    Scaffold(
        containerColor = Background,
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
                                focusedContainerColor = Surface,
                                unfocusedContainerColor = Surface,
                                focusedBorderColor = BrandTeal.copy(alpha = 0.4f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                cursorColor = BrandTeal,
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
                        // Search Options toggle (≥48dp hit target)
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(cornerM()))
                                .background(
                                    if (showOptions || state.filterDownloaded) {
                                        BrandTeal.copy(alpha = 0.15f)
                                    } else {
                                        Surface
                                    },
                                )
                                .border(
                                    1.dp,
                                    if (showOptions || state.filterDownloaded) {
                                        BrandTeal.copy(alpha = 0.35f)
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
                                    BrandTeal
                                } else {
                                    Color(0xFF666666)
                                },
                                modifier = Modifier.size(iconSmall()),
                            )
                            if (state.filterDownloaded) {
                                Box(
                                    Modifier.size(
                                        8.dp,
                                    ).align(Alignment.TopEnd).background(BrandPurple, CircleShape),
                                )
                            }
                        }
                    }
                    // Downloaded only filter panel
                    if (showOptions) {
                        Column(
                            Modifier.padding(top = 48.dp).fillMaxWidth()
                                .clip(RoundedCornerShape(cornerM()))
                                .background(Surface)
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(cornerM())),
                        ) {
                            // "SEARCH OPTIONS" label
                            Text(
                                "SEARCH OPTIONS",
                                color = NavUnselected,
                                fontSize = textMicro(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = spacingL(), top = spacingM(), bottom = spacingXS()),
                            )
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.05f),
                                modifier = Modifier.padding(horizontal = spacingL()),
                            )
                            // Downloaded only — type filters live in scrollable chip row (Phase-4)
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
                                    tint = if (state.filterDownloaded) BrandPurple else NavUnselected,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                FittingText(
                                    text = "Downloaded only",
                                    color = Color.White,
                                    fontSize = textBodyM(),
                                    minFontSize = textMicro(),
                                    modifier = Modifier.weight(1f),
                                    fillMaxWidth = false,
                                )
                                // Toggle switch
                                Box(
                                    Modifier.size(36.dp, spacingXL()).clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (state.filterDownloaded) BrandPurple else Color(0xFF333333),
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
                                .background(Surface)
                                .border(1.dp, BrandTeal.copy(alpha = 0.25f), RoundedCornerShape(cornerM())),
                        ) {
                            if (state.query.isEmpty()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingS()),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Recent searches",
                                        color = NavUnselected,
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
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clickable {
                                                viewModel.onRecentTap(term)
                                                isFocused = false
                                            }
                                            .padding(horizontal = spacingL(), vertical = spacingS()),
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
                                                        color = BrandTeal,
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
                                            FittingText(
                                                text = term,
                                                color = Color(0xFFCCCCCC),
                                                fontSize = textBodyM(),
                                                minFontSize = textMicro(),
                                                modifier = Modifier.weight(1f),
                                                fillMaxWidth = false,
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.clearRecent(term) },
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                "Remove",
                                                tint = Color(0xFF888888),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                } // key(term)
                            }
                        }
                    }
                }
            }
            // Loading bar + skeleton rows (market: Spotify/YT placeholder rows)
            if (state.isLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = spacingL()),
                        color = BrandTeal,
                        trackColor = Color.White.copy(alpha = 0.08f),
                    )
                }
                if (state.hasSearched || state.query.trim().length >= 2) {
                    items(6, key = { "skel-$it" }) {
                        SearchSkeletonRow()
                    }
                }
            }
            // ── Filter type chips (sticky when searched, even zero results) ──
            if (state.hasSearched) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacingL(), vertical = spacingXS())
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacingS()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(
                            SearchFilterType.ALL to "All",
                            SearchFilterType.ARTISTS to "Artists",
                            SearchFilterType.ALBUMS to "Albums",
                            SearchFilterType.SONGS to "Songs",
                            SearchFilterType.PLAYLISTS to "Playlists",
                            SearchFilterType.GENRES to "Genres",
                        ).forEach { (type, label) ->
                            val active = state.filterType == type
                            Box(
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(cornerM()))
                                    .background(
                                        if (active) BrandTeal.copy(alpha = 0.20f) else Surface,
                                    )
                                    .border(
                                        1.dp,
                                        if (active) BrandTeal.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(cornerM()),
                                    )
                                    .clickable { viewModel.setFilterType(type) }
                                    .padding(horizontal = spacingL(), vertical = spacingM()),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    color = if (active) BrandTeal else Color(0xFF777777),
                                    fontSize = textBodyM(),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            // ── Content area ──
            if (state.query.isEmpty()) {
                if (viewModel.isLocalOnly()) {
                    item { SearchStatusBanner("Offline · downloaded only") }
                }
                if (state.isIndexingLibrary) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = spacingL(), vertical = spacingXL()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Indexing library…",
                                color = Color.White,
                                fontSize = textHeadingS(),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(spacingXS()))
                            Text(
                                "Search works offline once FTS5 index finishes — sync in progress",
                                color = Color(0xFF999999),
                                fontSize = textLabelM(),
                            )
                        }
                    }
                }
                // Idle Recents (market: Spotify/Apple show when query empty)
                if (state.recentSearches.isNotEmpty() && !isFocused) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacingL(), vertical = spacingS()),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Recent searches",
                                color = Color(0xFF888888),
                                fontSize = textLabelL(),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Clear all",
                                color = Color(0xFFE84040),
                                fontSize = textLabelM(),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable { viewModel.clearAllRecent() }
                                    .wrapContentHeight(Alignment.CenterVertically),
                            )
                        }
                    }
                    items(state.recentSearches, key = { "idle-recent-$it" }) { term ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { viewModel.onRecentTap(term) }
                                .padding(horizontal = spacingL(), vertical = spacingS()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                null,
                                tint = Color(0xFF666666),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                term,
                                color = Color.White,
                                fontSize = textBodyM(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { viewModel.clearRecent(term) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Remove",
                                    tint = Color(0xFF888888),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.04f),
                            modifier = Modifier.padding(horizontal = spacingL()),
                        )
                    }
                }
                // Decade chips (Phase-3 WS-I)
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Browse Decades",
                        color = Color(0xFF888888),
                        fontSize = textLabelL(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingXS()),
                    )
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacingL())
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacingS()),
                    ) {
                        listOf("60s", "70s", "80s", "90s", "2000s", "2010s", "2020s").forEach { decade ->
                            Box(
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(cornerM()))
                                    .background(BrandTeal.copy(alpha = 0.15f))
                                    .clickable { viewModel.onDecadeChip(decade) }
                                    .padding(horizontal = spacingL(), vertical = spacingM()),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    decade,
                                    color = Color.White,
                                    fontSize = textHeadingS(),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
                // Moods chips (Phase-5)
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Browse Moods",
                        color = Color(0xFF888888),
                        fontSize = textLabelL(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingXS()),
                    )
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacingL())
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacingS()),
                    ) {
                        com.lucasdss.ftpmusic.app.data.search.SearchMoodTags.MOODS.forEach { mood ->
                            Box(
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(cornerM()))
                                    .background(BrandPurple.copy(alpha = 0.15f))
                                    .border(1.dp, BrandPurple.copy(alpha = 0.35f), RoundedCornerShape(cornerM()))
                                    .clickable { viewModel.onMoodChip(mood.label) }
                                    .padding(horizontal = spacingL(), vertical = spacingM()),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    mood.label,
                                    color = Color.White,
                                    fontSize = textHeadingS(),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
                // Tags chips (Phase-5)
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Browse Tags",
                        color = Color(0xFF888888),
                        fontSize = textLabelL(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingXS()),
                    )
                }
                if (state.popularTags.isNotEmpty()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacingL())
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacingS()),
                        ) {
                            state.popularTags.forEach { tag ->
                                Box(
                                    Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(cornerM()))
                                        .background(BrandTeal.copy(alpha = 0.12f))
                                        .border(1.dp, BrandTeal.copy(alpha = 0.3f), RoundedCornerShape(cornerM()))
                                        .clickable { viewModel.onTagChip(tag) }
                                        .padding(horizontal = spacingL(), vertical = spacingM()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        tag.replaceFirstChar { it.uppercase() },
                                        color = Color.White,
                                        fontSize = textHeadingS(),
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                } else if (state.tagsEmpty) {
                    item {
                        Text(
                            if (state.hasLastFmKey) {
                                "Tags appear after library sync enrichment"
                            } else {
                                "Add Last.fm API key in Settings to unlock tags"
                            },
                            color = Color(0xFF666666),
                            fontSize = textLabelM(),
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingXS()),
                        )
                    }
                }
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
                        val genreColorIndex = remember(state.genres) {
                            state.genres.mapIndexed { i, g -> g.name to i }.toMap()
                        }
                        Column(Modifier.padding(horizontal = spacingL())) {
                            val rows = state.genres.chunked(2)
                            rows.forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacingM())) {
                                    row.forEach { genre ->
                                        val color = GENRE_COLORS[
                                            (genreColorIndex[genre.name] ?: 0) % GENRE_COLORS.size,
                                        ]
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
                if (viewModel.isLocalOnly()) {
                    item { SearchStatusBanner("Offline · downloaded only") }
                    item { SearchStatusBanner("Discover unavailable offline") }
                }
                state.searchError?.let { err ->
                    item {
                        SearchStatusBanner(
                            message = err,
                            actionLabel = "Retry",
                            onAction = { viewModel.retrySearch() },
                        )
                    }
                }
                if (state.usedSoftTypo && state.resultCount > 0) {
                    item {
                        Text(
                            "Close matches — check spelling",
                            color = Color(0xFF999999),
                            fontSize = textLabelM(),
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingXS()),
                        )
                    }
                }

                // Top result hero — cover art + ≥48dp row (market P1)
                val top = state.topHit
                if (top != null && state.filterType == SearchFilterType.ALL) {
                    item { SectionHeader("Top result") }
                    item {
                        when (top) {
                            is SearchTopHit.TrackHit -> {
                                val t = top.track
                                val coverUrl = rememberCoverArtUrl(t.coverArt, 160)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onTrackClick(t) }
                                        .padding(horizontal = spacingL(), vertical = spacingM())
                                        .heightIn(min = 56.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SearchCoverThumb(coverUrl, t.title)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            t.title,
                                            color = Color.White,
                                            fontSize = textHeadingS(),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val sub = buildString {
                                            t.artist?.let { append(it) }
                                            val albumLabel = t.album?.takeIf { it.isNotBlank() }
                                                ?: if (t.albumId == null) "Singles" else null
                                            if (albumLabel != null) {
                                                if (isNotEmpty()) append(" · ")
                                                append(albumLabel)
                                            }
                                        }
                                        if (sub.isNotBlank()) {
                                            Text(sub, color = Color(0xFF999999), fontSize = textLabelM(), maxLines = 1)
                                        }
                                    }
                                    IconButton(
                                        onClick = { onTrackClick(t) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = BrandTeal,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                    TypeBadge("song")
                                }
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.04f),
                                    modifier = Modifier.padding(horizontal = spacingL()),
                                )
                            }

                            is SearchTopHit.ArtistHit -> {
                                val a = top.artist
                                val coverUrl = rememberCoverArtUrl(a.coverArt, 160)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onArtistClick(a.id) }
                                        .padding(horizontal = spacingL(), vertical = spacingM())
                                        .heightIn(min = 56.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SearchCoverThumb(coverUrl, a.name, circle = true)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            a.name,
                                            color = Color.White,
                                            fontSize = textHeadingS(),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text("Artist", color = Color(0xFF999999), fontSize = textLabelM())
                                    }
                                    TypeBadge("artist")
                                }
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.04f),
                                    modifier = Modifier.padding(horizontal = spacingL()),
                                )
                            }

                            is SearchTopHit.AlbumHit -> {
                                val a = top.album
                                val coverUrl = rememberCoverArtUrl(a.coverArt, 160)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onAlbumClick(a.id) }
                                        .padding(horizontal = spacingL(), vertical = spacingM())
                                        .heightIn(min = 56.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SearchCoverThumb(coverUrl, a.name)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            a.name,
                                            color = Color.White,
                                            fontSize = textHeadingS(),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        a.artist?.let {
                                            Text(it, color = Color(0xFF999999), fontSize = textLabelM(), maxLines = 1)
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
                    }
                }

                val showArtists =
                    state.filterType == SearchFilterType.ALL || state.filterType == SearchFilterType.ARTISTS
                val showAlbums = state.filterType == SearchFilterType.ALL || state.filterType == SearchFilterType.ALBUMS
                val showSongs = state.filterType == SearchFilterType.ALL || state.filterType == SearchFilterType.SONGS
                val showPlaylists =
                    state.filterType == SearchFilterType.ALL || state.filterType == SearchFilterType.PLAYLISTS
                val showGenres =
                    state.filterType == SearchFilterType.ALL || state.filterType == SearchFilterType.GENRES

                // Matched tags chip strip (Phase-5)
                if (state.matchedTags.isNotEmpty() && state.filterType == SearchFilterType.ALL) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacingL(), vertical = spacingXS())
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacingS()),
                        ) {
                            state.matchedTags.forEach { tag ->
                                Box(
                                    Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(cornerM()))
                                        .background(BrandPurple.copy(alpha = 0.12f))
                                        .border(1.dp, BrandPurple.copy(alpha = 0.3f), RoundedCornerShape(cornerM()))
                                        .clickable { viewModel.onTagChip(tag) }
                                        .padding(horizontal = spacingL(), vertical = spacingM()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        tag.replaceFirstChar { it.uppercase() },
                                        color = Color.White,
                                        fontSize = textLabelM(),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── GENRES section (text-matched) ──
                if (showGenres && state.matchedGenres.isNotEmpty()) {
                    item { SectionHeader("Genres") }
                    items(state.matchedGenres, key = { "genre-${it.name}" }) { genre ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onGenreClick(genre.name) }
                                .padding(horizontal = spacingL(), vertical = spacingS()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(iconLarge()).clip(RoundedCornerShape(cornerS()))
                                    .background(BrandTeal.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Label,
                                    null,
                                    tint = BrandTeal,
                                    modifier = Modifier.size(iconSmall()),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                FittingText(
                                    text = genre.name,
                                    color = Color.White,
                                    fontSize = textHeadingS(),
                                    minFontSize = textMicro(),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                FittingText(
                                    text = "${genre.songCount} songs · ${genre.albumCount} albums",
                                    color = Color(0xFF888888),
                                    fontSize = textLabelM(),
                                    minFontSize = textMicro(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            TypeBadge("genre")
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.04f),
                            modifier = Modifier.padding(horizontal = spacingL()),
                        )
                    }
                }

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
                                                .border(2.dp, BrandTeal.copy(alpha = 0.3f), CircleShape)
                                                .background(Color(0xFF1E1E1E)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (artUrl != null) {
                                                androidx.compose.foundation.Image(
                                                    painter = rememberAsyncImagePainter(
                                                        ImageRequest.Builder(
                                                            context,
                                                        ).data(artUrl).size(160).crossfade(false).build(),
                                                    ),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Person,
                                                    null,
                                                    tint = NavUnselected,
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
                                            color = BrandTeal,
                                            fontSize = textLabelM(),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(BrandTeal.copy(alpha = 0.12f))
                                                .border(
                                                    1.dp,
                                                    BrandTeal.copy(alpha = 0.3f),
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
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { onArtistClick(a.id) }
                                    .padding(horizontal = spacingL(), vertical = spacingS()),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(iconLarge()).clip(CircleShape).background(Color(0xFF1E1E1E)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (artUrl != null) {
                                        androidx.compose.foundation.Image(
                                            painter = rememberAsyncImagePainter(
                                                ImageRequest.Builder(
                                                    context,
                                                ).data(artUrl).size(96).crossfade(false).build(),
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            null,
                                            tint = NavUnselected,
                                            modifier = Modifier.size(iconSmall()),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    FittingText(
                                        text = a.name,
                                        color = Color.White,
                                        fontSize = textHeadingS(),
                                        minFontSize = textMicro(),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    a.albumCount?.let {
                                        FittingText(
                                            text = "$it albums",
                                            color = Color(0xFF888888),
                                            fontSize = textLabelM(),
                                            minFontSize = textMicro(),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
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
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onAlbumClick(album.id) }
                                .padding(horizontal = spacingL(), vertical = spacingS()),
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
                                FittingText(
                                    text = album.name,
                                    color = Color.White,
                                    fontSize = textHeadingS(),
                                    minFontSize = textMicro(),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                val subtitle = buildString {
                                    album.artist?.let { append(it) }
                                    album.year?.let {
                                        if (isNotEmpty()) append(" · ")
                                        append(it.toString())
                                    }
                                }
                                if (subtitle.isNotEmpty()) {
                                    FittingText(
                                        text = subtitle,
                                        color = Color(0xFF888888),
                                        fontSize = textLabelM(),
                                        minFontSize = textMicro(),
                                        modifier = Modifier.fillMaxWidth(),
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
                                                tint = if (i <= r) BrandTeal else Color(0xFF444444),
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

                // ── FROM LYRICS (Phase-6) ──
                if (showSongs && state.lyricsMatches.isNotEmpty()) {
                    item { SectionHeader("From lyrics") }
                    items(state.lyricsMatches, key = { "lyric-${it.track.id}" }) { hit ->
                        val t = hit.track
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onTrackClick(t) }
                                .padding(horizontal = spacingL(), vertical = spacingM())
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title,
                                    color = Color.White,
                                    fontSize = textHeadingS(),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val lyricSub = hit.snippet ?: buildString {
                                    t.artist?.let { append(it) }
                                    val albumLabel = t.album?.takeIf { it.isNotBlank() }
                                        ?: if (t.albumId == null) "Singles" else null
                                    if (albumLabel != null) {
                                        if (isNotEmpty()) append(" · ")
                                        append(albumLabel)
                                    }
                                }
                                if (lyricSub.isNotBlank()) {
                                    if (hit.snippet != null) {
                                        Text(
                                            highlightQuery(lyricSub, state.query),
                                            color = Color(0xFF999999),
                                            fontSize = textLabelM(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    } else {
                                        Text(
                                            lyricSub,
                                            color = Color(0xFF999999),
                                            fontSize = textLabelM(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            TypeBadge("lyrics")
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
                    items(state.tracks, key = { it.id }, contentType = { "song" }) { t ->
                        val trackCoverUrl = rememberCoverArtUrl(t.coverArt, 120)
                        val subtitle = buildString {
                            t.artist?.let { append(it) }
                            val albumLabel = t.album?.takeIf { it.isNotBlank() }
                                ?: if (t.albumId == null) "Singles" else null
                            if (albumLabel != null) {
                                if (isNotEmpty()) append(" · ")
                                append(albumLabel)
                            }
                        }.ifBlank { null }
                        SongListRow(
                            title = t.title,
                            subtitle = subtitle,
                            downloadStatus = if (t.id in state.localTrackIds) "downloaded" else "none",
                            durationLabel = t.formattedDuration.takeIf { it.isNotEmpty() },
                            onClick = { onTrackClick(t) },
                            modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
                            leadingContent = {
                                if (trackCoverUrl != null) {
                                    CoverArtImage(
                                        url = trackCoverUrl,
                                        contentDescription = t.title,
                                        modifier = Modifier.size(iconLarge()).clip(RoundedCornerShape(6.dp)),
                                        decodeSize = iconLarge(),
                                    )
                                } else {
                                    Box(
                                        Modifier.size(iconLarge())
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1E1E1E)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            null,
                                            tint = NavUnselected,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            },
                            trailingContent = { TypeBadge("song") },
                        )
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
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onPlaylistClick(pl.id) }
                                .padding(horizontal = spacingL(), vertical = spacingS()),
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
                                FittingText(
                                    text = pl.name,
                                    color = Color.White,
                                    fontSize = textHeadingS(),
                                    minFontSize = textMicro(),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                FittingText(
                                    text = "${pl.songCount} tracks",
                                    color = Color(0xFF888888),
                                    fontSize = textLabelM(),
                                    minFontSize = textMicro(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = BrandPurple,
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

                // Discover (MusicBrainz / Last.fm) — Phase-5
                if (state.filterType == SearchFilterType.ALL &&
                    (state.discoverArtists.isNotEmpty() || state.discoverTracks.isNotEmpty() || state.isDiscoverLoading)
                ) {
                    item { SectionHeader("Discover") }
                    if (state.isDiscoverLoading && state.discoverArtists.isEmpty() && state.discoverTracks.isEmpty()) {
                        item {
                            Text(
                                "Searching MusicBrainz…",
                                color = Color(0xFF666666),
                                fontSize = textLabelM(),
                                modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
                            )
                        }
                    }
                    items(state.discoverArtists, key = { "disc-ar-${it.mbid ?: it.name}" }) { hit ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onDiscoverArtistTap(hit) { id -> onArtistClick(id) }
                                }
                                .padding(horizontal = spacingL(), vertical = spacingM())
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SearchCoverThumb(null, hit.name, circle = true)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    hit.name,
                                    color = Color.White,
                                    fontSize = textHeadingS(),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val sub = when {
                                    hit.inLibrary -> "In library"
                                    !hit.disambiguation.isNullOrBlank() -> hit.disambiguation
                                    else -> "Not in library · ${hit.source}"
                                }
                                Text(sub, color = Color(0xFF888888), fontSize = textLabelM(), maxLines = 1)
                            }
                            TypeBadge("artist")
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.04f),
                            modifier = Modifier.padding(horizontal = spacingL()),
                        )
                    }
                    items(state.discoverTracks, key = { "disc-tr-${it.mbid ?: it.title}" }) { hit ->
                        val localId = hit.localTrackId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (localId != null) {
                                        Modifier.clickable {
                                            onTrackClick(
                                                com.lucasdss.ftpmusic.app.data.model.Track(
                                                    id = localId,
                                                    title = hit.title,
                                                    artist = hit.artistName,
                                                ),
                                            )
                                        }
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(horizontal = spacingL(), vertical = spacingM())
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SearchCoverThumb(null, hit.title, circle = false)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    hit.title,
                                    color = Color.White,
                                    fontSize = textHeadingS(),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildString {
                                        hit.artistName?.let {
                                            append(it)
                                            append(" · ")
                                        }
                                        append(if (hit.inLibrary) "In library" else "Not in library")
                                    },
                                    color = Color(0xFF999999),
                                    fontSize = textLabelM(),
                                    maxLines = 1,
                                )
                            }
                            TypeBadge("song")
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.04f),
                            modifier = Modifier.padding(horizontal = spacingL()),
                        )
                    }
                }

                // ── No results ──
                if (state.resultCount == 0 && !state.isLoading &&
                    state.discoverArtists.isEmpty() && state.discoverTracks.isEmpty()
                ) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No results found", color = Color(0xFF888888), fontSize = textHeadingS())
                            Spacer(Modifier.height(spacingS()))
                            val tip = when {
                                state.searchError != null -> state.searchError!!
                                viewModel.isLocalOnly() -> "Offline — try Downloaded only or sync when online"
                                state.ftsEmpty -> "Library still indexing — pull to sync or try again shortly"
                                state.usedSoftTypo -> "No close matches — check spelling"
                                !state.searchLyricsEnabled -> "Enable Search lyrics in Settings to match song words"
                                else -> "Try another spelling, an artist name, or a decade like 90s"
                            }
                            Text(
                                tip,
                                color = Color(0xFF999999),
                                fontSize = textLabelM(),
                                modifier = Modifier.padding(horizontal = spacingL()),
                            )
                            if (state.searchError != null) {
                                Spacer(Modifier.height(spacingM()))
                                TextButton(onClick = { viewModel.retrySearch() }) {
                                    Text("Retry", color = BrandTeal, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchStatusBanner(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacingL(), vertical = spacingXS())
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = Color(0xFF999999),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            message,
            color = Color(0xFF999999),
            fontSize = textLabelM(),
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = BrandTeal, fontWeight = FontWeight.SemiBold, fontSize = textLabelM())
            }
        }
    }
}

private fun highlightQuery(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return buildAnnotatedString { append(text) }
    val idx = text.indexOf(q, ignoreCase = true)
    if (idx < 0) return buildAnnotatedString { append(text) }
    return buildAnnotatedString {
        append(text.substring(0, idx))
        withStyle(SpanStyle(color = BrandTeal, fontWeight = FontWeight.SemiBold)) {
            append(text.substring(idx, idx + q.length))
        }
        append(text.substring(idx + q.length))
    }
}

@Composable
private fun SearchCoverThumb(url: String?, contentDesc: String, circle: Boolean = false) {
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

@Composable
private fun SearchSkeletonRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = spacingL(), vertical = spacingS()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1E1E)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF252525)),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E1E1E)),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = Color.White,
        fontSize = textLabelL(),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
    )
}
