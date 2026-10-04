package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import com.google.gson.Gson
import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.QueueJournalDao
import com.lucasdss.ftpmusic.app.data.db.QueueJournalEntity
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.util.MimeTypeResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Centralizes playback commands: play album, shuffle album, play single track, add to queue.
 * Builds MediaItems from Track objects + pre-built stream URLs,
 * delegates to QueueManager.
 */
@Singleton
class PlaybackManager @Inject constructor(
    private val queueManager: QueueManager,
    private val persistenceManager: QueuePersistenceManager,
    private val offlineModeManager: OfflineModeManager,
    private val downloadManager: DownloadManager,
    private val castPreferences: CastPreferences,
    private val appContext: android.app.Application,
    private val queueJournalDao: QueueJournalDao,
    private val dualQueue: DualQueueManager,
    private val optimist: OptimisticQueueDelegate,
    private val storage: com.lucasdss.ftpmusic.app.data.security.SecureStorage? = null,
) {
    // Secondary constructor without scope (for tests that don't need custom scope)
    constructor(
        queueManager: QueueManager,
        persistenceManager: QueuePersistenceManager,
        offlineModeManager: OfflineModeManager,
        downloadManager: DownloadManager,
        castPreferences: CastPreferences,
        appContext: android.app.Application,
        queueJournalDao: QueueJournalDao,
    ) : this(
        queueManager,
        persistenceManager,
        offlineModeManager,
        downloadManager,
        castPreferences,
        appContext,
        queueJournalDao,
        newDualQueuePair(),
    )

    // Secondary constructor with scope — defaults to IO
    constructor(
        queueManager: QueueManager,
        persistenceManager: QueuePersistenceManager,
        offlineModeManager: OfflineModeManager,
        downloadManager: DownloadManager,
        castPreferences: CastPreferences,
        appContext: android.app.Application,
        queueJournalDao: QueueJournalDao,
        scope: CoroutineScope,
    ) : this(
        queueManager,
        persistenceManager,
        offlineModeManager,
        downloadManager,
        castPreferences,
        appContext,
        queueJournalDao,
        newDualQueuePair(),
    ) {
        this.scope = scope
    }

    /**
     * Test constructors must pair the [dualQueue] and [optimist] on ONE
     * [DualQueueManager] instance — a separate instance per field would make
     * `optimist` mutate a queue the manager never reads, silently dropping
     * priority-queue mutations (addToQueue) in every test-built manager.
     */
    private constructor(
        queueManager: QueueManager,
        persistenceManager: QueuePersistenceManager,
        offlineModeManager: OfflineModeManager,
        downloadManager: DownloadManager,
        castPreferences: CastPreferences,
        appContext: android.app.Application,
        queueJournalDao: QueueJournalDao,
        queuePair: Pair<DualQueueManager, OptimisticQueueDelegate>,
    ) : this(
        queueManager, persistenceManager, offlineModeManager,
        downloadManager, castPreferences, appContext, queueJournalDao,
        queuePair.first, queuePair.second,
    )

    private companion object {
        fun newDualQueuePair(): Pair<DualQueueManager, OptimisticQueueDelegate> {
            val dualQueue = DualQueueManager()
            return dualQueue to OptimisticQueueDelegate(dualQueue)
        }
    }

    private var scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var journalCap: Int = 100
        private set

    @Volatile
    var continuousPlayEnabled: Boolean = true
        private set

    private val gson = Gson()

    /** Callback for bi-directional sync: local queue changes → Cast receiver during Cast. */
    @Volatile
    var castQueueListener: ((CastQueueAction) -> Unit)? = null

    /** Canonical queue size — reads directly from Player. */
    val queueSize: Int get() = PlayerHolder.player?.mediaItemCount ?: 0

    /** Size of the priority queue (user-added tracks via Play Next / Add to Queue). */
    val priorityQueueSize: Int get() = dualQueue.prioritySize

    /** Size of the context queue (the album/playlist/mix being played). */
    val contextSize: Int get() = dualQueue.contextSize

    /** Per-merged-index PRIORITY flags for Room / UI sectioning. */
    fun isPriorityFlags(): List<Boolean> = dualQueue.originIsContextFlags().map { !it }

    fun entryIds(): List<Int> = dualQueue.entryIds()

    fun peekNextEntryId(): Int = dualQueue.peekNextEntryId()

    /** Merged DualQueue size (context + priority) — authoritative phone queue length. */
    val dualQueueSize: Int get() = dualQueue.size

    // ── Overwrite protection ─────────────────────────────────────────────────
    data class PendingPlayback(
        val tracks: List<Track>,
        val urls: List<String>,
        val startIndex: Int = 0,
        val sourceType: String? = null,
        val sourceId: String? = null,
        val sourceName: String? = null,
        val shuffled: Boolean = false,
    )

    @Volatile
    var pendingPlayback: PendingPlayback? = null
        private set

    /** Current overwrite behavior from user setting (default ASK). */
    fun overwriteBehavior(): OverwriteBehavior = OverwriteBehavior.fromKey(
        storage?.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_QUEUE_OVERWRITE_BEHAVIOR),
    )

    /** Check if priority queue has items to protect. Returns true if start was immediate, false if blocked. */
    fun tryStartContext(
        tracks: List<Track>,
        urls: List<String>,
        startIndex: Int = 0,
        sourceType: String? = null,
        sourceId: String? = null,
        sourceName: String? = null,
    ): Boolean {
        val hasPriority = dualQueue.prioritySize > 0
        when (overwriteBehavior()) {
            // CLEAN: never ask — always clear priority and play the new context.
            OverwriteBehavior.CLEAN -> {
                dualQueue.clearPriority()
                playAlbum(
                    tracks,
                    urls,
                    startIndex,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    sourceName = sourceName,
                )
                return true
            }

            // PUSH: play the new context FIRST, preserve the old queue AFTER it.
            OverwriteBehavior.PUSH -> {
                pushContext(tracks, urls, startIndex, sourceType, sourceId, sourceName)
                return true
            }

            // ASK (default): block when priority has items so the user can choose.
            OverwriteBehavior.ASK -> {
                if (hasPriority) {
                    pendingPlayback = PendingPlayback(tracks, urls, startIndex, sourceType, sourceId, sourceName)
                    return false
                }
                playAlbum(
                    tracks,
                    urls,
                    startIndex,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    sourceName = sourceName,
                )
                return true
            }
        }
    }

    /**
     * Shuffle variant of [tryStartContext]: same Ask/Clean/Push semantics, but
     * the new context plays in shuffled order.
     */
    fun tryShuffleContext(
        tracks: List<Track>,
        urls: List<String>,
        sourceType: String? = null,
        sourceId: String? = null,
        sourceName: String? = null,
    ): Boolean {
        val hasPriority = dualQueue.prioritySize > 0
        when (overwriteBehavior()) {
            OverwriteBehavior.CLEAN -> {
                dualQueue.clearPriority()
                shuffleAlbum(tracks, urls, sourceType = sourceType, sourceId = sourceId, sourceName = sourceName)
                return true
            }

            OverwriteBehavior.PUSH -> {
                pushContext(tracks, urls, 0, sourceType, sourceId, sourceName, shuffled = true)
                return true
            }

            OverwriteBehavior.ASK -> {
                if (hasPriority) {
                    pendingPlayback =
                        PendingPlayback(tracks, urls, 0, sourceType, sourceId, sourceName, shuffled = true)
                    return false
                }
                shuffleAlbum(tracks, urls, sourceType = sourceType, sourceId = sourceId, sourceName = sourceName)
                return true
            }
        }
    }

    /**
     * PUSH behavior: the new context becomes the queue head, and the CURRENT
     * merged queue (old context + priority) is preserved and plays AFTER it.
     * Tracks already present in the new context are deduplicated (by id) so a
     * repeated "Push" of the same album does not grow the queue unboundedly.
     * When [shuffled] is true the pushed context is randomized first.
     */
    fun pushContext(
        tracks: List<Track>,
        urls: List<String>,
        startIndex: Int = 0,
        sourceType: String? = null,
        sourceId: String? = null,
        sourceName: String? = null,
        shuffled: Boolean = false,
    ) {
        val (alignedTracks, alignedUrls) = alignedTracksAndUrls(tracks, urls, "pushContext")
        if (alignedTracks.isEmpty()) return
        val existingMerged = dualQueue.getMerged()
        val existingIds = existingMerged.mapNotNull { it.mediaId.takeIf { id -> id.isNotEmpty() } }.toSet()
        var items = buildMediaItems(alignedTracks, alignedUrls)
        if (shuffled) items = items.shuffled()
        val si = startIndex.coerceIn(0, alignedTracks.lastIndex)

        optimist.snapshot()
        // New context = the pushed tracks (dedup against existing queue by id).
        val dedupedItems = items.filter { it.mediaId !in existingIds }
        dualQueue.setContextWithSource(
            if (dedupedItems.isEmpty()) items else dedupedItems,
            sourceName ?: "this queue",
        )
        // Preserve the old merged queue AFTER the new context (as priority).
        dualQueue.clearPriority()
        existingMerged.forEach { dualQueue.addToQueue(it) }
        val merged = dualQueue.getMerged()
        playMergedQueue(merged, if (si < dualQueue.contextSize) si else 0)
        if (sourceType != null && sourceId != null) {
            journalQueue(sourceType, sourceId, sourceName, alignedTracks.map { it.id }, si)
        }
        emitClearAndPlayOrCommit(merged, si)
    }

    /** Resolve a blocked context start: clear priority and play, or keep priority and discard. */
    fun resolveOverwrite(clearAndPlay: Boolean) {
        val pending = pendingPlayback ?: return
        pendingPlayback = null
        if (clearAndPlay) {
            dualQueue.clearPriority()
        }
        // If user chose to keep the queue, don't start the new context — just discard
        if (!clearAndPlay) return
        if (pending.shuffled) {
            shuffleAlbum(
                pending.tracks,
                pending.urls,
                sourceType = pending.sourceType,
                sourceId = pending.sourceId,
                sourceName = pending.sourceName,
            )
        } else {
            playAlbum(
                pending.tracks,
                pending.urls,
                pending.startIndex,
                sourceType = pending.sourceType,
                sourceId = pending.sourceId,
                sourceName = pending.sourceName,
            )
        }
    }

    @Volatile
    var onOverwriteRequired: ((String) -> Unit)? = null

    fun setJournalCap(cap: Int) {
        journalCap = cap.coerceIn(10, 500)
    }

    fun setContinuousPlayEnabled(enabled: Boolean) {
        continuousPlayEnabled = enabled
    }

    // Maps trackId → original server URL for queue URL swapping when Cast connects/disconnects
    private val trackInfoMap = mutableMapOf<String, TrackInfo>()

    /** True while URL-only swap is happening — suppress onMediaItemTransition side effects. */
    @Volatile
    var isUrlSwapInProgress = false

    /** Lightweight cached track metadata — available even when Cast device doesn't report it. */
    data class TrackInfo(
        val url: String,
        val localUrl: String,
        val castUrl: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val mimeType: String = "audio/mpeg",
    )

    private fun logWarn(tag: String, msg: String) {
        android.util.Log.w("ftpmusic-playback", "[$tag] $msg")
    }

    private fun ensurePlayer() {
        if (PlayerHolder.player == null) {
            MediaServiceStartRequest.foregroundRequested = true
            val intent = android.content.Intent(appContext, MediaService::class.java)
            intent.action = MediaServiceStartRequest.ACTION_PLAYBACK
            // minSdk is 26 — startForegroundService is always available
            try {
                appContext.startForegroundService(intent)
            } catch (e: Exception) {
                MediaServiceStartRequest.foregroundRequested = false
                // ForegroundServiceStartNotAllowedException (API 31+) when a
                // play action fires while the app is backgrounded — the service
                // will start on the next foreground resume instead.
                android.util.Log.w(
                    "ftpmusic-playback",
                    "[ensurePlayer] startForegroundService failed: ${e.message}",
                )
            }
            // Don't block main thread — service will be ready on next call
        }
    }

    fun playAlbum(
        tracks: List<Track>,
        streamUrls: List<String>,
        startIndex: Int = 0,
        skipPersistence: Boolean = false,
        sourceType: String? = null,
        sourceId: String? = null,
        sourceName: String? = null,
    ) {
        ensurePlayer()
        val (alignedTracks, alignedUrls) = alignedTracksAndUrls(tracks, streamUrls, "playAlbum")
        if (alignedTracks.isEmpty()) return
        val items = buildMediaItems(alignedTracks, alignedUrls)
        val si = startIndex.coerceIn(0, alignedTracks.lastIndex)

        optimist.snapshot()
        // Dual-queue: set context at startIndex so PRIORITY sits after current (industry).
        if (sourceType != null) dualQueue.clearPriority()
        if (sourceName != null) {
            dualQueue.setContextWithSource(items, sourceName, si)
        } else {
            dualQueue.setContext(items, si)
        }
        val merged = dualQueue.getMerged()
        playMergedQueue(merged, si)
        enqueuePlayQueue(alignedTracks, alignedUrls, si)
        if (!skipPersistence) persistenceSave(alignedTracks, alignedUrls, si)

        if (sourceType != null && sourceId != null) {
            journalQueue(sourceType, sourceId, sourceName, alignedTracks.map { it.id }, si)
        }

        emitClearAndPlayOrCommit(merged, si)
    }

    /**
     * Restore a persisted queue into the DUAL queue (industry layout).
     * Origin SoT is [isPriorityFlags]. [contextSize] is ignored.
     * Missing flags: treat all CONTEXT; Dual stamps fresh ids.
     *
     * @return true when the dual/Exo queue was rebuilt; false on empty/no-op
     * (callers that skipped a follow-up seek must fall back).
     */
    fun restoreQueue(
        tracks: List<Track>,
        streamUrls: List<String>,
        startIndex: Int = 0,
        @Suppress("UNUSED_PARAMETER") contextSize: Int = -1,
        isPriorityFlags: List<Boolean>? = null,
        entryIds: List<Int>? = null,
        nextEntryId: Int = 0,
        positionMs: Long = 0L,
        isAutoplayFlags: List<Boolean>? = null,
    ): Boolean {
        ensurePlayer()
        val (alignedTracks, alignedUrls) = alignedTracksAndUrls(tracks, streamUrls, "restoreQueue")
        if (alignedTracks.isEmpty()) return false
        val n = alignedTracks.size
        var items = buildMediaItems(alignedTracks, alignedUrls)
        // Prefix-align parallel arrays when tracks/urls were coerced shorter.
        val ids = alignParallelList(entryIds, n, tracks.size)
        if (ids != null) {
            items = items.mapIndexed { i, item ->
                if (ids[i] > 0) item.withQueueEntryId(ids[i]) else item
            }
        }
        val autoplay = alignParallelList(isAutoplayFlags, n, tracks.size)
        if (autoplay != null) {
            items = items.mapIndexed { i, item ->
                if (autoplay[i]) item.withAutoplay(true) else item
            }
        }
        val flags = alignParallelList(isPriorityFlags, n, tracks.size)
        if (flags != null) {
            dualQueue.restoreEntries(
                items.zip(flags).map { (item, isPri) ->
                    DualQueueManager.SnapshotEntry(item, isContext = !isPri)
                },
            )
            dualQueue.alignAnchorToMergedIndex(startIndex.coerceIn(0, items.lastIndex))
        } else {
            dualQueue.setContext(items, startIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)))
        }
        if (nextEntryId > 0) dualQueue.adoptNextEntryId(nextEntryId)
        val merged = dualQueue.getMerged()
        val si = startIndex.coerceIn(0, merged.lastIndex.coerceAtLeast(0))
        queueManager.playAll(merged, si, positionMs)
        enqueuePlayQueue(alignedTracks, alignedUrls, si)
        return true
    }

    fun shuffleAlbum(
        tracks: List<Track>,
        streamUrls: List<String>,
        sourceType: String? = null,
        sourceId: String? = null,
        sourceName: String? = null,
    ) {
        ensurePlayer()
        val (alignedTracks, alignedUrls) = alignedTracksAndUrls(tracks, streamUrls, "shuffleAlbum")
        if (alignedTracks.isEmpty()) return
        val items = buildMediaItems(alignedTracks, alignedUrls)
        val shuffled = items.shuffled()

        optimist.snapshot()
        // Dual-queue: set context (shuffled). Clear priority on explicit user-initiated context starts.
        if (sourceType != null) dualQueue.clearPriority()
        if (sourceName != null) {
            dualQueue.setContextWithSource(shuffled, sourceName)
        } else {
            dualQueue.setContext(shuffled)
        }
        val merged = dualQueue.getMerged()
        playMergedQueue(merged, 0)
        enqueuePlayQueue(alignedTracks, alignedUrls, 0)
        persistenceSave(alignedTracks, alignedUrls, 0)

        if (sourceType != null && sourceId != null) {
            journalQueue(sourceType, sourceId, sourceName, alignedTracks.map { it.id }, 0)
        }

        emitClearAndPlayOrCommit(merged, 0)
    }

    fun playSingleTrack(
        track: Track,
        streamUrl: String,
        sourceType: String? = null,
        sourceId: String? = null,
        sourceName: String? = null,
    ) {
        ensurePlayer()
        val mimeType = MimeTypeResolver.resolve(track.contentType, track.suffix)
        val item = queueManager.buildMediaItem(
            id = track.id, title = track.title, url = resolveStreamUrl(track.id, streamUrl),
            artist = track.artist, album = track.album,
            durationMs = (track.duration ?: 0) * 1000L, coverArt = track.coverArt,
            artistId = track.artistId, albumId = track.albumId,
            mimeType = mimeType,
        )
        optimist.snapshot()
        dualQueue.setSingleContextWithSource(item, sourceName ?: track.title)
        val merged = dualQueue.getMerged()
        playMergedQueue(merged, 0)
        persistenceSave(listOf(track), listOf(streamUrl), 0)

        if (sourceType != null && sourceId != null) {
            journalQueue(sourceType, sourceId, sourceName, listOf(track.id), 0)
        }

        emitClearAndPlayOrCommit(listOf(item), 0)
    }

    /**
     * Play a live radio stream URL directly — no proxy, no caching.
     * Enters Dual as single-item CONTEXT (keeps PRIORITY), matching [playSingleTrack]
     * market keep-Up-Next semantics (ADR 0067).
     */
    fun playStream(url: String, title: String) {
        ensurePlayer()
        val item = queueManager.buildMediaItem(
            id = "radio:${url.hashCode()}",
            title = title,
            url = url,
            durationMs = 0L,
            mediaType = "radio",
        ).ensureEntryId()
        optimist.snapshot()
        dualQueue.setSingleContextWithSource(item, title)
        val merged = dualQueue.getMerged()
        playMergedQueue(merged, 0)
        val (tracks, urls) = buildQueueStateFromDual()
        if (tracks.isEmpty()) {
            scope.launch { persistenceManager.clear() }
        } else {
            persistenceSave(tracks, urls, 0)
        }
        emitClearAndPlayOrCommit(merged, 0)
    }

    fun playNext(track: Track, streamUrl: String) {
        val mimeType = MimeTypeResolver.resolve(track.contentType, track.suffix)
        val item = queueManager.buildMediaItem(
            id = track.id, title = track.title, url = resolveStreamUrl(track.id, streamUrl),
            artist = track.artist, album = track.album,
            durationMs = (track.duration ?: 0) * 1000L, coverArt = track.coverArt,
            artistId = track.artistId, albumId = track.albumId,
            mimeType = mimeType,
        ).ensureEntryId()
        // Insert immediately after the canonical current item. The receiver may
        // trim played items, so derive the anchor from mediaId before mutation.
        val currentIndex = currentCanonicalIndex()
        optimist.playNextOptimistic(item, currentIndex)
        val inserted = dualQueue.getMerged().firstOrNull { it.queueEntryId() == item.queueEntryId() } ?: item
        val insertIndex = dualQueue.getMerged().indexOfFirst { it.queueEntryId() == item.queueEntryId() }
            .coerceAtLeast(0)
        // Local: insert without replacing timeline (same idea as addToQueue).
        if (!PlayerHolder.isCasting) {
            PlayerHolder.player?.addMediaItem(insertIndex, inserted)
        } else {
            syncDualQueueToPlayer()
        }
        // Persist Dual SoT (ADR 0067) — Player is projection only.
        val (tracks, urls) = buildQueueStateFromDual()
        persistenceSave(tracks, urls, currentCanonicalIndex().coerceAtLeast(0))
        PlayerHolder.player?.let { updateNextTrackPreview(it) }
        // Bi-directional sync during Cast: item inserted after current
        emitCastOrCommit {
            val beforeEntryId = dualQueue.getMerged().getOrNull(currentIndex + 2)?.queueEntryId()
                ?.takeIf { it > 0 }
            CastQueueAction.Add(inserted, beforeEntryId)
        }
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playback",
            "queueEdit action=playNext trackId=${track.id} casting=${PlayerHolder.isCasting} size=${dualQueue.size}",
        )
    }

    fun addAllToQueue(tracks: List<Track>, urls: List<String>) {
        val items = buildMediaItems(tracks, urls).map { it.ensureEntryId() }
        optimist.snapshot()
        dualQueue.addAllToQueue(items)
        // Append to the player WITHOUT replacing the timeline — the current
        // track keeps playing with no gap. During Cast the remote queue is
        // updated exclusively via the Add events below (old behavior replaced
        // the remote queue with the merged list AND appended again → dups).
        if (!PlayerHolder.isCasting) {
            PlayerHolder.player?.addMediaItems(items)
        } else {
            syncDualQueueToPlayer()
        }
        val (allTracks, allUrls) = buildQueueStateFromDual()
        persistenceSave(allTracks, allUrls, currentCanonicalIndex().coerceAtLeast(0))
        // Bi-directional sync during Cast
        emitCastAddsOrCommit(items)
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playback",
            "queueEdit action=addAll count=${items.size} casting=${PlayerHolder.isCasting} size=${dualQueue.size}",
        )
    }

    /**
     * Append tracks to the END of the CONTEXT queue — the continuous-play
     * continuation path. Unlike [addToQueue] (user-added priority), these
     * extend the current mix, so the context/priority split (contextSize)
     * stays correct across a Cast disconnect (a continuation is context, not
     * user-added priority).
     *
     * @param asAutoplay when true, stamps [IS_AUTOPLAY_EXTRA] so Queue UI can
     *   render an Autoplay section separate from Continue Playing (ADR-0053).
     */
    fun appendToContext(tracks: List<Track>, streamUrls: List<String>, asAutoplay: Boolean = false) {
        val (alignedTracks, alignedUrls) = alignedTracksAndUrls(tracks, streamUrls, "appendToContext")
        if (alignedTracks.isEmpty()) return
        val items = buildMediaItems(alignedTracks, alignedUrls)
            .map { it.ensureEntryId() }
            .map { if (asAutoplay) it.withAutoplay(true) else it }
        optimist.snapshot()
        dualQueue.addAllToContext(items)
        if (!PlayerHolder.isCasting) {
            PlayerHolder.player?.addMediaItems(items)
        } else {
            syncDualQueueToPlayer()
        }
        val player = PlayerHolder.player ?: return
        // Persist the DUAL queue (authoritative, captures the grown contextSize).
        val (allTracks, allUrls) = buildQueueStateFromDual()
        persistenceSave(allTracks, allUrls, currentCanonicalIndex())
        // Bi-directional sync during Cast — append at the end of the remote queue.
        emitCastAddsOrCommit(items)
    }

    /** Per merged index: Continuous Play autoplay-tail rows. */
    fun isAutoplayFlags(): List<Boolean> = dualQueue.getMerged().map { it.isAutoplay() }

    /**
     * Remove only Autoplay-stamped CONTEXT rows (Keep Queue + Continue Playing).
     */
    fun clearAutoplayQueue() {
        val merged = dualQueue.getMerged()
        val removeIndices = merged.mapIndexedNotNull { i, item ->
            if (item.isAutoplay()) i else null
        }.asReversed()
        if (removeIndices.isEmpty()) return
        optimist.snapshot()
        for (i in removeIndices) {
            dualQueue.remove(i)
        }
        if (!PlayerHolder.isCasting) {
            val player = PlayerHolder.player
            if (player != null) {
                for (i in removeIndices) {
                    if (i in 0 until player.mediaItemCount) player.removeMediaItem(i)
                }
            }
        } else {
            syncDualQueueToPlayer()
        }
        val (tracks, urls) = buildQueueStateFromDual()
        if (tracks.isEmpty()) {
            scope.launch { persistenceManager.clear() }
        } else {
            persistenceSave(tracks, urls, currentCanonicalIndex().coerceAtLeast(0))
        }
        emitClearAndPlayOrCommit(dualQueue.getMerged(), currentCanonicalIndex().coerceAtLeast(0))
    }

    fun addToQueue(track: Track, streamUrl: String) {
        val mimeType = MimeTypeResolver.resolve(track.contentType, track.suffix)
        val item = queueManager.buildMediaItem(
            id = track.id, title = track.title, url = resolveStreamUrl(track.id, streamUrl),
            artist = track.artist, album = track.album,
            durationMs = (track.duration ?: 0) * 1000L, coverArt = track.coverArt,
            artistId = track.artistId, albumId = track.albumId,
            mimeType = mimeType,
        ).ensureEntryId()
        // Dual-queue: append to priority queue with optimistic snapshot
        optimist.addToQueueOptimistic(item)
        // Append to the player without replacing the timeline (no playback gap).
        if (!PlayerHolder.isCasting) {
            PlayerHolder.player?.addMediaItems(listOf(item))
        } else {
            syncDualQueueToPlayer()
        }
        val (allTracks, allUrls) = buildQueueStateFromDual()
        persistenceSave(allTracks, allUrls, currentCanonicalIndex().coerceAtLeast(0))
        PlayerHolder.player?.let { updateNextTrackPreview(it) }
        // Bi-directional sync during Cast — -1 appends at the end of the remote
        // queue (mediaItemCount-1 pointed at the pre-append last item).
        emitCastOrCommit { CastQueueAction.Add(item) }
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playback",
            "queueEdit action=add trackId=${track.id} casting=${PlayerHolder.isCasting} size=${dualQueue.size}",
        )
    }

    fun playQueueItem(index: Int) {
        if (PlayerHolder.isCasting) {
            val entryId = dualQueue.getMerged().getOrNull(index)?.queueEntryId()?.takeIf { it > 0 } ?: return
            castQueueListener?.invoke(CastQueueAction.JumpTo(entryId))
        } else {
            queueManager.playFromIndex(index)
        }
    }

    fun removeFromQueue(index: Int) {
        val (removed, _) = optimist.removeOptimistic(index)
        if (removed == null) return
        if (!PlayerHolder.isCasting) {
            PlayerHolder.player?.removeMediaItem(index)
        } else {
            syncDualQueueToPlayer()
        }
        val (tracks, urls) = buildQueueStateFromDual()
        if (tracks.isEmpty()) {
            scope.launch { persistenceManager.clear() }
        } else {
            persistenceSave(tracks, urls, currentCanonicalIndex().coerceAtLeast(0))
        }
        emitCastOrCommit {
            CastQueueAction.Remove(removed.queueEntryId())
        }
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playback",
            "queueEdit action=remove idx=$index trackId=${removed.mediaId} casting=${PlayerHolder.isCasting} size=${dualQueue.size}",
        )
    }

    /**
     * Trim upcoming items after the current track (keep Now Playing).
     * Cast: ClearAndPlay remaining Dual. Persist Dual (ADR 0067); clear Room only
     * when nothing remains.
     */
    fun clearQueue() {
        val player = PlayerHolder.player
        if (player == null) {
            dualQueue.clear()
            scope.launch { persistenceManager.clear() }
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
                "ftpmusic-playback",
                "queueEdit action=clear casting=false size=0 noPlayer=true",
            )
            return
        }
        optimist.snapshot()
        val currentIdx = currentCanonicalIndex()
        if (currentIdx >= 0) {
            val total = dualQueue.size
            for (i in total - 1 downTo currentIdx + 1) {
                dualQueue.remove(i)
                if (!PlayerHolder.isCasting) {
                    player.removeMediaItem(i)
                }
            }
            if (PlayerHolder.isCasting) {
                syncDualQueueToPlayer()
            }
        } else {
            dualQueue.clear()
            if (!PlayerHolder.isCasting) {
                player.stop()
                player.clearMediaItems()
            } else {
                syncDualQueueToPlayer()
            }
        }
        val (tracks, urls) = buildQueueStateFromDual()
        if (tracks.isEmpty()) {
            scope.launch { persistenceManager.clear() }
        } else {
            persistenceSave(tracks, urls, currentCanonicalIndex().coerceAtLeast(0))
        }
        emitClearAndPlayOrCommit(dualQueue.getMerged(), currentCanonicalIndex().coerceAtLeast(0))
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playback",
            "queueEdit action=clear casting=${PlayerHolder.isCasting} size=${dualQueue.size}",
        )
    }

    private fun journalQueue(
        sourceType: String,
        sourceId: String,
        sourceName: String?,
        trackIds: List<String>,
        position: Int,
    ) {
        scope.launch {
            try {
                val json = gson.toJson(trackIds)
                queueJournalDao.upsert(
                    QueueJournalEntity(
                        sourceType = sourceType,
                        sourceId = sourceId,
                        sourceName = sourceName,
                        trackIdsJson = json,
                        position = position,
                    ),
                )
                queueJournalDao.evictIfNeeded(journalCap)
            } catch (_: Exception) { /* best-effort, never crash */ }
        }
    }

    /** Persists the current queue state from Dual SoT (ADR 0067). */
    fun persistCurrentQueue() {
        val (tracks, urls) = buildQueueStateFromDual()
        if (tracks.isEmpty()) {
            scope.launch { persistenceManager.clear() }
            return
        }
        persistenceSave(tracks, urls, currentCanonicalIndex().coerceAtLeast(0))
    }

    // ── Drag-reorder coalesce (Dual/UI during drag; one Exo move on release) ─
    @Volatile private var reorderDragActive = false
    private var reorderOriginIndex = -1
    private var reorderEntryId = 0
    private var reorderStartMediaId: String? = null
    private var reorderMoved = false

    /**
     * Start of a QueueScreen drag. Captures the Exo/Dual index of the row
     * before mid-drag Dual moves. Call from `draggableHandle(onDragStarted)`.
     */
    fun beginQueueReorder(entryId: Int, fromIndex: Int) {
        reorderDragActive = true
        reorderEntryId = entryId
        reorderOriginIndex = if (entryId > 0) {
            dualQueue.getMerged().indexOfFirst { it.queueEntryId() == entryId }
                .takeIf { it >= 0 } ?: fromIndex
        } else {
            fromIndex
        }
        reorderStartMediaId = dualQueue.getMerged().getOrNull(reorderOriginIndex)?.mediaId
        reorderMoved = false
    }

    /**
     * End of a QueueScreen drag. One `moveMediaItem` (local) or exo-mirror
     * sync (Cast), one persist, one Cast Move. Call from `onDragStopped`.
     */
    fun commitQueueReorder() {
        if (!reorderDragActive) return
        reorderDragActive = false
        if (!reorderMoved || reorderOriginIndex < 0) return
        val finalIndex = when {
            reorderEntryId > 0 ->
                dualQueue.getMerged().indexOfFirst { it.queueEntryId() == reorderEntryId }

            reorderStartMediaId != null ->
                dualQueue.getMerged().indexOfFirst { it.mediaId == reorderStartMediaId }

            else -> -1
        }
        if (finalIndex < 0) return
        val moved = dualQueue.getMerged()[finalIndex]
        if (!PlayerHolder.isCasting) {
            if (finalIndex != reorderOriginIndex) {
                PlayerHolder.player?.moveMediaItem(reorderOriginIndex, finalIndex)
            }
        } else {
            syncDualQueueToPlayer()
        }
        val (tracks, urls) = buildQueueStateFromDual()
        persistenceSave(tracks, urls, currentCanonicalIndex().coerceAtLeast(0))
        emitCastOrCommit {
            val beforeEntryId = dualQueue.getMerged().getOrNull(finalIndex + 1)?.queueEntryId()?.takeIf { it > 0 }
            CastQueueAction.Move(moved.queueEntryId(), beforeEntryId)
        }
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playback",
            "queueEdit action=reorder from=$reorderOriginIndex to=$finalIndex trackId=${moved.mediaId} casting=${PlayerHolder.isCasting}",
        )
    }

    /**
     * Reorder item in Dual queue. During an active drag ([beginQueueReorder]),
     * only Dual/UI update — Exo/persist/Cast wait for [commitQueueReorder].
     * Immediate (non-drag) callers still apply a gapless local `moveMediaItem`.
     */
    fun moveQueueItem(from: Int, to: Int) {
        if (from == to) return
        if (reorderDragActive) {
            if (!reorderMoved) optimist.snapshot()
            if (!dualQueue.move(from, to)) return
            reorderMoved = true
            return
        }
        val moved = dualQueue.getMerged().getOrNull(from) ?: return
        val (ok, _) = optimist.moveOptimistic(from, to)
        if (!ok) return
        if (!PlayerHolder.isCasting) {
            PlayerHolder.player?.moveMediaItem(from, to)
        } else {
            syncDualQueueToPlayer()
        }
        val (tracks, urls) = buildQueueStateFromDual()
        persistenceSave(tracks, urls, currentCanonicalIndex().coerceAtLeast(0))
        emitCastOrCommit {
            val beforeEntryId = dualQueue.getMerged().getOrNull(to + 1)?.queueEntryId()?.takeIf { it > 0 }
            CastQueueAction.Move(moved.queueEntryId(), beforeEntryId)
        }
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-playback",
            "queueEdit action=move from=$from to=$to trackId=${moved.mediaId} casting=${PlayerHolder.isCasting}",
        )
    }

    fun pause() {
        PlayerHolder.player?.pause()
    }

    fun destroy() {
        scope.cancel()
    }

    /** Roll back the last optimistic queue mutation if a background operation failed. */
    fun queueRollback(): Boolean {
        val ok = optimist.rollback()
        if (ok) {
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
                "ftpmusic-playback",
                "queueEdit action=rollback size=${dualQueue.size}",
            )
        }
        return ok
    }

    fun onCastCommandAck(revision: Long, success: Boolean) {
        if (success) {
            optimist.commitIf(revision)
            return
        }
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
            "ftpmusic-playback",
            "queueEdit action=castAckFail revision=$revision",
        )
        if (optimist.rollbackIf(revision)) {
            syncDualQueueToPlayer()
            val (tracks, urls) = buildQueueStateFromDual()
            if (tracks.isEmpty()) {
                scope.launch { persistenceManager.clear() }
            } else {
                persistenceSave(tracks, urls, currentCanonicalIndex().coerceAtLeast(0))
            }
        }
    }

    /** Get the last optimistic snapshot for UI state comparison. */
    fun getLastQueueSnapshot(): OptimisticQueueDelegate.Snapshot? = optimist.getLastSnapshot()

    /**
     * Local-first queue caching: the ENTIRE queue is enqueued so it is
     * progressively cached and playable offline.
     *
     *  - The next 3 tracks (streaming-continuity window) go at priority 0 —
     *    always downloaded, no Wi-Fi/battery gating, so playback never waits.
     *  - The remainder of the queue goes at priority 1 — gated by the
     *    standard constraints (Wi-Fi/battery/mobile-data) in the worker loop.
     *  - Completed rows that are still on disk are a no-op inside
     *    DownloadManager.enqueue (no re-download on every track advance).
     */
    fun enqueuePlayQueue(tracks: List<Track>, urls: List<String>, currentIndex: Int) {
        val (alignedTracks, alignedUrls) = alignedTracksAndUrls(tracks, urls, "enqueuePlayQueue")
        if (alignedTracks.isEmpty()) return
        val si = currentIndex.coerceIn(0, alignedTracks.lastIndex)
        val start = si + 1
        val urgentEnd = minOf(start + 3, alignedTracks.size)
        scope.launch(kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }) {
            for (i in start until alignedTracks.size) {
                try {
                    trackInfoMap[alignedTracks[i].id] =
                        TrackInfo(
                            url = alignedUrls[i],
                            localUrl = alignedUrls[i],
                            castUrl = alignedUrls[i],
                            title = alignedTracks[i].title,
                            artist = alignedTracks[i].artist,
                            album = alignedTracks[i].album,
                        ) // store for Cast URL swap
                    val priority = if (i < urgentEnd) 0 else 1
                    downloadManager.enqueue(alignedTracks[i].id, alignedUrls[i], priority = priority)
                } catch (e: Exception) {
                    logWarn("enqueuePlayQueue", e.message ?: "unknown error")
                    com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
                        "ftpmusic-cache",
                        "enqueuePlayQueue fail trackId=${alignedTracks.getOrNull(
                            i,
                        )?.id} priority=${if (i < urgentEnd) 0 else 1}",
                        e,
                    )
                }
            }
        }
    }

    /** Build queue state from ExoPlayer — always has the full queue for both players. */
    fun buildQueueState(): Pair<List<Track>, List<String>> {
        val player = PlayerHolder.exoPlayer ?: PlayerHolder.player ?: return Pair(emptyList(), emptyList())
        return buildQueueStateFromPlayer(player)
    }

    /**
     * Build queue state from the DUAL QUEUE's merged view (context + priority).
     * Sole Room persist SoT for local and Cast (ADR 0067). Player is projection only.
     */
    fun buildQueueStateFromDual(): Pair<List<Track>, List<String>> {
        val tracks = mutableListOf<Track>()
        val urls = mutableListOf<String>()
        for (item in dualQueue.getMerged()) {
            val (track, url) = trackAndUrlFromMediaItem(item)
            tracks.add(track)
            urls.add(url)
        }
        return Pair(tracks, urls)
    }

    internal fun buildQueueStateFromPlayer(player: androidx.media3.common.Player): Pair<List<Track>, List<String>> {
        val tracks = mutableListOf<Track>()
        val urls = mutableListOf<String>()
        for (i in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(i) ?: continue
            val (track, url) = trackAndUrlFromMediaItem(item)
            tracks.add(track)
            urls.add(url)
        }
        return Pair(tracks, urls)
    }

    /** Shared MediaItem → Track+URL mapping for Dual and Exo queue snapshots. */
    internal fun trackAndUrlFromMediaItem(item: MediaItem): Pair<Track, String> {
        val md = item.mediaMetadata
        val extras = md.extras
        val url = item.localConfiguration?.uri?.toString().orEmpty()
        val track = Track(
            id = item.mediaId,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString(),
            artistId = extras?.getString("artistId"),
            album = md.albumTitle?.toString(),
            albumId = extras?.getString("albumId"),
            coverArt = extractCoverArtId(md),
            duration = extras?.getLong("duration")?.div(1000)?.toInt(),
        )
        return track to url
    }

    private fun updateNextTrackPreview(player: androidx.media3.common.Player) {
        // Next track preview is handled by PlaybackState.fromPlayer
    }

    /** Cached track metadata map — keyed by trackId. For UI fallback when Cast device doesn't report metadata. */
    fun getTrackInfo(trackId: String): TrackInfo? = trackInfoMap[trackId]

    /**
     * Pair tracks with stream URLs at equal length. Mismatched lists used to
     * silently zip-truncate in [buildMediaItems] while [enqueuePlayQueue]
     * still indexed the full tracks list (OOB / wrong URL).
     */
    private fun alignedTracksAndUrls(
        tracks: List<Track>,
        urls: List<String>,
        tag: String,
    ): Pair<List<Track>, List<String>> {
        if (tracks.isEmpty()) return emptyList<Track>() to emptyList()
        if (urls.isEmpty()) {
            android.util.Log.w(
                "ftpmusic-playback",
                "[$tag] empty urls for ${tracks.size} tracks — no-op",
            )
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
                "ftpmusic-playback",
                "queueLengthGuard tag=$tag tracks=${tracks.size} urls=0",
            )
            return emptyList<Track>() to emptyList()
        }
        if (tracks.size == urls.size) return tracks to urls
        val n = minOf(tracks.size, urls.size)
        android.util.Log.w(
            "ftpmusic-playback",
            "[$tag] tracks/urls length mismatch tracks=${tracks.size} urls=${urls.size} — coerce to $n",
        )
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.w(
            "ftpmusic-playback",
            "queueLengthGuard tag=$tag tracks=${tracks.size} urls=${urls.size} coerce=$n",
        )
        return tracks.take(n) to urls.take(n)
    }

    /**
     * Align a parallel restore array to coerced queue length [n].
     * Same length → keep; sized for pre-coerce [originalSize] → prefix [take];
     * otherwise drop (caller falls back).
     */
    private fun <T> alignParallelList(list: List<T>?, n: Int, originalSize: Int): List<T>? {
        if (list == null) return null
        return when {
            list.size == n -> list
            list.size == originalSize && originalSize > n -> list.take(n)
            else -> null
        }
    }

    private fun buildMediaItems(tracks: List<Track>, urls: List<String>): List<androidx.media3.common.MediaItem> =
        tracks.zip(urls).map { (track, url) ->
            val localUrl = buildLocalUrl(track.id, url)
            trackInfoMap[track.id] = TrackInfo(
                url = url,
                localUrl = localUrl,
                castUrl = "",
                title = track.title,
                artist = track.artist,
                album = track.album,
            )
            // Always use proxy URL. SubsonicMediaItemConverter handles Cast URL resolution.
            val mimeType = MimeTypeResolver.resolve(track.contentType, track.suffix)
            queueManager.buildMediaItem(
                id = track.id, title = track.title, url = localUrl,
                artist = track.artist, album = track.album,
                artistId = track.artistId, albumId = track.albumId,
                durationMs = (track.duration ?: 0) * 1000L,
                coverArt = track.coverArt, mimeType = mimeType,
            )
        }

    private fun buildLocalUrl(trackId: String, serverUrl: String): String {
        // Handle legacy pipe-separated "localUrl|castUrl" format from old queue persistence
        val cleanUrl = serverUrl.substringBefore("|")
        // Legacy migration: extract embedded remote URL from old proxy-format URLs
        val migrated = if (cleanUrl.contains("127.0.0.1:9000/stream") && cleanUrl.contains("url=")) {
            val urlIdx = cleanUrl.indexOf("url=")
            val encoded = cleanUrl.substring(urlIdx + 4).substringBefore('&')
            try {
                java.net.URLDecoder.decode(encoded, "UTF-8")
            } catch (_: Exception) {
                cleanUrl
            }
        } else {
            cleanUrl
        }
        // mediaId/metadata must agree with stream URI id (cache key + bytes).
        return alignStreamUrlToTrackId(trackId, migrated)
    }

    private fun persistenceSave(
        tracks: List<Track>,
        urls: List<String>,
        index: Int,
        contextSize: Int = dualQueue.contextSize,
    ) {
        val safeCtx = contextSize.coerceIn(0, tracks.size)
        val flags = dualQueue.originIsContextFlags().map { !it }
            .let { f -> if (f.size == tracks.size) f else null }
        val autoplay = isAutoplayFlags().takeIf { it.size == tracks.size }
        val idx = index
        scope.launch(kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }) {
            persistenceManager.save(
                tracks,
                urls,
                idx,
                PlayerHolder.player?.currentPosition ?: 0L,
                safeCtx,
                flags,
                dualQueue.entryIds().takeIf { it.size == tracks.size },
                dualQueue.peekNextEntryId(),
                autoplay,
            )
        }
    }

    /** Clear manual Queue only (Spotify/Apple Clear) — keep Continue Playing. */
    fun clearPriorityQueue() {
        // Capture PRIORITY indices before Dual clear so local Exo can remove
        // high→low without setMediaItems (preserves currently playing CONTEXT).
        val priorityIndices = dualQueue.originIsContextFlags()
            .mapIndexedNotNull { i, isContext -> if (!isContext) i else null }
            .asReversed()
        optimist.snapshot()
        dualQueue.clearPriority()
        if (!PlayerHolder.isCasting) {
            val player = PlayerHolder.player
            if (player != null) {
                for (i in priorityIndices) {
                    if (i in 0 until player.mediaItemCount) player.removeMediaItem(i)
                }
            }
        } else {
            syncDualQueueToPlayer()
        }
        val (tracks, urls) = buildQueueStateFromDual()
        if (tracks.isEmpty()) {
            scope.launch { persistenceManager.clear() }
        } else {
            persistenceSave(tracks, urls, currentCanonicalIndex().coerceAtLeast(0))
        }
        emitClearAndPlayOrCommit(dualQueue.getMerged(), currentCanonicalIndex().coerceAtLeast(0))
    }

    private fun MediaItem.ensureEntryId(): MediaItem =
        if (hasQueueEntryId()) this else withQueueEntryId(dualQueue.peekNextEntryId())

    private fun emitCastOrCommit(action: () -> CastQueueAction) {
        if (PlayerHolder.isCasting) {
            castQueueListener?.invoke(action())
        } else {
            optimist.commit()
        }
    }

    private fun emitCastAddsOrCommit(items: List<MediaItem>) {
        if (PlayerHolder.isCasting) {
            for (item in items) {
                castQueueListener?.invoke(CastQueueAction.Add(item))
            }
        } else {
            optimist.commit()
        }
    }

    private fun emitClearAndPlayOrCommit(merged: List<MediaItem>, startIndex: Int) {
        if (PlayerHolder.isCasting) {
            castQueueListener?.invoke(CastQueueAction.ClearAndPlay(merged, startIndex))
        } else {
            optimist.commit()
        }
    }

    private fun resolveStreamUrl(trackId: String, serverUrl: String): String {
        return serverUrl // Direct URL — ExoPlayer CacheDataSource reads/writes SimpleCache
    }

    /**
     * Start a replacement queue. During Cast, ExoPlayer is only the paused full
     * queue mirror; the single ClearAndPlay command below owns receiver mutation.
     */
    private fun playMergedQueue(merged: List<MediaItem>, startIndex: Int) {
        if (!PlayerHolder.isCasting) {
            queueManager.playAll(merged, startIndex)
            return
        }
        val local = PlayerHolder.exoPlayer ?: return
        if (merged.isEmpty()) {
            local.clearMediaItems()
            return
        }
        local.setMediaItems(merged, startIndex.coerceIn(0, merged.lastIndex), 0L)
    }

    /** Resolve active item into the canonical full queue. */
    private fun currentCanonicalIndex(): Int {
        val merged = dualQueue.getMerged()
        if (merged.isEmpty()) return -1
        val active = PlayerHolder.player
        val activeId = active?.currentMediaItem?.mediaId
        val byId = activeId?.let { id -> merged.indexOfFirst { it.mediaId == id } } ?: -1
        val fallbackIndex = if (PlayerHolder.isCasting) {
            PlayerHolder.exoPlayer?.currentMediaItemIndex
        } else {
            active?.currentMediaItemIndex
        }
        return if (byId >= 0) byId else (fallbackIndex ?: 0).coerceIn(0, merged.lastIndex)
    }

    /**
     * Sync DualQueue into ExoPlayer (or the local active player when not casting).
     * Cast device-switch: CastTransferFilter may have truncated Exo — call before
     * [MediaService.loadFullQueueToReceiver] so the receiver gets the full phone queue.
     */
    fun syncDualQueueToPlayer() {
        val merged = dualQueue.getMerged()
        val active = PlayerHolder.player
        val player = if (PlayerHolder.isCasting) PlayerHolder.exoPlayer else active
        player ?: return
        if (merged.isEmpty()) {
            if (!PlayerHolder.isCasting) player.stop()
            player.clearMediaItems()
            return
        }
        val activeId = active?.currentMediaItem?.mediaId
        val mappedIndex = activeId?.let { id -> merged.indexOfFirst { it.mediaId == id } } ?: -1
        val currentIdx = if (mappedIndex >= 0) mappedIndex else player.currentMediaItemIndex
        val currentPos = active?.currentPosition ?: player.currentPosition
        player.setMediaItems(merged, currentIdx.coerceIn(0, merged.lastIndex), currentPos)
    }
}
