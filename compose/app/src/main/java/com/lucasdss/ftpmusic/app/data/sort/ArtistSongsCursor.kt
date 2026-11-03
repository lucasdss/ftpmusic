package com.lucasdss.ftpmusic.app.data.sort

/**
 * Keyset cursor for SQL-paginated artist Songs modes.
 *
 * Play-count rows advance by `(play_count, title, id)`; track-name rows by
 * `(title, id)`. The cursor always comes from the **raw** page's last row —
 * never from a deduplicated result set.
 */
sealed interface ArtistSongsCursor {
    data class PlayCount(val playCount: Int, val title: String, val id: String) : ArtistSongsCursor

    data class Title(val title: String, val id: String) : ArtistSongsCursor
}
