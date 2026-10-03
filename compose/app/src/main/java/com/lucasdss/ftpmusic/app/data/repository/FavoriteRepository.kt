package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favorites repository — local-first by contract (ADR 0020):
 * every write lands in Room FIRST, then mirrors to the Subsonic server
 * best-effort. Server failures never fail or roll back a local write; the
 * MetadataSyncWorker star mirror re-pushes local-only stars when connectivity
 * returns and protects them from being cleared (clearNonStarredBefore).
 *
 * Mirror is skipped when the local ensure/UPDATE did not persist (0-row),
 * so the server never gets a ghost star without Room truth.
 */
@Singleton
class FavoriteRepository @Inject constructor(
    private val api: SubsonicApi,
    private val storage: SecureStorage,
    private val offlineModeManager: OfflineModeManager,
    private val trackDao: com.lucasdss.ftpmusic.app.data.db.TrackDao,
    private val metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao,
    private val radioFavoriteDao: com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao,
) {
    private val auth = SubsonicAuthHelper()

    private suspend fun authParams(): Map<String, String> {
        val user = storage.get(SecureStorage.KEY_USERNAME) ?: ""
        val pass = storage.get(SecureStorage.KEY_PASSWORD) ?: ""
        return auth.buildAuthParams(user, pass)
    }

    /** Server mirror is skipped in offline mode — the local write already
     *  happened; the MetadataSyncWorker star mirror re-pushes it later. */
    private suspend fun mirrorStar(block: suspend () -> Unit) {
        if (offlineModeManager.isOfflineEnabled()) return
        try {
            block()
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-fav", "server sync deferred (kept local): ${e.message}")
        }
    }

    private suspend fun mirrorRating(block: suspend () -> Unit) {
        if (offlineModeManager.isOfflineEnabled()) return
        try {
            block()
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-fav", "rating sync deferred (kept local): ${e.message}")
        }
    }

    // ── Tracks: local-first like (= star) / dislike (local-only) ─────────────

    suspend fun starTrack(trackId: String) {
        trackDao.ensureTrackRow(trackId)
        trackDao.setStarredAt(trackId, System.currentTimeMillis())
        trackDao.setPendingUnstar(trackId, null)
        if (!trackDao.isTrackStarred(trackId)) {
            android.util.Log.w("ftpmusic-fav", "starTrack local miss — skip server: $trackId")
            return
        }
        mirrorStar { api.star(authParams(), id = trackId) }
    }

    suspend fun unstarTrack(trackId: String) {
        trackDao.ensureTrackRow(trackId)
        trackDao.clearStarAndMarkPendingUnstar(trackId, System.currentTimeMillis())
        // Local clear always attempted; mirror unstar even if row was new/empty.
        mirrorStar { api.unstar(authParams(), id = trackId) }
    }

    /** Thumbs up: like == starred on Navidrome. Clears any local dislike first. */
    suspend fun likeTrack(trackId: String) {
        trackDao.ensureTrackRow(trackId)
        trackDao.setDisliked(trackId, false, at = 0L)
        starTrack(trackId)
    }

    /** Remove thumbs up (unstar). Leaves dislike untouched. */
    suspend fun unlikeTrack(trackId: String) {
        unstarTrack(trackId)
    }

    /** Thumbs down: local-only dislike. Clears like (star) — best-effort server unstar. */
    suspend fun dislikeTrack(trackId: String) {
        trackDao.ensureTrackRow(trackId)
        val now = System.currentTimeMillis()
        trackDao.clearStarAndMarkPendingUnstar(trackId, now)
        trackDao.setDisliked(trackId, true, at = now)
        if (!trackDao.isTrackDisliked(trackId)) {
            android.util.Log.w("ftpmusic-fav", "dislikeTrack local miss — skip server: $trackId")
            return
        }
        mirrorStar { api.unstar(authParams(), id = trackId) }
    }

    /** Remove thumbs down (keep any star state). */
    suspend fun clearDislikeTrack(trackId: String) {
        trackDao.setDisliked(trackId, false, at = 0L)
    }

    /** 5★ rating: Room first, then best-effort setRating. */
    suspend fun rateTrack(trackId: String, rating: Int) {
        val clamped = rating.coerceIn(0, 5)
        trackDao.ensureTrackRow(trackId)
        trackDao.setRating(trackId, clamped)
        mirrorRating { api.setRating(authParams(), id = trackId, rating = clamped) }
    }

    // ── v43: Albums / Artists — same local-first semantics ──────────────────

    suspend fun starAlbum(albumId: String) {
        metadataDao.ensureAlbumLedgerRow(albumId)
        metadataDao.setAlbumStarredAt(albumId, System.currentTimeMillis())
        metadataDao.setAlbumPendingUnstar(albumId, null)
        if (!metadataDao.isAlbumStarred(albumId)) {
            android.util.Log.w("ftpmusic-fav", "starAlbum local miss — skip server: $albumId")
            return
        }
        mirrorStar { api.star(authParams(), albumId = albumId) }
    }

    suspend fun unstarAlbum(albumId: String) {
        metadataDao.clearAlbumStarAndMarkPendingUnstar(albumId, System.currentTimeMillis())
        mirrorStar { api.unstar(authParams(), albumId = albumId) }
    }

    suspend fun starArtist(artistId: String) {
        metadataDao.ensureArtistLedgerRow(artistId)
        metadataDao.setArtistStarredAt(artistId, System.currentTimeMillis())
        metadataDao.setArtistPendingUnstar(artistId, null)
        if (!metadataDao.isArtistStarred(artistId)) {
            android.util.Log.w("ftpmusic-fav", "starArtist local miss — skip server: $artistId")
            return
        }
        mirrorStar { api.star(authParams(), artistId = artistId) }
    }

    suspend fun unstarArtist(artistId: String) {
        metadataDao.clearArtistStarAndMarkPendingUnstar(artistId, System.currentTimeMillis())
        mirrorStar { api.unstar(authParams(), artistId = artistId) }
    }

    /** Thumbs up on an album: local star first, best-effort server mirror. */
    suspend fun likeAlbum(albumId: String) {
        metadataDao.setAlbumDisliked(albumId, false, at = 0L)
        starAlbum(albumId)
    }

    suspend fun unlikeAlbum(albumId: String) {
        unstarAlbum(albumId)
    }

    /** Thumbs down on an album: local-only, clears like with best-effort server unstar. */
    suspend fun dislikeAlbum(albumId: String) {
        metadataDao.ensureAlbumLedgerRow(albumId)
        val now = System.currentTimeMillis()
        metadataDao.clearAlbumStarAndMarkPendingUnstar(albumId, now)
        metadataDao.setAlbumDisliked(albumId, true, at = now)
        if (!metadataDao.isAlbumDisliked(albumId)) {
            android.util.Log.w("ftpmusic-fav", "dislikeAlbum local miss — skip server: $albumId")
            return
        }
        mirrorStar { api.unstar(authParams(), albumId = albumId) }
    }

    suspend fun clearDislikeAlbum(albumId: String) {
        metadataDao.setAlbumDisliked(albumId, false, at = 0L)
    }

    /** 5★ album rating: ledger ensure + Room, then best-effort setRating. */
    suspend fun rateAlbum(albumId: String, rating: Int) {
        val clamped = rating.coerceIn(0, 5)
        metadataDao.ensureAlbumLedgerRow(albumId)
        metadataDao.setAlbumRating(albumId, clamped)
        mirrorRating { api.setRating(authParams(), id = albumId, rating = clamped) }
    }

    /** Thumbs up on an artist: local star first, best-effort server mirror. */
    suspend fun likeArtist(artistId: String) {
        metadataDao.setArtistDisliked(artistId, false, at = 0L)
        starArtist(artistId)
    }

    suspend fun unlikeArtist(artistId: String) {
        unstarArtist(artistId)
    }

    /** Thumbs down on an artist: local-only, clears like with best-effort server unstar. */
    suspend fun dislikeArtist(artistId: String) {
        metadataDao.ensureArtistLedgerRow(artistId)
        val now = System.currentTimeMillis()
        metadataDao.clearArtistStarAndMarkPendingUnstar(artistId, now)
        metadataDao.setArtistDisliked(artistId, true, at = now)
        if (!metadataDao.isArtistDisliked(artistId)) {
            android.util.Log.w("ftpmusic-fav", "dislikeArtist local miss — skip server: $artistId")
            return
        }
        mirrorStar { api.unstar(authParams(), artistId = artistId) }
    }

    suspend fun clearDislikeArtist(artistId: String) {
        metadataDao.setArtistDisliked(artistId, false, at = 0L)
    }

    // ── v43: Radio bookmarks (local-only Room persistence) ──────────────────

    /** Bookmark (favorite) a radio station — local-only (no Subsonic radio star). */
    suspend fun bookmarkRadio(stationId: String, name: String, streamUrl: String, homePageUrl: String?) {
        radioFavoriteDao.upsert(
            com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                stationId = stationId,
                name = name,
                streamUrl = streamUrl,
                homePageUrl = homePageUrl,
                bookmarkedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun unbookmarkRadio(stationId: String) {
        radioFavoriteDao.delete(stationId)
    }

    suspend fun getStarred(): Map<String, Any> = api.getStarred2(authParams())
}
