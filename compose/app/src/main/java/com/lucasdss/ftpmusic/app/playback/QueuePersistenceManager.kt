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
            )
        }
        val derivedCtx = flags.count { !it }
        val derivedNext = maxOf(nextEntryId, ids.max() + 1, 1)
        dao.replaceAllAndState(
            entities,
            QueueStateEntity(
                currentIndex = currentIndex,
                positionMs = positionMs,
                contextSize = derivedCtx,
                nextEntryId = derivedNext,
            ),
        )
    }

    suspend fun savePositionOnly(currentIndex: Int, positionMs: Long) {
        dao.savePositionOnly(currentIndex, positionMs)
    }

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
        )
    }

    suspend fun clear() {
        dao.clear()
    }
}
