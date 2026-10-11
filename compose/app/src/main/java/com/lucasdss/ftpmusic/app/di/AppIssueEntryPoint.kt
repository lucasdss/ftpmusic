package com.lucasdss.ftpmusic.app.di

import com.lucasdss.ftpmusic.app.ui.issues.AppIssueAggregator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppIssueEntryPoint {
    fun appIssueAggregator(): AppIssueAggregator
}
