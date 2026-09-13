package com.lucasdss.ftpmusic.app.playback

import android.app.PendingIntent
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.MediaQueue
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao
import com.lucasdss.ftpmusic.app.data.db.QueueJournalDao
import com.lucasdss.ftpmusic.app.data.db.QueueJournalEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.ScrobbleService
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import com.lucasdss.ftpmusic.app.ui.player.CastButtonState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Media playback service with Cast support.
 * Pattern adapted from Tempo's MediaService.kt
 */

/** Action taken by the error auto-skip guard after a track fails to play. */
internal enum class ErrorSkipAction { SKIP_NEXT, LAST_TRACK_STOP, RETRY_LIMIT_STOP, RADIO_IGNORE }

/**
 * Pure decision logic for [MediaService]'s error auto-skip guard.
 *
 * @param errorCount consecutive errors on the current track (resets on transition)
 * @param currentMediaId media id of the failed track (null when unknown)
 * @param currentIndex current queue index
 * @param mediaItemCount total items in the queue
 */
internal fun decideErrorSkipAction(
    errorCount: Int,
    currentMediaId: String?,
    currentIndex: Int,
    mediaItemCount: Int,
): ErrorSkipAction = when {
    errorCount > 3 -> ErrorSkipAction.RETRY_LIMIT_STOP
    currentMediaId?.startsWith("radio:") == true -> ErrorSkipAction.RADIO_IGNORE
    currentIndex + 1 < mediaItemCount -> ErrorSkipAction.SKIP_NEXT
    else -> ErrorSkipAction.LAST_TRACK_STOP
}

/**
 * User-facing message when a manual Cast connect times out (no session events
 * within the timeout window). First sentence keeps the historical text; the
 * second gives actionable recovery. A connect that produces NO
 * onSessionStarted / onSessionStartFailed / onDeviceInfoChanged(remote=true)
 * means the failure happened inside Google Play Services (e.g. a corrupted
 * Cast dynamite module — logcat shows DynamiteLoaderV2Impl "Module APK has
 * been modified" — or the phone and device on different networks), which is
 * invisible to the app because no SessionManager callback fires. The message
 * must therefore tell the user what to check instead of a bare "try again".
 * Pure function, unit-tested.
 */
internal fun buildCastConnectTimeoutMessage(deviceName: String): String =
    "Couldn't connect to \"$deviceName\". Please try again. " +
        "If it keeps failing, make sure the phone and cast device are on the " +
        "same Wi-Fi, and that Google Play Services is up to date " +
        "(Settings → Apps → Google Play Services → Storage → Clear cache)."

/**
 * Cache key for a Subsonic stream URL — the track id query parameter, falling
 * back to the full URL when absent. Must be identical across ALL cache users
 * (CacheDataSource streaming writes, DownloadManager imports, Cast proxy) so a
 * stream-cached track is a read-cache hit and vice versa.
 */
internal fun streamCacheKey(uriString: String): String {
    val uri = android.net.Uri.parse(uriString)
    return uri.getQueryParameter("id") ?: uriString
}

internal object MediaServiceStartRequest {
    const val ACTION_INITIALIZE = "com.lucasdss.ftpmusic.action.INITIALIZE_PLAYBACK"
    const val ACTION_PLAYBACK = "com.lucasdss.ftpmusic.action.START_PLAYBACK"

    @Volatile
    var foregroundRequested: Boolean = false
}

internal fun shouldPromotePlaybackOnCreate(foregroundRequested: Boolean): Boolean = foregroundRequested

internal fun shouldReassertPlayback(action: String?): Boolean = action == MediaServiceStartRequest.ACTION_PLAYBACK

internal fun playbackServiceStartMode(action: String?): Int =
    if (action == MediaServiceStartRequest.ACTION_INITIALIZE) {
        android.app.Service.START_NOT_STICKY
    } else {
        android.app.Service.START_STICKY
    }

