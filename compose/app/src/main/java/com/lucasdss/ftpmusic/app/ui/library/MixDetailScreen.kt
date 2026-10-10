package com.lucasdss.ftpmusic.app.ui.library

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
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.BrandPurple
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.components.CollectionCoverArt
import com.lucasdss.ftpmusic.app.ui.components.DetailBackButton
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import com.lucasdss.ftpmusic.app.ui.components.SongListRow
import com.lucasdss.ftpmusic.app.ui.components.formatSongDuration
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
    var showMixSheet by remember { mutableStateOf(false) }
    var showTrackSheet by remember { mutableStateOf<GenreMixTrack?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D14))) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailBackButton(onBack = onBack, inset = false)
                FittingText(
                    text = mixName,
                    color = Color.White,
                    fontSize = textHeadingL(),
                    minFontSize = textMicro(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
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
                    contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    // Header: art + play/shuffle
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Fixed cover (usable), else montage (1–4 tiles), else lettermark.
                            val covers = state.tracks.mapNotNull { it.coverArt }.distinct().take(4)
                            val context = LocalContext.current
                            val coverStore = remember(context) {
                                CollectionCoverStore(context.applicationContext)
                            }
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
                            Box(
                                Modifier.size(200.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1E1E3E)),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    hasUsableFixed || covers.isEmpty() -> {
                                        CollectionCoverArt(
                                            name = mixName,
                                            fixedCoverKind = state.fixedCoverKind,
                                            fixedCoverValue = state.fixedCoverValue,
                                            derivedCoverArtIds = covers,
                                            primaryArtist = state.tracks.firstOrNull()?.artist,
                                            primaryAlbum = null,
                                            decodeSize = 200.dp,
                                            lettermarkLarge = true,
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
                                color = Color(0xFF888888),
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
                            leadingContent = {
                                Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                                    if (isActive) {
                                        AnimatedEqBarsMix()
                                    } else {
                                        Text(
                                            "${track.trackNumber ?: index + 1}",
                                            color = Color(0xFF666666),
                                            fontSize = textBodyM(),
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
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
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

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
        playbackManager.playAlbum(tracks, urls, startIndex = index)
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
        if (autoGenMixId != mixId) {
            autoGenMixId = mixId
            autoGenAttempted.set(false)
        }
        viewModelScope.launch {
            try {
                val today = java.time.LocalDate.now().toString()
                val yesterday = java.time.LocalDate.now().minusDays(1).toString()
                val recipe = dailyMixRepository.getMix(mixId)
                recipe?.let { _mixName.value = it.name }
                val fixedKind = recipe?.fixedCoverKind
                val fixedValue = recipe?.fixedCoverValue
                val dailyMix = genreMixDao.getDailyMix(today, mixId)
                    ?: genreMixDao.getDailyMix(yesterday, mixId)
                val trackIds: List<String> = if (dailyMix != null) {
                    genreMixDao.getDailyMixTrackIds(dailyMix.id)
                } else {
                    emptyList()
                }

                if (trackIds.isNotEmpty()) {
                    com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
                        "ftpmusic-dailymix",
                        "loadMix hit mixId=$mixId date=${dailyMix?.date} count=${trackIds.size}",
                    )
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
                    )
                    return@launch
                }

                // Guard against the "No mix generated yet" dead-end: auto-
                // generate once on open (generation is purely local — Room +
                // DailyMixGenerator, no network).
                if (autoGenAttempted.compareAndSet(false, true)) {
                    refreshMix(mixId)
                    return@launch
                }

                // No mix and nothing to generate from — show empty with a
                // working refresh action (Refresh button in the UI).
                _state.value = State(isLoading = false, error = "No mix generated yet — tap refresh")
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

    fun refreshMix(mixId: Long) {
        // Guard against concurrent refreshes
        if (!isRefreshing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val generated = dailyMixRepository.generateOne(mixId, manual = true)
                if (generated.isEmpty()) {
                    // Never persist an empty mix — an empty row would block
                    // regeneration for 48h (empty-mix deadlock).
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "No tracks for this mix yet. Try Resync Library in Settings.",
                    )
                    return@launch
                }
                loadMix(mixId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(isLoading = false, error = "Failed to refresh mix")
            } finally {
                isRefreshing.set(false)
            }
        }
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
