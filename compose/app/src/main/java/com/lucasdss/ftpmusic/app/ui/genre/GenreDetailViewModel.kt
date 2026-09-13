package com.lucasdss.ftpmusic.app.ui.genre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.data.model.Artist
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GenreDetailState(
    val genre: String = "",
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val tab: GenreTab = GenreTab.ALBUMS,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

enum class GenreTab { ALBUMS, ARTISTS }

@HiltViewModel
class GenreDetailViewModel @Inject constructor(private val api: SubsonicApi, private val storage: SecureStorage) :
    ViewModel() {

    private val _state = MutableStateFlow(GenreDetailState())
    val state: StateFlow<GenreDetailState> = _state.asStateFlow()
    private val auth = SubsonicAuthHelper()
    private var offset = 0
    private val pageSize = 100

    fun loadGenre(genre: String) {
        if (genre == _state.value.genre && _state.value.albums.isNotEmpty()) return
        offset = 0
        _state.value = _state.value.copy(genre = genre, isLoading = true, hasMore = true)
        fetchPage()
    }

    fun loadMore() {
        if (_state.value.isLoadingMore || !_state.value.hasMore) return
        _state.value = _state.value.copy(isLoadingMore = true)
        fetchPage()
    }

    private fun fetchPage() {
        viewModelScope.launch {
            try {
                val user = storage.get(SecureStorage.KEY_USERNAME) ?: ""
                val pass = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
                if (user.isEmpty()) {
                    _state.value = _state.value.copy(isLoading = false, isLoadingMore = false)
                    return@launch
                }
                val params = auth.buildAuthParams(user, pass)
                val response = api.getSongsByGenre(
                    params,
                    genre = _state.value.genre,
                    count = pageSize,
                    offset = offset,
                )
                val sr = response["subsonic-response"] as? Map<*, *>
                val songsByGenre = sr?.get("songsByGenre") as? Map<*, *>
                val songs = songsByGenre?.get("song") as? List<*>

                val previousAlbumCount = _state.value.albums.size
                val previousArtistCount = _state.value.artists.size

                val newAlbums = mutableMapOf<String, Album>()
                val newArtists = mutableMapOf<String, Artist>()

                songs?.forEach { s ->
                    val m = s as? Map<*, *> ?: return@forEach
                    val albumId = m["albumId"] as? String
                    val albumName = m["album"] as? String
                    val artistName = m["artist"] as? String
                    val artistId = m["artistId"] as? String
                    val coverArt = m["coverArt"] as? String
                    val year = (m["year"] as? Number)?.toInt()

                    if (albumId != null && albumId !in newAlbums &&
                        albumId !in _state.value.albums.associateBy { it.id }
                    ) {
                        newAlbums[albumId] =
                            Album(
                                id = albumId,
                                name = albumName ?: "",
                                artist = artistName,
                                artistId = artistId,
                                year = year,
                                coverArt = coverArt,
                            )
                    }
                    if (artistId != null && artistId !in newArtists &&
                        artistId !in _state.value.artists.associateBy { it.id }
                    ) {
                        newArtists[artistId] = Artist(id = artistId, name = artistName ?: "", albumCount = null)
                    }
                }

                val songCount = songs?.size ?: 0
                offset += pageSize

                val updatedAlbums = _state.value.albums + newAlbums.values
                val updatedArtists = _state.value.artists + newArtists.values

                _state.value = _state.value.copy(
                    albums = updatedAlbums,
                    artists = updatedArtists,
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = songCount >= pageSize && (songs != null) &&
                        (updatedAlbums.size > previousAlbumCount || updatedArtists.size > previousArtistCount),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, isLoading = false, isLoadingMore = false)
            }
        }
    }

    fun setTab(tab: GenreTab) {
        _state.value = _state.value.copy(tab = tab)
    }
}
