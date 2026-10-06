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

/** Best single hit shown above sectioned results (Phase-4 UX). */
sealed class SearchTopHit {
    data class TrackHit(val track: Track) : SearchTopHit()
    data class ArtistHit(val artist: Artist) : SearchTopHit()
    data class AlbumHit(val album: Album) : SearchTopHit()
}

/** External Discover row (MusicBrainz / Last.fm) — Phase-5. */
data class DiscoverArtistHit(
    val name: String,
    val mbid: String? = null,
    val disambiguation: String? = null,
    val source: String = "musicbrainz",
    val inLibrary: Boolean = false,
    val localArtistId: String? = null,
)

data class DiscoverTrackHit(
    val title: String,
    val artistName: String? = null,
    val mbid: String? = null,
    val inLibrary: Boolean = false,
    val localTrackId: String? = null,
)

/** Lyrics FTS hit shown in “From lyrics” section (Phase-6). */
data class LyricSearchHit(val track: Track, val snippet: String? = null)

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
    /** FTS empty while metadata sync running — show indexing empty state. */
    val isIndexingLibrary: Boolean = false,
    /** True when local FTS index has zero rows (cold start / pre-sync). */
    val ftsEmpty: Boolean = false,
    val topHit: SearchTopHit? = null,
    val usedSoftTypo: Boolean = false,
    /** Idle tag chips from Room Last.fm enrich (Phase-5). */
    val popularTags: List<String> = emptyList(),
    val tagsEmpty: Boolean = false,
    val hasLastFmKey: Boolean = false,
    /** Result-header tag chips overlapping query. */
    val matchedTags: List<String> = emptyList(),
    val discoverArtists: List<DiscoverArtistHit> = emptyList(),
    val discoverTracks: List<DiscoverTrackHit> = emptyList(),
    val isDiscoverLoading: Boolean = false,
    val lyricsMatches: List<LyricSearchHit> = emptyList(),
    val searchLyricsEnabled: Boolean = false,
    /** Local FTS bm25 ranks (entity id → score); kept across loadMore. */
    val ftsRanks: Map<String, Double> = emptyMap(),
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
    private val localSearch: com.lucasdss.ftpmusic.app.data.search.LocalSearchRepository,
    private val searchIndexRebuilder: com.lucasdss.ftpmusic.app.data.search.SearchIndexRebuilder,
    private val metadataSyncWorker: com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker,
    private val api: SubsonicApi,
    private val offlineModeManager: OfflineModeManager,
    private val musicBrainzService: com.lucasdss.ftpmusic.app.data.network.MusicBrainzService,
    private val lastFmService: com.lucasdss.ftpmusic.app.data.network.LastFmService,
    private val lyricsCacheDao: com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao,
) : ViewModel() {

    companion object {
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val MAX_RECENT = 10
        private const val TYPEAHEAD_DEBOUNCE_MS = 300L
        private const val PAGE_SIZE = 50
        private const val MIN_QUERY_LEN = 2
        private const val DISCOVER_LIMIT = 8
    }

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var typeaheadJob: Job? = null
    private var discoverJob: Job? = null
    private var albumSearchOffset = 0
    private var artistSearchOffset = 0
    private var songSearchOffset = 0
    private var isLoadingMoreSearch = false
    private var lastTagChipQuery: String? = null

    private fun isOffline(): Boolean = try {
        offlineModeManager.isOffline.value
    } catch (_: Exception) {
        try {
            offlineModeManager.isOfflineEnabled()
        } catch (_: Exception) {
            false
        }
    }

    /** Simulate Offline or no OS INTERNET — playable Room only. */
    fun isLocalOnly(): Boolean = LocalOnlyPolicy.isLocalOnly(isOffline())

    init {
        // Load recent searches from storage
        val raw = storage.get(KEY_RECENT_SEARCHES) ?: ""
        val recent = raw.split("|||").filter { it.isNotBlank() }
        val hasKey = !storage.get(SecureStorage.KEY_LASTFM_API_KEY).isNullOrBlank()
        val lyricsOn = storage.get(SecureStorage.KEY_SEARCH_LYRICS)?.toBooleanStrictOrNull() ?: false
        _state.value = _state.value.copy(
            recentSearches = recent,
            hasLastFmKey = hasKey,
            searchLyricsEnabled = lyricsOn,
        )
        loadGenres()
        loadPopularTags()
        maybeEnableSearchLyricsDefault()
        observeLocalOnly()
        observeIndexing()
    }

    private fun maybeEnableSearchLyricsDefault() {
        viewModelScope.launch {
            try {
                val enabled = com.lucasdss.ftpmusic.app.data.search.SearchLyricsDefaults.maybeEnable(
                    storage,
                    lyricsCacheDao,
                ) {
                    searchIndexRebuilder.scheduleRebuild(debounceMs = 500L)
                }
                if (enabled) {
                    _state.value = _state.value.copy(searchLyricsEnabled = true)
                } else {
                    val on = storage.get(SecureStorage.KEY_SEARCH_LYRICS)?.toBooleanStrictOrNull() ?: false
                    _state.value = _state.value.copy(searchLyricsEnabled = on)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun observeIndexing() {
        viewModelScope.launch {
            try {
                metadataSyncWorker.status.collect { sync ->
                    val ftsEmpty = searchIndexRebuilder.ftsCount() == 0
                    _state.value = _state.value.copy(
                        isIndexingLibrary = sync.isRunning && ftsEmpty,
                        ftsEmpty = ftsEmpty,
                    )
                }
            } catch (_: Exception) {
                // unit tests / missing mocks
            }
        }
    }

    /** Airplane / Simulate Offline flip mid-session → re-run active search (ADR 0051). */
    private fun observeLocalOnly() {
        viewModelScope.launch {
            try {
                val offlineFlow = offlineModeManager.isOffline
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

    fun loadPopularTags() {
        viewModelScope.launch {
            try {
                val hasKey = !storage.get(SecureStorage.KEY_LASTFM_API_KEY).isNullOrBlank()
                val tagged = metadataDao.getArtistsWithSearchTags(500)
                val tags = com.lucasdss.ftpmusic.app.data.search.SearchMoodTags.aggregateTags(tagged, limit = 24)
                _state.value = _state.value.copy(
                    popularTags = tags,
                    tagsEmpty = tags.isEmpty(),
                    hasLastFmKey = hasKey,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(tagsEmpty = true)
            }
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
        discoverJob?.cancel()
        lastTagChipQuery = null
        val trimmed = query.trim()
        // Phase-4: keep prior results painted until new search replaces them (no flicker).
        _state.value = _state.value.copy(
            query = query,
            isLoading = trimmed.length >= MIN_QUERY_LEN,
            // Clear only when query emptied
            artists = if (trimmed.isEmpty()) emptyList() else _state.value.artists,
            albums = if (trimmed.isEmpty()) emptyList() else _state.value.albums,
            tracks = if (trimmed.isEmpty()) emptyList() else _state.value.tracks,
            playlists = if (trimmed.isEmpty()) emptyList() else _state.value.playlists,
            matchedGenres = if (trimmed.isEmpty()) emptyList() else _state.value.matchedGenres,
            allTracks = if (trimmed.isEmpty()) emptyList() else _state.value.allTracks,
            localTrackIds = if (trimmed.isEmpty()) emptySet() else _state.value.localTrackIds,
            hasSearched = if (trimmed.isEmpty()) false else _state.value.hasSearched,
            resultCount = if (trimmed.isEmpty()) 0 else _state.value.resultCount,
            topHit = if (trimmed.isEmpty()) null else _state.value.topHit,
            usedSoftTypo = if (trimmed.isEmpty()) false else _state.value.usedSoftTypo,
            filterType = if (trimmed.isEmpty()) SearchFilterType.ALL else _state.value.filterType,
            matchedTags = if (trimmed.isEmpty()) emptyList() else _state.value.matchedTags,
            discoverArtists = if (trimmed.isEmpty()) emptyList() else _state.value.discoverArtists,
            discoverTracks = if (trimmed.isEmpty()) emptyList() else _state.value.discoverTracks,
            lyricsMatches = if (trimmed.isEmpty()) emptyList() else _state.value.lyricsMatches,
            ftsRanks = if (trimmed.isEmpty()) emptyMap() else _state.value.ftsRanks,
            isDiscoverLoading = false,
        )
        if (trimmed.length >= MIN_QUERY_LEN) {
            typeaheadJob = viewModelScope.launch {
                delay(TYPEAHEAD_DEBOUNCE_MS)
                if (_state.value.query.trim() == trimmed) {
                    search()
                }
            }
        } else {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    /** Decade chip: set query and search immediately (no blank flash). */
    fun onDecadeChip(decade: String) {
        typeaheadJob?.cancel()
        searchJob?.cancel()
        discoverJob?.cancel()
        lastTagChipQuery = null
        _state.value = _state.value.copy(query = decade, isLoading = true)
        search()
    }

    /** Tag chip: immediate local search; optional Last.fm Discover boost. */
    fun onTagChip(tag: String) {
        typeaheadJob?.cancel()
        searchJob?.cancel()
        discoverJob?.cancel()
        lastTagChipQuery = tag
        _state.value = _state.value.copy(query = tag, isLoading = true)
        search()
    }

    fun onMoodChip(moodLabel: String) {
        val mood = com.lucasdss.ftpmusic.app.data.search.SearchMoodTags.MOODS
            .firstOrNull { it.label.equals(moodLabel, ignoreCase = true) }
        val q = mood?.searchQuery() ?: moodLabel.lowercase()
        onTagChip(q)
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.length < MIN_QUERY_LEN) return
        typeaheadJob?.cancel()
        searchJob?.cancel()
        discoverJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val filterEnabled = _state.value.filterDownloaded
            val localOnly = isLocalOnly()
            try {
                // Local-only: playable Room / FTS only (1A) — skip full catalog + API.
                if (localOnly) {
                    val hit = localSearch.search(query, playableOnly = true)
                    val split = splitLyricHits(hit, query)
                    val ranks = hit.ftsRanks
                    val rankedArtists = SearchResultMerger.rankByQuery(
                        split.artists,
                        query,
                        bm25Of = { ranks[it.id] },
                    ) { it.name }
                    val rankedAlbums = SearchResultMerger.rankByQuery(
                        split.albums,
                        query,
                        bm25Of = { ranks[it.id] },
                    ) { it.name }
                    val rankedTracks = rankTracks(split.songTracks, query, ranks)
                    val localIds = hit.tracks.map { it.id }.toSet()
                    val matchedTags = com.lucasdss.ftpmusic.app.data.search.SearchMoodTags
                        .matchedTagsForQuery(hit.artists, query)
                    _state.value = _state.value.copy(
                        tracks = rankedTracks,
                        albums = rankedAlbums,
                        artists = rankedArtists,
                        playlists = split.playlists,
                        matchedGenres = hit.genres,
                        allTracks = rankedTracks,
                        localTrackIds = localIds,
                        filterDownloaded = true,
                        resultCount = rankedTracks.size + rankedAlbums.size + rankedArtists.size +
                            split.playlists.size + hit.genres.size + split.lyricsMatches.size,
                        hasSearched = true,
                        isLoading = false,
                        topHit = pickTopHit(query, rankedTracks, rankedArtists, rankedAlbums),
                        usedSoftTypo = hit.usedSoftTypo,
                        ftsEmpty = !hit.usedFts && searchIndexRebuilder.ftsCount() == 0,
                        matchedTags = matchedTags,
                        lyricsMatches = split.lyricsMatches,
                        discoverArtists = emptyList(),
                        discoverTracks = emptyList(),
                        isDiscoverLoading = false,
                        ftsRanks = ranks,
                    )
                    return@launch
                }

                // 1. Show cached results from local DB / FTS immediately (ranked)
                val hit = localSearch.search(query, playableOnly = false)
                val split = splitLyricHits(hit, query)
                val ranks = hit.ftsRanks
                val cachedTracks = rankTracks(split.songTracks, query, ranks)
                val cachedAlbums = SearchResultMerger.rankByQuery(
                    split.albums,
                    query,
                    bm25Of = { ranks[it.id] },
                ) { it.name }
                val cachedArtists = SearchResultMerger.rankByQuery(
                    split.artists,
                    query,
                    bm25Of = { ranks[it.id] },
                ) { it.name }
                val cachedPlaylists = SearchResultMerger.rankByQuery(split.playlists, query) { it.name }
                val matchedGenres = hit.genres
                val matchedTags = com.lucasdss.ftpmusic.app.data.search.SearchMoodTags
                    .matchedTagsForQuery(hit.artists, query)
                _state.value = _state.value.copy(
                    tracks = cachedTracks,
                    albums = cachedAlbums,
                    artists = cachedArtists,
                    playlists = cachedPlaylists,
                    matchedGenres = matchedGenres,
                    allTracks = cachedTracks,
                    resultCount = cachedTracks.size + cachedAlbums.size + cachedArtists.size +
                        cachedPlaylists.size + matchedGenres.size + split.lyricsMatches.size,
                    hasSearched = true,
                    topHit = pickTopHit(query, cachedTracks, cachedArtists, cachedAlbums),
                    usedSoftTypo = hit.usedSoftTypo,
                    matchedTags = matchedTags,
                    lyricsMatches = split.lyricsMatches,
                    ftsRanks = ranks,
                )
                launchDiscover(query)
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
                    bm25Of = { ranks[it.id] },
                ) { it.name }
                val mergedAlbums = SearchResultMerger.rankByQuery(
                    SearchResultMerger.unionById(results.albums, cachedAlbums) { it.id },
                    query,
                    bm25Of = { ranks[it.id] },
                ) { it.name }
                val mergedTracksRaw = SearchResultMerger.unionById(results.tracks, cachedTracks) { it.id }
                val mergedTracks = rankTracks(mergedTracksRaw, query, ranks)
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
                val lyricsCount = _state.value.lyricsMatches.size
                val resultCount = mergedArtists.size + mergedAlbums.size + mergedTracks.size +
                    mergedPlaylists.size + matchedGenres.size + lyricsCount
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
                    topHit = pickTopHit(query, filteredTracks, mergedArtists, mergedAlbums),
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
                    val ranks = _state.value.ftsRanks
                    val artists = SearchResultMerger.rankByQuery(
                        SearchResultMerger.unionById(_state.value.artists, results.artists) { it.id },
                        query,
                        bm25Of = { ranks[it.id] },
                    ) { it.name }
                    val albums = SearchResultMerger.rankByQuery(
                        SearchResultMerger.unionById(_state.value.albums, results.albums) { it.id },
                        query,
                        bm25Of = { ranks[it.id] },
                    ) { it.name }
                    val tracks = rankTracks(
                        SearchResultMerger.unionById(_state.value.allTracks, results.tracks) { it.id },
                        query,
                        ranks,
                    )
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

    /** Prefer exact artist/album name match, else best-ranked track. */
    private fun pickTopHit(
        query: String,
        tracks: List<Track>,
        artists: List<Artist>,
        albums: List<Album>,
    ): SearchTopHit? {
        artists.firstOrNull { SearchResultMerger.isExactName(it.name, query) }
            ?.let { return SearchTopHit.ArtistHit(it) }
        albums.firstOrNull { SearchResultMerger.isExactName(it.name, query) }
            ?.let { return SearchTopHit.AlbumHit(it) }
        return when {
            tracks.isNotEmpty() -> SearchTopHit.TrackHit(tracks.first())
            artists.isNotEmpty() -> SearchTopHit.ArtistHit(artists.first())
            albums.isNotEmpty() -> SearchTopHit.AlbumHit(albums.first())
            else -> null
        }
    }

    private fun rankTracks(
        tracks: List<Track>,
        query: String,
        ftsRanks: Map<String, Double> = emptyMap(),
    ): List<Track> = SearchResultMerger.rankByFields(
        tracks,
        query,
        fieldsOf = { listOf(it.title, it.artist, it.album) },
        popularityOf = { SearchResultMerger.trackPopularity(it.playCount, it.lastPlayedAt) },
        bm25Of = if (ftsRanks.isEmpty()) {
            null
        } else {
            { ftsRanks[it.id] }
        },
    )

    private data class LyricSplit(
        val songTracks: List<Track>,
        val lyricsMatches: List<LyricSearchHit>,
        val artists: List<Artist>,
        val albums: List<Album>,
        val playlists: List<Playlist>,
    )

    private suspend fun splitLyricHits(
        hit: com.lucasdss.ftpmusic.app.data.search.LocalSearchHit,
        query: String,
    ): LyricSplit {
        val trackMatch = hit.trackMatchIds.toSet()
        val lyricOnlyIds = hit.lyricTrackIds.filter { it !in trackMatch }.toSet()
        val byId = hit.tracks.associateBy { it.id }
        val songTracks = hit.tracks
            .filter { it.id !in lyricOnlyIds }
            .map { it.toTrack() }
        val lyricsMatches = if (lyricOnlyIds.isEmpty()) {
            emptyList()
        } else {
            val cached = try {
                lyricsCacheDao.getByTrackIds(lyricOnlyIds.toList()).associateBy { it.trackId }
            } catch (_: Exception) {
                emptyMap()
            }
            lyricOnlyIds.mapNotNull { id ->
                val entity = byId[id] ?: return@mapNotNull null
                LyricSearchHit(
                    track = entity.toTrack(),
                    snippet = com.lucasdss.ftpmusic.app.data.search.SearchLyricsDefaults.snippet(
                        cached[id],
                        query,
                    ),
                )
            }
        }
        return LyricSplit(
            songTracks = songTracks,
            lyricsMatches = lyricsMatches,
            artists = hit.artists.map { it.toArtist() },
            albums = hit.albums.map { it.toAlbum() },
            playlists = hit.playlists.map { it.toPlaylist() },
        )
    }

    /**
     * Secondary live Discover after local paint (Phase-5).
     * Skipped when offline / local-only. Cancelled on new keystroke.
     */
    private fun launchDiscover(query: String) {
        discoverJob?.cancel()
        if (isLocalOnly() || query.length < MIN_QUERY_LEN) {
            _state.value = _state.value.copy(
                discoverArtists = emptyList(),
                discoverTracks = emptyList(),
                isDiscoverLoading = false,
            )
            return
        }
        discoverJob = viewModelScope.launch {
            _state.value = _state.value.copy(isDiscoverLoading = true)
            try {
                val tagBoost = lastTagChipQuery
                val mbArtists = try {
                    musicBrainzService.searchArtists(query, DISCOVER_LIMIT)
                } catch (_: Exception) {
                    emptyList()
                }
                val mbTracks = try {
                    musicBrainzService.searchRecordings(query, DISCOVER_LIMIT)
                } catch (_: Exception) {
                    emptyList()
                }
                val lastFmArtists = try {
                    if (tagBoost != null) {
                        lastFmService.fetchTagTopArtists(tagBoost, DISCOVER_LIMIT)
                    } else {
                        lastFmService.searchArtists(query, DISCOVER_LIMIT)
                    }
                } catch (_: Exception) {
                    emptyList()
                }

                val artistHits = LinkedHashMap<String, DiscoverArtistHit>()
                for (a in mbArtists) {
                    val local = resolveLocalArtist(a.mbid, a.name)
                    artistHits[a.name.lowercase()] = DiscoverArtistHit(
                        name = a.name,
                        mbid = a.mbid,
                        disambiguation = a.disambiguation,
                        source = "musicbrainz",
                        inLibrary = local != null,
                        localArtistId = local?.id,
                    )
                }
                for (a in lastFmArtists) {
                    val key = a.name.lowercase()
                    if (artistHits.containsKey(key)) continue
                    val local = resolveLocalArtist(a.mbid, a.name)
                    artistHits[key] = DiscoverArtistHit(
                        name = a.name,
                        mbid = a.mbid,
                        source = "lastfm",
                        inLibrary = local != null,
                        localArtistId = local?.id,
                    )
                }

                val trackHits = mbTracks.map { t ->
                    val local = if (!t.mbid.isNullOrBlank()) {
                        try {
                            trackDao.getTrackByMbid(t.mbid)
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    DiscoverTrackHit(
                        title = t.name,
                        artistName = t.artistName,
                        mbid = t.mbid,
                        inLibrary = local != null,
                        localTrackId = local?.id,
                    )
                }

                if (_state.value.query.trim() != query) return@launch
                _state.value = _state.value.copy(
                    discoverArtists = artistHits.values.take(DISCOVER_LIMIT).toList(),
                    discoverTracks = trackHits.take(DISCOVER_LIMIT),
                    isDiscoverLoading = false,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(isDiscoverLoading = false)
            }
        }
    }

    private suspend fun resolveLocalArtist(mbid: String?, name: String): CachedArtistEntity? {
        if (!mbid.isNullOrBlank()) {
            try {
                metadataDao.getArtistByMbid(mbid)?.let { return it }
            } catch (_: Exception) {
            }
        }
        return try {
            metadataDao.getArtistByExactName(name)
        } catch (_: Exception) {
            null
        }
    }

    /** Soft-cache Discover artist stub into Room + FTS, then navigate (market: tap opens). */
    fun onDiscoverArtistTap(hit: DiscoverArtistHit, onNavigate: (String) -> Unit) {
        if (hit.inLibrary && !hit.localArtistId.isNullOrBlank()) {
            onNavigate(hit.localArtistId)
            return
        }
        viewModelScope.launch {
            val stubId = hit.localArtistId
                ?: hit.mbid?.let { "mb:$it" }
                ?: "ext:${hit.name.lowercase().hashCode()}"
            try {
                metadataDao.upsertArtists(
                    listOf(
                        CachedArtistEntity(
                            id = stubId,
                            name = hit.name,
                            musicbrainzId = hit.mbid,
                        ),
                    ),
                )
                searchIndexRebuilder.scheduleRebuild(debounceMs = 3_000L)
            } catch (_: Exception) {
            }
            onNavigate(stubId)
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
            searchIndexRebuilder.scheduleRebuild(debounceMs = 3_000L)
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
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
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
