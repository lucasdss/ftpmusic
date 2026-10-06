package com.lucasdss.ftpmusic.app.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.db.QueueDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.ui.player.CastButtonState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val provider: PlaybackStateProvider,
    private val playbackManager: PlaybackManager,
    private val favoriteRepository: FavoriteRepository,
    private val storage: SecureStorage,
    private val trackDao: TrackDao,
    private val queueDao: QueueDao,
    private val playlistRepository: PlaylistRepository,
    private val api: SubsonicApi,
) : ViewModel() {
    private val authHelper = SubsonicAuthHelper()

    // Non-player state fields managed locally. Underscores distinguish mutable
    // inputs consumed by the combined public state below.
    @Suppress("ktlint:standard:backing-property-naming")
    private val _isStarred = MutableStateFlow(false)

    @Suppress("ktlint:standard:backing-property-naming")
    private val _isDisliked = MutableStateFlow(false)

    @Suppress("ktlint:standard:backing-property-naming")
    private val _trackRating = MutableStateFlow(0)

    @Suppress("ktlint:standard:backing-property-naming")
    private val _sleepTimerEndMs = MutableStateFlow(0L)

    @Suppress("ktlint:standard:backing-property-naming")
    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())

    /** Recently played tracks for queue sheet history band (ADR-0075). */
    @Suppress("ktlint:standard:backing-property-naming")
    private val _queueHistory = MutableStateFlow<List<QueueHistoryTrack>>(emptyList())
    val queueHistory: StateFlow<List<QueueHistoryTrack>> = _queueHistory.asStateFlow()

    /** Combined state: provider (Player) state + local non-player state. */
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Navigation-scope state (P1): position stripped so a 200 ms tick never
     *  recomposes NavHost/every screen. Collected by NavHost instead of [state];
     *  position is collected separately via [positionMs] in player scopes.
     *  (Flow operators live here, not in composition — lint
     *  FlowOperatorInvokedInComposition gate.) */
    val stateWithoutPosition: StateFlow<PlaybackState> = state
        .map { it.copy(position = 0L) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackState())

    /** High-frequency position (P1): pass-through from the provider so the UI
     *  collects position in its own scope, independent of [state]. */
    val positionMs: StateFlow<Long> = provider.positionMs

    /**
     * Mutation generation for reaction writes. Track-change loader skips DB apply
     * only while [mutationTrackId] still matches and [mutationGeneration] is live.
     * Overlapping same-track toggles each bump generation; older `finally` must not
     * clear a newer in-flight mutation.
     */
    @Volatile
    private var mutationTrackId: String? = null

    private val mutationGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    /** Serialize like/dislike/rate writes per track so overlapping toggles cannot reorder Room. */
    private val reactionMutexes = ConcurrentHashMap<String, Mutex>()

    private fun reactionMutex(trackId: String): Mutex = reactionMutexes.getOrPut(trackId) { Mutex() }

    init {
        // Restore sleep timer from queue_state (ADR-0074) — NOT PlayerHolder,
        // which is process-local and empty right after process death. The
        // service re-arms from the same row on restart (MediaService.onCreate).
        viewModelScope.launch {
            val persistedEndMs = queueDao.getState()?.sleepTimerEndMs ?: 0L
            if (persistedEndMs > System.currentTimeMillis()) {
                _sleepTimerEndMs.value = persistedEndMs
                syncExtraState()
                provider.armSleepTimer(persistedEndMs)
            }
        }

        viewModelScope.launch {
            // isStarred/isDisliked/trackRating are already carried by
            // provider.playbackState via updateExtraState — only merge the
            // locally-managed sleep timer, downloads and priority size here.
            combine(
                provider.playbackState,
                _sleepTimerEndMs,
                _downloadedTrackIds,
            ) { providerState, sleepTimer, downloaded ->
                providerState.copy(
                    sleepTimerEndMs = sleepTimer,
                    downloadedTrackIds = downloaded,
                    priorityQueueSize = playbackManager.priorityQueueSize,
                )
            }.collect { _state.value = it }
        }
        viewModelScope.launch {
            provider.playbackState
                .map { it.currentTrackId }
                .distinctUntilChanged()
                .collect { trackId ->
                    // Load persisted reaction state (like=starred_at, dislike, rating)
                    // from the local DB on every track change — the previous behavior
                    // reset to false without ever reading the persisted values.
                    var starred = false
                    var disliked = false
                    var rating = 0
                    if (trackId != null) {
                        try {
                            val entity = trackDao.getTrack(trackId)
                            // Skip applying only when THIS track still has an in-flight
                            // mutation — never blanket-skip (would leave next track blank).
                            if (trackId == mutationTrackId && mutationGeneration.get() != 0) return@collect
                            starred = entity?.starredAt != null
                            disliked = entity?.isDisliked == true
                            rating = entity?.userRating ?: 0
                        } catch (_: Exception) {}
                    }
                    _isStarred.value = starred
                    _isDisliked.value = disliked
                    _trackRating.value = rating
                    syncExtraState()
                }
        }
    }

    private fun syncExtraState() {
        provider.updateExtraState(
            isStarred = _isStarred.value,
            sleepTimerEndMs = _sleepTimerEndMs.value,
            downloadedTrackIds = _downloadedTrackIds.value,
            isDisliked = _isDisliked.value,
            trackRating = _trackRating.value,
        )
    }

    fun playPause() {
        android.util.Log.w("ftpmusic", "DEBUG playPause called, delegating to provider")
        provider.playPause()
    }
    fun skipNext() = provider.skipNext()
    fun skipPrev() = provider.skipPrev()
    fun seekTo(fraction: Float) = provider.seekTo(fraction)
    fun toggleRepeat() = provider.toggleRepeat()
    fun toggleShuffle() = provider.toggleShuffle()
    fun toggleSpeed() = provider.toggleSpeed()
    fun setVolume(volume: Float) = provider.setVolume(volume)
    fun playQueueItem(index: Int) {
        playbackManager.playQueueItem(index)
    }
    fun removeFromQueue(index: Int) = playbackManager.removeFromQueue(index)

    /** Remove selected queue indices descending so earlier removals don't shift later ones. */
    fun removeFromQueueBatch(indices: Collection<Int>) {
        indices.sortedDescending().forEach { playbackManager.removeFromQueue(it) }
    }

    fun clearQueue() = playbackManager.clearQueue()

    /** Clear manual Queue only (Spotify / Apple Clear) — keep Continue Playing. */
    fun clearPriorityQueue() = playbackManager.clearPriorityQueue()

    /** Clear Continuous Play Autoplay tail only (ADR-0053). */
    fun clearAutoplayQueue() = playbackManager.clearAutoplayQueue()

    fun isAutoplayFlags(): List<Boolean> = playbackManager.isAutoplayFlags()

    fun setContinuousPlayEnabled(enabled: Boolean) {
        playbackManager.setContinuousPlayEnabled(enabled)
        storage.put(SecureStorage.KEY_CONTINUOUS_PLAY_ENABLED, enabled.toString())
    }

    fun isContinuousPlayEnabled(): Boolean = playbackManager.continuousPlayEnabled

    /** Reactive Continuous Play for queue sheet (settings + in-sheet toggle). */
    val continuousPlayEnabled: StateFlow<Boolean> = playbackManager.continuousPlayEnabledFlow

    fun playStream(url: String, title: String) = playbackManager.playStream(url, title)
    fun persistQueue() {
        playbackManager.persistCurrentQueue()
    }
    fun beginQueueReorder(entryId: Int, fromIndex: Int) = playbackManager.beginQueueReorder(entryId, fromIndex)
    fun commitQueueReorder() = playbackManager.commitQueueReorder()
    fun moveQueueItem(from: Int, to: Int) {
        playbackManager.moveQueueItem(from, to)
    }
    fun getTrackInfo(trackId: String) = playbackManager.getTrackInfo(trackId)

    fun isPriorityFlags(): List<Boolean> = playbackManager.isPriorityFlags()

    /** Rollback the last optimistic queue mutation if the background sync failed. */
    fun queueRollback() {
        playbackManager.queueRollback()
    }

    fun startSleepTimer(minutes: Int) {
        // Enforcement lives in MediaService (survives recents-swipe + process
        // death via persisted queue_state). This ViewModel only mirrors the
        // deadline into shared state and forwards the arm command.
        val endMs = System.currentTimeMillis() + (minutes * 60_000L)
        _sleepTimerEndMs.value = endMs
        syncExtraState()
        provider.armSleepTimer(endMs)
    }

    fun cancelSleepTimer() {
        _sleepTimerEndMs.value = 0L
        syncExtraState()
        provider.cancelSleepTimer()
    }

    fun onCastDisconnected() {
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null
    }

    fun onCastConnected(deviceName: String) {
        // Cast state is now read from Player directly — no-op at ViewModel level
    }

    fun onPauseByUi() {
        // Pause state is now read from Player directly — no-op at ViewModel level
    }

    /** Toggle thumbs-up (like). Like == star on Navidrome; clears dislike. */
    fun toggleLike() {
        val mutationTrackId = provider.playbackState.value.currentTrackId ?: return
        val isStarred = _isStarred.value
        val isDisliked = _isDisliked.value
        // Optimistic UI before repo (ADR UI contract).
        if (isStarred) {
            _isStarred.value = false
        } else {
            _isStarred.value = true
            if (isDisliked) _isDisliked.value = false
        }
        syncExtraState()
        this.mutationTrackId = mutationTrackId
        val myGen = mutationGeneration.incrementAndGet()
        viewModelScope.launch {
            reactionMutex(mutationTrackId).withLock {
                try {
                    if (isStarred) {
                        favoriteRepository.unlikeTrack(mutationTrackId)
                    } else {
                        favoriteRepository.likeTrack(mutationTrackId)
                    }
                    if (provider.playbackState.value.currentTrackId == mutationTrackId &&
                        mutationGeneration.get() == myGen
                    ) {
                        if (isStarred) {
                            _isStarred.value = false
                        } else {
                            _isStarred.value = true
                            if (isDisliked) _isDisliked.value = false
                        }
                    }
                } catch (_: Exception) {
                    // Only restore if this generation is still current — never undo a newer tap.
                    if (provider.playbackState.value.currentTrackId == mutationTrackId &&
                        mutationGeneration.get() == myGen
                    ) {
                        _isStarred.value = isStarred
                        _isDisliked.value = isDisliked
                    }
                } finally {
                    mutationGeneration.compareAndSet(myGen, 0)
                    if (mutationGeneration.get() == 0) {
                        this@PlaybackViewModel.mutationTrackId = null
                    }
                }
            }
            syncExtraState()
        }
    }

    /** Toggle thumbs-down (dislike). Local-only; clears like (star) on server best-effort. */
    fun toggleDislike() {
        val mutationTrackId = provider.playbackState.value.currentTrackId ?: return
        val isStarred = _isStarred.value
        val isDisliked = _isDisliked.value
        if (isDisliked) {
            _isDisliked.value = false
        } else {
            _isDisliked.value = true
            if (isStarred) _isStarred.value = false
        }
        syncExtraState()
        this.mutationTrackId = mutationTrackId
        val myGen = mutationGeneration.incrementAndGet()
        viewModelScope.launch {
            reactionMutex(mutationTrackId).withLock {
                try {
                    if (isDisliked) {
                        favoriteRepository.clearDislikeTrack(mutationTrackId)
                    } else {
                        favoriteRepository.dislikeTrack(mutationTrackId)
                    }
                    if (provider.playbackState.value.currentTrackId == mutationTrackId &&
                        mutationGeneration.get() == myGen
                    ) {
                        if (isDisliked) {
                            _isDisliked.value = false
                        } else {
                            _isDisliked.value = true
                            if (isStarred) _isStarred.value = false
                        }
                    }
                } catch (_: Exception) {
                    if (provider.playbackState.value.currentTrackId == mutationTrackId &&
                        mutationGeneration.get() == myGen
                    ) {
                        _isDisliked.value = isDisliked
                        _isStarred.value = isStarred
                    }
                } finally {
                    mutationGeneration.compareAndSet(myGen, 0)
                    if (mutationGeneration.get() == 0) {
                        this@PlaybackViewModel.mutationTrackId = null
                    }
                }
            }
            syncExtraState()
        }
    }

    /** Rate the current track 0-5. Local-first write, then best-effort server sync. */
    fun rateCurrent(rating: Int) {
        val mutationTrackId = provider.playbackState.value.currentTrackId ?: return
        val clamped = rating.coerceIn(0, 5)
        val previous = _trackRating.value
        _trackRating.value = clamped
        syncExtraState()
        this.mutationTrackId = mutationTrackId
        val myGen = mutationGeneration.incrementAndGet()
        viewModelScope.launch {
            try {
                favoriteRepository.rateTrack(mutationTrackId, clamped)
                if (provider.playbackState.value.currentTrackId == mutationTrackId &&
                    mutationGeneration.get() == myGen
                ) {
                    _trackRating.value = clamped
                }
            } catch (_: Exception) {
                if (provider.playbackState.value.currentTrackId == mutationTrackId) {
                    _trackRating.value = previous
                }
            } finally {
                mutationGeneration.compareAndSet(myGen, 0)
                if (mutationGeneration.get() == 0) {
                    this@PlaybackViewModel.mutationTrackId = null
                }
            }
            syncExtraState()
        }
    }

    /** Build a stream URL for a given track ID — direct URL, no proxy wrapping. */
    fun buildStreamUrl(trackId: String): String {
        val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        val helper = authHelper
        val username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
        val password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
        return helper.buildStreamUrl(base, trackId, username, password)
    }

    /** Play a single track using the playback manager. */
    fun playSingleTrack(track: com.lucasdss.ftpmusic.app.data.model.Track, streamUrl: String) {
        playbackManager.playSingleTrack(track, streamUrl)
    }

    fun playAll(
        tracks: List<com.lucasdss.ftpmusic.app.data.model.Track>,
        urls: List<String>,
        sourceType: String? = null,
        sourceId: String? = null,
        sourceName: String? = null,
    ) {
        playbackManager.playAlbum(tracks, urls, sourceType = sourceType, sourceId = sourceId, sourceName = sourceName)
    }

    fun shuffleAll(
        tracks: List<com.lucasdss.ftpmusic.app.data.model.Track>,
        urls: List<String>,
        sourceType: String? = null,
        sourceId: String? = null,
        sourceName: String? = null,
    ) {
        playbackManager.shuffleAlbum(
            tracks,
            urls,
            sourceType = sourceType,
            sourceId = sourceId,
            sourceName = sourceName,
        )
    }

    override fun onCleared() {
        super.onCleared()
        // UI state dies with the ViewModel. The service keeps enforcing the
        // timer (and owns PlayerHolder.sleepTimerEndMs) — do NOT clear it here,
        // otherwise a recents-swipe would cancel the pending pause.
        _sleepTimerEndMs.value = 0L
    }

    /**
     * Save queue as local-first playlist, publish public on Navidrome, share deep link.
     * ADR-0076 — replaces prior server-only shareQueue exception (ADR-0015 row 18).
     */
    fun shareQueue(context: android.content.Context) {
        val trackIds = currentQueueTrackIds()
        if (trackIds.isEmpty()) return
        val playlistName = defaultQueuePlaylistName()

        viewModelScope.launch {
            try {
                val serverId = playlistRepository.createPlaylistWithTracksSynced(playlistName, trackIds)
                if (serverId == null) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Saved locally — couldn't publish for sharing",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@launch
                }
                val published = playlistRepository.setPlaylistPublic(serverId, true)
                if (!published) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Playlist saved but couldn't make it public — share cancelled",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@launch
                }
                val base = DynamicBaseUrl.url.trimEnd('/')
                val deepLink = "$base/app/#/playlist/$serverId/show"
                val shareText = "\"$playlistName\" (${trackIds.size} tracks)\n$deepLink"
                withContext(Dispatchers.Main) {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(shareIntent, "Share Queue"),
                    )
                    android.widget.Toast.makeText(
                        context,
                        "Shared \"$playlistName\" (public)",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "shareQueue failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to share queue",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    /** Default name for save-as-playlist (local-first, ADR-0075). */
    fun defaultQueuePlaylistName(): String {
        val dateStr = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US)
            .format(java.util.Date())
        return "Queue - $dateStr"
    }

    private fun currentQueueTrackIds(): List<String> {
        val player = PlayerHolder.exoPlayer ?: PlayerHolder.player ?: return emptyList()
        if (player.mediaItemCount == 0) return emptyList()
        return (0 until player.mediaItemCount).mapNotNull { i ->
            player.getMediaItemAt(i)?.mediaId?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Save current queue as a local-first playlist (ADR-0075).
     * Unlike [shareQueue], this uses [PlaylistRepository] Room + pending sync.
     */
    fun saveQueueAsPlaylist(context: android.content.Context, name: String) {
        val trackIds = currentQueueTrackIds()
        if (trackIds.isEmpty()) return
        val trimmed = name.trim().ifEmpty { defaultQueuePlaylistName() }
        viewModelScope.launch {
            try {
                val tempId = playlistRepository.createPlaylist(trimmed)
                playlistRepository.addToPlaylist(tempId, trackIds)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Saved \"$trimmed\" (${trackIds.size} tracks)",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "saveQueueAsPlaylist failed: ${e.message}")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to save queue",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    /** Refresh Recently Played band, excluding IDs already in the active queue. */
    fun refreshQueueHistory() {
        viewModelScope.launch {
            try {
                val exclude = currentQueueTrackIds().toSet() +
                    setOfNotNull(PlayerHolder.player?.currentMediaItem?.mediaId)
                val rows = trackDao.getRecentlyPlayed(limit = 20)
                _queueHistory.value = rows
                    .filter { it.id !in exclude }
                    .map {
                        QueueHistoryTrack(
                            id = it.id,
                            title = it.title,
                            artist = it.artist,
                            coverArtId = it.coverArtUrl,
                        )
                    }
            } catch (_: Exception) {
                _queueHistory.value = emptyList()
            }
        }
    }

    fun playNextFromHistory(trackId: String) {
        viewModelScope.launch {
            try {
                val entity = trackDao.getTrack(trackId) ?: return@launch
                val track = Track(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    albumId = entity.albumId,
                    artistId = entity.artistId,
                    duration = entity.durationSeconds,
                    coverArt = entity.coverArtUrl,
                    bitrate = entity.bitrate,
                    suffix = entity.suffix,
                    contentType = entity.contentType,
                    path = entity.path,
                )
                val url = buildStreamUrl(track.id)
                playbackManager.playNext(track, url)
                refreshQueueHistory()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "playNextFromHistory failed: ${e.message}")
            }
        }
    }

    fun refreshQueueDownloadStatus() {
        viewModelScope.launch {
            try {
                // exoPlayer always has the full queue (same as player during local)
                val player = PlayerHolder.exoPlayer ?: PlayerHolder.player ?: return@launch
                val trackIds = (0 until player.mediaItemCount).mapNotNull { i ->
                    player.getMediaItemAt(i)?.mediaId?.takeIf { it.isNotEmpty() }
                }
                if (trackIds.isEmpty()) return@launch
                val entities = trackDao.getTracksByIds(trackIds)
                val downloadedIds = entities
                    .filter { it.isDownloaded || it.cachedFilePath != null }
                    .map { it.id }
                    .toSet()
                _downloadedTrackIds.value = downloadedIds
                syncExtraState()
            } catch (_: Exception) {}
        }
    }

    /** Get N tracks from the player queue starting at index 0 (full queue visibility). */
    fun getUpcomingTracks(count: Int): List<UpcomingTrack> {
        // exoPlayer always has the full queue — both local and during Cast
        val player = PlayerHolder.exoPlayer ?: PlayerHolder.player
        val queuePlayer = player ?: return emptyList()
        if (queuePlayer.mediaItemCount == 0) return emptyList()
        // Current track by ID from the ACTIVE player (CastPlayer during Cast) —
        // ExoPlayer's index is stale while the receiver auto-advances.
        // ID-matching is the Cast SDK recommended pattern (index-matching breaks
        // with windowed/shuffled receiver queues).
        val activeTrackId = PlayerHolder.player?.currentMediaItem?.mediaId
            ?.takeIf { it.isNotEmpty() }
        val currentIndex = activeTrackId?.let { id ->
            (0 until queuePlayer.mediaItemCount).firstOrNull { i ->
                queuePlayer.getMediaItemAt(i)?.mediaId == id
            }
        } ?: queuePlayer.currentMediaItemIndex
        if (currentIndex < 0) return emptyList()
        val start = 0
        val end = minOf(currentIndex + count, queuePlayer.mediaItemCount)
        val priorityFlags = playbackManager.isPriorityFlags()
        val autoplayFlags = playbackManager.isAutoplayFlags()
        val entryIds = playbackManager.entryIds()
        return (start until end).mapNotNull { i ->
            val item = player.getMediaItemAt(i) ?: return@mapNotNull null
            val title = item.mediaMetadata.title?.toString() ?: return@mapNotNull null
            val coverUrl = item.mediaMetadata.artworkUri?.toString()
            val durationMs = item.mediaMetadata.extras?.getLong("duration") ?: 0L
            val rating = item.mediaMetadata.extras?.getInt("userRating")?.takeIf { it > 0 }
            UpcomingTrack(
                title = title,
                artist = item.mediaMetadata.artist?.toString(),
                coverArtUrl = coverUrl,
                durationMs = durationMs,
                userRating = rating,
                isCurrent = i == currentIndex,
                trackId = item.mediaId.takeIf { it.isNotEmpty() },
                queueIndex = i,
                entryId = entryIds.getOrElse(i) { item.queueEntryId() },
                isPriority = priorityFlags.getOrElse(i) { false },
                isAutoplay = autoplayFlags.getOrElse(i) { false } || item.isAutoplay(),
            )
        }
    }
}

@androidx.compose.runtime.Stable
data class UpcomingTrack(
    val title: String,
    val artist: String? = null,
    val coverArtUrl: String? = null,
    val durationMs: Long = 0L,
    val userRating: Int? = null,
    val isCurrent: Boolean = false,
    val trackId: String? = null,
    val queueIndex: Int = 0,
    /** Stable Dual-queue entry id (>0) for reorder keys; 0 = fall back to index. */
    val entryId: Int = 0,
    val isPriority: Boolean = false,
    val isAutoplay: Boolean = false,
)

/** Recently played row for queue sheet history band (ADR-0075). */
@androidx.compose.runtime.Immutable
data class QueueHistoryTrack(
    val id: String,
    val title: String,
    val artist: String? = null,
    val coverArtId: String? = null,
)
