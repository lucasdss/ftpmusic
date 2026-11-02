package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.ListenEventDao
import com.lucasdss.ftpmusic.app.data.db.ListenEventEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*

/**
 * Play tracking + Subsonic scrobbling service.
 * Increments local play_count, inserts listen_events, reports to Subsonic.
 *
 * Local-first: play count + listen event ALWAYS written (survives offline);
 * server API call skipped while software offline mode is on.
 */
@Singleton
class ScrobbleService @Inject constructor(
    private val trackDao: TrackDao,
    private val listenEventDao: ListenEventDao,
    private val api: SubsonicApi,
    private val storage: SecureStorage,
    private val offlineModeManager: OfflineModeManager,
) {
    private val auth = SubsonicAuthHelper()

    /**
     * Process-scoped (MET-1): never cancel this from a service/feature
     * lifecycle. A CoroutineExceptionHandler prevents an uncaught Room/API
     * failure from crashing the process (MET-8); cancellation is rethrown by
     * callers so structured concurrency still works.
     */
    private val scope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.IO +
            CoroutineExceptionHandler { _, throwable ->
                if (throwable is CancellationException) return@CoroutineExceptionHandler
                android.util.Log.e("ftpmusic-scrobble", "Uncaught scrobble failure", throwable)
                DiagnosticLog.e("ftpmusic-scrobble", "uncaught: ${throwable.message}", throwable)
            },
    )

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
            // Local first (MET-2): never let a slow/failed API call delay or
            // lose the metadata row that like/dislike/rating depend on.
            ensureTrackRow(
                trackId, title, artist, albumId, artistId, durationSeconds, coverArtUrl, genre,
                album = null,
            )
            if (!offlineModeManager.isOfflineEnabled()) {
                try {
                    api.scrobble(authParams(), id = trackId, submission = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("ftpmusic-scrobble", "API call failed: " + e.message)
                }
            }
        }
    }

    /**
     * Report a completed listen: Subsonic scrobble (if online), increment play_count,
     * and insert a [ListenEventEntity] with honest [listenedSeconds] (ADR-0047 / 0128).
     *
     * Sole **incremental** play-count writer — `incrementPlayCount` is invoked only
     * from here. (Densify / REPLACE upserts may set the column, but never increment.)
     */
    fun scrobble(
        trackId: String,
        title: String? = null,
        artist: String? = null,
        albumId: String? = null,
        artistId: String? = null,
        durationSeconds: Int? = null,
        coverArtUrl: String? = null,
        genre: String? = null,
        listenedSeconds: Int = 1,
        albumName: String? = null,
        listenedAt: Long = System.currentTimeMillis(),
    ) {
        scope.launch {
            // Local first (MET-2): the play_count + listen_event are the
            // durable record. A slow/failed server scrobble must not delay or
            // lose them (and service teardown must not cancel in-flight writes).
            ensureTrackRow(
                trackId, title, artist, albumId, artistId, durationSeconds, coverArtUrl, genre,
                album = albumName,
            )
            trackDao.incrementPlayCount(trackId)
            val row = trackDao.getTrack(trackId)
            val resolvedDuration = durationSeconds ?: row?.durationSeconds
            val cappedSeconds = when {
                resolvedDuration != null && resolvedDuration > 0 ->
                    listenedSeconds.coerceIn(1, resolvedDuration)

                else -> listenedSeconds.coerceAtLeast(1)
            }
            listenEventDao.insert(
                ListenEventEntity(
                    trackId = trackId,
                    listenedAt = listenedAt,
                    listenedSeconds = cappedSeconds,
                    artistId = artistId ?: row?.artistId,
                    artistName = artist ?: row?.artist,
                    albumId = albumId ?: row?.albumId,
                    albumName = albumName ?: row?.album,
                    genre = genre ?: row?.genre,
                    trackTitle = title ?: row?.title ?: trackId,
                    isBackfill = false,
                ),
            )
            DiagnosticLog.d(
                "ftpmusic-scrobble",
                "listen event id=$trackId secs=$cappedSeconds",
            )
            // Server report is best-effort and runs after the durable local write.
            if (!offlineModeManager.isQueueEnabled()) {
                try {
                    api.scrobble(authParams(), id = trackId, submission = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("ftpmusic-scrobble", "API call failed: " + e.message)
                    DiagnosticLog.w("ftpmusic-scrobble", "API skip/fail: ${e.message}")
                }
            } else {
                DiagnosticLog.d("ftpmusic-scrobble", "API skip — offline id=$trackId")
            }
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
        album: String? = null,
    ) {
        // MET-3: INSERT OR IGNORE + targeted UPDATE. This preserves every
        // local-weight/cache column and cannot lose a concurrent
        // incrementPlayCount() to a stale read-copy-REPLACE.
        trackDao.ensureTrackRow(trackId)
        trackDao.updateTrackMetadata(
            trackId = trackId,
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            albumId = albumId,
            artistId = artistId,
            durationSeconds = durationSeconds,
            coverArtUrl = coverArtUrl,
        )
    }

    /** Save the play queue to the Subsonic server (fire-and-forget). */
    fun savePlayQueue(auth: Map<String, String>, ids: String, current: String?, position: Long?) {
        scope.launch {
            try {
                api.savePlayQueue(auth, ids, current, position)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic-scrobble", "API call failed: " + e.message)
            }
        }
    }

    /** Fetch similar songs from Subsonic (instant mix / continuous play). */
    suspend fun fetchSimilarSongs(trackId: String, count: Int = 10): List<Track> {
        return try {
            val response = api.getSimilarSongs2(authParams(), id = trackId, count = count)
            val sr = response["subsonic-response"] as? Map<*, *>
            val similar = sr?.get("similarSongs2") as? Map<*, *>
            val raw = similar?.get("song")
            val songs: List<*> = when (raw) {
                is List<*> -> raw
                is Map<*, *> -> listOf(raw)
                else -> emptyList<Any>()
            }
            songs.mapNotNull { song ->
                val s = song as? Map<*, *> ?: return@mapNotNull null
                Track(
                    id = s["id"] as? String ?: return@mapNotNull null,
                    title = s["title"] as? String ?: return@mapNotNull null,
                    artist = s["artist"] as? String,
                    album = s["album"] as? String,
                    artistId = s["artistId"] as? String,
                    albumId = s["albumId"] as? String,
                    duration = (s["duration"] as? Number)?.toInt(),
                    coverArt = s["coverArt"] as? String,
                    contentType = s["contentType"] as? String,
                    suffix = s["suffix"] as? String,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Test-only teardown. Production code must never call this: the service is
     * a process-scoped @Singleton (MET-1). Kept internal so tests can release
     * their IO scope.
     */
    @androidx.annotation.VisibleForTesting
    internal fun cancelScopeForTest() {
        scope.cancel()
    }
}
