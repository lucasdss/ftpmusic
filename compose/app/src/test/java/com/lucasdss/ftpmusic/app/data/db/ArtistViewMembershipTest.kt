package com.lucasdss.ftpmusic.app.data.db

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistViewMembershipTest {

    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)

    @Test
    fun `loadAlbums merges id name and track-artist sources distinct by id`() = runTest {
        coEvery { metadataDao.getAlbumsByArtistId("ar-1") } returns listOf(
            CachedAlbumEntity(id = "a1", name = "Own", artist = "X"),
            CachedAlbumEntity(id = "shared", name = "Shared", artist = "X"),
        )
        coEvery { metadataDao.getAlbumsByArtistName("X") } returns listOf(
            CachedAlbumEntity(id = "shared", name = "Shared", artist = "X"),
            CachedAlbumEntity(id = "a2", name = "ByName", artist = "X"),
        )
        coEvery { metadataDao.getAlbumsByTrackArtistId("ar-1") } returns listOf(
            CachedAlbumEntity(id = "ost", name = "OST", artist = "Various"),
        )

        val albums = ArtistViewMembership.loadAlbums(metadataDao, "ar-1", "X")
        assertEquals(listOf("a1", "shared", "a2", "ost"), albums.map { it.id })
    }

    @Test
    fun `loadAllTracks delegates to artist-view DAO with empty name when null`() = runTest {
        coEvery { trackDao.getAllTracksForArtistView("ar-1", "") } returns listOf(
            TrackEntity(id = "t1", title = "One", artistId = "ar-1"),
        )
        val tracks = ArtistViewMembership.loadAllTracks(trackDao, "ar-1", null)
        assertEquals(1, tracks.size)
        coVerify { trackDao.getAllTracksForArtistView("ar-1", "") }
    }

    @Test
    fun `loadTracksPage uses after-cursor variant when cursor set`() = runTest {
        coEvery {
            trackDao.getTracksForArtistViewAfter("ar-1", "X", "M", 50)
        } returns emptyList()
        ArtistViewMembership.loadTracksPage(trackDao, "ar-1", "X", 50, titleCursor = "M")
        coVerify { trackDao.getTracksForArtistViewAfter("ar-1", "X", "M", 50) }
    }
}
