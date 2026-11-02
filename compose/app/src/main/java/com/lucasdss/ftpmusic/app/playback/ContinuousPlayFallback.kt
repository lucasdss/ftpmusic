package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.TrackEntity

/**
 * CP-6 local fallback ladder. DAO calls live in [MediaService]; this object
 * owns tier order and bounded de-duplication so the ladder is unit-testable.
 *
 * The ladder runs after the similar-songs API and the journal both produced
 * nothing. It is used both online and offline (CP-6 explicitly requires an
 * online fallback); [MediaService] applies [ContinuousPlayLoader.resolve] with
 * `localOnly` so offline only keeps rows with a cache path.
 */
object ContinuousPlayFallback {
    enum class Tier { SAME_GENRE, SAME_ARTIST, MOST_PLAYED, RANDOM }

    data class Context(val genre: String?, val artistId: String?, val artistName: String?)

    fun contextOf(entity: TrackEntity?): Context =
        Context(genre = entity?.genre, artistId = entity?.artistId, artistName = entity?.artist)

    /** Tier order, skipping genre/artist when the current track has no such id. */
    fun tiers(context: Context): List<Tier> = buildList {
        if (!context.genre.isNullOrBlank()) add(Tier.SAME_GENRE)
        if (!context.artistId.isNullOrBlank()) add(Tier.SAME_ARTIST)
        add(Tier.MOST_PLAYED)
        add(Tier.RANDOM)
    }

    /**
     * Returns up to [limit] tier ids that survive every hard filter before
     * slicing, so dislike/offline drops do not consume fallback slots.
     * [excludedIds] should contain the current queue, the playing track, and
     * tracks picked by earlier tiers.
     */
    fun eligibleIds(
        entities: List<TrackEntity>,
        excludedIds: Set<String>,
        localOnly: Boolean,
        dislikedAlbumIds: Set<String> = emptySet(),
        dislikedArtistIds: Set<String> = emptySet(),
        limit: Int,
    ): List<String> {
        if (limit <= 0) return emptyList()
        return entities.asSequence()
            .filter { it.id !in excludedIds && !it.isDisliked }
            .filter { !localOnly || !it.cachedFilePath.isNullOrBlank() }
            .filter { it.albumId == null || it.albumId !in dislikedAlbumIds }
            .filter { it.artistId == null || it.artistId !in dislikedArtistIds }
            .map { it.id }
            .distinct()
            .take(limit)
            .toList()
    }
}
