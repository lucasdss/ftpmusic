package com.lucasdss.ftpmusic.app.di

import com.lucasdss.ftpmusic.app.data.waveform.AndroidWaveformAssetSource
import com.lucasdss.ftpmusic.app.data.waveform.WaveformAssetSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the Android AssetManager-backed source to the loader abstraction. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WaveformModule {
    @Binds
    abstract fun bindWaveformAssetSource(impl: AndroidWaveformAssetSource): WaveformAssetSource
}
