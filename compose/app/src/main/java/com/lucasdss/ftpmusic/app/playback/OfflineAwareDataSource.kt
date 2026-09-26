package com.lucasdss.ftpmusic.app.playback

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import java.io.IOException

/**
 * Upstream data source that fails fast (IOException) before opening a socket when:
 * - software offline mode is enabled ([OfflineModeManager]), OR
 * - the Subsonic server is marked unreachable ([ReachabilityStateHolder]).
 *
 * Outside-LAN / 5G with a home Navidrome: phone has cellular but ping fails →
 * reachability false. Without this gate, Exo opened HTTP and hung for seconds
 * per uncached track (user: blank Now Playing, broken Next, force-close).
 * Cache hits never reach this source — CacheDataSource serves them from disk.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OfflineAwareHttpDataSource(
    private val offlineModeManager: OfflineModeManager,
    private val delegate: DataSource,
    private val isServerReachable: () -> Boolean = { ReachabilityStateHolder.isReachable.value },
) : DataSource by delegate {

    override fun open(dataSpec: DataSpec): Long {
        if (offlineModeManager.isOfflineEnabled()) {
            throw IOException("Offline mode — network blocked")
        }
        if (!isServerReachable()) {
            throw IOException("Server unreachable — network blocked")
        }
        return delegate.open(dataSpec)
    }
}

/**
 * [DataSource.Factory] for [OfflineAwareHttpDataSource]. Wraps the default
 * HTTP factory so ExoPlayer's upstream can never open a socket while offline
 * mode is on or the server is known unreachable.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OfflineAwareHttpDataSourceFactory(
    private val offlineModeManager: OfflineModeManager,
    private val upstreamFactory: DataSource.Factory = DefaultHttpDataSource.Factory(),
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        OfflineAwareHttpDataSource(offlineModeManager, upstreamFactory.createDataSource())
}
