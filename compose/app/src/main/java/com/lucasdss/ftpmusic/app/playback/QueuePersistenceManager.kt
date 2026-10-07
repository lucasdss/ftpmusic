package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.QueueDao
import com.lucasdss.ftpmusic.app.data.db.QueueItemEntity
import com.lucasdss.ftpmusic.app.data.db.QueueStateEntity
import com.lucasdss.ftpmusic.app.data.model.Track
import javax.inject.Inject
import javax.inject.Singleton

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
    /** Per-row PRIORITY flags aligned with [tracks]. */
    val isPriorityFlags: List<Boolean>? = null,
    /** Per-row [queueEntryId]; 0 = unstamped legacy. */
    val entryIds: List<Int>? = null,
    val nextEntryId: Int = 1,
    /** Per-row Continuous Play Autoplay flags aligned with [tracks]. */
    val isAutoplayFlags: List<Boolean>? = null,
    /** ExoPlayer [Player.repeatMode] from queue_state (ADR-0087). */
    val repeatMode: Int = 0,
    /** ExoPlayer shuffle from queue_state (ADR-0087). */
    val shuffleEnabled: Boolean = false,
)

@Singleton
class QueuePersistenceManager @Inject constructor(private val dao: QueueDao) {
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
    ) {
        if (tracks.isEmpty()) {
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
                isPriority = flags[index],
                entryId = ids[index],
                isAutoplay = autoplay[index],
            )
        }
        val derivedCtx = flags.count { !it }
        val derivedNext = maxOf(nextEntryId, ids.max() + 1, 1)
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
    }

    suspend fun savePositionOnly(currentIndex: Int, positionMs: Long) {
        dao.savePositionOnly(currentIndex, positionMs)
    }

    /** Persist sleep timer + transport extras without touching queue items. */
    suspend fun savePlaybackExtras(
        sleepTimerEndMs: Long,
        isPlaying: Boolean,
        repeatMode: Int,
        shuffleEnabled: Boolean,
    ) {
        dao.savePlaybackExtras(sleepTimerEndMs, isPlaying, repeatMode, shuffleEnabled)
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
            repeatMode = state.repeatMode,
            shuffleEnabled = state.shuffleEnabled,
        )
    }

    suspend fun clear() {
        dao.clear()
    }
}
