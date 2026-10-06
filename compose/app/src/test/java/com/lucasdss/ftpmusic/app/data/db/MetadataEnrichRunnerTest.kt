package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.network.LastFmService
import com.lucasdss.ftpmusic.app.data.network.MusicBrainzService
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.search.SearchIndexRebuilder
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class MetadataEnrichRunnerTest {

    private val context: Context = mockk(relaxed = true)
    private val api: SubsonicApi = mockk(relaxed = true)
    private val authHelper = SubsonicAuthHelper()
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val offline: OfflineModeManager = mockk(relaxed = true)
    private val rebuilder: SearchIndexRebuilder = mockk(relaxed = true)
    private val mb: MusicBrainzService = mockk(relaxed = true)
    private val lfm: LastFmService = mockk(relaxed = true)

    private lateinit var runner: MetadataEnrichRunner

    @Before
    fun setUp() {
        mockkObject(SubsonicCredentials)
        every { SubsonicCredentials.username } returns "u"
        every { SubsonicCredentials.password } returns "p"
        every { offline.isOfflineEnabled() } returns false
        every { lfm.currentApiKey() } returns "key"
        runner = MetadataEnrichRunner(
            context,
            api,
            authHelper,
            metadataDao,
            offline,
            rebuilder,
            mb,
            lfm,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(SubsonicCredentials)
    }

    @Test
    fun `enrichSearchMetadata writes bio and schedules FTS rebuild`() = runTest {
        coEvery { metadataDao.getArtistsNeedingEnrichment(any()) } returns listOf(
            CachedArtistEntity(id = "ar-1", name = "A"),
        )
        coEvery { metadataDao.getArtistsNeedingAliases(any()) } returns emptyList()
        coEvery { metadataDao.getArtistsNeedingTags(any()) } returns emptyList()
        coEvery { metadataDao.getAlbumsNeedingEnrichment(any()) } returns emptyList()
        coEvery { api.getArtistInfo2("ar-1", any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "artistInfo2" to mapOf("biography" to "Long bio text"),
            ),
        )

        runner.enrichSearchMetadata()

        coVerify { metadataDao.setArtistBiography("ar-1", "Long bio text") }
        verify { rebuilder.scheduleRebuild() }
    }

    @Test
    fun `enrichSearchMetadata skips when offline`() = runTest {
        every { offline.isOfflineEnabled() } returns true

        runner.enrichSearchMetadata()

        coVerify(exactly = 0) { api.getArtistInfo2(any(), any()) }
        verify(exactly = 0) { rebuilder.scheduleRebuild() }
    }

    @Test
    fun `enrichSearchMetadata fills album notes`() = runTest {
        coEvery { metadataDao.getArtistsNeedingEnrichment(any()) } returns emptyList()
        coEvery { metadataDao.getArtistsNeedingAliases(any()) } returns emptyList()
        coEvery { metadataDao.getArtistsNeedingTags(any()) } returns emptyList()
        coEvery { metadataDao.getAlbumsNeedingEnrichment(any()) } returns listOf(
            CachedAlbumEntity(id = "al-1", name = "Album"),
        )
        coEvery { api.getAlbumInfo2("al-1", any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "albumInfo" to mapOf("notes" to "Album notes here"),
            ),
        )

        runner.enrichSearchMetadata()

        coVerify { metadataDao.setAlbumNotes("al-1", "Album notes here") }
        verify { rebuilder.scheduleRebuild() }
    }
}
