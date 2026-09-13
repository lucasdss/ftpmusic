package com.lucasdss.ftpmusic.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackNotificationProvider(
    private val context: Context,
    private val authHelper: SubsonicAuthHelper,
    private val stateProvider: () -> PlaybackState,
    private val notificationsEnabled: () -> Boolean = { true },
) : MediaNotification.Provider {

    companion object {
        const val CHANNEL_ID = "ftpmusic_playback"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "ftpmusic-notif"
        private const val MAX_URL_CACHE_ENTRIES = 128

        /**
         * Idempotent channel creation. MUST run before the FIRST
         * startForeground (MediaService.onCreate posts the placeholder to
         * [CHANNEL_ID] before the provider — and therefore this class's init —
         * exists). A startForeground notification targeting a channel that
         * does not exist yet is rejected as "Bad notification for
         * startForeground" and kills the process on a fresh install
         * (CannotPostForegroundServiceNotificationException, Android 12+).
         */
        fun ensureChannel(context: Context) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Playback",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "Shows current track and playback controls"
                        setShowBadge(false)
                        setSound(null, null)
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }

    private var lastSession: MediaSession? = null
    private var lastActionFactory: MediaNotification.ActionFactory? = null
    private var lastCallback: MediaNotification.Provider.Callback? = null
    private var lastCoverArtId: String? = null

    @Volatile
    private var coverArtBitmap: Bitmap? = null
    private var coverArtJob: Job? = null

    /** P6: memoization — skip a full rebuild + post when nothing that renders
     *  changed (isPlaying, title/artist, cover, cast device, volume toggle).
     *  Position ticks never reach buildNotification (it is called on state
     *  events only), but play-pause + track transitions double-fire
     *  (app listener + media3 session events); this dedupes them. */
    private var lastBuildKey: String? = null
    private var lastBuiltNotification: MediaNotification? = null
    private var lastPostedKey: String? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val imageLoader = ImageLoader.Builder(context)
        .crossfade(true)
        .build()

    private val appIcon by lazy {
        context.resources.getDrawable(com.lucasdss.ftpmusic.app.R.mipmap.ic_launcher, null)
            .toBitmap(128, 128)
    }

    init {
        ensureChannel(context)
    }

    fun dispose() {
        coverArtJob?.cancel()
        scope.cancel()
        imageLoader.shutdown()
    }

    override fun createNotification(
        session: MediaSession,
        customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        lastSession = session
        lastActionFactory = actionFactory
        lastCallback = onNotificationChangedCallback
        return buildNotification(session, actionFactory)
    }

    /** Everything that renders in the notification — the memoization key. */
    private fun buildKey(state: PlaybackState): String =
        "${state.title}|${state.artist}|${state.isPlaying}|${state.coverArtId}|" +
            "${state.castDeviceName}|${state.volume}|${notificationsEnabled()}"

    internal fun buildNotification(
        session: MediaSession,
        actionFactory: MediaNotification.ActionFactory,
    ): MediaNotification {
        // Authoritative source: re-derive from the live player so the
        // notification always reflects the ACTUAL current track, immune to
        // StateFlow/event-order races on auto-advance. The cached state
        // (stateProvider) is used only as fallback for non-player fields
        // (starred/rating/sleep timer) and when no player is attached.
        val state = resolveNotificationState(stateProvider())

        // P6: return the memoized notification when nothing changed.
        val key = buildKey(state)
        if (key == lastBuildKey && lastBuiltNotification != null) {
            return lastBuiltNotification!!
        }
        lastBuildKey = key

        val title = state.title ?: "ftpmusic"
        val artist = state.artist ?: ""
        val isPlaying = state.isPlaying
        val isCasting = state.isCasting
        val castDevice = state.castDeviceName
        val coverArtId = state.coverArtId

        // v46: feature toggle — OFF keeps the FGS-satisfying minimal silent
        // notification, without media controls, artwork, or lock-screen
        // integration. The MediaSession still handles lock-screen controls on
        // other devices while this notification is absent of controls.
        if (!notificationsEnabled()) {
            val minimal = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(if (artist.isNotEmpty()) artist else "Playing…")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            return MediaNotification(NOTIFICATION_ID, minimal.build()).also { lastBuiltNotification = it }
        }

        // Load cover art async if changed
        if (coverArtId != lastCoverArtId) {
            lastCoverArtId = coverArtId
            loadCoverArtAsync(coverArtId)
        }

        val largeIcon = coverArtBitmap ?: appIcon

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(
                when {
                    isCasting && castDevice != null -> "$artist — Casting to $castDevice"
                    artist.isNotEmpty() -> artist
                    else -> "Playing…"
                },
            )
            .setSubText("FTP Music")
            .setLargeIcon(largeIcon)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)

        // Link notification to MediaSession for lock screen / Quick Settings controls
        val platformToken = session.platformToken
        val mediaStyle = MediaNotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        if (platformToken != null) {
            try {
                val compatToken = android.support.v4.media.session.MediaSessionCompat.Token.fromToken(
                    platformToken as Any,
                )
                mediaStyle.setMediaSession(compatToken)
            } catch (_: Exception) {
                Log.w(TAG, "setMediaSession failed, lock screen controls unavailable")
            }
        }
        builder.setStyle(mediaStyle)

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder.setContentIntent(openIntent)

        // Play/Pause action
        val playPauseAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(
                context,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            ),
            if (isPlaying) "Pause" else "Play",
            Player.COMMAND_PLAY_PAUSE,
        )
        builder.addAction(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            playPauseAction.actionIntent,
        )
        val nextAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(context, android.R.drawable.ic_media_next),
            "Next",
            Player.COMMAND_SEEK_TO_NEXT,
        )
        builder.addAction(android.R.drawable.ic_media_next, "Next", nextAction.actionIntent)
        val prevAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(context, android.R.drawable.ic_media_previous),
            "Previous",
            Player.COMMAND_SEEK_TO_PREVIOUS,
        )
        builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevAction.actionIntent)

        return MediaNotification(NOTIFICATION_ID, builder.build()).also { lastBuiltNotification = it }
    }

    /** Force notification update when playback state changes (e.g. new track, pause/resume). */
    fun notifyChanged() {
        val session = lastSession ?: return
        val factory = lastActionFactory ?: return
        // P6: skip redundant posts when nothing that renders changed — play-pause
        // and track transitions double-fire (app listener + media3 session
        // events); the second post is byte-identical.
        val key = buildKey(resolveNotificationState(stateProvider()))
        if (key == lastPostedKey) return
        lastPostedKey = key
        try {
            // Use the callback if available (set by Media3 after first createNotification call)
            lastCallback?.onNotificationChanged(buildNotification(session, factory))
        } catch (_: Exception) {
            // Fallback: post directly to NotificationManager (first call before Media3 init)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(session, factory).notification)
        }
    }

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        MediaNotification.Provider.NotificationChannelInfo(CHANNEL_ID, "Playback")

    override fun handleCustomCommand(session: MediaSession, action: String, extras: android.os.Bundle): Boolean = false

    private fun loadCoverArtAsync(coverArtId: String?) {
        coverArtJob?.cancel()
        if (coverArtId == null) {
            coverArtBitmap = null
            // Invalidate the rebuild cache — the bitmap is not part of the key.
            lastBuildKey = null
            lastPostedKey = null
            notifyChanged()
            return
        }
        coverArtJob = scope.launch {
            try {
                val url = buildCoverArtUrl(coverArtId)
                val bitmap = imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(Size(256, 256))
                        .build(),
                ).drawable?.let {
                    android.graphics.Bitmap.createBitmap(
                        it.intrinsicWidth.coerceAtLeast(1),
                        it.intrinsicHeight.coerceAtLeast(1),
                        android.graphics.Bitmap.Config.ARGB_8888,
                    ).also { bmp ->
                        val canvas = android.graphics.Canvas(bmp)
                        it.setBounds(0, 0, canvas.width, canvas.height)
                        it.draw(canvas)
                    }
                }
                if (bitmap != null) {
                    coverArtBitmap = bitmap
                    // Force a rebuild so the freshly loaded artwork actually posts.
                    lastBuildKey = null
                    lastPostedKey = null
                    withContext(Dispatchers.Main) { notifyChanged() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load cover art", e)
                coverArtBitmap = null
            }
        }
    }

    internal fun buildCoverArtUrl(coverArtId: String): String {
        // Key by (coverArtId, baseUrl): DynamicBaseUrl resets after process
        // death and is restored from SecureStorage — a stale cached URL would
        // carry the old auth parameters and 401 until the id changes.
        val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        return urlCache.getOrPut("$coverArtId|$base") {
            val username = com.lucasdss.ftpmusic.app.di.SubsonicCredentials.username
            val password = com.lucasdss.ftpmusic.app.di.SubsonicCredentials.password
            val authParams = authHelper.buildAuthParams(username, password)
            val params = authParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            "$base/rest/getCoverArt?id=$coverArtId&$params&size=256"
        }
    }

    /** coverArtId|base → URL cache, capped (P9): grows one entry per id seen. */
    private val urlCache = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_URL_CACHE_ENTRIES
    }
}

/**
 * Resolve the notification's authoritative state: re-derive from the live
 * player so the notification always reflects the ACTUAL current track (immune
 * to StateFlow/event-order races on auto-advance), falling back to the cached
 * state when no player is attached or when off the main thread. Cached state
 * still feeds the non-player fields (starred/rating/sleep timer) via
 * fromPlayer's prevState.
 */
internal fun resolveNotificationState(cached: PlaybackState): PlaybackState {
    val player = PlayerHolder.player ?: return cached
    // Media3 players (SimpleBasePlayer/CastPlayer) must only be accessed on the
    // application (main) thread — accessing them from Dispatchers.IO (e.g.
    // MediaService.onCreate's restore coroutine) throws IllegalStateException.
    // Off-thread callers read the already-fresh cached StateFlow instead.
    if (!isOnMainThread()) return cached
    return PlaybackState.fromPlayer(player, cached)
}

internal fun isOnMainThread(): Boolean {
    val main = Looper.getMainLooper()
    val current = Looper.myLooper()
    // No looper at all (plain-JVM unit tests) → treat as main so the
    // re-derive logic stays directly testable.
    return main == null || current == main
}
