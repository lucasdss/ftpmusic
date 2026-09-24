package com.lucasdss.ftpmusic.app.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.ui.player.CastButtonState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val provider: PlaybackStateProvider,
    private val playbackManager: PlaybackManager,
    private val favoriteRepository: FavoriteRepository,
    private val storage: SecureStorage,
    private val trackDao: TrackDao,
    private val playbackStateDao: PlaybackStateDao,
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
     * True while a reaction mutation (like/dislike/rate) is in flight. The
     * track-change reaction loader checks this before applying a stale DB read
     * so it never clobbers the user's just-set optimistic value.
     */
    @Volatile
    private var reactionMutationInFlight = false

    init {
        // Restore the sleep timer from the persisted DAO — NOT PlayerHolder,
        // which is process-local and empty right after process death. The
        // service re-arms from the same row on restart (MediaService.onCreate),
        // so arming here too is race-free and idempotent; enforcement itself
        // lives in the service so it survives recents-swipe. (get() is a Room
        // suspend call — Room runs it on its own executor, no IO hop needed.)
        viewModelScope.launch {
            val persistedEndMs = playbackStateDao.get()?.sleepTimerEndMs ?: 0L
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
                            // Skip applying when a reaction mutation is in flight —
                            // the toggle/rate already set the optimistic value and a
                            // stale DB read must not clobber it.
                            if (reactionMutationInFlight) return@collect
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
    fun clearQueue() = playbackManager.clearQueue()

    /** Clear manual Queue only (Spotify / Apple Clear) — keep Continue Playing. */
    fun clearPriorityQueue() = playbackManager.clearPriorityQueue()
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
        // death via persisted playback_state). This ViewModel only mirrors the
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
        val trackId = provider.playbackState.value.currentTrackId ?: return
        val isStarred = _isStarred.value
        val isDisliked = _isDisliked.value
        reactionMutationInFlight = true
        viewModelScope.launch {
            try {
                if (isStarred) {
                    favoriteRepository.unlikeTrack(trackId)
                    _isStarred.value = false
                } else {
                    favoriteRepository.likeTrack(trackId)
                    _isStarred.value = true
                    // Mutual exclusion: liking clears dislike
                    if (isDisliked) _isDisliked.value = false
                }
            } catch (_: Exception) {
                // Restore BOTH on failure — likeTrack may have cleared dislike
                // locally before the server star failed.
                _isStarred.value = isStarred
                _isDisliked.value = isDisliked
            } finally {
                reactionMutationInFlight = false
            }
            syncExtraState()
        }
    }

    /** Toggle thumbs-down (dislike). Local-only; clears like (star) on server best-effort. */
    fun toggleDislike() {
        val trackId = provider.playbackState.value.currentTrackId ?: return
        val isStarred = _isStarred.value
        val isDisliked = _isDisliked.value
        reactionMutationInFlight = true
        viewModelScope.launch {
            try {
                if (isDisliked) {
                    favoriteRepository.clearDislikeTrack(trackId)
                    _isDisliked.value = false
                } else {
                    favoriteRepository.dislikeTrack(trackId)
                    _isDisliked.value = true
                    // Mutual exclusion: disliking clears like (star)
                    if (isStarred) _isStarred.value = false
                }
            } catch (_: Exception) {
                _isDisliked.value = isDisliked
                _isStarred.value = isStarred
            } finally {
                reactionMutationInFlight = false
            }
            syncExtraState()
        }
    }

    /** Rate the current track 0-5. Local-first write, then best-effort server sync. */
    fun rateCurrent(rating: Int) {
        val trackId = provider.playbackState.value.currentTrackId ?: return
        val clamped = rating.coerceIn(0, 5)
        _trackRating.value = clamped
        syncExtraState()
        reactionMutationInFlight = true
        viewModelScope.launch {
            try {
                trackDao.setRating(trackId, clamped)
                val user = storage.get(SecureStorage.KEY_USERNAME) ?: ""
                val pass = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
                val auth = authHelper.buildAuthParams(user, pass)
                api.setRating(auth, id = trackId, rating = clamped)
            } catch (_: Exception) {} finally {
                reactionMutationInFlight = false
            }
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

    /** Share the current queue as a playlist on the Subsonic server.
     *  Creates a playlist named "Queue - {date}" and opens the Android share sheet. */
    fun shareQueue(context: android.content.Context) {
        val player = PlayerHolder.exoPlayer ?: PlayerHolder.player ?: return
        if (player.mediaItemCount == 0) return
        val trackIds = (0 until player.mediaItemCount).mapNotNull { i ->
            player.getMediaItemAt(i)?.mediaId?.takeIf { it.isNotEmpty() }
        }
        if (trackIds.isEmpty()) return

        val dateStr = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US)
            .format(java.util.Date())
        val playlistName = "Queue - $dateStr"

        viewModelScope.launch {
            try {
                val user = storage.get(SecureStorage.KEY_USERNAME) ?: ""
                val pass = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
                val params = authHelper.buildAuthParams(user, pass)
                val response = api.createPlaylist(
                    params,
                    name = playlistName,
                    songIds = trackIds.joinToString(","),
                )
                if (!authHelper.checkResponseStatus(response)) {
                    val err = authHelper.getResponseError(response)
                    android.util.Log.w("ftpmusic", "shareQueue failed: $err")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Failed to create playlist",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@launch
                }
                val sr = response["subsonic-response"] as? Map<*, *>
                val pl = sr?.get("playlist") as? Map<*, *>
                val newId = pl?.get("id") as? String
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (newId != null) {
                        val count = trackIds.size
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "Shared via ftpmusic: \"$playlistName\" ($count tracks)",
                            )
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Queue"))
                    }
                    android.widget.Toast.makeText(
                        context,
                        "Created \"$playlistName\" (${trackIds.size} tracks)",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "shareQueue failed: ${e.message}")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to share queue",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
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
                isPriority = priorityFlags.getOrElse(i) { false },
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
    val isPriority: Boolean = false,
)
