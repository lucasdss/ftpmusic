package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedAlbumTrackEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixEntity
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.GenreEntity
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository
import com.lucasdss.ftpmusic.app.ui.player.QueueSheetGrouping
import java.time.LocalDate

/**
 * Local-first Android Auto browse tree (ADR-0091 / ADR-0131 / ADR-0138).
 * Room for library nodes; radio uses [AutoRadioStationsSource] (network + bookmarks).
 * Dual Up-Next via optional [queueSnapshotProvider].
 */
class AutoBrowseCatalog(
    private val trackDao: TrackDao,
    private val metadataDao: CachedMetadataDao,
    private val playlistDao: PlaylistDao,
    private val artworkUriFor: (coverArtId: String?) -> Uri?,
    private val streamUriFor: (trackId: String) -> Uri?,
    private val genreDao: GenreDao? = null,
    private val customMixDao: CustomMixDao? = null,
    private val genreMixDao: GenreMixDao? = null,
    private val dailyMixRepository: DailyMixRepository? = null,
    private val radioSource: AutoRadioStationsSource? = null,
    private val queueSnapshotProvider: AutoQueueSnapshotProvider? = null,
) {
    /** Revision-scoped Up-Next cache so page N matches page 0 (ADR-0138). */
    @Volatile
    private var cachedQueueSnapshot: AutoQueueBrowseSnapshot? = null

    /**
     * Prime from main-thread Dual snapshot before Queue browse (ADR-0138).
     * Same Dual revision can still change [AutoQueueBrowseSnapshot.currentIndex]
     * on playhead advance — refresh so Up Next bands drop the now-playing row.
     */
    fun primeQueueSnapshot(snapshot: AutoQueueBrowseSnapshot) {
        val cached = cachedQueueSnapshot
        if (cached != null &&
            cached.revision == snapshot.revision &&
            cached.currentIndex == snapshot.currentIndex
        ) {
            return
        }
        cachedQueueSnapshot = snapshot
    }

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

            AutoBrowseIds.OFFLINE -> trackDao.getDownloadedPaged(limit = size, offset = offset).map {
                trackItem(it)
            }

            AutoBrowseIds.FAVORITES -> trackDao.getStarred(limit = size, offset = offset).map { trackItem(it) }

            AutoBrowseIds.MIXES -> {
                val mixes = customMixDao?.getAll().orEmpty().drop(offset).take(size)
                mixes.map { mixFolder(it) }
            }

            AutoBrowseIds.PLAYLISTS -> playlistDao.getAll().drop(offset).take(size).map { playlistFolder(it) }

            AutoBrowseIds.ALBUMS -> metadataDao.getAlbumsPaged(size, offset).map { albumFolder(it) }

            AutoBrowseIds.ARTISTS -> metadataDao.getArtistsPaged(size, offset).map { artistFolder(it) }

            AutoBrowseIds.GENRES -> genreDao?.getAllByPopularity().orEmpty()
                .drop(offset).take(size).map { genreFolder(it) }

            AutoBrowseIds.RADIO -> loadRadioStations().drop(offset).take(size).map { radioItem(it) }

            AutoBrowseIds.QUEUE -> queueBandFolders()

            AutoBrowseIds.QUEUE_PRIORITY,
            AutoBrowseIds.QUEUE_CONTEXT,
            AutoBrowseIds.QUEUE_AUTOPLAY,
            AutoBrowseIds.QUEUE_LATE,
            -> queueBandTracks(parentId, offset, size)

            else -> {
                AutoBrowseIds.parsePlaylist(parentId)?.let { id ->
                    playlistTracks(id, offset, size)
                } ?: AutoBrowseIds.parseAlbum(parentId)?.let { id ->
                    albumTracks(id, offset, size)
                } ?: AutoBrowseIds.parseArtist(parentId)?.let { id ->
                    artistAlbums(id, offset, size)
                } ?: AutoBrowseIds.parseMix(parentId)?.let { id ->
                    mixTracks(id, offset, size)
                } ?: AutoBrowseIds.parseGenre(parentId)?.let { name ->
                    genreTracks(name, offset, size)
                } ?: emptyList()
            }
        }
    }

    suspend fun item(mediaId: String): MediaItem? {
        when (mediaId) {
            AutoBrowseIds.ROOT -> return rootItem()

            AutoBrowseIds.RECENT -> return folder(AutoBrowseIds.RECENT, "Recently played", playable = false)

            AutoBrowseIds.OFFLINE -> return folder(AutoBrowseIds.OFFLINE, "Downloads", playable = false)

            AutoBrowseIds.FAVORITES -> return folder(AutoBrowseIds.FAVORITES, "Favorites", playable = false)

            AutoBrowseIds.MIXES -> return folder(AutoBrowseIds.MIXES, "Daily Mixes", playable = false)

            AutoBrowseIds.PLAYLISTS -> return folder(AutoBrowseIds.PLAYLISTS, "Playlists", playable = false)

            AutoBrowseIds.ALBUMS -> return folder(AutoBrowseIds.ALBUMS, "Albums", playable = false)

            AutoBrowseIds.ARTISTS -> return folder(AutoBrowseIds.ARTISTS, "Artists", playable = false)

            AutoBrowseIds.GENRES -> return folder(AutoBrowseIds.GENRES, "Genres", playable = false)

            AutoBrowseIds.RADIO -> return folder(AutoBrowseIds.RADIO, "Live Radio", playable = false)

            AutoBrowseIds.QUEUE -> return folder(AutoBrowseIds.QUEUE, "Queue", playable = false)

            AutoBrowseIds.QUEUE_PRIORITY -> return folder(
                AutoBrowseIds.QUEUE_PRIORITY,
                "Next in Queue",
                playable = false,
            )

            AutoBrowseIds.QUEUE_CONTEXT -> return folder(AutoBrowseIds.QUEUE_CONTEXT, "Next from", playable = false)

            AutoBrowseIds.QUEUE_AUTOPLAY -> return folder(AutoBrowseIds.QUEUE_AUTOPLAY, "Autoplay", playable = false)

            AutoBrowseIds.QUEUE_LATE -> return folder(AutoBrowseIds.QUEUE_LATE, "Queued later", playable = false)
        }
        AutoBrowseIds.parseQueueTrack(mediaId)?.let { entryId ->
            return queueTrackItem(entryId)
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
        AutoBrowseIds.parseMix(mediaId)?.let { id ->
            return customMixDao?.getById(id)?.let { mixFolder(it) }
        }
        AutoBrowseIds.parseGenre(mediaId)?.let { name ->
            val genres = genreDao?.getGenresByNames(listOf(name)).orEmpty()
            return genres.firstOrNull()?.let { genreFolder(it) }
                ?: folder(AutoBrowseIds.genre(name), name, playable = false)
        }
        AutoBrowseIds.parseRadio(mediaId)?.let { stationId ->
            return loadRadioStations().firstOrNull { it.id == stationId }?.let { radioItem(it) }
        }
        val track = trackDao.getTrack(mediaId) ?: return null
        return trackItem(track)
    }

    /** Expand browse media id to a playable timeline. */
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
        AutoBrowseIds.parseMix(mediaId)?.let { mixId ->
            return mixTracks(mixId, offset = 0, size = 500)
        }
        AutoBrowseIds.parseGenre(mediaId)?.let { name ->
            return genreTracks(name, offset = 0, size = 500)
        }
        AutoBrowseIds.parseRadio(mediaId)?.let { stationId ->
            val station = loadRadioStations().firstOrNull { it.id == stationId } ?: return emptyList()
            return listOf(radioItem(station))
        }
        when (mediaId) {
            AutoBrowseIds.OFFLINE -> return trackDao.getDownloadedPaged(limit = 500, offset = 0).map { trackItem(it) }

            AutoBrowseIds.FAVORITES -> return trackDao.getStarred(limit = 500, offset = 0).map { trackItem(it) }

            AutoBrowseIds.RECENT -> return trackDao.getRecentlyPlayedPaged(limit = 500, offset = 0).map {
                trackItem(it)
            }
        }
        if (AutoBrowseIds.isBrowseNode(mediaId)) return emptyList()
        val track = trackDao.getTrack(mediaId) ?: return emptyList()
        return listOf(trackItem(track))
    }

    /**
     * Flatten local search hit into Auto MediaItems (tracks + folder hits), then page.
     * Used by [MediaSessionCallback.onGetSearchResult] — not the voice expand path.
     */
    fun searchResultItems(
        tracks: List<TrackEntity>,
        albums: List<CachedAlbumEntity>,
        artists: List<CachedArtistEntity>,
        playlists: List<PlaylistEntity>,
        genres: List<GenreEntity>,
        page: Int,
        pageSize: Int,
    ): List<MediaItem> {
        val all = buildList {
            tracks.forEach { add(trackItem(it)) }
            albums.forEach { add(albumFolder(it)) }
            artists.forEach { add(artistFolder(it)) }
            playlists.forEach { add(playlistFolder(it)) }
            genres.forEach { add(genreFolder(it)) }
        }
        val size = pageSize.coerceIn(1, 100)
        val offset = (page.coerceAtLeast(0)) * size
        return all.drop(offset).take(size)
    }

    private fun rootChildren(): List<MediaItem> = listOf(
        folder(AutoBrowseIds.RECENT, "Recently played", playable = false),
        folder(AutoBrowseIds.QUEUE, "Queue", playable = false),
        folder(AutoBrowseIds.OFFLINE, "Downloads", playable = false),
        folder(AutoBrowseIds.FAVORITES, "Favorites", playable = false),
        folder(AutoBrowseIds.MIXES, "Daily Mixes", playable = false),
        folder(AutoBrowseIds.PLAYLISTS, "Playlists", playable = false),
        folder(AutoBrowseIds.ALBUMS, "Albums", playable = false),
        folder(AutoBrowseIds.ARTISTS, "Artists", playable = false),
        folder(AutoBrowseIds.GENRES, "Genres", playable = false),
        folder(AutoBrowseIds.RADIO, "Live Radio", playable = false),
    )

    private fun queueSnapshot(): AutoQueueBrowseSnapshot {
        val cached = cachedQueueSnapshot
        if (cached != null) return cached
        val live = queueSnapshotProvider?.snapshot()
            ?: return AutoQueueBrowseSnapshot(revision = 0L, currentIndex = -1, rows = emptyList())
        cachedQueueSnapshot = live
        return live
    }

    private fun queueBandFolders(): List<MediaItem> {
        val bands = groupUpcoming(queueSnapshot())
        return buildList {
            if (bands.priority.isNotEmpty()) {
                add(
                    folder(
                        AutoBrowseIds.QUEUE_PRIORITY,
                        "Next in Queue",
                        subtitle = "${bands.priority.size}",
                        playable = false,
                    ),
                )
            }
            if (bands.continuePlaying.isNotEmpty()) {
                add(
                    folder(
                        AutoBrowseIds.QUEUE_CONTEXT,
                        "Next from",
                        subtitle = "${bands.continuePlaying.size}",
                        playable = false,
                    ),
                )
            }
            if (bands.autoplay.isNotEmpty()) {
                add(
                    folder(
                        AutoBrowseIds.QUEUE_AUTOPLAY,
                        "Autoplay",
                        subtitle = "${bands.autoplay.size}",
                        playable = false,
                    ),
                )
            }
            if (bands.late.isNotEmpty()) {
                add(
                    folder(
                        AutoBrowseIds.QUEUE_LATE,
                        "Queued later",
                        subtitle = "${bands.late.size}",
                        playable = false,
                    ),
                )
            }
        }
    }

    private fun queueBandTracks(bandId: String, offset: Int, size: Int): List<MediaItem> {
        val bands = groupUpcoming(queueSnapshot())
        val rows = when (bandId) {
            AutoBrowseIds.QUEUE_PRIORITY -> bands.priority
            AutoBrowseIds.QUEUE_CONTEXT -> bands.continuePlaying
            AutoBrowseIds.QUEUE_AUTOPLAY -> bands.autoplay
            AutoBrowseIds.QUEUE_LATE -> bands.late
            else -> emptyList()
        }
        return rows.drop(offset.coerceAtLeast(0)).take(size).map { queueTrackMediaItem(it) }
    }

    private fun queueTrackItem(entryId: Int): MediaItem? {
        val row = queueSnapshot().rows.firstOrNull { it.entryId == entryId } ?: return null
        return queueTrackMediaItem(row)
    }

    /** Group Up Next with the same band rules as the phone queue sheet (ADR-0138). */
    internal fun groupUpcoming(snapshot: AutoQueueBrowseSnapshot) = QueueSheetGrouping.groupUpcoming(
        items = snapshot.rows,
        isPriority = { it.isPriority },
        isLate = { it.isLate },
        isAutoplay = { it.isAutoplay },
        include = { true },
    )

    private suspend fun loadRadioStations(): List<AutoRadioStation> = try {
        radioSource?.loadStations().orEmpty()
    } catch (e: Exception) {
        android.util.Log.w("ftpmusic", "[AutoBrowse] radio load failed: ${e.message}")
        emptyList()
    }

    private suspend fun playlistTracks(playlistId: String, offset: Int, size: Int): List<MediaItem> {
        val entries = playlistDao.getEntries(playlistId)
        // ADR-0136: page by playable index (not raw entry offset) so dangling
        // skips on page 0 do not duplicate/skip on page N.
        val orderedIds = entries.map { it.trackId }
        val present = trackDao.getTracksByIds(orderedIds).associateBy { it.id }
        return orderedIds
            .mapNotNull { present[it] }
            .drop(offset.coerceAtLeast(0))
            .take(size)
            .map { trackItem(it) }
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

    private suspend fun mixTrackIds(mixId: Long): List<String> {
        val mixDao = genreMixDao ?: return emptyList()
        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()
        val row = mixDao.getDailyMix(today, mixId) ?: mixDao.getDailyMix(yesterday, mixId) ?: return emptyList()
        val ids = mixDao.getDailyMixTrackIds(row.id)
        val filtered = dailyMixRepository?.filterPlayableMixTrackIds(ids) ?: ids
        return filtered
    }

    private suspend fun mixTracks(mixId: Long, offset: Int, size: Int): List<MediaItem> {
        val allIds = mixTrackIds(mixId)
        // ADR-0136: page by playable index after Room filter (same as playlist).
        val present = trackDao.getTracksByIds(allIds).associateBy { it.id }
        return allIds
            .mapNotNull { present[it] }
            .drop(offset.coerceAtLeast(0))
            .take(size)
            .map { trackItem(it) }
    }

    private suspend fun genreTracks(genre: String, offset: Int, size: Int): List<MediaItem> =
        trackDao.getTracksByGenrePaged(genre, size, offset).map { trackItem(it) }

    private suspend fun mixFolder(mix: CustomMixEntity): MediaItem {
        val count = mixTrackIds(mix.id).size
        return folder(
            mediaId = AutoBrowseIds.mix(mix.id),
            title = mix.name,
            subtitle = if (count > 0) "$count tracks" else null,
            playable = count > 0,
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
        )
    }

    private fun genreFolder(genre: GenreEntity): MediaItem = folder(
        mediaId = AutoBrowseIds.genre(genre.name),
        title = genre.name,
        subtitle = if (genre.songCount > 0) "${genre.songCount} tracks" else null,
        playable = genre.songCount > 0,
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    )

    private fun radioItem(station: AutoRadioStation): MediaItem {
        val uri = station.streamUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        val metadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setIsBrowsable(false)
            .setIsPlayable(uri != null)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .apply {
                setExtras(
                    Bundle().apply {
                        putString("type", "radio")
                        putString("streamUrl", station.streamUrl)
                        station.homePageUrl?.let { putString("homePageUrl", it) }
                    },
                )
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(AutoBrowseIds.radio(station.id))
            .apply { if (uri != null) setUri(uri) }
            .setMediaMetadata(metadata)
            .build()
    }

    private fun playlistFolder(playlist: PlaylistEntity): MediaItem = folder(
        mediaId = AutoBrowseIds.playlist(playlist.id),
        title = playlist.name,
        subtitle = "${playlist.trackCount} tracks",
        artwork = artworkUriFor(playlist.coverArt),
        playable = playlist.trackCount > 0,
        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
    )

    private fun albumFolder(album: CachedAlbumEntity): MediaItem = folder(
        mediaId = AutoBrowseIds.album(album.id),
        title = album.name,
        subtitle = album.artist,
        artwork = artworkUriFor(album.coverArt),
        playable = (album.songCount ?: 0) > 0,
        mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
    )

    private fun artistFolder(artist: CachedArtistEntity): MediaItem = folder(
        mediaId = AutoBrowseIds.artist(artist.id),
        title = artist.name,
        subtitle = artist.albumCount?.let { "$it albums" },
        artwork = artworkUriFor(artist.coverArt),
        // ADR-0132: empty artists stay browsable but not Play-all.
        playable = (artist.albumCount ?: 0) > 0,
        mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
    )

    private fun trackItem(track: TrackEntity): MediaItem {
        val uri = streamUriFor(track.id)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setIsBrowsable(false)
            .setIsPlayable(uri != null)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                // ADR-0137: per-item Play Next / Add only when streamable.
                if (uri != null) {
                    setSupportedCommands(MediaSessionQueueCommands.SUPPORTED_COMMANDS)
                }
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
        val uri = streamUriFor(track.id)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setIsBrowsable(false)
            .setIsPlayable(uri != null)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                if (uri != null) {
                    setSupportedCommands(MediaSessionQueueCommands.SUPPORTED_COMMANDS)
                }
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

    /** Queue-node row: playable jump target, no Play Next/Add buttons (ADR-0138). */
    private fun queueTrackMediaItem(row: AutoQueueBrowseRow): MediaItem {
        val uri = streamUriFor(row.mediaId)
        val metadata = MediaMetadata.Builder()
            .setTitle(row.title)
            .setArtist(row.artist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                setExtras(
                    Bundle().apply {
                        putString("type", "music")
                        putInt("queueEntryId", row.entryId)
                    },
                )
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(AutoBrowseIds.queueTrack(row.entryId))
            .apply { if (uri != null) setUri(uri) }
            .setMediaMetadata(metadata)
            .build()
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
}
