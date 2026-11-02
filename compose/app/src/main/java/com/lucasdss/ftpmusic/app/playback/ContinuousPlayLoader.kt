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
     * Single source of truth for dislike-ledger filtering. Used by [resolve]
     * for journal candidates and by [MediaService] for API similar-songs,
     * where album/artist ids may need to be resolved from Room first.
     */
    fun isExcluded(
        trackId: String,
        albumId: String?,
        artistId: String?,
        excludedTrackIds: Set<String> = emptySet(),
        excludedAlbumIds: Set<String> = emptySet(),
        excludedArtistIds: Set<String> = emptySet(),
    ): Boolean = trackId in excludedTrackIds ||
        (albumId != null && albumId in excludedAlbumIds) ||
        (artistId != null && artistId in excludedArtistIds)

    /**
     * @param selectedIds journal-picked IDs (already de-duped vs current queue)
     * @param localOnly when true, keep only downloaded/cached rows
     * @param excludedTrackIds locally disliked track IDs (track ledger)
     * @param excludedAlbumIds locally disliked album IDs (album ledger)
     * @param excludedArtistIds locally disliked artist IDs (artist ledger)
     */
    fun resolve(
        selectedIds: List<String>,
        localOnly: Boolean,
        loadTrack: (String) -> TrackEntity?,
        buildStreamUrl: (String) -> String,
        excludedTrackIds: Set<String> = emptySet(),
        excludedAlbumIds: Set<String> = emptySet(),
        excludedArtistIds: Set<String> = emptySet(),
    ): List<Candidate> {
        if (selectedIds.isEmpty()) return emptyList()
        val out = ArrayList<Candidate>(selectedIds.size)
        for (id in selectedIds) {
            val entity = loadTrack(id) ?: continue
            if (entity.isDisliked) continue
            val excluded = isExcluded(
                id,
                entity.albumId,
                entity.artistId,
                excludedTrackIds,
                excludedAlbumIds,
                excludedArtistIds,
            )
            if (excluded) continue
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

    /**
     * Offline-playable requires a non-blank cache path (ADR-0095).
     * Healed pins keep `is_downloaded=1` with null path — those are NOT playable
     * until bytes exist again (Downloads UI warns; user re-caches on play online).
     */
    fun isPlayableOffline(entity: TrackEntity): Boolean = !entity.cachedFilePath.isNullOrBlank()
}
