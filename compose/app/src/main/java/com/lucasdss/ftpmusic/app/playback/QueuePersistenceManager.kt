package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.QueueDao
import com.lucasdss.ftpmusic.app.data.db.QueueItemEntity
import com.lucasdss.ftpmusic.app.data.db.QueueStateEntity
import com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog
import com.lucasdss.ftpmusic.app.data.model.Track
import java.util.Objects
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SavedQueueState(
    val tracks: List<Track>,
    val urls: List<String>,
    val currentIndex: Int,
    val positionMs: Long,
    /**
     * Derived count of CONTEXT rows (non-priority). Written for one more
     * release; never origin SoT (use [isPriorityFlags]).
     */
    val contextSize: Int = -1,
    /** Per-row PRIORITY/LATE flags aligned with [tracks]. */
    val isPriorityFlags: List<Boolean>? = null,
    /** Per-row [queueEntryId]; 0 = unstamped legacy. */
    val entryIds: List<Int>? = null,
    val nextEntryId: Int = 1,
    /** Per-row Continuous Play Autoplay flags aligned with [tracks]. */
    val isAutoplayFlags: List<Boolean>? = null,
    /** Per-row LATE (session_end Add to Queue) flags — ADR-0135. */
    val isLateFlags: List<Boolean>? = null,
    /** ExoPlayer [Player.repeatMode] from queue_state (ADR-0087). */
    val repeatMode: Int = 0,
    /** ExoPlayer shuffle from queue_state (ADR-0087). */
    val shuffleEnabled: Boolean = false,
)

@Singleton
class QueuePersistenceManager @Inject constructor(private val dao: QueueDao) {
    /**
     * F3 / D7: MediaService.saveQueueState (2 call sites) and
     * PlaybackManager.persistenceSave (~18 call sites) all funnel here from
     * independent unconstrained `Dispatchers.IO` scopes, so commit order is not
     * guaranteed. Two guards:
     *  - [writeMutex] serialises the read-modify-write against
     *    savePositionOnly/savePlaybackExtras.
     *  - [generation] is captured at snapshot time by the caller; a snapshot
     *    older than the last applied one is dropped, so a stale transition
     *    snapshot can never overwrite a newer pause snapshot.
     */
    private val writeMutex = Mutex()
    private val generationCounter = AtomicLong(0L)
    private var lastAppliedGeneration = 0L
    private var lastFingerprint: Int = 0
    private var hasApplied = false

    /** Monotonic snapshot id — call BEFORE launching the write coroutine. */
    fun nextGeneration(): Long = generationCounter.incrementAndGet()

