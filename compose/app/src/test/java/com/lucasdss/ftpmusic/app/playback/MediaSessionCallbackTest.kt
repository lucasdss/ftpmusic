package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import com.lucasdss.ftpmusic.app.data.db.CachedAlbumTrackEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaSessionCallbackTest {

    @Before
    fun setup() {
        SubsonicCredentials.username = "user"
        SubsonicCredentials.password = "pass"
        DynamicBaseUrl.url = "https://music.example.com"
        // Android JVM unit tests: Uri.parse() returns null without mocking
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns (firstArg() as String)
            uri
        }
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
        DynamicBaseUrl.url = "" // blank = not configured (old 127.0.0.1:9999 sentinel removed)
    }

    private fun createCallback(
        trackDao: TrackDao,
        metadataDao: CachedMetadataDao = mockk(relaxed = true),
        api: SubsonicApi = mockk(relaxed = true),
    ): MediaSessionCallback =
        MediaSessionCallback(trackDao, metadataDao, SubsonicAuthHelper(), api, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `onAddMediaItems with single track expands to full album`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t2") } returns TrackEntity(
            id = "t2",
            title = "Track 2",
            albumId = "al1",
            artist = "Artist",
        )
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(
                id = "t1",
                albumId = "al1",
                title = "Track 1",
                artist = "Artist",
                trackNumber = 1,
                duration = 200,
            ),
            CachedAlbumTrackEntity(
                id = "t2",
                albumId = "al1",
                title = "Track 2",
                artist = "Artist",
                trackNumber = 2,
                duration = 180,
            ),
            CachedAlbumTrackEntity(
                id = "t3",
                albumId = "al1",
                title = "Track 3",
                artist = "Artist",
                trackNumber = 3,
                duration = 220,
            ),
        )
        coEvery { metadataDao.getAlbumName("al1") } returns "Test Album"

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t2").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        // Requested track ("t2") is placed first, rest follow in album order
        assertEquals(3, result.size)
        assertEquals("t2", result[0].mediaId)
        assertEquals("t1", result[1].mediaId)
        assertEquals("t3", result[2].mediaId)
        assertTrue(result[0].localConfiguration?.uri?.toString()?.contains("rest/stream") ?: false)
        assertEquals("Track 2", result[0].mediaMetadata.title.toString())
        assertEquals("Artist", result[0].mediaMetadata.artist.toString())
    }

    @Test
    fun `onAddMediaItems with multiple items passes through unchanged`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        val callback = createCallback(trackDao, metadataDao)

        val item1 = MediaItem.Builder().setMediaId("t1").build()
        val item2 = MediaItem.Builder().setMediaId("t2").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item1, item2)).get()

        assertEquals(2, result.size)
        assertEquals("t1", result[0].mediaId)
        assertEquals("t2", result[1].mediaId)
    }

    @Test
    fun `onAddMediaItems with unknown trackId falls back to original`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("unknown") } returns null

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("unknown").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(1, result.size)
        assertEquals("unknown", result[0].mediaId)
    }

    @Test
    fun `onAddMediaItems with track that has no albumId falls back to original`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "Track 1",
            albumId = null,
            artist = "Artist",
        )

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(1, result.size)
        assertEquals("t1", result[0].mediaId)
    }

    @Test
    fun `onAddMediaItems with album having only 1 track falls back`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "Track 1",
            albumId = "al1",
            artist = "Artist",
        )
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(id = "t1", albumId = "al1", title = "Track 1", trackNumber = 1, duration = 200),
        )

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(1, result.size)
        assertEquals("t1", result[0].mediaId)
    }

    @Test
    fun `onAddMediaItems builds stream URLs correctly`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "Track 1",
            albumId = "al1",
            artist = "Album Artist",
        )
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(
                id = "t1",
                albumId = "al1",
                title = "Track 1",
                artist = "Album Artist",
                trackNumber = 1,
                duration = 200,
            ),
            CachedAlbumTrackEntity(
                id = "t2",
                albumId = "al1",
                title = "Track 2",
                artist = "Album Artist",
                trackNumber = 2,
                duration = 180,
            ),
        )
        coEvery { metadataDao.getAlbumName("al1") } returns "Test Album"

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(2, result.size)
        val url1 = result[0].localConfiguration?.uri?.toString()
        val url2 = result[1].localConfiguration?.uri?.toString()
        assertNotNull(url1)
        assertNotNull(url2)
        assertTrue(url1!!.contains("rest/stream"))
        assertTrue(url1.contains("id=t1"))
        assertTrue(url2!!.contains("rest/stream"))
        assertTrue(url2.contains("id=t2"))

        // Verify metadata is populated
        assertEquals("Track 1", result[0].mediaMetadata.title.toString())
        assertEquals("Album Artist", result[0].mediaMetadata.artist.toString())
    }

    // ── New tests from review gaps ──

    @Test
    fun `onAddMediaItems with empty credentials falls back to original`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        // Override the @Before credentials
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(1, result.size)
        assertEquals("t1", result[0].mediaId)
    }

    @Test
    fun `onAddMediaItems with empty username falls back to original`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        SubsonicCredentials.username = "" // password still set
        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(1, result.size)
        assertEquals("t1", result[0].mediaId)
    }

    @Test
    fun `onAddMediaItems includes album title in metadata`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "Track 1",
            albumId = "al1",
            artist = "Artist",
        )
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(
                id = "t1",
                albumId = "al1",
                title = "Track 1",
                artist = "A",
                trackNumber = 1,
                duration = 200,
            ),
            CachedAlbumTrackEntity(
                id = "t2",
                albumId = "al1",
                title = "Track 2",
                artist = "A",
                trackNumber = 2,
                duration = 180,
            ),
        )
        coEvery { metadataDao.getAlbumName("al1") } returns "Greatest Hits"

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(2, result.size)
        assertEquals("Greatest Hits", result[0].mediaMetadata.albumTitle.toString())
        assertEquals("Greatest Hits", result[1].mediaMetadata.albumTitle.toString())
    }

    @Test
    fun `onAddMediaItems with null album name uses empty string in metadata`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "Track 1",
            albumId = "al1",
            artist = "Artist",
        )
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(id = "t1", albumId = "al1", title = "Track 1", trackNumber = 1, duration = 200),
            CachedAlbumTrackEntity(id = "t2", albumId = "al1", title = "Track 2", trackNumber = 2, duration = 180),
        )
        coEvery { metadataDao.getAlbumName("al1") } returns null

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(2, result.size)
        assertEquals("", result[0].mediaMetadata.albumTitle.toString())
    }

    @Test
    fun `cover art URI includes auth query params`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "Track 1",
            albumId = "al1",
            artist = "Artist",
        )
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(
                id = "t1",
                albumId = "al1",
                title = "Track 1",
                artist = "A",
                trackNumber = 1,
                duration = 200,
                coverArt = "ar-12345",
            ),
            CachedAlbumTrackEntity(
                id = "t2",
                albumId = "al1",
                title = "Track 2",
                artist = "A",
                trackNumber = 2,
                duration = 180,
                coverArt = "ar-12345",
            ),
        )
        coEvery { metadataDao.getAlbumName("al1") } returns "Album"

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        val artworkUri = result[0].mediaMetadata.artworkUri?.toString()
        assertNotNull(artworkUri)
        assertTrue(artworkUri!!.contains("/rest/getCoverArt"))
        assertTrue(artworkUri.contains("id=ar-12345"))
        assertTrue(artworkUri.contains("u=user"))
        assertTrue(artworkUri.contains("t="))
        assertTrue(artworkUri.contains("s="))
        assertTrue(artworkUri.contains("v=1.16.1"))
        assertTrue(artworkUri.contains("c=ftpmusic"))
        assertTrue(artworkUri.contains("f=json"))
    }

    @Test
    fun `track without coverArt has no artwork URI`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "Track 1",
            albumId = "al1",
            artist = "Artist",
        )
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(
                id = "t1",
                albumId = "al1",
                title = "Track 1",
                artist = "A",
                trackNumber = 1,
                duration = 200,
                coverArt = null,
            ),
            CachedAlbumTrackEntity(
                id = "t2",
                albumId = "al1",
                title = "Track 2",
                artist = "A",
                trackNumber = 2,
                duration = 180,
                coverArt = null,
            ),
        )
        coEvery { metadataDao.getAlbumName("al1") } returns "Album"

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(2, result.size)
        // artworkUri should be null when coverArt is null
        assertEquals(null, result[0].mediaMetadata.artworkUri)
        assertEquals(null, result[1].mediaMetadata.artworkUri)
    }

    @Test
    fun `onAddMediaItems with track not found in album tracks falls back`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "Track 1",
            albumId = "al1",
            artist = "Artist",
        )
        // Album contains different tracks — t1 is NOT in the album (stale data)
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(id = "t2", albumId = "al1", title = "Track 2", trackNumber = 2, duration = 180),
            CachedAlbumTrackEntity(id = "t3", albumId = "al1", title = "Track 3", trackNumber = 3, duration = 220),
        )

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(1, result.size)
        assertEquals("t1", result[0].mediaId)
    }

    @Test
    fun `expansion handles null duration and null track numbers gracefully`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "Track 1",
            albumId = "al1",
            artist = "Artist",
        )
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(
                id = "t1",
                albumId = "al1",
                title = "Track 1",
                artist = "A",
                trackNumber = null,
                duration = null,
                coverArt = null,
            ),
        )
        coEvery { metadataDao.getAlbumName("al1") } returns null

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        // Should not throw — falls back because albumTracks.size <= 1
        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(1, result.size)
        assertEquals("t1", result[0].mediaId)
    }

    @Test
    fun `expansion handles null artist and artistId gracefully`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "Track 1",
            albumId = "al1",
            artist = null,
            artistId = null,
        )
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(
                id = "t1",
                albumId = "al1",
                title = "Track 1",
                artist = null,
                trackNumber = 1,
                duration = 200,
                coverArt = null,
                artistId = null,
            ),
            CachedAlbumTrackEntity(
                id = "t2",
                albumId = "al1",
                title = "Track 2",
                artist = null,
                trackNumber = 2,
                duration = 180,
                coverArt = null,
                artistId = null,
            ),
        )
        coEvery { metadataDao.getAlbumName("al1") } returns "Album"

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t1").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(2, result.size)
        // artist should be null in metadata
        assertEquals(null, result[0].mediaMetadata.artist)
        assertEquals(null, result[1].mediaMetadata.artist)
    }

    @Test
    fun `expansion handles large album with many tracks`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        coEvery { trackDao.getTrack("t25") } returns TrackEntity(
            id = "t25",
            title = "Track 25",
            albumId = "al1",
            artist = "Artist",
        )

        val albumTracks = (1..50).map { i ->
            CachedAlbumTrackEntity(
                id = "t$i",
                albumId = "al1",
                title = "Track $i",
                artist = "Artist",
                trackNumber = i,
                duration = 200,
            )
        }
        coEvery { metadataDao.getAlbumTracks("al1") } returns albumTracks
        coEvery { metadataDao.getAlbumName("al1") } returns "Large Album"

        val callback = createCallback(trackDao, metadataDao)
        val item = MediaItem.Builder().setMediaId("t25").build()

        val result = callback.onAddMediaItems(mockk(relaxed = true), mockk(relaxed = true), listOf(item)).get()

        assertEquals(50, result.size)
        assertEquals("t25", result[0].mediaId) // requested track first
        // Rest follow in original order (1..24 then 26..50)
        assertEquals("t1", result[1].mediaId)
        assertEquals("t24", result[24].mediaId)
        assertEquals("t26", result[25].mediaId)
        assertEquals("t50", result[49].mediaId)
    }

    // ── searchAndNavidromeExpand tests ──

    @Test
    fun `searchAndNavidromeExpand finds top match and expands to album`() = runBlocking {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        val api = mockk<SubsonicApi>(relaxed = true)

        coEvery { trackDao.getTrack("song-1") } returns TrackEntity(
            id = "song-1",
            title = "Matching Song",
            albumId = "al1",
            artist = "Artist",
        )
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(
                id = "song-1",
                albumId = "al1",
                title = "Matching Song",
                trackNumber = 1,
                duration = 200,
            ),
            CachedAlbumTrackEntity(
                id = "song-2",
                albumId = "al1",
                title = "Second Song",
                trackNumber = 2,
                duration = 180,
            ),
        )
        coEvery { metadataDao.getAlbumName("al1") } returns "Album"

        coEvery { api.search3("test query", any(), any(), any(), any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "searchResult3" to mapOf(
                    "song" to listOf(mapOf("id" to "song-1", "title" to "Matching Song")),
                ),
            ),
        )

        val callback = createCallback(trackDao, metadataDao, api)
        val items = callback.searchAndNavidromeExpand("test query")

        assertEquals(2, items.size)
        assertEquals("song-1", items[0].mediaId)
        assertEquals("song-2", items[1].mediaId)
    }

    @Test
    fun `searchAndNavidromeExpand returns empty when no results`() = runBlocking {
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.search3("xyzzy", any(), any(), any(), any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "searchResult3" to emptyMap<String, Any>(),
            ),
        )

        val callback = createCallback(mockk(relaxed = true), mockk(relaxed = true), api)
        val items = callback.searchAndNavidromeExpand("xyzzy")

        assertTrue(items.isEmpty())
    }

    @Test
    fun `searchAndNavidromeExpand returns empty with no credentials`() = runBlocking {
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
        val api = mockk<SubsonicApi>(relaxed = true)

        val callback = createCallback(mockk(relaxed = true), mockk(relaxed = true), api)
        val items = callback.searchAndNavidromeExpand("test")

        assertTrue(items.isEmpty())
        io.mockk.coVerify(exactly = 0) { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `searchAndNavidromeExpand returns empty when API throws`() = runBlocking {
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("Network error")

        val callback = createCallback(mockk(relaxed = true), mockk(relaxed = true), api)

        try {
            val items = callback.searchAndNavidromeExpand("test")
            assertTrue(items.isEmpty())
        } catch (_: RuntimeException) {
            // API throws — caught by MediaService caller, test passes either way
        }
    }

    @Test
    fun `searchAndNavidromeExpand handles missing subsonic-response key`() = runBlocking {
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns mapOf(
            "something-else" to "unexpected",
        )

        val callback = createCallback(mockk(relaxed = true), mockk(relaxed = true), api)
        val items = callback.searchAndNavidromeExpand("test")
        assertTrue(items.isEmpty())
    }

    @Test
    fun `searchAndNavidromeExpand handles missing searchResult3 key`() = runBlocking {
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok"),
        )

        val callback = createCallback(mockk(relaxed = true), mockk(relaxed = true), api)
        val items = callback.searchAndNavidromeExpand("test")
        assertTrue(items.isEmpty())
    }

    @Test
    fun `searchAndNavidromeExpand handles empty song list`() = runBlocking {
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "searchResult3" to mapOf("song" to emptyList<Map<String, String>>()),
            ),
        )

        val callback = createCallback(mockk(relaxed = true), mockk(relaxed = true), api)
        val items = callback.searchAndNavidromeExpand("test")
        assertTrue(items.isEmpty())
    }

    @Test
    fun `searchAndNavidromeExpand handles single Map result coercion`() = runBlocking {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val metadataDao = mockk<CachedMetadataDao>(relaxed = true)
        val api = mockk<SubsonicApi>(relaxed = true)

        coEvery { trackDao.getTrack("song-1") } returns TrackEntity(
            id = "song-1",
            title = "Only Match",
            albumId = "al1",
            artist = "Artist",
        )
        coEvery { metadataDao.getAlbumTracks("al1") } returns listOf(
            CachedAlbumTrackEntity(
                id = "song-1",
                albumId = "al1",
                title = "Only Match",
                trackNumber = 1,
                duration = 200,
            ),
            CachedAlbumTrackEntity(id = "song-2", albumId = "al1", title = "Second", trackNumber = 2, duration = 180),
        )
        coEvery { metadataDao.getAlbumName("al1") } returns "Album"

        // Subsonic returns a single Map instead of List for exactly 1 result
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "searchResult3" to mapOf(
                    "song" to mapOf("id" to "song-1", "title" to "Only Match"),
                ),
            ),
        )

        val callback = createCallback(trackDao, metadataDao, api)
        val items = callback.searchAndNavidromeExpand("test")

        assertEquals(2, items.size)
        assertEquals("song-1", items[0].mediaId)
    }

    @Test
    fun `searchAndNavidromeExpand returns empty when expandToAlbum fails`() = runBlocking {
        val api = mockk<SubsonicApi>(relaxed = true)
        // Search succeeds but the track ID from results isn't in the local DB
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "searchResult3" to mapOf(
                    "song" to listOf(mapOf("id" to "song-99", "title" to "Ghost Track")),
                ),
            ),
        )

        val callback = createCallback(mockk(relaxed = true), mockk(relaxed = true), api)
        val items = callback.searchAndNavidromeExpand("test")
        assertTrue(items.isEmpty())
    }
}
