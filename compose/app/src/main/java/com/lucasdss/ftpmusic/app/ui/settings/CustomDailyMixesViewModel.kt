package com.lucasdss.ftpmusic.app.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedGenreEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.repository.CustomMix
import com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository
import com.lucasdss.ftpmusic.app.data.repository.MixFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One artist search result row. */
@Immutable
data class ArtistOption(val id: String, val name: String, val isFavorite: Boolean)

/**
 * Ephemeral editor state for one Custom Daily Mix (new or existing).
 * Dimensions are cumulative: switching tabs never clears picks.
 */
@Immutable
data class MixEditorState(
    val mixId: Long? = null,
    val name: String = "",
    val activeTab: String = DailyMixRepository.KIND_GENRES,
    val genres: Set<String> = emptySet(),
    val decades: Set<String> = emptySet(),
    val artistIds: Set<String> = emptySet(),
    val includeFavoriteArtists: Boolean = false,
    val artistQuery: String = "",
    val artistResults: List<ArtistOption> = emptyList(),
    val artistResultsExhausted: Boolean = false,
    val autoCache: Boolean = false,
    val pendingDelete: Boolean = false,
) {
    val isNew: Boolean get() = mixId == null
}

@Immutable
data class CustomDailyMixesUiState(
    val mixes: List<CustomMix> = emptyList(),
    val genres: List<CachedGenreEntity> = emptyList(),
    val genreDisplayCount: Int = CustomDailyMixesViewModel.GENRE_PAGE,
    val likedArtists: List<String> = emptyList(),
    val likedArtistIds: Set<String> = emptySet(),
    /** Selected artist id → display name (chips). */
    val selectedArtistNames: Map<String, String> = emptyMap(),
    /** Mix id → genre names that no longer exist in the library. */
    val missingGenres: Map<Long, List<String>> = emptyMap(),
    val editor: MixEditorState? = null,
    val loading: Boolean = true,
    val isSaving: Boolean = false,
) {
    val atCapacity: Boolean get() = mixes.size >= DailyMixRepository.MAX_MIXES
}

/**
 * Settings page for user-managed Custom Daily Mixes: cumulative composite
 * filters (genres + decades + artists, AND across dimensions), paged genre
 * picker, searchable artist library and the legacy dynamic favorites toggle.
 */
