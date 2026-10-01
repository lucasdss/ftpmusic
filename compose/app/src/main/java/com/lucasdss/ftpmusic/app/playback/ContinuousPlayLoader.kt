package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Track

/**
 * Resolves journal-selected track IDs into playable Continuous Play candidates.
 * Pure orchestration extracted from [MediaService] for unit tests (ADR-0053).
 */
object ContinuousPlayLoader {

    data class Candidate(val track: Track, val streamUrl: String)

    /**
     * @param selectedIds journal-picked IDs (already de-duped vs current queue)
     * @param localOnly when true, keep only downloaded/cached rows
     */
    fun resolve(
        selectedIds: List<String>,
        localOnly: Boolean,
        loadTrack: (String) -> TrackEntity?,
        buildStreamUrl: (String) -> String,
    ): List<Candidate> {
        if (selectedIds.isEmpty()) return emptyList()
        val out = ArrayList<Candidate>(selectedIds.size)
        for (id in selectedIds) {
            val entity = loadTrack(id) ?: continue
            if (localOnly && !isPlayableOffline(entity)) continue
            out.add(
                Candidate(
                    track = Track(
                        id = entity.id,
                        title = entity.title,
                        artist = entity.artist,
                        album = null,
                        artistId = entity.artistId,
                        albumId = entity.albumId,
                        duration = entity.durationSeconds,
                        coverArt = entity.coverArtUrl,
                        contentType = entity.contentType,
                        suffix = entity.suffix,
                    ),
                    streamUrl = buildStreamUrl(entity.id),
                ),
            )
        }
        return out
    }

    fun isPlayableOffline(entity: TrackEntity): Boolean = entity.isDownloaded || !entity.cachedFilePath.isNullOrBlank()
}
