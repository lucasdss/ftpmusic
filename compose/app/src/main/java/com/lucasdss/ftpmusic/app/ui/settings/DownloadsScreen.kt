package com.lucasdss.ftpmusic.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.SurfaceElevated
import com.lucasdss.ftpmusic.app.ui.components.CoverArtImage
import com.lucasdss.ftpmusic.app.ui.components.DetailBackButton
import com.lucasdss.ftpmusic.app.ui.components.FittingText
import com.lucasdss.ftpmusic.app.ui.components.SongListRow
import com.lucasdss.ftpmusic.app.ui.components.formatSongDuration
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl
import com.lucasdss.ftpmusic.app.ui.textBodyM
import com.lucasdss.ftpmusic.app.ui.textHeadingS
import com.lucasdss.ftpmusic.app.ui.textLabelM
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val PAGE = 50

data class DownloadsUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val staleIds: Set<String> = emptySet(),
    val loading: Boolean = true,
    val endReached: Boolean = false,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val cacheService: CacheService,
    private val playbackManager: PlaybackManager,
    private val authHelper: SubsonicAuthHelper,
    private val storage: SecureStorage,
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadsUiState())
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    private var offset = 0
    private val loadMutex = Mutex()

    /** Synchronous guard so two scrolls cannot both pass before loading=true publishes. */
    private val loadInFlight = AtomicBoolean(false)

    /** Bumped on clearAll so a late fetchPage cannot resurrect deleted rows. */
    private var loadGeneration = 0

    /** refresh() while busy — flush once current load finishes. */
    private val pendingRefresh = AtomicBoolean(false)

    init {
        refresh()
    }

    fun refresh() {
        if (!loadInFlight.compareAndSet(false, true)) {
            pendingRefresh.set(true)
            return
        }
        viewModelScope.launch {
            try {
                loadMutex.withLock {
                    offset = 0
                    _state.value = DownloadsUiState(loading = true)
                    fetchPage(reset = true)
                }
            } catch (_: Exception) {
                // loading cleared in finally
            } finally {
                _state.value = _state.value.copy(loading = false)
                loadInFlight.set(false)
                flushPendingRefresh()
            }
        }
    }

    fun loadMore() {
        if (_state.value.endReached) return
        if (!loadInFlight.compareAndSet(false, true)) return
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            try {
                loadMutex.withLock {
                    fetchPage(reset = false)
                }
            } catch (_: Exception) {
                // loading cleared in finally
            } finally {
                _state.value = _state.value.copy(loading = false)
                loadInFlight.set(false)
                flushPendingRefresh()
            }
        }
    }

    private fun flushPendingRefresh() {
        if (pendingRefresh.compareAndSet(true, false)) {
            refresh()
        }
    }

    private suspend fun fetchPage(reset: Boolean) {
        val generation = loadGeneration
        val page = trackDao.getDownloadedPaged(PAGE, if (reset) 0 else offset)
        if (generation != loadGeneration) return
        val stale = mutableSetOf<String>()
        page.forEach { track ->
            if (cacheService.healStaleCachePath(track.id)) {
                stale.add(track.id)
            } else if (!track.cachedFilePath.isNullOrBlank() && !cacheService.isStoredInCache(track.id)) {
                stale.add(track.id)
            }
        }
        val healedPage = if (stale.isEmpty()) {
            page
        } else {
            page.map { t ->
                if (t.id in stale) t.copy(cachedFilePath = null) else t
            }
        }
        if (generation != loadGeneration) return
        offset = if (reset) healedPage.size else offset + healedPage.size
        _state.value = _state.value.copy(
            tracks = if (reset) healedPage else _state.value.tracks + healedPage,
            staleIds = if (reset) stale else _state.value.staleIds + stale,
            loading = false,
            endReached = page.size < PAGE,
        )
    }

    fun playAt(index: Int) {
        val entities = _state.value.tracks
        if (index !in entities.indices) return
        val tracks = entities.map { e ->
            Track(
                id = e.id,
                title = e.title,
                artist = e.artist,
                album = e.album,
                duration = e.durationSeconds,
                coverArt = e.coverArtUrl,
                suffix = e.suffix,
                contentType = e.contentType,
            )
        }
        val urls = tracks.map { buildStreamUrl(it.id) }
        playbackManager.playAlbum(tracks, urls, startIndex = index)
    }

    fun remove(trackId: String) {
        viewModelScope.launch {
            loadMutex.withLock {
                cacheService.removeDownload(trackId)
                _state.value = _state.value.copy(
                    tracks = _state.value.tracks.filter { it.id != trackId },
                    staleIds = _state.value.staleIds - trackId,
                )
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            // Bump before mutex so an in-flight fetchPage discards its write.
            loadGeneration++
            loadMutex.withLock {
                cacheService.clearDownloads()
                _state.value = DownloadsUiState(loading = false, endReached = true)
                offset = 0
            }
        }
    }

    private fun buildStreamUrl(trackId: String): String {
        val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        val username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
        val password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
        return authHelper.buildStreamUrl(base, trackId, username, password)
    }
}

@Composable
fun DownloadsScreen(onBack: () -> Unit, viewModel: DownloadsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 3) viewModel.loadMore()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .testTag("downloads_screen"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailBackButton(onBack = onBack, inset = false)
            FittingText(
                text = "Downloads",
                color = Color.White,
                fontSize = textHeadingS(),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            if (state.tracks.isNotEmpty()) {
                TextButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.testTag("downloads_clear_all"),
                ) {
                    Text("Clear all", color = BrandTeal, fontSize = textLabelM())
                }
            }
        }

        Text(
            text = "Pinned offline tracks. Auto-cache is managed separately.",
            color = NavUnselected,
            fontSize = textBodyM(),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        when {
            state.loading && state.tracks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = NavUnselected)
                }
            }

            state.tracks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = null,
                            tint = NavUnselected,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No downloads yet", color = Color.White, fontSize = textHeadingS())
                        Text(
                            "Download tracks from album or queue menus.",
                            color = NavUnselected,
                            fontSize = textBodyM(),
                            modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, track ->
                        DownloadRow(
                            track = track,
                            stale = track.id in state.staleIds,
                            onPlay = { viewModel.playAt(index) },
                            onRemove = { viewModel.remove(track.id) },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear Downloads") },
            text = { Text("Remove all pinned offline tracks from this device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearAll()
                    },
                ) { Text("Clear", color = BrandTeal) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DownloadRow(track: TrackEntity, stale: Boolean, onPlay: () -> Unit, onRemove: () -> Unit) {
    val coverUrl = rememberCoverArtUrl(track.coverArtUrl)
    val subtitle = buildString {
        append(listOfNotNull(track.artist, track.album).joinToString(" · "))
        if (stale) {
            if (isNotEmpty()) append(" · ")
            append("File missing — play may re-cache")
        }
    }.ifBlank { null }
    SongListRow(
        title = track.title,
        subtitle = subtitle,
        downloadStatus = downloadsRowStatus(stale),
        durationLabel = track.durationSeconds?.let { formatSongDuration(it) },
        onClick = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("download_row_${track.id}"),
        leadingContent = {
            CoverArtImage(
                url = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                decodeSize = 48.dp,
            )
        },
        trailingContent = {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("download_remove_${track.id}"),
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Remove download", tint = NavUnselected)
            }
        },
    )
}

/** Stale = file missing; hide purple downloaded glyph (ADR-0100). */
internal fun downloadsRowStatus(stale: Boolean): String = if (stale) "none" else "downloaded"