    suspend fun save(
        tracks: List<Track>,
        urls: List<String>,
        currentIndex: Int,
        positionMs: Long,
        contextSize: Int = -1,
        isPriorityFlags: List<Boolean>? = null,
        entryIds: List<Int>? = null,
        nextEntryId: Int = 1,
        isAutoplayFlags: List<Boolean>? = null,
        isLateFlags: List<Boolean>? = null,
        generation: Long = nextGeneration(),
        src: String = "unknown",
    ) {
        writeMutex.withLock {
            if (generation < lastAppliedGeneration) {
                DiagnosticLog.d(
                    "ftpmusic-queue",
                    "save skip stale src=$src gen=$generation applied=$lastAppliedGeneration",
                )
                return
            }
            if (tracks.isEmpty()) {
                lastAppliedGeneration = generation
                lastFingerprint = 0
                hasApplied = false
                dao.clear()
                return
            }
            val flags = isPriorityFlags?.takeIf { it.size == tracks.size }
                ?: List(tracks.size) { index ->
                    contextSize >= 0 && index >= contextSize
                }
            val ids = entryIds?.takeIf { it.size == tracks.size }
                ?: List(tracks.size) { index -> index + 1 }
            val autoplay = isAutoplayFlags?.takeIf { it.size == tracks.size }
                ?: List(tracks.size) { false }
            val late = isLateFlags?.takeIf { it.size == tracks.size }
                ?: List(tracks.size) { false }
            val entities = tracks.zip(urls).mapIndexed { index, (track, url) ->
                QueueItemEntity(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    coverArtId = track.coverArt,
                    artistId = track.artistId,
                    albumId = track.albumId,
                    url = url,
                    position = index,
                    durationSeconds = track.duration,
                    isPriority = flags[index] || late[index],
                    entryId = ids[index],
                    isAutoplay = autoplay[index],
                    isLate = late[index],
                )
            }
            val derivedCtx = entities.count { !it.isPriority }
            val derivedNext = maxOf(nextEntryId, ids.max() + 1, 1)
            // Fingerprint includes flags/entry ids so a Keep-Queue / clear-priority
            // change with identical ids+position is still written. `urls` is part
            // of the key because a legacy/proxy URL refresh rewrites QueueItemEntity.url
            // without touching ids, index, flags or position — skipping it would
            // leave the stale URL persisted (QA F3/D7).
            val fingerprint = Objects.hash(
                tracks.map { it.id },
                urls,
                currentIndex,
                positionMs / 1000L,
                derivedCtx,
                flags,
                ids,
                autoplay,
                late,
                derivedNext,
            )
            if (hasApplied && fingerprint == lastFingerprint) {
                lastAppliedGeneration = generation
                DiagnosticLog.d(
                    "ftpmusic-queue",
                    "save skip identical src=$src gen=$generation",
                )
                return
            }
            // Preserve sleep/play extras from existing row (ADR-0074 merge).
            // Construct a real entity (do not copy a MockK stub) so nextEntryId sticks.
            val prev = dao.getState()
            dao.replaceAllAndState(
                entities,
                QueueStateEntity(
                    currentIndex = currentIndex,
                    positionMs = positionMs,
                    isCasting = prev?.isCasting ?: false,
                    castDeviceName = prev?.castDeviceName,
                    contextSize = derivedCtx,
                    nextEntryId = derivedNext,
                    sleepTimerEndMs = prev?.sleepTimerEndMs ?: 0L,
                    isPlaying = prev?.isPlaying ?: false,
                    repeatMode = prev?.repeatMode ?: 0,
                    shuffleEnabled = prev?.shuffleEnabled ?: false,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            lastAppliedGeneration = generation
            lastFingerprint = fingerprint
            hasApplied = true
            DiagnosticLog.d(
                "ftpmusic-queue",
                "save src=$src gen=$generation tracks=${entities.size} " +
                    "idx=$currentIndex pos=$positionMs ctx=$derivedCtx",
            )
        }
    }

    suspend fun savePositionOnly(currentIndex: Int, positionMs: Long) {
        writeMutex.withLock { dao.savePositionOnly(currentIndex, positionMs) }
    }

    /** Persist sleep timer + transport extras without touching queue items. */
    suspend fun savePlaybackExtras(
        sleepTimerEndMs: Long,
        isPlaying: Boolean,
        repeatMode: Int,
        shuffleEnabled: Boolean,
    ) {
        writeMutex.withLock {
            dao.savePlaybackExtras(sleepTimerEndMs, isPlaying, repeatMode, shuffleEnabled)
        }
    }

    suspend fun sleepTimerEndMs(): Long = dao.getState()?.sleepTimerEndMs ?: 0L

    suspend fun restore(): SavedQueueState? {
        val entities = dao.getAllOnce()
        if (entities.isEmpty()) return null
        val tracks = entities.map {
            Track(
                id = it.trackId,
                title = it.title,
                artist = it.artist,
                artistId = it.artistId,
                album = it.album,
                albumId = it.albumId,
                coverArt = it.coverArtId?.takeUnless { id -> id == "getCoverArt" },
                duration = it.durationSeconds,
            )
        }
        val urls = entities.map { it.url }
        val state = dao.getState() ?: QueueStateEntity()
        val flags = entities.map { it.isPriority }
        val ids = entities.map { it.entryId }
        val autoplay = entities.map { it.isAutoplay }
        val late = entities.map { it.isLate }
        val derivedCtx = flags.count { !it }
        val derivedNext = maxOf(state.nextEntryId, ids.max() + 1, 1)
        return SavedQueueState(
            tracks = tracks,
            urls = urls,
            currentIndex = state.currentIndex,
            positionMs = state.positionMs,
            contextSize = derivedCtx,
            isPriorityFlags = flags,
            entryIds = ids,
            nextEntryId = derivedNext,
            isAutoplayFlags = autoplay,
            isLateFlags = late,
            repeatMode = state.repeatMode,
            shuffleEnabled = state.shuffleEnabled,
        )
    }

    suspend fun clear() {
        dao.clear()
    }
}
