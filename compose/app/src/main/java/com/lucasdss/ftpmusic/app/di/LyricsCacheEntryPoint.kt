package com.lucasdss.ftpmusic.app.di

import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LyricsCacheEntryPoint {
    fun lyricsCacheDao(): LyricsCacheDao
    fun subsonicApi(): SubsonicApi
    fun subsonicAuthHelper(): SubsonicAuthHelper
}
