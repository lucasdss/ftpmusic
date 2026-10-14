package com.lucasdss.ftpmusic.app.playback

import android.net.Uri

/**
 * Media3 library media-id scheme for Android Auto browse (ADR-0091 / ADR-0131).
 * Track ids stay bare Subsonic ids so [MediaSessionCallback.onAddMediaItems] expand works.
 */
object AutoBrowseIds {
    const val ROOT = "auto_root"
    const val RECENT = "auto_recent"
    const val OFFLINE = "auto_offline"
    const val FAVORITES = "auto_favorites"
    const val MIXES = "auto_mixes"
    const val PLAYLISTS = "auto_playlists"
    const val ALBUMS = "auto_albums"
    const val ARTISTS = "auto_artists"
    const val GENRES = "auto_genres"
    const val RADIO = "auto_radio"

    /** Dual Up-Next browse root (ADR-0138). */
    const val QUEUE = "auto_queue"
    const val QUEUE_PRIORITY = "auto_queue_priority"
    const val QUEUE_CONTEXT = "auto_queue_context"
    const val QUEUE_AUTOPLAY = "auto_queue_autoplay"
    const val QUEUE_LATE = "auto_queue_late"

    private const val PLAYLIST_PREFIX = "auto_playlist:"
    private const val ALBUM_PREFIX = "auto_album:"
    private const val ARTIST_PREFIX = "auto_artist:"
    private const val MIX_PREFIX = "auto_mix:"
    private const val GENRE_PREFIX = "auto_genre:"
    private const val RADIO_PREFIX = "auto_radio:"
    private const val QUEUE_TRACK_PREFIX = "auto_queue_track:"

    fun playlist(id: String): String = PLAYLIST_PREFIX + id
    fun album(id: String): String = ALBUM_PREFIX + id
    fun artist(id: String): String = ARTIST_PREFIX + id
    fun mix(id: Long): String = MIX_PREFIX + id
    fun genre(name: String): String = GENRE_PREFIX + Uri.encode(name)
    fun radio(stationId: String): String = RADIO_PREFIX + Uri.encode(stationId)
    fun queueTrack(entryId: Int): String = QUEUE_TRACK_PREFIX + entryId

    fun parsePlaylist(mediaId: String): String? =
        mediaId.takeIf { it.startsWith(PLAYLIST_PREFIX) }?.removePrefix(PLAYLIST_PREFIX)

    fun parseAlbum(mediaId: String): String? =
        mediaId.takeIf { it.startsWith(ALBUM_PREFIX) }?.removePrefix(ALBUM_PREFIX)

    fun parseArtist(mediaId: String): String? =
        mediaId.takeIf { it.startsWith(ARTIST_PREFIX) }?.removePrefix(ARTIST_PREFIX)

    fun parseMix(mediaId: String): Long? = mediaId.takeIf { it.startsWith(MIX_PREFIX) }
        ?.removePrefix(MIX_PREFIX)
        ?.toLongOrNull()

    fun parseGenre(mediaId: String): String? = mediaId.takeIf { it.startsWith(GENRE_PREFIX) }
        ?.removePrefix(GENRE_PREFIX)
        ?.let { Uri.decode(it) }

    fun parseRadio(mediaId: String): String? = mediaId.takeIf { it.startsWith(RADIO_PREFIX) }
        ?.removePrefix(RADIO_PREFIX)
        ?.let { Uri.decode(it) }

    fun parseQueueTrack(mediaId: String): Int? = mediaId.takeIf { it.startsWith(QUEUE_TRACK_PREFIX) }
        ?.removePrefix(QUEUE_TRACK_PREFIX)
        ?.toIntOrNull()

    fun isRadioMediaId(mediaId: String): Boolean = mediaId.startsWith(RADIO_PREFIX) || mediaId.startsWith("radio:")

    fun isQueueTrack(mediaId: String): Boolean = mediaId.startsWith(QUEUE_TRACK_PREFIX)

    fun isQueueBand(mediaId: String): Boolean = mediaId == QUEUE ||
        mediaId == QUEUE_PRIORITY ||
        mediaId == QUEUE_CONTEXT ||
        mediaId == QUEUE_AUTOPLAY ||
        mediaId == QUEUE_LATE

    fun isBrowseNode(mediaId: String): Boolean = mediaId == ROOT ||
        mediaId == RECENT ||
        mediaId == OFFLINE ||
        mediaId == FAVORITES ||
        mediaId == MIXES ||
        mediaId == PLAYLISTS ||
        mediaId == ALBUMS ||
        mediaId == ARTISTS ||
        mediaId == GENRES ||
        mediaId == RADIO ||
        isQueueBand(mediaId) ||
        isQueueTrack(mediaId) ||
        mediaId.startsWith(PLAYLIST_PREFIX) ||
        mediaId.startsWith(ALBUM_PREFIX) ||
        mediaId.startsWith(ARTIST_PREFIX) ||
        mediaId.startsWith(MIX_PREFIX) ||
        mediaId.startsWith(GENRE_PREFIX) ||
        mediaId.startsWith(RADIO_PREFIX)
}
