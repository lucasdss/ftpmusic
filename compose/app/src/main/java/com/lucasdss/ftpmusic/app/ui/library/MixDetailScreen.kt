package com.lucasdss.ftpmusic.app.ui.library

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverKind
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverResolver
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverStore
import com.lucasdss.ftpmusic.app.data.db.CachedGenreSongEntity
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.art.ArtBackdrop
import com.lucasdss.ftpmusic.app.ui.art.rememberCoverScheme
import com.lucasdss.ftpmusic.app.ui.components.CollectionCoverArt
import com.lucasdss.ftpmusic.app.ui.components.CollectionCoverSubject
import com.lucasdss.ftpmusic.app.ui.components.DetailBackButton
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import com.lucasdss.ftpmusic.app.ui.components.ScrollToNowPlayingEffect
import com.lucasdss.ftpmusic.app.ui.components.SongListRow
import com.lucasdss.ftpmusic.app.ui.components.formatSongDuration
import com.lucasdss.ftpmusic.app.ui.components.indexOfTrackId
import com.lucasdss.ftpmusic.app.ui.components.rememberCollectionCoverEditController
import com.lucasdss.ftpmusic.app.ui.components.rememberResolvedCollectionCover
import com.lucasdss.ftpmusic.app.ui.components.resolveLazyIndex
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.ui.library.rememberPreferredCoverArt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GenreMixTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val albumId: String?,
    val duration: Int?,
    val trackNumber: Int?,
    val coverArt: String?,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MixDetailScreen(
    mixId: Long,
    mixName: String,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    viewModel: MixDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(mixId) { viewModel.loadMix(mixId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()
    // System back AND the toolbar button both activate pending then pop once.
    // BackHandler is required so predictive/system back still runs leave→activate
    // (NavHost's default pop would skip it). leaveActivated makes the activate
    // side idempotent if both fire in the same gesture.
    val leaveAndBack: () -> Unit = {
        viewModel.onLeaveActivatePending()
        onBack()
    }
    androidx.activity.compose.BackHandler { leaveAndBack() }
    var showMixSheet by remember { mutableStateOf(false) }
    var showTrackSheet by remember { mutableStateOf<GenreMixTrack?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }
    val dominantGenre = remember(state.recipeGenres) {
        state.recipeGenres.firstOrNull { it.isNotBlank() }
    }
    val coverEdit = rememberCollectionCoverEditController(
        title = mixName,
        hasFixedCover = state.fixedCoverKind != null,
        subject = CollectionCoverSubject.MIX,
        collectionName = mixName,
        dominantGenre = dominantGenre,
        onLibraryCover = viewModel::setFixedCoverFromLibrary,
        onCroppedBitmap = viewModel::setFixedCoverFromCroppedBitmap,
        onSavedCover = viewModel::setFixedCoverFromSaved,
        onBundledCover = viewModel::setFixedCoverFromBundled,
        onDerivedCover = viewModel::setFixedCoverFromDerived,
        onClear = viewModel::clearFixedCover,
        onLoadFailed = viewModel::notifyCoverCropLoadFailed,
        onExportFailed = viewModel::notifyCoverCropExportFailed,
    )
    val listState = rememberLazyListState(
        cacheWindow = LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.2f),
    )
    val autoCenter = LocalListChromePrefs.current.autoCenterNowPlaying
    val trackIds = remember(state.tracks) { state.tracks.map { it.id } }
    val nowPlayingLazyIndex = remember(trackIds, currentTrackId) {
        resolveLazyIndex(headerCount = 1, trackIndex = indexOfTrackId(trackIds, currentTrackId))
    }
    ScrollToNowPlayingEffect(
        enabled = autoCenter && state.tracks.isNotEmpty(),
        listState = listState,
        targetLazyIndex = nowPlayingLazyIndex,
        scrollKey = currentTrackId,
        onlyIfNotVisible = true,
    )

    val covers = remember(state.tracks) { state.tracks.mapNotNull { it.coverArt }.distinct().take(4) }
    val coverStore = remember(context) { CollectionCoverStore(context.applicationContext) }
    val localFixedPath = remember(state.fixedCoverKind, state.fixedCoverValue) {
        if (state.fixedCoverKind == CollectionCoverKind.LOCAL) {
            coverStore.absolutePath(state.fixedCoverValue.orEmpty())
        } else {
            null
        }
    }
    val hasUsableFixed = CollectionCoverResolver.isUsableFixed(
        state.fixedCoverKind,
        state.fixedCoverValue,
        localFixedPath,
    )
    val resolvedCover = rememberResolvedCollectionCover(
        name = mixName,
        fixedCoverKind = state.fixedCoverKind,
        fixedCoverValue = state.fixedCoverValue,
        derivedCoverArtIds = covers,
        primaryArtist = state.tracks.firstOrNull()?.artist,
        primaryAlbum = null,
        decodeSizePx = 200,
        subject = CollectionCoverSubject.MIX,
        dominantGenre = dominantGenre,
        scope = "mix|$mixId",
    )
    val fallbackService = remember { CoverArtFallbackService.getInstance(context) }
    val montageActive = !hasUsableFixed && covers.isNotEmpty() &&
        resolvedCover.resolved is CollectionCoverResolver.Resolved.Derived
    // Match GenreMixCoverImage display rules: 1 tile / 2 tiles / 4 tiles.
    val displayedTileCount = when {
        !montageActive -> 0
        covers.size >= 4 -> 4
        covers.size >= 2 -> 2
        else -> 1
    }
    val displayedCoverIds = remember(covers, displayedTileCount) {
        covers.take(displayedTileCount)
    }
    val tile0 = if (displayedTileCount >= 1) {
        rememberPreferredCoverArt(
            coverArtId = displayedCoverIds.getOrNull(0),
            artist = null,
            album = null,
            size = 112,
            fallbackService = fallbackService,
        )
    } else {
        null
    }
    val tile1 = if (displayedTileCount >= 2) {
        rememberPreferredCoverArt(
            coverArtId = displayedCoverIds.getOrNull(1),
            artist = null,
            album = null,
            size = 112,
            fallbackService = fallbackService,
        )
    } else {
        null
    }
    val tile2 = if (displayedTileCount >= 3) {
        rememberPreferredCoverArt(
            coverArtId = displayedCoverIds.getOrNull(2),
            artist = null,
            album = null,
            size = 112,
            fallbackService = fallbackService,
        )
    } else {
        null
    }
    val tile3 = if (displayedTileCount >= 4) {
        rememberPreferredCoverArt(
            coverArtId = displayedCoverIds.getOrNull(3),
            artist = null,
            album = null,
            size = 112,
            fallbackService = fallbackService,
        )
    } else {
        null
    }
    // Only extract once every displayed tile URL is ready — never cache a partial montage.
    val tileModels = remember(tile0, tile1, tile2, tile3, displayedTileCount) {
        val ready = listOfNotNull(tile0, tile1, tile2, tile3)
        if (displayedTileCount > 0 && ready.size == displayedTileCount) ready else emptyList()
    }
    val montageCover = remember(resolvedCover, displayedCoverIds, displayedTileCount) {
        if (displayedTileCount <= 1) {
            resolvedCover
        } else {
            resolvedCover.copy(
                artKey = "mix|$mixId|montage|${displayedCoverIds.joinToString(",")}",
                sourceVersion = displayedCoverIds.joinToString("|"),
                montageCoverArtIds = displayedCoverIds,
            )
        }
    }
    val serverScope = com.lucasdss.ftpmusic.app.di.ServerConfigState.value.url.ifBlank { "default" }
    val coverScheme = rememberCoverScheme(
        cover = montageCover,
        serverScope = serverScope,
        tileImageModels = if (displayedTileCount > 1) tileModels else emptyList(),
    )
    ArtBackdrop(scheme = coverScheme) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailBackButton(onBack = leaveAndBack, inset = false)
                FittingText(
                    text = mixName,
                    color = coverScheme.onBg,
                    fontSize = textHeadingL(),
                    minFontSize = textMicro(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            if (updateAvailable) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(cornerM()))
                        .background(Color(0xFF1A2330))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "New mix ready",
                        color = Color.White,
                        fontSize = textBodyM(),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { viewModel.ignorePendingUpdate() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFAAAAAA)),
                    ) { Text("Ignore") }
                    TextButton(
                        onClick = { viewModel.adoptPendingUpdate() },
                        colors = ButtonDefaults.textButtonColors(contentColor = BrandTeal),
                    ) { Text("Update") }
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandTeal)
                }
            } else if (state.tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: "No tracks cached for $mixName",
                            color = if (state.error != null) Color(0xFFFF6B6B) else Color(0xFF888888),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        // The empty state MUST offer the refresh action — the ⋮
                        // sheet (the other refresh entry point) only renders when
                        // tracks exist, so without this button the screen is a
                        // dead-end ("tap refresh" with nothing to tap).
                        if (onRefresh != null) {
                            Spacer(Modifier.height(20.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(cornerM()))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(BrandTeal, BrandPurple),
                                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                            end = androidx.compose.ui.geometry.Offset(
                                                Float.POSITIVE_INFINITY,
                                                Float.POSITIVE_INFINITY,
                                            ),
                                        ),
                                    )
                                    .clickable { onRefresh?.invoke() }
                                    .padding(horizontal = 28.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(knobSize()),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Refresh Mix",
                                        color = Color.White,
                                        fontSize = textBodyM(),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    // Header: art + play/shuffle
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Fixed cover (usable), else montage (1–4 tiles), else lettermark.
                            Box(
                                Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1E1E3E))
                                    .combinedClickable(
                                        onClick = { coverEdit.openSheet() },
                                        onLongClick = { coverEdit.openSheet() },
                                    )
                                    .semantics { testTag = "mix_detail_cover_art" },
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    // Pack/lettermark path must match ArtBackdrop (skipDerived MIX).
                                    // Without montageActive, covers.size collage diverged from backdrop.
                                    hasUsableFixed || covers.isEmpty() || !montageActive -> {
                                        CollectionCoverArt(
                                            name = mixName,
                                            fixedCoverKind = state.fixedCoverKind,
                                            fixedCoverValue = state.fixedCoverValue,
                                            derivedCoverArtIds = covers,
                                            primaryArtist = state.tracks.firstOrNull()?.artist,
                                            primaryAlbum = null,
                                            decodeSize = 200.dp,
                                            lettermarkLarge = true,
                                            subject = CollectionCoverSubject.MIX,
                                            dominantGenre = dominantGenre,
                                            contentDescription = "Mix cover. Long-press to edit.",
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }

                                    covers.size >= 4 -> {
                                        Column(Modifier.fillMaxSize()) {
                                            Row(Modifier.weight(1f)) {
                                                GenreMixCoverImage(covers[0], Modifier.weight(1f).fillMaxHeight())
                                                GenreMixCoverImage(covers[1], Modifier.weight(1f).fillMaxHeight())
                                            }
                                            Row(Modifier.weight(1f)) {
                                                GenreMixCoverImage(covers[2], Modifier.weight(1f).fillMaxHeight())
                                                GenreMixCoverImage(covers[3], Modifier.weight(1f).fillMaxHeight())
                                            }
                                        }
                                    }

                                    covers.size >= 2 -> {
                                        Row(Modifier.fillMaxSize()) {
                                            GenreMixCoverImage(covers[0], Modifier.weight(1f).fillMaxHeight())
                                            GenreMixCoverImage(covers[1], Modifier.weight(1f).fillMaxHeight())
                                        }
                                    }

                                    else -> {
                                        GenreMixCoverImage(covers[0], Modifier.fillMaxSize())
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            // Track count (parity with Album)
                            Text(
                                "${state.tracks.size} tracks",
                                color = coverScheme.onBgSecondary,
                                fontSize = textLabelM(),
                            )
                            Spacer(Modifier.height(16.dp))
                            // Play + Shuffle + ⋮ buttons — consistent Box pattern (Album reference)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    Modifier.weight(1f).height(adp(42f)).clip(RoundedCornerShape(cornerM()))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(BrandTeal, BrandPurple),
                                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                                end = androidx.compose.ui.geometry.Offset(
                                                    Float.POSITIVE_INFINITY,
                                                    Float.POSITIVE_INFINITY,
                                                ),
                                            ),
                                        )
                                        .clickable { viewModel.playAll() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier.size(knobSize()),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Play",
                                            color = Color.White,
                                            fontSize = textBodyM(),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                // Shuffle — dark with gradient border (consistent across screens)
                                Box(
                                    Modifier.weight(1f).height(adp(42f)).clip(RoundedCornerShape(cornerM()))
                                        .background(Color(0xFF252538))
                                        .border(
                                            1.dp,
                                            Brush.horizontalGradient(listOf(BrandTeal, BrandPurple)),
                                            RoundedCornerShape(cornerM()),
                                        )
                                        .clickable { viewModel.shuffle() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Shuffle,
                                            null,
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
                                // ⋮ button — mix action sheet (parity with Album)
                                Box(
                                    Modifier.size(40.dp).clip(RoundedCornerShape(cornerM()))
                                        .background(Color(0xFF252538))
                                        .clickable { showMixSheet = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        null,
                                        tint = Color(0xFFAAAAAA),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }

                    // Track list — SongListRow (fixed title size, meta below)
                    itemsIndexed(
                        state.tracks,
                        key = { _, track -> track.id },
                        contentType = { _, _ -> "song" },
                    ) { index, track ->
                        val isActive = currentTrackId != null && track.id == currentTrackId && isPlaying
                        val isDownloaded = viewModel.isDownloaded(track.id)
                        val ds = com.lucasdss.ftpmusic.app.ui.components.downloadStatus(
                            isDownloaded = isDownloaded,
                            isQueued = false,
                            isCached = isDownloaded,
                        )
                        val isLiked = track.id in state.likedTrackIds
                        val isDisliked = track.id in state.dislikedTrackIds
                        SongListRow(
                            title = track.title,
                            subtitle = track.artist,
                            isActive = isActive,
                            downloadStatus = ds,
                            durationLabel = track.duration?.let { formatSongDuration(it) },
                            isLiked = isLiked,
                            isDisliked = isDisliked,
                            onLike = { viewModel.toggleTrackLike(track.id) },
                            onDislike = { viewModel.toggleTrackDislike(track.id) },
                            onMore = { showTrackSheet = track },
                            onClick = { viewModel.playTrack(index) },
                            onLongClick = { showTrackSheet = track },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            leadingContent = when {
                                isActive -> {
                                    {
                                        Box(
                                            Modifier.widthIn(min = 16.dp, max = 28.dp),
                                            contentAlignment = Alignment.Center,
                                        ) { AnimatedEqBarsMix() }
                                    }
                                }

                                LocalListChromePrefs.current.showListSequence -> {
                                    {
                                        Box(
                                            Modifier.widthIn(min = 16.dp, max = 28.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "${index + 1}",
                                                color = Color(0xFF666666),
                                                fontSize = textLabelM(),
                                            )
                                        }
                                    }
                                }

                                else -> null
                            },
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.04f),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            // ═══ Overwrite Protection (Ask mode) ═══
            val showOverwrite by viewModel.showOverwriteModal.collectAsStateWithLifecycle()
            if (showOverwrite) {
                android.app.AlertDialog.Builder(context).apply {
                    setTitle("Tracks in your queue")
                    setMessage(
                        "You have tracks in your Priority Queue. Do you want to clear them and play this mix, or keep them?",
                    )
                    setNegativeButton("Keep Queue") { _, _ -> viewModel.resolveOverwrite(false) }
                    setPositiveButton("Clear & Play") { _, _ -> viewModel.resolveOverwrite(true) }
                    setOnCancelListener { viewModel.resolveOverwrite(false) }
                    show()
                }
            }

            // ═══ Mix Action Sheet ═══
            if (showMixSheet) {
                MixActionSheet(
                    trackCount = state.tracks.size,
                    onPlayNextAll = {
                        viewModel.playNextAll()
                        android.widget.Toast.makeText(context, "Playing next", android.widget.Toast.LENGTH_SHORT).show()
                        showMixSheet = false
                    },
                    onAddAllToQueue = {
                        viewModel.addAllToQueue()
                        android.widget.Toast.makeText(
                            context,
                            "${state.tracks.size} tracks added to queue",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        showMixSheet = false
                    },
                    onDownloadAll = {
                        viewModel.downloadAll()
                        showMixSheet = false
                    },
                    onRefresh = {
                        onRefresh?.invoke()
                        showMixSheet = false
                    },
                    onDismiss = { showMixSheet = false },
                )
            }

            // ═══ Track Action Sheet ═══
            showTrackSheet?.let { track ->
                MixTrackActionSheet(
                    track = track,
                    onAddToQueue = {
                        val t = Track(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            albumId = track.albumId,
                            duration = track.duration,
                            trackNumber = track.trackNumber,
                            coverArt = track.coverArt,
                        )
                        viewModel.addToQueueTrack(t, viewModel.buildStreamUrl(t.id))
                        android.widget.Toast.makeText(
                            context,
                            "Added to queue",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        showTrackSheet = null
                    },
                    onPlayNext = {
                        val t = Track(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            albumId = track.albumId,
                            duration = track.duration,
                            trackNumber = track.trackNumber,
                            coverArt = track.coverArt,
                        )
                        viewModel.playNextTrack(t, viewModel.buildStreamUrl(t.id))
                        android.widget.Toast.makeText(context, "Playing next", android.widget.Toast.LENGTH_SHORT).show()
                        showTrackSheet = null
                    },
                    onDownload = {
                        val t = Track(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            albumId = track.albumId,
                            duration = track.duration,
                            trackNumber = track.trackNumber,
                            coverArt = track.coverArt,
                        )
                        viewModel.downloadTrack(t)
                        showTrackSheet = null
                    },
                    onDismiss = { showTrackSheet = null },
                )
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandTeal)
                }
            }
        }
    }
}

@HiltViewModel
class MixDetailViewModel @Inject constructor(
    private val genreMixDao: GenreMixDao,
    private val dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository,
    private val trackDao: com.lucasdss.ftpmusic.app.data.db.TrackDao,
    private val authHelper: SubsonicAuthHelper,
    private val storage: com.lucasdss.ftpmusic.app.data.security.SecureStorage,
    private val playbackManager: com.lucasdss.ftpmusic.app.playback.PlaybackManager,
    private val cacheService: com.lucasdss.ftpmusic.app.data.cache.CacheService,
    private val downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager,
    private val favoriteRepository: com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository,
    private val api: com.lucasdss.ftpmusic.app.data.network.SubsonicApi,
) : androidx.lifecycle.ViewModel() {

    data class State(
        val tracks: List<GenreMixTrack> = emptyList(),
        val likedTrackIds: Set<String> = emptySet(),
        val dislikedTrackIds: Set<String> = emptySet(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val fixedCoverKind: String? = null,
        val fixedCoverValue: String? = null,
        val recipeGenres: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    /** DM-16: row id bound at open; UI stays on this until Update / leave. */
    private var sessionDailyMixId: Int? = null
    private var ignoredPendingForSession: Boolean = false
    private var leaveActivated: Boolean = false

    fun clearToast() {
        _toast.value = null
    }

    fun setFixedCoverFromLibrary(coverArtId: String) {
        val id = loadedMixId ?: return
        viewModelScope.launch {
            dailyMixRepository.setFixedCover(id, CollectionCoverKind.NAVIDROME, coverArtId)
            _state.value = _state.value.copy(
                fixedCoverKind = CollectionCoverKind.NAVIDROME,
                fixedCoverValue = coverArtId,
            )
        }
    }

    fun setFixedCoverFromCroppedBitmap(bitmap: Bitmap) {
        val id = loadedMixId ?: run {
            bitmap.recycle()
            return
        }
        viewModelScope.launch {
            val relative = dailyMixRepository.setFixedCoverFromCroppedBitmap(id, bitmap)
            if (relative == null) {
                _toast.value = "Could not import image"
                return@launch
            }
            _state.value = _state.value.copy(
                fixedCoverKind = CollectionCoverKind.LOCAL,
                fixedCoverValue = relative,
            )
        }
    }

    fun setFixedCoverFromSaved(relativePath: String) {
        val id = loadedMixId ?: return
        if (relativePath.isBlank()) return
        viewModelScope.launch {
            dailyMixRepository.setFixedCover(id, CollectionCoverKind.LOCAL, relativePath)
            _state.value = _state.value.copy(
                fixedCoverKind = CollectionCoverKind.LOCAL,
                fixedCoverValue = relativePath,
            )
        }
    }

    fun setFixedCoverFromBundled(assetPath: String) {
        val id = loadedMixId ?: return
        if (assetPath.isBlank()) return
        viewModelScope.launch {
            dailyMixRepository.setFixedCover(id, CollectionCoverKind.BUNDLED, assetPath)
            _state.value = _state.value.copy(
                fixedCoverKind = CollectionCoverKind.BUNDLED,
                fixedCoverValue = assetPath,
            )
        }
    }

    fun setFixedCoverFromDerived() {
        val id = loadedMixId ?: return
        viewModelScope.launch {
            dailyMixRepository.setFixedCover(id, CollectionCoverKind.DERIVED, "montage")
            _state.value = _state.value.copy(
                fixedCoverKind = CollectionCoverKind.DERIVED,
                fixedCoverValue = "montage",
            )
        }
    }

    fun clearFixedCover() {
        val id = loadedMixId ?: return
        viewModelScope.launch {
            dailyMixRepository.clearFixedCover(id)
            _state.value = _state.value.copy(fixedCoverKind = null, fixedCoverValue = null)
        }
    }

    fun notifyCoverCropLoadFailed() {
        _toast.value = "Could not load image for crop"
    }

    fun notifyCoverCropExportFailed() {
        _toast.value = "Could not save cropped cover"
    }

    private val isRefreshing = java.util.concurrent.atomic.AtomicBoolean(false)

    /** One auto-generation attempt per mix open — prevents regeneration
     *  loops when a mix source yields no tracklist (refreshMix re-enters loadMix). */
    private val autoGenAttempted = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Mix id the auto-generation attempt belongs to (VM reuse across ids). */
    private var autoGenMixId: Long? = null

    /** Currently loaded mix id — used for queue journal sourceId (ADR-0053). */
    private var loadedMixId: Long? = null

    /** Display name of the loaded mix (title + playback context source name). */
    private val _mixName = kotlinx.coroutines.flow.MutableStateFlow("Daily Mix")
    val mixName: kotlinx.coroutines.flow.StateFlow<String> = _mixName.asStateFlow()

    /** Convert the internal GenreMixTrack list to PlaybackManager Track objects. */
    private fun toPlayableTracks(): List<Track> = _state.value.tracks.map { t ->
        Track(
            id = t.id,
            title = t.title,
            artist = t.artist,
            albumId = t.albumId,
            duration = t.duration,
            trackNumber = t.trackNumber,
            coverArt = t.coverArt,
        )
    }

    /** Convert to Tracks + their stream URLs. */
    private fun toPlayable(): Pair<List<Track>, List<String>> {
        val tracks = toPlayableTracks()
        return Pair(tracks, tracks.map { buildStreamUrl(it.id) })
    }

    /** Full mix CONTEXT + jump to tapped index; PRIORITY kept (ADR 0061). */
    fun playTrack(index: Int) {
        val (tracks, urls) = toPlayable()
        if (index < 0 || index >= tracks.size) return
        val mixId = loadedMixId?.toString() ?: autoGenMixId?.toString() ?: "unknown"
        // Journal-only source: a track tap must not clear the Priority queue (CP-12).
        playbackManager.playAlbum(
            tracks,
            urls,
            startIndex = index,
            journalSource = com.lucasdss.ftpmusic.app.playback.PlaybackManager.JournalSource(
                type = "genremix",
                id = mixId,
                name = _mixName.value,
            ),
        )
    }

    fun playAll() {
        val (tracks, urls) = toPlayable()
        if (tracks.isEmpty()) return
        val mixId = loadedMixId?.toString() ?: autoGenMixId?.toString() ?: "unknown"
        val started = playbackManager.tryStartContext(
            tracks,
            urls,
            sourceType = "genremix",
            sourceId = mixId,
            sourceName = _mixName.value,
        )
        if (!started) _showOverwriteModal.value = true
    }

    private val _showOverwriteModal = kotlinx.coroutines.flow.MutableStateFlow(false)
    val showOverwriteModal: kotlinx.coroutines.flow.StateFlow<Boolean> = _showOverwriteModal

    fun resolveOverwrite(clearAndPlay: Boolean) {
        _showOverwriteModal.value = false
        playbackManager.resolveOverwrite(clearAndPlay)
    }

    fun shuffle() {
        val (tracks, urls) = toPlayable()
        if (tracks.isEmpty()) return
        val mixId = loadedMixId?.toString() ?: autoGenMixId?.toString() ?: "unknown"
        val started = playbackManager.tryShuffleContext(
            tracks,
            urls,
            sourceType = "genremix",
            sourceId = mixId,
            sourceName = _mixName.value,
        )
        if (!started) _showOverwriteModal.value = true
    }

    /** Insert all mix tracks at the front of the Priority Queue (Play Next). */
    fun playNextAll() {
        val (tracks, urls) = toPlayable()
        if (tracks.isEmpty()) return
        tracks.zip(urls).reversed().forEach { (track, url) ->
            playbackManager.playNext(track, url)
        }
    }

    /** Append all mix tracks to the Priority Queue (Add to Queue). */
    fun addAllToQueue() {
        val (tracks, urls) = toPlayable()
        if (tracks.isEmpty()) return
        tracks.zip(urls).forEach { (track, url) ->
            playbackManager.addToQueue(track, url)
        }
    }

    /** Insert a single mix track at the front of the Priority Queue. */
    fun playNextTrack(track: Track, url: String) {
        playbackManager.playNext(track, url)
    }

    /** Append a single mix track to the Priority Queue. */
    fun addToQueueTrack(track: Track, url: String) {
        playbackManager.addToQueue(track, url)
    }

    /** Download a single mix track (priority=1 → permanent). */
    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            try {
                if (cacheService.promoteToDownload(track.id)) return@launch
                downloadManager.enqueue(track.id, buildStreamUrl(track.id), priority = 1)
            } catch (_: Exception) {}
        }
    }

    /** Download all mix tracks. */
    fun downloadAll() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            tracks.forEach { t ->
                try {
                    val track = Track(
                        id = t.id,
                        title = t.title,
                        artist = t.artist,
                        albumId = t.albumId,
                        duration = t.duration,
                        trackNumber = t.trackNumber,
                        coverArt = t.coverArt,
                    )
                    if (cacheService.promoteToDownload(t.id)) {
                        if (!_downloadedTrackIds.contains(t.id)) _downloadedTrackIds.add(t.id)
                    } else {
                        downloadManager.enqueue(t.id, buildStreamUrl(t.id), priority = 1)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // ── Per-track state (parity with Album track rows) ─────────────────

    private val _downloadedTrackIds = androidx.compose.runtime.mutableStateListOf<String>()
    val downloadedTrackIds: List<String> get() = _downloadedTrackIds.toList()

    private val _trackRatings = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Int>>(emptyMap())
    val trackRatings: kotlinx.coroutines.flow.StateFlow<Map<String, Int>> = _trackRatings

    fun isTrackLiked(trackId: String): Boolean = _state.value.likedTrackIds.contains(trackId)
    fun isTrackDisliked(trackId: String): Boolean = _state.value.dislikedTrackIds.contains(trackId)
    fun getTrackRating(trackId: String): Int = _trackRatings.value[trackId] ?: 0
    fun isDownloaded(trackId: String): Boolean = _downloadedTrackIds.contains(trackId)

    fun toggleTrackLike(trackId: String) {
        val previousLiked = _state.value.likedTrackIds
        val previousDisliked = _state.value.dislikedTrackIds
        val isLiked = trackId in previousLiked
        // Optimistic before await so Compose collecting `state` updates immediately.
        if (isLiked) {
            _state.value = _state.value.copy(likedTrackIds = previousLiked - trackId)
        } else {
            _state.value = _state.value.copy(
                likedTrackIds = previousLiked + trackId,
                dislikedTrackIds = previousDisliked - trackId,
            )
        }
        viewModelScope.launch {
            try {
                if (isLiked) {
                    favoriteRepository.unlikeTrack(trackId)
                } else {
                    favoriteRepository.likeTrack(trackId)
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    likedTrackIds = previousLiked,
                    dislikedTrackIds = previousDisliked,
                )
            }
        }
    }

    /** Toggle thumbs-down (local dislike). Mutual exclusion: disliking clears like (star). */
    fun toggleTrackDislike(trackId: String) {
        val previousLiked = _state.value.likedTrackIds
        val previousDisliked = _state.value.dislikedTrackIds
        val isDisliked = trackId in previousDisliked
        if (isDisliked) {
            _state.value = _state.value.copy(dislikedTrackIds = previousDisliked - trackId)
        } else {
            _state.value = _state.value.copy(
                dislikedTrackIds = previousDisliked + trackId,
                likedTrackIds = previousLiked - trackId,
            )
        }
        viewModelScope.launch {
            try {
                if (isDisliked) {
                    favoriteRepository.clearDislikeTrack(trackId)
                } else {
                    favoriteRepository.dislikeTrack(trackId)
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    likedTrackIds = previousLiked,
                    dislikedTrackIds = previousDisliked,
                )
            }
        }
    }

    fun rateTrack(trackId: String, rating: Int) {
        val clamped = rating.coerceIn(0, 5)
        _trackRatings.value = _trackRatings.value + (trackId to clamped)
        viewModelScope.launch {
            try {
                favoriteRepository.rateTrack(trackId, clamped)
            } catch (_: Exception) {}
        }
    }

    fun loadMix(mixId: Long) {
        loadedMixId = mixId
        leaveActivated = false
        ignoredPendingForSession = false
        if (autoGenMixId != mixId) {
            autoGenMixId = mixId
            autoGenAttempted.set(false)
        }
        viewModelScope.launch {
            try {
                val recipe = dailyMixRepository.getMix(mixId)
                recipe?.let { _mixName.value = it.name }
                val fixedKind = recipe?.fixedCoverKind
                val fixedValue = recipe?.fixedCoverValue
                val recipeGenres = recipe?.filters?.genres.orEmpty()
                // DM-16: bind session to active pointer (not today|yesterday).
                val dailyMix = dailyMixRepository.getActiveDailyMix(mixId)
                sessionDailyMixId = dailyMix?.id
                val trackIds: List<String> = if (dailyMix != null) {
                    genreMixDao.getDailyMixTrackIds(dailyMix.id)
                } else {
                    emptyList()
                }
                refreshUpdateBanner(mixId)

                if (trackIds.isNotEmpty()) {
                    com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
                        "ftpmusic-dailymix",
                        "loadMix hit mixId=$mixId activeId=${dailyMix?.id} v=${dailyMix?.version} count=${trackIds.size}",
                    )
                    applyTracks(trackIds, fixedKind, fixedValue, recipeGenres)
                    return@launch
                }

                // R2 / DeepSeek B1: auto build+activate only when there is no
                // active pointer. An empty active list (all filtered/stale) must
                // not silently force-activate a new version on open.
                if (dailyMix == null && autoGenAttempted.compareAndSet(false, true)) {
                    refreshMix(mixId)
                    return@launch
                }

                _state.value = State(
                    isLoading = false,
                    error = if (dailyMix != null) {
                        "No playable tracks — tap refresh"
                    } else {
                        "No mix generated yet — tap refresh"
                    },
                    fixedCoverKind = fixedKind,
                    fixedCoverValue = fixedValue,
                    recipeGenres = recipeGenres,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-dailymix", "[loadMix] $mixId failed: ${e.message}")
                com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
                    "ftpmusic-dailymix",
                    "loadMix fail mixId=$mixId",
                    e,
                )
                _state.value = State(isLoading = false)
            }
        }
    }

    /** Force build + activate (Update / PTR / refresh). Does not touch Media queue. */
    fun refreshMix(mixId: Long) {
        if (!isRefreshing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val generated = dailyMixRepository.generateOne(mixId, manual = true, activate = true)
                if (generated.isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "No tracks for this mix yet. Try Resync Library in Settings.",
                    )
                    return@launch
                }
                ignoredPendingForSession = false
                _updateAvailable.value = false
                loadMix(mixId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(isLoading = false, error = "Failed to refresh mix")
            } finally {
                isRefreshing.set(false)
            }
        }
    }

    /** Adopt pending version into the session UI (banner Update). */
    fun adoptPendingUpdate() {
        val mixId = loadedMixId ?: return
        viewModelScope.launch {
            val ok = dailyMixRepository.activatePending(mixId)
            if (ok) {
                ignoredPendingForSession = false
                _updateAvailable.value = false
                loadMix(mixId)
            } else {
                refreshMix(mixId)
            }
        }
    }

    /** Keep session list for this visit; leave will still activate pending (R7). */
    fun ignorePendingUpdate() {
        ignoredPendingForSession = true
        _updateAvailable.value = false
    }

    /**
     * Nav-pop / dispose: activate pending so re-enter shows the new list.
     * Idempotent; skips config-change double-fire via [leaveActivated].
     * Uses process-scoped [DailyMixRepository.activatePendingDetached] so the
     * Room transaction is not cancelled when this ViewModel is cleared on pop.
     */
    fun onLeaveActivatePending() {
        val mixId = loadedMixId ?: return
        if (leaveActivated) return
        leaveActivated = true
        dailyMixRepository.activatePendingDetached(mixId)
    }

    private suspend fun refreshUpdateBanner(mixId: Long) {
        if (ignoredPendingForSession) {
            _updateAvailable.value = false
            return
        }
        val pending = dailyMixRepository.getPendingDailyMix(mixId)
        val sessionId = sessionDailyMixId
        _updateAvailable.value = pending != null && pending.id != sessionId
    }

    private suspend fun applyTracks(
        trackIds: List<String>,
        fixedKind: String?,
        fixedValue: String?,
        recipeGenres: List<String> = emptyList(),
    ) {
        val playableIds = dailyMixRepository.filterPlayableMixTrackIds(trackIds)
        val allTracks = if (playableIds.isEmpty()) {
            emptyList()
        } else {
            trackDao.getTracksByIds(playableIds)
        }
        val liked = allTracks.filter { it.starredAt != null }.map { it.id }.toSet()
        val disliked = allTracks.filter { it.isDisliked }.map { it.id }.toSet()
        val trackMap = allTracks.associateBy { it.id }
        val displayTracks = playableIds.mapNotNull { id ->
            trackMap[id]?.let { t ->
                GenreMixTrack(
                    t.id,
                    t.title,
                    t.artist,
                    t.albumId,
                    t.durationSeconds,
                    t.trackNumber,
                    t.coverArtUrl,
                )
            }
        }
        _state.value = State(
            tracks = displayTracks,
            likedTrackIds = liked,
            dislikedTrackIds = disliked,
            isLoading = false,
            fixedCoverKind = fixedKind,
            fixedCoverValue = fixedValue,
            recipeGenres = recipeGenres,
        )
    }

    fun buildStreamUrl(trackId: String): String {
        val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        val username = storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_USERNAME) ?: ""
        val password = storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_PASSWORD) ?: ""
        return authHelper.buildStreamUrl(base, trackId, username, password)
    }
}

// ─── Mix Action Sheet ───
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MixActionSheet(
    trackCount: Int,
    onPlayNextAll: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onDownloadAll: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(bottom = spacing3XL())) {
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mix · $trackCount tracks", color = Color(0xFF888888), fontSize = textLabelM())
            }
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(horizontal = spacingL()),
            )
            MixSheetAction(
                "Play Next",
                "Insert $trackCount tracks after current",
                Icons.Default.ArrowUpward,
                BrandTeal,
                onPlayNextAll,
            )
            MixSheetAction(
                "Add to Queue",
                "Append $trackCount tracks",
                Icons.AutoMirrored.Filled.QueueMusic,
                Color(0xFF5B8DEE),
                onAddAllToQueue,
            )
            MixSheetAction(
                "Download All",
                "Save all tracks offline",
                Icons.Default.Download,
                Color(0xFF888888),
                onDownloadAll,
            )
            MixSheetAction("Refresh Mix", "Regenerate this mix", Icons.Default.Refresh, BrandTeal, onRefresh)
        }
    }
}

