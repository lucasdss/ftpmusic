package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import org.junit.Assert.*
import org.junit.Test

class ContinuousPlayFallbackTest {

    private fun entity(
        id: String,
        albumId: String? = null,
        artistId: String? = null,
        genre: String? = null,
        cachedFilePath: String? = null,
        isDisliked: Boolean = false,
    ): TrackEntity = TrackEntity(
        id = id,
        title = id,
        albumId = albumId,
        artistId = artistId,
        genre = genre,
        cachedFilePath = cachedFilePath,
        isDisliked = isDisliked,
    )

    @Test
    fun `tier order follows genre artist most-played random`() {
        val tiers = ContinuousPlayFallback.tiers(
            ContinuousPlayFallback.Context(genre = "Rock", artistId = "ar-1", artistName = "Artist"),
        )
        assertEquals(
            listOf(
                ContinuousPlayFallback.Tier.SAME_GENRE,
                ContinuousPlayFallback.Tier.SAME_ARTIST,
                ContinuousPlayFallback.Tier.MOST_PLAYED,
                ContinuousPlayFallback.Tier.RANDOM,
            ),
            tiers,
        )
    }

    @Test
    fun `tiers skip missing context`() {
        assertEquals(
            listOf(ContinuousPlayFallback.Tier.MOST_PLAYED, ContinuousPlayFallback.Tier.RANDOM),
            ContinuousPlayFallback.tiers(ContinuousPlayFallback.Context(null, null, null)),
        )
        assertEquals(
            listOf(ContinuousPlayFallback.Tier.MOST_PLAYED, ContinuousPlayFallback.Tier.RANDOM),
            ContinuousPlayFallback.tiers(ContinuousPlayFallback.Context("  ", "", " ")),
        )
    }

    @Test
    fun `eligible ids dedupe and respect exclusions and limit`() {
        val entities = listOf(entity("a"), entity("b"), entity("a"), entity("c"), entity("d"))
        val result = ContinuousPlayFallback.eligibleIds(
            entities = entities,
            excludedIds = setOf("b"),
            localOnly = false,
            limit = 2,
        )
        assertEquals(listOf("a", "c"), result)
        assertTrue(ContinuousPlayFallback.eligibleIds(listOf(entity("a")), setOf("a"), false, limit = 1).isEmpty())
        assertTrue(ContinuousPlayFallback.eligibleIds(listOf(entity("a")), emptySet(), false, limit = 0).isEmpty())
    }

    @Test
    fun `offline eligibility keeps only cache-path rows and drops dislikes before slicing`() {
        val entities = listOf(
            entity("uncached", artistId = "ar-1", genre = "Rock"),
            entity("track-disliked", artistId = "ar-1", genre = "Rock", cachedFilePath = "/cache/x", isDisliked = true),
            entity("album-disliked", albumId = "al-bad", artistId = "ar-1", cachedFilePath = "/cache/y"),
            entity("artist-disliked", artistId = "ar-bad", cachedFilePath = "/cache/z"),
            entity("cached", artistId = "ar-1", genre = "Rock", cachedFilePath = "/cache/cached"),
        )
        val result = ContinuousPlayFallback.eligibleIds(
            entities = entities,
            excludedIds = setOf("already-playing"),
            localOnly = true,
            dislikedAlbumIds = setOf("al-bad"),
            dislikedArtistIds = setOf("ar-bad"),
            limit = 1,
        )
        assertEquals(listOf("cached"), result)
    }

    @Test
    fun `online eligibility keeps uncached rows`() {
        val entities = listOf(entity("uncached", artistId = "ar-1", genre = "Rock"))
        assertEquals(
            listOf("uncached"),
            ContinuousPlayFallback.eligibleIds(entities, emptySet(), localOnly = false, limit = 1),
        )
    }

    @Test
    fun `ladder candidates resolve offline and drop dislikes`() {
        val entities = listOf(
            entity("cached", artistId = "ar-1", genre = "Rock", cachedFilePath = "/cache/cached"),
            entity("uncached", artistId = "ar-1", genre = "Rock"),
            entity("track-disliked", artistId = "ar-1", genre = "Rock", cachedFilePath = "/cache/x", isDisliked = true),
            entity("album-disliked", albumId = "al-bad", artistId = "ar-1", cachedFilePath = "/cache/y"),
            entity("artist-disliked", artistId = "ar-bad", cachedFilePath = "/cache/z"),
        )
        val byId = entities.associateBy { it.id }
        val eligible = ContinuousPlayFallback.eligibleIds(
            entities = entities,
            excludedIds = emptySet(),
            localOnly = true,
            dislikedAlbumIds = setOf("al-bad"),
            dislikedArtistIds = setOf("ar-bad"),
            limit = 10,
        )
        val resolved = ContinuousPlayLoader.resolve(
            selectedIds = eligible,
            localOnly = true,
            loadTrack = { byId[it] },
            buildStreamUrl = { "https://server/stream?id=$it" },
            excludedAlbumIds = setOf("al-bad"),
            excludedArtistIds = setOf("ar-bad"),
        )
        assertEquals(listOf("cached"), resolved.map { it.track.id })
    }
}
