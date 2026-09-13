package com.lucasdss.ftpmusic.app.di

import android.content.Context
import androidx.room.Room
import com.lucasdss.ftpmusic.app.data.db.AppDatabase
import com.lucasdss.ftpmusic.app.data.db.CacheQueueDao
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixDao
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.db.PendingPlaylistChangeDao
import com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorker
import com.lucasdss.ftpmusic.app.data.db.QueueDao
import com.lucasdss.ftpmusic.app.data.db.QueueJournalDao
import com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackWaveformDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Shared IO dispatcher for background DB/network work (injectable so
     *  ViewModels are unit-testable with a virtual-time dispatcher). */
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "ftpmusic.db",
    )
        .addMigrations(*AppDatabase.ALL_MIGRATIONS_50)
        // NO fallbackToDestructiveMigration: all migrations 1→46 are registered,
        // so a future version-bump that forgets one must FAIL loudly (recoverable)
        // instead of silently wiping the database (the playlist-loss root cause).
        .build()

    @Provides fun provideTrackDao(db: AppDatabase): TrackDao = db.trackDao()

    @Provides fun provideGenreDao(db: AppDatabase): GenreDao = db.genreDao()

    @Provides fun provideCacheQueueDao(db: AppDatabase): CacheQueueDao = db.cacheQueueDao()

    @Provides fun provideQueueDao(db: AppDatabase): QueueDao = db.queueDao()

    @Provides fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides fun provideCachedMetadataDao(db: AppDatabase): CachedMetadataDao = db.cachedMetadataDao()

    @Provides
    fun providePendingPlaylistChangeDao(db: AppDatabase): PendingPlaylistChangeDao = db.pendingPlaylistChangeDao()

    @Provides fun providePlaybackStateDao(db: AppDatabase): PlaybackStateDao = db.playbackStateDao()

    @Provides fun provideTrackWaveformDao(db: AppDatabase): TrackWaveformDao = db.trackWaveformDao()

    @Provides fun provideLyricsCacheDao(db: AppDatabase): LyricsCacheDao = db.lyricsCacheDao()

    @Provides fun provideQueueJournalDao(db: AppDatabase): QueueJournalDao = db.queueJournalDao()

    @Provides fun provideGenreMixDao(db: AppDatabase): GenreMixDao = db.genreMixDao()

    @Provides fun provideCustomMixDao(db: AppDatabase): CustomMixDao = db.customMixDao()

    @Provides fun provideRadioFavoriteDao(db: AppDatabase): RadioFavoriteDao = db.radioFavoriteDao()

    @Provides
    @Singleton
    fun provideCacheDir(@ApplicationContext context: Context): File {
        val dir = File(context.cacheDir, "music_cache")
        dir.mkdirs()
        return dir
    }

    @Provides
    @Singleton
    fun providePlaylistSyncWorker(
        @ApplicationContext context: Context,
        pendingDao: PendingPlaylistChangeDao,
        playlistDao: PlaylistDao,
        api: SubsonicApi,
        authHelper: SubsonicAuthHelper,
        offlineModeManager: com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager,
    ): PlaylistSyncWorker = PlaylistSyncWorker(context, pendingDao, playlistDao, api, authHelper, offlineModeManager)

    @Provides
    @Singleton
    fun provideMetadataSyncWorker(
        @ApplicationContext context: Context,
        api: SubsonicApi,
        authHelper: SubsonicAuthHelper,
        metadataDao: CachedMetadataDao,
        trackDao: TrackDao,
        genreMixDao: GenreMixDao,
        coverArtFallback: com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService,
        dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository,
        offlineModeManager: com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager,
    ): MetadataSyncWorker = MetadataSyncWorker(
        context, api, authHelper, metadataDao, trackDao, genreMixDao, coverArtFallback,
        offlineModeManager = offlineModeManager,
        dailyMixRepository = dailyMixRepository,
    )
}
