package com.lucasdss.ftpmusic.app.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared host for Save-to-playlist from Now Playing / Search (and other
 * surfaces that should not pull LibraryViewModel). Room-only reads (ADR-0015).
 */
@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<PlaylistPickerItem>>(emptyList())
    val playlists: StateFlow<List<PlaylistPickerItem>> = _playlists.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun clearToast() {
        _toast.value = null
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                // User playlists only — Daily Mixes live in Mix tables, not here.
                _playlists.value = playlistDao.getAll().map {
                    PlaylistPickerItem(id = it.id, name = it.name, trackCount = it.trackCount)
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-playlist", "loadPlaylists failed: ${e.message}")
                _playlists.value = emptyList()
            }
        }
    }

    fun addTracks(playlistId: String, trackIds: List<String>) {
        if (playlistId.isBlank() || trackIds.isEmpty()) return
        viewModelScope.launch {
            try {
                playlistRepository.addToPlaylist(playlistId, trackIds)
                val name = _playlists.value.find { it.id == playlistId }?.name ?: "playlist"
                _toast.value = "Added to $name"
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-playlist", "addTracks failed: ${e.message}")
                _toast.value = "Could not add to playlist"
            }
        }
    }
}
