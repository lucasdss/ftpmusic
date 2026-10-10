package com.lucasdss.ftpmusic.app.di

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.lucasdss.ftpmusic.app.data.update.AppUpdateChecker
import com.lucasdss.ftpmusic.app.data.update.PlayAppUpdateChecker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateBindModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateChecker(impl: PlayAppUpdateChecker): AppUpdateChecker
}

@Module
@InstallIn(SingletonComponent::class)
object UpdateProvideModule {
    @Provides
    @Singleton
    fun provideAppUpdateManager(@ApplicationContext context: Context): AppUpdateManager =
        AppUpdateManagerFactory.create(context)
}
