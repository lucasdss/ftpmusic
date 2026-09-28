package com.lucasdss.ftpmusic.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.db.ListenEventDao
import com.lucasdss.ftpmusic.app.data.db.RecentListenRow
import com.lucasdss.ftpmusic.app.data.db.TopCountRow
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileSummary(
    val listeningMinutes: Int = 0,
    val plays: Int = 0,
    val songCount: Int = 0,
    val artistCount: Int = 0,
    val streakDays: Int = 0,
)

data class ProfileState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val summary: ProfileSummary = ProfileSummary(),
    val topTracks: List<TopCountRow> = emptyList(),
    val topArtists: List<TopCountRow> = emptyList(),
    val topAlbums: List<TopCountRow> = emptyList(),
    val topGenres: List<TopCountRow> = emptyList(),
    val recentlyPlayed: List<TrackEntity> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val listenEventDao: ListenEventDao,
    private val trackDao: TrackDao,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun setPeriod(period: StatsPeriod) {
        if (_state.value.period == period) return
        _state.value = _state.value.copy(period = period)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val period = _state.value.period
                val (startMs, endMs) = ProfileStatsMath.periodBounds(period)
                val seconds = listenEventDao.sumListenedSeconds(startMs, endMs)
                val plays = listenEventDao.countPlays(startMs, endMs)
                val songs = listenEventDao.countDistinctTracks(startMs, endMs)
                val artists = listenEventDao.countDistinctArtists(startMs, endMs)
                val streak = ProfileStatsMath.computeStreakDays(listenEventDao.distinctListenDays())
                val tops = listenEventDao.topTracks(startMs, endMs)
                val topArtists = listenEventDao.topArtists(startMs, endMs)
                val topAlbums = listenEventDao.topAlbums(startMs, endMs)
                val topGenres = listenEventDao.topGenres(startMs, endMs)
                val recentRows = listenEventDao.recentlyPlayed(startMs, endMs, 20)
                val recentTracks = resolveRecentTracks(recentRows)
                _state.value = _state.value.copy(
                    summary = ProfileSummary(
                        listeningMinutes = (seconds / 60L).toInt(),
                        plays = plays,
                        songCount = songs,
                        artistCount = artists,
                        streakDays = streak,
                    ),
                    topTracks = tops,
                    topArtists = topArtists,
                    topAlbums = topAlbums,
                    topGenres = topGenres,
                    recentlyPlayed = recentTracks,
                    isLoading = false,
                )
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-profile", "refresh: ${e.message}")
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    private suspend fun resolveRecentTracks(rows: List<RecentListenRow>): List<TrackEntity> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.trackId }
        val byId = trackDao.getTracksByIds(ids).associateBy { it.id }
        return rows.map { row ->
            byId[row.trackId] ?: TrackEntity(
                id = row.trackId,
                title = row.trackTitle ?: row.trackId,
                artist = row.artistName,
                durationSeconds = row.listenedSeconds,
                lastPlayedAt = row.listenedAt,
            )
        }
    }
}