// ─── Mix Track Action Sheet ───
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MixTrackActionSheet(
    track: GenreMixTrack,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(bottom = spacing3XL())) {
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    FittingText(
                        text = track.title,
                        color = Color.White,
                        fontSize = textHeadingS(),
                        minFontSize = textMicro(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    track.artist?.let {
                        FittingText(
                            text = it,
                            color = Color(0xFF888888),
                            fontSize = textLabelM(),
                            minFontSize = textMicro(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(horizontal = spacingL()),
            )
            MixSheetAction(
                "Play Next",
                "Insert at top of Priority Queue",
                Icons.Default.ArrowUpward,
                BrandTeal,
                onPlayNext,
            )
            MixSheetAction(
                "Add to Queue",
                "Append to Priority Queue",
                Icons.AutoMirrored.Filled.QueueMusic,
                Color(0xFF5B8DEE),
                onAddToQueue,
            )
            MixSheetAction(
                "Download",
                "Save for offline playback",
                Icons.Default.Download,
                Color(0xFF888888),
                onDownload,
            )
        }
    }
}

@Composable
private fun MixSheetAction(
    label: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = spacingL(), vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FittingText(
                text = label,
                color = Color.White,
                fontSize = textBodyM(),
                minFontSize = textMicro(),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
            FittingText(
                text = subtitle,
                color = Color(0xFF888888),
                fontSize = textLabelM(),
                minFontSize = textMicro(),
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = spacingL()))
}

// ─── Helpers for Album-style track rows ───

/** Animated EQ bars for the active track (parity with Album detail). */
@Composable
private fun AnimatedEqBarsMix() {
    val transition = rememberInfiniteTransition(label = "eqMix")
    val bars = listOf(
        transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            "b1",
        ),
        transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(400, easing = FastOutSlowInEasing, delayMillis = 100), RepeatMode.Reverse),
            "b2",
        ),
        transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(550, easing = FastOutSlowInEasing, delayMillis = 200), RepeatMode.Reverse),
            "b3",
        ),
    )
    Row(
        Modifier.width(12.dp).height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { anim ->
            Box(
                Modifier.weight(1f).fillMaxHeight(fraction = 0.35f + anim.value * 0.65f)
                    .clip(RoundedCornerShape(1.dp)).background(BrandTeal),
            )
        }
    }
}
