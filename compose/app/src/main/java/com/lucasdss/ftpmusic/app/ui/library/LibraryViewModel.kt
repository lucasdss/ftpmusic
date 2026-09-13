package com.lucasdss.ftpmusic.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.db.AlbumEntity
import com.lucasdss.ftpmusic.app.data.db.ArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedGenreEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.GenreEntity
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeDao
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntryEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorker
import com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.data.model.Artist
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.DailyMixGenerationCoordinator
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class HomeStats(
    val totalPlays: Int = 0,
    val listeningMinutes: Int = 0,
    val artistCount: Int = 0,
    val trackCount: Int = 0,
)

data class PlaylistView(
    val id: String,
    val name: String,
    val trackCount: Int = 0,
    val coverArt: String? = null,
    val isSynced: Boolean = false,
    val isDownloaded: Boolean = false,
)

data class LibraryState(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    /** Non-null while an album search is active; browse [albums] stays intact. */
    val albumSearchResults: List<Album>? = null,
    /** Non-null while an artist search is active; browse [artists] stays intact. */
    val artistSearchResults: List<Artist>? = null,
    val randomAlbums: List<Album> = emptyList(),
    val recentlyPlayed: List<TrackEntity> = emptyList(),
    val genres: List<String> = emptyList(),
    val mixCards: List<MixCard> = emptyList(),
    val playlists: List<PlaylistView> = emptyList(),
    val alphaLetters: List<String> = emptyList(),
    val radioStations: List<RadioStation> = emptyList(),
    val stats: HomeStats = HomeStats(),
    val isLoading: Boolean = false,
    /** True once any DB-backed loader produced data — the full-screen loading
     *  spinner only shows until the first content render (data-first). */
    val hasLoadedOnce: Boolean = false,
    val isCreatingPlaylist: Boolean = false,
    val createPlaylistError: String? = null,
    val playlistCreated: Boolean = false,
    /** The playlist just created (temp id until the server remap flush). */
    val createdPlaylist: PlaylistView? = null,
    val error: String? = null,
    val downloadStatusByAlbumId: Map<String, String> = emptyMap(),
    val isResyncing: Boolean = false,
    /** True while lazy Daily Mix generation runs from a Home open. */
    val isGeneratingMixes: Boolean = false,
    /**
     * True when the configured server URL points at the device itself
     * (127.0.0.1 / localhost) — almost always a stale dev proxy address.
     * Surfaced as a warning; never blocks (adb-reverse dev setups are valid).
     */
    val configWarning: Boolean = false,
    // v43: Entity-level favorites (ledger reads + bookmarked radio)
    val starredAlbums: List<AlbumEntity> = emptyList(),
    val starredArtists: List<ArtistEntity> = emptyList(),
    val bookmarkedRadio: List<RadioFavoriteEntity> = emptyList(),
    val likedAlbumIds: Set<String> = emptySet(),
    val dislikedAlbumIds: Set<String> = emptySet(),
    val likedArtistIds: Set<String> = emptySet(),
    val dislikedArtistIds: Set<String> = emptySet(),
    val bookmarkedStationIds: Set<String> = emptySet(),
    /** Cover-montage art ids per playlist id (Home Playlists row). */
    val playlistMontages: Map<String, List<String>> = emptyMap(),
    // v43: Home section visibility (Settings → Home & Favorites)
    val showPlaylistsOnHome: Boolean = true,
    val showFavArtistsSection: Boolean = true,
    val showFavAlbumsSection: Boolean = true,
    val showFavRadioSection: Boolean = true,
)

/** Albums-tab slice — Compose only invalidates when these fields change. */
data class AlbumTabUi(
    val albums: List<Album> = emptyList(),
    val albumSearchResults: List<Album>? = null,
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val likedAlbumIds: Set<String> = emptySet(),
    val dislikedAlbumIds: Set<String> = emptySet(),
    val configWarning: Boolean = false,
)

/** Artists-tab slice — Compose only invalidates when these fields change. */
data class ArtistTabUi(
    val artists: List<Artist> = emptyList(),
    val artistSearchResults: List<Artist>? = null,
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val likedArtistIds: Set<String> = emptySet(),
    val dislikedArtistIds: Set<String> = emptySet(),
    val configWarning: Boolean = false,
)

data class LibraryShellUi(
    val configWarning: Boolean = false,
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
)

data class RadioStation(val id: String, val name: String, val streamUrl: String, val homePageUrl: String? = null)

