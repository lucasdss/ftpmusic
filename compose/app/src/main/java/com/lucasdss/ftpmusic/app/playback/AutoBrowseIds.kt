/**
 * Media3 library media-id scheme for Android Auto browse (ADR-0091).
 * Track ids stay bare Subsonic ids so [MediaSessionCallback.onAddMediaItems] expand works.
 */
object AutoBrowseIds {
    const val ROOT = "auto_root"
    const val RECENT = "auto_recent"
    const val FAVORITES = "auto_favorites"
    const val PLAYLISTS = "auto_playlists"
    const val ALBUMS = "auto_albums"
    const val ARTISTS = "auto_artists"

    private const val PLAYLIST_PREFIX = "auto_playlist:"
    private const val ALBUM_PREFIX = "auto_album:"
    private const val ARTIST_PREFIX = "auto_artist:"

    fun playlist(id: String): String = PLAYLIST_PREFIX + id
    fun album(id: String): String = ALBUM_PREFIX + id
    fun artist(id: String): String = ARTIST_PREFIX + id

    fun parsePlaylist(mediaId: String): String? =
        mediaId.takeIf { it.startsWith(PLAYLIST_PREFIX) }?.removePrefix(PLAYLIST_PREFIX)

    fun parseAlbum(mediaId: String): String? =
        mediaId.takeIf { it.startsWith(ALBUM_PREFIX) }?.removePrefix(ALBUM_PREFIX)

    fun parseArtist(mediaId: String): String? =
        mediaId.takeIf { it.startsWith(ARTIST_PREFIX) }?.removePrefix(ARTIST_PREFIX)

    fun isBrowseNode(mediaId: String): Boolean = mediaId == ROOT ||
        mediaId == RECENT ||
        mediaId == FAVORITES ||
        mediaId == PLAYLISTS ||
        mediaId == ALBUMS ||
        mediaId == ARTISTS ||
        mediaId.startsWith(PLAYLIST_PREFIX) ||
        mediaId.startsWith(ALBUM_PREFIX) ||
        mediaId.startsWith(ARTIST_PREFIX)
}
