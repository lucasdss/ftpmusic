package com.lucasdss.ftpmusic.app.data.preferences

import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.network.CustomHeadersInterceptor
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PreferenceBootstrapTest {

    private val storage: SecureStorage = mockk(relaxed = true)
    private val playbackManager: PlaybackManager = mockk(relaxed = true)
    private val coverArtFallback: CoverArtFallbackService = mockk(relaxed = true)

    @Before
    fun setUp() {
        CustomHeadersInterceptor.updateHeaders(emptyList())
        every { playbackManager.setJournalCap(any()) } just runs
        every { playbackManager.setContinuousPlayEnabled(any()) } just runs
    }

    @After
    fun tearDown() {
        CustomHeadersInterceptor.updateHeaders(emptyList())
    }

    private fun bootstrap() = PreferenceBootstrap(storage, playbackManager, coverArtFallback)

    @Test
    fun `parseCustomHeaders splits encoded pairs`() {
        val parsed = PreferenceBootstrap.parseCustomHeaders("X-A=1||X-B=two||bad|| =skip||=also")
        assertEquals(listOf("X-A" to "1", "X-B" to "two"), parsed)
    }

    @Test
    fun `hydrateCustomHeaders applies interceptor from storage`() {
        every { storage.get(SecureStorage.KEY_CUSTOM_HEADERS) } returns "Auth=token||X-Foo=bar"
        bootstrap().hydrateCustomHeaders()
        assertEquals(listOf("Auth" to "token", "X-Foo" to "bar"), CustomHeadersInterceptor.headers)
    }

    @Test
    fun `hydrateJournalAndContinuousPlay restores playback manager`() {
        every { storage.get(SecureStorage.KEY_QUEUE_JOURNAL_CAP) } returns "250"
        every { storage.get(SecureStorage.KEY_CONTINUOUS_PLAY_ENABLED) } returns "false"
        bootstrap().hydrateJournalAndContinuousPlay()
        verify { playbackManager.setJournalCap(250) }
        verify { playbackManager.setContinuousPlayEnabled(false) }
    }

    @Test
    fun `hydrateCoverArtQuota applies saved mb`() {
        every { storage.get(SecureStorage.KEY_COVER_ART_QUOTA_MB) } returns "150"
        every { coverArtFallback.maxCacheBytes = any() } just runs
        bootstrap().hydrateCoverArtQuota()
        verify { coverArtFallback.maxCacheBytes = 150L * 1024 * 1024 }
    }

    @Test
    fun `hydrate uses defaults when storage empty`() {
        every { storage.get(any()) } returns null
        every { coverArtFallback.maxCacheBytes = any() } just runs
        bootstrap().hydrate()
        verify { playbackManager.setJournalCap(100) }
        verify { playbackManager.setContinuousPlayEnabled(true) }
        verify {
            coverArtFallback.maxCacheBytes =
                CoverArtFallbackService.DEFAULT_QUOTA_MB.toLong() * 1024 * 1024
        }
        assertEquals(emptyList<Pair<String, String>>(), CustomHeadersInterceptor.headers)
    }

    @Test
    fun `hydrateJournal clamps low and high caps`() {
        every { storage.get(SecureStorage.KEY_QUEUE_JOURNAL_CAP) } returns "5"
        every { storage.get(SecureStorage.KEY_CONTINUOUS_PLAY_ENABLED) } returns "garbage"
        bootstrap().hydrateJournalAndContinuousPlay()
        verify { playbackManager.setJournalCap(10) }
        verify { playbackManager.setContinuousPlayEnabled(true) }

        every { storage.get(SecureStorage.KEY_QUEUE_JOURNAL_CAP) } returns "9999"
        every { storage.get(SecureStorage.KEY_CONTINUOUS_PLAY_ENABLED) } returns "true"
        bootstrap().hydrateJournalAndContinuousPlay()
        verify { playbackManager.setJournalCap(500) }
    }

    @Test
    fun `hydrateCoverArtQuota clamps low and high values`() {
        every { coverArtFallback.maxCacheBytes = any() } just runs
        every { storage.get(SecureStorage.KEY_COVER_ART_QUOTA_MB) } returns "10"
        bootstrap().hydrateCoverArtQuota()
        verify { coverArtFallback.maxCacheBytes = 50L * 1024 * 1024 }

        every { storage.get(SecureStorage.KEY_COVER_ART_QUOTA_MB) } returns "5000"
        bootstrap().hydrateCoverArtQuota()
        verify { coverArtFallback.maxCacheBytes = 1000L * 1024 * 1024 }
    }

    @Test
    fun `hydrateCustomHeaders no-op when storage empty`() {
        every { storage.get(SecureStorage.KEY_CUSTOM_HEADERS) } returns null
        CustomHeadersInterceptor.updateHeaders(listOf("Keep" to "me"))
        bootstrap().hydrateCustomHeaders()
        assertEquals(listOf("Keep" to "me"), CustomHeadersInterceptor.headers)
    }
}
