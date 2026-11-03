package com.lucasdss.ftpmusic.app.data.sort

/**
 * Browse sort for the artist detail **Songs** tab (ADR-0152).
 *
 * Independent from [com.lucasdss.ftpmusic.app.playback.ArtistPlayOrder] (Play All):
 * this preference only changes the Songs list and tap-through queue window.
 *
 * Play-count and track-name modes are served by SQL keyset pagination; the
 * album-block and random modes load the full artist-view membership once and
 * sort in memory.
 */
enum class ArtistSongsSort(val key: String, val mode: ArtistSongsMode, val ascending: Boolean) {
    PLAY_COUNT_DESC("play_count_desc", ArtistSongsMode.PLAY_COUNT, false),
    PLAY_COUNT_ASC("play_count_asc", ArtistSongsMode.PLAY_COUNT, true),
    RANDOM("random", ArtistSongsMode.RANDOM, true),
    ALBUM_NAME_ASC("album_name_asc", ArtistSongsMode.ALBUM_NAME, true),
    ALBUM_NAME_DESC("album_name_desc", ArtistSongsMode.ALBUM_NAME, false),
    ALBUM_YEAR_ASC("album_year_asc", ArtistSongsMode.ALBUM_YEAR, true),
    ALBUM_YEAR_DESC("album_year_desc", ArtistSongsMode.ALBUM_YEAR, false),
    TRACK_NAME_ASC("track_name_asc", ArtistSongsMode.TRACK_NAME, true),
    TRACK_NAME_DESC("track_name_desc", ArtistSongsMode.TRACK_NAME, false),
    ;

    val isRandom: Boolean get() = mode == ArtistSongsMode.RANDOM

    /** Album-block modes need the full membership + album years. */
    val usesAlbumBlocks: Boolean
        get() = mode == ArtistSongsMode.ALBUM_NAME || mode == ArtistSongsMode.ALBUM_YEAR

    /** SQL keyset pagination is available for flat (non-block, non-random) modes. */
    val isKeyset: Boolean get() = !usesAlbumBlocks && !isRandom

    val supportsDirection: Boolean get() = !isRandom

    fun withAscending(ascending: Boolean): ArtistSongsSort = when (mode) {
        ArtistSongsMode.PLAY_COUNT ->
            if (ascending) PLAY_COUNT_ASC else PLAY_COUNT_DESC

        ArtistSongsMode.TRACK_NAME ->
            if (ascending) TRACK_NAME_ASC else TRACK_NAME_DESC

        ArtistSongsMode.ALBUM_NAME ->
            if (ascending) ALBUM_NAME_ASC else ALBUM_NAME_DESC

        ArtistSongsMode.ALBUM_YEAR ->
            if (ascending) ALBUM_YEAR_ASC else ALBUM_YEAR_DESC

        ArtistSongsMode.RANDOM -> RANDOM
    }

    companion object {
        val DEFAULT: ArtistSongsSort = PLAY_COUNT_DESC

        /** Direction chosen when the user picks a mode for the first time. */
        fun natural(mode: ArtistSongsMode): ArtistSongsSort = when (mode) {
            ArtistSongsMode.PLAY_COUNT -> PLAY_COUNT_DESC
            ArtistSongsMode.RANDOM -> RANDOM
            ArtistSongsMode.ALBUM_NAME -> ALBUM_NAME_ASC
            ArtistSongsMode.ALBUM_YEAR -> ALBUM_YEAR_ASC
            ArtistSongsMode.TRACK_NAME -> TRACK_NAME_ASC
        }

        /** Unknown / legacy keys fall back to the product default (most played). */
        fun fromKey(key: String?): ArtistSongsSort = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

enum class ArtistSongsMode { PLAY_COUNT, RANDOM, ALBUM_NAME, ALBUM_YEAR, TRACK_NAME }
