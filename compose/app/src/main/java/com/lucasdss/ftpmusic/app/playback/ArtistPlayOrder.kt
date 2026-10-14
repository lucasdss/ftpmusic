package com.lucasdss.ftpmusic.app.playback

/**
 * How artist detail **Play All** builds the context queue (ADR-0141).
 *
 * Default [SHUFFLE] matches Symfonium / YouTube Music artist ambiguity.
 * Chronological modes group by album year then album name (null years last).
 */
enum class ArtistPlayOrder(val key: String, val label: String) {
    SHUFFLE("shuffle", "Shuffle"),
    ALBUM_YEAR_ASC("album_year_asc", "Oldest albums first"),
    ALBUM_YEAR_DESC("album_year_desc", "Newest albums first"),
    TITLE_ASC("title_asc", "Song title A–Z"),
    ;

    companion object {
        fun fromKey(key: String?): ArtistPlayOrder = entries.firstOrNull { it.key == key } ?: SHUFFLE
    }
}
