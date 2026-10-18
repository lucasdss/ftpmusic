package com.lucasdss.ftpmusic.app.ui.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.lucasdss.ftpmusic.app.data.cache.LocalOnlyPolicy
import com.lucasdss.ftpmusic.app.data.db.AlbumEntity
import com.lucasdss.ftpmusic.app.data.db.ArtistEntity
import com.lucasdss.ftpmusic.app.data.db.DesiredItemEntity
import com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.favorites.FavoritePendingKind
import com.lucasdss.ftpmusic.app.data.favorites.FavoritePendingStore
import com.lucasdss.ftpmusic.app.data.favorites.FavoritesPaging
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import com.lucasdss.ftpmusic.app.ui.*
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.BrandTeal
import com.lucasdss.ftpmusic.app.ui.NavUnselected
import com.lucasdss.ftpmusic.app.ui.Surface
import com.lucasdss.ftpmusic.app.ui.SurfaceChip
import com.lucasdss.ftpmusic.app.ui.components.ArtistAvatar
import com.lucasdss.ftpmusic.app.ui.components.CoverArtImage
import com.lucasdss.ftpmusic.app.ui.components.RadioBookmarkIcon
import com.lucasdss.ftpmusic.app.ui.components.ReactionGlyphButton
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChip
import com.lucasdss.ftpmusic.app.ui.components.SegmentedChipRow
import com.lucasdss.ftpmusic.app.ui.components.SongListRow
import com.lucasdss.ftpmusic.app.ui.components.downloadStatus
import com.lucasdss.ftpmusic.app.ui.components.formatSongDuration
import com.lucasdss.ftpmusic.app.ui.library.rememberCoverArtUrl
import com.lucasdss.ftpmusic.app.ui.search.DiscoverAlbumHit
import com.lucasdss.ftpmusic.app.ui.search.DiscoverArtistHit
import com.lucasdss.ftpmusic.app.ui.search.DiscoverTrackHit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class FavoritesMode { LIKED, DISLIKED, DESIRED }

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val favoriteRepository: FavoriteRepository,
    private val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao,
    private val radioFavoriteDao: com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao,
    private val desiredRepository: com.lucasdss.ftpmusic.app.data.repository.DesiredRepository,
    private val deezerService: com.lucasdss.ftpmusic.app.data.network.DeezerService,
    private val musicBrainzService: com.lucasdss.ftpmusic.app.data.network.MusicBrainzService,
    private val lastFmService: com.lucasdss.ftpmusic.app.data.network.LastFmService,
    private val discoverLibraryResolver: com.lucasdss.ftpmusic.app.ui.search.DiscoverLibraryResolver,
    private val discoverArtistBioEnricher: com.lucasdss.ftpmusic.app.ui.search.DiscoverArtistBioEnricher,
    private val searchIndexRebuilder: com.lucasdss.ftpmusic.app.data.search.SearchIndexRebuilder,
    private val offlineModeManager: com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager,
    private val storage: SecureStorage,
    private val playbackManager: PlaybackManager,
    private val authHelper: SubsonicAuthHelper,
) : ViewModel() {
    private val _state = MutableStateFlow(FavoritesState())
    val state: StateFlow<FavoritesState> = _state.asStateFlow()

    /** One-shot share payloads — UI starts ACTION_SEND chooser. */
    private val _shareDesiredEvents = kotlinx.coroutines.channels.Channel<String>(
        kotlinx.coroutines.channels.Channel.BUFFERED,
    )
    val shareDesiredEvents = _shareDesiredEvents.receiveAsFlow()

    /** How many rows currently held per entity (liked / disliked). */
    private var loadedLikedTracks = FavoritesPaging.PAGE_SIZE
    private var loadedLikedAlbums = FavoritesPaging.PAGE_SIZE
    private var loadedLikedArtists = FavoritesPaging.PAGE_SIZE
    private var loadedDislikedTracks = FavoritesPaging.PAGE_SIZE
    private var loadedDislikedAlbums = FavoritesPaging.PAGE_SIZE
    private var loadedDislikedArtists = FavoritesPaging.PAGE_SIZE

    private val windowMutex = Mutex()
    private val trackPending = FavoritePendingStore()
    private val albumPending = FavoritePendingStore()
    private val artistPending = FavoritePendingStore()
    private var desiredFilterJob: kotlinx.coroutines.Job? = null
    private var catalogJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val CATALOG_DEBOUNCE_MS = 300L
    }

    fun setMode(mode: FavoritesMode) {
        _state.value = _state.value.copy(mode = mode)
        if (mode == FavoritesMode.DESIRED) {
            refreshDesiredList()
        }
    }

    fun setFilterQuery(query: String) {
        _state.value = _state.value.copy(filterQuery = query)
        if (_state.value.mode == FavoritesMode.DESIRED) {
            refreshDesiredList()
            maybeLaunchCatalogAdd(query)
        }
    }

    /**
     * Full loaded favorites track list as CONTEXT + jump to [index].
     * PRIORITY kept (ADR 0061). Uses current mode's track list.
     */
    fun playTrack(index: Int) {
        val entities = when (_state.value.mode) {
            FavoritesMode.LIKED -> _state.value.tracks
            FavoritesMode.DISLIKED -> _state.value.dislikedTracks
            FavoritesMode.DESIRED -> emptyList()
        }
        if (index < 0 || index >= entities.size) return
        val tracks = entities.map { e ->
            Track(
                id = e.id,
                title = e.title,
                artist = e.artist,
                album = null,
                duration = e.durationSeconds,
                coverArt = e.coverArtUrl,
                suffix = e.suffix,
                contentType = e.contentType,
            )
        }
        val urls = tracks.map { buildStreamUrl(it.id) }
        playbackManager.playAlbum(tracks, urls, startIndex = index)
    }

    fun buildStreamUrl(trackId: String): String {
        val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
        val username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
        val password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
        return authHelper.buildStreamUrl(base, trackId, username, password)
    }

    fun load() {
        viewModelScope.launch {
            try {
                windowMutex.withLock {
                    refreshLikedWindow(forceMinPage = true)
                    refreshDislikedWindow(forceMinPage = true)
                    _state.value = _state.value.copy(
                        radio = radioFavoriteDao.getAll(),
                        showFavArtistsSection =
                            storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ARTISTS)?.toBooleanStrictOrNull() ?: true,
                        showFavAlbumsSection =
                            storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ALBUMS)?.toBooleanStrictOrNull() ?: true,
                        showFavRadioSection =
                            storage.get(SecureStorage.KEY_HOME_SHOW_FAV_RADIO)?.toBooleanStrictOrNull() ?: true,
                    )
                }
                refreshDesiredList()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "load failed", e)
            }
        }
    }

    /**
     * Room page-0 Flow invalidation → refresh the **already-loaded window**
     * (limit=loadedCount, offset=0) so appends survive and order stays correct.
     */
    fun observe() {
        viewModelScope.launch {
            try {
                val page = FavoritesPaging.PAGE_SIZE
                val liked = kotlinx.coroutines.flow.combine(
                    trackDao.getStarredFlow(page, 0),
                    metadataDao.getStarredAlbumsFlow(page, 0),
                    metadataDao.getStarredArtistsFlow(page, 0),
                    radioFavoriteDao.getAllFlow(),
                ) { tracks, albums, artists, radio ->
                    LikedBundle(tracks, albums, artists, radio)
                }
                val disliked = kotlinx.coroutines.flow.combine(
                    trackDao.getDislikedFlow(page, 0),
                    metadataDao.getDislikedAlbumsFlow(page, 0),
                    metadataDao.getDislikedArtistsFlow(page, 0),
                ) { tracks, albums, artists ->
                    DislikedBundle(tracks, albums, artists)
                }
                liked.combine(disliked) { _, _ ->
                    windowMutex.withLock {
                        refreshLikedWindow(forceMinPage = false)
                        refreshDislikedWindow(forceMinPage = false)
                        _state.value = _state.value.copy(radio = radioFavoriteDao.getAll())
                    }
                }.collect { }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "observe stopped", e)
            }
        }
        viewModelScope.launch {
            try {
                desiredRepository.observeAll().collect {
                    if (_state.value.mode == FavoritesMode.DESIRED) {
                        refreshDesiredList()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "desired observe stopped", e)
            }
        }
    }

    /** Append next page for the active mode (Liked or Disliked). Desired is full-list. */
    fun loadMore() {
        when (_state.value.mode) {
            FavoritesMode.LIKED -> loadMoreLiked()
            FavoritesMode.DISLIKED -> loadMoreDisliked()
            FavoritesMode.DESIRED -> Unit
        }
    }

    private fun refreshDesiredList() {
        desiredFilterJob?.cancel()
        desiredFilterJob = viewModelScope.launch {
            try {
                val q = _state.value.filterQuery.trim()
                val items = if (q.isEmpty()) {
                    desiredRepository.getAll()
                } else {
                    desiredRepository.search(q)
                }
                _state.value = _state.value.copy(
                    desiredItems = items,
                    desiredTracks = items.filter {
                        it.entityType == com.lucasdss.ftpmusic.app.data.repository.DesiredRepository.TYPE_TRACK
                    },
                    desiredArtists = items.filter {
                        it.entityType == com.lucasdss.ftpmusic.app.data.repository.DesiredRepository.TYPE_ARTIST
                    },
                    desiredAlbums = items.filter {
                        it.entityType == com.lucasdss.ftpmusic.app.data.repository.DesiredRepository.TYPE_ALBUM
                    },
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "desired refresh failed", e)
            }
        }
    }

    private fun isLocalOnly(): Boolean = try {
        LocalOnlyPolicy.isLocalOnly(offlineModeManager.isOffline.value)
    } catch (_: Exception) {
        false
    }

    private fun maybeLaunchCatalogAdd(query: String) {
        catalogJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _state.value = _state.value.copy(
                catalogArtists = emptyList(),
                catalogAlbums = emptyList(),
                catalogTracks = emptyList(),
                isCatalogLoading = false,
                catalogOfflineTip = false,
            )
            return
        }
        if (isLocalOnly()) {
            _state.value = _state.value.copy(
                catalogArtists = emptyList(),
                catalogAlbums = emptyList(),
                catalogTracks = emptyList(),
                isCatalogLoading = false,
                catalogOfflineTip = true,
            )
            return
        }
        catalogJob = viewModelScope.launch {
            kotlinx.coroutines.delay(CATALOG_DEBOUNCE_MS)
            if (_state.value.filterQuery.trim() != q) return@launch
            _state.value = _state.value.copy(isCatalogLoading = true, catalogOfflineTip = false)
            try {
                val mbArtists = try {
                    musicBrainzService.searchArtists(q, 6)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val lastFm = try {
                    lastFmService.searchArtists(q, 6)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val dzArtists = try {
                    deezerService.searchArtists(q, 6)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val dzAlbums = try {
                    deezerService.searchAlbums(q, 6)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val dzTracks = try {
                    deezerService.searchTracks(q, 6)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val artists = LinkedHashMap<String, DiscoverArtistHit>()
                for (a in mbArtists) {
                    val local = discoverLibraryResolver.resolveArtist(a.mbid, a.name)
                    artists[a.name.lowercase()] = DiscoverArtistHit(
                        name = a.name,
                        mbid = a.mbid,
                        disambiguation = a.disambiguation,
                        source = "musicbrainz",
                        inLibrary = local != null,
                        localArtistId = local?.id,
                    )
                }
                for (a in lastFm) {
                    val key = a.name.lowercase()
                    val existing = artists[key]
                    if (existing != null) {
                        if (existing.imageUrl.isNullOrBlank() && !a.imageUrl.isNullOrBlank()) {
                            artists[key] = existing.copy(imageUrl = a.imageUrl)
                        }
                        continue
                    }
                    val local = discoverLibraryResolver.resolveArtist(a.mbid, a.name)
                    artists[key] = DiscoverArtistHit(
                        name = a.name,
                        mbid = a.mbid,
                        source = "lastfm",
                        inLibrary = local != null,
                        localArtistId = local?.id,
                        imageUrl = a.imageUrl,
                    )
                }
                for (a in dzArtists) {
                    val key = a.name.lowercase()
                    val existing = artists[key]
                    if (existing != null) {
                        var merged = existing
                        if (merged.deezerId.isNullOrBlank()) {
                            merged = merged.copy(deezerId = a.id)
                        }
                        if (merged.imageUrl.isNullOrBlank() && !a.imageUrl.isNullOrBlank()) {
                            merged = merged.copy(imageUrl = a.imageUrl)
                        }
                        artists[key] = merged
                    } else {
                        val local = discoverLibraryResolver.resolveArtist(null, a.name)
                        artists[key] = DiscoverArtistHit(
                            name = a.name,
                            deezerId = a.id,
                            source = "deezer",
                            inLibrary = local != null,
                            localArtistId = local?.id,
                            imageUrl = a.imageUrl,
                        )
                    }
                }
                if (_state.value.filterQuery.trim() != q) return@launch
                val catalogArtistList = discoverArtistBioEnricher.enrich(artists.values.take(6).toList())
                if (_state.value.filterQuery.trim() != q) return@launch
                _state.value = _state.value.copy(
                    catalogArtists = catalogArtistList,
                    catalogAlbums = dzAlbums.map {
                        val local = discoverLibraryResolver.resolveAlbum(it.title, it.artistName)
                        DiscoverAlbumHit(
                            title = it.title,
                            artistName = it.artistName,
                            deezerId = it.id,
                            source = "deezer",
                            inLibrary = local != null,
                            localAlbumId = local?.id,
                            imageUrl = it.imageUrl,
                        )
                    },
                    catalogTracks = dzTracks.map {
                        val local = discoverLibraryResolver.resolveTrack(null, it.title, it.artistName)
                        DiscoverTrackHit(
                            title = it.title,
                            artistName = it.artistName,
                            albumName = it.albumName,
                            deezerId = it.id,
                            inLibrary = local != null,
                            localTrackId = local?.id,
                            imageUrl = it.imageUrl,
                        )
                    },
                    isCatalogLoading = false,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(isCatalogLoading = false)
            }
        }
    }

    fun addCatalogArtistDesired(hit: DiscoverArtistHit) {
        if (hit.inLibrary) return
        viewModelScope.launch {
            desiredRepository.addDesired(
                entityType = com.lucasdss.ftpmusic.app.data.repository.DesiredRepository.TYPE_ARTIST,
                title = hit.name,
                musicbrainzId = hit.mbid,
                deezerId = hit.deezerId,
                source = hit.source,
                imageUrl = hit.imageUrl,
                description = hit.descriptionText(),
                matchedLocalId = hit.localArtistId,
            )
            refreshDesiredList()
        }
    }

    /**
     * Navigate to artist detail from Desired catalog / saved artist row.
     * Library → local id; else soft-cache stub (parity with Search Discover).
     */
    fun onDesiredArtistTap(hit: DiscoverArtistHit, onNavigate: (String) -> Unit) {
        if (hit.inLibrary && !hit.localArtistId.isNullOrBlank()) {
            onNavigate(hit.localArtistId)
            return
        }
        viewModelScope.launch {
            val stubId = hit.localArtistId
                ?: hit.mbid?.let { "mb:$it" }
                ?: "ext:${com.lucasdss.ftpmusic.app.data.search.ArtistNameNormalizer.normalize(hit.name).hashCode()}"
            try {
                metadataDao.upsertArtistsPreserveEnrich(
                    listOf(
                        com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity(
                            id = stubId,
                            name = hit.name,
                            musicbrainzId = hit.mbid,
                            biography = hit.biography,
                        ),
                    ),
                )
                if (!hit.biography.isNullOrBlank()) {
                    try {
                        metadataDao.setArtistBiography(stubId, hit.biography)
                    } catch (_: Exception) {
                    }
                }
                searchIndexRebuilder.indexArtistStub(stubId, hit.name, hit.mbid)
                searchIndexRebuilder.scheduleRebuild(debounceMs = 3_000L)
            } catch (_: Exception) {
            }
            onNavigate(stubId)
        }
    }

    fun onDesiredItemArtistTap(item: DesiredItemEntity, onNavigate: (String) -> Unit) {
        val matched = item.matchedLocalId?.takeIf { it.isNotBlank() }
        if (matched != null) {
            onNavigate(matched)
            return
        }
        if (item.entityType !=
            com.lucasdss.ftpmusic.app.data.repository.DesiredRepository.TYPE_ARTIST
        ) {
            return
        }
        onDesiredArtistTap(
            DiscoverArtistHit(
                name = item.title,
                mbid = item.musicbrainzId,
                deezerId = item.deezerId,
                disambiguation = item.description,
                biography = item.description,
                source = item.source,
                inLibrary = false,
                imageUrl = item.imageUrl,
            ),
            onNavigate,
        )
    }

    fun addCatalogAlbumDesired(hit: DiscoverAlbumHit) {
        if (hit.inLibrary) return
        viewModelScope.launch {
            desiredRepository.addDesired(
                entityType = com.lucasdss.ftpmusic.app.data.repository.DesiredRepository.TYPE_ALBUM,
                title = hit.title,
                artistName = hit.artistName,
                musicbrainzId = hit.mbid,
                deezerId = hit.deezerId,
                source = hit.source,
                imageUrl = hit.imageUrl,
                matchedLocalId = hit.localAlbumId,
            )
            refreshDesiredList()
        }
    }

    fun addCatalogTrackDesired(hit: DiscoverTrackHit) {
        if (hit.inLibrary) return
        viewModelScope.launch {
            desiredRepository.addDesired(
                entityType = com.lucasdss.ftpmusic.app.data.repository.DesiredRepository.TYPE_TRACK,
                title = hit.title,
                artistName = hit.artistName,
                albumName = hit.albumName,
                musicbrainzId = hit.mbid,
                deezerId = hit.deezerId,
                source = when {
                    !hit.mbid.isNullOrBlank() -> "musicbrainz"
                    !hit.deezerId.isNullOrBlank() -> "deezer"
                    else -> "manual"
                },
                imageUrl = hit.imageUrl,
                matchedLocalId = hit.localTrackId,
            )
            refreshDesiredList()
        }
    }

    fun removeDesired(id: String) {
        viewModelScope.launch {
            desiredRepository.removeDesired(id)
            refreshDesiredList()
        }
    }

    fun shareDesired() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val text = desiredRepository.snapshotShareText()
                _shareDesiredEvents.send(text)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "shareDesired failed", e)
            }
        }
    }

    private fun loadMoreLiked() {
        if (!_state.value.hasMoreLiked || _state.value.isLoadingMoreLiked) return
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoadingMoreLiked = true)
                windowMutex.withLock {
                    if (!_state.value.hasMoreLiked) return@withLock
                    val page = FavoritesPaging.PAGE_SIZE
                    val moreTracks = if (_state.value.hasMoreLikedTracks) {
                        trackDao.getStarred(page, loadedLikedTracks)
                    } else {
                        emptyList()
                    }
                    val moreAlbums = if (_state.value.hasMoreLikedAlbums) {
                        metadataDao.getStarredAlbums(page, loadedLikedAlbums)
                    } else {
                        emptyList()
                    }
                    val moreArtists = if (_state.value.hasMoreLikedArtists) {
                        metadataDao.getStarredArtists(page, loadedLikedArtists)
                    } else {
                        emptyList()
                    }
                    if (moreTracks.isNotEmpty()) loadedLikedTracks += moreTracks.size
                    if (moreAlbums.isNotEmpty()) loadedLikedAlbums += moreAlbums.size
                    if (moreArtists.isNotEmpty()) loadedLikedArtists += moreArtists.size
                    val s = _state.value
                    val existingTrackIds = s.tracks.mapTo(mutableSetOf()) { it.id }
                    val existingAlbumIds = s.albums.mapTo(mutableSetOf()) { it.id }
                    val existingArtistIds = s.artists.mapTo(mutableSetOf()) { it.id }
                    val tracks =
                        applyTrackPendingLiked(
                            s.tracks + moreTracks.filter { it.id !in existingTrackIds },
                        )
                    val albums =
                        applyAlbumPendingLiked(
                            s.albums + moreAlbums.filter { it.id !in existingAlbumIds },
                        )
                    val artists =
                        applyArtistPendingLiked(
                            s.artists + moreArtists.filter { it.id !in existingArtistIds },
                        )
                    _state.value = s.copy(
                        tracks = tracks,
                        albums = albums,
                        artists = artists,
                        hasMoreLikedTracks = moreTracks.size >= page,
                        hasMoreLikedAlbums = moreAlbums.size >= page,
                        hasMoreLikedArtists = moreArtists.size >= page,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "loadMoreLiked failed", e)
            } finally {
                _state.value = _state.value.copy(isLoadingMoreLiked = false)
            }
        }
    }

    private fun loadMoreDisliked() {
        if (!_state.value.hasMoreDisliked || _state.value.isLoadingMoreDisliked) return
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoadingMoreDisliked = true)
                windowMutex.withLock {
                    if (!_state.value.hasMoreDisliked) return@withLock
                    val page = FavoritesPaging.PAGE_SIZE
                    val moreTracks = if (_state.value.hasMoreDislikedTracks) {
                        trackDao.getDisliked(page, loadedDislikedTracks)
                    } else {
                        emptyList()
                    }
                    val moreAlbums = if (_state.value.hasMoreDislikedAlbums) {
                        metadataDao.getDislikedAlbums(page, loadedDislikedAlbums)
                    } else {
                        emptyList()
                    }
                    val moreArtists = if (_state.value.hasMoreDislikedArtists) {
                        metadataDao.getDislikedArtists(page, loadedDislikedArtists)
                    } else {
                        emptyList()
                    }
                    if (moreTracks.isNotEmpty()) loadedDislikedTracks += moreTracks.size
                    if (moreAlbums.isNotEmpty()) loadedDislikedAlbums += moreAlbums.size
                    if (moreArtists.isNotEmpty()) loadedDislikedArtists += moreArtists.size
                    val s = _state.value
                    val existingTrackIds = s.dislikedTracks.mapTo(mutableSetOf()) { it.id }
                    val existingAlbumIds = s.dislikedAlbums.mapTo(mutableSetOf()) { it.id }
                    val existingArtistIds = s.dislikedArtists.mapTo(mutableSetOf()) { it.id }
                    val tracks = applyTrackPendingDisliked(
                        s.dislikedTracks + moreTracks.filter { it.id !in existingTrackIds },
                    )
                    val albums = applyAlbumPendingDisliked(
                        s.dislikedAlbums + moreAlbums.filter { it.id !in existingAlbumIds },
                    )
                    val artists = applyArtistPendingDisliked(
                        s.dislikedArtists + moreArtists.filter { it.id !in existingArtistIds },
                    )
                    _state.value = s.copy(
                        dislikedTracks = tracks,
                        dislikedAlbums = albums,
                        dislikedArtists = artists,
                        hasMoreDislikedTracks = moreTracks.size >= page,
                        hasMoreDislikedAlbums = moreAlbums.size >= page,
                        hasMoreDislikedArtists = moreArtists.size >= page,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-fav", "loadMoreDisliked failed", e)
            } finally {
                _state.value = _state.value.copy(isLoadingMoreDisliked = false)
            }
        }
    }

    private suspend fun refreshLikedWindow(forceMinPage: Boolean) {
        val page = FavoritesPaging.PAGE_SIZE
        if (forceMinPage) {
            loadedLikedTracks = maxOf(loadedLikedTracks, page)
            loadedLikedAlbums = maxOf(loadedLikedAlbums, page)
            loadedLikedArtists = maxOf(loadedLikedArtists, page)
        } else {
            // Preserve at least what UI already shows.
            loadedLikedTracks = maxOf(loadedLikedTracks, _state.value.tracks.size, page)
            loadedLikedAlbums = maxOf(loadedLikedAlbums, _state.value.albums.size, page)
            loadedLikedArtists = maxOf(loadedLikedArtists, _state.value.artists.size, page)
        }
        val tracks = trackDao.getStarred(loadedLikedTracks, 0)
        val albums = metadataDao.getStarredAlbums(loadedLikedAlbums, 0)
        val artists = metadataDao.getStarredArtists(loadedLikedArtists, 0)
        // Both sets required so Neutral pending is not cleared prematurely.
        val dislikedTrackIds = trackDao.getDislikedIds().toSet()
        val dislikedAlbumIds = metadataDao.getDislikedAlbumIds().toSet()
        val dislikedArtistIds = metadataDao.getDislikedArtistIds().toSet()
        trackPending.reconcile(tracks.map { it.id }.toSet(), dislikedTrackIds)
        albumPending.reconcile(albums.map { it.id }.toSet(), dislikedAlbumIds)
        artistPending.reconcile(artists.map { it.id }.toSet(), dislikedArtistIds)
        // Probe one more row past the loaded window.
        val probeTracks = trackDao.getStarred(1, tracks.size)
        val probeAlbums = metadataDao.getStarredAlbums(1, albums.size)
        val probeArtists = metadataDao.getStarredArtists(1, artists.size)
        loadedLikedTracks = tracks.size.coerceAtLeast(page)
        loadedLikedAlbums = albums.size.coerceAtLeast(page)
        loadedLikedArtists = artists.size.coerceAtLeast(page)
        _state.value = _state.value.copy(
            tracks = applyTrackPendingLiked(tracks),
            albums = applyAlbumPendingLiked(albums),
            artists = applyArtistPendingLiked(artists),
            hasMoreLikedTracks = probeTracks.isNotEmpty(),
            hasMoreLikedAlbums = probeAlbums.isNotEmpty(),
            hasMoreLikedArtists = probeArtists.isNotEmpty(),
        )
    }

    private suspend fun refreshDislikedWindow(forceMinPage: Boolean) {
        val page = FavoritesPaging.PAGE_SIZE
        if (forceMinPage) {
            loadedDislikedTracks = maxOf(loadedDislikedTracks, page)
            loadedDislikedAlbums = maxOf(loadedDislikedAlbums, page)
            loadedDislikedArtists = maxOf(loadedDislikedArtists, page)
        } else {
            loadedDislikedTracks = maxOf(loadedDislikedTracks, _state.value.dislikedTracks.size, page)
            loadedDislikedAlbums = maxOf(loadedDislikedAlbums, _state.value.dislikedAlbums.size, page)
            loadedDislikedArtists = maxOf(loadedDislikedArtists, _state.value.dislikedArtists.size, page)
        }
        val tracks = trackDao.getDisliked(loadedDislikedTracks, 0)
        val albums = metadataDao.getDislikedAlbums(loadedDislikedAlbums, 0)
        val artists = metadataDao.getDislikedArtists(loadedDislikedArtists, 0)
        val starredTrackIds = trackDao.getStarredIds().map { it.id }.toSet()
        val starredAlbumIds = metadataDao.getStarredAlbumIds().toSet()
        val starredArtistIds = metadataDao.getStarredArtistIds().toSet()
        trackPending.reconcile(starredTrackIds, tracks.map { it.id }.toSet())
        albumPending.reconcile(starredAlbumIds, albums.map { it.id }.toSet())
        artistPending.reconcile(starredArtistIds, artists.map { it.id }.toSet())
        val probeTracks = trackDao.getDisliked(1, tracks.size)
        val probeAlbums = metadataDao.getDislikedAlbums(1, albums.size)
        val probeArtists = metadataDao.getDislikedArtists(1, artists.size)
        loadedDislikedTracks = tracks.size.coerceAtLeast(page)
        loadedDislikedAlbums = albums.size.coerceAtLeast(page)
        loadedDislikedArtists = artists.size.coerceAtLeast(page)
        _state.value = _state.value.copy(
            dislikedTracks = applyTrackPendingDisliked(tracks),
            dislikedAlbums = applyAlbumPendingDisliked(albums),
            dislikedArtists = applyArtistPendingDisliked(artists),
            hasMoreDislikedTracks = probeTracks.isNotEmpty(),
            hasMoreDislikedAlbums = probeAlbums.isNotEmpty(),
            hasMoreDislikedArtists = probeArtists.isNotEmpty(),
        )
    }

    private fun applyTrackPendingLiked(room: List<TrackEntity>): List<TrackEntity> =
        room.filter { trackPending.get(it.id) != FavoritePendingKind.Neutral }

    private fun applyAlbumPendingLiked(room: List<AlbumEntity>): List<AlbumEntity> =
        room.filter { albumPending.get(it.id) != FavoritePendingKind.Neutral }

    private fun applyArtistPendingLiked(room: List<ArtistEntity>): List<ArtistEntity> =
        room.filter { artistPending.get(it.id) != FavoritePendingKind.Neutral }

    private fun applyTrackPendingDisliked(room: List<TrackEntity>): List<TrackEntity> =
        room.filter { trackPending.get(it.id) != FavoritePendingKind.Neutral }

    private fun applyAlbumPendingDisliked(room: List<AlbumEntity>): List<AlbumEntity> =
        room.filter { albumPending.get(it.id) != FavoritePendingKind.Neutral }

    private fun applyArtistPendingDisliked(room: List<ArtistEntity>): List<ArtistEntity> =
        room.filter { artistPending.get(it.id) != FavoritePendingKind.Neutral }

    private data class LikedBundle(
        val tracks: List<TrackEntity>,
        val albums: List<AlbumEntity>,
        val artists: List<ArtistEntity>,
        val radio: List<RadioFavoriteEntity>,
    )

    private data class DislikedBundle(
        val tracks: List<TrackEntity>,
        val albums: List<AlbumEntity>,
        val artists: List<ArtistEntity>,
    )

    init {
        observe()
    }

    fun unstarTrack(trackId: String) {
        val removed = _state.value.tracks.firstOrNull { it.id == trackId }
        trackPending.set(trackId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(tracks = _state.value.tracks.filter { it.id != trackId })
        viewModelScope.launch {
            try {
                favoriteRepository.unstarTrack(trackId)
            } catch (_: Exception) {
                trackPending.clear(trackId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        tracks = (_state.value.tracks + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }

    fun unlikeAlbum(albumId: String) {
        val removed = _state.value.albums.firstOrNull { it.id == albumId }
        albumPending.set(albumId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(albums = _state.value.albums.filter { it.id != albumId })
        viewModelScope.launch {
            try {
                favoriteRepository.unlikeAlbum(albumId)
            } catch (_: Exception) {
                albumPending.clear(albumId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        albums = (_state.value.albums + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }

    fun unlikeArtist(artistId: String) {
        val removed = _state.value.artists.firstOrNull { it.id == artistId }
        artistPending.set(artistId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(artists = _state.value.artists.filter { it.id != artistId })
        viewModelScope.launch {
            try {
                favoriteRepository.unlikeArtist(artistId)
            } catch (_: Exception) {
                artistPending.clear(artistId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        artists = (_state.value.artists + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }

    fun unbookmarkRadio(stationId: String) {
        val previous = _state.value.radio
        _state.value = _state.value.copy(radio = previous.filter { it.stationId != stationId })
        viewModelScope.launch {
            try {
                favoriteRepository.unbookmarkRadio(stationId)
            } catch (_: Exception) {
                _state.value = _state.value.copy(radio = previous)
            }
        }
    }

    fun clearDislikeTrack(trackId: String) {
        val removed = _state.value.dislikedTracks.firstOrNull { it.id == trackId }
        trackPending.set(trackId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(dislikedTracks = _state.value.dislikedTracks.filter { it.id != trackId })
        viewModelScope.launch {
            try {
                favoriteRepository.clearDislikeTrack(trackId)
            } catch (_: Exception) {
                trackPending.clear(trackId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        dislikedTracks = (_state.value.dislikedTracks + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }

    fun clearDislikeAlbum(albumId: String) {
        val removed = _state.value.dislikedAlbums.firstOrNull { it.id == albumId }
        albumPending.set(albumId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(dislikedAlbums = _state.value.dislikedAlbums.filter { it.id != albumId })
        viewModelScope.launch {
            try {
                favoriteRepository.clearDislikeAlbum(albumId)
            } catch (_: Exception) {
                albumPending.clear(albumId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        dislikedAlbums = (_state.value.dislikedAlbums + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }

    fun clearDislikeArtist(artistId: String) {
        val removed = _state.value.dislikedArtists.firstOrNull { it.id == artistId }
        artistPending.set(artistId, FavoritePendingKind.Neutral)
        _state.value = _state.value.copy(dislikedArtists = _state.value.dislikedArtists.filter { it.id != artistId })
        viewModelScope.launch {
            try {
                favoriteRepository.clearDislikeArtist(artistId)
            } catch (_: Exception) {
                artistPending.clear(artistId)
                if (removed != null) {
                    _state.value = _state.value.copy(
                        dislikedArtists = (_state.value.dislikedArtists + removed).distinctBy { it.id },
                    )
                }
            }
        }
    }
}

data class FavoritesState(
    val mode: FavoritesMode = FavoritesMode.LIKED,
    val tracks: List<TrackEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<ArtistEntity> = emptyList(),
    val radio: List<RadioFavoriteEntity> = emptyList(),
    val dislikedTracks: List<TrackEntity> = emptyList(),
    val dislikedAlbums: List<AlbumEntity> = emptyList(),
    val dislikedArtists: List<ArtistEntity> = emptyList(),
    val desiredItems: List<DesiredItemEntity> = emptyList(),
    val desiredTracks: List<DesiredItemEntity> = emptyList(),
    val desiredAlbums: List<DesiredItemEntity> = emptyList(),
    val desiredArtists: List<DesiredItemEntity> = emptyList(),
    val filterQuery: String = "",
    val catalogArtists: List<DiscoverArtistHit> = emptyList(),
    val catalogAlbums: List<DiscoverAlbumHit> = emptyList(),
    val catalogTracks: List<DiscoverTrackHit> = emptyList(),
    val isCatalogLoading: Boolean = false,
    /** Offline tip when Desired filter would hit catalog but local-only blocks it. */
    val catalogOfflineTip: Boolean = false,
    val hasMoreLikedTracks: Boolean = false,
    val hasMoreLikedAlbums: Boolean = false,
    val hasMoreLikedArtists: Boolean = false,
    val hasMoreDislikedTracks: Boolean = false,
    val hasMoreDislikedAlbums: Boolean = false,
    val hasMoreDislikedArtists: Boolean = false,
    val isLoadingMoreLiked: Boolean = false,
    val isLoadingMoreDisliked: Boolean = false,
    val showFavArtistsSection: Boolean = true,
    val showFavAlbumsSection: Boolean = true,
    val showFavRadioSection: Boolean = true,
) {
    val hasMoreLiked: Boolean
        get() = hasMoreLikedTracks || hasMoreLikedAlbums || hasMoreLikedArtists
    val hasMoreDisliked: Boolean
        get() = hasMoreDislikedTracks || hasMoreDislikedAlbums || hasMoreDislikedArtists
}

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onAlbumClick: (AlbumEntity) -> Unit = {},
    onArtistClick: (ArtistEntity) -> Unit = {},
    /** Desired catalog / saved artist rows navigate by artist id (local or stub). */
    onDesiredArtistClick: (String) -> Unit = {},
    onRadioStationClick: (RadioFavoriteEntity) -> Unit = {},
    currentTrackId: String? = null,
    currentAlbumId: String? = null,
    isPlaying: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.load()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.shareDesiredEvents.collect { text ->
            try {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "ftpmusic desired list")
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                context.startActivity(android.content.Intent.createChooser(send, "Share desired list"))
            } catch (_: android.content.ActivityNotFoundException) {
            }
        }
    }

    val dislikedHasAnything = state.dislikedTracks.isNotEmpty() ||
        (state.showFavArtistsSection && state.dislikedArtists.isNotEmpty()) ||
        (state.showFavAlbumsSection && state.dislikedAlbums.isNotEmpty())

    val likedHasAnything = state.tracks.isNotEmpty() ||
        (state.showFavArtistsSection && state.artists.isNotEmpty()) ||
        (state.showFavAlbumsSection && state.albums.isNotEmpty()) ||
        (state.showFavRadioSection && state.radio.isNotEmpty())

    val desiredHasAnything = state.desiredItems.isNotEmpty() ||
        state.catalogArtists.isNotEmpty() ||
        state.catalogAlbums.isNotEmpty() ||
        state.catalogTracks.isNotEmpty() ||
        state.isCatalogLoading ||
        state.filterQuery.isNotBlank()

    val hasAnything = when (state.mode) {
        FavoritesMode.LIKED -> likedHasAnything
        FavoritesMode.DISLIKED -> dislikedHasAnything
        FavoritesMode.DESIRED -> desiredHasAnything
    }

    // Mode chips sit above Lazy — pad Column so chrome clears overlay AppHeader.
    // ADR-0114: chips + list ride via appHeaderRidingContent (fixed over-measure +
    // graphicsLayer -offsetPx) so collapse leaves no mid/bottom Background strip.
    val headerPad = LocalAppHeaderContentPadding.current
    val headerOffset = LocalAppHeaderOffsetPx.current
    val headerPadPx = with(LocalDensity.current) { headerPad.toPx() }
    Column(
        Modifier.appHeaderRidingClipHost(headerPad),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .appHeaderRidingContent(
                    offsetPx = { headerOffset.offsetPx() },
                    maxOffsetPx = headerPadPx,
                )
                .background(Background),
        ) {
            FavoritesModeChips(
                selected = state.mode,
                onSelect = viewModel::setMode,
            )

            // Filter + share only apply to Desired (Liked/Disliked ignore filterQuery).
            if (state.mode == FavoritesMode.DESIRED) {
                FavoritesFilterRow(
                    query = state.filterQuery,
                    onQueryChange = viewModel::setFilterQuery,
                    showShare = true,
                    onShare = viewModel::shareDesired,
                )
            }

            if (!hasAnything) {
                Box(Modifier.fillMaxSize().testTag("favorites_empty"), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            when (state.mode) {
                                FavoritesMode.LIKED -> Icons.Outlined.ThumbUp
                                FavoritesMode.DISLIKED -> Icons.Outlined.ThumbDown
                                FavoritesMode.DESIRED -> Icons.Outlined.BookmarkBorder
                            },
                            null,
                            tint = Color(0xFF2A2A3E),
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            when (state.mode) {
                                FavoritesMode.LIKED -> "No favorites yet"
                                FavoritesMode.DISLIKED -> "No dislikes yet"
                                FavoritesMode.DESIRED -> "Nothing desired yet"
                            },
                            color = Color(0xFF888888),
                            fontSize = textBodyM(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when (state.mode) {
                                FavoritesMode.LIKED ->
                                    "Like any track, album, or artist, or bookmark a radio station to add it here"

                                FavoritesMode.DISLIKED ->
                                    "Thumbs-down any track, album, or artist to keep it out of Daily Mixes"

                                FavoritesMode.DESIRED ->
                                    "Search above or add from Discover to track music you want in your library"
                            },
                            color = Color(0xFF666666),
                            fontSize = textLabelM(),
                            modifier = Modifier.padding(horizontal = spacingXL()),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                when (state.mode) {
                    FavoritesMode.LIKED -> LikedFavoritesList(
                        state = state,
                        viewModel = viewModel,
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick,
                        onRadioStationClick = onRadioStationClick,
                        currentTrackId = currentTrackId,
                        currentAlbumId = currentAlbumId,
                        isPlaying = isPlaying,
                    )

                    FavoritesMode.DISLIKED -> DislikedFavoritesList(
                        state = state,
                        viewModel = viewModel,
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick,
                        currentTrackId = currentTrackId,
                        currentAlbumId = currentAlbumId,
                        isPlaying = isPlaying,
                    )

                    FavoritesMode.DESIRED -> DesiredFavoritesList(
                        state = state,
                        viewModel = viewModel,
                        onDesiredArtistClick = onDesiredArtistClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesFilterRow(
    query: String,
    onQueryChange: (String) -> Unit,
    showShare: Boolean,
    onShare: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacingL(), vertical = spacingS()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .testTag("favorites_filter_query"),
            singleLine = true,
            placeholder = { Text("Filter…", color = Color(0xFF666666)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = Color(0xFF333348),
                cursorColor = BrandTeal,
            ),
        )
        if (showShare) {
            IconButton(
                onClick = onShare,
                modifier = Modifier.testTag("favorites_share_desired"),
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share desired list", tint = Color.White)
            }
        }
    }
}

@Composable
private fun FavoritesModeChips(selected: FavoritesMode, onSelect: (FavoritesMode) -> Unit) {
    SegmentedChipRow(Modifier.testTag("favorites_mode_chips")) {
        SegmentedChip(
            label = "Liked",
            selected = selected == FavoritesMode.LIKED,
            onClick = { onSelect(FavoritesMode.LIKED) },
            modifier = Modifier.weight(1f).testTag("favorites_mode_liked"),
        )
        SegmentedChip(
            label = "Disliked",
            selected = selected == FavoritesMode.DISLIKED,
            onClick = { onSelect(FavoritesMode.DISLIKED) },
            modifier = Modifier.weight(1f).testTag("favorites_mode_disliked"),
        )
        SegmentedChip(
            label = "Desired",
            selected = selected == FavoritesMode.DESIRED,
            onClick = { onSelect(FavoritesMode.DESIRED) },
            modifier = Modifier.weight(1f).testTag("favorites_mode_desired"),
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DesiredFavoritesList(
    state: FavoritesState,
    viewModel: FavoritesViewModel,
    onDesiredArtistClick: (String) -> Unit,
) {
    val listState = rememberLazyListState(
        cacheWindow = LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.2f),
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("favorites_desired_list"),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        if (state.desiredTracks.isNotEmpty()) {
            item { FavoriteSectionHeader("Tracks", Icons.Outlined.BookmarkBorder) }
            items(state.desiredTracks, key = { it.id }) { item ->
                DesiredItemRow(item = item, onRemove = { viewModel.removeDesired(item.id) })
            }
        }
        if (state.desiredArtists.isNotEmpty()) {
            item { FavoriteSectionHeader("Artists", Icons.Outlined.BookmarkBorder) }
            items(state.desiredArtists, key = { it.id }) { item ->
                DesiredItemRow(
                    item = item,
                    onRemove = { viewModel.removeDesired(item.id) },
                    onClick = {
                        viewModel.onDesiredItemArtistTap(item) { id -> onDesiredArtistClick(id) }
                    },
                )
            }
        }
        if (state.desiredAlbums.isNotEmpty()) {
            item { FavoriteSectionHeader("Albums", Icons.Outlined.BookmarkBorder) }
            items(state.desiredAlbums, key = { it.id }) { item ->
                DesiredItemRow(item = item, onRemove = { viewModel.removeDesired(item.id) })
            }
        }
        if (state.catalogOfflineTip && state.filterQuery.trim().length >= 2) {
            item {
                Text(
                    "Offline — catalog add needs a connection",
                    color = Color(0xFF666666),
                    fontSize = textLabelM(),
                    modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
                )
            }
        }
        val showCatalog = state.filterQuery.trim().length >= 2 &&
            (
                state.isCatalogLoading || state.catalogArtists.isNotEmpty() ||
                    state.catalogAlbums.isNotEmpty() || state.catalogTracks.isNotEmpty()
                )
        if (showCatalog) {
            item { FavoriteSectionHeader("Add from catalog", Icons.Filled.Add) }
            if (state.isCatalogLoading) {
                item {
                    Text(
                        "Searching catalog…",
                        color = Color(0xFF666666),
                        fontSize = textLabelM(),
                        modifier = Modifier.padding(horizontal = spacingL(), vertical = spacingS()),
                    )
                }
            }
            items(state.catalogArtists, key = { "cat-ar-${it.mbid ?: it.deezerId ?: it.name}" }) { hit ->
                CatalogAddRow(
                    title = hit.name,
                    subtitle = hit.originSubtitle(),
                    description = hit.descriptionText(),
                    imageUrl = hit.imageUrl,
                    circle = true,
                    showAdd = !hit.inLibrary,
                    onAdd = { viewModel.addCatalogArtistDesired(hit) },
                    onClick = {
                        viewModel.onDesiredArtistTap(hit) { id -> onDesiredArtistClick(id) }
                    },
                )
            }
            items(
                state.catalogAlbums,
                key = { "cat-al-${it.deezerId ?: it.title}|${it.artistName.orEmpty()}" },
            ) { hit ->
                CatalogAddRow(
                    title = hit.title,
                    subtitle = buildString {
                        hit.artistName?.let {
                            append(it)
                            append(" · ")
                        }
                        append(if (hit.inLibrary) "In library" else "Not in library · ${hit.source}")
                    },
                    imageUrl = hit.imageUrl,
                    showAdd = !hit.inLibrary,
                    onAdd = { viewModel.addCatalogAlbumDesired(hit) },
                )
            }
            items(
                state.catalogTracks,
                key = { "cat-tr-${it.deezerId ?: it.mbid ?: it.title}|${it.artistName.orEmpty()}" },
            ) { hit ->
                CatalogAddRow(
                    title = hit.title,
                    subtitle = buildString {
                        hit.artistName?.let {
                            append(it)
                            append(" · ")
                        }
                        append(
                            if (hit.inLibrary) {
                                "In library"
                            } else {
                                "Not in library · deezer"
                            },
                        )
                    },
                    imageUrl = hit.imageUrl,
                    showAdd = !hit.inLibrary,
                    onAdd = { viewModel.addCatalogTrackDesired(hit) },
                )
            }
        }
    }
}

@Composable
private fun DesiredItemRow(item: DesiredItemEntity, onRemove: () -> Unit, onClick: (() -> Unit)? = null) {
    val isArtist =
        item.entityType == com.lucasdss.ftpmusic.app.data.repository.DesiredRepository.TYPE_ARTIST
    val originLine = buildString {
        when {
            !item.matchedLocalId.isNullOrBlank() -> {
                item.artistName?.takeIf { it.isNotBlank() }?.let {
                    append(it)
                    append(" · ")
                }
                append("In library · ")
                append(item.source)
            }

            else -> {
                item.artistName?.takeIf { it.isNotBlank() }?.let {
                    append(it)
                    append(" · ")
                }
                append("Not in library · ")
                append(item.source)
            }
        }
    }
    val desc = item.description?.takeIf { it.isNotBlank() }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = spacingL(), vertical = spacingM())
            .heightIn(min = 48.dp)
            .testTag("desired_item_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.lucasdss.ftpmusic.app.ui.search.SearchCoverThumb(
            url = item.imageUrl,
            contentDesc = item.title,
            circle = isArtist,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = Color.White,
                fontSize = textHeadingS(),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                originLine,
                color = Color(0xFF888888),
                fontSize = textLabelM(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (desc != null) {
                Text(
                    desc,
                    color = Color(0xFF666666),
                    fontSize = textLabelM(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove desired", tint = Color(0xFF888888))
        }
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.04f),
        modifier = Modifier.padding(horizontal = spacingL()),
    )
}

@Composable
private fun CatalogAddRow(
    title: String,
    subtitle: String,
    description: String? = null,
    imageUrl: String? = null,
    circle: Boolean = false,
    showAdd: Boolean = true,
    onAdd: () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = spacingL(), vertical = spacingM())
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.lucasdss.ftpmusic.app.ui.search.SearchCoverThumb(
            url = imageUrl,
            contentDesc = title,
            circle = circle,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = textHeadingS(),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = Color(0xFF888888),
                fontSize = textLabelM(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    color = Color(0xFF666666),
                    fontSize = textLabelM(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (showAdd) {
            TextButton(onClick = onAdd) {
                Text("Add", color = BrandTeal, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.04f),
        modifier = Modifier.padding(horizontal = spacingL()),
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LikedFavoritesList(
    state: FavoritesState,
    viewModel: FavoritesViewModel,
    onAlbumClick: (AlbumEntity) -> Unit,
    onArtistClick: (ArtistEntity) -> Unit,
    onRadioStationClick: (RadioFavoriteEntity) -> Unit,
    currentTrackId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
) {
    val listState = rememberLazyListState(
        cacheWindow = LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.2f),
    )
    // Re-key on loaded sizes so near-end stays true after append still triggers another loadMore.
    LaunchedEffect(
        listState,
        state.hasMoreLiked,
        state.tracks.size,
        state.albums.size,
        state.artists.size,
        state.isLoadingMoreLiked,
    ) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && last >= total - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && state.hasMoreLiked && !state.isLoadingMoreLiked) {
                viewModel.loadMore()
            }
        }
    }
    LazyColumn(
        Modifier.testTag("favorites_liked_list"),
        state = listState,
    ) {
        if (state.tracks.isNotEmpty()) {
            item { FavoriteSectionHeader("Tracks", Icons.Filled.ThumbUp) }
            itemsIndexed(
                state.tracks,
                key = { _, t -> "liked_t_${t.id}" },
                contentType = { _, _ -> "song" },
            ) { index, track ->
                TrackFavoriteRow(
                    track = track,
                    isActive = currentTrackId != null && track.id == currentTrackId,
                    actionIcon = Icons.Filled.ThumbUp,
                    actionTint = BrandTeal,
                    actionCd = "Unlike",
                    onRowClick = { viewModel.playTrack(index) },
                    onAction = { viewModel.unstarTrack(track.id) },
                )
            }
        }
        if (state.showFavArtistsSection && state.artists.isNotEmpty()) {
            item { FavoriteSectionHeader("Artists", Icons.Filled.ThumbUp) }
            items(state.artists, key = { "liked_ar_${it.id}" }, contentType = { "artist" }) { artist ->
                ArtistFavoriteRow(
                    artist = artist,
                    actionIcon = Icons.Filled.ThumbUp,
                    actionTint = BrandTeal,
                    actionCd = "Unlike artist",
                    onRowClick = { onArtistClick(artist) },
                    onAction = { viewModel.unlikeArtist(artist.id) },
                )
            }
        }
        if (state.showFavAlbumsSection && state.albums.isNotEmpty()) {
            item { FavoriteSectionHeader("Albums", Icons.Filled.ThumbUp) }
            items(state.albums, key = { "liked_al_${it.id}" }, contentType = { "album" }) { album ->
                AlbumFavoriteRow(
                    album = album,
                    isActive = currentAlbumId != null && album.id == currentAlbumId && isPlaying,
                    actionIcon = Icons.Filled.ThumbUp,
                    actionTint = BrandTeal,
                    actionCd = "Unlike album",
                    onRowClick = { onAlbumClick(album) },
                    onAction = { viewModel.unlikeAlbum(album.id) },
                )
            }
        }
        if (state.showFavRadioSection && state.radio.isNotEmpty()) {
            item { FavoriteSectionHeader("Radio", Icons.Filled.Bookmark) }
            items(state.radio, key = { "liked_r_${it.stationId}" }, contentType = { "radio" }) { station ->
                RadioFavoriteRow(
                    station = station,
                    onRowClick = { onRadioStationClick(station) },
                    onUnbookmark = { viewModel.unbookmarkRadio(station.stationId) },
                )
            }
        }
        if (state.hasMoreLiked) {
            item {
                TextButton(
                    onClick = { viewModel.loadMore() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("favorites_load_more_liked"),
                ) {
                    Text("Load more", color = BrandTeal)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DislikedFavoritesList(
    state: FavoritesState,
    viewModel: FavoritesViewModel,
    onAlbumClick: (AlbumEntity) -> Unit,
    onArtistClick: (ArtistEntity) -> Unit,
    currentTrackId: String?,
    currentAlbumId: String?,
    isPlaying: Boolean,
) {
    val dislikeTint = Color(0xFFE84040)
    val listState = rememberLazyListState(
        cacheWindow = LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.2f),
    )
    LaunchedEffect(
        listState,
        state.hasMoreDisliked,
        state.dislikedTracks.size,
        state.dislikedAlbums.size,
        state.dislikedArtists.size,
        state.isLoadingMoreDisliked,
    ) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && last >= total - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && state.hasMoreDisliked && !state.isLoadingMoreDisliked) {
                viewModel.loadMore()
            }
        }
    }
    LazyColumn(
        Modifier.testTag("favorites_disliked_list"),
        state = listState,
    ) {
        if (state.dislikedTracks.isNotEmpty()) {
            item { FavoriteSectionHeader("Tracks", Icons.Filled.ThumbDown, tint = dislikeTint) }
            itemsIndexed(
                state.dislikedTracks,
                key = { _, t -> "dis_t_${t.id}" },
                contentType = { _, _ -> "song" },
            ) { index, track ->
                TrackFavoriteRow(
                    track = track,
                    isActive = currentTrackId != null && track.id == currentTrackId,
                    actionIcon = Icons.Filled.ThumbDown,
                    actionTint = dislikeTint,
                    actionCd = "Remove dislike",
                    onRowClick = { viewModel.playTrack(index) },
                    onAction = { viewModel.clearDislikeTrack(track.id) },
                )
            }
        }
        if (state.showFavArtistsSection && state.dislikedArtists.isNotEmpty()) {
            item { FavoriteSectionHeader("Artists", Icons.Filled.ThumbDown, tint = dislikeTint) }
            items(state.dislikedArtists, key = { "dis_ar_${it.id}" }, contentType = { "artist" }) { artist ->
                ArtistFavoriteRow(
                    artist = artist,
                    actionIcon = Icons.Filled.ThumbDown,
                    actionTint = dislikeTint,
                    actionCd = "Remove artist dislike",
                    onRowClick = { onArtistClick(artist) },
                    onAction = { viewModel.clearDislikeArtist(artist.id) },
                )
            }
        }
        if (state.showFavAlbumsSection && state.dislikedAlbums.isNotEmpty()) {
            item { FavoriteSectionHeader("Albums", Icons.Filled.ThumbDown, tint = dislikeTint) }
            items(state.dislikedAlbums, key = { "dis_al_${it.id}" }, contentType = { "album" }) { album ->
                AlbumFavoriteRow(
                    album = album,
                    isActive = currentAlbumId != null && album.id == currentAlbumId && isPlaying,
                    actionIcon = Icons.Filled.ThumbDown,
                    actionTint = dislikeTint,
                    actionCd = "Remove album dislike",
                    onRowClick = { onAlbumClick(album) },
                    onAction = { viewModel.clearDislikeAlbum(album.id) },
                )
            }
        }
        if (state.hasMoreDisliked) {
            item {
                TextButton(
                    onClick = { viewModel.loadMore() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("favorites_load_more_disliked"),
                ) {
                    Text("Load more", color = BrandTeal)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TrackFavoriteRow(
    track: TrackEntity,
    isActive: Boolean,
    actionIcon: ImageVector,
    actionTint: Color,
    actionCd: String,
    onRowClick: () -> Unit,
    onAction: () -> Unit,
) {
    val trackCoverUrl = rememberCoverArtUrl(track.coverArtUrl, 48)
    val ds = downloadStatus(track.isDownloaded, false, track.cachedFilePath != null)
    // Meta: cache; duration on title line (ADR-0104). Mode-specific trailing thumb
    // gated by list chrome prefs — Now Playing keeps reactions always.
    val showListReactions = LocalListChromePrefs.current.showListReactions
    SongListRow(
        title = track.title,
        subtitle = track.artist,
        isActive = isActive,
        downloadStatus = ds,
        durationLabel = track.durationSeconds?.let { formatSongDuration(it) },
        onClick = onRowClick,
        modifier = Modifier
            .padding(horizontal = spacingL(), vertical = 10.dp)
            .testTag("fav_track_${track.id}"),
        leadingContent = {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center,
            ) {
                if (trackCoverUrl != null) {
                    CoverArtImage(
                        url = trackCoverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        decodeSize = 48.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        null,
                        tint = NavUnselected,
                        modifier = Modifier.size(iconSmall()),
                    )
                }
            }
        },
        trailingContent = if (showListReactions) {
            {
                ReactionGlyphButton(
                    icon = actionIcon,
                    contentDescription = actionCd,
                    tint = actionTint,
                    onClick = onAction,
                )
            }
        } else {
            null
        },
    )
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.padding(horizontal = spacingL()),
    )
}

@Composable
private fun ArtistFavoriteRow(
    artist: ArtistEntity,
    actionIcon: ImageVector,
    actionTint: Color,
    actionCd: String,
    onRowClick: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onRowClick)
            .padding(horizontal = spacingL(), vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistAvatar(artistName = artist.name, coverArtId = artist.coverArtUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = Color.White,
                fontSize = textBodyM(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Artist", color = Color(0xFF888888), fontSize = textLabelM())
        }
        ReactionGlyphButton(
            icon = actionIcon,
            contentDescription = actionCd,
            tint = actionTint,
            onClick = onAction,
        )
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.padding(horizontal = spacingL()),
    )
}

@Composable
private fun AlbumFavoriteRow(
    album: AlbumEntity,
    isActive: Boolean,
    actionIcon: ImageVector,
    actionTint: Color,
    actionCd: String,
    onRowClick: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onRowClick)
            .padding(horizontal = spacingL(), vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            val url = rememberCoverArtUrl(album.coverArtUrl, 48)
            if (url != null) {
                CoverArtImage(
                    url = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    decodeSize = 48.dp,
                )
            } else {
                Icon(
                    Icons.Default.Album,
                    null,
                    tint = NavUnselected,
                    modifier = Modifier.size(iconSmall()),
                )
            }
            if (isActive) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        listOf(0.4f, 0.7f, 1.0f).forEachIndexed { i, h ->
                            Box(
                                Modifier.width(2.dp).height((16 * h).dp)
                                    .clip(RoundedCornerShape(1.dp)).background(BrandTeal),
                            )
                            if (i < 2) Spacer(Modifier.width(2.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = album.name,
                color = if (isActive) BrandTeal else Color.White,
                fontSize = textBodyM(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
                color = Color(0xFF888888),
                fontSize = textLabelM(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ReactionGlyphButton(
            icon = actionIcon,
            contentDescription = actionCd,
            tint = actionTint,
            onClick = onAction,
        )
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.padding(horizontal = spacingL()),
    )
}

@Composable
private fun RadioFavoriteRow(station: RadioFavoriteEntity, onRowClick: () -> Unit, onUnbookmark: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onRowClick)
            .padding(horizontal = spacingL(), vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.SettingsInputAntenna,
                null,
                tint = BrandTeal,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = station.name,
                color = Color.White,
                fontSize = textBodyM(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SettingsInputAntenna,
                    null,
                    tint = BrandTeal,
                    modifier = Modifier.size(10.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = station.streamUrl,
                    color = Color(0xFF888888),
                    fontSize = textLabelM(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        RadioBookmarkIcon(
            bookmarked = true,
            onClick = onUnbookmark,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(BrandTeal.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                null,
                tint = BrandTeal,
                modifier = Modifier.size(14.dp),
            )
        }
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.padding(horizontal = spacingL()),
    )
}

/** Teal uppercase section header with a favorite icon — design's SectionHead. */
@Composable
private fun FavoriteSectionHeader(label: String, icon: ImageVector, tint: Color = BrandTeal) {
    Row(
        Modifier.padding(start = spacingL(), end = spacingL(), top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label.uppercase(),
            color = tint,
            fontSize = textLabelM(),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}
