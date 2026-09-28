package com.lucasdss.ftpmusic.app.data.preferences

import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.network.CustomHeadersInterceptor
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restores Settings prefs that live in process RAM after process death.
 * Called from [com.lucasdss.ftpmusic.app.FtpmusicApp.onCreate] so consumers
 * work before the user opens Settings (ADR-0044).
 */
@Singleton
class PreferenceBootstrap @Inject constructor(
    private val storage: SecureStorage,
    private val playbackManager: PlaybackManager,
    private val coverArtFallback: CoverArtFallbackService,
) {

    fun hydrate() {
        hydrateCustomHeaders()
        hydrateJournalAndContinuousPlay()
        hydrateCoverArtQuota()
    }

    fun hydrateCustomHeaders() {
        val raw = storage.get(SecureStorage.KEY_CUSTOM_HEADERS) ?: return
        val headers = parseCustomHeaders(raw)
        CustomHeadersInterceptor.updateHeaders(headers)
    }

    fun hydrateJournalAndContinuousPlay() {
        val journalCap = storage.get(SecureStorage.KEY_QUEUE_JOURNAL_CAP)?.toIntOrNull() ?: 100
        playbackManager.setJournalCap(journalCap.coerceIn(10, 500))
        val continuous = storage.get(SecureStorage.KEY_CONTINUOUS_PLAY_ENABLED)
            ?.toBooleanStrictOrNull() ?: true
        playbackManager.setContinuousPlayEnabled(continuous)
    }

    fun hydrateCoverArtQuota() {
        val mb = storage.get(SecureStorage.KEY_COVER_ART_QUOTA_MB)?.toIntOrNull()
            ?: CoverArtFallbackService.DEFAULT_QUOTA_MB
        coverArtFallback.maxCacheBytes = mb.coerceIn(50, 1000).toLong() * 1024 * 1024
    }

    companion object {
        /** Parse `k=v||k2=v2` encoding used by Settings. */
        fun parseCustomHeaders(raw: String): List<Pair<String, String>> = raw.split("||").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq > 0) part.substring(0, eq) to part.substring(eq + 1) else null
        }.filter { it.first.isNotBlank() && it.second.isNotBlank() }.take(5)
    }
}
