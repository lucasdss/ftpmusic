package com.lucasdss.ftpmusic.app.ui.search

import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.network.LastFmService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DiscoverArtistBioEnricherTest {

    private val lastFm: LastFmService = mockk()
    private val metadataDao: CachedMetadataDao = mockk()
    private lateinit var enricher: DiscoverArtistBioEnricher

    @Before
    fun setUp() {
        enricher = DiscoverArtistBioEnricher(lastFm, metadataDao)
    }

    @Test
    fun `uses library biography without lastfm`() = runTest {
        coEvery { metadataDao.getArtistById("local-1") } returns CachedArtistEntity(
            id = "local-1",
            name = "Band",
            biography = "Local bio text",
        )
        val hit = DiscoverArtistHit(
            name = "Band",
            inLibrary = true,
            localArtistId = "local-1",
            source = "musicbrainz",
        )
        val out = enricher.enrich(listOf(hit))
        assertEquals("Local bio text", out[0].biography)
        coVerify(exactly = 0) { lastFm.fetchArtistInfo(any(), any(), any()) }
    }

    @Test
    fun `fans out lastfm for top hits only`() = runTest {
        coEvery { lastFm.fetchArtistInfo("A", null, any()) } returns "Bio A"
        coEvery { lastFm.fetchArtistInfo("B", null, any()) } returns "Bio B"
        coEvery { lastFm.fetchArtistInfo("C", null, any()) } returns "Bio C"
        coEvery { lastFm.fetchArtistInfo("D", null, any()) } returns "Bio D"
        val hits = listOf("A", "B", "C", "D").map {
            DiscoverArtistHit(name = it, source = "deezer")
        }
        val out = enricher.enrich(hits)
        assertEquals("Bio A", out[0].biography)
        assertEquals("Bio B", out[1].biography)
        assertEquals("Bio C", out[2].biography)
        assertNull(out[3].biography)
        coVerify(exactly = 0) { lastFm.fetchArtistInfo("D", any(), any()) }
    }
}