@HiltViewModel
class CustomDailyMixesViewModel @Inject constructor(
    private val repository: DailyMixRepository,
    private val metadataDao: CachedMetadataDao,
) : ViewModel() {

    companion object {
        const val GENRE_PAGE = 20
        const val ARTIST_PAGE = 50
        const val ARTIST_SEARCH_DEBOUNCE_MS = 250L

        val MOOD_SUGGESTIONS = listOf(
            "Chill Mode", "Focus Flow", "Late Night", "Morning Boost",
            "Workout", "Party Mix", "Road Trip", "Deep Dive", "Throwback",
        )
    }

    private val _state = MutableStateFlow(CustomDailyMixesUiState())
    val state: StateFlow<CustomDailyMixesUiState> = _state.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var artistSearchJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val mixes = repository.getAll()
            val genres = repository.allGenres()
            val liked = metadataDao.getAllStarredArtists()
            _state.value = _state.value.copy(
                mixes = mixes,
                genres = genres,
                likedArtists = liked.map { it.name },
                likedArtistIds = liked.map { it.id }.toSet(),
                missingGenres = repository.missingSourceGenres(),
                loading = false,
            )
        }
    }

    // ── List actions ─────────────────────────────────────────────────────

    fun openNew() {
        if (_state.value.atCapacity) return
        _state.value = _state.value.copy(
            editor = MixEditorState(),
            selectedArtistNames = emptyMap(),
            genreDisplayCount = GENRE_PAGE,
        )
    }

    fun openEditor(mix: CustomMix) {
        _state.value = _state.value.copy(
            selectedArtistNames = emptyMap(),
            genreDisplayCount = GENRE_PAGE,
            editor = MixEditorState(
                mixId = mix.id,
                name = mix.name,
                activeTab = tabFor(mix.filters),
                genres = mix.filters.genres.toSet(),
                decades = mix.filters.decades.toSet(),
                artistIds = mix.filters.artistIds.toSet(),
                includeFavoriteArtists = mix.filters.includeFavoriteArtists,
                autoCache = mix.autoCache,
            ),
        )
        val ids = mix.filters.artistIds
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                val names = ids.chunked(DailyMixRepository.SQL_CHUNK)
                    .flatMap { metadataDao.getArtistsByIds(it) }
                    .associate { it.id to it.name }
                _state.value = _state.value.copy(selectedArtistNames = _state.value.selectedArtistNames + names)
            }
        }
    }

    private fun tabFor(filters: MixFilters): String = when {
        filters.genres.isNotEmpty() -> DailyMixRepository.KIND_GENRES
        filters.decades.isNotEmpty() -> DailyMixRepository.KIND_DECADES
        else -> DailyMixRepository.KIND_FAVORITE_ARTISTS
    }

    fun closeEditor() {
        artistSearchJob?.cancel()
        _state.value = _state.value.copy(editor = null)
    }

    /** Immediate delete from the list (design: X deletes without confirmation). */
    fun delete(mix: CustomMix) {
        viewModelScope.launch {
            repository.deleteMix(mix.id)
            _toast.value = "\"${mix.name}\" removed"
            load()
        }
    }

    // ── Editor: common ───────────────────────────────────────────────────

    fun setName(name: String) = updateEditor { it.copy(name = name.take(DailyMixRepository.NAME_MAX)) }

    /** Tabs are cumulative — switching never clears the other dimensions. */
    fun setActiveTab(tab: String) = updateEditor { it.copy(activeTab = tab) }

    fun setAutoCache(enabled: Boolean) = updateEditor { it.copy(autoCache = enabled) }

    fun requestDelete() = updateEditor { it.copy(pendingDelete = true) }

    fun cancelDelete() = updateEditor { it.copy(pendingDelete = false) }

    fun confirmDelete() {
        val editor = _state.value.editor ?: return
        val mixId = editor.mixId ?: return
        val name = editor.name
        artistSearchJob?.cancel()
        viewModelScope.launch {
            repository.deleteMix(mixId)
            _toast.value = "\"$name\" removed"
            _state.value = _state.value.copy(editor = null)
            load()
        }
    }

    // ── Editor: genres ───────────────────────────────────────────────────

    fun toggleGenre(genre: String) = updateEditor { editor ->
        val next = if (genre in editor.genres) editor.genres - genre else editor.genres + genre
        editor.copy(genres = next)
    }

    /** "Load more": 20 more genres per tap (selected genres are always shown). */
    fun loadMoreGenres() {
        _state.value = _state.value.copy(
            genreDisplayCount = _state.value.genreDisplayCount + GENRE_PAGE,
        )
    }

    /** Selected genres pinned first, then unselected up to the page window. */
    fun visibleGenres(editor: MixEditorState): List<CachedGenreEntity> {
        val state = _state.value
        val selected = state.genres.filter { it.name in editor.genres }
        val rest = state.genres.filter { it.name !in editor.genres }.take(state.genreDisplayCount)
        return selected + rest
    }

    /** True while unselected genres remain beyond the page window. */
    fun hasMoreGenres(editor: MixEditorState): Boolean = remainingGenres(editor) > 0

    /** Unselected genres still hidden behind "Load more". */
    fun remainingGenres(editor: MixEditorState): Int {
        val state = _state.value
        val unselected = state.genres.count { it.name !in editor.genres }
        return (unselected - state.genreDisplayCount).coerceAtLeast(0)
    }

    // ── Editor: decades ──────────────────────────────────────────────────

    fun toggleDecade(decade: String) = updateEditor { editor ->
        val next = if (decade in editor.decades) editor.decades - decade else editor.decades + decade
        editor.copy(decades = next)
    }

    // ── Editor: artists ──────────────────────────────────────────────────

    fun setArtistQuery(query: String) {
        // Clear the previous page immediately: stale rows must not linger
        // during the debounce, and the next page offset must start at zero.
        updateEditor {
            it.copy(artistQuery = query, artistResults = emptyList(), artistResultsExhausted = false)
        }
        artistSearchJob?.cancel()
        artistSearchJob = viewModelScope.launch {
            delay(ARTIST_SEARCH_DEBOUNCE_MS)
            val results = metadataDao.searchArtistsPaged(query, ARTIST_PAGE, 0)
            updateEditor { editor ->
                if (editor.artistQuery != query) {
                    editor
                } else {
                    editor.copy(
                        artistResults = results.map { it.toOption() },
                        artistResultsExhausted = results.size < ARTIST_PAGE,
                    )
                }
            }
        }
    }

    fun loadMoreArtists() {
        val editor = _state.value.editor ?: return
        if (editor.artistResultsExhausted) return
        val query = editor.artistQuery
        val offset = editor.artistResults.size
        artistSearchJob?.cancel()
        artistSearchJob = viewModelScope.launch {
            val results = metadataDao.searchArtistsPaged(query, ARTIST_PAGE, offset)
            updateEditor { current ->
                if (current.artistQuery != query) {
                    current
                } else {
                    current.copy(
                        artistResults = current.artistResults + results.map { it.toOption() },
                        artistResultsExhausted = results.size < ARTIST_PAGE,
                    )
                }
            }
        }
    }

    fun toggleArtist(artist: ArtistOption) {
        updateEditor { editor ->
            val next = if (artist.id in editor.artistIds) {
                editor.artistIds - artist.id
            } else {
                editor.artistIds + artist.id
            }
            editor.copy(artistIds = next)
        }
        _state.value = _state.value.copy(
            selectedArtistNames = _state.value.selectedArtistNames + (artist.id to artist.name),
        )
    }

    fun setIncludeFavoriteArtists(enabled: Boolean) = updateEditor { it.copy(includeFavoriteArtists = enabled) }

    // ── Editor: save ─────────────────────────────────────────────────────

    fun canSave(editor: MixEditorState): Boolean {
        if (editor.name.isBlank()) return false
        val hasArtists = editor.artistIds.isNotEmpty() ||
            (editor.includeFavoriteArtists && _state.value.likedArtists.isNotEmpty())
        return editor.genres.isNotEmpty() || editor.decades.isNotEmpty() || hasArtists
    }

    fun save() {
        val editor = _state.value.editor ?: return
        if (_state.value.isSaving) return
        if (!canSave(editor)) return
        artistSearchJob?.cancel()
        val filters = MixFilters(
            genres = editor.genres.toList(),
            decades = editor.decades.toList(),
            artistIds = editor.artistIds.toList(),
            includeFavoriteArtists = editor.includeFavoriteArtists,
        )
        val name = editor.name.trim()
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            try {
                val mixId = editor.mixId
                if (mixId == null) {
                    val id = repository.addMix(name, filters, editor.autoCache)
                    if (id == null) {
                        // Cap reached (or a racing save won) — keep the editor
                        // open so the typed recipe is not silently lost.
                        _toast.value = "Limit reached (${DailyMixRepository.MAX_MIXES}) — delete a mix first"
                        return@launch
                    }
                    _toast.value = "\"$name\" added"
                } else {
                    repository.updateMix(mixId, name, filters, editor.autoCache)
                    _toast.value = "\"$name\" saved"
                }
                _state.value = _state.value.copy(editor = null)
                load()
            } finally {
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    /** Context-sensitive name suggestions for the ACTIVE tab. */
    fun suggestions(editor: MixEditorState): List<String> {
        val moods = MOOD_SUGGESTIONS
        return when (editor.activeTab) {
            DailyMixRepository.KIND_GENRES -> {
                if (editor.genres.isEmpty()) {
                    moods
                } else {
                    (editor.genres.map { "$it Mix" } + moods).distinct().take(10)
                }
            }

            DailyMixRepository.KIND_DECADES -> {
                if (editor.decades.isEmpty()) {
                    moods
                } else {
                    val sorted = DailyMixRepository.DECADE_RANGES.keys.filter { it in editor.decades }
                    val labels = if (sorted.size == 1) {
                        listOf("${sorted[0]} Hits", "${sorted[0]} Classics", "${sorted[0]} Gold")
                    } else {
                        listOf("${sorted.first()}–${sorted.last()} Mix", "Throwback Mix", "Retro Blend")
                    }
                    (labels + moods).distinct().take(10)
                }
            }

            else -> (listOf("My Artists Mix", "Artist Showcase", "Handpicked") + moods).distinct().take(8)
        }
    }

    fun clearToast() {
        _toast.value = null
    }

    private fun CachedArtistEntity.toOption(): ArtistOption =
        ArtistOption(id = id, name = name, isFavorite = id in _state.value.likedArtistIds)

    private inline fun updateEditor(transform: (MixEditorState) -> MixEditorState) {
        val editor = _state.value.editor ?: return
        _state.value = _state.value.copy(editor = transform(editor))
    }
}
