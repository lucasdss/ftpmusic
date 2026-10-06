package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.network.LastFmService
import com.lucasdss.ftpmusic.app.data.network.MusicBrainzService
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.search.SearchIndexRebuilder
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Deferred search enrichment (ADR-0079 / ADR-0085): Navidrome info + MusicBrainz
 * aliases + Last.fm tags. Runs off the critical metadata sync path so SyncingScreen
 * / WorkManager sync completion never waits on external APIs.
 */
@Singleton
class MetadataEnrichRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SubsonicApi,
    private val authHelper: SubsonicAuthHelper,
    private val metadataDao: CachedMetadataDao,
    private val offlineModeManager: OfflineModeManager,
    private val searchIndexRebuilder: SearchIndexRebuilder,
    private val musicBrainzService: MusicBrainzService,
    private val lastFmService: LastFmService,
) {
    companion object {
        private const val TAG = "ftpmusic-metaenrich"
        const val UNIQUE_WORK_NAME = "metadata_enrich"

        private const val ENRICH_DELAY_MS = 250L
        private const val ENRICH_ARTIST_LIMIT = 40
        private const val ENRICH_ALBUM_LIMIT = 25
        private const val ENRICH_ALIAS_LIMIT = 30
        private const val ENRICH_TAG_LIMIT = 30
    }

    /** Enqueue a one-shot enrich job (network required). No-op in unit tests. */
    fun enqueue() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<MetadataEnrichScheduleWorker>()
                .setConstraints(constraints)
                .addTag(UNIQUE_WORK_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            Log.d(TAG, "Enqueued metadata enrich work")
        } catch (e: IllegalStateException) {
            Log.d(TAG, "WorkManager unavailable — enrich skipped: ${e.message}")
        }
    }

    /**
     * Background getArtistInfo2 / getAlbumInfo2 + MB aliases + Last.fm tags.
     * Never called from search keystroke path (ADR 0079).
     */
    @VisibleForTesting
    suspend fun enrichSearchMetadata() {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isEmpty()) return
        if (offlineModeManager.isOfflineEnabled()) return
        val params = authHelper.buildAuthParams(username, password)
        val artists = metadataDao.getArtistsNeedingEnrichment(ENRICH_ARTIST_LIMIT)
        for (artist in artists) {
            try {
                delay(ENRICH_DELAY_MS)
                val response = api.getArtistInfo2(artist.id, params)
                val sr = response["subsonic-response"] as? Map<*, *> ?: continue
                val info = sr["artistInfo2"] as? Map<*, *> ?: continue
                val bio = (info["biography"] as? String)?.take(2000)
                if (!bio.isNullOrBlank()) {
                    metadataDao.setArtistBiography(artist.id, bio)
                }
                val similar = (info["similarArtist"] as? List<*>)?.mapNotNull { s ->
                    val m = s as? Map<*, *> ?: return@mapNotNull null
                    m["name"] as? String
                }
                if (!similar.isNullOrEmpty() && artist.similarArtistsJson.isNullOrBlank()) {
                    val json = similar.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
                    metadataDao.setArtistSimilarArtists(artist.id, json)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        for (artist in metadataDao.getArtistsNeedingAliases(ENRICH_ALIAS_LIMIT)) {
            try {
                delay(ENRICH_DELAY_MS)
                val mbid = artist.musicbrainzId
                    ?: musicBrainzService.searchArtistMbid(artist.name)
                    ?: continue
                if (artist.musicbrainzId.isNullOrBlank()) {
                    metadataDao.setArtistPublicRating(
                        artist.id,
                        artist.publicRating,
                        artist.publicRatingVotes,
                        mbid,
                    )
                }
                val aliases = musicBrainzService.fetchArtistAliases(mbid)
                if (aliases.isNotEmpty()) {
                    metadataDao.setArtistSearchAliases(artist.id, aliases.joinToString(" "))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        if (lastFmService.currentApiKey().isNotEmpty()) {
            for (artist in metadataDao.getArtistsNeedingTags(ENRICH_TAG_LIMIT)) {
                try {
                    delay(ENRICH_DELAY_MS)
                    val tags = lastFmService.fetchArtistTopTags(artist.name)
                    if (tags.isNotEmpty()) {
                        metadataDao.setArtistSearchTags(artist.id, tags.joinToString(" "))
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
        val albums = metadataDao.getAlbumsNeedingEnrichment(ENRICH_ALBUM_LIMIT)
        for (album in albums) {
            try {
                delay(ENRICH_DELAY_MS)
                val response = api.getAlbumInfo2(album.id, params)
                val sr = response["subsonic-response"] as? Map<*, *> ?: continue
                val info = sr["albumInfo"] as? Map<*, *> ?: continue
                val notes = (info["notes"] as? String)?.take(2000) ?: continue
                if (notes.isNotBlank()) metadataDao.setAlbumNotes(album.id, notes)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        searchIndexRebuilder.scheduleRebuild()
        Log.d(TAG, "Enrich pass complete — FTS rebuild scheduled")
    }
}

@HiltWorker
class MetadataEnrichScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val enrichRunner: MetadataEnrichRunner,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = try {
        enrichRunner.enrichSearchMetadata()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("ftpmusic-metaenrich", "Enrich work failed: ${e.message}", e)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
