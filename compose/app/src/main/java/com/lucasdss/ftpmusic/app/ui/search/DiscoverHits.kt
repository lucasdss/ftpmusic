package com.lucasdss.ftpmusic.app.ui.search

/**
 * Shared Discover / Desired catalog-add hit models (ADR-0130, ADR-0144).
 * Used by Search Discover and Favorites Desired “Add from catalog”.
 */
data class DiscoverArtistHit(
    val name: String,
    val mbid: String? = null,
    val deezerId: String? = null,
    /** MusicBrainz disambiguation — short label when present. */
    val disambiguation: String? = null,
    /**
     * Readable bio: library getArtistInfo2 cache or Last.fm artist.getInfo
     * fanout (ADR-0144 amend). Prefer over [disambiguation] for display/persist.
     */
    val biography: String? = null,
    val source: String = "musicbrainz",
    val inLibrary: Boolean = false,
    val localArtistId: String? = null,
    val listenCount: Long? = null,
    /** Deezer / Last.fm thumb URL when available. */
    val imageUrl: String? = null,
) {
    /** Persist / secondary line: bio then MB disambiguation. */
    fun descriptionText(): String? = biography?.takeIf { it.isNotBlank() } ?: disambiguation?.takeIf { it.isNotBlank() }

    /** Always includes origin (library vs catalog + provider source). */
    fun originSubtitle(): String = if (inLibrary) "In library · $source" else "Not in library · $source"
}

data class DiscoverTrackHit(
    val title: String,
    val artistName: String? = null,
    val albumName: String? = null,
    val mbid: String? = null,
    val deezerId: String? = null,
    val inLibrary: Boolean = false,
    val localTrackId: String? = null,
    val listenCount: Long? = null,
    val imageUrl: String? = null,
)

data class DiscoverAlbumHit(
    val title: String,
    val artistName: String? = null,
    val mbid: String? = null,
    val deezerId: String? = null,
    val source: String = "deezer",
    val inLibrary: Boolean = false,
    val localAlbumId: String? = null,
    val imageUrl: String? = null,
)
