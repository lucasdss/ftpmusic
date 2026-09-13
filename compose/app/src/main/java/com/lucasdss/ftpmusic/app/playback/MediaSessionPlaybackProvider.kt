package com.lucasdss.ftpmusic.app.playback

import androidx.annotation.VisibleForTesting
import androidx.media3.common.Player
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reads Player state directly from PlayerHolder.player (same-process).
 * Controls delegate to the callback wired by MediaService, which calls
 * Player commands directly. This matches the approach used by Substreamer,
 * Tempo, Synfonium, and UAMP.
 */
@Singleton
class MediaSessionPlaybackProvider @Inject constructor(
    private val offlineModeManager: com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager,
) : PlaybackStateProvider {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    /** 200 ms position mirror (P1): consumers that only need the progress bar
     *  collect this instead of the full [playbackState], so a position tick
     *  never recomposes scopes that read metadata only. */
    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val pollerScope = CoroutineScope(Dispatchers.Main)
    private var positionJob: Job? = null
    private var offlineJob: Job? = null
    private var connected = false

    private var controlCallback: (PlaybackControl) -> Unit = {}

    /** True while a Player is attached to [PlayerHolder] AND its listener is
     *  registered, so transport controls can actually be dispatched. UI layers
     *  can use it to tell "paused" from "playback pipeline down" instead of
     *  showing silently dead buttons. */
    private val _playerWired = MutableStateFlow(false)
    val playerWired: StateFlow<Boolean> = _playerWired.asStateFlow()

    /** Replay target: the last transport control that arrived while no Player
     *  was attached. Applied once the pipeline comes back (service restart) so
     *  a tap during a dead window is never dropped. */
    @Volatile
    private var pendingControl: PlaybackControl? = null

    /** Fired when a transport control arrives while no Player is attached.
     *  MainActivity sets this to (re)start/assert [MediaService]. */
    @Volatile
    internal var onPlaybackUnavailable: (() -> Unit)? = null

    private fun setPlayerWired(wired: Boolean) {
        if (_playerWired.value != wired) _playerWired.value = wired
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishFromPlayer(player)
        }

        // CastPlayer forwards typed callbacks (not the raw onEvents mask) for
        // events from the underlying ExoPlayer. Queue mutations via
        // addMediaItems() fire ONLY onTimelineChanged — without this override
        // queueSize stays stale and queue-driven refresh (download badges,
        // refill triggers) never fires.
        //
        // Guard: track transitions ALSO fire onTimelineChanged (before
        // onMediaItemTransition) — emitting there would read the OLD
        // currentMediaItem with the NEW queue, flashing stale metadata into
        // Now Playing / QuickSettings. Only emit when the queue SIZE actually
        // changed (append/remove/clear — the structural cases this callback
        // exists for); the transition path emits the correct state.
        private var lastMediaItemCount = -1
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            val player = PlayerHolder.player ?: return
            val count = player.mediaItemCount
            if (count == lastMediaItemCount) return
            lastMediaItemCount = count
            publishFromPlayer(player)
        }

        // Seek/transition reconciliation: a seek-to-end triggers a position
        // discontinuity then a transition; emitting fresh state here keeps
        // title/index/position/duration consistent (defense for onEvents order).
        override fun onPositionDiscontinuity(
            oldPosition: androidx.media3.common.Player.PositionInfo,
            newPosition: androidx.media3.common.Player.PositionInfo,
            reason: Int,
        ) {
            val player = PlayerHolder.player ?: return
            publishFromPlayer(player)
        }

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            val confirmedValue = volume / 100f
            // Cast SDK often reports 0 before the real level. Writing that into
            // both holder fields kills the 0.5 / session seed, so fromPlayer()
            // shows 0 and the slider's first SET_VOLUME sends 0. Ignore that
            // untrusted 0 unless the receiver is muted or the user dragged to 0.
            val pending = PlayerHolder.pendingCastVolume
            val untrustedZero = confirmedValue <= 0.001f &&
                !muted &&
                (pending == null || pending > 0.001f)
            if (untrustedZero) return
            PlayerHolder.castVolume = confirmedValue
            PlayerHolder.castDeviceVolume = confirmedValue
            PlayerHolder.castDeviceMuted = muted
            // Clear pending if confirmed value matches (±2%) — device acknowledged our send.
            // Stale intermediate ramp values are ignored because pending holds the *final* sent value.
            PlayerHolder.pendingCastVolume?.let { pending ->
                if (kotlin.math.abs(confirmedValue - pending) < 0.02f) {
                    PlayerHolder.pendingCastVolume = null
                    PlayerHolder.pendingCastVolumeTimestamp = 0L
                }
            }
            // Only update UI if volume actually changed — prevents initial 0→0 flicker
            // when the device reports its stored volume on first callback.
            // Route through fromPlayer() to respect the priority chain (pendingCastVolume
            // must win over intermediate ramp values from the device during active drag).
            val player = PlayerHolder.player
            if (player != null && kotlin.math.abs(_playbackState.value.volume - confirmedValue) > 0.001f) {
                publishFromPlayer(player)
            }
        }
    }

    /** Publish a fresh player snapshot to BOTH flows. Position mirrors to
     *  [_positionMs] so metadata-only scopes never observe the 200 ms tick. */
    private fun publishFromPlayer(player: Player) {
        val state = PlaybackState.fromPlayer(player, _playbackState.value)
        _playbackState.value = state
        _positionMs.value = state.position
    }

    /** Pending Cast volume expiry — if the device never confirms within this
     *  window (network drop, receiver off, GMS glitch), pendingCastVolume reverts
     *  to null so the UI falls back to the last confirmed castVolume. */
    internal val pendingVolumeTimeoutMs: Long = 3000L

    /** Polls currentPosition every 200ms for smooth progress bar updates. */
    private fun startPositionPoller() {
        positionJob?.cancel()
        positionJob = pollerScope.launch {
            while (isActive) {
                val player = PlayerHolder.player
                if (player == null) {
                    // Self-heal (E8): a transient null player (service recreate,
                    // partial onCreate crash) must not kill the poller forever —
                    // recheck on a short delay instead of breaking the loop.
                    setPlayerWired(false)
                    delay(500)
                    continue
                }
                if (!_playerWired.value) onPlayerWired(player)
                expireStalePendingVolume(player)
                if (player.isPlaying || PlayerHolder.isCasting) {
                    // While casting, prefer the receiver-state mirror: the
                    // CastPlayer timeline may be empty/stale, but the receiver
                    // mediaStatus is ground truth for current item + position.
                    if (PlayerHolder.isCasting) {
                        val castState = castStateSource?.invoke()
                        if (castState != null) {
                            // M6: only emit when the snapshot actually CHANGED —
                            // a paused receiver yields byte-identical snapshots
                            // every 200ms, and assigning a fresh PlaybackState
                            // unconditionally made the StateFlow emit 5×/sec of
                            // identical state (every collector recomposed doing
                            // nothing). PlaybackState is a data class — value
                            // equality is free.
                            if (_playbackState.value != castState) {
                                _playbackState.value = castState
                                _positionMs.value = castState.position
                            }
                            delay(positionPollMs)
                            continue
                        }
                    }
                    // Track changed since the last poll? Then a transition's
                    // metadata may not have settled — do a FULL fromPlayer so
                    // title/index can never lag behind position/duration
                    // ("shows track N but plays track N-1"). Otherwise the
                    // lightweight position/duration patch is enough.
                    val currentId = player.currentMediaItem?.mediaId
                    if (currentId != null && currentId != _playbackState.value.currentTrackId) {
                        publishFromPlayer(player)
                    } else {
                        _playbackState.update {
                            it.copy(
                                position = player.currentPosition,
                                duration = player.duration,
                            )
                        }
                        _positionMs.value = player.currentPosition
                    }
                }
                delay(positionPollMs)
            }
        }
    }

    /** Clears pendingCastVolume if it has been unconfirmed for > pendingVolumeTimeoutMs.
     *  Called each poll cycle so the UI self-heals without waiting for a device event. */
    internal fun expireStalePendingVolume(player: Player) {
        val pending = PlayerHolder.pendingCastVolume ?: return
        val ageMs = System.currentTimeMillis() - PlayerHolder.pendingCastVolumeTimestamp
        if (ageMs >= pendingVolumeTimeoutMs) {
            PlayerHolder.pendingCastVolume = null
            PlayerHolder.pendingCastVolumeTimestamp = 0L
            publishFromPlayer(player)
        }
    }

    @Volatile
    internal var positionPollMs: Long = 200L

    /**
     * Receiver-state mirror source, wired by MediaService while casting.
     * Returns a fully-formed [PlaybackState] synthesized from the Cast
     * receiver's media status (currentItemId → local queue item, receiver
     * position/playerState), or null when no session/status is available.
     *
     * When non-null output while casting, it WINS over the player-derived
     * path in the position poller — the media3 CastPlayer timeline can be
     * empty/stale (queue loaded via raw SDK APIs), so the mirror is the
     * reliable source for UI/notification during Cast.
     */
    @Volatile
    internal var castStateSource: (() -> PlaybackState?)? = null

    /**
     * A Player is attached (service (re)started or switched): wire the listener,
     * surface current state, and replay any transport control that arrived while
     * playback was unavailable — a tap during the dead window must never be lost.
     */
    private fun onPlayerWired(player: Player) {
        setPlayerWired(true)
        registerListener()
        publishFromPlayer(player)
        val pending = pendingControl
        if (pending != null) {
            pendingControl = null
            android.util.Log.w(
                "ftpmusic",
                "[PlaybackProvider] player wired — replaying pending control: $pending",
            )
            controlCallback(pending)
        }
    }

    fun connect() {
        if (connected) return
        connected = true
        // Emit initial state if a Player is already active (also replays any
        // control that arrived before the pipeline existed).
        PlayerHolder.player?.let { onPlayerWired(it) }
        startPositionPoller()
        // Offline flag → PlaybackState.isOffline (StateFlow only re-emits on
        // actual change, so this adds no polling overhead). Drives the
        // "offline" banner in Now Playing / PlayerBar.
        offlineJob = pollerScope.launch {
            offlineModeManager.isOffline.collect { offline ->
                if (_playbackState.value.isOffline != offline) {
                    _playbackState.update { it.copy(isOffline = offline) }
                }
            }
        }
    }

    /** Called when the active Player changes (e.g., Cast connect/disconnect). */
    fun onPlayerSwitched() {
        if (!connected) return
        PlayerHolder.player?.let { onPlayerWired(it) }
    }

    private var registeredOn: Player? = null
    private fun registerListener() {
        registeredOn?.removeListener(playerListener)
        registeredOn = PlayerHolder.player
        setPlayerWired(registeredOn != null)
        registeredOn?.addListener(playerListener)
    }

    fun disconnect() {
        connected = false
        setPlayerWired(false)
        pendingControl = null
        positionJob?.cancel()
        positionJob = null
        offlineJob?.cancel()
        offlineJob = null
        registeredOn?.removeListener(playerListener)
        registeredOn = null
    }

    fun setControlCallback(callback: (PlaybackControl) -> Unit) {
        controlCallback = callback
    }

    /** Push non-player state fields back to the provider so all consumers
     *  (notification, tile, composables) see the same canonical state.
     *  Called by PlaybackViewModel when isStarred, sleepTimer, or download status changes. */
    override fun updateExtraState(
        isStarred: Boolean,
        sleepTimerEndMs: Long,
        downloadedTrackIds: Set<String>,
        isDisliked: Boolean,
        trackRating: Int,
    ) {
        _playbackState.update {
            it.copy(
                isStarred = isStarred,
                sleepTimerEndMs = sleepTimerEndMs,
                downloadedTrackIds = downloadedTrackIds,
                isDisliked = isDisliked,
                trackRating = trackRating,
            )
        }
    }

    /**
     * Dispatch a transport control. While a Player is attached this fires the
     * service-side callback immediately (fire-and-forget, matching the prior
     * contract). While the pipeline is down — PlayerHolder.player == null, e.g.
     * the system idle-stopped MediaService — the control is NEVER silently
     * dropped: it is remembered and the host is asked to bring playback back;
     * [onPlayerWired] replays it once a Player is attached.
     */
    private fun dispatchControl(control: PlaybackControl) {
        if (PlayerHolder.player != null) {
            pendingControl = null
            controlCallback(control)
        } else {
            android.util.Log.w(
                "ftpmusic",
                "[PlaybackProvider] no player attached — queueing $control and requesting re-arm",
            )
            pendingControl = control
            onPlaybackUnavailable?.invoke()
        }
    }

    // All controls delegate to callback — MediaService calls Player commands directly
    override fun playPause() = dispatchControl(PlaybackControl.PLAY_PAUSE)
    override fun skipNext() = dispatchControl(PlaybackControl.SKIP_NEXT)
    override fun skipPrev() = dispatchControl(PlaybackControl.SKIP_PREV)
    override fun seekTo(fraction: Float) = dispatchControl(PlaybackControl.SEEK_TO(fraction))
    override fun toggleRepeat() = dispatchControl(PlaybackControl.REPEAT_TOGGLE)
    override fun toggleShuffle() = dispatchControl(PlaybackControl.SHUFFLE_TOGGLE)
    override fun toggleSpeed() = dispatchControl(PlaybackControl.SPEED_TOGGLE)
    override fun setVolume(volume: Float) = dispatchControl(PlaybackControl.SET_VOLUME(volume))
    override fun armSleepTimer(endMs: Long) = dispatchControl(PlaybackControl.SLEEP_TIMER_ARM(endMs))
    override fun cancelSleepTimer() = dispatchControl(PlaybackControl.SLEEP_TIMER_CANCEL)
}

@Suppress("ktlint:standard:class-naming")
sealed class PlaybackControl {
    data object PLAY_PAUSE : PlaybackControl()
    data object SKIP_NEXT : PlaybackControl()
    data object SKIP_PREV : PlaybackControl()
    data class SEEK_TO(val fraction: Float) : PlaybackControl()
    data object REPEAT_TOGGLE : PlaybackControl()
    data object SHUFFLE_TOGGLE : PlaybackControl()
    data object SPEED_TOGGLE : PlaybackControl()
    data class SET_VOLUME(val volume: Float) : PlaybackControl()
    data class SLEEP_TIMER_ARM(val endMs: Long) : PlaybackControl()
    data object SLEEP_TIMER_CANCEL : PlaybackControl()
}
