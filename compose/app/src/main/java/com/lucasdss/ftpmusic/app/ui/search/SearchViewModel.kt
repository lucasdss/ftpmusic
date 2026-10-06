package com.lucasdss.ftpmusic.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.cache.LocalOnlyPolicy
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.GenreEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.data.model.Artist
import com.lucasdss.ftpmusic.app.data.model.Playlist
import com.lucasdss.ftpmusic.app.data.model.SearchResults
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.SearchRepository
import com.lucasdss.ftpmusic.app.data.search.SearchQueryNormalizer
import com.lucasdss.ftpmusic.app.data.search.SearchResultMerger
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val matchedGenres: List<GenreEntity> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val genres: List<GenreEntity> = emptyList(),
    val resultCount: Int = 0,
    val filterDownloaded: Boolean = false,
    val filterType: SearchFilterType = SearchFilterType.ALL,
    // Full unfiltered tracks list (used when filterDownloaded is active)
    val allTracks: List<Track> = emptyList(),
    // Track IDs that are downloaded or cached locally
    val localTrackIds: Set<String> = emptySet(),
)

enum class SearchFilterType { ALL, ARTISTS, ALBUMS, SONGS, PLAYLISTS, GENRES }

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val storage: SecureStorage,
    private val genreDao: GenreDao,
    private val trackDao: TrackDao,
    private val metadataDao: CachedMetadataDao,
    private val playlistDao: PlaylistDao,
    private val api: SubsonicApi,
    private val offlineModeManager: OfflineModeManager,
) : ViewModel() {

    companion object {
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val MAX_RECENT = 10
        private const val TYPEAHEAD_DEBOUNCE_MS = 300L
        private const val PAGE_SIZE = 50
        private const val MIN_QUERY_LEN = 2
    }

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var typeaheadJob: Job? = null
    private var albumSearchOffset = 0
    private var artistSearchOffset = 0
    private var songSearchOffset = 0
    private var isLoadingMoreSearch = false

    private fun isOffline(): Boolean = try {
        offlineModeManager.isOffline?.value == true
    } catch (_: Exception) {
        false
    }

    /** Simulate Offline or no OS INTERNET — playable Room only. */
    fun isLocalOnly(): Boolean = LocalOnlyPolicy.isLocalOnly(isOffline())

    init {
        // Load recent searches from storage
        val raw = storage.get(KEY_RECENT_SEARCHES) ?: ""
        val recent = raw.split("|||").filter { it.isNotBlank() }
        _state.value = _state.value.copy(recentSearches = recent)
        loadGenres()
        observeLocalOnly()
    }

    /** Airplane / Simulate Offline flip mid-session → re-run active search (ADR 0051). */
    private fun observeLocalOnly() {
        viewModelScope.launch {
            try {
                val offlineFlow = offlineModeManager.isOffline ?: return@launch
                var previous: Boolean? = null
                combine(offlineFlow, NetworkAvailabilityHolder.hasOsNetwork) { offline, hasNet ->
                    LocalOnlyPolicy.isLocalOnly(offline, hasNet)
                }.distinctUntilChanged().collect { localOnly ->
                    val was = previous
                    previous = localOnly
                    if (was == null || was == localOnly) return@collect
                    val q = _state.value.query.trim()
                    if (_state.value.hasSearched && q.length >= MIN_QUERY_LEN) {
                        search()
                    }
                }
            } catch (_: Exception) {
                // Relaxed mocks / missing offline flow in unit tests
            }
        }
    }

    fun loadGenres() {
        viewModelScope.launch {
            try {
                // Try cached genres from local DB
                var allGenres = genreDao.getAllByPopularity()
                if (allGenres.isEmpty() && !isLocalOnly()) {
                    // Fetch from API (skip when local-only — cached data may be empty)
                    val authHelper = SubsonicAuthHelper()
                    val username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
                    val password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
                    if (username.isNotEmpty()) {
                        val auth = authHelper.buildAuthParams(username, password)
                        val response = api.getGenres(auth)
                        val sr = response["subsonic-response"] as? Map<*, *>
                        val genresData = sr?.get("genres") as? Map<*, *>
                        val genreList = genresData?.get("genre") as? List<*>
                        val entities = genreList?.mapNotNull { g ->
                            val m = g as? Map<*, *> ?: return@mapNotNull null
                            val name = m["value"] as? String ?: (m["name"] as? String) ?: return@mapNotNull null
                            GenreEntity(
                                name = name,
                                songCount = (m["songCount"] as? Number)?.toInt() ?: 0,
                                albumCount = (m["albumCount"] as? Number)?.toInt() ?: 0,
                            )
                        } ?: emptyList()
                        genreDao.upsertAll(entities)
                        allGenres = entities
                    }
                }
                _state.value = _state.value.copy(genres = allGenres.take(8))
            } catch (_: Exception) {}
        }
    }

    private fun saveRecentSearch(query: String) {
        val current = _state.value.recentSearches.toMutableList()
        current.remove(query) // remove duplicate if exists
        current.add(0, query) // add to front
        if (current.size > MAX_RECENT) current.removeAt(current.lastIndex)
        _state.value = _state.value.copy(recentSearches = current)
        storage.put(KEY_RECENT_SEARCHES, current.joinToString("|||"))
    }

    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        typeaheadJob?.cancel()
        _state.value = _state.value.copy(
            query = query,
            artists = emptyList(),
            albums = emptyList(),
            tracks = emptyList(),
            playlists = emptyList(),
            matchedGenres = emptyList(),
            allTracks = emptyList(),
            localTrackIds = emptySet(),
            filterType = SearchFilterType.ALL,
            hasSearched = false,
            resultCount = 0,
        )
        val trimmed = query.trim()
        if (trimmed.length >= MIN_QUERY_LEN) {
            typeaheadJob = viewModelScope.launch {
                delay(TYPEAHEAD_DEBOUNCE_MS)
                if (_state.value.query.trim() == trimmed) {
                    search()
                }
            }
        }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.length < MIN_QUERY_LEN) return
        typeaheadJob?.cancel()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val filterEnabled = _state.value.filterDownloaded
            val localOnly = isLocalOnly()
            val likeQuery = SearchQueryNormalizer.escapeLike(query)
            try {
                // Local-only: playable Room queries only (1A) — skip full catalog + API.
                if (localOnly) {
                    val playableTracks = trackDao.searchPlayableTracks(likeQuery).map { it.toTrack() }
                    val playableAlbums = metadataDao.searchPlayableAlbums(likeQuery).map { it.toAlbum() }
                    val playableArtists = metadataDao.searchPlayableArtists(likeQuery).map { it.toArtist() }
                    val localPlaylists = playlistDao.searchPlaylists(likeQuery).map { it.toPlaylist() }
                    val matchedGenres = genreDao.searchGenres(likeQuery)
                    val rankedArtists = SearchResultMerger.rankByQuery(playableArtists, query) { it.name }
                    val rankedAlbums = SearchResultMerger.rankByQuery(playableAlbums, query) { it.name }
                    val rankedTracks = SearchResultMerger.rankByQuery(playableTracks, query) { it.title }
                    val localIds = playableTracks.map { it.id }.toSet()
                    _state.value = _state.value.copy(
                        tracks = rankedTracks,
                        albums = rankedAlbums,
                        artists = rankedArtists,
                        playlists = localPlaylists,
                        matchedGenres = matchedGenres,
                        allTracks = rankedTracks,
                        localTrackIds = localIds,
                        filterDownloaded = true,
                        resultCount = rankedTracks.size + rankedAlbums.size + rankedArtists.size +
                            localPlaylists.size + matchedGenres.size,
                        hasSearched = true,
                        isLoading = false,
                    )
                    return@launch
                }

                // 1. Show cached results from local DB immediately
                val cachedTracks = trackDao.searchAllTracks(likeQuery).map { it.toTrack() }
                val cachedAlbums = metadataDao.searchAlbums(likeQuery).map { it.toAlbum() }
                val cachedArtists = metadataDao.searchArtists(likeQuery).map { it.toArtist() }
                val cachedPlaylists = playlistDao.searchPlaylists(likeQuery).map { it.toPlaylist() }
                val matchedGenres = genreDao.searchGenres(likeQuery)
                _state.value = _state.value.copy(
                    tracks = cachedTracks,
                    albums = cachedAlbums,
                    artists = cachedArtists,
                    playlists = cachedPlaylists,
                    matchedGenres = matchedGenres,
                    allTracks = cachedTracks,
                    resultCount = cachedTracks.size + cachedAlbums.size + cachedArtists.size +
                        cachedPlaylists.size + matchedGenres.size,
                    hasSearched = true,
                )
                // Compute local download/cache status from Phase 1 results
                if (cachedTracks.isNotEmpty()) {
                    val localEntities = trackDao.getTracksByIds(cachedTracks.map { it.id })
                    val localIds = localEntities
                        .filter { it.isDownloaded || it.cachedFilePath != null }
                        .map { it.id }.toSet()
                    val filteredTracks = if (filterEnabled) {
                        cachedTracks.filter { it.id in localIds }
                    } else {
                        cachedTracks
                    }
                    _state.value = _state.value.copy(
                        tracks = filteredTracks,
                        localTrackIds = localIds,
                    )
                }

                // 2. Fetch fresh results from server
                val username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
                val password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
                if (username.isEmpty()) {
                    _state.value = _state.value.copy(isLoading = false)
                    return@launch
                }
                albumSearchOffset = 0
                artistSearchOffset = 0
                songSearchOffset = 0
                val results = repository.search(
                    query,
                    username,
                    password,
                    artistCount = PAGE_SIZE,
                    albumCount = PAGE_SIZE,
                    songCount = PAGE_SIZE,
                )
                val mergedArtists = SearchResultMerger.rankByQuery(
                    SearchResultMerger.unionById(results.artists, cachedArtists) { it.id },
                    query,
                ) { it.name }
                val mergedAlbums = SearchResultMerger.rankByQuery(
                    SearchResultMerger.unionById(results.albums, cachedAlbums) { it.id },
                    query,
                ) { it.name }
                val mergedTracksRaw = SearchResultMerger.unionById(results.tracks, cachedTracks) { it.id }
                val mergedTracks = SearchResultMerger.rankByQuery(mergedTracksRaw, query) { it.title }
                val mergedPlaylists = SearchResultMerger.unionById(
                    results.playlists,
                    cachedPlaylists,
                ) { it.id }

                val localEntities = if (mergedTracks.isNotEmpty()) {
                    trackDao.getTracksByIds(mergedTracks.map { it.id })
                } else {
                    emptyList()
                }
                val localIds = localEntities
                    .filter { it.isDownloaded || it.cachedFilePath != null }
                    .map { it.id }
                    .toSet()
                val filteredTracks = if (filterEnabled) {
                    mergedTracks.filter { it.id in localIds }
                } else {
                    mergedTracks
                }
                val resultCount = mergedArtists.size + mergedAlbums.size + mergedTracks.size +
                    mergedPlaylists.size + matchedGenres.size
                _state.value = _state.value.copy(
                    artists = mergedArtists,
                    albums = mergedAlbums,
                    tracks = filteredTracks,
                    playlists = mergedPlaylists,
                    matchedGenres = matchedGenres,
                    allTracks = mergedTracks,
                    localTrackIds = localIds,
                    isLoading = false,
                    hasSearched = true,
                    resultCount = resultCount,
                )
                saveRecentSearch(query)
                // Cache server results to local DB for offline reuse
                cacheServerResults(results)
            } catch (e: Exception) {
                // Network failed — keep cached results visible with download filter applied
                android.util.Log.w("ftpmusic-search", "Server search failed: ${e.message}")
                val current = _state.value
                // Re-apply download filter on cached tracks if enabled
                val filtered = if (current.filterDownloaded && current.allTracks.isNotEmpty()) {
                    current.allTracks.filter { it.id in current.localTrackIds }
                } else {
                    current.tracks
                }
                _state.value = current.copy(tracks = filtered, isLoading = false)
            }
        }
    }

    fun clearAllRecent() {
        _state.value = _state.value.copy(recentSearches = emptyList())
        storage.put(KEY_RECENT_SEARCHES, "")
    }

    fun clearRecent(query: String) {
        val updated = _state.value.recentSearches - query
        _state.value = _state.value.copy(recentSearches = updated)
        storage.put(KEY_RECENT_SEARCHES, updated.joinToString("|||"))
    }

    fun setFilterDownloaded(enabled: Boolean) {
        // Local-only forces playable filter — refuse clearing.
        if (isLocalOnly() && !enabled) return
        _state.value = _state.value.copy(filterDownloaded = enabled)
        if (enabled) {
            applyDownloadFilter()
        } else {
            // Restore full list
            val all = _state.value.allTracks
            if (all.isNotEmpty()) {
                _state.value = _state.value.copy(tracks = all)
            }
        }
    }

    fun setFilterType(type: SearchFilterType) {
        _state.value = _state.value.copy(filterType = type)
    }

    private fun applyDownloadFilter() {
        val allTracks = _state.value.allTracks
        if (allTracks.isEmpty()) return
        val localIds = _state.value.localTrackIds
        val filtered = allTracks.filter { it.id in localIds }
        _state.value = _state.value.copy(tracks = filtered)
    }

    fun onRecentTap(query: String) {
        typeaheadJob?.cancel()
        _state.value = _state.value.copy(query = query)
        search()
    }

    fun loadMoreSearchResults() {
        viewModelScope.launch {
            if (isLoadingMoreSearch) return@launch
            isLoadingMoreSearch = true
            val query = _state.value.query.trim()
            if (query.length < MIN_QUERY_LEN) {
                isLoadingMoreSearch = false
                return@launch
            }
            // Pagination requires the network — skip when local-only
            if (isLocalOnly()) {
                isLoadingMoreSearch = false
                return@launch
            }
            albumSearchOffset += PAGE_SIZE
            artistSearchOffset += PAGE_SIZE
            songSearchOffset += PAGE_SIZE
            try {
                val username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
                val password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
                if (username.isEmpty()) {
                    isLoadingMoreSearch = false
                    return@launch
                }
                val results = repository.search(
                    query,
                    username,
                    password,
                    artistCount = PAGE_SIZE,
                    albumCount = PAGE_SIZE,
                    songCount = PAGE_SIZE,
                    artistOffset = artistSearchOffset,
                    albumOffset = albumSearchOffset,
                    songOffset = songSearchOffset,
                )
                if (_state.value.query.trim() == query) {
                    val artists = SearchResultMerger.unionById(
                        _state.value.artists,
                        results.artists,
                    ) { it.id }
                    val albums = SearchResultMerger.unionById(
                        _state.value.albums,
                        results.albums,
                    ) { it.id }
                    val tracks = SearchResultMerger.unionById(
                        _state.value.allTracks,
                        results.tracks,
                    ) { it.id }
                    val filterEnabled = _state.value.filterDownloaded
                    val filtered = if (filterEnabled) {
                        tracks.filter { it.id in _state.value.localTrackIds }
                    } else {
                        tracks
                    }
                    _state.value = _state.value.copy(
                        artists = artists,
                        albums = albums,
                        tracks = filtered,
                        allTracks = tracks,
                        resultCount = artists.size + albums.size + tracks.size +
                            _state.value.playlists.size + _state.value.matchedGenres.size,
                    )
                    cacheServerResults(results)
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-search", "loadMoreSearch: ${e.message}")
            } finally {
                isLoadingMoreSearch = false
            }
        }
    }

    private suspend fun cacheServerResults(results: SearchResults) {
        try {
            // Persist tracks so offline search finds them via trackDao.searchAllTracks
            if (results.tracks.isNotEmpty()) {
                val entities = results.tracks.map { t ->
                    TrackEntity(
                        id = t.id,
                        title = t.title,
                        artist = t.artist,
                        album = t.album,
                        albumId = t.albumId,
                        artistId = t.artistId,
                        durationSeconds = t.duration,
                        trackNumber = t.trackNumber,
                        coverArtUrl = t.coverArt,
                        suffix = t.suffix,
                        contentType = t.contentType,
                        path = t.path,
                    )
                }
                trackDao.upsertAll(entities)
            }
            // Persist albums so offline search finds them via metadataDao.searchAlbums
            if (results.albums.isNotEmpty()) {
                val albumEntities = results.albums.map { a ->
                    CachedAlbumEntity(
                        id = a.id,
                        name = a.name,
                        artist = a.artist,
                        artistId = a.artistId,
                        year = a.year,
                        coverArt = a.coverArt,
                        genre = a.genre,
                    )
                }
                metadataDao.upsertAlbums(albumEntities)
            }
            // Persist artists so offline search finds them via metadataDao.searchArtists
            if (results.artists.isNotEmpty()) {
                val artistEntities = results.artists.map { a ->
                    CachedArtistEntity(
                        id = a.id,
                        name = a.name,
                        coverArt = a.coverArt,
                    )
                }
                metadataDao.upsertArtists(artistEntities)
            }
        } catch (_: Exception) {
            android.util.Log.w("ftpmusic-search", "cacheServerResults failed")
        }
    }

    private fun TrackEntity.toTrack() = Track(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        coverArt = coverArtUrl,
        duration = durationSeconds,
        path = path,
    )

    private fun CachedAlbumEntity.toAlbum() = Album(
        id = id,
        name = name,
        artist = artist,
        artistId = artistId,
        year = year,
        coverArt = coverArt,
        genre = genre,
    )

    private fun CachedArtistEntity.toArtist() = Artist(id = id, name = name, coverArt = coverArt)

    private fun com.lucasdss.ftpmusic.app.data.db.PlaylistEntity.toPlaylist() = Playlist(
        id = id,
        name = name,
        comment = comment,
        songCount = trackCount,
        coverArt = coverArt,
    )
}
