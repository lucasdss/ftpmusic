package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Rating
import androidx.media3.common.ThumbRating
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext

/**
 * Implements the Android SDK-recommended pattern for MediaLibrarySession:
 * overrides onAddMediaItems to expand single-track external play requests
 * (from Bluetooth, Android Auto, notification, Wear OS) to the full
 * parent album context.
 *
 * Also implements [onPlaybackResumption] (ADR-0087) so MediaButtonReceiver /
 * System UI can rebuild the last queue after process death.
 *
 * Android Auto browse tree: [onGetLibraryRoot] / [onGetChildren] / [onGetItem]
 * via [AutoBrowseCatalog] (ADR-0091 / ADR-0131). Local-first Room + radio source.
 *
 * In-Auto search: [onSearch] / [onGetSearchResult] (ADR-0131). Voice
 * [searchAndNavidromeExpand] stays album-expand — do not converge.
 *
 * Like/dislike custom commands (ADR-0118): [onConnect] / [onCustomCommand] /
 * [onSetRating] via [CurrentTrackReactionController]. Radio gated (ADR-0131).
 *
 * The requested track is placed first in the returned list so playback
 * starts at the correct position. The remaining album tracks follow in
 * their original order.
 *
 * DB queries run asynchronously via coroutines — the callback's
 * ListenableFuture resolves once expansion completes, keeping the
 * binder thread unblocked.
 */
