package com.lucasdss.ftpmusic.app.ui.search

import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.network.LastFmService
import com.lucasdss.ftpmusic.app.data.network.ListenBrainzService
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Limited biography fanout for Discover / Desired catalog (ADR-0144 amend).
 * Top [BIO_FANOUT] hits: library cache first, else Last.fm artist.getInfo.
 */
@Singleton
class DiscoverArtistBioEnricher @Inject constructor(
    private val lastFmService: LastFmService,
    private val metadataDao: CachedMetadataDao,
) {
    companion object {
        val BIO_FANOUT: Int = ListenBrainzService.DISCOVER_FANOUT
        const val BIO_MAX_CHARS = 2000
    }

    suspend fun enrich(hits: List<DiscoverArtistHit>): List<DiscoverArtistHit> {
        if (hits.isEmpty()) return hits
        return hits.mapIndexed { index, hit ->
            if (index >= BIO_FANOUT) return@mapIndexed hit
            if (!hit.biography.isNullOrBlank()) return@mapIndexed hit
            try {
                if (hit.inLibrary && !hit.localArtistId.isNullOrBlank()) {
                    val cached = metadataDao.getArtistById(hit.localArtistId)?.biography
                        ?.takeIf { it.isNotBlank() }
                    if (cached != null) {
                        return@mapIndexed hit.copy(biography = cached.take(BIO_MAX_CHARS))
                    }
                }
                val remote = lastFmService.fetchArtistInfo(hit.name, hit.mbid, BIO_MAX_CHARS)
                if (!remote.isNullOrBlank()) hit.copy(biography = remote) else hit
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                hit
            }
        }
    }
}
