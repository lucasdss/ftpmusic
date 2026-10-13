package com.lucasdss.ftpmusic.app.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.session.MediaLibraryService
import com.lucasdss.ftpmusic.app.MainActivity
import com.lucasdss.ftpmusic.app.data.cache.AdjustableCacheEvictor
import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixDao
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository
import com.lucasdss.ftpmusic.app.data.search.LocalSearchRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.AutoRadioStationsSource
import com.lucasdss.ftpmusic.app.playback.CurrentTrackReactionController
import com.lucasdss.ftpmusic.app.playback.DefaultAutoRadioStationsSource
import com.lucasdss.ftpmusic.app.playback.MediaSessionCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideAutoRadioStationsSource(
        api: SubsonicApi,
        authHelper: SubsonicAuthHelper,
        radioFavoriteDao: RadioFavoriteDao,
    ): AutoRadioStationsSource = DefaultAutoRadioStationsSource(api, authHelper, radioFavoriteDao)

    @Provides
    @Singleton
    fun provideMediaSessionCallback(
        trackDao: TrackDao,
        metadataDao: CachedMetadataDao,
        authHelper: SubsonicAuthHelper,
        api: SubsonicApi,
        localSearch: LocalSearchRepository,
        persistenceManager: com.lucasdss.ftpmusic.app.playback.QueuePersistenceManager,
        playlistDao: PlaylistDao,
        reactionController: CurrentTrackReactionController,
        genreDao: GenreDao,
        customMixDao: CustomMixDao,
        genreMixDao: GenreMixDao,
        dailyMixRepository: DailyMixRepository,
        radioSource: AutoRadioStationsSource,
    ): MediaSessionCallback = MediaSessionCallback(
        trackDao = trackDao,
        metadataDao = metadataDao,
        authHelper = authHelper,
        api = api,
        localSearch = localSearch,
        persistenceManager = persistenceManager,
        playlistDao = playlistDao,
        reactionController = reactionController,
        genreDao = genreDao,
        customMixDao = customMixDao,
        genreMixDao = genreMixDao,
        dailyMixRepository = dailyMixRepository,
        radioSource = radioSource,
    )

    @Provides
    @Singleton
    fun provideMediaSession(
        @ApplicationContext context: Context,
        callback: MediaSessionCallback,
        bitmapLoader: com.lucasdss.ftpmusic.app.playback.CoverArtBitmapLoader,
    ): MediaLibraryService.MediaLibrarySession {
        val exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
        val sessionActivity = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return MediaLibraryService.MediaLibrarySession.Builder(context, exoPlayer, callback)
            .setBitmapLoader(bitmapLoader)
            .setSessionActivity(sessionActivity)
            .build()
    }

    @Provides
    @Singleton
    fun provideAudioCacheEvictor(storage: SecureStorage): AdjustableCacheEvictor = AdjustableCacheEvictor {
        storage.get(SecureStorage.KEY_AUDIO_CACHE_MAX_BYTES)?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: AdjustableCacheEvictor.DEFAULT_MAX_BYTES
    }

    /** The single unified audio cache shared by ExoPlayer, DownloadManager and the Cast proxy. */
    @Provides
    @Singleton
    fun provideAudioCache(
        @ApplicationContext context: Context,
        cacheDir: File,
        evictor: AdjustableCacheEvictor,
    ): SimpleCache {
        cacheDir.mkdirs()
        // Park legacy (pre-SimpleCache) files BEFORE SimpleCache's initial scan,
        // which deletes anything it can't parse as a span — otherwise existing
        // users' downloads are wiped on upgrade. CacheService.initialize()
        // imports the parked files into the cache afterwards.
        CacheService.relocateLegacyFiles(cacheDir)
        return SimpleCache(cacheDir, evictor, StandaloneDatabaseProvider(context))
    }
}