class MediaSessionCallback(
    private val trackDao: TrackDao,
    private val metadataDao: CachedMetadataDao,
    private val authHelper: SubsonicAuthHelper,
    private val api: SubsonicApi,
    private val localSearch: com.lucasdss.ftpmusic.app.data.search.LocalSearchRepository? = null,
    private val persistenceManager: QueuePersistenceManager? = null,
    private val playlistDao: PlaylistDao? = null,
    private val reactionController: CurrentTrackReactionController? = null,
    private val genreDao: com.lucasdss.ftpmusic.app.data.db.GenreDao? = null,
    private val customMixDao: com.lucasdss.ftpmusic.app.data.db.CustomMixDao? = null,
    private val genreMixDao: com.lucasdss.ftpmusic.app.data.db.GenreMixDao? = null,
    private val dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository? = null,
    private val radioSource: AutoRadioStationsSource? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : MediaLibraryService.MediaLibrarySession.Callback {

    /** Cached in-Auto search hits keyed by query (ADR-0131). */
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, SearchCacheEntry>()

    private data class SearchCacheEntry(
        val tracks: List<com.lucasdss.ftpmusic.app.data.db.TrackEntity>,
        val albums: List<com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity>,
        val artists: List<com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity>,
        val playlists: List<com.lucasdss.ftpmusic.app.data.db.PlaylistEntity>,
        val genres: List<com.lucasdss.ftpmusic.app.data.db.GenreEntity>,
        val totalCount: Int,
    )

    private val browseCatalog: AutoBrowseCatalog? by lazy {
        val playlists = playlistDao ?: return@lazy null
        AutoBrowseCatalog(
            trackDao = trackDao,
            metadataDao = metadataDao,
            playlistDao = playlists,
            artworkUriFor = { artworkUriForTrack(it) },
            streamUriFor = { trackId ->
                val username = SubsonicCredentials.username
                val password = SubsonicCredentials.password
                val baseUrl = DynamicBaseUrl.url.trimEnd('/')
                if (username.isEmpty() || password.isEmpty() || !DynamicBaseUrl.isConfigured()) {
                    null
                } else {
                    Uri.parse(authHelper.buildStreamUrl(baseUrl, trackId, username, password))
                }
            },
            genreDao = genreDao,
            customMixDao = customMixDao,
            genreMixDao = genreMixDao,
            dailyMixRepository = dailyMixRepository,
            radioSource = radioSource,
        )
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val catalog = browseCatalog
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
        return Futures.immediateFuture(LibraryResult.ofItem(catalog.rootItem(), params))
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val catalog = browseCatalog
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
        return scope.future {
            try {
                val kids = catalog.children(parentId, page, pageSize)
                LibraryResult.ofItemList(ImmutableList.copyOf(kids), params)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[MediaSessionCallback] onGetChildren failed: ${e.message}")
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val catalog = browseCatalog
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
        return scope.future {
            try {
                val item = catalog.item(mediaId)
                if (item != null) {
                    LibraryResult.ofItem(item, null)
                } else {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[MediaSessionCallback] onGetItem failed: ${e.message}")
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        if (mediaItems.isEmpty()) return Futures.immediateFuture(mediaItems)
        if (mediaItems.size != 1) return Futures.immediateFuture(mediaItems)
        val first = mediaItems.first()
        val mediaId = first.mediaId ?: return Futures.immediateFuture(mediaItems)
        // Radio items already carry stream URI — do not album-expand.
        if (AutoBrowseIds.isRadioMediaId(mediaId) ||
            first.mediaMetadata.extras?.getString("type") == "radio"
        ) {
            return Futures.immediateFuture(mediaItems)
        }
        return scope.future {
            try {
                if (AutoBrowseIds.isBrowseNode(mediaId)) {
                    val expanded = browseCatalog?.expandForPlayback(mediaId).orEmpty()
                    // Empty expand (dangling playlist entries, empty mix): do not
                    // fall through to album expand with a folder MediaItem (no URI).
                    return@future if (expanded.isNotEmpty()) expanded else emptyList()
                }
                val expanded = expandToAlbum(mediaId)
                if (expanded != null && expanded.size > 1) expanded else mediaItems
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[MediaSessionCallback] expand failed for $mediaId", e)
                mediaItems
            }
        }
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        if (query.isBlank()) {
            session.notifySearchResultChanged(browser, query, 0, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
        return scope.future {
            try {
                val entry = runSearch(query)
                searchCache[query] = entry
                session.notifySearchResultChanged(browser, query, entry.totalCount, params)
                LibraryResult.ofVoid()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[MediaSessionCallback] onSearch failed: ${e.message}")
                session.notifySearchResultChanged(browser, query, 0, params)
                LibraryResult.ofVoid()
            }
        }
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val catalog = browseCatalog
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
        return scope.future {
            try {
                val entry = searchCache[query] ?: runSearch(query).also { searchCache[query] = it }
                val items = catalog.searchResultItems(
                    tracks = entry.tracks,
                    albums = entry.albums,
                    artists = entry.artists,
                    playlists = entry.playlists,
                    genres = entry.genres,
                    page = page,
                    pageSize = pageSize,
                )
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[MediaSessionCallback] onGetSearchResult failed: ${e.message}")
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    private suspend fun runSearch(query: String): SearchCacheEntry {
        val local = try {
            localSearch?.search(query, limit = 50, playableOnly = false)
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic", "[MediaSessionCallback] local search failed: ${e.message}")
            null
        }
        var tracks = local?.tracks.orEmpty()
        val albums = local?.albums.orEmpty()
        val artists = local?.artists.orEmpty()
        val playlists = local?.playlists.orEmpty()
        val genres = local?.genres.orEmpty()

        // Optional search3 when local corpus empty and credentials present.
        if (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            tracks = networkSearchTracks(query)
        }

        val total = tracks.size + albums.size + artists.size + playlists.size + genres.size
        return SearchCacheEntry(tracks, albums, artists, playlists, genres, total)
    }

    private suspend fun networkSearchTracks(query: String): List<com.lucasdss.ftpmusic.app.data.db.TrackEntity> {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isEmpty() || password.isEmpty() || !DynamicBaseUrl.isConfigured()) {
            return emptyList()
        }
        return try {
            val auth = authHelper.buildAuthParams(username, password)
            val response = api.search3(query, songCount = 20, artistCount = 0, albumCount = 0, auth = auth)
            val sr = response["subsonic-response"] as? Map<*, *> ?: return emptyList()
            val searchResult = sr["searchResult3"] as? Map<*, *> ?: return emptyList()
            val songs: List<*> = when (val raw = searchResult["song"]) {
                is List<*> -> raw
                is Map<*, *> -> listOf(raw)
                else -> return emptyList()
            }
            val maps = songs.mapNotNull { it as? Map<*, *> }
            val ids = maps.mapNotNull { songIdOf(it["id"]) }
            val fromRoom = if (ids.isEmpty()) emptyList() else trackDao.getTracksByIds(ids)
            if (fromRoom.isNotEmpty()) {
                // Preserve search3 ranking where possible.
                val byId = fromRoom.associateBy { it.id }
                return ids.mapNotNull { byId[it] }.ifEmpty { fromRoom }
            }
            // Local corpus empty: synthesize thin TrackEntity rows from search3
            // so Auto search can still show / play streamable hits (ADR-0131).
            maps.mapNotNull { m ->
                val id = songIdOf(m["id"]) ?: return@mapNotNull null
                com.lucasdss.ftpmusic.app.data.db.TrackEntity(
                    id = id,
                    title = m["title"] as? String ?: id,
                    artist = m["artist"] as? String,
                    album = m["album"] as? String,
                    albumId = songIdOf(m["albumId"]),
                    artistId = songIdOf(m["artistId"]),
                    coverArtUrl = songIdOf(m["coverArt"]),
                    durationSeconds = when (val d = m["duration"]) {
                        is Number -> d.toInt()
                        is String -> d.toIntOrNull()
                        else -> null
                    },
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic", "[MediaSessionCallback] search3 fallback failed: ${e.message}")
            emptyList()
        }
    }

    private fun songIdOf(raw: Any?): String? = when (raw) {
        is String -> raw.takeIf { it.isNotBlank() }
        is Number -> raw.toString()
        else -> null
    }

    private fun isRadioCurrent(mediaItem: MediaItem?): Boolean {
        if (mediaItem == null) return false
        val id = mediaItem.mediaId
        if (!id.isNullOrBlank() && AutoBrowseIds.isRadioMediaId(id)) return true
        return mediaItem.mediaMetadata.extras?.getString("type") == "radio"
    }

    /**
     * ADR-0087/0088: rebuild last queue from Room for BT / System UI resumption.
     * Never returns an empty playlist when a saved queue exists (Media3 FGS hazard).
     * When no queue: fail like the Media3 default (UnsupportedOperationException).
     * If the player is already seated (BT eager restore), return that playlist.
     */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        val alreadySeated = withContext(mainDispatcher) {
            PlaybackResumptionMapper.fromPlayer(mediaSession.player)
        }
        if (alreadySeated != null) {
            android.util.Log.i(
                "ftpmusic",
                "[MediaSessionCallback] onPlaybackResumption using seated player " +
                    "items=${alreadySeated.mediaItems.size}",
            )
            return@future alreadySeated
        }
        val persistence = persistenceManager
            ?: throw UnsupportedOperationException("Queue persistence unavailable")
        val saved = persistence.restore()
            ?: throw UnsupportedOperationException("No saved queue for resumption")
        val playlist = PlaybackResumptionMapper.toMediaItemsWithStartPosition(
            saved,
            artworkUriFor = { track -> artworkUriForTrack(track.coverArt) },
        ) ?: throw UnsupportedOperationException("Empty saved queue")
        if (isForPlayback) {
            try {
                withContext(mainDispatcher) {
                    PlaybackResumptionMapper.applyTransportExtras(
                        mediaSession.player,
                        saved.repeatMode,
                        saved.shuffleEnabled,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w(
                    "ftpmusic",
                    "[MediaSessionCallback] applyTransportExtras failed: ${e.message}",
                )
            }
        }
        android.util.Log.i(
            "ftpmusic",
            "[MediaSessionCallback] onPlaybackResumption items=${playlist.mediaItems.size} " +
                "index=${playlist.startIndex} isForPlayback=$isForPlayback",
        )
        playlist
    }

    private fun artworkUriForTrack(coverArtId: String?): Uri? {
        if (coverArtId.isNullOrBlank() || coverArtId == "getCoverArt") return null
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        val baseUrl = DynamicBaseUrl.url.trimEnd('/')
        if (username.isEmpty() || password.isEmpty() || !DynamicBaseUrl.isConfigured()) return null
        return try {
            val authParams = authHelper.buildAuthParams(username, password)
            val authQs = authParams.entries.joinToString("&") {
                "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
            Uri.parse("$baseUrl/rest/getCoverArt?id=$coverArtId&$authQs")
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun expandToAlbum(mediaId: String, prebuiltAuth: Map<String, String>? = null): List<MediaItem>? {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        val baseUrl = DynamicBaseUrl.url.trimEnd('/')
        if (username.isEmpty() || password.isEmpty() || !DynamicBaseUrl.isConfigured()) return null

        val track = trackDao.getTrack(mediaId) ?: return null
        val albumId = track.albumId ?: return null

        val albumTracks = metadataDao.getAlbumTracks(albumId)
        if (albumTracks.size <= 1) return null

        // If requested track not in album, fall back to single item
        if (albumTracks.none { it.id == mediaId }) return null

        // Look up album name for setAlbumTitle
        val albumName = metadataDao.getAlbumName(albumId)

        // Build auth params once for all cover art URLs — reuse if provided by caller
        val authParams = prebuiltAuth ?: authHelper.buildAuthParams(username, password)
        val authQs = authParams.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

        // Build MediaItems with the requested track FIRST, then remaining in order
        val allItems = albumTracks.map { t ->
            val url = authHelper.buildStreamUrl(baseUrl, t.id, username, password)
            val metadata = MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(albumName ?: "")
                .apply {
                    if (t.coverArt != null) {
                        setArtworkUri(android.net.Uri.parse("$baseUrl/rest/getCoverArt?id=${t.coverArt}&$authQs"))
                    }
                    setExtras(
                        Bundle().apply {
                            putLong("duration", (t.duration ?: 0) * 1000L)
                            putString("type", "music")
                            putString("artistId", t.artistId)
                            putString("albumId", t.albumId)
                        },
                    )
                }
                .build()

            MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(url)
                .setMediaMetadata(metadata)
                .build()
        }

        // Reorder: requested track first, then rest in album order (single-pass)
        val (requested, rest) = allItems.partition { it.mediaId == mediaId }
        return requested + rest
    }

    /**
     * Search Navidrome for [query], take the top song match, and expand
     * it to full album context. Returns empty list if no match found
     * or credentials are missing.
     *
     * Called from MediaService when a MEDIA_PLAY_FROM_SEARCH intent is
     * received (Assistant / Gemini voice commands). The caller is
     * responsible for setting items on the player and starting playback.
     *
     * Public visibility for use by MediaService; not part of the
     * Callback interface.
     */
    suspend fun searchAndNavidromeExpand(query: String): List<MediaItem> {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        val baseUrl = DynamicBaseUrl.url.trimEnd('/')
        if (username.isEmpty() || password.isEmpty() || !DynamicBaseUrl.isConfigured()) {
            android.util.Log.d("ftpmusic", "[MediaSessionCallback] searchAndNavidromeExpand: credentials not available")
            return emptyList()
        }

        val auth = authHelper.buildAuthParams(username, password)

        // Prefer local FTS/LIKE corpus (same as Search tab), then search3.
        try {
            val local = localSearch?.search(query, limit = 10, playableOnly = false)
            val localTrack = local?.tracks?.firstOrNull()
            if (localTrack != null) {
                android.util.Log.d(
                    "ftpmusic",
                    "[MediaSessionCallback] searchAndNavidromeExpand: local hit '$query' → ${localTrack.id}",
                )
                return expandToAlbum(localTrack.id, auth) ?: emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic", "[MediaSessionCallback] local search failed: ${e.message}")
        }

        val response = api.search3(query, songCount = 10, artistCount = 20, albumCount = 10, auth = auth)
        val sr = response["subsonic-response"] as? Map<*, *> ?: run {
            android.util.Log.d(
                "ftpmusic",
                "[MediaSessionCallback] searchAndNavidromeExpand: no subsonic-response in result",
            )
            return emptyList()
        }
        val searchResult = sr["searchResult3"] as? Map<*, *> ?: run {
            android.util.Log.d("ftpmusic", "[MediaSessionCallback] searchAndNavidromeExpand: no searchResult3")
            return emptyList()
        }
        val songs: List<*> = when (val raw = searchResult["song"]) {
            is List<*> -> raw

            is Map<*, *> -> listOf(raw)

            // Subsonic API returns single object, not list, for 1 result
            else -> null
        } ?: run {
            android.util.Log.d("ftpmusic", "[MediaSessionCallback] searchAndNavidromeExpand: no songs in result")
            return emptyList()
        }
        if (songs.isEmpty()) return emptyList()

        val firstSong = songs.first() as? Map<*, *> ?: return emptyList()
        val songId = firstSong["id"] as? String ?: return emptyList()
        android.util.Log.d(
            "ftpmusic",
            "[MediaSessionCallback] searchAndNavidromeExpand: matched '$query' → songId=$songId",
        )

        return expandToAlbum(songId, auth) ?: emptyList()
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val snap = reactionController?.reactions?.value
        val buttons = MediaSessionReactionCommands.buttons(
            isStarred = snap?.isStarred == true,
            isDisliked = snap?.isDisliked == true,
        )
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(MediaSessionReactionCommands.LIKE_COMMAND)
            .add(MediaSessionReactionCommands.DISLIKE_COMMAND)
            .add(SessionCommand.COMMAND_CODE_SESSION_SET_RATING)
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setMediaButtonPreferences(buttons)
            .setCustomLayout(buttons)
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        val action = customCommand.customAction
        if (action != MediaSessionReactionCommands.ACTION_LIKE &&
            action != MediaSessionReactionCommands.ACTION_DISLIKE
        ) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
        val controllerRef = reactionController
            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        return scope.future {
            val current = withContext(mainDispatcher) { session.player.currentMediaItem }
            if (isRadioCurrent(current)) {
                return@future SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
            }
            val trackId = current?.mediaId
            if (trackId.isNullOrBlank()) {
                return@future SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)
            }
            try {
                // Emit via controller — MediaService.wireReactionSurfaceUpdates refreshes
                // session buttons / ThumbRating / notification (ADR-0118).
                when (action) {
                    MediaSessionReactionCommands.ACTION_LIKE -> controllerRef.toggleLike(trackId)
                    else -> controllerRef.toggleDislike(trackId)
                }
                SessionResult(SessionResult.RESULT_SUCCESS)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[MediaSessionCallback] reaction failed: ${e.message}")
                SessionResult(SessionResult.RESULT_ERROR_UNKNOWN)
            }
        }
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        rating: Rating,
    ): ListenableFuture<SessionResult> {
        val controllerRef = reactionController
            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        if (rating !is ThumbRating) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
        return scope.future {
            val currentItem = withContext(mainDispatcher) { session.player.currentMediaItem }
            if (isRadioCurrent(currentItem)) {
                return@future SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
            }
            val trackId = currentItem?.mediaId
            if (trackId.isNullOrBlank()) {
                return@future SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)
            }
            try {
                val current = controllerRef.load(trackId)
                when {
                    !rating.isRated -> {
                        when {
                            current.isStarred -> controllerRef.toggleLike(trackId, wasStarred = true)
                            current.isDisliked -> controllerRef.toggleDislike(trackId, wasDisliked = true)
                            else -> Unit
                        }
                    }

                    rating.isThumbsUp -> {
                        if (!current.isStarred) {
                            controllerRef.toggleLike(trackId, wasStarred = false)
                        }
                    }

                    else -> {
                        if (!current.isDisliked) {
                            controllerRef.toggleDislike(trackId, wasDisliked = false)
                        }
                    }
                }
                SessionResult(SessionResult.RESULT_SUCCESS)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[MediaSessionCallback] onSetRating failed: ${e.message}")
                SessionResult(SessionResult.RESULT_ERROR_UNKNOWN)
            }
        }
    }

    /** Refresh overflow buttons + ThumbRating metadata after a reaction change. */
    fun refreshReactionSurfaces(session: MediaSession, snap: CurrentTrackReactionController.Snapshot) {
        val current = try {
            session.player.currentMediaItem
        } catch (_: Exception) {
            null
        }
        if (isRadioCurrent(current)) {
            try {
                session.setMediaButtonPreferences(ImmutableList.of())
                session.setCustomLayout(ImmutableList.of())
            } catch (_: Exception) {
                // Session may be released during teardown.
            }
            return
        }
        val buttons = MediaSessionReactionCommands.buttons(snap.isStarred, snap.isDisliked)
        try {
            session.setMediaButtonPreferences(buttons)
            session.setCustomLayout(buttons)
        } catch (_: Exception) {
            // Session may be released during teardown.
        }
        MediaSessionReactionCommands.applyThumbRatingToCurrentItem(
            session.player,
            snap.isStarred,
            snap.isDisliked,
        )
    }

    /**
     * ADR-0088: MediaSessionCallback is a DI singleton — do **not** cancel its
     * CoroutineScope on MediaService destroy or onPlaybackResumption dies until
     * process death. Pending expand work is best-effort after teardown.
     */
    fun destroy() {
        // no-op (intentionally)
    }
}
