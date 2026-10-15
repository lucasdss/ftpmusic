package com.lucasdss.ftpmusic.app.ui.search

import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DiscoverLibraryResolverTest {

    private val metadataDao: CachedMetadataDao = mockk()
    private val trackDao: TrackDao = mockk()
    private lateinit var resolver: DiscoverLibraryResolver

    @Before
    fun setUp() {
        resolver = DiscoverLibraryResolver(metadataDao, trackDao)
    }

    @Test
    fun `resolveAlbum null artist skips DAO`() = runTest {
        assertNull(resolver.resolveAlbum("Thriller", null))
        coVerify(exactly = 0) { metadataDao.getAlbumByExactName(any(), any()) }
    }

    @Test
    fun `resolveAlbum blank artist skips DAO`() = runTest {
        assertNull(resolver.resolveAlbum("Thriller", "  "))
        coVerify(exactly = 0) { metadataDao.getAlbumByExactName(any(), any()) }
    }

    @Test
    fun `resolveAlbum title and artist hits entity`() = runTest {
        val album = CachedAlbumEntity(id = "a1", name = "Thriller", artist = "MJ")
        coEvery { metadataDao.getAlbumByExactName("Thriller", "MJ") } returns album
        assertEquals(album, resolver.resolveAlbum("Thriller", "MJ"))
    }

    @Test
    fun `resolveTrack blank artist without mbid returns null`() = runTest {
        assertNull(resolver.resolveTrack(null, "Song", null))
        assertNull(resolver.resolveTrack(null, "Song", " "))
        coVerify(exactly = 0) { trackDao.getTrackByExactTitleArtist(any(), any()) }
    }

    @Test
    fun `resolveTrack mbid short-circuits before title artist`() = runTest {
        val track = TrackEntity(id = "t1", title = "Song", artist = "Band", musicbrainzId = "mb-1")
        coEvery { trackDao.getTrackByMbid("mb-1") } returns track
        assertEquals(track, resolver.resolveTrack("mb-1", "Other", null))
        coVerify(exactly = 0) { trackDao.getTrackByExactTitleArtist(any(), any()) }
    }

    @Test
    fun `resolveTrack title and artist hits entity`() = runTest {
        val track = TrackEntity(id = "t2", title = "Song", artist = "Band")
        coEvery { trackDao.getTrackByExactTitleArtist("Song", "Band") } returns track
        assertEquals(track, resolver.resolveTrack(null, "Song", "Band"))
    }

    @Test
    fun `resolveArtist mbid hit short-circuits`() = runTest {
        val artist = CachedArtistEntity(id = "ar1", name = "Band", musicbrainzId = "mb-a")
        coEvery { metadataDao.getArtistByMbid("mb-a") } returns artist
        assertEquals(artist, resolver.resolveArtist("mb-a", "Other"))
        coVerify(exactly = 0) { metadataDao.getArtistByExactName(any()) }
    }

    @Test
    fun `resolveArtist falls back to exact name`() = runTest {
        val artist = CachedArtistEntity(id = "ar2", name = "Band")
        coEvery { metadataDao.getArtistByMbid(any()) } returns null
        coEvery { metadataDao.getArtistByExactName("Band") } returns artist
        assertEquals(artist, resolver.resolveArtist("missing", "Band"))
    }

    @Test
    fun `resolveArtist curly apostrophe matches normalized library name`() = runTest {
        val artist = CachedArtistEntity(id = "gnr", name = "Guns N' Roses")
        coEvery { metadataDao.getArtistByMbid(any()) } returns null
        coEvery { metadataDao.getArtistByExactName("Guns N\u2019 Roses") } returns null
        coEvery { metadataDao.getArtistByNormalizedName("guns n' roses") } returns artist
        assertEquals(artist, resolver.resolveArtist(null, "Guns N\u2019 Roses"))
        coVerify(exactly = 0) { metadataDao.getArtistByAliasContains(any()) }
    }

    @Test
    fun `resolveArtist falls back to search aliases`() = runTest {
        val artist = CachedArtistEntity(
            id = "ar3",
            name = "Canonical",
            searchAliases = "guns n' roses aka",
        )
        coEvery { metadataDao.getArtistByMbid(any()) } returns null
        coEvery { metadataDao.getArtistByExactName(any()) } returns null
        coEvery { metadataDao.getArtistByNormalizedName(any()) } returns null
        coEvery { metadataDao.getArtistByAliasContains("guns n' roses") } returns artist
        assertEquals(artist, resolver.resolveArtist(null, "Guns N' Roses"))
    }
}