@AndroidEntryPoint
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MediaService : MediaLibraryService() {

    @Inject lateinit var mediaSession: MediaLibraryService.MediaLibrarySession

    @Inject lateinit var playbackProvider: MediaSessionPlaybackProvider

    @Inject lateinit var persistenceManager: QueuePersistenceManager

    @Inject lateinit var playbackManager: PlaybackManager

    @Inject lateinit var scrobbleService: ScrobbleService

    @Inject lateinit var downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager

    @Inject lateinit var castPreferences: CastPreferences

    @Inject lateinit var authHelper: SubsonicAuthHelper

    @Inject lateinit var cacheService: com.lucasdss.ftpmusic.app.data.cache.CacheService

    @Inject lateinit var queueJournalDao: QueueJournalDao

    @Inject lateinit var trackDao: TrackDao

    @Inject lateinit var playbackStateDao: PlaybackStateDao

    @Inject lateinit var secureStorage: com.lucasdss.ftpmusic.app.data.security.SecureStorage

    @Inject lateinit var serverConfigStore: com.lucasdss.ftpmusic.app.di.ServerConfigStore

    /** Cast tier 3 (castFromPhone): LAN proxy serving cached audio to the Cast receiver. */
    @Inject lateinit var playbackProxy: PlaybackProxy

    /** Unified audio cache shared with DownloadManager, CacheService and the Cast proxy. */
    @Inject lateinit var unifiedAudioCache: SimpleCache

    @Inject lateinit var mediaSessionCallback: MediaSessionCallback

    @Inject lateinit var offlineModeManager: com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager

    // Cached CastContext — avoids repeated getSharedInstance() calls
    private val castCtx: CastContext? by lazy {
        try {
            CastContext.getSharedInstance(this)
        } catch (_: Exception) {
            null
        }
    }

    /** Registered MediaQueue.Callback for receiver queue change tracking. */
    private var mediaQueueCallback: MediaQueue.Callback? = null

    /** The receiver's MediaQueue (set after queueLoad). */
    private var castMediaQueue: MediaQueue? = null

    /** Registered RemoteMediaClient.Callback for queue status updates. */
    private var rmcCallback: RemoteMediaClient.Callback? = null

    /** SessionManagerListener handles suspend/resume lifecycle. */
    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            android.util.Log.w("ftpmusic-cast", "[SessionManager] onSessionSuspended: reason=$reason")
            // Session suspended (network drop). Don't clear Cast state —
            // framework will auto-resume. PlayerHolder.isCasting stays true.
            // A suspension is NOT a manual disconnect: clear the stale flag so
            // a later remote=false cleanup can never be skipped by a leftover
            // disconnectingManually=true (phone-playback divergence, Bug C).
            disconnectingManually = false
            notificationProvider?.notifyChanged()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            android.util.Log.w("ftpmusic-cast", "[SessionManager] onSessionResumed: wasSuspended=$wasSuspended")
            sessionWasResumed = true
            // Per Cast SDK docs: onSessionResumed fires when a session is resumed
            // after suspension OR after the application is restarted (process death).
            // The SDK is telling us a session is NOW active — restore Cast state
            // unconditionally. PlayerHolder.isCasting is false after process death
            // and must not block this (that was the chicken-and-egg deadlock).
            PlayerHolder.isCasting = true
            CastButtonState.isCasting.value = true
            val sessionDevice = session.castDevice
            val deviceName = sessionDevice?.friendlyName
            if (deviceName != null) {
                PlayerHolder.castDeviceName = deviceName
                CastButtonState.connectedDeviceName.value = deviceName
            }
            PlayerHolder.pendingCastVolume = null
            PlayerHolder.pendingCastVolumeTimestamp = 0L
            PlayerHolder.seedFromSessionVolume(session.volume)
            // Re-register both RemoteMediaClient and MediaQueue callbacks.
            // On resume, the receiver is the source of truth — sync remote→local.
            val rmc = session.remoteMediaClient
            if (rmc != null) {
                val oldRmc = rmcCallback
                rmcCallback = null
                oldRmc?.let {
                    try {
                        rmc.unregisterCallback(it)
                    } catch (_: Exception) {}
                }
                val newRmcCb = object : RemoteMediaClient.Callback() {
                    override fun onQueueStatusUpdated() {
                        val remoteIds = rmc.mediaQueue?.getItemIds()?.toList() ?: return
                        if (remoteIds.isNotEmpty()) {
                            syncReceiverPosition(rmc)
                        }
                    }
                }
                rmc.registerCallback(newRmcCb)
                rmcCallback = newRmcCb
                // Re-register MediaQueue.Callback
                val oldMq = mediaQueueCallback
                mediaQueueCallback = null
                oldMq?.let {
                    try {
                        castMediaQueue?.unregisterCallback(it)
                    } catch (_: Exception) {}
                }
                val mq = rmc.getMediaQueue()
                castMediaQueue = mq
                val newMqCb = object : MediaQueue.Callback() {
                    override fun itemsUpdatedAtIndexes(indexes: IntArray) {
                        syncReceiverPosition(rmc)
                    }
                    override fun mediaQueueChanged() {}
                    override fun itemsInsertedInRange(start: Int, count: Int) {
                        itemsUpdatedAtIndexes((start until start + count).toList().toIntArray())
                    }
                    override fun itemsRemovedAtIndexes(indexes: IntArray) {}
                    override fun itemsReloaded() {
                        itemsUpdatedAtIndexes((0 until mq.itemCount).toList().toIntArray())
                    }
                }
                mq.registerCallback(newMqCb)
                mediaQueueCallback = newMqCb
                // Sync current position from the receiver — queue stays local.
                if (rmc.mediaQueue?.itemCount ?: 0 > 0) {
                    syncReceiverPosition(rmc)
                }
            }
            // A resume can follow a cleanup that nulled the mirror (FIND-04):
            // re-wire it so the UI never blanks while isCasting=true.
            wireCastStateMirror()
            // Re-seat the primary player to CastPlayer (Bug C). media3 keeps
            // DeviceInfo remote across suspension (session non-null on both
            // sides), so onDeviceInfoChanged(remote=true) never fires on resume
            // — without this, PlayerHolder.player stays the local ExoPlayer from
            // the drop cleanup while isCasting=true: blue icon, but Play starts
            // the phone (observed 2026-08-27 Soundbar).
            attachCastPlayerToSession()
            notificationProvider?.notifyChanged()
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            android.util.Log.w("ftpmusic-cast", "[SessionManager] onSessionResumeFailed: error=$error")
            // Resume failed after suspension — fall back to local playback
            // to avoid being stuck in a broken Cast state.
            if (PlayerHolder.isCasting) {
                handler.post {
                    // M4: never kill an in-flight manual connect — its deferred
                    // route.select + timeout own the recovery. A resume-failure
                    // of the OLD session during a switch must not clear the
                    // connect attempt.
                    if (castConnectInFlight) return@post
                    clearCastConnectAttempt()
                    PlayerHolder.isCasting = false
                    PlayerHolder.castDeviceName = null
                    PlayerHolder.castVolume = 0f
                    PlayerHolder.castDeviceVolume = 0.5f
                    PlayerHolder.pendingCastVolume = null
                    PlayerHolder.pendingCastVolumeTimestamp = 0L
                    PlayerHolder.castDeviceMuted = false
                    CastButtonState.isCasting.value = false
                    CastButtonState.connectedDeviceName.value = null
                    CastButtonState.connectingDeviceName.value = null
                    sessionWasResumed = false
                    exoPlayer?.volume = 1f
                    switchToLocalPlayback()
                    notificationProvider?.notifyChanged()
                }
            }
        }

        override fun onSessionEnding(session: CastSession) {
            // Re-seat the session player to ExoPlayer SYNCHRONOUSLY, before
            // RemoteCastPlayer flushes its shrinking/empty timeline — the
            // MediaSession's PlayerWrapper crashes (checkState currentIndex <
            // windowCount) when the cast timeline shrinks mid-teardown
            // (crash observed 2026-08-21). CastPlayer.onDeviceInfoChanged
            // handles the rest of the cleanup.
            detachCastSessionFromMediaSession()
        }
        override fun onSessionStarting(session: CastSession) {
            // Queue load will happen in onDeviceInfoChanged(remote=true).
            // This is a safety net if that callback doesn't fire.
            android.util.Log.d("ftpmusic-cast", "[SessionManager] onSessionStarting")
        }
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            android.util.Log.d("ftpmusic-cast", "[SessionManager] onSessionStarted: sessionId=$sessionId")
            // A NEW session starts a fresh context — the previous resume flag is
            // stale. Without this reset, isReconnectingToExistingSession() stays
            // true forever after the first resume and every later session skips
            // the queue (re)load, leaving the phone-side timeline empty
            // (EDGE-03).
            sessionWasResumed = false
            // Manual-connect success: cancel the connect timeout/in-flight lock so
            // the UI never hangs on "Connecting…" (Bug B).
            clearCastConnectAttempt()
            // Re-seat the MediaSession to CastPlayer (Bug C). onDeviceInfoChanged
            // (remote=true) usually fires right after, but attaching here closes
            // the window where a session is live yet the player is still local.
            attachCastPlayerToSession()
            // Ensure queue is loaded if onDeviceInfoChanged hasn't done it yet.
            // The receiver may already have a queue from a previous session.
            // NOTE: no queue-load is scheduled here — setupRemoteCastSession
            // (via onDeviceInfoChanged(remote=true) → setupRemoteCastSessionWithRetry)
            // is the canonical load with its own retry + reconnect guards. An
            // additional 300L post here caused a DOUBLE queueLoad ~200ms apart
            // (audible restart, L11); the PLAY_PAUSE idle self-heal remains the
            // fallback if remote=true is ever missed.
            // Resumed/new session → the receiver-state mirror must be active
            // even if onDeviceInfoChanged's setup is still retrying.
            wireCastStateMirror()
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            android.util.Log.w("ftpmusic-cast", "[SessionManager] onSessionStartFailed: error=$error")
            // A failed start definitively ends the connect attempt. NO isCasting
            // guard (M5): during a device switch isCasting is still true, and a
            // start-failure there must clean up instead of deferring to the 10s
            // timeout (which then leaves a stuck cast state).
            failCastConnect(
                deviceName = CastButtonState.connectingDeviceName.value ?: "device",
                message = "Cast connection failed (start error $error)",
            )
            CastButtonState.showDialog.value = false
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {
            android.util.Log.d("ftpmusic-cast", "[SessionManager] onSessionResuming: sessionId=$sessionId")
            // UI will show "Casting to [device]" while the session reconnects —
            // PlayerHolder.isCasting stays true, no UI flicker.
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            android.util.Log.w("ftpmusic-cast", "[SessionManager] onSessionEnded: error=$error")
            // Safety net (see onSessionEnding): never let the MediaSession
            // process a shrinking cast timeline.
            detachCastSessionFromMediaSession()
            // Edge-01 (2026-08-27 review): a device switch tears down the OLD
            // session BEFORE selecting the new route. That teardown reaches
            // here — clearing the connect attempt would kill the deferred
            // route.select(B) (its in-flight guard) and the reconnect retry
            // would silently re-select the OLD device. When a switch is
            // pending, keep the connect attempt alive and skip the retry.
            val switchPending = castSwitchInProgress
            if (!switchPending) {
                // A session end terminates any in-flight manual connect (Bug B).
                clearCastConnectAttempt()
                // Cache the device for the reconnect retry before it's gone.
                if (session.castDevice != null) castLastDevice = session.castDevice
            }
            // Session is gone — a future onSessionStarted is a FRESH session and
            // must re-load the queue (sticky-resume fix, EDGE-03).
            sessionWasResumed = false
            // Clean up immediately — gen guard prevents cross-contamination with new sessions
            if (PlayerHolder.isCasting) {
                val endGen = ++castDisconnectGen
                handler.post {
                    if (endGen != castDisconnectGen) {
                        android.util.Log.w(
                            "ftpmusic-cast",
                            "[SessionManager] onSessionEnded: new session started, skipping cleanup",
                        )
                        return@post
                    }
                    if (switchPending) {
                        // The deferred route.select() in connectToCastDevice will
                        // establish the new session — never reconnect to the old
                        // device or force local playback here.
                        android.util.Log.w(
                            "ftpmusic-cast",
                            "[SessionManager] onSessionEnded: device switch pending — leaving connect to route.select",
                        )
                        return@post
                    }
                    // Transient errors (2155 TIMEOUT, 2161 APP_NOT_RUNNING): the
                    // receiver may be reachable again shortly. Retry the last device
                    // within a 10s window (up to 3 attempts, ~3.3s apart) instead of
                    // immediately falling back to local playback. A successful retry
                    // fires onDeviceInfoChanged(remote=true) which restores Cast state.
                    if (attemptCastReconnect(endGen)) return@post
                    android.util.Log.w("ftpmusic-cast", "[SessionManager] onSessionEnded: forcing local playback")
                    PlayerHolder.isCasting = false
                    PlayerHolder.castDeviceName = null
                    PlayerHolder.castVolume = 0f
                    PlayerHolder.castDeviceVolume = 0.5f
                    PlayerHolder.pendingCastVolume = null
                    PlayerHolder.pendingCastVolumeTimestamp = 0L
                    PlayerHolder.castDeviceMuted = false
                    CastButtonState.isCasting.value = false
                    CastButtonState.connectedDeviceName.value = null
                    CastButtonState.connectingDeviceName.value = null
                    exoPlayer?.volume = 1f
                    switchToLocalPlayback()
                    notificationProvider?.notifyChanged()
                }
            }
        }
    }

    /** Local ExoPlayer. Typed as [Player] — only the Player API is used on it
     *  (also keeps it mockable in unit tests). */
    private var exoPlayer: Player? = null
    private var exoPlayerCache: SimpleCache? = null
    internal val audioCache: SimpleCache? get() = exoPlayerCache

    /** Primary cast player (media3 CastPlayer wrapping ExoPlayer + RemoteCastPlayer).
     *  Typed as [Player] because only the Player API is used on it — also keeps it
     *  mockable in unit tests. */
    private var castPlayer: Player? = null

    /** media3 RemoteCastPlayer inside [castPlayer]. Its `isCastSessionAvailable()`
     *  decides whether CastPlayer commands actually route to the receiver: while
     *  false, CastPlayerImpl forwards setMediaItems to the LOCAL ExoPlayer
     *  (device-switch window, early onSessionStarted) and a "cast" queue load
     *  would silently land on the phone. Used by loadFullQueueToReceiver and
     *  ClearAndPlay to pick setMediaItems vs raw queueLoad. */
    private var remoteCastPlayer: androidx.media3.cast.RemoteCastPlayer? = null
    private var lastTrackId: String? = null
    private var playerErrorCount = 0
    private var hasLoadedContinuation = false

    /** Serializes fast-forward seeks so overlapping seeks can't interleave with a transition. */
    private val seekCoalescer = SeekCoalescer()

    /** Apply a (coalesced) seek to the current track — no clamp (end-of-track skip is intentional). */
    private fun performSeek(player: androidx.media3.common.Player, fraction: Float) {
        val dur = player.duration
        if (dur > 0) {
            val ms = (dur * fraction).toLong()
            if (player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                player.seekTo(ms)
            }
        }
    }
    private var disconnectingManually = false

    @Volatile private var castProxyStarted = false

    @Volatile private var castDisconnectGen = 0

    /** Reconnect retry: attempts remaining within the 10s window after a session
     *  ends with a transient error. Reset on successful connect / manual disconnect. */
    @Volatile private var castReconnectAttempts = 0

    /** Device the retry loop re-selects when the session died transiently. */
    @Volatile private var castLastDevice: com.google.android.gms.cast.CastDevice? = null

    /** Last Cast deviceId that owned a remote session — used to force queue reload on A→B. */
    @Volatile private var castSessionDeviceId: String? = null

    /** True while a manual connect (route.select) is awaiting session establishment.
     *  Serializes connect requests so a double-tap can't start two teardowns. */
    @Volatile private var castConnectInFlight = false

    /** Absolute deadline (System.currentTimeMillis) of the active connect attempt; 0 = none.
     *  Guards the "Connecting…" hang when route.select() is a no-op (GMS SessionManager
     *  still owns a desynced/phantom session — observed 2026-08-27 Soundbar). */
    @Volatile private var castConnectDeadlineMs = 0L

    /** How long a manual connect may take before it is failed and reset. */
    @VisibleForTesting
    internal val castConnectTimeoutMs = 10_000L

    /** True while a connect/switch is tearing down the OLD session before
     *  selecting the NEW route. In that window, onSessionEnded of the old
     *  session must NOT clear the connect attempt, cache the old device for
     *  reconnect, or run the reconnect retry — the deferred route.select()
     *  establishes the new session (Edge-01, 2026-08-27 review). */
    @Volatile private var castSwitchInProgress = false

    /** deviceId of the device the active manual connect is targeting. The
     *  connect timeout aborts only when NO CONNECTED session exists on this
     *  target — a surviving session on another device (failed switch, GMS
     *  endCurrentSession no-op) must not suppress the timeout (M5). */
    @Volatile private var castConnectTargetDeviceId: String? = null

    /** Monotonic token for the active connect attempt. Captured by the
     *  deferred 800ms route.select and compared at execution: a cancelled or
     *  superseded attempt's select is invalidated by bumping the token — the
     *  deadline-capture equivalent for the deferred select (M7). */
    @Volatile private var castConnectSeq = 0

    /** True when the current track has played past 60% of its duration.
     *  Set by positionPoller, consumed by onMediaItemTransition. */
    @Volatile private var hasPassed60Percent = false

    /** True once startForeground succeeded — lets onStartCommand retry it. */
    @Volatile private var foregroundStarted = false

    /** Last real media notification from Media3's provider callback — used to
     *  re-assert foreground in [onStartCommand] without flashing the
     *  placeholder ("Ready to play") over the actual track. */
    @Volatile private var lastForegroundNotification: android.app.Notification? = null

    /**
     * Minimal pre-init notification that satisfies the 5s startForeground
     * deadline while MediaService.onCreate still runs its heavy setup. The real
     * media notification replaces it once playback state is available.
     */
    @VisibleForTesting
    internal fun buildPlaceholderNotification(context: android.content.Context): android.app.Notification =
        android.app.Notification.Builder(context, PlaybackNotificationProvider.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ftpmusic")
            .setContentText("Ready to play")
            .setPriority(android.app.Notification.PRIORITY_LOW)
            .build()
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Process-lifetime best-effort persistence scope. NEVER cancelled in
     *  onDestroy: teardown persistence (playback state + queue) drains here so
     *  the Room writes never park the main thread while the system idle-stops
     *  the service (09-07 freeze wedge). */
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal var notificationProvider: PlaybackNotificationProvider? = null // accessible for state-triggered updates

    /** Service-owned sleep timer job. Owned here (not the ViewModel) so the
     *  pause fires even after a recents-swipe (service survives onTaskRemoved)
     *  and is re-armed from persisted state after process death. */
    private var sleepTimerJob: Job? = null

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var audioFocusRequest: AudioFocusRequest? = null

    /** Max retries for remote-session setup when remoteMediaClient is momentarily null. */
    @Suppress("ktlint:standard:property-naming", "VariableNaming")
    private val MAX_CAST_SETUP_RETRIES = 5

    private val playerListener: Player.Listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // Bug-A guard: never let MediaSessionImpl observe CastPlayer's empty
            // timeline. PlayerWrapper.createPositionInfo crashes with
            // checkState(currentIndex < windowCount) when RemoteCastPlayer flushes
            // an empty timeline during session teardown while the MediaSession is
            // still attached (process deaths 2026-08-24/08-27). This listener is
            // registered on CastPlayer BEFORE MediaSession's own listener (both go
            // through CastPlayerImpl's ListenerSet in registration order), so
            // re-seating the MediaSession here preempts the crash dispatch.
            if (shouldDetachEmptyTimeline(
                    mediaSession.player,
                    castPlayer,
                    castPlayer?.mediaItemCount ?: 0,
                    hasLiveCastSession(),
                )
            ) {
                android.util.Log.w(
                    "ftpmusic-cast",
                    "[CastPlayer] empty timeline with no session while attached — detaching MediaSession from CastPlayer",
                )
                detachCastSessionFromMediaSession()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            android.util.Log.d(
                "ftpmusic-playback",
                "[DEBUG-aud] isPlaying=$isPlaying player=${PlayerHolder.player} casting=${PlayerHolder.isCasting}",
            )
            notificationProvider?.notifyChanged()
            if (isPlaying) {
                handler.post(positionPoller)
                val currentId = lastTrackId
                if (currentId != null) {
                    val meta = PlayerHolder.player?.currentMediaItem?.mediaMetadata
                    val extras = meta?.extras
                    scope.launch {
                        scrobbleService.nowPlaying(
                            currentId,
                            meta?.title?.toString(),
                            meta?.artist?.toString(),
                            extras?.getString("albumId"),
                            extras?.getString("artistId"),
                            (
                                extras?.getLong("duration")
                                    ?: 0L
                                ).toInt(),
                            meta?.artworkUri?.lastPathSegment,
                        )
                    }
                }
            } else {
                handler.removeCallbacks(positionPoller)
                saveQueueState()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // CastPlayer handles Cast track loading automatically via RemoteCastPlayer
            // Capture previous track metadata NOW — currentMediaItem is still the old track at this point
            val previousMeta = PlayerHolder.player?.currentMediaItem?.mediaMetadata
            // Scrobble the previous track when playback reaches ≥60% duration.
            // Auto-advance naturally exceeds this threshold; manual skip after
            // 60% is also captured via the hasPassed60Percent flag set by the
            // position poller.
            val previousTrackId = lastTrackId
            lastTrackId = mediaItem?.mediaId
            playerErrorCount = 0 // Reset error counter on new track
            if (previousTrackId != null && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || hasPassed60Percent)) {
                val extras = previousMeta?.extras
                scope.launch {
                    scrobbleService.scrobble(
                        previousTrackId,
                        previousMeta?.title?.toString(),
                        previousMeta?.artist?.toString(),
                        extras?.getString("albumId"),
                        extras?.getString("artistId"),
                        (
                            extras?.getLong("duration")
                                ?: 0L
                            ).toInt(),
                        previousMeta?.artworkUri?.lastPathSegment,
                    )
                }
            }
            hasPassed60Percent = false // reset for the new track

            // Persist queue + enqueue upcoming tracks for download (local-first:
            // the whole queue is progressively cached, next 3 urgent).
            // Only touch ExoPlayer's queue during local playback — CastPlayer items are transient
            if (!PlayerHolder.isCasting) {
                val player = PlayerHolder.player
                if (player != null && player.mediaItemCount > 0) {
                    val (tracks, urls) = playbackManager.buildQueueState()
                    playbackManager.enqueuePlayQueue(tracks, urls, player.currentMediaItemIndex)
                }
            }

            if (mediaItem != null && mediaItem.mediaId != null) {
                val metadata = mediaItem.mediaMetadata
                val player = PlayerHolder.player
                android.util.Log.w(
                    "ftpmusic",
                    "[MediaService] onMediaItemTransition: title=${metadata.title} artist=${metadata.artist} album=${metadata.albumTitle} idx=${player?.currentMediaItemIndex} count=${player?.mediaItemCount}",
                )
                if (playbackManager.isUrlSwapInProgress) {
                    // URL-only swap — don't reset position or metadata, just update track index
                    // idx is read from Player directly by PlaybackState.fromPlayer
                } else {
                    hasLoadedContinuation = false
                }
                // Apply replay gain if available (prefer track, fall back to album)
                val extras = mediaItem.mediaMetadata.extras
                val trackGain = extras?.getFloat("replaygain_track_gain", 0f)?.takeIf { it != 0f }
                val albumGain = extras?.getFloat("replaygain_album_gain", 0f)?.takeIf { it != 0f }
                if (trackGain != null || albumGain != null) {
                    ReplayGainUtil.applyReplayGain(PlayerHolder.player, trackGain, albumGain)
                }

                // DownloadManager caches tracks on play-queue enqueue — no proxy involved in local playback
                android.util.Log.d(
                    "ftpmusic-playback",
                    "[DEBUG-aud] mediaItem.uri=${mediaItem.localConfiguration?.uri} player=${PlayerHolder.player}",
                )

                // Continuous play: journal-based smart track selection
                if (player != null) {
                    // Reset continuation flag when a new track starts (mediaId changed)
                    if (mediaItem.mediaId != previousTrackId) {
                        hasLoadedContinuation = false
                    }
                    if (!PlayerHolder.isCasting &&
                        player.currentMediaItemIndex >= player.mediaItemCount - 1 && !hasLoadedContinuation &&
                        playbackManager.continuousPlayEnabled
                    ) {
                        hasLoadedContinuation = true
                        scope.launch {
                            try {
                                val entries = queueJournalDao.getAllRecent()
                                if (entries.isNotEmpty()) {
                                    val currentTrackIds = (0 until player.mediaItemCount).mapNotNull {
                                        player.getMediaItemAt(it)?.mediaId
                                    }.toSet()

                                    val selected = JournalTrackSelector.select(entries, currentTrackIds)

                                    selected.forEach { trackId ->
                                        try {
                                            val entity = trackDao.getTrack(trackId)
                                            if (entity == null) {
                                                android.util.Log.d(
                                                    "ftpmusic-playback",
                                                    "Continuous play: track $trackId not in local DB, skipping",
                                                )
                                                return@forEach
                                            }
                                            val track = Track(
                                                id = entity.id,
                                                title = entity.title,
                                                artist = entity.artist,
                                                album = null,
                                                artistId = entity.artistId,
                                                albumId = entity.albumId,
                                                duration = entity.durationSeconds,
                                                coverArt = entity.coverArtUrl,
                                            )
                                            val url = SubsonicAuthHelper().buildStreamUrl(
                                                DynamicBaseUrl.url,
                                                track.id,
                                                SubsonicCredentials.username,
                                                SubsonicCredentials.password,
                                            )
                                            // Continuous play is a continuation of the current mix —
                                            // append to CONTEXT (not user-added priority) so the
                                            // context/priority split stays correct after disconnect.
                                            playbackManager.appendToContext(listOf(track), listOf(url))
                                        } catch (_: Exception) {}
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Lazy-load more tracks when approaching end of window.
                // Read from ExoPlayer (full queue), not active player (windowed during Cast).
                // CRITICAL: never run during Cast — the full queue is already on the
                // receiver, and during the Cast→local switch onMediaItemTransition fires
                // repeatedly with a truncated ExoPlayer queue, making shouldLoadMore()
                // true each time. Each firing APPENDS a chunk to the dual-queue PRIORITY
                // queue (never cleared by the context restore in switchToLocalPlayback),
                // so the merged queue grew by 50 tracks in a real session (113 → 163).
                val totalLoaded = PlayerHolder.exoPlayer?.mediaItemCount ?: 0
                val currentIndex = PlayerHolder.player?.currentMediaItemIndex ?: 0
                if (!PlayerHolder.isCasting && QueueAutoLoader.shouldLoadMore(currentIndex, totalLoaded)) {
                    scope.launch(Dispatchers.Main) {
                        val saved = persistenceManager.restore()
                        if (saved != null && saved.tracks.size > totalLoaded) {
                            // Dedup gate: the continuous-play coroutine (launched earlier in
                            // this same transition) may have appended journal-selected tracks
                            // BEFORE we snapshot the queue here, so the persisted queue now
                            // contains them while `totalLoaded` was captured before the
                            // append. Re-read the live player queue and skip tracks already
                            // present, otherwise the chunk re-appends the same tracks
                            // (duplicated queue growth).
                            val playerQueue = PlayerHolder.exoPlayer
                            val existingIds = if (playerQueue != null) {
                                (0 until playerQueue.mediaItemCount).mapNotNull {
                                    playerQueue.getMediaItemAt(it)?.mediaId
                                }.toSet()
                            } else {
                                emptySet()
                            }
                            val nextChunk = saved.tracks.drop(totalLoaded)
                                .zip(saved.urls.drop(totalLoaded))
                                .filter { (track, _) -> track.id !in existingIds }
                                .take(QueueAutoLoader.CHUNK_SIZE)
                            if (nextChunk.isNotEmpty()) {
                                playbackManager.appendToContext(
                                    nextChunk.map { it.first },
                                    nextChunk.map { it.second },
                                )
                            }
                        }
                    }
                }
            } else {
                // Suppress clear during transient null transitions (e.g., Cast setup).
                // Real queue-empty is handled via saveQueueState + player state elsewhere.
                android.util.Log.d("ftpmusic", "[MediaService] onMediaItemTransition: null item suppressed")
            }
            // Cast: receiver handles auto-advance with full queue loaded.
            // No sliding-window management needed — the receiver has all items.
            // Local-first: persist queue to DB immediately, sync to server async
            saveQueueState()
            // Rebuild the QS/notification metadata on EVERY track change, including
            // gapless auto-advance (onIsPlayingChanged doesn't fire there, so the
            // notification previously froze on the old track). Deferred via
            // handler.post: onMediaItemTransition fires while currentMediaItem
            // still points at the old track, and buildNotification re-derives
            // fresh state from the player — both need the transition to settle.
            // Guarded on mediaItem != null to avoid flashing the placeholder
            // during Cast's transient null transitions.
            if (mediaItem != null) {
                handler.post { notificationProvider?.notifyChanged() }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // Position is read from Player directly by MediaSessionPlaybackProvider.
            // The seek coalescer treats a discontinuity as the settle point: apply
            // the next coalesced seek (if any) so rapid fast-forwards serialize.
            val next = seekCoalescer.onSettled()
            if (next != null) {
                val p = PlayerHolder.player ?: return
                performSeek(p, next)
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            // Repeat mode is read from Player directly by MediaSessionPlaybackProvider
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            // Shuffle mode is read from Player directly by MediaSessionPlaybackProvider
        }

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
            val isRemote = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
            android.util.Log.w("ftpmusic-cast", "[CastPlayer] onDeviceInfoChanged: remote=$isRemote")
            if (isRemote) {
                // Successful (re)connect — stop the reconnect retry loop and cache the device.
                castReconnectAttempts = 0
                val sessionDevice = castCtx?.sessionManager?.currentCastSession?.castDevice
                sessionDevice?.let { castLastDevice = it }
                // DeviceId change (soundbar → Mini): force full queue reload.
                noteCastDeviceForQueuePolicy(sessionDevice?.deviceId)
                // Skip phantom remote=true — authenticated by session existence, not UI state.
                // After process death, connectingDeviceName/connectedDeviceName are reset to
                // null, but the Cast SDK may auto-reconnect (ReconnectionService). In that
                // case currentCastSession is non-null and the event is REAL, not phantom.
                // Derive the device name from the session when UI state was wiped.
                if (CastButtonState.connectingDeviceName.value == null &&
                    CastButtonState.connectedDeviceName.value == null &&
                    sessionDevice == null
                ) {
                    android.util.Log.w("ftpmusic-cast", "[CastPlayer] Ignoring phantom remote=true (no session)")
                    return
                }
                // A REAL remote session is here — invalidate any pending disconnect
                // cleanup (stale onDeviceInfoChanged(remote=false) from a device switch).
                // NOTE: the gen bump happens AFTER the phantom check, so a phantom event
                // can no longer cancel a legitimate pending cleanup.
                castDisconnectGen++
                // Manual-connect success (Bug B): a real remote session means the
                // connect completed — release the in-flight lock + timeout so the
                // UI never hangs on "Connecting…".
                clearCastConnectAttempt()
                // A genuine remote session is NOT a manual disconnect — clear the
                // stale flag so a later remote=false cleanup is never skipped
                // (Bug C phone-playback divergence).
                disconnectingManually = false
                // Promote connecting device name to connected when session establishes.
                // Fall back to the actual session device (auto-reconnect after process death).
                val deviceName = CastButtonState.connectingDeviceName.value
                    ?: CastButtonState.connectedDeviceName.value
                    ?: sessionDevice?.friendlyName
                    ?: "Cast Device"
                if (CastButtonState.connectingDeviceName.value != null) {
                    CastButtonState.connectedDeviceName.value = deviceName
                }
                CastButtonState.connectingDeviceName.value = null
                CastButtonState.showDialog.value = false
                PlayerHolder.isCasting = true
                PlayerHolder.castDeviceName = deviceName
                CastButtonState.isCasting.value = true
                // Cast tier 3: start LAN proxy so the receiver can stream cached audio from the phone
                if (castPreferences.castFromPhone) {
                    // LAN proxy: the Cast receiver streams cached files from the
                    // phone — explicit user opt-in (castFromPhone setting).
                    playbackProxy.start(listenOnAllInterfaces = true)
                    castProxyStarted = true
                }
                // ExoPlayer muted during Cast — Cast receiver provides audio
                exoPlayer?.volume = 0f
                // Switch primary player to CastPlayer for remote control
                attachCastPlayerToSession()
                // CastPlayer may still be in STATE_IDLE after switching to remote.
                // Force prepare so play/pause commands work during Cast.
                handler.postDelayed({ castPlayer?.prepare() }, 200L)
                // Load full queue to receiver + register receiver callbacks, retrying
                // when the session's remoteMediaClient is momentarily unavailable.
                setupRemoteCastSessionWithRetry()
                notificationProvider?.notifyChanged()
            } else {
                // Bug-A guard: detach the MediaSession from CastPlayer
                // SYNCHRONOUSLY. The deferred cleanup below runs 300ms later,
                // by which time RemoteCastPlayer has already flushed its
                // shrinking/empty timeline — crashing MediaSessionImpl's
                // PlayerWrapper if still attached (process deaths 08-24/08-27).
                // Guarded on a live session so a device-switch A→B (session B
                // already connected) keeps CastPlayer attached for B's setup.
                if (mediaSession.player === castPlayer && !hasLiveCastSession()) {
                    detachCastSessionFromMediaSession()
                }
                // Unregister Cast receiver callbacks
                mediaQueueCallback?.let { cb ->
                    try {
                        castMediaQueue?.unregisterCallback(cb)
                    } catch (_: Exception) {}
                }
                mediaQueueCallback = null
                castMediaQueue = null
                rmcCallback?.let { cb ->
                    try {
                        castCtx?.sessionManager?.currentCastSession?.remoteMediaClient
                            ?.unregisterCallback(cb)
                    } catch (_: Exception) {}
                }
                rmcCallback = null
                // Handle asynchronously — CastPlayer may still be transitioning
                val disconnectGen = ++castDisconnectGen
                handler.postDelayed({
                    // If a new Cast session started while this cleanup was pending,
                    // skip entirely — the new session's setup already handles state.
                    // Prevents stale cleanup from overwriting PlayerHolder/Player
                    // during a device switch (e.g., A→B within 300ms).
                    if (disconnectGen != castDisconnectGen) return@postDelayed

                    // Edge-02 (2026-08-27 review): during an in-flight connect/
                    // switch the old session's remote=false cleanup must NOT run a
                    // full local teardown in the gap before the new session
                    // establishes — the deferred route.select(B) owns the state.
                    if (castConnectInFlight) {
                        android.util.Log.w(
                            "ftpmusic-cast",
                            "[CastPlayer] remote=false during in-flight connect — skipping deferred cleanup",
                        )
                        return@postDelayed
                    }

                    if (castProxyStarted) {
                        playbackProxy.stop()
                        castProxyStarted = false
                    }
                    clearCastMirror()
                    if (disconnectingManually) {
                        disconnectingManually = false
                        notificationProvider?.notifyChanged()
                        return@postDelayed
                    }
                    PlayerHolder.isCasting = false
                    PlayerHolder.castDeviceName = null
                    PlayerHolder.castVolume = 0f
                    PlayerHolder.castDeviceVolume = 0.5f
                    PlayerHolder.pendingCastVolume = null
                    PlayerHolder.pendingCastVolumeTimestamp = 0L
                    PlayerHolder.castDeviceMuted = false
                    CastButtonState.isCasting.value = false
                    CastButtonState.connectedDeviceName.value = null
                    CastButtonState.connectingDeviceName.value = null
                    exoPlayer?.volume = 1f
                    // Switch to local playback
                    switchToLocalPlayback()
                    // Restore position from persistence
                    scope.launch {
                        kotlinx.coroutines.delay(500)
                        try {
                            if (PlayerHolder.isCasting) return@launch
                            val saved = persistenceManager.restore()
                            if (saved != null) {
                                val idx = saved.currentIndex.coerceIn(0, maxOf(0, (exoPlayer?.mediaItemCount ?: 1) - 1))
                                exoPlayer?.seekTo(idx, saved.positionMs)
                                android.util.Log.d(
                                    "ftpmusic-cast",
                                    "[MediaService] Restored position idx=$idx pos=${saved.positionMs}",
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ftpmusic-cast", "[MediaService] Position restore failed: ${e.message}")
                        }
                    }
                    notificationProvider?.notifyChanged()
                }, 300L) // wait for CastPlayer internal transition
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.e("MediaService", "Playback error: ${error.message}", error)
            val ep = exoPlayer
            // Edge 40: recovery must not require STATE_IDLE — an error arriving
            // while the player is mid-transition (buffering) previously skipped
            // ALL recovery and left the player stuck in the error state. The
            // playerError null-check alone identifies a genuine failure.
            if (ep == null || ep.playerError == null) return
            playerErrorCount++
            val currentId = ep.currentMediaItem?.mediaId
            when (decideErrorSkipAction(playerErrorCount, currentId, ep.currentMediaItemIndex, ep.mediaItemCount)) {
                ErrorSkipAction.SKIP_NEXT -> {
                    // Remove possibly corrupt cached content so the next play of
                    // this track re-fetches (span index stays consistent via SimpleCache).
                    if (!currentId.isNullOrEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                cacheService.removeCached(currentId)
                            } catch (_: Exception) {}
                        }
                    }
                    android.util.Log.w("ftpmusic", "[MediaService] Auto-advancing past failed track: $currentId")
                    ep.seekToNextMediaItem()
                    // seekToNextMediaItem() positions the next source but does NOT
                    // resume playback: with playWhenReady=false (audio-focus loss,
                    // pause-before-error) the player would sit in STATE_IDLE forever
                    // — Now Playing / QuickSettings show "stopped". media3's internal
                    // error-advance policy would have prepared + played, which the
                    // old retry loop suppressed; restore that behavior explicitly.
                    ep.playWhenReady = true
                    ep.prepare()
                }

                ErrorSkipAction.LAST_TRACK_STOP -> {
                    android.util.Log.w("ftpmusic", "[MediaService] Last track failed ($playerErrorCount) — stopping")
                    playerErrorCount = 0
                    ep.stop()
                }

                ErrorSkipAction.RETRY_LIMIT_STOP -> {
                    android.util.Log.e("ftpmusic", "[MediaService] Too many errors ($playerErrorCount) — stopping")
                    playerErrorCount = 0
                    ep.stop()
                }

                ErrorSkipAction.RADIO_IGNORE -> {
                    // Radio streams — no cached file, no queue position to advance.
                    // Leave the player as-is; the radio listener owns its lifecycle.
                    android.util.Log.w("ftpmusic", "[MediaService] Radio stream error — not retrying")
                    playerErrorCount = 0
                }
            }
        }
    }

    private val positionPoller = object : Runnable {
        private var pollCount = 0
        override fun run() {
            // CastPlayer handles Cast progress and auto-advance internally via RemoteCastPlayer
            val player = PlayerHolder.player ?: run {
                handler.postDelayed(this, 500L)
                return
            }
            if (player.isPlaying) {
                // Track 60% playback threshold for scrobble eligibility.
                // Position is read every 200ms; once crossed, the flag persists
                // until the next track transition.
                if (!hasPassed60Percent && player.duration > 0 &&
                    player.currentPosition >= player.duration * 0.6f
                ) {
                    hasPassed60Percent = true
                }
                pollCount++
                // Sender-driven advance (timer path, <=800ms tail cut): check every
                // poll (200ms) so the 800ms window is never missed. The cheap
                // duration/position guards in preloadNextIfNeeded make this free
                // until the last second of the track. The event path
                // (onStatusUpdated IDLE+FINISHED) is the zero-cut primary.
                if (PlayerHolder.isCasting) {
                    preloadNextIfNeeded(player.currentPosition, player.duration)
                }
                if (pollCount % 25 == 0) {
                    // During Cast, ExoPlayer's index is frozen at Cast start — read the
                    // CastPlayer index (tracks the receiver's actual current track).
                    // Locally, ExoPlayer is the source of truth.
                    val idx = if (PlayerHolder.isCasting) {
                        PlayerHolder.player?.currentMediaItemIndex
                    } else {
                        PlayerHolder.exoPlayer?.currentMediaItemIndex ?: return
                    }
                    val pos = player.currentPosition
                    // Persist position every 5s (25 × 200ms)
                    scope.launch {
                        persistenceManager.savePositionOnly(idx ?: 0, pos)
                    }
                }
            }
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreate() {
        try {
            // The placeholder notification targets ftpmusic_playback — the
            // channel MUST exist before the first startForeground or the system
            // rejects it ("Bad notification", kills the process on fresh
            // install). Create it first; the provider's init is idempotent.
            PlaybackNotificationProvider.ensureChannel(this)
            // PlaybackManager marks foreground starts before calling
            // startForegroundService. Promote before heavy player/Cast setup to
            // satisfy the five-second deadline. Plain app initialization stays
            // background and does not show an idle playback notification.
            if (shouldPromotePlaybackOnCreate(MediaServiceStartRequest.foregroundRequested)) {
                try {
                    startForeground(
                        PlaybackNotificationProvider.NOTIFICATION_ID,
                        buildPlaceholderNotification(this),
                    )
                    foregroundStarted = true
                } finally {
                    MediaServiceStartRequest.foregroundRequested = false
                }
            }

            // Restore credentials AFTER super.onCreate() — Hilt injects @Inject
            // fields inside super.onCreate(), so touching them before it throws
            // UninitializedPropertyAccessException (previously swallowed here,
            // making this restore path a silent no-op).
            super.onCreate()
            try {
                serverConfigStore.initialize(force = true)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-network", "[MediaService] config restore failed: ${e.message}")
            }
            android.util.Log.w("ftpmusic", "[MediaService] super.onCreate OK, building ExoPlayer...")

            // Unified SimpleCache (DI singleton) — same directory CacheService uses,
            // shared with DownloadManager and the Cast proxy. Size is user-configurable
            // via the AdjustableCacheEvictor (Settings → Audio cache size).
            val simpleCache = unifiedAudioCache
            exoPlayerCache = simpleCache
            // Streaming auto-cache: the write sink is ENABLED so every track played is
            // persisted to SimpleCache as it streams (Rolling Cache design). The
            // DownloadManager handles the tracks AHEAD of the current one (priority 0);
            // CacheDataSink writes the CURRENT track in parallel with playback. The two
            // never target the same track simultaneously (DownloadManager starts at
            // currentIndex+1), so CacheService.importIntoCache's removeResource() cannot
            // destroy spans while CacheDataSink is writing them. 1MB fragments give
            // fine-grained LRU eviction granularity for Opus tracks (3-5MB each).
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(
                    OfflineAwareHttpDataSourceFactory(
                        offlineModeManager,
                        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
                    ),
                )
                .setCacheWriteDataSinkFactory(
                    CacheDataSink.Factory().setCache(simpleCache).setFragmentSize(1024L * 1024L),
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .setCacheKeyFactory { dataSpec -> streamCacheKey(dataSpec.uri.toString()) }

            exoPlayer = ExoPlayer.Builder(this)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(cacheDataSourceFactory),
                )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
            android.util.Log.w("ftpmusic", "[MediaService] ExoPlayer built OK")

            // Wire CastPlayer for seamless local↔remote playback.
            val castContext = castCtx
            if (castContext != null) {
                val converter = SubsonicMediaItemConverter(castPreferences)
                val remotePlayer = RemoteCastPlayer.Builder(this)
                    .setMediaItemConverter(converter)
                    .setSeekBackIncrementMs(10_000L)
                    .setSeekForwardIncrementMs(10_000L)
                    .setMaxSeekToPreviousPositionMs(30_000L)
                    .build()
                remoteCastPlayer = remotePlayer
                castPlayer = CastPlayer.Builder(this)
                    .setLocalPlayer(exoPlayer!!)
                    .setRemotePlayer(remotePlayer)
                    // media3 1.10.1's TransferCallback.DEFAULT is a plain
                    // PlayerTransferState.setToPlayer() with NO null-URI filtering.
                    // A null-URI MediaItem (MediaItem.EMPTY produced by
                    // CastTimelineTracker for the current item when the in-memory
                    // contentId map is empty — e.g. after process death/reinstall
                    // reconnect) crashes DefaultMediaSourceFactory.checkNotNull
                    // during the Cast→ExoPlayer transfer. This mirrors the upstream
                    // DefaultCastPlayerTransferCallback filter (added after 1.10.1).
                    .setTransferCallback { sourcePlayer, targetPlayer ->
                        CastTransferFilter.transferFilteredState(sourcePlayer, targetPlayer)
                    }
                    .build()
                castPlayer!!.addListener(playerListener)
                android.util.Log.w("ftpmusic-cast", "[MediaService] CastPlayer created as primary player")
            } else {
                android.util.Log.w("ftpmusic-cast", "[MediaService] No CastContext — local-only playback")
            }

            // Register SessionManagerListener for suspend/resume lifecycle.
            // This handles network drops (automatic session recovery) and
            // re-syncs volume/queue state on session resume.
            castCtx?.sessionManager?.addSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java,
            )

            // CastPlayer forwards ExoPlayer callbacks when local, RemoteCastPlayer when remote.
            // Only register on CastPlayer — ExoPlayer listener would cause duplicate events.
            // ExoPlayer listener removed — CastPlayer is the single listener source.
            // Attach the LOCAL player by default. CastPlayer must never be the
            // MediaSession's player while no Cast session exists: RemoteCastPlayer
            // flushes a shrinking/empty timeline when a (possibly stale/phantom)
            // session tears down, and MediaSessionImpl's PlayerWrapper crashes on
            // it with checkState(currentIndex < windowCount) — process death
            // observed 2026-08-27, 08-24 (media3 1.10.1; fixed upstream in 1.11).
            // A live session re-seats CastPlayer via onSessionResumed /
            // onSessionStarted / onDeviceInfoChanged(remote=true), all of which
            // call attachCastPlayerToSession().
            val sessionPlayer: Player = exoPlayer!!
            val diPlaceholderPlayer = mediaSession.player
            mediaSession.player = sessionPlayer
            PlayerHolder.player = sessionPlayer
            PlayerHolder.exoPlayer = exoPlayer
            (diPlaceholderPlayer as? ExoPlayer)?.release()

            // Process-death auto-reconnect seeding (Edge-04): the Cast SDK's
            // ReconnectionService can re-establish a session before/without
            // onSessionResumed reaching us. If a session already exists at startup,
            // restore Cast state so the UI reflects the receiver (and Disconnect is
            // available) instead of showing a phone-bound player for an active cast.
            try {
                val existing = castCtx?.sessionManager?.currentCastSession
                if (existing != null && existing.isConnected) {
                    android.util.Log.w(
                        "ftpmusic-cast",
                        "[MediaService] seeding Cast state from existing session at startup (${existing.castDevice?.friendlyName})",
                    )
                    PlayerHolder.isCasting = true
                    CastButtonState.isCasting.value = true
                    existing.castDevice?.friendlyName?.let {
                        PlayerHolder.castDeviceName = it
                        CastButtonState.connectedDeviceName.value = it
                    }
                    PlayerHolder.seedFromSessionVolume(existing.volume)
                    attachCastPlayerToSession()
                    wireCastStateMirror()
                }
            } catch (_: Exception) {}

            // Wire bi-directional Cast queue sync: local queue changes → receiver
            playbackManager.castQueueListener = { action ->
                syncLocalToRemote(action)
            }

            // Wire UI control callbacks to actual Player commands
            playbackProvider.setControlCallback { control ->
                handlePlaybackControl(control)
            }

            val provider = PlaybackNotificationProvider(
                this,
                authHelper,
                { playbackProvider.playbackState.value },
                {
                    secureStorage.get(
                        com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_PLAYBACK_NOTIFICATIONS,
                    )?.toBooleanStrictOrNull()
                        ?: true
                },
            )
            notificationProvider = provider
            setMediaNotificationProvider(provider)

            // Connect Player listener so UI reads Player state directly
            playbackProvider.connect()

            // Register disconnect callback — update notification immediately on user-initiated disconnect
            CastButtonState.onDisconnectRequested = {
                disconnectingManually = true // prevent duplicate switchToLocalPlayback from onDeviceInfoChanged
                sessionWasResumed = false // force queue load on next connect
                castReconnectAttempts = 0 // manual disconnect stops any retry loop
                castLastDevice = null
                // 1. Pause the Cast receiver before tearing down, so the remote
                //    device stops playback immediately rather than drifting.
                try {
                    castCtx?.sessionManager?.currentCastSession?.remoteMediaClient
                        ?.pause(null)
                } catch (_: Exception) {}
                // 2. Save current Cast position for local handoff
                //    (read before clearing state, then save for switchToLocalPlayback).
                //    Use full-queue index — CastPlayer index is relative to the
                //    receiver's trimmed window (soundbar DMR) and would seek to
                //    the wrong / older track after restore.
                try {
                    val cp = PlayerHolder.player // still CastPlayer at this point
                    if (cp != null && cp.playbackState != Player.STATE_IDLE) {
                        // Map against full Exo mirror — CastPlayer only holds the
                        // trimmed receiver window after soundbar DMR advances.
                        val idx = resolveCurrentIndexForPersistence(PlayerHolder.exoPlayer ?: cp)
                        val pos = cp.currentPosition
                        kotlinx.coroutines.runBlocking {
                            persistenceManager.savePositionOnly(idx, pos)
                        }
                    }
                } catch (_: Exception) {}
                castSessionDeviceId = null
                // 3. Unregister Cast receiver callbacks before disconnecting
                mediaQueueCallback?.let { cb ->
                    try {
                        castMediaQueue?.unregisterCallback(cb)
                    } catch (_: Exception) {}
                }
                mediaQueueCallback = null
                castMediaQueue = null
                rmcCallback?.let { cb ->
                    try {
                        castCtx?.sessionManager?.currentCastSession?.remoteMediaClient
                            ?.unregisterCallback(cb)
                    } catch (_: Exception) {}
                }
                rmcCallback = null
                PlayerHolder.isCasting = false
                PlayerHolder.castDeviceName = null
                PlayerHolder.castVolume = 0f
                PlayerHolder.castDeviceVolume = 0.5f
                PlayerHolder.pendingCastVolume = null
                PlayerHolder.pendingCastVolumeTimestamp = 0L
                PlayerHolder.castDeviceMuted = false
                CastButtonState.isCasting.value = false
                CastButtonState.connectedDeviceName.value = null
                exoPlayer?.volume = 1f
                // 4. Switch to local playback (reads position we just saved)
                switchToLocalPlayback()
                // 5. Tell the Cast SDK to tear down the session so its SessionManager
                //    state is clean. Without this, SessionManager keeps the old session
                //    alive internally and subsequent route.select() calls are no-ops,
                //    preventing the user from reconnecting to the same device.
                endCurrentCastSession()
                notificationProvider?.notifyChanged()
            }

            // Device-switch flow (A→B): end the old session + deselect its route so
            // route.select(B) creates a FRESH connection. No state clearing — the
            // new session's onDeviceInfoChanged(remote=true) restores Cast state.
            CastButtonState.onEndSessionForSwitchRequested = {
                endCurrentCastSession()
            }

            // Manual connect flow (Bug B): MediaService owns route selection so
            // connects are serialized against session teardown and guarded by a
            // timeout — the UI never hangs on "Connecting…" after a disconnect.
            CastButtonState.onConnectRequested = { device ->
                connectToCastDevice(device)
            }

            // Picker Cancel/dismiss while a connect is pending (Bug B).
            CastButtonState.onConnectCancelled = {
                cancelCastConnect()
            }

            // scrobbleFlushJob removed with pending_scrobbles table (dead code)

            // Initialize the notification provider so MediaStyle's setMediaSession()
            // links the notification to the platform MediaSession for lock screen controls.
            // This must be called to set up lastSession/lastActionFactory/lastCallback.
            val compatButtonList: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton> =
                com.google.common.collect.ImmutableList.of()
            provider.createNotification(
                mediaSession,
                compatButtonList,
                object : androidx.media3.session.MediaNotification.ActionFactory {
                    // E9: every action the APP builds (warm-up + notifyChanged
                    // posts) must carry a LIVE PendingIntent. The media3-driven
                    // invocations use media3's own DefaultActionFactory (session
                    // command intents); this factory covers the app-driven path
                    // with MEDIA_ACTION broadcasts routed to MediaActionReceiver.
                    // Null intents here made lock-screen/shade buttons dead until
                    // media3's next update replaced the notification.
                    override fun createMediaAction(
                        s: MediaSession,
                        icon: androidx.core.graphics.drawable.IconCompat,
                        name: CharSequence,
                        code: Int,
                    ) = androidx.core.app.NotificationCompat.Action.Builder(
                        icon,
                        name,
                        createMediaActionPendingIntent(s, code),
                    ).build()
                    override fun createCustomAction(
                        s: MediaSession,
                        icon: androidx.core.graphics.drawable.IconCompat,
                        name: CharSequence,
                        action: String,
                        extras: android.os.Bundle,
                    ) = androidx.core.app.NotificationCompat.Action.Builder(
                        icon,
                        name,
                        createMediaActionPendingIntent(s, Player.COMMAND_PLAY_PAUSE),
                    ).build()
                    override fun createCustomActionFromCustomCommandButton(
                        s: MediaSession,
                        button: androidx.media3.session.CommandButton,
                    ) = androidx.core.app.NotificationCompat.Action.Builder(
                        androidx.core.graphics.drawable.IconCompat.createWithResource(
                            this@MediaService,
                            android.R.drawable.ic_media_play,
                        ),
                        button.displayName,
                        createMediaActionPendingIntent(s, Player.COMMAND_PLAY_PAUSE),
                    ).build()
                    override fun createMediaActionPendingIntent(s: MediaSession, commandCode: Int) =
                        PendingIntent.getBroadcast(
                            this@MediaService,
                            commandCode,
                            Intent("com.lucasdss.ftpmusic.app.MEDIA_ACTION").apply { putExtra("command", commandCode) },
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                },
                object : androidx.media3.session.MediaNotification.Provider.Callback {
                    override fun onNotificationChanged(notification: MediaNotification) {
                        // Cache the REAL media notification so a later foreground
                        // re-assert (onStartCommand after system FGS demotion) can
                        // re-promote with the actual track UI instead of the
                        // placeholder ("Ready to play").
                        lastForegroundNotification = notification.notification
                        startForeground(PlaybackNotificationProvider.NOTIFICATION_ID, notification.notification)
                    }
                },
            )
            notificationProvider?.notifyChanged()

            // Auto-restore saved queue on service start (without auto-playing)
            // Must wait for proxy to be ready — otherwise ExoPlayer gets Connection refused
            scope.launch {
                // 1. Restore Now Playing state from persisted playback_state immediately
                //    so notification, lock screen, and mini player show the last track
                //    even before the queue finishes loading.
                val persisted = playbackStateDao.get()
                if (persisted != null) {
                    // Re-arm the service-owned sleep timer from persisted state
                    // (process death / service restart). armSleepTimer also clears
                    // an already-expired deadline.
                    armSleepTimer(persisted.sleepTimerEndMs)
                    notificationProvider?.notifyChanged()
                }

                // Only restore if queue is empty — don't overwrite user's current selection
                val shouldRestore = withContext(Dispatchers.Main) {
                    val p = PlayerHolder.player
                    (p == null || p.mediaItemCount == 0) && !PlayerHolder.isCasting
                }
                if (!shouldRestore) {
                    android.util.Log.w("ftpmusic", "[MediaService] Queue already has items — skipping restore")
                    return@launch
                }
                val saved = persistenceManager.restore() ?: return@launch
                withContext(Dispatchers.Main) {
                    // Double-check: user might have played something while we loaded from DB
                    val p = PlayerHolder.player
                    if (p != null && p.mediaItemCount > 0) {
                        android.util.Log.w("ftpmusic", "[MediaService] Queue populated during restore load — skipping")
                        return@withContext
                    }
                    // E5: a lock screen / notification tap during the DB load sets
                    // playWhenReady on the (still empty) player. Capture it BEFORE
                    // playAlbum (which itself sets playWhenReady=true) so the
                    // restore-then-pause below never swallows the user's intent.
                    val userInitiatedPlayback = p?.playWhenReady == true
                    playbackManager.restoreQueue(
                        saved.tracks,
                        saved.urls,
                        saved.currentIndex,
                        saved.contextSize,
                        saved.isPriorityFlags,
                        saved.entryIds,
                        saved.nextEntryId,
                    )
                    p?.seekTo(saved.currentIndex, saved.positionMs)
                    if (!userInitiatedPlayback) {
                        p?.pause()
                    } else {
                        android.util.Log.w(
                            "ftpmusic",
                            "[MediaService] User requested playback during restore — leaving playing",
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ftpmusic", "[MediaService] onCreate FAILED: ${e.message}", e)
            // Never survive half-wired: a running service with PlayerHolder.player
            // unset makes every transport control a silent no-op until restart
            // (09-07 incident). Tear down cleanly; MainActivity's resume/control
            // self-heal re-starts us.
            try {
                stopSelf()
            } catch (_: Exception) {}
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        // The service is exported (required for Android Auto / Bluetooth /
        // external controllers). Media3 routes commands through the session
        // callback; log unknown packages for diagnostics without blocking —
        // media apps must accept system controllers even when untrusted.
        if (!controllerInfo.isTrusted) {
            android.util.Log.d(
                "ftpmusic",
                "[MediaService] Session controller: pkg=${controllerInfo.packageName} trusted=false",
            )
        }
        return (mediaSession as MediaLibrarySession)
    }

    override fun onDestroy() {
        performDestroyCleanup()
        super.onDestroy()
    }

    /**
     * All destroy-time cleanup except `super.onDestroy()` (which requires a
     * real Looper — not available in plain-JVM tests). Kept internal so the
     * mirror-leak fix and cast-state resets are directly unit-testable.
     */
    @VisibleForTesting
    internal fun performDestroyCleanup() {
        // Persist state BEFORE any cleanup. State is snapshotted synchronously
        // here; the Room writes drain async on persistenceScope (never parking
        // main — an idle-stop teardown blocking main on Room was the 09-07
        // freeze wedge).
        persistPlaybackState()
        saveQueueStateSync()

        // Release the cast-state mirror closure held by the @Singleton provider
        // (a stale closure would leak this service and keep polling a released
        // player after recreation — FIND-03) and reset cast state.
        clearCastMirror()
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        PlayerHolder.castVolume = 0f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.pendingCastVolumeTimestamp = 0L
        PlayerHolder.castDeviceMuted = false
        // Cancel pending handler callbacks (deferred cast setup, reconnect
        // retries) so none run against this destroyed instance.
        handler.removeCallbacksAndMessages(null)

        lastTrackId = null
        PlayerHolder.player = null
        // Unregister Cast receiver callbacks
        mediaQueueCallback?.let { cb ->
            try {
                castMediaQueue?.unregisterCallback(cb)
            } catch (_: Exception) {}
        }
        mediaQueueCallback = null
        castMediaQueue = null
        rmcCallback?.let { cb ->
            try {
                castCtx?.sessionManager?.currentCastSession?.remoteMediaClient
                    ?.unregisterCallback(cb)
            } catch (_: Exception) {}
        }
        rmcCallback = null
        // Unregister SessionManagerListener to prevent leaks
        try {
            castCtx?.sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        } catch (
            _: Exception,
        ) {}
        // Clear the callback lambdas to release the MediaService reference
        CastButtonState.onDisconnectRequested = null
        CastButtonState.onEndSessionForSwitchRequested = null
        CastButtonState.onConnectRequested = null
        CastButtonState.onConnectCancelled = null
        scrobbleService.dispose()
        playbackManager.destroy()
        playbackProvider.disconnect()
        notificationProvider?.dispose()
        try {
            castPlayer?.removeListener(playerListener)
        } catch (_: Exception) {}
        castPlayer?.release()
        castPlayer = null
        remoteCastPlayer = null
        playbackProxy.stop()
        castProxyStarted = false
        // Do NOT release the cache — it's an app-wide singleton shared with
        // DownloadManager/CacheService and must survive service restarts.
        exoPlayerCache = null
        // De-zombie the singleton MediaSession: it is process-scoped (DI) and
        // outlives this service, so leaving it wired to the released ExoPlayer
        // below keeps a zombie session registered — frozen PlaybackState, dead
        // media-button routing, stale dumpsys owner — until process death
        // (09-07 incident). Re-seat an idle player so any straggler command
        // no-ops instead of touching a released player. The next onCreate swaps
        // in the real player and releases this placeholder.
        try {
            val idlePlayer = ExoPlayer.Builder(this).build()
            mediaSession.player = idlePlayer
        } catch (_: Exception) {}
        exoPlayer?.release()
        exoPlayer = null
        mediaSessionCallback.destroy()
        scope.cancel()
        // NOTE: no super.onDestroy() here — onDestroy() invokes it after this
        // cleanup returns (this method stays plain-JVM testable, and calling
        // super twice from the real lifecycle path is wrong).
    }

    /** Last-chance persist when user swipes app from recents (OS may skip onDestroy).
     *  Bounded sync (<= lastChancePersistBoundMs each) — the process may be killed
     *  right after this returns. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        persistPlaybackState(waitBoundedMs = lastChancePersistBoundMs)
        saveQueueStateSync(waitBoundedMs = lastChancePersistBoundMs)
        // NOTE: Do NOT remove the SessionManagerListener here. On Android 14+ the
        // foreground service survives task removal — removing the listener would
        // kill Cast session lifecycle handling (network-drop recovery, session-end
        // cleanup) while the service keeps running. onDestroy handles real teardown.
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (shouldReassertPlayback(intent?.action)) {
            MediaServiceStartRequest.foregroundRequested = false
            reassertForegroundIfNeeded()
        }
        // Handle MEDIA_PLAY_FROM_SEARCH (Google Assistant / Gemini voice commands)
        if (intent?.action == "android.media.action.MEDIA_PLAY_FROM_SEARCH") {
            val query = intent.getStringExtra(android.app.SearchManager.QUERY)
            if (!query.isNullOrBlank()) {
                android.util.Log.d("ftpmusic", "[MediaService] MEDIA_PLAY_FROM_SEARCH: '$query'")
                launchSearchAndPlay(query)
            }
        }
        return playbackServiceStartMode(intent?.action)
    }

    /**
     * (Re)promote the foreground state on a start command. Returns true when
     * startForeground was issued. Uses the cached real media notification when
     * one exists — never flashes the placeholder over the actual track.
     * Unconditional: covers system FGS demotion (Android 14+ media-session
     * inactivity timeout) where the one-shot flag is stale.
     */
    @VisibleForTesting
    internal fun reassertForegroundIfNeeded(): Boolean = try {
        val notification = lastForegroundNotification
            ?: buildPlaceholderNotification(this)
        startForegroundInternal(PlaybackNotificationProvider.NOTIFICATION_ID, notification)
        foregroundStarted = true
        android.util.Log.d("ftpmusic", "[MediaService] startForeground re-asserted")
        true
    } catch (e: Exception) {
        // Background-start restriction (API 31+) or transient failure — the
        // next start command retries; the activity's onResume path re-arms.
        android.util.Log.w("ftpmusic", "[MediaService] startForeground re-assert failed: ${e.message}")
        false
    }

    /**
     * Indirection over the final [android.app.Service.startForeground] so the
     * re-assert wiring is unit-testable (mockk cannot intercept the final
     * framework method on Robolectric's android.jar classes).
     */
    @VisibleForTesting
    internal fun startForegroundInternal(id: Int, notification: android.app.Notification) {
        startForeground(id, notification)
    }

    private fun launchSearchAndPlay(query: String) {
        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    mediaSessionCallback.searchAndNavidromeExpand(query)
                }
                if (items.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val player = PlayerHolder.player
                        if (player == null) {
                            // E14: the service is still mid-create (player not
                            // attached yet). Retry once shortly after; if still
                            // null the Assistant command is dropped rather than
                            // silently lost on a race.
                            delay(1500)
                            val retried = PlayerHolder.player
                            if (retried == null) {
                                android.util.Log.w(
                                    "ftpmusic",
                                    "[MediaService] Search dropped: player still null after retry",
                                )
                                return@withContext
                            }
                            playSearchItems(retried, items)
                        } else {
                            playSearchItems(player, items)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[MediaService] Search failed: $query — ${e.message}")
            }
        }
    }

    private fun playSearchItems(player: Player, items: List<MediaItem>) {
        player.stop()
        player.clearMediaItems()
        player.setMediaItems(items)
        player.prepare()
        player.play()
        android.util.Log.d("ftpmusic", "[MediaService] Playing ${items.size} items for search query")
    }

    internal object QueueAutoLoader {
        /** Load more tracks when within this many items of the end of the window. */
        const val LOAD_AHEAD_THRESHOLD = 2

        /** Number of tracks to load per chunk from database. */
        const val CHUNK_SIZE = 5

        fun shouldLoadMore(currentIndex: Int, totalLoaded: Int): Boolean = totalLoaded > LOAD_AHEAD_THRESHOLD &&
            currentIndex >= totalLoaded - LOAD_AHEAD_THRESHOLD
    }

    /** Bounded main-thread wait for the last-chance persistence on the user
     *  swipe-away path (process may die right after onTaskRemoved). */
    private val lastChancePersistBoundMs = 1500L

    /** Persist current playback state. The snapshot is built synchronously; the
     *  Room write never parks the caller's thread (09-07 freeze: main was
     *  blocked in runBlocking while the system idle-stopped the service). With
     *  [waitBoundedMs] set the write blocks up to that long — used ONLY on
     *  [onTaskRemoved], where the process may be killed immediately after. */
    private fun persistPlaybackState(waitBoundedMs: Long? = null) {
        try {
            val player = PlayerHolder.player ?: return
            val meta = player.currentMediaItem?.mediaMetadata
            val extras = meta?.extras
            val snapshot = PersistedPlaybackState(
                trackId = player.currentMediaItem?.mediaId,
                title = meta?.title?.toString(),
                artist = meta?.artist?.toString(),
                album = meta?.albumTitle?.toString(),
                albumId = extras?.getString("albumId"),
                artistId = extras?.getString("artistId"),
                coverArtId = extractCoverArtId(meta),
                durationMs = player.duration,
                positionMs = player.currentPosition,
                isPlaying = player.isPlaying,
                isCasting = PlayerHolder.isCasting,
                castDeviceName = PlayerHolder.castDeviceName,
                repeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
                sleepTimerEndMs = PlayerHolder.sleepTimerEndMs,
                updatedAt = System.currentTimeMillis(),
            )
            if (waitBoundedMs != null) {
                runBlocking {
                    withTimeoutOrNull(waitBoundedMs) {
                        playbackStateDao.put(snapshot)
                    }
                }
            } else {
                persistenceScope.launch {
                    try {
                        playbackStateDao.put(snapshot)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    /** Guard against duplicate switchToLocalPlayback calls. */
    private var switchingToLocal = false

    /** Max retry attempts within the 10s reconnect window. */
    @VisibleForTesting
    internal val maxCastReconnectAttempts = 3

    /** Delay between reconnect retry attempts (10s window / 3 attempts). */
    @VisibleForTesting
    internal val castReconnectDelayMs = 3333L

    /**
     * Attempt to re-select the last Cast device's MediaRouter route after a
     * transient session end. Returns true when a retry was scheduled (caller
     * should NOT fall back to local playback yet). Returns false when attempts
     * are exhausted or no device/route is available — caller falls back.
     *
     * Guarded by [endGen]: if a NEW session started while we retried (user
     * picked another device), the retry must stop immediately.
     */
    @VisibleForTesting
    internal fun attemptCastReconnect(endGen: Int): Boolean {
        if (castReconnectAttempts >= maxCastReconnectAttempts) return false
        val device = castLastDevice ?: return false
        // Phantom-resume guard (Bug D): if the Cast SDK already re-established a
        // session for the SAME device (GMS auto-reconnect racing our teardown —
        // "Ignoring session creation result for unknown request", observed
        // 2026-08-27), the retry is redundant and route.select() would no-op.
        // Let the resumed session's events restore Cast state instead.
        val live = try {
            castCtx?.sessionManager?.currentCastSession
        } catch (_: Exception) {
            null
        }
        if (live?.castDevice?.deviceId == device.deviceId) {
            android.util.Log.w(
                "ftpmusic-cast",
                "[Cast] reconnect retry skipped — session already live for ${device.friendlyName}",
            )
            return false
        }
        if (live != null) {
            // Desynced session on ANOTHER device — tear it down first so
            // route.select() below is not treated as a no-op.
            android.util.Log.w(
                "ftpmusic-cast",
                "[Cast] reconnect retry: ending desynced session on ${live.castDevice?.friendlyName} before retry",
            )
            endCurrentCastSession()
        }
        castReconnectAttempts++
        android.util.Log.w(
            "ftpmusic-cast",
            "[Cast] reconnect attempt $castReconnectAttempts/$maxCastReconnectAttempts for ${device.friendlyName}",
        )
        handler.postDelayed({
            if (endGen != castDisconnectGen) {
                android.util.Log.w("ftpmusic-cast", "[Cast] reconnect retry cancelled (new session)")
                return@postDelayed
            }
            // Re-select the last device's MediaRouter route. Selecting a route
            // for a device with an active receiver session re-establishes the
            // Cast session (same fast path as manual connect).
            selectCastRoute(device) {
                android.util.Log.w(
                    "ftpmusic-cast",
                    "[Cast] reconnect retry: route not found for ${device.friendlyName}",
                )
                // Try again later within the window, or give up if exhausted.
                if (castReconnectAttempts < maxCastReconnectAttempts) {
                    attemptCastReconnect(endGen)
                }
            }
        }, castReconnectDelayMs)
        return true
    }

    /**
     * Tear down the Cast SDK session + deselect the MediaRouter route WITHOUT
     * clearing app state. Used by the device-switch flow (A→B): the old session
     * must be ended and its route deselected so route.select(B) creates a fresh
     * connection; the new session's onDeviceInfoChanged(remote=true) will set
     * the Cast state. The full manual-disconnect path (state clearing +
     * switchToLocalPlayback) stays in onDisconnectRequested.
     */
    @VisibleForTesting
    internal fun endCurrentCastSession() {
        // Tell the Cast SDK to tear down the session so its SessionManager
        // state is clean. Without this, SessionManager keeps the old session
        // alive internally and subsequent route.select() calls are no-ops,
        // preventing the user from reconnecting to the same device.
        try {
            castCtx?.sessionManager?.endCurrentSession(true)
        } catch (_: Exception) {}
        // Deselect the MediaRouter route so the next route.select() creates a
        // fresh connection. Without this, the route stays "selected" even after
        // the session is torn down, and route.select() is treated as a no-op
        // by the Cast SDK — causing the UI to hang on "Connecting…" indefinitely.
        try {
            val router = MediaRouter.getInstance(this)
            router.selectRoute(router.defaultRoute)
        } catch (_: Exception) {}
    }

    /**
     * Re-seat the primary player + MediaSession to CastPlayer. The single
     * attachment point for a live Cast session (Bug C): called from
     * onSessionStarted, onSessionResumed and onDeviceInfoChanged(remote=true).
     * Idempotent — repeated attachment is harmless.
     */
    private fun attachCastPlayerToSession() {
        castPlayer?.let {
            PlayerHolder.player = it
            mediaSession.player = it
            setListenerPlayer(it)
            playbackProvider.onPlayerSwitched()
        }
    }

    /**
     * Serialized manual connect (Bug B). MediaService owns route selection so
     * the connect is ordered against session teardown:
     *
     * 1. If the Cast SDK SessionManager still owns a session (stale/desynced
     *    phantom after a drop — observed 2026-08-27), end it FIRST and defer
     *    route.select() — otherwise select() is a no-op and the UI hangs on
     *    "Connecting…" forever.
     * 2. A connect timeout fails the attempt (clear UI, end any session) so a
     *    dead route can never leave the picker stuck.
     *
     * Success is signaled by onSessionStarted / onDeviceInfoChanged(remote=true),
     * which call [clearCastConnectAttempt].
     */
    @VisibleForTesting
    internal fun connectToCastDevice(device: CastDevice) {
        if (shouldIgnoreConnectRequest(castConnectInFlight)) {
            // M3 supersede: a second tap re-targets the pending attempt to the
            // NEWEST device (name, timeout target, deferred select) instead of
            // just renaming the picker — otherwise the first device gets
            // selected while the picker shows the second.
            android.util.Log.w(
                "ftpmusic-cast",
                "[Cast] connect in flight — superseding target to ${device.friendlyName}",
            )
            castConnectTargetDeviceId = device.deviceId
            CastButtonState.connectingDeviceName.value = device.friendlyName
            // Invalidate the previous attempt's deferred select + timeout chain.
            val attemptSeq = ++castConnectSeq
            castConnectDeadlineMs = 0L
            startCastConnectTimeout(device.friendlyName)
            handler.postDelayed({
                if (castConnectSeq != attemptSeq || !castConnectInFlight) return@postDelayed
                selectCastRoute(device) {
                    failCastConnect(device.friendlyName, "Device \"${device.friendlyName}\" is no longer available")
                }
            }, 800L)
            return
        }
        castConnectInFlight = true
        castConnectTargetDeviceId = device.deviceId
        val attemptSeq = castConnectSeq
        CastButtonState.connectingDeviceName.value = device.friendlyName
        val current = try {
            castCtx?.sessionManager?.currentCastSession
        } catch (_: Exception) {
            null
        }
        if (current != null) {
            if (current.castDevice?.deviceId == device.deviceId) {
                // Edge-05: a HEALTHY session on the same device is already the
                // target — never tear down live playback. Only a stale/phantom
                // session (no live media) needs the teardown-reconnect path.
                val healthy = try {
                    current.remoteMediaClient != null && current.remoteMediaClient?.mediaStatus != null
                } catch (_: Exception) {
                    false
                }
                if (healthy) {
                    android.util.Log.w(
                        "ftpmusic-cast",
                        "[Cast] already connected to ${device.friendlyName} with a live session — clearing connecting state",
                    )
                    castConnectInFlight = false
                    castSwitchInProgress = false
                    castConnectSeq++
                    castConnectDeadlineMs = 0L
                    CastButtonState.connectingDeviceName.value = null
                    return
                }
                // Same device, stale/phantom session — force a clean teardown so
                // route.select() starts a FRESH connection.
                android.util.Log.w(
                    "ftpmusic-cast",
                    "[Cast] connecting to same device ${device.friendlyName} — ending stale session first",
                )
                castSwitchInProgress = true
                endCurrentCastSession()
            } else {
                // Device switch A→B — end old session + deselect route without
                // clearing app state (the new session restores it).
                android.util.Log.w(
                    "ftpmusic-cast",
                    "[Cast] device switch to ${device.friendlyName} — ending session on ${current.castDevice?.friendlyName} first",
                )
                castSwitchInProgress = true
                sessionWasResumed = false // never skip queueLoad on A→B
                CastButtonState.endSessionForSwitch()
            }
            handler.postDelayed({
                // M7/Edge-01: never select a route for a connect that was
                // cancelled, superseded or reset during the teardown wait.
                if (castConnectSeq != attemptSeq || !castConnectInFlight) return@postDelayed
                selectCastRoute(device) {
                    failCastConnect(device.friendlyName, "Device \"${device.friendlyName}\" is no longer available")
                }
            }, 800L)
        } else {
            selectCastRoute(device) {
                failCastConnect(device.friendlyName, "Device \"${device.friendlyName}\" is no longer available")
            }
        }
        startCastConnectTimeout(device.friendlyName)
    }

    /** Find the MediaRouter route for [device] and select it. [onFailure] runs
     *  when no matching route exists or selection throws. */
    private fun selectCastRoute(device: CastDevice, onFailure: (String) -> Unit) {
        try {
            val router = MediaRouter.getInstance(this)
            var found = false
            for (route in router.routes) {
                val castDevice = com.google.android.gms.cast.CastDevice.getFromBundle(route.extras)
                if (castDevice?.deviceId == device.deviceId) {
                    route.select()
                    found = true
                    android.util.Log.w(
                        "ftpmusic-cast",
                        "[Cast] route.select() on '${route.name}' for ${device.friendlyName}",
                    )
                    break
                }
            }
            if (!found) onFailure("route_not_found")
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-cast", "[Cast] route.select failed for ${device.friendlyName}: ${e.message}")
            onFailure("exception")
        }
    }

    /** Arm the connect timeout. Fires only when no CONNECTED session exists on
     *  the [targetDeviceId] (M5): an old session surviving a failed switch on
     *  another device must not suppress the timeout. The deadline is captured
     *  per attempt so a stale timeout callback can never fail a newer one; a 1s
     *  grace re-check keeps a slow-but-establishing session from failing
     *  prematurely (Edge-03). */
    private fun startCastConnectTimeout(deviceName: String, targetDeviceId: String? = castConnectTargetDeviceId) {
        val deadline = System.currentTimeMillis() + castConnectTimeoutMs
        castConnectDeadlineMs = deadline
        handler.postDelayed({
            if (castConnectDeadlineMs != deadline) return@postDelayed
            if (!shouldAbortConnectTimeout(hasLiveCastSessionOn(targetDeviceId))) return@postDelayed
            // Grace period: the session may be mid-establishment (slow receiver,
            // proxy start). Re-check once before failing.
            handler.postDelayed({
                if (castConnectDeadlineMs != deadline) return@postDelayed
                castConnectDeadlineMs = 0L
                if (!shouldAbortConnectTimeout(hasLiveCastSessionOn(targetDeviceId))) return@postDelayed
                fireConnectTimeout(deviceName)
            }, 1000L)
        }, castConnectTimeoutMs)
    }

    /**
     * Fire the connect-timeout recovery: log the diagnostic (DynamiteModule /
     * DynamiteLoaderV2Impl hint) and surface the actionable message. Extracted
     * from the [startCastConnectTimeout] lambda so the failure path is directly
     * unit-testable. Runs on the main handler; once per failed connect only.
     */
    @VisibleForTesting
    internal fun fireConnectTimeout(deviceName: String) {
        android.util.Log.w(
            "ftpmusic-cast",
            "[Cast] connect timed out for $deviceName — no session events within ${castConnectTimeoutMs}ms. " +
                "Check logcat for DynamiteModule/DynamiteLoaderV2Impl errors (corrupted GMS Cast module) " +
                "or network isolation (phone/device not on the same Wi-Fi).",
        )
        failCastConnect(deviceName, buildCastConnectTimeoutMessage(deviceName))
    }

    /**
     * Fail the active manual connect and recover deterministically (H1):
     * clears all connect state, surfaces [message], and — when nothing is
     * actually casting — tears down to local playback. When a DIFFERENT
     * session survived the failed attempt (GMS endCurrentSession no-op during
     * a switch), that session's cast state is kept: the blue icon stays
     * accurate instead of pointing at a dead session.
     */
    @VisibleForTesting
    internal fun failCastConnect(deviceName: String, message: String) {
        castConnectInFlight = false
        castSwitchInProgress = false
        castConnectDeadlineMs = 0L
        castConnectSeq++
        CastButtonState.connectingDeviceName.value = null
        CastButtonState.castErrorMessage.value = message
        val liveDevice = liveCastSessionDeviceId()
        if (liveDevice == null) {
            // Nothing is casting — a failed switch left stale cast state (its
            // onSessionEnded cleanup was skipped by the switch-pending guard).
            // Close it: end any desynced session, force local playback.
            endCurrentCastSession()
            forceLocalPlaybackAfterCastFailure()
        } else {
            android.util.Log.w(
                "ftpmusic-cast",
                "[Cast] connect failed but session on $liveDevice survived — keeping cast state",
            )
        }
    }

    /** User cancelled a pending connect (picker Cancel/dismiss). Deterministic
     *  recovery (H2): if the cancelled attempt was tearing down an OLD session
     *  for a switch, that teardown's cleanup was skipped — restore local
     *  playback instead of leaving a stuck cast state. */
    @VisibleForTesting
    internal fun cancelCastConnect() {
        if (!castConnectInFlight && castConnectDeadlineMs == 0L) return
        android.util.Log.d("ftpmusic-cast", "[Cast] connect cancelled by user")
        val wasSwitch = castSwitchInProgress
        castConnectInFlight = false
        castSwitchInProgress = false
        castConnectDeadlineMs = 0L
        castConnectSeq++
        CastButtonState.connectingDeviceName.value = null
        if (wasSwitch) {
            forceLocalPlaybackAfterCastFailure()
        }
    }

    /** Connect completed or session ended — release the in-flight lock + timeout.
     *  Also clears any stale error dialog (Edge-03): a success after a previous
     *  failure must not leave "Couldn't connect" over a working session. */
    @VisibleForTesting
    internal fun clearCastConnectAttempt() {
        castConnectInFlight = false
        castSwitchInProgress = false
        castConnectDeadlineMs = 0L
        castConnectSeq++
        CastButtonState.castErrorMessage.value = null
    }

    /**
     * Deterministic teardown to local playback after a failed cast attempt.
     * Guards on isCasting so a fresh-connect failure (never casting) is a
     * no-op. [switchToLocalPlayback] is idempotent (re-entrancy guard) and
     * re-seats PlayerHolder.player + mediaSession.player to ExoPlayer.
     */
    private fun forceLocalPlaybackAfterCastFailure() {
        if (!PlayerHolder.isCasting) return
        android.util.Log.w("ftpmusic-cast", "[Cast] forcing local playback after failed cast attempt")
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        PlayerHolder.castVolume = 0f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.pendingCastVolumeTimestamp = 0L
        PlayerHolder.castDeviceMuted = false
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null
        exoPlayer?.volume = 1f
        switchToLocalPlayback()
        notificationProvider?.notifyChanged()
    }

    private fun switchToLocalPlayback() {
        if (switchingToLocal) return // idempotent guard
        switchingToLocal = true
        try {
            val ep = exoPlayer ?: return
            setListenerPlayer(ep)
            clearCastMirror()
            PlayerHolder.player = ep
            mediaSession.player = ep
            playbackProvider.onPlayerSwitched()
            // Restore full queue from DB — ExoPlayer may have fewer items than Cast receiver.
            // CRITICAL: CastPlayer's internal transfer (CastTransferFilter) replaces
            // ExoPlayer's queue with the receiver's TRUNCATED queue (the DMR trims
            // played items) before this runs. Checking saved.currentIndex >= itemCount
            // is therefore never true (the truncated queue is smaller, index in bounds),
            // so the full queue was silently lost on every disconnect. Compare SIZES:
            // any mismatch → always restore the full queue from DB.
            kotlinx.coroutines.runBlocking {
                val saved = persistenceManager.restore()
                if (saved != null) {
                    android.util.Log.d(
                        "ftpmusic-cast",
                        "[switchToLocal] saved: tracks=${saved.tracks.size} contextSize=${saved.contextSize} currentIndex=${saved.currentIndex} pos=${saved.positionMs} -> local count=${ep.mediaItemCount}",
                    )
                    val queueTruncated = saved.tracks.size != ep.mediaItemCount
                    if (queueTruncated) {
                        android.util.Log.w(
                            "ftpmusic",
                            "[switchToLocal] restoring full queue: saved=${saved.tracks.size} local=${ep.mediaItemCount}",
                        )
                        // restoreQueue rebuilds the dual queue preserving the
                        // persisted context/priority split (contextSize), and clears
                        // stale priority items first — the lazy-loader may have
                        // appended chunks to it during the Cast→local switch, which
                        // would otherwise merge on top of the restored context
                        // (113+50=163). Legacy saves (contextSize=-1) restore
                        // everything as context, matching the old behavior.
                        playbackManager.restoreQueue(
                            saved.tracks,
                            saved.urls,
                            saved.currentIndex,
                            saved.contextSize,
                            saved.isPriorityFlags,
                            saved.entryIds,
                            saved.nextEntryId,
                        )
                    }
                    if (saved.currentIndex !in 0 until ep.mediaItemCount) {
                        android.util.Log.w(
                            "ftpmusic-cast",
                            "[switchToLocal] saved.currentIndex=${saved.currentIndex} out of range (count=${ep.mediaItemCount}) — clamped",
                        )
                    }
                    // Seek to saved position
                    val idx = saved.currentIndex.coerceIn(0, maxOf(0, ep.mediaItemCount - 1))
                    ep.seekTo(idx, saved.positionMs)
                }
            }
            ep.playWhenReady = false
            ep.prepare()
        } finally {
            switchingToLocal = false
        }
    }

    /** Index of the current track IN THE SAVED tracks list. During Cast the
     *  receiver trims played items, so CastPlayer.currentMediaItemIndex is
     *  relative to the receiver's shorter queue — persisting it would restore
     *  the WRONG track on disconnect ("lost the music I was playing"). Map the
     *  receiver's currentItemId to the full local (ExoPlayer) queue instead. */
    @VisibleForTesting
    internal fun resolveCurrentIndexForPersistence(player: androidx.media3.common.Player): Int {
        if (PlayerHolder.isCasting) {
            val rmc = castCtx?.sessionManager?.currentCastSession?.remoteMediaClient
            val itemId = rmc?.mediaStatus?.currentItemId
            val mapped = itemId?.let { resolveLocalIndexFromReceiverId(player, it) }
            if (mapped != null) return mapped
            android.util.Log.w(
                "ftpmusic-cast",
                "[Cast] currentItemId=$itemId not found in local queue — falling back to index ${(PlayerHolder.player ?: player).currentMediaItemIndex}",
            )
        }
        return (PlayerHolder.player ?: player).currentMediaItemIndex
    }

    private fun saveQueueState() {
        val player = PlayerHolder.exoPlayer ?: return
        // During Cast, ExoPlayer is frozen (muted) and its queue never reflects
        // albums/mixes played while casting. The dual queue (context + priority)
        // is the authoritative state — persist THAT so a disconnect restores the
        // queue the user was actually hearing, not the pre-Cast one.
        val (tracks, urls) = if (PlayerHolder.isCasting) {
            playbackManager.buildQueueStateFromDual()
        } else {
            playbackManager.buildQueueState()
        }
        if (tracks.isEmpty()) return
        val currentIndex = resolveCurrentIndexForPersistence(player)
        val currentPosition = PlayerHolder.player?.currentPosition ?: 0L
        android.util.Log.d(
            "ftpmusic-cast",
            "[saveQueueState] casting=${PlayerHolder.isCasting} tracks=${tracks.size} contextSize=${playbackManager.contextSize} currentIndex=$currentIndex pos=$currentPosition",
        )
        scope.launch {
            persistenceManager.save(
                tracks,
                urls,
                currentIndex,
                currentPosition,
                playbackManager.contextSize,
                playbackManager.isPriorityFlags(),
                playbackManager.entryIds(),
                playbackManager.peekNextEntryId(),
            )
            // savePlayQueue: debounce by 1.5s — cancels previous pending call, fires after idle window.
            // saveQueueStateSync() (onDestroy/onTaskRemoved) bypasses this for guaranteed last-chance persistence.
            saveQueueDebounceJob?.cancel()
            saveQueueDebounceJob = launch {
                delay(1500L)
                try {
                    val ids = tracks.joinToString(",") { it.id }
                    val auth = SubsonicAuthHelper().buildAuthParams(
                        SubsonicCredentials.username,
                        SubsonicCredentials.password,
                    )
                    scrobbleService.savePlayQueue(
                        auth,
                        ids,
                        tracks.getOrNull(currentIndex)?.id,
                        currentPosition,
                    )
                    PlayerHolder.queueSaveFailed = false
                } catch (_: Exception) {
                    PlayerHolder.queueSaveFailed = true
                }
            }
        }
    }

    /** Last-chance queue persistence for teardown paths. The snapshot is built
     *  synchronously here; the Room write drains async on [persistenceScope]
     *  (with [waitBoundedMs] it blocks up to that long — used ONLY on
     *  [onTaskRemoved], where the process may die right after). The server-side
     *  savePlayQueue flush is always fire-and-forget on ScrobbleService's own
     *  IO scope so no network call can ever run on the main thread here
     *  (an unreachable server would otherwise ANR the teardown). */
    private fun saveQueueStateSync(waitBoundedMs: Long? = null) {
        val player = PlayerHolder.exoPlayer ?: return
        val (tracks, urls) = if (PlayerHolder.isCasting) {
            playbackManager.buildQueueStateFromDual()
        } else {
            playbackManager.buildQueueState()
        }
        if (tracks.isEmpty()) return
        // Full queue loaded — currentMediaItemIndex IS the correct index locally,
        // but during Cast map the receiver's itemId to the full queue (see
        // resolveCurrentIndexForPersistence).
        val currentIndex = resolveCurrentIndexForPersistence(player)
        val currentPosition = PlayerHolder.player?.currentPosition ?: 0L
        android.util.Log.d(
            "ftpmusic-cast",
            "[saveQueueStateSync] casting=${PlayerHolder.isCasting} tracks=${tracks.size} contextSize=${playbackManager.contextSize} currentIndex=$currentIndex pos=$currentPosition",
        )
        try {
            if (waitBoundedMs != null) {
                runBlocking {
                    withTimeoutOrNull(waitBoundedMs) {
                        persistenceManager.save(
                            tracks,
                            urls,
                            currentIndex,
                            currentPosition,
                            playbackManager.contextSize,
                            playbackManager.isPriorityFlags(),
                            playbackManager.entryIds(),
                            playbackManager.peekNextEntryId(),
                        )
                    }
                }
            } else {
                persistenceScope.launch {
                    try {
                        persistenceManager.save(
                            tracks,
                            urls,
                            currentIndex,
                            currentPosition,
                            playbackManager.contextSize,
                            playbackManager.isPriorityFlags(),
                            playbackManager.entryIds(),
                            playbackManager.peekNextEntryId(),
                        )
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        // Server flush — fire-and-forget on ScrobbleService's own scope; the
        // local queue is already safe, and saveQueueState retries the flush
        // on the next queue change.
        try {
            val ids = tracks.joinToString(",") { it.id }
            val auth = SubsonicAuthHelper().buildAuthParams(
                SubsonicCredentials.username,
                SubsonicCredentials.password,
            )
            scrobbleService.savePlayQueue(
                auth,
                ids,
                tracks.getOrNull(currentIndex)?.id,
                currentPosition,
            )
        } catch (_: Exception) {}
    }

    private fun requestAudioFocus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> PlayerHolder.player?.pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> PlayerHolder.player?.pause()
                        AudioManager.AUDIOFOCUS_GAIN -> PlayerHolder.player?.play()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { /* lower volume */ }
                    }
                }
                .build()
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { /* deprecated listener */ },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
    }

    private fun abandonAudioFocus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    /** Rebuild a MediaItem with a fallback mimeType if none is set. */
    @VisibleForTesting
    internal fun ensureMimeType(item: MediaItem): MediaItem {
        val uri = item.localConfiguration?.uri?.toString() ?: return item
        val mimeType = when {
            uri.contains(".mp3", ignoreCase = true) || uri.contains("format=mp3") -> "audio/mpeg"
            uri.contains(".ogg", ignoreCase = true) || uri.contains("format=ogg") -> "audio/ogg"
            uri.contains(".opus", ignoreCase = true) || uri.contains("format=opus") -> "audio/ogg;codecs=opus"
            uri.contains(".flac", ignoreCase = true) || uri.contains("format=flac") -> "audio/flac"
            uri.contains(".aac", ignoreCase = true) || uri.contains("format=aac") -> "audio/aac"
            uri.contains(".m4a", ignoreCase = true) || uri.contains("format=m4a") -> "audio/mp4"
            uri.contains(".wav", ignoreCase = true) || uri.contains("format=wav") -> "audio/wav"
            else -> "audio/mpeg" // safe default
        }
        return MediaItem.Builder()
            .setMediaId(item.mediaId)
            .setUri(item.localConfiguration?.uri ?: Uri.EMPTY)
            .setMimeType(mimeType)
            .setMediaMetadata(item.mediaMetadata)
            .build()
    }

    // ── savePlayQueue debounce ────────────────────────────────────────────
    private var saveQueueDebounceJob: Job? = null

    /** True if the receiver already has items queued (reconnect, not fresh). */
    private fun isReconnectingToExistingSession(): Boolean {
        val rmc = castCtx?.sessionManager?.currentCastSession?.remoteMediaClient ?: return false
        val hasQueue = rmc.getMediaQueue()?.itemCount?.let { it > 0 } ?: false
        if (!hasQueue) return false
        android.util.Log.d(
            "ftpmusic-cast",
            "[Cast] isReconnectingToExistingSession: hasQueue=true wasResumed=$sessionWasResumed",
        )
        return sessionWasResumed
    }

    /**
     * Skip receiver queueLoad only for same-device sleep resume when Exo already
     * mirrors DualQueue. Device switches and truncated Exo always reload.
     */
    @VisibleForTesting
    internal fun shouldSkipReceiverQueueLoad(): Boolean {
        if (!isReconnectingToExistingSession()) return false
        val exo = PlayerHolder.exoPlayer?.mediaItemCount ?: 0
        val dual = playbackManager.dualQueueSize
        val match = dual > 0 && exo == dual
        android.util.Log.d(
            "ftpmusic-cast",
            "[Cast] shouldSkipReceiverQueueLoad exo=$exo dual=$dual dualCtx=${playbackManager.contextSize} dualPri=${playbackManager.priorityQueueSize} skip=$match",
        )
        return match
    }

    /** Record Cast deviceId; clear sessionWasResumed when the physical device changes. */
    @VisibleForTesting
    internal fun noteCastDeviceForQueuePolicy(deviceId: String?) {
        if (deviceId == null) return
        if (castSessionDeviceId != null && castSessionDeviceId != deviceId) {
            android.util.Log.w(
                "ftpmusic-cast",
                "[Cast] deviceId change $castSessionDeviceId → $deviceId — force queue reload",
            )
            sessionWasResumed = false
        }
        castSessionDeviceId = deviceId
    }

    /**
     * Rebuild Exo from DualQueue when CastTransferFilter (or a switch gap) left
     * a truncated/stale Exo mirror. No-op when sizes already match or Dual empty.
     */
    @VisibleForTesting
    internal fun ensureExoMatchesDual() {
        val exo = PlayerHolder.exoPlayer ?: return
        val dualSize = playbackManager.dualQueueSize
        val exoCount = exo.mediaItemCount
        val deviceId = castSessionDeviceId ?: castLastDevice?.deviceId
        if (dualSize <= 0) {
            android.util.Log.d(
                "ftpmusic-cast",
                "[ensureExoMatchesDual] dual empty — skip (exo=$exoCount deviceId=$deviceId)",
            )
            return
        }
        if (exoCount == dualSize) return
        android.util.Log.w(
            "ftpmusic-cast",
            "[ensureExoMatchesDual] exo=$exoCount dual=$dualSize (ctx=${playbackManager.contextSize} pri=${playbackManager.priorityQueueSize}) deviceId=$deviceId — rebuilding Exo from DualQueue",
        )
        playbackManager.syncDualQueueToPlayer()
    }

    private var sessionWasResumed: Boolean = false

    /** ID of the track for which we issued a sender-driven advance jump. */
    private var lastAdvanceItemId: String? = null

    /** Maximum remaining time (ms) at which the timer path fires the advance jump.
     *  Path 1 (onStatusUpdated IDLE+FINISHED) cuts no tail; this timer path cuts
     *  at most this much when the event is missed or delayed. */
    @VisibleForTesting
    internal val advanceLeadMs: Long = 800L

    /**
     * Computes the receiver queue operation for preloading the item at [nextIdx]:
     * the existing itemId to remove, and the itemId to insert BEFORE (or
     * INVALID_ITEM_ID to append). Pure logic, unit-tested.
     */
    @VisibleForTesting
    internal fun computePreloadQueueOps(itemIds: List<Int>, nextIdx: Int): Pair<Int, Int>? {
        if (nextIdx < 0 || nextIdx >= itemIds.size) return null
        val existingId = itemIds[nextIdx]
        val insertBeforeId = itemIds.getOrNull(nextIdx + 1) ?: MediaQueueItem.INVALID_ITEM_ID
        return existingId to insertBeforeId
    }

    /**
     * Reconnect resilience decision: does the receiver's current item differ from
     * the local ExoPlayer index? Returns the receiver's current index if a sync
     * is needed, null otherwise. Pure logic, unit-tested.
     *
     * @param remoteCurrentId the receiver's currentItemId (from mediaStatus)
     * @param remoteIds       receiver queue itemIds in order (from getItemIds)
     * @param localIdx        local ExoPlayer currentMediaItemIndex
     */
    @VisibleForTesting
    internal fun computeReconnectSync(remoteCurrentId: Int, remoteIds: List<Int>, localIdx: Int): Int? {
        val remoteIdx = remoteIds.indexOf(remoteCurrentId)
        if (remoteIdx < 0) return null
        return if (remoteIdx != localIdx) remoteIdx else null
    }

    /**
     * True when the Cast SDK holds a session that is actually CONNECTED with a
     * live media client. A non-null `currentCastSession` is NOT enough: the GMS
     * SessionManager can retain a stale/phantom session object mid-teardown
     * (observed 2026-08-27 — "Ignoring session creation result for unknown
     * request"), which would wrongly suppress the empty-timeline detach guard
     * (Edge-08). Requiring `remoteMediaClient != null` additionally excludes a
     * connected-but-idle session with no receiver media (L10).
     */
    private fun hasLiveCastSession(): Boolean = try {
        val s = castCtx?.sessionManager?.currentCastSession
        s?.isConnected == true && s.remoteMediaClient != null
    } catch (_: Exception) {
        false
    }

    /** deviceId of the currently CONNECTED session, or null when none. */
    private fun liveCastSessionDeviceId(): String? = try {
        castCtx?.sessionManager?.currentCastSession
            ?.takeIf { it.isConnected }
            ?.castDevice?.deviceId
    } catch (_: Exception) {
        null
    }

    /** True when a CONNECTED session exists on [targetDeviceId] (M5: the
     *  connect timeout aborts only when the TARGET session never became live —
     *  a surviving session on another device must not suppress it). */
    private fun hasLiveCastSessionOn(targetDeviceId: String?): Boolean =
        targetDeviceId != null && liveCastSessionDeviceId() == targetDeviceId

    /** Bug-A decision: must the MediaSession be detached from CastPlayer NOW?
     * True when the MediaSession is attached to CastPlayer whose timeline is
     * EMPTY while no Cast session exists — RemoteCastPlayer flushes an empty
     * timeline during teardown and MediaSessionImpl's PlayerWrapper crashes
     * with checkState(currentIndex < windowCount) (media3 1.10.1; process
     * deaths 2026-08-24/08-27). Pure logic, unit-tested.
     */
    @VisibleForTesting
    internal fun shouldDetachEmptyTimeline(
        attachedPlayer: Player?,
        castPlayer: Player?,
        castItemCount: Int,
        hasLiveSession: Boolean,
    ): Boolean = attachedPlayer === castPlayer && castPlayer != null && castItemCount == 0 && !hasLiveSession

    /**
     * Bug-B decision: should a manual connect request be ignored because one
     * is already in flight? Serializes double-taps so two teardowns can't
     * race. Pure logic, unit-tested.
     */
    @VisibleForTesting
    internal fun shouldIgnoreConnectRequest(inFlight: Boolean): Boolean = inFlight

    /**
     * Bug-B decision: should the connect timeout abort the attempt? Only when
     * no session became real within the window — a live session means the
     * connect succeeded and the deadline was cleared by the success path.
     * Pure logic, unit-tested.
     */
    @VisibleForTesting
    internal fun shouldAbortConnectTimeout(hasLiveSession: Boolean): Boolean = !hasLiveSession

    /** ID of the last track we preloaded via queueInsert — prevents duplicates. */
    private var lastPreloadedItemId: String? = null

    /**
     * Sender-driven auto-advance via the FAST jump path. Two triggers:
     *
     * Path 1 (event, zero tail cut): onStatusUpdated fires with PLAYER_STATE_IDLE
     *   + IDLE_REASON_FINISHED when the DMR completes the current track. The jump
     *   is issued at the exact natural end — nothing lost.
     *
     * Path 2 (timer, <= 800ms tail cut): the position poller fires when
     *   positionMs + advanceLeadMs >= durationMs (within 800ms of the computed
     *   end). Covers missed/delayed events or slightly-off metadata.
     *
     * Why jump at all: queueLoad preloadTime is a best-effort hint the DMR
     * "will try to honor but not guarantee" — empirically NOT honored on this
     * receiver (server shows next-track fetch only at track end), causing the
     * 5–20s cold-start gap. queueJumpToItem is the same command path as a manual
     * user skip (observed fast) and loads immediately.
     *
     * Guard: only jump when the NEXT item is a different track (not last), and
     * only once per track (lastAdvanceItemId) so both paths can't double-fire.
     * Falls back to the receiver's own autoplay if the jump can't be issued
     * (no rmc, no next item).
     */
    internal fun preloadNextIfNeeded(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        if (positionMs + advanceLeadMs < durationMs) return
        jumpToNextTrack()
    }

    /**
     * Issue queueJumpToItem to the next queue item via the receiver's itemIds.
     * Shared by the event path (onStatusUpdated) and the timer path
     * (preloadNextIfNeeded). No-op when there is no next item, no session, or
     * the jump was already issued for this track.
     */
    internal fun jumpToNextTrack() {
        val exo = PlayerHolder.exoPlayer ?: return
        val rmc = castCtx?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        // Resolve the CURRENT track from the receiver's itemId — the receiver is
        // ground truth (ADR-0011). Never derive nextIdx from
        // PlayerHolder.player.currentMediaItemIndex during cast: that's the
        // CastPlayer index, which is relative to the receiver's TRIMMED queue
        // (and can be stale/empty when the timeline tracker is desynced), while
        // the local ExoPlayer holds the FULL queue — indexing one with the
        // other jumped the receiver to the WRONG track (phone UI jump + abrupt
        // receiver stop, observed 2026-08-23 on the Soundbar).
        val currentItemId = rmc.mediaStatus?.currentItemId ?: return
        val localIdx = resolveLocalIndexFromReceiverId(exo, currentItemId) ?: return
        val nextIdx = localIdx + 1
        if (nextIdx >= exo.mediaItemCount) return
        val nextItem = exo.getMediaItemAt(nextIdx) ?: return
        val nextId = nextItem.mediaId ?: return
        if (nextId == lastAdvanceItemId) return
        lastAdvanceItemId = nextId
        lastPreloadedItemId = nextId

        android.util.Log.d("ftpmusic-cast", "[Cast] advance jump: next=$nextId at index $nextIdx")
        try {
            val itemIds = rmc.getMediaQueue()?.getItemIds()?.toList() ?: return
            val nextEntryId = nextItem.queueEntryId()
            val targetItemId = when {
                nextEntryId > 0 && itemIds.contains(nextEntryId) -> nextEntryId
                else -> itemIds.firstOrNull { it == nextId.hashCode() }
            } ?: return
            rmc.queueJumpToItem(targetItemId, null)
            val preloadedId = rmc.mediaStatus?.preloadedItemId
            android.util.Log.d(
                "ftpmusic-cast",
                "[Cast] advance jump issued: jumpTo=$targetItemId preloadedItemId=$preloadedId",
            )
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-cast", "[Cast] advance jump failed: ${e.message}")
        }
    }

    /**
     * Load the full ExoPlayer queue to the Cast receiver. Prefers media3's
     * `CastPlayer.setMediaItems` — RemoteCastPlayer converts via
     * [SubsonicMediaItemConverter] (identical receiver items: customData
     * trackId + entryId, preloadTime 45s, itemId = queueEntryId, autoplay) AND
     * populates `CastTimelineTracker`'s contentId→MediaItem map (a missing map
     * resolves the current item to `MediaItem.EMPTY` — blank Now Playing — or
     * an empty timeline: the cast-state-death failure).
     *
     * setMediaItems is only used while `RemoteCastPlayer.isCastSessionAvailable()`
     * — CastPlayerImpl forwards player commands to the LOCAL ExoPlayer until
     * session availability flips (device-switch window, early onSessionStarted).
     * A load routed locally never reaches the receiver. In that window we fall
     * back to the session-direct raw `rmc.queueLoad`, which is immune to the
     * CastPlayer state machine.
     */
    @VisibleForTesting
    internal fun loadFullQueueToReceiver(rmc: RemoteMediaClient) {
        ensureExoMatchesDual()
        val exo = PlayerHolder.exoPlayer ?: return
        val allItems = (0 until exo.mediaItemCount).mapNotNull { exo.getMediaItemAt(it) }
        if (allItems.isEmpty()) return
        // Reconnect-safe start index: during cast the LOCAL ExoPlayer index is
        // frozen at cast start (the receiver advances independently), so using
        // exo.currentMediaItemIndex on a reconnect reload re-seats the receiver
        // at the WRONG track — the phone mirror follows the receiver's new
        // currentItemId and the phone UI jumps (observed 2026-08-23 Soundbar).
        // Resolve the start index from the receiver's current itemId (ground
        // truth, ADR-0011); fall back to the local index only for fresh loads
        // where the receiver has no current item yet.
        val startIdx = rmc.mediaStatus?.currentItemId
            ?.let { resolveLocalIndexFromReceiverId(exo, it) }
            ?: exo.currentMediaItemIndex
        val position = PlayerHolder.player?.currentPosition ?: 0L
        lastPreloadedItemId = null // Reset for new queue
        lastAdvanceItemId = null // Reset for new queue
        val cp = castPlayer
        if (remoteCastPlayer?.isCastSessionAvailable() == true && cp != null) {
            android.util.Log.d(
                "ftpmusic-cast",
                "[Cast] loadFullQueueToReceiver: ${allItems.size} items via CastPlayer.setMediaItems, startIdx=$startIdx, position=$position",
            )
            cp.setMediaItems(allItems, startIdx, position)
        } else {
            // CastPlayer not yet remote (or unavailable) — session-direct load.
            android.util.Log.d(
                "ftpmusic-cast",
                "[Cast] loadFullQueueToReceiver: ${allItems.size} items via raw queueLoad (CastPlayer not remote), startIdx=$startIdx, position=$position",
            )
            val converter = SubsonicMediaItemConverter(castPreferences)
            val queueItems = allItems.map { converter.toMediaQueueItem(it) }.toTypedArray()
            rmc.queueLoad(queueItems, startIdx, MediaStatus.REPEAT_MODE_REPEAT_OFF, position, null)
        }
    }

    /** Last track seen by the receiver-state mirror — drives the once-per-
     *  transition metric log (the mirror polls every 200ms; we only log on
     *  actual receiver transitions). */
    @Volatile
    internal var lastMirrorTrackId: String? = null

    /**
     * Receiver-state mirror: synthesize a [PlaybackState] from the Cast
     * receiver's mediaStatus (currentItemId → local queue index via
     * queueEntryId), receiver position
     * (approximateStreamPosition) and playerState. Returns null when no
     * session/status/current item is resolvable — the position poller then
     * falls back to the player path. This keeps UI/notification live even when
     * media3's CastPlayer timeline is empty/stale (raw-SDK queue loads,
     * session churn, reconnect-to-playing-receiver).
     */
    @VisibleForTesting
    internal fun buildCastSnapshot(): PlaybackState? {
        val exo = PlayerHolder.exoPlayer ?: return null
        val rmc = castCtx?.sessionManager?.currentCastSession?.remoteMediaClient ?: return null
        val status = rmc.mediaStatus ?: return null
        val currentItemId = status.currentItemId ?: return null
        val localIdx = resolveLocalIndexFromReceiverId(exo, currentItemId) ?: return null
        val item = exo.getMediaItemAt(localIdx) ?: return null
        val nextItem = if (localIdx + 1 < exo.mediaItemCount) exo.getMediaItemAt(localIdx + 1) else null
        val receiverPlaying = status.playerState == MediaStatus.PLAYER_STATE_PLAYING ||
            status.playerState == MediaStatus.PLAYER_STATE_BUFFERING ||
            status.playerState == MediaStatus.PLAYER_STATE_LOADING
        val state = PlaybackState.fromCastState(
            item = item,
            nextItem = nextItem,
            prevState = playbackProvider.playbackState.value,
            isPlaying = receiverPlaying,
            positionMs = rmc.approximateStreamPosition,
            index = localIdx,
            queueSize = exo.mediaItemCount,
        )
        // Receiver-transition metric: log once per track change, not per poll.
        // This line is the primary diagnostic for cast-state-death regressions.
        if (state.currentTrackId != lastMirrorTrackId) {
            lastMirrorTrackId = state.currentTrackId
            android.util.Log.w(
                "ftpmusic-cast",
                "[Cast mirror] receiver transition → ${state.title} @ local idx $localIdx (pos=${rmc.approximateStreamPosition}ms, playerState=${status.playerState}, queue=${exo.mediaItemCount})",
            )
        }
        return state
    }

    /**
     * Single-listener discipline: [playerListener] lives on EXACTLY ONE player.
     * Removes it from both known players, then attaches to the active one.
     * Prevents duplicate scrobbles/notifications after cast→local switches
     * (the old listenerOnExoPlayer flag was a stale ledger — it only gated
     * addListener and never removed from the inactive player).
     */
    private fun setListenerPlayer(target: Player?) {
        exoPlayer?.removeListener(playerListener)
        castPlayer?.removeListener(playerListener)
        target?.addListener(playerListener)
    }

    /**
     * True when a deferred cast-setup attempt is still valid: the session
     * generation is unchanged AND we are still casting. Guards the retry loop
     * against session churn — a stale attempt could otherwise register
     * callbacks on the NEW session (duplicate queueLoads = audible restart),
     * re-wire the mirror after cleanup cleared it, or run after onDestroy.
     */
    @VisibleForTesting
    internal fun shouldAttemptCastSetup(gen: Int): Boolean = gen == castDisconnectGen && PlayerHolder.isCasting

    /** Bounded retry for the remote-session setup when `remoteMediaClient` is
     *  momentarily unavailable during session churn (EDGE-01/02). */
    @VisibleForTesting
    internal fun setupRemoteCastSessionWithRetry() {
        // Seed the device volume from the Cast session before onDeviceVolumeChanged fires
        PlayerHolder.seedFromSessionVolume(castCtx?.sessionManager?.currentCastSession?.volume)
        var attempts = 0
        val gen = castDisconnectGen
        fun attempt() {
            // Abort when the session churned or cleanup ran while we waited.
            if (!shouldAttemptCastSetup(gen)) {
                android.util.Log.d(
                    "ftpmusic-cast",
                    "[Cast] deferred setup aborted (session generation changed or not casting)",
                )
                return
            }
            val rmc = castCtx?.sessionManager?.currentCastSession?.remoteMediaClient
            if (rmc != null) {
                setupRemoteCastSession(rmc)
                if (attempts > 0) {
                    android.util.Log.w(
                        "ftpmusic-cast",
                        "[Cast] remote session setup completed on retry (attempt ${attempts + 1}/$MAX_CAST_SETUP_RETRIES)",
                    )
                }
            } else if (attempts < MAX_CAST_SETUP_RETRIES) {
                attempts++
                android.util.Log.w(
                    "ftpmusic-cast",
                    "[Cast] remoteMediaClient not ready (attempt $attempts/$MAX_CAST_SETUP_RETRIES) — retrying in 500ms",
                )
                handler.postDelayed({ attempt() }, 500L)
            } else {
                android.util.Log.e(
                    "ftpmusic-cast",
                    "[Cast] remoteMediaClient never became ready after $MAX_CAST_SETUP_RETRIES retries — mirror stays wired, poller falls back to player path",
                )
                // Defensive (E7): keep the receiver-state mirror wired even when
                // retries are exhausted. buildCastSnapshot() self-nulls when the
                // receiver is unavailable, so the poller degrades gracefully
                // instead of drifting on a stale/empty CastPlayer timeline while
                // the receiver keeps playing.
                wireCastStateMirror()
            }
        }
        attempt()
    }

    /** Wire the receiver-state mirror closure into the (singleton) provider.
     *  Called from onSessionStarted, onSessionResumed, and the remote-setup
     *  path — a resume after cleanup must re-wire it or the UI can blank
     *  again while isCasting=true (FIND-04). */
    @VisibleForTesting
    internal fun wireCastStateMirror() {
        playbackProvider.castStateSource = { buildCastSnapshot() }
    }

    /** Full mirror teardown: drop the closure (release the service reference
     *  held by the @Singleton provider) and reset the transition marker so a
     *  reconnect logs a real transition. Called on disconnect cleanup,
     *  switchToLocalPlayback, and onDestroy. */
    @VisibleForTesting
    internal fun clearCastMirror() {
        lastMirrorTrackId = null
        playbackProvider.castStateSource = null
    }

    /**
     * Synchronously re-seat the media session's player to ExoPlayer BEFORE the
     * CastPlayer can flush a shrinking/empty timeline during session teardown.
     *
     * MediaSessionImpl's PlayerWrapper crashes with
     * `checkState(currentIndex < windowCount)` when RemoteCastPlayer's timeline
     * shrinks (receiver trim / session end) while the session still holds the
     * old current index — process death observed 2026-08-21 (fixed upstream in
     * media3 1.11; this guards teardown regardless of version). A successful
     * reconnect re-seats back to CastPlayer via onDeviceInfoChanged(remote=true).
     */
    @VisibleForTesting
    internal fun detachCastSessionFromMediaSession() {
        val ep = exoPlayer ?: return
        setListenerPlayer(ep)
        if (::mediaSession.isInitialized) {
            mediaSession.player = ep
        }
        playbackProvider.onPlayerSwitched()
        clearCastMirror()
    }

    /** Register receiver callbacks, load the queue, and wire the state mirror
     *  for a live remote session. Callers: onDeviceInfoChanged(remote=true)
     *  (via [setupRemoteCastSessionWithRetry]). */
    private fun setupRemoteCastSession(rmc: RemoteMediaClient) {
        // Unregister any previous callbacks before registering new ones
        mediaQueueCallback?.let { cb ->
            try {
                castMediaQueue?.unregisterCallback(cb)
            } catch (_: Exception) {}
        }
        rmcCallback?.let { oldCb ->
            try {
                rmc.unregisterCallback(oldCb)
            } catch (_: Exception) {}
        }
        // Only load the full queue if this is a new Cast session,
        // NOT a reconnect after sleep (receiver already has the queue).
        // Cast SDK queue survives on the receiver independently of the sender.
        android.util.Log.d(
            "ftpmusic-cast",
            "[Cast connect] deviceId=$castSessionDeviceId exo=${exoPlayer?.mediaItemCount} dualCtx=${playbackManager.contextSize} dualPri=${playbackManager.priorityQueueSize} dual=${playbackManager.dualQueueSize} reconnect=${isReconnectingToExistingSession()} skipLoad=${shouldSkipReceiverQueueLoad()}",
        )
        ensureExoMatchesDual()
        if (!shouldSkipReceiverQueueLoad()) {
            handler.postDelayed({
                if (!PlayerHolder.isCasting) return@postDelayed
                loadFullQueueToReceiver(rmc)
            }, 500L)
        } else {
            android.util.Log.d("ftpmusic-cast", "[Cast] Reconnecting to existing session — skipping queueLoad")
        }
        // Register MediaQueue.Callback for queue change tracking
        val mq = rmc.getMediaQueue() ?: run {
            android.util.Log.w("ftpmusic-cast", "[Cast] setup: receiver MediaQueue is null — skipping queue callbacks")
            return
        }
        castMediaQueue = mq
        val cb = object : MediaQueue.Callback() {
            override fun itemsUpdatedAtIndexes(indexes: IntArray) {
                val remoteIds = mq.getItemIds().toList()
                val localCount = PlayerHolder.exoPlayer?.mediaItemCount ?: 0
                if (remoteIds.size != localCount && remoteIds.isNotEmpty()) {
                    android.util.Log.w(
                        "ftpmusic-cast",
                        "[Cast] remote queue size (${remoteIds.size}) != local ($localCount)",
                    )
                    // Phone queue is authoritative — never replace it with
                    // the receiver's (subset) queue. Sync position only.
                    syncReceiverPosition(rmc)
                }
            }
            override fun mediaQueueChanged() {}
            override fun itemsInsertedInRange(start: Int, count: Int) {
                itemsUpdatedAtIndexes((start until start + count).toList().toIntArray())
            }
            override fun itemsRemovedAtIndexes(indexes: IntArray) {}
            override fun itemsReloaded() {
                itemsUpdatedAtIndexes((0 until mq.itemCount).toList().toIntArray())
            }
        }
        mq.registerCallback(cb)
        mediaQueueCallback = cb
        // Register RemoteMediaClient.Callback for queue status.
        // NOTE: onStatusUpdated (track-finished event) is intentionally
        // NOT overridden — after a reconnect the receiver may replay a
        // stale IDLE_REASON_FINISHED from before the disconnect, causing
        // an unwanted jump. The timer path (preloadNextIfNeeded, 200ms
        // cadence, 800ms lead) handles transitions reliably; the
        // reconnect sync below realigns local state instead.
        rmcCallback = object : RemoteMediaClient.Callback() {
            override fun onQueueStatusUpdated() {
                val currentItemId = rmc.mediaStatus?.currentItemId ?: return
                android.util.Log.d("ftpmusic-cast", "[Cast] onQueueStatusUpdated: currentItemId=$currentItemId")
                val preloadedId = rmc.mediaStatus?.preloadedItemId
                if (preloadedId != null && preloadedId != MediaQueueItem.INVALID_ITEM_ID) {
                    android.util.Log.d("ftpmusic-cast", "[Cast] receiver preloaded item $preloadedId")
                }
                val remoteIds = rmc.mediaQueue?.getItemIds()?.toList() ?: return
                val localCount = PlayerHolder.exoPlayer?.mediaItemCount ?: 0
                if (remoteIds.size > localCount) {
                    android.util.Log.w("ftpmusic-cast", "[Cast] remote queue larger than local, reloading")
                    loadFullQueueToReceiver(rmc)
                } else if (remoteIds.size < localCount && remoteIds.isNotEmpty()) {
                    // Receiver trims played items — its queue is naturally
                    // smaller. The PHONE queue is authoritative; never
                    // replace it with the receiver's (subset) queue. Just sync
                    // the current position so the timer path computes
                    // nextIdx from the correct local track.
                    syncReceiverPosition(rmc)
                }
                val loadingId = rmc.mediaStatus?.loadingItemId
                if (loadingId != MediaStatus.IDLE_REASON_NONE) {
                    android.util.Log.d("ftpmusic-cast", "[Cast] receiver loading item $loadingId")
                }
            }
        }
        rmc.registerCallback(rmcCallback!!)
        // Reconnect resilience: 500ms after session establishment (or
        // resume), the receiver is the ground truth. If it advanced to a
        // different track while we were disconnected, realign the local
        // ExoPlayer queue to the receiver's current item + position so
        // the timer path (preloadNextIfNeeded) computes nextIdx from the
        // CORRECT current track. Without this, a stale local index made
        // the jump fire at the wrong queue position after reconnect.
        handler.postDelayed({
            if (!PlayerHolder.isCasting) return@postDelayed
            val srmc = castCtx?.sessionManager?.currentCastSession?.remoteMediaClient ?: return@postDelayed
            val status = srmc.mediaStatus ?: return@postDelayed
            val remoteCurrentId = status.currentItemId ?: return@postDelayed
            val remoteIds = srmc.mediaQueue?.getItemIds()?.toList() ?: return@postDelayed
            val localIdx = PlayerHolder.exoPlayer?.currentMediaItemIndex ?: return@postDelayed
            val remoteIdx = computeReconnectSync(remoteCurrentId, remoteIds, localIdx) ?: return@postDelayed
            android.util.Log.w(
                "ftpmusic-cast",
                "[Cast] reconnect sync: remoteIdx=$remoteIdx != localIdx=$localIdx, syncing position to receiver",
            )
            syncReceiverPosition(srmc)
        }, 500L)
        // Wire the receiver-state mirror: the poller reads this every 200ms
        // while casting, so UI/notification stay live even if media3's
        // CastPlayer timeline is empty or events are missed.
        wireCastStateMirror()
    }

    /**
     * Arm the sleep timer at [endMs] (epoch ms). Service-owned enforcement:
     * cancels any prior job, then pauses playback and clears the persisted
     * end timestamp when the deadline hits. An already-expired deadline is
     * treated as a cancel (clears stale state).
     */
    @VisibleForTesting
    internal fun armSleepTimer(endMs: Long) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (endMs <= System.currentTimeMillis()) {
            PlayerHolder.sleepTimerEndMs = 0L
            persistPlaybackState()
            return
        }
        PlayerHolder.sleepTimerEndMs = endMs
        sleepTimerJob = scope.launch {
            val remaining = endMs - System.currentTimeMillis()
            if (remaining > 0) delay(remaining)
            // Pause local player (CastPlayer.pause() pauses the receiver too).
            try {
                PlayerHolder.player?.pause()
            } catch (_: Exception) {}
            PlayerHolder.sleepTimerEndMs = 0L
            persistPlaybackState()
            sleepTimerJob = null
        }
    }

    /** Cancel any armed sleep timer and clear its persisted state. */
    @VisibleForTesting
    internal fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        PlayerHolder.sleepTimerEndMs = 0L
        persistPlaybackState()
    }

    /**
     * Routes UI playback controls to the active player. Extracted from the
     * setControlCallback closure so it is directly unit-testable.
     *
     * PLAY_PAUSE never switches the session player mid-cast: swapping
     * mediaSession.player to ExoPlayer while the receiver plays detaches the
     * session from the cast route (two audio sources, diverged state). An idle
     * cast player with 0 items instead re-loads the queue THROUGH CastPlayer
     * (populates media3's timeline tracker) so state recovers in place.
     */
    @VisibleForTesting
    internal fun handlePlaybackControl(control: PlaybackControl) {
        val player = PlayerHolder.player ?: return
        when (control) {
            PlaybackControl.PLAY_PAUSE -> {
                android.util.Log.d(
                    "ftpmusic",
                    "[PLAY_PAUSE] player=${player::class.simpleName} isPlaying=${player.isPlaying} state=${player.playbackState} casting=${PlayerHolder.isCasting}",
                )
                if (player.playbackState == Player.STATE_IDLE) {
                    if (PlayerHolder.isCasting && player.mediaItemCount == 0) {
                        val rmc = castCtx?.sessionManager?.currentCastSession?.remoteMediaClient
                        if (rmc != null) {
                            android.util.Log.w(
                                "ftpmusic-cast",
                                "[PLAY_PAUSE] CastPlayer idle with 0 items — reloading queue through CastPlayer",
                            )
                            loadFullQueueToReceiver(rmc)
                        } else {
                            // Session in the reconnect retry window (isCasting
                            // stale-true, rmc null): re-attempt the remote setup
                            // so the queue load fires when the session returns —
                            // the old ExoPlayer-swap self-heal is gone by design.
                            android.util.Log.w(
                                "ftpmusic-cast",
                                "[PLAY_PAUSE] cast idle with 0 items and no session — re-attempting remote setup",
                            )
                            setupRemoteCastSessionWithRetry()
                        }
                    }
                    player.prepare()
                    player.play()
                } else if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }

            PlaybackControl.SKIP_NEXT -> {
                if (PlayerHolder.isCasting) {
                    try {
                        castCtx?.sessionManager?.currentCastSession?.remoteMediaClient
                            ?.queueNext(null)
                    } catch (_: Exception) {
                        player.seekToNextMediaItem()
                    }
                } else {
                    player.seekToNextMediaItem()
                }
            }

            PlaybackControl.SKIP_PREV -> {
                if (PlayerHolder.isCasting) {
                    try {
                        castCtx?.sessionManager?.currentCastSession?.remoteMediaClient
                            ?.queuePrev(null)
                    } catch (_: Exception) {
                        player.seekToPreviousMediaItem()
                    }
                } else {
                    player.seekToPreviousMediaItem()
                }
            }

            is PlaybackControl.SEEK_TO -> {
                // Serialize seeks: coalesce rapid fast-forwards so a stale
                // fraction can't land in the wrong track after a seek-triggered
                // transition. No clamping — end-of-track skip is intentional.
                val target = seekCoalescer.request(control.fraction) ?: return
                performSeek(player, target)
            }

            PlaybackControl.REPEAT_TOGGLE -> {
                val next = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                player.repeatMode = next
            }

            PlaybackControl.SHUFFLE_TOGGLE -> {
                val enabled = !player.shuffleModeEnabled
                player.shuffleModeEnabled = enabled
            }

            PlaybackControl.SPEED_TOGGLE -> {
                val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                val current = player.playbackParameters.speed
                val nextIndex = (speeds.indexOfFirst { it == current } + 1) % speeds.size
                val next = speeds[nextIndex]
                player.playbackParameters = PlaybackParameters(next)
            }

            is PlaybackControl.SET_VOLUME -> {
                if (PlayerHolder.isCasting) {
                    // Track the value we're sending so fromPlayer() shows it while
                    // the device confirms asynchronously (ramps can take 2+ seconds).
                    // Timestamp drives the 3s expiry in MediaSessionPlaybackProvider's
                    // position poller: if no confirmation arrives (network drop),
                    // pending reverts to the last confirmed castVolume.
                    PlayerHolder.pendingCastVolume = control.volume
                    PlayerHolder.pendingCastVolumeTimestamp = System.currentTimeMillis()
                    // Only dispatch to Cast SDK if an active CastSession exists.
                    // During session transitions (device switch, reconnect),
                    // currentCastSession may be null — skip the dispatch
                    // rather than letting the SDK throw.
                    val castSession = try {
                        com.google.android.gms.cast.framework.CastContext.getSharedInstance(this)
                            .sessionManager.currentCastSession
                    } catch (_: Exception) {
                        null
                    }
                    if (castSession != null) {
                        try {
                            castSession.volume = control.volume.toDouble()
                        } catch (_: Exception) {}
                    }
                } else {
                    player.volume = control.volume
                }
            }

            is PlaybackControl.SLEEP_TIMER_ARM -> armSleepTimer(control.endMs)

            PlaybackControl.SLEEP_TIMER_CANCEL -> cancelSleepTimer()
        }
    }

    /**
     * Sync the local ExoPlayer's CURRENT POSITION from the Cast receiver —
     * WITHOUT touching the local queue items. The phone queue is authoritative
     * and must never be replaced by the receiver's (smaller, post-advance)
     * queue. Maps the receiver's currentItemId to the local index via the
     * queueEntryId mapping used in SubsonicMediaItemConverter, then seeks
     * ExoPlayer to that index +
     * the receiver's approximate stream position.
     */
    @VisibleForTesting
    internal fun syncReceiverPosition(rmc: RemoteMediaClient) {
        val ep = PlayerHolder.exoPlayer ?: return
        val currentItemId = rmc.mediaStatus?.currentItemId ?: return
        val localIdx = resolveLocalIndexFromReceiverId(ep, currentItemId) ?: return
        val positionMs = rmc.approximateStreamPosition
        android.util.Log.d(
            "ftpmusic-cast",
            "[Cast] syncReceiverPosition: localIdx=$localIdx pos=$positionMs (queue untouched)",
        )
        ep.seekTo(localIdx, positionMs)
    }

    /**
     * @deprecated Replaces the local queue with the receiver's (subset) queue.
     * The phone queue is authoritative; use [syncReceiverPosition] instead.
     * Kept only for reference/rollback.
     */
    @Deprecated("Phone queue is authoritative — use syncReceiverPosition")
    private fun syncRemoteQueueToLocal(rmc: RemoteMediaClient) {
        val mq = castMediaQueue ?: return
        val ep = PlayerHolder.exoPlayer ?: return
        val converter = SubsonicMediaItemConverter(castPreferences)
        val itemIds = mq.getItemIds() ?: return
        if (itemIds.isEmpty()) return

        val localItems = itemIds.toList().mapNotNull { id ->
            val queueItem = mq.getItemAtIndex(id) ?: return@mapNotNull null
            val mediaItem = converter.toMediaItem(queueItem)
            // Defensive: skip items with no URI — their localConfiguration is null,
            // which would NPE in DefaultMediaSourceFactory if set on ExoPlayer.
            if (mediaItem.localConfiguration?.uri == null) return@mapNotNull null
            mediaItem
        }
        if (localItems.isEmpty()) return

        val currentItemId = rmc.mediaStatus?.currentItemId ?: return
        val currentIdx = itemIds.indexOf(currentItemId).coerceAtLeast(0)
        val positionMs = rmc.approximateStreamPosition

        ep.setMediaItems(localItems, currentIdx, positionMs)
        android.util.Log.d("ftpmusic-cast", "[Cast] syncRemoteQueueToLocal: ${localItems.size} items, idx=$currentIdx")
    }

    /**
     * Push local queue changes to the Cast receiver during Cast.
     * Called by PlaybackManager via castQueueListener callback.
     */
    @VisibleForTesting
    internal fun syncLocalToRemote(action: CastQueueAction) {
        val rmc = castCtx?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        if (action is CastQueueAction.ClearAndPlay && action.mediaItems.isNotEmpty()) {
            lastPreloadedItemId = null
            lastAdvanceItemId = null
        }
        val converter = SubsonicMediaItemConverter(castPreferences)
        val revision = playbackManager.getLastQueueSnapshot()?.revision ?: 0L
        var pending: com.google.android.gms.common.api.PendingResult<*>? = null
        var skipAck = action is CastQueueAction.JumpTo
        var playerReplaceSuccess = false
        CastQueueCommandExecutor(converter::toMediaQueueItem).execute(
            action,
            object : CastQueueReceiver {
                override val itemIds: Set<Int>
                    get() = castMediaQueue?.getItemIds()?.toSet().orEmpty()

                override fun insert(item: MediaQueueItem, beforeItemId: Int) {
                    pending = rmc.queueInsertItems(arrayOf(item), beforeItemId, null)
                }

                override fun remove(itemId: Int) {
                    pending = rmc.queueRemoveItems(intArrayOf(itemId), null)
                }

                override fun move(itemId: Int, beforeItemId: Int) {
                    pending = rmc.queueReorderItems(intArrayOf(itemId), beforeItemId, null)
                }

                override fun jumpTo(itemId: Int) {
                    rmc.queueJumpToItem(itemId, null)
                    skipAck = true
                }

                override fun replaceThroughPlayer(
                    items: List<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long,
                ): Boolean {
                    val cp = castPlayer
                    if (remoteCastPlayer?.isCastSessionAvailable() != true || cp == null) return false
                    android.util.Log.d(
                        "ftpmusic-cast",
                        "[Cast] ClearAndPlay: ${items.size} items via CastPlayer.setMediaItems, startIdx=$startIndex, pos=$startPositionMs",
                    )
                    cp.setMediaItems(items, startIndex, startPositionMs)
                    playerReplaceSuccess = true
                    return true
                }

                override fun load(items: List<MediaQueueItem>, startIndex: Int, startPositionMs: Long) {
                    pending = rmc.queueLoad(
                        items.toTypedArray(),
                        startIndex,
                        MediaStatus.REPEAT_MODE_REPEAT_OFF,
                        startPositionMs,
                        null,
                    )
                }
            },
        )
        if (skipAck) return
        val captured = pending
        if (captured != null) {
            captured.setResultCallback { result ->
                playbackManager.onCastCommandAck(revision, result.status.isSuccess)
            }
        } else if (playerReplaceSuccess || action is CastQueueAction.ClearAndPlay) {
            playbackManager.onCastCommandAck(revision, true)
        } else {
            // Missing remote item: local already mutated; commit.
            playbackManager.onCastCommandAck(revision, true)
        }
    }
}
