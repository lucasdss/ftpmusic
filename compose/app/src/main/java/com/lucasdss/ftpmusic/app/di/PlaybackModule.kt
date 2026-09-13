package com.lucasdss.ftpmusic.app.di

import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.playback.MediaSessionPlaybackProvider
import com.lucasdss.ftpmusic.app.playback.PlaybackProxy
import com.lucasdss.ftpmusic.app.playback.PlaybackStateProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun providePlaybackProxy(cacheService: CacheService): PlaybackProxy {
        return PlaybackProxy(cacheService)
        // Proxy is started on-demand for Cast tier 3 — not at injection time
    }

    @Provides
    @Singleton
    fun providePlaybackStateProvider(provider: MediaSessionPlaybackProvider): PlaybackStateProvider = provider
}
