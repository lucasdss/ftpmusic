package com.lucasdss.ftpmusic.app.ui.search

import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared library membership resolve for Discover + Desired catalog-add (ADR-0144).
 */
@Singleton
class DiscoverLibraryResolver @Inject constructor(
    private val metadataDao: CachedMetadataDao,
    private val trackDao: TrackDao,
) {
    suspend fun resolveArtist(mbid: String?, name: String): CachedArtistEntity? {
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

    /**
     * Requires non-blank artist: [CachedMetadataDao.getAlbumByExactName] treats
     * null artist as a title-only wildcard (false-positive In library).
     */
    suspend fun resolveAlbum(title: String, artistName: String?): CachedAlbumEntity? {
        val artist = artistName?.takeIf { it.isNotBlank() } ?: return null
        return try {
            metadataDao.getAlbumByExactName(title, artist)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveTrack(mbid: String?, title: String, artistName: String?): TrackEntity? {
        if (!mbid.isNullOrBlank()) {
            try {
                trackDao.getTrackByMbid(mbid)?.let { return it }
            } catch (_: Exception) {
            }
        }
        val artist = artistName?.takeIf { it.isNotBlank() } ?: return null
        return try {
            trackDao.getTrackByExactTitleArtist(title, artist)
        } catch (_: Exception) {
            null
        }
    }
}
