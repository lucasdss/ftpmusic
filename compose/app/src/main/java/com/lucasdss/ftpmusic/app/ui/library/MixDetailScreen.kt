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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import com.lucasdss.ftpmusic.app.data.db.CachedGenreSongEntity
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.ui.*
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
    onTrackClick: (Track, String) -> Unit = { _, _ -> },
    onPlayAll: (List<Track>, List<String>) -> Unit = { _, _ -> },
    onShuffle: (List<Track>, List<String>) -> Unit = { _, _ -> },
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
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    mixName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00C8B4))
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
                                            listOf(Color(0xFF00C8B4), Color(0xFFB040E8)),
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
                            // Cover art montage from first 4 unique covers
                            val covers = state.tracks.mapNotNull { it.coverArt }.distinct().take(4)
                            Box(
                                Modifier.size(200.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1E1E3E)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (covers.size >= 4) {
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
                                } else {
                                    Text(mixName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
                                                listOf(Color(0xFF00C8B4), Color(0xFFB040E8)),
                                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                                end = androidx.compose.ui.geometry.Offset(
                                                    Float.POSITIVE_INFINITY,
                                                    Float.POSITIVE_INFINITY,
                                                ),
                                            ),
                                        )
                                        .clickable {
                                            val tracks = state.tracks.map { t ->
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
                                            val urls = tracks.map { viewModel.buildStreamUrl(it.id) }
                                            onPlayAll(tracks, urls)
                                        },
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
                                            Brush.horizontalGradient(listOf(Color(0xFF00C8B4), Color(0xFFB040E8))),
                                            RoundedCornerShape(cornerM()),
                                        )
                                        .clickable {
                                            val tracks = state.tracks.map { t ->
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
                                            val urls = tracks.map { viewModel.buildStreamUrl(it.id) }
                                            onShuffle(tracks, urls)
                                        },
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

                    // Track list — Album-style rows
                    items(state.tracks) { track ->
                        val t = Track(
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            albumId = track.albumId,
                            duration = track.duration,
                            trackNumber = track.trackNumber,
                            coverArt = track.coverArt,
                        )
                        val isActive = currentTrackId != null && track.id == currentTrackId && isPlaying
                        val isDownloaded = viewModel.isDownloaded(track.id)
                        val ds = com.lucasdss.ftpmusic.app.ui.components.downloadStatus(
                            isDownloaded = isDownloaded,
                            isQueued = false,
                            isCached = isDownloaded,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Track number / EQ
                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                                if (isActive) {
                                    AnimatedEqBarsMix()
                                } else {
                                    Text(
                                        "${track.trackNumber ?: indexOfTrack(state.tracks, track) + 1}",
                                        color = Color(0xFF666666),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            // Info (tap to play, long-press for menu)
                            Column(
                                Modifier.weight(1f).combinedClickable(
                                    onClick = {
                                        onTrackClick(t, viewModel.buildStreamUrl(t.id))
                                    },
                                    onLongClick = { showTrackSheet = track },
                                ),
                            ) {
                                Text(
                                    track.title,
                                    color = if (isActive) Color(0xFF00C8B4) else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                track.artist?.let {
                                    Text(
                                        it,
                                        color = Color(0xFF888888),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                // Star rating (parity with Album)
                                Row {
                                    for (i in 1..5) {
                                        val r = viewModel.getTrackRating(track.id)
                                        Icon(
                                            if (i <= r) Icons.Default.Star else Icons.Default.StarBorder,
                                            null,
                                            tint = if (i <= r) Color(0xFF00C8B4) else Color(0xFF444444),
                                            modifier = Modifier.size(11.dp).clickable {
                                                viewModel.rateTrack(track.id, i)
                                            },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            // Download badge
                            com.lucasdss.ftpmusic.app.ui.components.DownloadDot(ds)
                            Spacer(Modifier.width(6.dp))
                            // ThumbsUp (like == star)
                            val isLiked = viewModel.isTrackLiked(track.id)
                            Icon(
                                if (isLiked) Icons.Filled.ThumbUp else Icons.Filled.ThumbUp,
                                contentDescription = if (isLiked) "Unlike" else "Like",
                                tint = if (isLiked) Color(0xFF00C8B4) else Color(0xFF444444),
                                modifier = Modifier.size(knobSize()).clickable { viewModel.toggleTrackLike(track.id) },
                            )
                            Spacer(Modifier.width(6.dp))
                            // ThumbsDown (dislike — local)
                            val isDisliked = viewModel.isTrackDisliked(track.id)
                            Icon(
                                if (isDisliked) Icons.Filled.ThumbDown else Icons.Filled.ThumbDown,
                                contentDescription = if (isDisliked) "Remove dislike" else "Dislike",
                                tint = if (isDisliked) Color(0xFFE84040) else Color(0xFF444444),
                                modifier = Modifier.size(knobSize()).clickable {
                                    viewModel.toggleTrackDislike(track.id)
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            // Duration
                            track.duration?.let { d ->
                                Text(
                                    "${d / 60}:${(d % 60).toString().padStart(2, '0')}",
                                    color = Color(0xFF888888),
                                    fontSize = 13.sp,
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            // ⋮ menu
                            Icon(
                                Icons.Default.MoreVert,
                                null,
                                tint = Color(0xFF444444),
                                modifier = Modifier.size(18.dp).clickable { showTrackSheet = track },
                            )
                        }
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
                    CircularProgressIndicator(color = Color(0xFF00C8B4))
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
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val isRefreshing = java.util.concurrent.atomic.AtomicBoolean(false)

    /** One auto-generation attempt per mix open — prevents regeneration
     *  loops when a mix source yields no tracklist (refreshMix re-enters loadMix). */
    private val autoGenAttempted = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Mix id the auto-generation attempt belongs to (VM reuse across ids). */
    private var autoGenMixId: Long? = null

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

    fun playAll() {
        val (tracks, urls) = toPlayable()
        if (tracks.isEmpty()) return
        val started = playbackManager.tryStartContext(
            tracks,
            urls,
            sourceType = "genremix",
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
        val started = playbackManager.tryShuffleContext(
            tracks,
            urls,
            sourceType = "genremix",
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

    private val _likedTrackIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds: kotlinx.coroutines.flow.StateFlow<Set<String>> = _likedTrackIds

    private val _dislikedTrackIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val dislikedTrackIds: kotlinx.coroutines.flow.StateFlow<Set<String>> = _dislikedTrackIds

    private val _trackRatings = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Int>>(emptyMap())
    val trackRatings: kotlinx.coroutines.flow.StateFlow<Map<String, Int>> = _trackRatings

    fun isTrackLiked(trackId: String): Boolean = _likedTrackIds.value.contains(trackId)
    fun isTrackDisliked(trackId: String): Boolean = _dislikedTrackIds.value.contains(trackId)
    fun getTrackRating(trackId: String): Int = _trackRatings.value[trackId] ?: 0
    fun isDownloaded(trackId: String): Boolean = _downloadedTrackIds.contains(trackId)

    fun toggleTrackLike(trackId: String) {
        val isLiked = _likedTrackIds.value.contains(trackId)
        viewModelScope.launch {
            try {
                if (isLiked) {
                    favoriteRepository.unlikeTrack(trackId)
                    _likedTrackIds.value = _likedTrackIds.value - trackId
                } else {
                    favoriteRepository.likeTrack(trackId)
                    _likedTrackIds.value = _likedTrackIds.value + trackId
                    _dislikedTrackIds.value = _dislikedTrackIds.value - trackId
                }
            } catch (_: Exception) {}
        }
    }

    /** Toggle thumbs-down (local dislike). Mutual exclusion: disliking clears like (star). */
    fun toggleTrackDislike(trackId: String) {
        val isDisliked = _dislikedTrackIds.value.contains(trackId)
        viewModelScope.launch {
            try {
                if (isDisliked) {
                    favoriteRepository.clearDislikeTrack(trackId)
                    _dislikedTrackIds.value = _dislikedTrackIds.value - trackId
                } else {
                    favoriteRepository.dislikeTrack(trackId)
                    _dislikedTrackIds.value = _dislikedTrackIds.value + trackId
                    _likedTrackIds.value = _likedTrackIds.value - trackId
                }
            } catch (_: Exception) {}
        }
    }

    fun rateTrack(trackId: String, rating: Int) {
        val clamped = rating.coerceIn(0, 5)
        _trackRatings.value = _trackRatings.value + (trackId to clamped)
        viewModelScope.launch {
            try {
                // Local-first, then best-effort server sync (parity with Now Playing)
                trackDao.setRating(trackId, clamped)
                val username = storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_USERNAME) ?: ""
                val password = storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_PASSWORD) ?: ""
                val params = authHelper.buildAuthParams(username, password)
                api.setRating(params = params, id = trackId, rating = clamped)
            } catch (_: Exception) {}
        }
    }

    fun loadMix(mixId: Long) {
        if (autoGenMixId != mixId) {
            autoGenMixId = mixId
            autoGenAttempted.set(false)
        }
        viewModelScope.launch {
            try {
                val today = java.time.LocalDate.now().toString()
                val yesterday = java.time.LocalDate.now().minusDays(1).toString()
                dailyMixRepository.getMix(mixId)?.let { _mixName.value = it.name }
                val dailyMix = genreMixDao.getDailyMix(today, mixId)
                    ?: genreMixDao.getDailyMix(yesterday, mixId)
                val trackIds: List<String> = if (dailyMix != null) {
                    genreMixDao.getDailyMixTrackIds(dailyMix.id)
                } else {
                    emptyList()
                }

                if (trackIds.isNotEmpty()) {
                    val allTracks = trackDao.getTracksByIds(trackIds)
                    _likedTrackIds.value = allTracks.filter { it.starredAt != null }.map { it.id }.toSet()
                    _dislikedTrackIds.value = allTracks.filter { it.isDisliked }.map { it.id }.toSet()
                    val trackMap = allTracks.associateBy { it.id }
                    val displayTracks = trackIds.mapNotNull { id ->
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
                    _state.value = State(tracks = displayTracks, isLoading = false)
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
        containerColor = Color(0xFF1C1C2E),
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(bottom = spacing3XL())) {
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mix · $trackCount tracks", color = Color(0xFF888888), fontSize = textBodyM())
            }
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(horizontal = spacingL()),
            )
            MixSheetAction(
                "Play Next",
                "Insert $trackCount tracks after current",
                Icons.Default.ArrowUpward,
                Color(0xFF00C8B4),
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
            MixSheetAction("Refresh Mix", "Regenerate this mix", Icons.Default.Refresh, Color(0xFF00C8B4), onRefresh)
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
        containerColor = Color(0xFF1C1C2E),
        shape = RoundedCornerShape(topStart = spacingXL(), topEnd = spacingXL()),
    ) {
        Column(Modifier.padding(bottom = spacing3XL())) {
            Box(Modifier.fillMaxWidth().padding(top = spacingS()), contentAlignment = Alignment.Center) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF444444)))
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        track.title,
                        color = Color.White,
                        fontSize = textHeadingS(),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    track.artist?.let {
                        Text(
                            it,
                            color = Color(0xFF888888),
                            fontSize = textBodyM(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                Color(0xFF00C8B4),
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
            Text(label, color = Color.White, fontSize = textBodyM(), fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF888888), fontSize = textLabelM())
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = spacingL()))
}

// ─── Helpers for Album-style track rows ───

/** Find the 1-based index of a track in the list (for the number column). */
private fun indexOfTrack(tracks: List<GenreMixTrack>, track: GenreMixTrack): Int {
    val idx = tracks.indexOfFirst { it.id == track.id }
    return if (idx >= 0) idx else 0
}

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
                    .clip(RoundedCornerShape(1.dp)).background(Color(0xFF00C8B4)),
            )
        }
    }
}
