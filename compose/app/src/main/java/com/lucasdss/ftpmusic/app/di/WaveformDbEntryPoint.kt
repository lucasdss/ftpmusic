package com.lucasdss.ftpmusic.app.di

import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WaveformDbEntryPoint {
    fun waveformRepository(): WaveformRepository
}
