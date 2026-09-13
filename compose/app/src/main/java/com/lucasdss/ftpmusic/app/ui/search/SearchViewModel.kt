package com.lucasdss.ftpmusic.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.GenreEntity
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
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
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

enum class SearchFilterType { ALL, ARTISTS, ALBUMS, SONGS, PLAYLISTS }

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val storage: SecureStorage,
    private val genreDao: GenreDao,
    private val trackDao: TrackDao,
    private val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao,
    private val api: SubsonicApi,
    private val offlineModeManager: com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager,
) : ViewModel() {

    companion object {
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val MAX_RECENT = 10
    }

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var albumSearchOffset = 0
    private var isLoadingMoreSearch = false

    private fun isOffline(): Boolean = try {
        offlineModeManager.isOffline?.value == true
    } catch (_: Exception) {
        false
    }

    init {
        // Load recent searches from storage
        val raw = storage.get(KEY_RECENT_SEARCHES) ?: ""
        val recent = raw.split("|||").filter { it.isNotBlank() }
        _state.value = _state.value.copy(recentSearches = recent)
        loadGenres()
    }

    fun loadGenres() {
        viewModelScope.launch {
            try {
                // Try cached genres from local DB
                var allGenres = genreDao.getAllByPopularity()
                if (allGenres.isEmpty() && !isOffline()) {
                    // Fetch from API (skip when offline — cached data may be empty)
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
        _state.value = _state.value.copy(
            query = query,
            artists = emptyList(),
            albums = emptyList(),
            tracks = emptyList(),
            playlists = emptyList(),
            allTracks = emptyList(),
            localTrackIds = emptySet(),
            filterType = SearchFilterType.ALL,
        )
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.length < 2) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val filterEnabled = _state.value.filterDownloaded
            try {
                // 1. Show cached results from local DB immediately
                val cachedTrackEntities = trackDao.searchAllTracks(query)
                val cachedTracks = cachedTrackEntities.map { t ->
                    Track(
                        id = t.id,
                        title = t.title,
                        artist = t.artist,
                        artistId = t.artistId,
                        albumId = t.albumId,
                        coverArt = t.coverArtUrl,
                        duration = t.durationSeconds,
                    )
                }
                val cachedAlbums = metadataDao.searchAlbums(query).map {
                    Album(
                        id = it.id,
                        name = it.name,
                        artist = it.artist,
                        artistId = it.artistId,
                        coverArt = it.coverArt,
                    )
                }
                val cachedArtists = metadataDao.searchArtists(query).map {
                    Artist(id = it.id, name = it.name, coverArt = it.coverArt)
                }
                _state.value = _state.value.copy(
                    tracks = cachedTracks,
                    albums = cachedAlbums,
                    artists = cachedArtists,
                    allTracks = cachedTracks,
                    resultCount = cachedTracks.size + cachedAlbums.size + cachedArtists.size,
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

                // 2. Fetch fresh results from server (skip when offline)
                if (isOffline()) {
                    _state.value = _state.value.copy(isLoading = false)
                    return@launch
                }
                val username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
                val password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
                if (username.isEmpty()) {
                    _state.value = _state.value.copy(isLoading = false)
                    return@launch
                }
                albumSearchOffset = 0
                val results = repository.search(query, username, password)
                val resultCount = results.totalCount
                val allTracks = results.tracks
                val localEntities = if (results.tracks.isNotEmpty()) {
                    trackDao.getTracksByIds(results.tracks.map { it.id })
                } else {
                    emptyList()
                }
                val localIds = localEntities
                    .filter { it.isDownloaded || it.cachedFilePath != null }
                    .map { it.id }
                    .toSet()
                val filteredTracks = if (filterEnabled) {
                    allTracks.filter { it.id in localIds }
                } else {
                    allTracks
                }
                _state.value = _state.value.copy(
                    artists = results.artists.ifEmpty { cachedArtists },
                    albums = results.albums.ifEmpty { cachedAlbums },
                    tracks = filteredTracks.ifEmpty { cachedTracks },
                    playlists = results.playlists,
                    allTracks = allTracks.ifEmpty { cachedTracks },
                    localTrackIds = localIds,
                    isLoading = false,
                    hasSearched = true,
                    resultCount = maxOf(resultCount, cachedTracks.size + cachedAlbums.size + cachedArtists.size),
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
        _state.value = _state.value.copy(query = query)
        search()
    }

    fun loadMoreSearchResults() {
        viewModelScope.launch {
            if (isLoadingMoreSearch) return@launch
            isLoadingMoreSearch = true
            val query = _state.value.query
            if (query.length < 2) {
                isLoadingMoreSearch = false
                return@launch
            }
            // Pagination requires the network — skip in offline mode
            if (isOffline()) {
                isLoadingMoreSearch = false
                return@launch
            }
            albumSearchOffset += 20
            try {
                val username = storage.get(SecureStorage.KEY_USERNAME) ?: ""
                val password = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
                if (username.isEmpty()) {
                    isLoadingMoreSearch = false
                    return@launch
                }
                val results = repository.search(query, username, password, albumOffset = albumSearchOffset)
                if (_state.value.query == query) {
                    _state.value = _state.value.copy(
                        albums = _state.value.albums + results.albums,
                    )
                }
            } catch (
                e: Exception,
            ) {
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
                        id = t.id, title = t.title, artist = t.artist,
                        albumId = t.albumId, artistId = t.artistId,
                        durationSeconds = t.duration, trackNumber = t.trackNumber,
                        coverArtUrl = t.coverArt, suffix = t.suffix,
                        contentType = t.contentType,
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
}