@androidx.compose.runtime.Stable
data class MixCard(val id: Long, val name: String, val coverArts: List<String>, val songCount: Int = 0)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val api: SubsonicApi,
    private val trackDao: TrackDao,
    private val genreDao: GenreDao,
    private val genreMixDao: GenreMixDao,
    private val playlistDao: PlaylistDao,
    private val pendingChangeDao: PendingPlaylistChangeDao,
    private val syncWorker: PlaylistSyncWorker,
    private val playlistRepo: PlaylistRepository,
    private val metadataDao: CachedMetadataDao,
    private val metadataSyncWorker: MetadataSyncWorker,
    private val downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager,
    private val storage: SecureStorage,
    private val playbackManager: PlaybackManager,
    private val offlineModeManager: com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager,
    private val favoriteRepository: com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository,
    private val radioFavoriteDao: com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao,
    private val dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    val libraryShellUi: StateFlow<LibraryShellUi> = _state
        .map {
            LibraryShellUi(
                configWarning = it.configWarning,
                isLoading = it.isLoading,
                hasLoadedOnce = it.hasLoadedOnce,
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryShellUi())

    val albumTabUi: StateFlow<AlbumTabUi> = _state
        .map {
            AlbumTabUi(
                albums = it.albums,
                albumSearchResults = it.albumSearchResults,
                isLoading = it.isLoading,
                hasLoadedOnce = it.hasLoadedOnce,
                likedAlbumIds = it.likedAlbumIds,
                dislikedAlbumIds = it.dislikedAlbumIds,
                configWarning = it.configWarning,
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumTabUi())

    val artistTabUi: StateFlow<ArtistTabUi> = _state
        .map {
            ArtistTabUi(
                artists = it.artists,
                artistSearchResults = it.artistSearchResults,
                isLoading = it.isLoading,
                hasLoadedOnce = it.hasLoadedOnce,
                likedArtistIds = it.likedArtistIds,
                dislikedArtistIds = it.dislikedArtistIds,
                configWarning = it.configWarning,
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistTabUi())

    private val authHelper = SubsonicAuthHelper()

    // Read credentials fresh per-call (Settings may change server at runtime)
    private fun username() = storage.get(SecureStorage.KEY_USERNAME) ?: ""
    private fun password() = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
    fun isOffline(): Boolean = try {
        offlineModeManager.isOffline?.value == true
    } catch (_: Exception) {
        false
    }

    /**
     * Refresh the loopback-config warning from the current server URL.
     * Called on Home resume (a stale dev proxy URL like 127.0.0.1:9999 must be
     * surfaced, and cleared again once the user fixes it in Settings).
     */
    fun refreshConfigWarning() {
        _state.value = _state.value.copy(
            configWarning = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.isLoopback(),
        )
    }
    private var albumOffset = 0
    private var isLoadingMore = false

    /** True after [loadAlphaAlbums] has a full (DB or API) catalog — disables
     *  [loadMoreAlbums] so newest-page append cannot fight the alpha list. */
    @Volatile private var alphaCatalogComplete = false
    private var alphaAlbums: List<Album> = emptyList()
    private var lastAlphaSync: Long = 0

    init {
        if (username().isNotEmpty()) {
            loadArtists()
            loadAlbums()
            loadRandomAlbums()
            loadRecentlyPlayed()
        }
        refreshHomePrefs()
        loadFavorites()
        observeFavorites()
    }

    // ── v43: Home section visibility + favorites ─────────────────────────────

    /** Re-read the Home & Favorites toggles from storage (Settings may have
     *  changed them). Called on every Home resume so changes apply live. */
    fun refreshHomePrefs() {
        _state.value = _state.value.copy(
            showPlaylistsOnHome = storage.get(SecureStorage.KEY_HOME_SHOW_PLAYLISTS)?.toBooleanStrictOrNull() ?: true,
            showFavArtistsSection =
                storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ARTISTS)?.toBooleanStrictOrNull() ?: true,
            showFavAlbumsSection = storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ALBUMS)?.toBooleanStrictOrNull() ?: true,
            showFavRadioSection = storage.get(SecureStorage.KEY_HOME_SHOW_FAV_RADIO)?.toBooleanStrictOrNull() ?: true,
        )
    }

    /** Load starred albums/artists, bookmarked radio, and the like/dislike id
     *  sets used by the Library tabs and Home favorite rows. */
    fun loadFavorites() {
        viewModelScope.launch {
            try {
                applyFavoritesSnapshot(readFavoritesSnapshot())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Never silent: a failing favorites load hides every Home
                // favorite section — surface it for diagnosis.
                android.util.Log.w("ftpmusic-home", "loadFavorites failed", e)
            }
        }
    }

    /** Reactive favorites: Room Flow observation so Home/Library/Favorites
     *  update live whenever ANY screen writes the favorites tables — no
     *  resume/recomposition dependency. */
    fun observeFavorites() {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.flow.combine(
                    metadataDao.getStarredAlbumsFlow(50),
                    metadataDao.getStarredArtistsFlow(50),
                    metadataDao.getDislikedAlbumIdsFlow(),
                    metadataDao.getDislikedArtistIdsFlow(),
                    radioFavoriteDao.getAllFlow(),
                ) { albums, artists, dislikedAlbums, dislikedArtists, radio ->
                    FavoritesSnapshot(
                        albums = albums,
                        artists = artists,
                        radio = radio,
                        dislikedAlbums = dislikedAlbums.toSet(),
                        dislikedArtists = dislikedArtists.toSet(),
                    )
                }.collect { applyFavoritesSnapshot(it) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-home", "observeFavorites stopped", e)
            }
        }
    }

    private data class FavoritesSnapshot(
        val albums: List<AlbumEntity>,
        val artists: List<ArtistEntity>,
        val radio: List<RadioFavoriteEntity>,
        val dislikedAlbums: Set<String>,
        val dislikedArtists: Set<String>,
    )

    private suspend fun readFavoritesSnapshot(): FavoritesSnapshot {
        val albums = metadataDao.getStarredAlbums(50)
        val artists = metadataDao.getStarredArtists(50)
        val radio = radioFavoriteDao.getAll()
        return FavoritesSnapshot(
            albums = albums,
            artists = artists,
            radio = radio,
            dislikedAlbums = metadataDao.getDislikedAlbumIds().toSet(),
            dislikedArtists = metadataDao.getDislikedArtistIds().toSet(),
        )
    }

    private fun applyFavoritesSnapshot(s: FavoritesSnapshot) {
        _state.value = _state.value.copy(
            starredAlbums = s.albums,
            starredArtists = s.artists,
            bookmarkedRadio = s.radio,
            likedAlbumIds = s.albums.map { it.id }.toSet(),
            dislikedAlbumIds = s.dislikedAlbums,
            likedArtistIds = s.artists.map { it.id }.toSet(),
            dislikedArtistIds = s.dislikedArtists,
            bookmarkedStationIds = s.radio.map { it.stationId }.toSet(),
            hasLoadedOnce = true,
        )
    }

    /** Ensure the favorites ledger is populated even when no metadata sync has
     *  run since the ledger code shipped (network-free fix-up: INSERT OR IGNORE
     *  + metadata refresh from cached_albums/cached_artists). No-op when the
     *  ledger already covers the cached metadata. */
    fun healEmptyLedger() {
        viewModelScope.launch {
            try {
                val cachedAlbums = metadataDao.albumCount()
                if (cachedAlbums > 0 && metadataDao.ledgerAlbumCount() < cachedAlbums) {
                    metadataDao.syncAlbumLedger()
                    android.util.Log.i(
                        "ftpmusic-home",
                        "Ledger healed: albums populated from $cachedAlbums cached albums",
                    )
                }
                val cachedArtists = metadataDao.artistCount()
                if (cachedArtists > 0 && metadataDao.ledgerArtistCount() < cachedArtists) {
                    metadataDao.syncArtistLedger()
                    android.util.Log.i(
                        "ftpmusic-home",
                        "Ledger healed: artists populated from $cachedArtists cached artists",
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("ftpmusic-home", "Ledger heal failed", e)
            }
        }
    }

    // ── Album favorites (thumbs, like == server star) ───────────────────────

    fun toggleAlbumLike(albumId: String) {
        val liked = _state.value.likedAlbumIds.contains(albumId)
        val previous = _state.value
        _state.value = _state.value.copy(
            likedAlbumIds = if (liked) previous.likedAlbumIds - albumId else previous.likedAlbumIds + albumId,
            dislikedAlbumIds = previous.dislikedAlbumIds - albumId,
        )
        viewModelScope.launch {
            try {
                if (liked) {
                    favoriteRepository.unlikeAlbum(albumId)
                } else {
                    favoriteRepository.likeAlbum(albumId)
                }
                loadFavorites()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-home", "toggleAlbumLike failed — rolled back", e)
                _state.value = previous // rollback
            }
        }
    }

    fun toggleAlbumDislike(albumId: String) {
        val disliked = _state.value.dislikedAlbumIds.contains(albumId)
        val previous = _state.value
        _state.value = _state.value.copy(
            dislikedAlbumIds = if (disliked) {
                previous.dislikedAlbumIds - albumId
            } else {
                previous.dislikedAlbumIds +
                    albumId
            },
            likedAlbumIds = previous.likedAlbumIds - albumId,
        )
        viewModelScope.launch {
            try {
                if (disliked) {
                    favoriteRepository.clearDislikeAlbum(albumId)
                } else {
                    favoriteRepository.dislikeAlbum(albumId)
                }
                loadFavorites()
            } catch (e: Exception) {
                _state.value = previous
            }
        }
    }

    // ── Artist favorites (thumbs, like == server star) ──────────────────────

    fun toggleArtistLike(artistId: String) {
        val liked = _state.value.likedArtistIds.contains(artistId)
        val previous = _state.value
        _state.value = _state.value.copy(
            likedArtistIds = if (liked) previous.likedArtistIds - artistId else previous.likedArtistIds + artistId,
            dislikedArtistIds = previous.dislikedArtistIds - artistId,
        )
        viewModelScope.launch {
            try {
                if (liked) {
                    favoriteRepository.unlikeArtist(artistId)
                } else {
                    favoriteRepository.likeArtist(artistId)
                }
                loadFavorites()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-home", "toggleArtistLike failed — rolled back", e)
                _state.value = previous
            }
        }
    }

    fun toggleArtistDislike(artistId: String) {
        val disliked = _state.value.dislikedArtistIds.contains(artistId)
        val previous = _state.value
        _state.value = _state.value.copy(
            dislikedArtistIds = if (disliked) {
                previous.dislikedArtistIds - artistId
            } else {
                previous.dislikedArtistIds +
                    artistId
            },
            likedArtistIds = previous.likedArtistIds - artistId,
        )
        viewModelScope.launch {
            try {
                if (disliked) {
                    favoriteRepository.clearDislikeArtist(artistId)
                } else {
                    favoriteRepository.dislikeArtist(artistId)
                }
                loadFavorites()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-home", "toggleArtistDislike failed — rolled back", e)
                _state.value = previous
            }
        }
    }

    // ── Radio bookmarks (local-only) ────────────────────────────────────────

    fun toggleRadioBookmark(station: RadioStation) {
        val bookmarked = _state.value.bookmarkedStationIds.contains(station.id)
        val previous = _state.value
        _state.value = _state.value.copy(
            bookmarkedStationIds = if (bookmarked) {
                previous.bookmarkedStationIds - station.id
            } else {
                previous.bookmarkedStationIds + station.id
            },
        )
        viewModelScope.launch {
            try {
                if (bookmarked) {
                    favoriteRepository.unbookmarkRadio(station.id)
                } else {
                    favoriteRepository.bookmarkRadio(station.id, station.name, station.streamUrl, station.homePageUrl)
                }
                loadFavorites()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-home", "toggleRadioBookmark failed — rolled back", e)
                _state.value = previous
            }
        }
    }

    private var lastMontageSignature: String? = null

    /** Build the 4-cover montage map for Home's Playlists row. Falls back to
     *  the playlist's single coverArt; callers render an icon for empty.
     *  Skipped when the playlist id+count signature is unchanged (Home resume
     *  fires this on every visit). */
    fun loadPlaylistMontages() {
        viewModelScope.launch {
            try {
                val playlists = _state.value.playlists
                if (playlists.isEmpty()) return@launch
                val signature = playlists.joinToString(",") { "${it.id}:${it.trackCount}" }
                if (signature == lastMontageSignature) return@launch
                lastMontageSignature = signature
                val montages = mutableMapOf<String, List<String>>()
                for (pl in playlists) {
                    val entries = playlistDao.getEntries(pl.id)
                    if (entries.isEmpty()) continue
                    val covers = metadataDao.getPlaylistMontageCovers(entries.map { it.trackId })
                    montages[pl.id] = covers.ifEmpty { listOfNotNull(pl.coverArt) }
                }
                _state.value = _state.value.copy(playlistMontages = montages)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    fun refreshRecentlyPlayed() {
        loadRecentlyPlayed()
    }

    fun loadStats() {
        viewModelScope.launch {
            try {
                val plays = trackDao.getTotalPlays()
                val secs = trackDao.getTotalListeningSeconds()
                val artists = trackDao.getArtistCount()
                val tracks = trackDao.getTrackCount()
                _state.value = _state.value.copy(
                    stats = HomeStats(
                        totalPlays = plays,
                        listeningMinutes = secs / 60,
                        artistCount = artists,
                        trackCount = tracks,
                    ),
                )
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "loadStats: ${e.message}")
            }
        }
    }

    fun loadGenres() {
        viewModelScope.launch { loadGenresInternal() }
    }

    private suspend fun loadGenresInternal() {
        try {
            val recentGenres = genreDao.getRecentlyPlayedGenres()
            if (recentGenres.isNotEmpty()) {
                _state.value = _state.value.copy(genres = recentGenres.take(8))
                return
            }
            val allGenres = genreDao.getAllByPopularity()
            if (allGenres.isNotEmpty()) {
                _state.value = _state.value.copy(genres = allGenres.map { it.name }.take(8))
                // Background refresh only when online
                if (!isOffline()) maybeSyncGenres()
                return
            }
            // No cached genres — fetch from API only when online
            if (isOffline()) {
                _state.value = _state.value.copy(genres = emptyList())
                return
            }
            syncGenres()
            val synced = genreDao.getAllByPopularity()
            _state.value = _state.value.copy(genres = synced.map { it.name }.take(4))
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-library", "loadGenres: ${e.message}")
        }
    }

    fun loadDailyMixes() {
        // Home-resume hook: reflect a changed/fixed server URL in the banner.
        refreshConfigWarning()
        viewModelScope.launch {
            try {
                if (metadataDao.albumCount() == 0) {
                    _state.value = _state.value.copy(mixCards = emptyList())
                    return@launch
                }
                val mixes = dailyMixRepository.getAll()
                var cards = readMixCards(mixes)
                if (cards.size < mixes.size) {
                    // Some recipes have no tracklist for today/yesterday
                    // (e.g. a genre pool populated after the last run):
                    // generate/regenerate lazily, then re-read so the missing
                    // cards appear without a manual sync.
                    if (maybeGenerateMixes()) {
                        cards = readMixCards(mixes)
                    }
                }
                _state.value = _state.value.copy(mixCards = cards, hasLoadedOnce = true)
            } catch (_: Exception) { /* silent — generation failures log inside */ }
        }
    }

    /** Read today's mix cards (falling back to yesterday's), track-count > 0. */
    private suspend fun readMixCards(mixes: List<com.lucasdss.ftpmusic.app.data.repository.CustomMix>): List<MixCard> {
        if (mixes.isEmpty()) return emptyList()
        val today = java.time.LocalDate.now().toString()
        val yesterday = java.time.LocalDate.now().minusDays(1).toString()

        // 1. All daily rows for today+yesterday in one query.
        val rowsByMix = genreMixDao.getDailyMixesForDates(listOf(today, yesterday)).groupBy { it.mixId }
        val existingByMix = mixes.mapNotNull { mix ->
            val rows = rowsByMix[mix.id] ?: return@mapNotNull null
            val existing = rows.firstOrNull { it.date == today } ?: rows.firstOrNull { it.date == yesterday }
            existing?.let { mix.id to it }
        }.toMap()
        if (existingByMix.isEmpty()) return emptyList()

        // 2. Track counts for all those daily rows in one query.
        val counts = genreMixDao.getDailyMixTrackCounts(existingByMix.values.map { it.id })
            .associate { it.mixId to it.trackCount }

        // 3. Covers only for the mixes that actually have tracks.
        return mixes.mapNotNull { mix ->
            val existing = existingByMix[mix.id] ?: return@mapNotNull null
            val songCount = counts[existing.id] ?: 0
            if (songCount == 0) return@mapNotNull null
            val coverArts = genreMixDao.getDailyMixCovers(existing.id).mapNotNull { it.coverArtUrl }
            MixCard(id = mix.id, name = mix.name, coverArts = coverArts, songCount = songCount)
        }
    }

    /**
     * Lazy Daily Mix generation on Home open: fills missing mixes AND
     * regenerates stale ones (existing mix absent, >48h old, or >=24h old with
     * >=10% listened — the same policy as the sync screen). Runs on IO.
     *
     * Returns true when a run was attempted (callers re-read the mixes after).
     * Suppressed while: another generation is in-flight (global gate), a
     * metadata sync is repopulating the library, or a same-day run already
     * produced nothing (empty pools — retry tomorrow).
     */
    private suspend fun maybeGenerateMixes(): Boolean {
        val today = java.time.LocalDate.now().toString()
        if (!DailyMixGenerationCoordinator.tryBegin()) return false
        try {
            if (metadataSyncWorker.status.value.isRunning) return false
            if (!DailyMixGenerationCoordinator.shouldAttemptToday(today)) return false
            _state.value = _state.value.copy(isGeneratingMixes = true)
            val produced = withContext(ioDispatcher) {
                dailyMixRepository.generateAll(today, manual = false)
            }
            if (produced == 0) {
                // Empty outcome (e.g. no genres populated yet) — suppress
                // same-day retries so every Home open doesn't re-run generation.
                DailyMixGenerationCoordinator.markEmptyAttempt(today)
            }
            return true
        } finally {
            _state.value = _state.value.copy(isGeneratingMixes = false)
            DailyMixGenerationCoordinator.finish()
        }
    }

    /** Refresh one mix (Home per-card action) — forced regeneration. */
    fun refreshMix(mixId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGeneratingMixes = true)
            try {
                dailyMixRepository.generateOne(mixId, manual = true)
                loadDailyMixes()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                _state.value = _state.value.copy(isGeneratingMixes = false)
            }
        }
    }

    /** Refresh every visible mix (Home header button) — one batched run. */
    fun refreshAllMixes() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGeneratingMixes = true)
            try {
                val today = java.time.LocalDate.now().toString()
                withContext(ioDispatcher) {
                    dailyMixRepository.generateAll(today, manual = true)
                }
                loadDailyMixes()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                _state.value = _state.value.copy(isGeneratingMixes = false)
            }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            var cached: List<PlaylistEntity> = emptyList()
            try {
                cached = playlistDao.getAll()
                val autoDownload = storage.get("auto_download_playlists")?.toBooleanStrictOrNull() ?: true
                _state.value = _state.value.copy(
                    playlists = cached.map { entity ->
                        val pendingCount = pendingChangeDao.pendingCountForPlaylist(entity.id)
                        val isSynced = entity.lastSyncedAt != null && pendingCount == 0
                        PlaylistView(
                            id = entity.id,
                            name = entity.name,
                            trackCount = entity.trackCount,
                            coverArt = entity.coverArt,
                            isSynced = isSynced,
                            isDownloaded = autoDownload,
                        )
                    },
                    hasLoadedOnce = true,
                )
                // Server data is fetched for the import picker (loadServerPlaylists),
                // not shown here. Local DB is the authoritative playlist list.
                loadPlaylistMontages()
            } catch (_: Exception) {
                if (cached.isEmpty()) {
                    _state.value = _state.value.copy(error = "Failed to load playlists")
                }
            }
        }
    }

    fun loadRadioStations() {
        if (isOffline()) return
        viewModelScope.launch {
            try {
                val authParams = authHelper.buildAuthParams(username(), password())
                val response = api.getInternetRadioStations(authParams)
                val sr = response["subsonic-response"] as? Map<*, *>
                val radioData = sr?.get("internetRadioStations") as? Map<*, *>
                val stations = radioData?.get("internetRadioStation") as? List<*>
                val parsed = stations?.mapNotNull { s ->
                    val m = s as? Map<*, *> ?: return@mapNotNull null
                    RadioStation(
                        id = m["id"] as? String ?: (m["streamUrl"] as? String ?: ""),
                        name = m["name"] as? String ?: "Unknown Station",
                        streamUrl = m["streamUrl"] as? String ?: "",
                        homePageUrl = m["homePageUrl"] as? String,
                    )
                } ?: emptyList()
                _state.value = _state.value.copy(radioStations = parsed)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "loadRadioStations: ${e.message}")
                // Radio stations are optional — silently ignore failures
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            _state.value =
                _state.value.copy(isCreatingPlaylist = true, createPlaylistError = null, playlistCreated = false)
            try {
                val tempId = playlistRepo.createPlaylist(name)
                val playListView = PlaylistView(id = tempId, name = name, trackCount = 0)
                val current = _state.value.playlists.toMutableList()
                current.add(0, playListView)
                _state.value = _state.value.copy(
                    playlists = current,
                    isCreatingPlaylist = false,
                    playlistCreated = true,
                    createdPlaylist = playListView,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isCreatingPlaylist = false,
                    createPlaylistError = e.message ?: "Failed to create playlist",
                )
            }
        }
    }

    fun clearCreatePlaylistError() {
        if (_state.value.createPlaylistError != null) {
            _state.value = _state.value.copy(createPlaylistError = null)
        }
    }

    /** Reset the post-creation flow (dismissed sheet or Done). */
    fun clearCreatedPlaylist() {
        if (_state.value.playlistCreated || _state.value.createdPlaylist != null) {
            _state.value = _state.value.copy(playlistCreated = false, createdPlaylist = null)
        }
    }

    /**
     * Add tracks to a freshly created playlist from the "Add Songs" picker.
     * Local-first via PlaylistRepository (dedup + pending `add_tracks` + flush),
     * then reloads the list so the track count reflects the add.
     */
    fun addTracksToNewPlaylist(playlistId: String, trackIds: List<String>) {
        if (playlistId.isBlank() || trackIds.isEmpty()) return
        viewModelScope.launch {
            try {
                // The create flush replaces the temp id with the server-assigned
                // id; add against the playlist's CURRENT id so tracks are not
                // orphaned under a dead temp id.
                val currentId = resolveCurrentPlaylistId(playlistId)
                playlistRepo.addToPlaylist(currentId, trackIds.distinct())
                // Mirror PlaylistDetailViewModel: auto-download when enabled
                val autoDownload = storage.get("auto_download_playlists")?.toBooleanStrictOrNull() ?: true
                if (autoDownload) {
                    val base = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
                    trackIds.distinct().forEach { tid ->
                        downloadManager.enqueue(
                            tid,
                            authHelper.buildStreamUrl(base, tid, username(), password()),
                            priority = 1,
                        )
                    }
                }
                loadPlaylists()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "addTracksToNewPlaylist failed: ${e.message}")
            }
        }
    }

    /**
     * Resolve the playlist's current id. The create flush deletes the temp id
     * (`new-<ts>`) and re-inserts the playlist under the server id; if the
     * requested id no longer exists, fall back to the just-created playlist
     * (matched by name) so the Add Songs flow survives the remap.
     */
    private suspend fun resolveCurrentPlaylistId(requestedId: String): String {
        val all = playlistDao.getAll()
        if (all.any { it.id == requestedId }) return requestedId
        val name = _state.value.createdPlaylist?.name
        return if (!name.isNullOrBlank()) {
            all.firstOrNull { it.name == name }?.id ?: requestedId
        } else {
            requestedId
        }
    }

    /**
     * Remove a playlist from this device only — the server copy is kept.
     * Mirrors PlaylistDetailViewModel.removeLocally: pure local delete
     * (entries + playlist row), no pending-change cancellation, no server
     * call. Unflushed pending changes for the playlist are retained and still
     * flush to the server copy; a local-only (never-synced) playlist is
     * permanently gone.
     */
    fun removePlaylistLocally(playlistId: String) {
        if (playlistId.isBlank()) return
        viewModelScope.launch {
            try {
                playlistDao.clearEntries(playlistId)
                playlistDao.delete(playlistId)
                // Drop the stale post-create reference if this was the new playlist
                if (_state.value.createdPlaylist?.id == playlistId) {
                    _state.value = _state.value.copy(playlistCreated = false, createdPlaylist = null)
                }
                loadPlaylists()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "removePlaylistLocally failed: ${e.message}")
            }
        }
    }

    /** Search the local track catalog for the Add Songs picker. */
    suspend fun searchPickerTracks(query: String): List<TrackEntity> = trackDao.searchAllTracks(query)

    /** Recently played tracks shown as Add Songs picker suggestions. */
    suspend fun pickerSuggestions(): List<TrackEntity> = trackDao.getRecentlyPlayed(limit = 50)

    private val _serverPlaylists = MutableStateFlow<List<PlaylistView>>(emptyList())
    val serverPlaylists: StateFlow<List<PlaylistView>> = _serverPlaylists.asStateFlow()

    fun loadServerPlaylists() {
        viewModelScope.launch {
            try {
                _serverPlaylists.value = playlistRepo.loadServerPlaylists()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "loadServerPlaylists failed: ${e.message}")
            }
        }
    }

    fun importPlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                playlistRepo.importPlaylist(playlistId)
                loadPlaylists()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "importPlaylist failed: ${e.message}")
            }
        }
    }

    private var lastGenreSyncMs: Long = 0L
    private var lastGenreHash: String = ""

    init {
        // Restore genre sync state across process death
        storage.get("genre_sync_ms")?.toLongOrNull()?.let { lastGenreSyncMs = it }
        storage.get("genre_sync_hash")?.let { lastGenreHash = it }
    }

    private fun maybeSyncGenres() {
        val now = System.currentTimeMillis()
        if (now - lastGenreSyncMs < 3_600_000) return // 1 hour throttle
        viewModelScope.launch {
            try {
                syncGenres()
                lastGenreSyncMs = System.currentTimeMillis()
                storage.put("genre_sync_ms", lastGenreSyncMs.toString())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    private suspend fun syncGenres() {
        try {
            val authParams = authHelper.buildAuthParams(username(), password())
            val response = api.getGenres(authParams)
            val sr = response["subsonic-response"] as? Map<*, *>
            val genresData = sr?.get("genres") as? Map<*, *>
            val genreList = genresData?.get("genre") as? List<*>
            val genres = genreList?.mapNotNull { g ->
                val m = g as? Map<*, *> ?: return@mapNotNull null
                val name = m["value"] as? String ?: (m["name"] as? String) ?: return@mapNotNull null
                GenreEntity(
                    name = name,
                    songCount = (m["songCount"] as? Number)?.toInt() ?: 0,
                    albumCount = (m["albumCount"] as? Number)?.toInt() ?: 0,
                )
            } ?: return
            val hash = genres.joinToString("|") { "${it.name}:${it.songCount}:${it.albumCount}" }.hashCode().toString()
            if (hash == lastGenreHash) return
            genreDao.upsertAll(genres)
            lastGenreHash = hash
            storage.put("genre_sync_hash", hash)
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-library", "syncGenres: ${e.message}")
        }
    }

    /**
     * Force-resync all library data from server. Fetches all 6 scopes:
     * albums, artists, playlists, genres, recently added, recently played.
     * Awaits completion of all scopes before marking sync as done.
     */
    fun resyncAll() {
        viewModelScope.launch {
            if (_state.value.isResyncing) return@launch
            _state.value = _state.value.copy(isResyncing = true)
            try {
                // Start metadata cache refresh first — it uses its own CAS guard
                // and won't double-fire if another sync is already running.
                metadataSyncWorker.syncNow()
                // Launch library API refreshes in parallel (these run alongside
                // the metadata worker's own API calls if the CAS passed above).
                val jobs = listOf(
                    launch { loadArtistsInternal() },
                    launch { loadAlbumsInternal() },
                    launch { loadRandomAlbumsInternal() },
                    launch { loadGenresInternal() },
                    launch { loadRecentlyPlayedInternal() },
                )
                jobs.joinAll()
                android.util.Log.d("ftpmusic-library", "Force resync completed — all scopes done")
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "resyncAll: ${e.message}")
            } finally {
                _state.value = _state.value.copy(isResyncing = false)
            }
        }
    }

    // Internal versions that don't set isLoading (used by resyncAll to avoid flag conflicts)
    private suspend fun loadArtistsInternal() {
        try {
            val auth = authHelper.buildAuthParams(username(), password())
            val response = api.getArtists(auth)
            val sr = response["subsonic-response"] as? Map<*, *>
            val artistsData = sr?.get("artists") as? Map<*, *>
            val artistList = artistsData?.get("index") as? List<*>
            val artists = artistList?.flatMap { idx ->
                val m = idx as? Map<*, *> ?: return@flatMap emptyList<Artist>()
                (m["artist"] as? List<*>)?.mapNotNull { a ->
                    val am = a as? Map<*, *> ?: return@mapNotNull null
                    Artist(
                        id = am["id"] as? String ?: return@mapNotNull null,
                        name = am["name"] as? String ?: return@mapNotNull null,
                        albumCount = (am["albumCount"] as? Number)?.toInt(),
                        coverArt = am["coverArt"] as? String,
                    )
                } ?: emptyList()
            } ?: emptyList()
            _state.value = _state.value.copy(artists = artists)
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-library", "loadArtists: ${e.message}")
        }
    }

    private suspend fun loadAlbumsInternal() {
        try {
            val auth = authHelper.buildAuthParams(username(), password())
            val response = api.getAlbumList2("newest", 50, 0, auth)
            val sr = response["subsonic-response"] as? Map<*, *>
            val albumList = sr?.get("albumList2") as? Map<*, *>
            val albums = (albumList?.get("album") as? List<*>)?.mapNotNull { a ->
                val m = a as? Map<*, *> ?: return@mapNotNull null
                Album(
                    id = m["id"] as? String ?: return@mapNotNull null,
                    name = m["name"] as? String ?: return@mapNotNull null,
                    artist = m["artist"] as? String,
                    year = (m["year"] as? Number)?.toInt(),
                    coverArt = m["coverArt"] as? String,
                    rating = (m["userRating"] as? Number)?.toInt(),
                )
            } ?: emptyList()
            _state.value = _state.value.copy(albums = albums)
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-library", "loadAlbums: ${e.message}")
        }
    }

    private suspend fun loadRandomAlbumsInternal() {
        try {
            val auth = authHelper.buildAuthParams(username(), password())
            val response = api.getAlbumList2("newest", 10, 0, auth)
            val sr = response["subsonic-response"] as? Map<*, *>
            val albumList = sr?.get("albumList2") as? Map<*, *>
            val albums = (albumList?.get("album") as? List<*>)?.mapNotNull { a ->
                val m = a as? Map<*, *> ?: return@mapNotNull null
                Album(
                    id = m["id"] as? String ?: return@mapNotNull null,
                    name = m["name"] as? String ?: return@mapNotNull null,
                    artist = m["artist"] as? String,
                    coverArt = m["coverArt"] as? String,
                    rating = (m["userRating"] as? Number)?.toInt(),
                )
            } ?: emptyList()
            _state.value = _state.value.copy(randomAlbums = albums)
            refreshAlbumDownloadStatuses(albums.map { it.id })
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-library", "loadRandomAlbums: ${e.message}")
        }
    }

    fun connect(serverUrl: String, username: String, password: String) {
        // Persist all three keys in one durable write (no torn state) and apply
        // to the process globals so per-call credential reads and URL builders
        // agree. Also publish the Compose-observable snapshot.
        val normalizedUrl = serverUrl.trim().trimEnd('/')
        storage.putAll(
            mapOf(
                SecureStorage.KEY_URL to normalizedUrl,
                SecureStorage.KEY_USERNAME to username,
                SecureStorage.KEY_PASSWORD to password,
            ),
        )
        com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url = normalizedUrl
        com.lucasdss.ftpmusic.app.di.SubsonicCredentials.username = username
        com.lucasdss.ftpmusic.app.di.SubsonicCredentials.password = password
        com.lucasdss.ftpmusic.app.di.ServerConfigState.value =
            com.lucasdss.ftpmusic.app.di.ServerConfig(normalizedUrl, username, password)
        loadArtists()
        loadAlbums()
        loadRandomAlbums()
        loadRecentlyPlayed()
    }

    fun loadArtists() {
        viewModelScope.launch {
            try {
                // Load cached artists first — filter to offline-only when offline.
                // The Room read is bounded: a startup DB write storm must never
                // strand isLoading=true forever (Home eternal spinner).
                val cached = if (isOffline()) {
                    metadataDao.getOfflineArtists()
                } else {
                    kotlinx.coroutines.withTimeoutOrNull(30_000) { metadataDao.getAllArtists() }
                        ?: emptyList()
                }
                if (cached.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        artists = cached.map {
                            Artist(id = it.id, name = it.name, coverArt = it.coverArt, albumCount = it.albumCount)
                        },
                        hasLoadedOnce = true,
                    )
                } else {
                    _state.value = _state.value.copy(isLoading = true)
                }
                // Background refresh from API — skip when offline
                if (isOffline()) {
                    _state.value = _state.value.copy(isLoading = false)
                    return@launch
                }
                val auth = authHelper.buildAuthParams(username(), password())
                // Hard cap on the API fetch: the full-screen loading spinner must
                // never stick beyond this window even if a socket hangs.
                val response = withTimeoutOrNull(30_000) { api.getArtists(auth) }
                    ?: throw java.net.SocketTimeoutException("Server took too long to respond")
                val sr = response["subsonic-response"] as? Map<*, *>
                val artistsData = sr?.get("artists") as? Map<*, *>
                val indices = artistsData?.get("index") as? List<*> ?: emptyList<Any>()
                val artists = indices.flatMap { index ->
                    ((index as? Map<*, *>)?.get("artist") as? List<*>)?.mapNotNull { a ->
                        val m = a as? Map<*, *> ?: return@mapNotNull null
                        Artist(
                            id = m["id"] as? String ?: return@mapNotNull null,
                            name = m["name"] as? String ?: return@mapNotNull null,
                            coverArt = m["coverArt"] as? String,
                            albumCount = (m["albumCount"] as? Number)?.toInt(),
                        )
                    } ?: emptyList()
                }
                // Overlay albumCount from cached data — updateArtistAlbumCounts() has
                // already recomputed it from cached_albums, which is the source of
                // truth. The getArtists API count (all appearances incl. compilations)
                // would otherwise diverge from the albums actually shown.
                val cachedCounts = cached.associate { it.id to it.albumCount }
                _state.value = _state.value.copy(
                    artists = artists.map { a -> a.copy(albumCount = cachedCounts[a.id] ?: a.albumCount) },
                    isLoading = false,
                    hasLoadedOnce = true,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, isLoading = false)
            } finally {
                // Never leave the loading spinner stuck, whatever stalled.
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun loadAlbums() {
        viewModelScope.launch {
            albumOffset = 0
            alphaCatalogComplete = false
            _state.value = _state.value.copy(isLoading = true)
            try {
                // Load from local cache — filter to offline-only when offline.
                // The Room read is bounded: a startup DB write storm must never
                // strand isLoading=true forever (Home eternal spinner).
                val cached = if (isOffline()) {
                    metadataDao.getOfflineAlbums()
                } else {
                    kotlinx.coroutines.withTimeoutOrNull(30_000) { metadataDao.getAllAlbums() }
                        ?: emptyList()
                }
                if (cached.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        albums = cached.map {
                            Album(
                                id = it.id, name = it.name, artist = it.artist, artistId = it.artistId,
                                year = it.year, coverArt = it.coverArt, songCount = it.songCount,
                                duration = it.duration, genre = it.genre,
                            )
                        },
                        hasLoadedOnce = true,
                    )
                }
                // Then fetch newest 50 from API for freshness
                if (isOffline()) {
                    _state.value = _state.value.copy(isLoading = false)
                    return@launch
                }
                val auth = authHelper.buildAuthParams(username(), password())
                // Hard cap: the full-screen loading spinner must never stick
                // beyond this window even if a socket hangs.
                val response = withTimeoutOrNull(30_000) { api.getAlbumList2("newest", 50, albumOffset, auth) }
                    ?: throw java.net.SocketTimeoutException("Server took too long to respond")
                val sr = response["subsonic-response"] as? Map<*, *>
                val albumList = sr?.get("albumList2") as? Map<*, *>
                val apiAlbums = (albumList?.get("album") as? List<*>)?.mapNotNull { a ->
                    val m = a as? Map<*, *> ?: return@mapNotNull null
                    Album(
                        id = m["id"] as? String ?: return@mapNotNull null,
                        name = m["name"] as? String ?: return@mapNotNull null,
                        artist = m["artist"] as? String,
                        year = (m["year"] as? Number)?.toInt(),
                        coverArt = m["coverArt"] as? String,
                        rating = (m["userRating"] as? Number)?.toInt(),
                    )
                } ?: emptyList()
                // Merge: cached albums + API newest (deduplicated by ID, cache takes precedence)
                val cachedIds = cached.map { it.id }.toSet()
                val merged = cached.map {
                    Album(
                        id = it.id, name = it.name, artist = it.artist, artistId = it.artistId,
                        year = it.year, coverArt = it.coverArt, songCount = it.songCount,
                        duration = it.duration, genre = it.genre,
                    )
                } + apiAlbums.filter { it.id !in cachedIds }
                _state.value = _state.value.copy(albums = merged, isLoading = false, hasLoadedOnce = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, isLoading = false)
            } finally {
                // Never leave the loading spinner stuck, whatever stalled.
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun loadRandomAlbums() {
        if (isOffline()) {
            // Offline: show cached albums (shuffled) instead of nothing
            viewModelScope.launch {
                try {
                    val cached = metadataDao.getOfflineAlbums()
                    if (cached.isNotEmpty()) {
                        val albums = cached.shuffled().take(10).map { a ->
                            Album(
                                id = a.id,
                                name = a.name,
                                artist = a.artist,
                                coverArt = a.coverArt,
                                year = a.year,
                            )
                        }
                        _state.value = _state.value.copy(randomAlbums = albums)
                        refreshAlbumDownloadStatuses(albums.map { it.id })
                    }
                } catch (
                    e: Exception,
                ) {
                    android.util.Log.w("ftpmusic-library", "loadRandomAlbums offline: ${e.message}")
                }
            }
            return
        }
        viewModelScope.launch {
            try {
                val auth = authHelper.buildAuthParams(username(), password())
                val response = api.getAlbumList2("newest", 10, 0, auth)
                val sr = response["subsonic-response"] as? Map<*, *>
                val albumList = sr?.get("albumList2") as? Map<*, *>
                val albums = (albumList?.get("album") as? List<*>)?.mapNotNull { a ->
                    val m = a as? Map<*, *> ?: return@mapNotNull null
                    Album(
                        id = m["id"] as? String ?: return@mapNotNull null,
                        name = m["name"] as? String ?: return@mapNotNull null,
                        artist = m["artist"] as? String,
                        coverArt = m["coverArt"] as? String,
                        rating = (m["userRating"] as? Number)?.toInt(),
                    )
                } ?: emptyList()
                _state.value = _state.value.copy(randomAlbums = albums)
                refreshAlbumDownloadStatuses(albums.map { it.id })
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "loadRandomAlbums: ${e.message}")
            }
        }
    }

    fun refreshAlbumDownloadStatuses(albumIds: List<String>) {
        viewModelScope.launch {
            try {
                if (albumIds.isEmpty()) return@launch
                val allTrackEntities = trackDao.getTracksByAlbumIds(albumIds)
                val statusByAlbum = mutableMapOf<String, String>()
                for (albumId in albumIds) {
                    val tracks = allTrackEntities.filter { it.albumId == albumId }
                    if (tracks.isEmpty()) continue
                    val allDownloaded = tracks.all { it.isDownloaded }
                    val anyDownloaded = tracks.any { it.isDownloaded }
                    val anyCached = tracks.any { it.cachedFilePath != null }
                    statusByAlbum[albumId] = when {
                        allDownloaded -> "downloaded"
                        anyDownloaded || anyCached -> "cached"
                        else -> "none"
                    }
                }
                _state.value = _state.value.copy(downloadStatusByAlbumId = statusByAlbum)
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "refreshAlbumDownloadStatuses: ${e.message}")
            }
        }
    }

    fun loadRecentlyPlayed() {
        viewModelScope.launch { loadRecentlyPlayedInternal() }
    }

    private suspend fun loadRecentlyPlayedInternal() {
        try {
            val tracks = trackDao.getRecentlyPlayed(20)
            _state.value = _state.value.copy(recentlyPlayed = tracks, hasLoadedOnce = true)
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-library", "loadRecentlyPlayed: ${e.message}")
        }
    }

    fun loadMoreAlbums() {
        viewModelScope.launch {
            // Albums tab uses loadAlphaAlbums (full catalog). Appending
            // newest-offset pages would scramble sort and jank the grid.
            if (alphaCatalogComplete) return@launch
            if (isOffline()) return@launch
            if (isLoadingMore) return@launch
            isLoadingMore = true
            try {
                val nextOffset = albumOffset + 50
                val auth = authHelper.buildAuthParams(username(), password())
                val response = api.getAlbumList2("newest", 50, nextOffset, auth)
                val sr = response["subsonic-response"] as? Map<*, *>
                val albumList = sr?.get("albumList2") as? Map<*, *>
                val newAlbums = (albumList?.get("album") as? List<*>)?.mapNotNull { a ->
                    val m = a as? Map<*, *> ?: return@mapNotNull null
                    Album(
                        id = m["id"] as? String ?: return@mapNotNull null,
                        name = m["name"] as? String ?: return@mapNotNull null,
                        artist = m["artist"] as? String,
                        year = (m["year"] as? Number)?.toInt(),
                        coverArt = m["coverArt"] as? String,
                        rating = (m["userRating"] as? Number)?.toInt(),
                    )
                } ?: emptyList()
                albumOffset = nextOffset
                _state.value = _state.value.copy(
                    albums = _state.value.albums + newAlbums,
                )
            } catch (
                e: Exception,
            ) {
                android.util.Log.w("ftpmusic-library", "loadMoreAlbums: ${e.message}")
            } finally {
                isLoadingMore = false
            }
        }
    }

    /** Search albums via database; browse [LibraryState.albums] stays intact. */
    private var albumSearchGen = 0

    fun searchAlbums(query: String) {
        if (query.isBlank()) {
            clearAlbumSearch()
            return
        }
        val gen = ++albumSearchGen
        viewModelScope.launch {
            try {
                val results = metadataDao.searchAlbums(query)
                if (gen != albumSearchGen) return@launch
                _state.value = _state.value.copy(
                    albumSearchResults = results
                        .distinctBy { it.id }
                        .map {
                            Album(
                                id = it.id, name = it.name, artist = it.artist, artistId = it.artistId,
                                year = it.year, coverArt = it.coverArt, songCount = it.songCount,
                                duration = it.duration, genre = it.genre,
                            )
                        },
                )
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "searchAlbums DB: ${e.message}")
            }
        }
    }

    fun clearAlbumSearch() {
        albumSearchGen++
        if (_state.value.albumSearchResults != null) {
            _state.value = _state.value.copy(albumSearchResults = null)
        }
    }

    private var artistSearchGen = 0

    fun searchArtists(query: String) {
        if (query.isBlank()) {
            clearArtistSearch()
            return
        }
        val gen = ++artistSearchGen
        viewModelScope.launch {
            try {
                val results = metadataDao.searchArtists(query)
                if (gen != artistSearchGen) return@launch
                _state.value = _state.value.copy(
                    artistSearchResults = results.map {
                        Artist(id = it.id, name = it.name, coverArt = it.coverArt, albumCount = it.albumCount)
                    },
                )
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "searchArtists DB: ${e.message}")
            }
        }
    }

    fun clearArtistSearch() {
        artistSearchGen++
        if (_state.value.artistSearchResults != null) {
            _state.value = _state.value.copy(artistSearchResults = null)
        }
    }

    /** Load all albums alphabetically. DB-first, background API refresh. */
    fun loadAlphaAlbums() {
        // Immediate: load from cached_albums DB table (filter to offline-only when offline)
        viewModelScope.launch {
            val cached = if (isOffline()) metadataDao.getOfflineAlbums() else metadataDao.getAllAlbums()
            if (cached.isNotEmpty()) {
                val albums = cached.map { c ->
                    Album(
                        id = c.id, name = c.name, artist = c.artist, artistId = c.artistId,
                        year = c.year, coverArt = c.coverArt, songCount = c.songCount,
                        duration = c.duration, genre = c.genre,
                    )
                }
                alphaCatalogComplete = true
                _state.value = _state.value.copy(albums = albums)
            }
        }
        // Background: refresh from API (skip when offline)
        viewModelScope.launch {
            if (isOffline()) return@launch
            try {
                val auth = authHelper.buildAuthParams(username(), password())
                val allAlbums = mutableListOf<Album>()
                var offset = 0
                while (true) {
                    val response = api.getAlbumList2("alphabeticalByName", 500, offset, auth)
                    val sr = response["subsonic-response"] as? Map<*, *>
                    if (sr?.get("status") as? String == "failed") break
                    val albumList = sr?.get("albumList2") as? Map<*, *>
                    val batch = albumList?.get("album") as? List<*>
                    if (batch.isNullOrEmpty()) break
                    batch.forEach { a ->
                        val m = a as? Map<*, *> ?: return@forEach
                        val id = m["id"] as? String ?: return@forEach
                        allAlbums.add(
                            Album(
                                id = id,
                                name = m["name"] as? String ?: return@forEach,
                                artist = m["artist"] as? String,
                                coverArt = m["coverArt"] as? String,
                                rating = (m["userRating"] as? Number)?.toInt(),
                            ),
                        )
                    }
                    if (batch.size < 500) break
                    offset += 500
                }
                if (allAlbums.isEmpty()) return@launch
                alphaAlbums = allAlbums
                alphaCatalogComplete = true
                _state.value = _state.value.copy(albums = allAlbums)
                lastAlphaSync = System.currentTimeMillis()
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-library", "loadAlphaAlbums: ${e.message}")
            }
        }
    }

    private var surpriseMeLoading = false

    private val _showOverwriteModal = kotlinx.coroutines.flow.MutableStateFlow(false)
    val showOverwriteModal: kotlinx.coroutines.flow.StateFlow<Boolean> = _showOverwriteModal

    fun resolveOverwrite(clearAndPlay: Boolean) {
        _showOverwriteModal.value = false
        playbackManager.resolveOverwrite(clearAndPlay)
    }

    /**
     * Fetch 50 random tracks from Navidrome and play them.
     * Smart append: if nothing is playing, replace queue and start playback.
     * If already playing, append to end without interrupting.
     */
    fun playSurpriseMe() {
        if (surpriseMeLoading) return
        surpriseMeLoading = true
        viewModelScope.launch {
            // Offline: play locally cached random tracks instead of the API.
            if (isOffline()) {
                surpriseMeOffline()
                return@launch
            }
            try {
                val auth = authHelper.buildAuthParams(username(), password())
                val response = api.getRandomSongs(auth, size = 50)
                val sr = response["subsonic-response"] as? Map<*, *> ?: return@launch
                val randomSongs = sr["randomSongs"] as? Map<*, *> ?: return@launch
                val songs = randomSongs["song"] as? List<*> ?: return@launch

                val tracks = songs.mapNotNull { s ->
                    val m = s as? Map<*, *> ?: return@mapNotNull null
                    val id = m["id"] as? String ?: return@mapNotNull null
                    Track(
                        id = id,
                        title = m["title"] as? String ?: "",
                        artist = m["artist"] as? String,
                        album = m["album"] as? String,
                        albumId = m["albumId"] as? String,
                        artistId = m["artistId"] as? String,
                        duration = (m["duration"] as? Number)?.toInt(),
                        coverArt = m["coverArt"] as? String,
                        trackNumber = (m["track"] as? Number)?.toInt(),
                        contentType = m["contentType"] as? String,
                        suffix = m["suffix"] as? String,
                    )
                }

                if (tracks.isEmpty()) return@launch

                val baseUrl = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
                val urls = tracks.map { track ->
                    authHelper.buildStreamUrl(baseUrl, track.id, username(), password())
                }

                // Surprise Me always starts a NEW context (like Play All) — never
                // silently appends. tryStartContext applies the overwrite behavior:
                // Ask (modal), Clean (replace), Push (new context first, old queue after).
                val started = playbackManager.tryStartContext(
                    tracks,
                    urls,
                    sourceType = "random",
                    sourceId = "surprise-me",
                    sourceName = "Surprise Me",
                )
                if (!started) _showOverwriteModal.value = true

                android.util.Log.d("ftpmusic", "[SurpriseMe] Loaded ${tracks.size} random tracks")
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[SurpriseMe] failed: ${e.message}")
            } finally {
                surpriseMeLoading = false
            }
        }
    }

    /**
     * Offline fallback for Surprise Me: plays up to 50 locally CACHED random
     * tracks (no network). Always starts a NEW context through the overwrite
     * protection — the background refill path (maybeRefillRandomQueue) has its
     * own inline append and never routes through here.
     */
    private fun surpriseMeOffline() {
        viewModelScope.launch {
            try {
                val entities = trackDao.getRandomCachedTracks(50)
                if (entities.isEmpty()) {
                    android.util.Log.w("ftpmusic", "[SurpriseMe] offline: no cached tracks available")
                    return@launch
                }
                val tracks = entities.map { e ->
                    Track(
                        id = e.id, title = e.title, artist = e.artist,
                        albumId = e.albumId, artistId = e.artistId,
                        duration = e.durationSeconds, coverArt = e.coverArtUrl,
                        contentType = e.contentType, suffix = e.suffix,
                    )
                }
                val baseUrl = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
                val urls = tracks.map { authHelper.buildStreamUrl(baseUrl, it.id, username(), password()) }
                val started = playbackManager.tryStartContext(
                    tracks,
                    urls,
                    sourceType = "random",
                    sourceId = "surprise-me",
                    sourceName = "Surprise Me",
                )
                if (!started) _showOverwriteModal.value = true
                android.util.Log.d("ftpmusic", "[SurpriseMe] offline: ${tracks.size} cached tracks")
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[SurpriseMe] offline failed: ${e.message}")
            } finally {
                surpriseMeLoading = false
            }
        }
    }

    /**
     * Album quick actions from the Home screen card sheet: queue or download an
     * album by id. Fetches the album's tracks from the local DB, builds stream
     * URLs, then delegates to PlaybackManager (queue) or DownloadManager (download).
     */
    fun albumAction(albumId: String, action: String) {
        viewModelScope.launch {
            try {
                val entities = trackDao.getTracksByAlbumIds(listOf(albumId))
                if (entities.isEmpty()) return@launch
                val tracks = entities.map { e ->
                    Track(
                        id = e.id, title = e.title, artist = e.artist,
                        albumId = e.albumId, artistId = e.artistId,
                        duration = e.durationSeconds, coverArt = e.coverArtUrl,
                        contentType = e.contentType, suffix = e.suffix,
                    )
                }
                val baseUrl = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
                val urls = tracks.map { authHelper.buildStreamUrl(baseUrl, it.id, username(), password()) }
                when (action) {
                    "queue" -> tracks.zip(urls).forEach { (t, u) -> playbackManager.addToQueue(t, u) }

                    "playnext" -> tracks.zip(urls).reversed().forEach { (t, u) -> playbackManager.playNext(t, u) }

                    "download" -> if (com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.isConfigured()) {
                        tracks.forEach { t ->
                            try {
                                downloadManager.enqueue(t.id, baseUrl + "/rest/stream?id=" + t.id, priority = 1)
                            } catch (_: Exception) {}
                        }
                    } else {
                        android.util.Log.w("ftpmusic-home", "albumAction download skipped — server not configured")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-home", "albumAction $action failed: ${e.message}")
            }
        }
    }

    /**
     * Refill the random queue when it drops below 10 tracks.
     * Only active if surprise me playback was previously initiated.
     */
    fun maybeRefillRandomQueue() {
        val queueSize = PlayerHolder.exoPlayer?.mediaItemCount ?: return
        if (queueSize >= 10) return
        if (surpriseMeLoading) return
        surpriseMeLoading = true
        // Offline: refill from locally cached random tracks (no network).
        // Appends — a background refill must never replace the playing queue.
        if (isOffline()) {
            viewModelScope.launch {
                try {
                    val entities = trackDao.getRandomCachedTracks(50)
                    if (entities.isNotEmpty()) {
                        val tracks = entities.map { e ->
                            Track(
                                id = e.id, title = e.title, artist = e.artist,
                                albumId = e.albumId, artistId = e.artistId,
                                duration = e.durationSeconds, coverArt = e.coverArtUrl,
                                contentType = e.contentType, suffix = e.suffix,
                            )
                        }
                        val baseUrl = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
                        val urls = tracks.map { authHelper.buildStreamUrl(baseUrl, it.id, username(), password()) }
                        playbackManager.addAllToQueue(tracks, urls)
                        android.util.Log.d("ftpmusic", "[SurpriseMe] offline refill: ${tracks.size} cached tracks")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ftpmusic", "[SurpriseMe] offline refill failed: ${e.message}")
                } finally {
                    surpriseMeLoading = false
                }
            }
            return
        }
        viewModelScope.launch {
            try {
                val auth = authHelper.buildAuthParams(username(), password())
                val response = api.getRandomSongs(auth, size = 50)
                val sr = response["subsonic-response"] as? Map<*, *> ?: return@launch
                val randomSongs = sr["randomSongs"] as? Map<*, *> ?: return@launch
                val songs = randomSongs["song"] as? List<*> ?: return@launch

                val tracks = songs.mapNotNull { s ->
                    val m = s as? Map<*, *> ?: return@mapNotNull null
                    val id = m["id"] as? String ?: return@mapNotNull null
                    Track(
                        id = id,
                        title = m["title"] as? String ?: "",
                        artist = m["artist"] as? String,
                        album = m["album"] as? String,
                        albumId = m["albumId"] as? String,
                        artistId = m["artistId"] as? String,
                        duration = (m["duration"] as? Number)?.toInt(),
                        coverArt = m["coverArt"] as? String,
                        trackNumber = (m["track"] as? Number)?.toInt(),
                        contentType = m["contentType"] as? String,
                        suffix = m["suffix"] as? String,
                    )
                }

                if (tracks.isEmpty()) return@launch

                val baseUrl = com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url.trimEnd('/')
                val urls = tracks.map { track ->
                    authHelper.buildStreamUrl(baseUrl, track.id, username(), password())
                }

                playbackManager.addAllToQueue(tracks, urls)
                android.util.Log.d("ftpmusic", "[SurpriseMe] Refilled ${tracks.size} random tracks")
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[SurpriseMe] Refill failed: ${e.message}")
            } finally {
                surpriseMeLoading = false
            }
        }
    }

    fun getUsername(): String? = username().ifEmpty { null }

    fun getPassword(): String? = password().ifEmpty { null }
}
