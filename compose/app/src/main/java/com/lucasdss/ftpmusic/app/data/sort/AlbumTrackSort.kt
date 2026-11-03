package com.lucasdss.ftpmusic.app.data.sort

/**
 * How album detail orders its track list (ADR-0152).
 *
 * Default is the historical disc → track order. The play-count modes are a
 * browse-only preference: they never change `getAlbumTracks` SQL (Android Auto
 * / assistant album expansion keep disc/track) and never change Shuffle.
 */
enum class AlbumTrackSort(val key: String) {
    DISC_TRACK("disc_track"),
    PLAY_COUNT_DESC("play_count_desc"),
    PLAY_COUNT_ASC("play_count_asc"),
    ;

    val usesPlayCount: Boolean get() = this != DISC_TRACK

    companion object {
        val DEFAULT: AlbumTrackSort = DISC_TRACK

        /** Unknown / legacy keys fall back to disc → track ascending. */
        fun fromKey(key: String?): AlbumTrackSort = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
