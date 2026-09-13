package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*

/**
 * Play tracking + Subsonic scrobbling service.
 * Increments local play_count, reports now-playing / scrobble to Subsonic server.
 *
 * Local-first: the local play count / track row is ALWAYS written (survives
 * offline); the server API call is skipped while software offline mode is on.
 */
@Singleton
class ScrobbleService @Inject constructor(
    private val trackDao: TrackDao,
    private val api: SubsonicApi,
    private val storage: SecureStorage,
    private val offlineModeManager: OfflineModeManager,
) {
    private val auth = SubsonicAuthHelper()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private suspend fun authParams(): Map<String, String> {
        val user = storage.get(SecureStorage.KEY_USERNAME) ?: ""
        val pass = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
        return auth.buildAuthParams(user, pass)
    }

    /** Report now-playing to Subsonic server and ensure local track row exists. */
    fun nowPlaying(
        trackId: String,
        title: String? = null,
        artist: String? = null,
        albumId: String? = null,
        artistId: String? = null,
        durationSeconds: Int? = null,
        coverArtUrl: String? = null,
        genre: String? = null,
    ) {
        scope.launch {
            if (!offlineModeManager.isOfflineEnabled()) {
                try {
                    api.scrobble(authParams(), id = trackId, submission = false)
                } catch (
                    e: Exception,
                ) {
                    android.util.Log.w("ftpmusic-scrobble", "API call failed: " + e.message)
                }
            }
            ensureTrackRow(trackId, title, artist, albumId, artistId, durationSeconds, coverArtUrl, genre)
        }
    }

    /** Report a completed listen and increment local play count. */
    fun scrobble(
        trackId: String,
        title: String? = null,
        artist: String? = null,
        albumId: String? = null,
        artistId: String? = null,
        durationSeconds: Int? = null,
        coverArtUrl: String? = null,
        genre: String? = null,
    ) {
        scope.launch {
            if (!offlineModeManager.isOfflineEnabled()) {
                try {
                    api.scrobble(authParams(), id = trackId, submission = true)
                } catch (
                    e: Exception,
                ) {
                    android.util.Log.w("ftpmusic-scrobble", "API call failed: " + e.message)
                }
            }
            ensureTrackRow(trackId, title, artist, albumId, artistId, durationSeconds, coverArtUrl, genre)
            trackDao.incrementPlayCount(trackId)
        }
    }

    private suspend fun ensureTrackRow(
        trackId: String,
        title: String?,
        artist: String?,
        albumId: String?,
        artistId: String?,
        durationSeconds: Int?,
        coverArtUrl: String?,
        genre: String? = null,
    ) {
        // Always upsert — CacheService may have created a stub row with title=trackId
        // PlaybackState metadata from the server is authoritative
        // CRITICAL: read existing play_count to avoid erasing cumulative plays.
        val existing = trackDao.getTrack(trackId)
        trackDao.upsert(
            TrackEntity(
                id = trackId,
                title = title ?: trackId,
                artist = artist,
                genre = genre,
                albumId = albumId,
                artistId = artistId,
                durationSeconds = durationSeconds,
                coverArtUrl = coverArtUrl,
                playCount = existing?.playCount ?: 0,
            ),
        )
    }

    /** Save the play queue to the Subsonic server (fire-and-forget). */
    fun savePlayQueue(auth: Map<String, String>, ids: String, current: String?, position: Long?) {
        scope.launch {
            try {
                api.savePlayQueue(auth, ids, current, position)
            } catch (
                e: Exception,
            ) {
                android.util.Log.w("ftpmusic-scrobble", "API call failed: " + e.message)
            }
        }
    }

    /** Track a play: increment local count and update timestamp. */
    fun trackPlay(trackId: String) {
        scope.launch {
            trackDao.incrementPlayCount(trackId)
        }
    }

    /** Fetch similar songs from Subsonic (instant mix / continuous play). */
    suspend fun fetchSimilarSongs(trackId: String, count: Int = 10): List<Track> {
        return try {
            val response = api.getSimilarSongs2(authParams(), id = trackId, count = count)
            val sr = response["subsonic-response"] as? Map<*, *>
            val similar = sr?.get("similarSongs2") as? Map<*, *>
            val songs = similar?.get("song") as? List<*>
            songs?.mapNotNull { song ->
                val s = song as? Map<*, *> ?: return@mapNotNull null
                Track(
                    id = s["id"] as? String ?: return@mapNotNull null,
                    title = s["title"] as? String ?: return@mapNotNull null,
                    artist = s["artist"] as? String,
                    album = s["album"] as? String,
                    duration = (s["duration"] as? Number)?.toInt(),
                    coverArt = s["coverArt"] as? String,
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
