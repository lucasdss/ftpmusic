package com.lucasdss.ftpmusic.app.playback

/**
 * How artist detail **Play All** builds the context queue (ADR-0141 / ADR-0145).
 *
 * Default [SHUFFLE_ALBUMS] matches Apple Music “Shuffle Albums”: random album
 * order with tracks kept in disc/track order inside each album.
 *
 * Album year/name modes also keep within-album track order; only [SHUFFLE_SONGS]
 * randomizes individual tracks.
 */
enum class ArtistPlayOrder(val key: String, val label: String) {
    SHUFFLE_ALBUMS("shuffle_albums", "Shuffle albums"),
    SHUFFLE_SONGS("shuffle", "Shuffle all songs"),
    ALBUM_YEAR_ASC("album_year_asc", "Albums by year"),
    ALBUM_YEAR_DESC("album_year_desc", "Albums by year"),
    ALBUM_NAME_ASC("album_name_asc", "Albums by name"),
    ALBUM_NAME_DESC("album_name_desc", "Albums by name"),
    ;

    val isSongShuffle: Boolean get() = this == SHUFFLE_SONGS

    val usesYear: Boolean get() = this == ALBUM_YEAR_ASC || this == ALBUM_YEAR_DESC

    val usesName: Boolean get() = this == ALBUM_NAME_ASC || this == ALBUM_NAME_DESC

    /** True for year/name modes when the ascending direction chip is selected. */
    val isAscending: Boolean
        get() = this == ALBUM_YEAR_ASC || this == ALBUM_NAME_ASC

    fun withAscending(ascending: Boolean): ArtistPlayOrder = when {
        usesYear -> if (ascending) ALBUM_YEAR_ASC else ALBUM_YEAR_DESC
        usesName -> if (ascending) ALBUM_NAME_ASC else ALBUM_NAME_DESC
        else -> this
    }

    companion object {
        fun fromKey(key: String?): ArtistPlayOrder = when (key) {
            null, "" -> SHUFFLE_ALBUMS

            // Legacy song-title A–Z → albums by name A–Z (song A–Z removed from UI).
            "title_asc" -> ALBUM_NAME_ASC

            else -> entries.firstOrNull { it.key == key } ?: SHUFFLE_ALBUMS
        }
    }
}
