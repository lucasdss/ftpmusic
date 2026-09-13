package com.lucasdss.ftpmusic.app.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.session.MediaLibraryService
import com.lucasdss.ftpmusic.app.data.cache.AdjustableCacheEvictor
import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
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
    fun provideMediaSessionCallback(
        trackDao: TrackDao,
        metadataDao: CachedMetadataDao,
        authHelper: SubsonicAuthHelper,
        api: SubsonicApi,
    ): MediaSessionCallback = MediaSessionCallback(trackDao, metadataDao, authHelper, api)

    @Provides
    @Singleton
    fun provideMediaSession(
        @ApplicationContext context: Context,
        callback: MediaSessionCallback,
        bitmapLoader: com.lucasdss.ftpmusic.app.playback.CoverArtBitmapLoader,
    ): MediaLibraryService.MediaLibrarySession {
        val exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
        return MediaLibraryService.MediaLibrarySession.Builder(context, exoPlayer, callback)
            .setBitmapLoader(bitmapLoader)
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
