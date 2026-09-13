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

    // ── Tracks: local-first like (= star) / dislike (local-only) ─────────────

    suspend fun starTrack(trackId: String) {
        trackDao.setStarredAt(trackId, System.currentTimeMillis())
        trackDao.setPendingUnstar(trackId, null)
        mirrorStar { api.star(authParams(), id = trackId) }
    }

    suspend fun unstarTrack(trackId: String) {
        trackDao.setStarredAt(trackId, null)
        trackDao.setPendingUnstar(trackId, System.currentTimeMillis())
        mirrorStar { api.unstar(authParams(), id = trackId) }
    }

    /** Thumbs up: like == starred on Navidrome. Clears any local dislike first. */
    suspend fun likeTrack(trackId: String) {
        trackDao.setDisliked(trackId, false)
        starTrack(trackId)
    }

    /** Remove thumbs up (unstar). Leaves dislike untouched. */
    suspend fun unlikeTrack(trackId: String) {
        unstarTrack(trackId)
    }

    /** Thumbs down: local-only dislike. Clears like (star) — best-effort server unstar. */
    suspend fun dislikeTrack(trackId: String) {
        trackDao.setStarredAt(trackId, null)
        trackDao.setDisliked(trackId, true)
        trackDao.setPendingUnstar(trackId, System.currentTimeMillis())
        mirrorStar { api.unstar(authParams(), id = trackId) }
    }

    /** Remove thumbs down (keep any star state). */
    suspend fun clearDislikeTrack(trackId: String) {
        trackDao.setDisliked(trackId, false)
    }

    // ── v43: Albums / Artists — same local-first semantics ──────────────────

    suspend fun starAlbum(albumId: String) {
        metadataDao.ensureAlbumLedgerRow(albumId)
        metadataDao.setAlbumStarredAt(albumId, System.currentTimeMillis())
        metadataDao.setAlbumPendingUnstar(albumId, null)
        mirrorStar { api.star(authParams(), albumId = albumId) }
    }

    suspend fun unstarAlbum(albumId: String) {
        metadataDao.setAlbumStarredAt(albumId, null)
        metadataDao.setAlbumPendingUnstar(albumId, System.currentTimeMillis())
        mirrorStar { api.unstar(authParams(), albumId = albumId) }
    }

    suspend fun starArtist(artistId: String) {
        metadataDao.ensureArtistLedgerRow(artistId)
        metadataDao.setArtistStarredAt(artistId, System.currentTimeMillis())
        metadataDao.setArtistPendingUnstar(artistId, null)
        mirrorStar { api.star(authParams(), artistId = artistId) }
    }

    suspend fun unstarArtist(artistId: String) {
        metadataDao.setArtistStarredAt(artistId, null)
        metadataDao.setArtistPendingUnstar(artistId, System.currentTimeMillis())
        mirrorStar { api.unstar(authParams(), artistId = artistId) }
    }

    /** Thumbs up on an album: local star first, best-effort server mirror.
     *  starAlbum ensures the ledger row exists. */
    suspend fun likeAlbum(albumId: String) {
        metadataDao.setAlbumDisliked(albumId, false)
        starAlbum(albumId)
    }

    suspend fun unlikeAlbum(albumId: String) {
        unstarAlbum(albumId)
    }

    /** Thumbs down on an album: local-only, clears like with best-effort server unstar. */
    suspend fun dislikeAlbum(albumId: String) {
        metadataDao.ensureAlbumLedgerRow(albumId)
        metadataDao.setAlbumStarredAt(albumId, null)
        metadataDao.setAlbumDisliked(albumId, true)
        metadataDao.setAlbumPendingUnstar(albumId, System.currentTimeMillis())
        mirrorStar { api.unstar(authParams(), albumId = albumId) }
    }

    suspend fun clearDislikeAlbum(albumId: String) {
        metadataDao.setAlbumDisliked(albumId, false)
    }

    /** Thumbs up on an artist: local star first, best-effort server mirror.
     *  starArtist ensures the ledger row exists. */
    suspend fun likeArtist(artistId: String) {
        metadataDao.setArtistDisliked(artistId, false)
        starArtist(artistId)
    }

    suspend fun unlikeArtist(artistId: String) {
        unstarArtist(artistId)
    }

    /** Thumbs down on an artist: local-only, clears like with best-effort server unstar. */
    suspend fun dislikeArtist(artistId: String) {
        metadataDao.ensureArtistLedgerRow(artistId)
        metadataDao.setArtistStarredAt(artistId, null)
        metadataDao.setArtistDisliked(artistId, true)
        metadataDao.setArtistPendingUnstar(artistId, System.currentTimeMillis())
        mirrorStar { api.unstar(authParams(), artistId = artistId) }
    }

    suspend fun clearDislikeArtist(artistId: String) {
        metadataDao.setArtistDisliked(artistId, false)
    }

    // ── v43: Radio bookmarks (local-only Room persistence) ──────────────────

    /** Bookmark (favorite) a radio station — local-only persistence. */
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
