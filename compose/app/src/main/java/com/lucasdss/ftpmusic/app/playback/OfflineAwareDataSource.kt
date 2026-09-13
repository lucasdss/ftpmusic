package com.lucasdss.ftpmusic.app.playback

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import java.io.IOException

/**
 * Upstream data source that fails fast (IOException) while software offline
 * mode is enabled — BEFORE any socket is opened.
 *
 * Local-first contract: with offline mode on, a cache miss must surface as a
 * playback error in milliseconds (the auto-skip guard then advances the queue
 * to the next cached track) instead of burning the HTTP retry budget with
 * multi-second stalls per track. Cache hits never reach this source — the
 * CacheDataSource serves them from disk.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OfflineAwareHttpDataSource(private val offlineModeManager: OfflineModeManager, private val delegate: DataSource) :
    DataSource by delegate {

    override fun open(dataSpec: DataSpec): Long {
        if (offlineModeManager.isOfflineEnabled()) {
            throw IOException("Offline mode — network blocked")
        }
        return delegate.open(dataSpec)
    }
}

/**
 * [DataSource.Factory] for [OfflineAwareHttpDataSource]. Wraps the default
 * HTTP factory so ExoPlayer's upstream can never open a socket while the user
 * has offline mode toggled on.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OfflineAwareHttpDataSourceFactory(
    private val offlineModeManager: OfflineModeManager,
    private val upstreamFactory: DataSource.Factory = DefaultHttpDataSource.Factory(),
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        OfflineAwareHttpDataSource(offlineModeManager, upstreamFactory.createDataSource())
}
