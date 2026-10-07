package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedAlbumTrackEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity

/**
 * Local-first Android Auto browse tree (ADR-0091).
 * Room only — no network on browse clicks.
 */
class AutoBrowseCatalog(
    private val trackDao: TrackDao,
    private val metadataDao: CachedMetadataDao,
    private val playlistDao: PlaylistDao,
    private val artworkUriFor: (coverArtId: String?) -> Uri?,
    private val streamUriFor: (trackId: String) -> Uri?,
) {
    fun rootItem(): MediaItem = folder(
        mediaId = AutoBrowseIds.ROOT,
        title = "FTP Music",
        playable = false,
    )

    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        val size = pageSize.coerceIn(1, 100)
        val offset = (page.coerceAtLeast(0)) * size
        return when (parentId) {
            AutoBrowseIds.ROOT -> rootChildren()

            AutoBrowseIds.RECENT -> trackDao.getRecentlyPlayedPaged(limit = size, offset = offset).map {
                trackItem(it)
            }

            AutoBrowseIds.FAVORITES -> trackDao.getStarred(limit = size, offset = offset).map { trackItem(it) }

            AutoBrowseIds.PLAYLISTS -> pageList(playlistDao.getAll(), offset, size) { playlistFolder(it) }

            AutoBrowseIds.ALBUMS -> metadataDao.getAlbumsPaged(size, offset).map { albumFolder(it) }

            AutoBrowseIds.ARTISTS -> metadataDao.getArtistsPaged(size, offset).map { artistFolder(it) }

            else -> {
                AutoBrowseIds.parsePlaylist(parentId)?.let { id ->
                    playlistTracks(id, offset, size)
                } ?: AutoBrowseIds.parseAlbum(parentId)?.let { id ->
                    albumTracks(id, offset, size)
                } ?: AutoBrowseIds.parseArtist(parentId)?.let { id ->
                    artistAlbums(id, offset, size)
                } ?: emptyList()
            }
        }
    }

    suspend fun item(mediaId: String): MediaItem? {
        when (mediaId) {
            AutoBrowseIds.ROOT -> return rootItem()
            AutoBrowseIds.RECENT -> return folder(AutoBrowseIds.RECENT, "Recently played", playable = false)
            AutoBrowseIds.FAVORITES -> return folder(AutoBrowseIds.FAVORITES, "Favorites", playable = false)
            AutoBrowseIds.PLAYLISTS -> return folder(AutoBrowseIds.PLAYLISTS, "Playlists", playable = false)
            AutoBrowseIds.ALBUMS -> return folder(AutoBrowseIds.ALBUMS, "Albums", playable = false)
            AutoBrowseIds.ARTISTS -> return folder(AutoBrowseIds.ARTISTS, "Artists", playable = false)
        }
        AutoBrowseIds.parsePlaylist(mediaId)?.let { id ->
            return playlistDao.getById(id)?.let { playlistFolder(it) }
        }
        AutoBrowseIds.parseAlbum(mediaId)?.let { id ->
            return metadataDao.getAlbumById(id)?.let { albumFolder(it) }
        }
        AutoBrowseIds.parseArtist(mediaId)?.let { id ->
            return metadataDao.getArtistById(id)?.let { artistFolder(it) }
        }
        val track = trackDao.getTrack(mediaId) ?: return null
        return trackItem(track)
    }

    /** Expand browse media id to a playable timeline (album / playlist / track). */
    suspend fun expandForPlayback(mediaId: String): List<MediaItem> {
        AutoBrowseIds.parseAlbum(mediaId)?.let { albumId ->
            return albumTracks(albumId, offset = 0, size = 500)
        }
        AutoBrowseIds.parsePlaylist(mediaId)?.let { playlistId ->
            return playlistTracks(playlistId, offset = 0, size = 500)
        }
        AutoBrowseIds.parseArtist(mediaId)?.let { artistId ->
            val albums = metadataDao.getAlbumsByArtistId(artistId)
                .ifEmpty { metadataDao.getAlbumsByTrackArtistId(artistId) }
            return albums.flatMap { albumTracks(it.id, offset = 0, size = 200) }.take(500)
        }
        if (AutoBrowseIds.isBrowseNode(mediaId)) return emptyList()
        val track = trackDao.getTrack(mediaId) ?: return emptyList()
        return listOf(trackItem(track))
    }

    private fun rootChildren(): List<MediaItem> = listOf(
        folder(AutoBrowseIds.RECENT, "Recently played", playable = false),
        folder(AutoBrowseIds.FAVORITES, "Favorites", playable = false),
        folder(AutoBrowseIds.PLAYLISTS, "Playlists", playable = false),
        folder(AutoBrowseIds.ALBUMS, "Albums", playable = false),
        folder(AutoBrowseIds.ARTISTS, "Artists", playable = false),
    )

    private suspend fun playlistTracks(playlistId: String, offset: Int, size: Int): List<MediaItem> {
        val entries = playlistDao.getEntries(playlistId)
        val page = entries.drop(offset).take(size)
        if (page.isEmpty()) return emptyList()
        val tracks = trackDao.getTracksByIds(page.map { it.trackId }).associateBy { it.id }
        return page.mapNotNull { entry -> tracks[entry.trackId]?.let { trackItem(it) } }
    }

    private suspend fun albumTracks(albumId: String, offset: Int, size: Int): List<MediaItem> {
        val all = metadataDao.getAlbumTracks(albumId)
        return all.drop(offset).take(size).map { cachedTrackItem(it, albumId) }
    }

    private suspend fun artistAlbums(artistId: String, offset: Int, size: Int): List<MediaItem> {
        val albums = metadataDao.getAlbumsByArtistId(artistId)
            .ifEmpty { metadataDao.getAlbumsByTrackArtistId(artistId) }
        return albums.drop(offset).take(size).map { albumFolder(it) }
    }

    private fun playlistFolder(playlist: PlaylistEntity): MediaItem = folder(
        mediaId = AutoBrowseIds.playlist(playlist.id),
        title = playlist.name,
        subtitle = "${playlist.trackCount} tracks",
        artwork = artworkUriFor(playlist.coverArt),
        playable = true,
        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
    )

    private fun albumFolder(album: CachedAlbumEntity): MediaItem = folder(
        mediaId = AutoBrowseIds.album(album.id),
        title = album.name,
        subtitle = album.artist,
        artwork = artworkUriFor(album.coverArt),
        playable = true,
        mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
    )

    private fun artistFolder(artist: CachedArtistEntity): MediaItem = folder(
        mediaId = AutoBrowseIds.artist(artist.id),
        title = artist.name,
        subtitle = artist.albumCount?.let { "$it albums" },
        artwork = artworkUriFor(artist.coverArt),
        playable = true,
        mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
    )

    private fun trackItem(track: TrackEntity): MediaItem {
        // ADR-0095: prefer local file when path present; else stream; else not playable
        val uri = localOrStreamUri(track.cachedFilePath, track.id)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setIsBrowsable(false)
            .setIsPlayable(uri != null)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                artworkUriFor(track.coverArtUrl)?.let { setArtworkUri(it) }
                setExtras(
                    Bundle().apply {
                        putLong("duration", (track.durationSeconds ?: 0) * 1000L)
                        putString("type", "music")
                        putString("artistId", track.artistId)
                        putString("albumId", track.albumId)
                    },
                )
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .apply { if (uri != null) setUri(uri) }
            .setMediaMetadata(metadata)
            .build()
    }

    private suspend fun cachedTrackItem(track: CachedAlbumTrackEntity, albumId: String): MediaItem {
        val room = trackDao.getTrack(track.id)
        val uri = localOrStreamUri(room?.cachedFilePath, track.id)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setIsBrowsable(false)
            .setIsPlayable(uri != null)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                artworkUriFor(track.coverArt)?.let { setArtworkUri(it) }
                setExtras(
                    Bundle().apply {
                        putLong("duration", (track.duration ?: 0) * 1000L)
                        putString("type", "music")
                        putString("artistId", track.artistId)
                        putString("albumId", albumId)
                    },
                )
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .apply { if (uri != null) setUri(uri) }
            .setMediaMetadata(metadata)
            .build()
    }

    private fun localOrStreamUri(cachedFilePath: String?, trackId: String): Uri? {
        if (!cachedFilePath.isNullOrBlank()) {
            return try {
                Uri.parse("file://$cachedFilePath")
            } catch (_: Exception) {
                null
            } ?: streamUriFor(trackId)
        }
        return streamUriFor(trackId)
    }

    private fun folder(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artwork: Uri? = null,
        playable: Boolean,
        mediaType: @MediaMetadata.MediaType Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(playable)
            .setMediaType(mediaType)
            .apply { if (artwork != null) setArtworkUri(artwork) }
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun <T> pageList(all: List<T>, offset: Int, size: Int, map: (T) -> MediaItem): List<MediaItem> =
        all.drop(offset).take(size).map(map)
}
