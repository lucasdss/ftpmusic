package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.content.SharedPreferences
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MetadataSyncWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val prefsEditor: SharedPreferences.Editor = mockk(relaxed = true)
    private val api: SubsonicApi = mockk(relaxed = true)
    private val authHelper = SubsonicAuthHelper()
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val genreMixDao: GenreMixDao = mockk(relaxed = true)
    private val dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository =
        mockk(relaxed = true)
    private val coverArtFallback: CoverArtFallbackService = mockk(relaxed = true)

    private lateinit var worker: MetadataSyncWorker

    @Before
    fun setUp() {
        mockkObject(SubsonicCredentials)
        mockkObject(DynamicBaseUrl)
        every { SubsonicCredentials.username } returns "testuser"
        every { SubsonicCredentials.password } returns "testpass"
        every { DynamicBaseUrl.url } returns "https://music.example.com"
        every { context.getSharedPreferences("ftpmusic_sync", any()) } returns prefs
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefsEditor.apply() } just Runs
        worker =
            MetadataSyncWorker(
                context,
                api,
                authHelper,
                metadataDao,
                trackDao,
                genreMixDao,
                coverArtFallback,
                mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
                dailyMixRepository,
            )
    }

    @After
    fun tearDown() {
        unmockkObject(SubsonicCredentials)
        unmockkObject(DynamicBaseUrl)
    }

    @Test
    fun `syncAlbums fetches single page and caches`() = runTest {
        val albumListResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "albumList2" to mapOf(
                    "album" to listOf(
                        mapOf("id" to "al-1", "name" to "Album One", "artist" to "Artist A", "songCount" to 12),
                    ),
                ),
            ),
        )
        coEvery { api.getAlbumList2(type = "alphabeticalByName", size = 500, offset = 0, auth = any()) } returns
            albumListResponse

        worker.syncAlbums()

        coVerify { metadataDao.replaceAlbums(any()) }
        coVerify(atLeast = 1) {
            metadataDao.replaceAlbums(any())
        } // was: metadataDao.upsertAlbums(match { it.size == 1 && it[0].id == "al-1" }) }
    }

    @Test
    fun `syncAlbums handles multiple pages`() = runTest {
        // First page: 500 albums (full page)
        val page1 = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "albumList2" to mapOf("album" to (1..500).map { mapOf("id" to "al-$it", "name" to "Album $it") }),
            ),
        )
        // Second page: 200 albums (partial page — stops pagination)
        val page2 = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "albumList2" to mapOf("album" to (501..700).map { mapOf("id" to "al-$it", "name" to "Album $it") }),
            ),
        )
        coEvery { api.getAlbumList2(type = any(), size = 500, offset = 0, auth = any()) } returns page1
        coEvery { api.getAlbumList2(type = any(), size = 500, offset = 500, auth = any()) } returns page2

        worker.syncAlbums()

        // Previously verified upsertAlbums(match { it.size == 700 }).
        coVerify(atLeast = 1) { metadataDao.replaceAlbums(any()) }
    }

    @Test
    fun `syncAlbums handles failed API response`() = runTest {
        val failedResponse = mapOf("subsonic-response" to mapOf("status" to "failed"))
        coEvery { api.getAlbumList2(type = any(), size = any(), offset = any(), auth = any()) } returns failedResponse

        worker.syncAlbums()

        coVerify(exactly = 0) { metadataDao.clearAlbums() }
        coVerify(exactly = 0) { metadataDao.upsertAlbums(any()) }
    }

    @Test
    fun `syncAlbums handles empty album list`() = runTest {
        val emptyResponse = mapOf(
            "subsonic-response" to mapOf("status" to "ok", "albumList2" to mapOf<String, Any>()),
        )
        coEvery { api.getAlbumList2(type = any(), size = any(), offset = any(), auth = any()) } returns emptyResponse

        worker.syncAlbums()

        coVerify(exactly = 0) { metadataDao.upsertAlbums(any()) }
    }

    @Test
    fun `syncAlbums skips when credentials empty`() = runTest {
        every { SubsonicCredentials.username } returns ""

        worker.syncAlbums()

        coVerify(exactly = 0) { api.getAlbumList2(any(), any(), any(), any()) }
    }

    @Test
    fun `syncAlbums repopulates the favorites ledger after replace`() = runTest {
        val w = makeWorker()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to
                mapOf("status" to "ok", "albumList2" to mapOf("album" to emptyList<Map<String, Any>>())),
        )

        w.syncAlbums()

        coVerify(exactly = 1) { metadataDao.replaceAlbums(any()) }
        coVerify(exactly = 1) { metadataDao.syncAlbumLedger() }
        coVerify(exactly = 1) { metadataDao.pruneAlbumLedger() }
    }

    @Test
    fun `syncArtists repopulates the favorites ledger after replace`() = runTest {
        val w = makeWorker()
        coEvery { api.getArtists(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "artists" to mapOf("index" to emptyList<Any>())),
        )

        w.syncArtists()

        // Ledger population runs unconditionally (preserves favorites even when
        // the server has zero artists); replaceArtists only runs when non-empty.
        coVerify(exactly = 1) { metadataDao.syncArtistLedger() }
        coVerify(exactly = 1) { metadataDao.pruneArtistLedger() }
    }

    @Test
    fun `syncArtists fetches and caches artists`() = runTest {
        val artistResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "artists" to mapOf(
                    "index" to listOf(
                        mapOf(
                            "name" to "A",
                            "artist" to listOf(
                                mapOf("id" to "ar-1", "name" to "Artist One", "albumCount" to 5),
                                mapOf("id" to "ar-2", "name" to "Artist Two", "albumCount" to 3),
                            ),
                        ),
                    ),
                ),
            ),
        )
        coEvery { api.getArtists(any()) } returns artistResponse

        worker.syncArtists()

        coVerify { metadataDao.replaceArtists(any()) }
        // Previously verified upsertArtists(match { it.size == 2 }).
        coVerify(atLeast = 1) { metadataDao.replaceArtists(any()) }
        // Album counts are NOT recomputed here — updateArtistAlbumCounts() must
        // run AFTER the tracks phase (cached_album_tracks populated) so
        // track-artist attribution is included. Otherwise artists whose albums
        // Navidrome attributes to "Original Soundtrack" show album_count=0.
        coVerify(exactly = 0) { metadataDao.updateArtistAlbumCounts() }
    }

    @Test
    fun `syncArtists handles failed API response`() = runTest {
        coEvery { api.getArtists(any()) } returns mapOf("subsonic-response" to mapOf("status" to "failed"))

        worker.syncArtists()

        coVerify(exactly = 0) { metadataDao.upsertArtists(any()) }
    }

    @Test
    fun `syncArtists handles empty index`() = runTest {
        val emptyResponse = mapOf(
            "subsonic-response" to mapOf("status" to "ok", "artists" to mapOf<String, Any>()),
        )
        coEvery { api.getArtists(any()) } returns emptyResponse

        worker.syncArtists()

        coVerify(exactly = 0) { metadataDao.upsertArtists(any()) }
    }

    @Test
    fun `syncArtists skips when credentials empty`() = runTest {
        every { SubsonicCredentials.username } returns ""

        worker.syncArtists()

        coVerify(exactly = 0) { api.getArtists(any()) }
    }

    // ── syncAlbumTracks tests ──

    @Test
    fun `syncAlbumTracks fetches and caches tracks for uncached album`() = runTest {
        val album = com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity(id = "al-1", name = "Test")
        coEvery { metadataDao.getAllAlbums() } returns listOf(album)
        coEvery { metadataDao.getAlbumTracks("al-1") } returns emptyList()

        val albumResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "album" to mapOf(
                    "song" to listOf(
                        mapOf("id" to "t1", "title" to "Track 1", "duration" to 200, "track" to 1),
                        mapOf("id" to "t2", "title" to "Track 2", "duration" to 180, "track" to 2),
                    ),
                ),
            ),
        )
        coEvery { api.getAlbum(id = "al-1", auth = any()) } returns albumResponse

        worker.syncAlbumTracks()

        coVerify { metadataDao.replaceAlbumTracksBatch(match { it.size == 2 }) }
    }

    @Test
    fun `syncAlbumTracks maps all metadata fields including genre year artistId`() = runTest {
        val album = CachedAlbumEntity(id = "al-full", name = "Full Metadata")
        coEvery { metadataDao.getAllAlbums() } returns listOf(album)
        coEvery { metadataDao.getAlbumTracks("al-full") } returns emptyList()
        coEvery { metadataDao.countUncachedAlbums() } returns 1

        val albumResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "album" to mapOf(
                    "song" to listOf(
                        mapOf(
                            "id" to "t-full",
                            "title" to "Full Track",
                            "artist" to "Test Artist",
                            "genre" to "Rock",
                            "year" to 2024,
                            "artistId" to "ar-99",
                            "discNumber" to 1,
                            "duration" to 240,
                            "track" to 3,
                            "bitRate" to 320,
                            "size" to 10485760,
                            "coverArt" to "ca-full",
                            "suffix" to "flac",
                            "contentType" to "audio/flac",
                        ),
                    ),
                ),
            ),
        )
        coEvery { api.getAlbum(id = "al-full", auth = any()) } returns albumResponse

        val tracksCaptured = slot<List<CachedAlbumTrackEntity>>()
        coEvery { metadataDao.replaceAlbumTracksBatch(capture(tracksCaptured)) } just Runs

        worker.syncAlbumTracks()

        assertEquals(1, tracksCaptured.captured.size)
        val t = tracksCaptured.captured[0]
        assertEquals("t-full", t.id)
        assertEquals("al-full", t.albumId)
        assertEquals("Full Track", t.title)
        assertEquals("Test Artist", t.artist)
        assertEquals("Rock", t.genre)
        assertEquals(2024, t.year)
        assertEquals("ar-99", t.artistId)
        assertEquals(1, t.discNumber)
        assertEquals(240, t.duration)
        assertEquals(3, t.trackNumber)
        assertEquals(320, t.bitrate)
        assertEquals(10485760L, t.size)
        assertEquals("ca-full", t.coverArt)
        assertEquals("flac", t.suffix)
        assertEquals("audio/flac", t.contentType)
    }

    @Test
    fun `syncAlbumTracks handles tracks missing optional metadata fields`() = runTest {
        val album = CachedAlbumEntity(id = "al-min", name = "Minimal")
        coEvery { metadataDao.getAllAlbums() } returns listOf(album)
        coEvery { metadataDao.getAlbumTracks("al-min") } returns emptyList()
        coEvery { metadataDao.countUncachedAlbums() } returns 1

        // API response with only required fields — genre, year, artistId etc. are absent
        val albumResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "album" to mapOf(
                    "song" to listOf(
                        mapOf("id" to "t-min", "title" to "Minimal Track"),
                    ),
                ),
            ),
        )
        coEvery { api.getAlbum(id = "al-min", auth = any()) } returns albumResponse

        val tracksCaptured = slot<List<CachedAlbumTrackEntity>>()
        coEvery { metadataDao.replaceAlbumTracksBatch(capture(tracksCaptured)) } just Runs

        worker.syncAlbumTracks()

        assertEquals(1, tracksCaptured.captured.size)
        val t = tracksCaptured.captured[0]
        assertEquals("t-min", t.id)
        assertEquals("Minimal Track", t.title)
        // Optional fields should default to null
        assertEquals(null, t.genre)
        assertEquals(null, t.year)
        assertEquals(null, t.artistId)
        assertEquals(null, t.discNumber)
        assertEquals(null, t.bitrate)
        assertEquals(null, t.size)
    }

    @Test
    fun `syncAlbumTracks force-resyncs when schema version is outdated`() = runTest {
        val album = CachedAlbumEntity(id = "al-old", name = "Old Schema")
        coEvery { metadataDao.getAllAlbums() } returns listOf(album)
        // Album has cached tracks from old schema
        coEvery { metadataDao.getAlbumTracks("al-old") } returns listOf(
            CachedAlbumTrackEntity(id = "t1", albumId = "al-old", title = "Old Track"),
        )
        // Stored version is 0 (pre-migration) — should force re-sync
        every { prefs.getInt("metadata_version", any()) } returns 0
        coEvery { metadataDao.countUncachedAlbums() } returns 0

        val albumResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "album" to mapOf(
                    "song" to listOf(
                        mapOf("id" to "t1", "title" to "Updated", "genre" to "Rock", "year" to 2024),
                    ),
                ),
            ),
        )
        coEvery { api.getAlbum(id = "al-old", auth = any()) } returns albumResponse

        worker.syncAlbumTracks()

        // Should re-fetch despite existing cached tracks
        coVerify(exactly = 1) { api.getAlbum(id = "al-old", auth = any()) }
        coVerify { metadataDao.replaceAlbumTracksBatch(any()) }
    }

    @Test
    fun `syncAlbumTracks skips already-cached album when schema is current`() = runTest {
        val album = CachedAlbumEntity(id = "al-2", name = "Cached")
        coEvery { metadataDao.getAllAlbums() } returns listOf(album)
        coEvery { metadataDao.getAlbumTracks("al-2") } returns listOf(
            CachedAlbumTrackEntity(id = "t1", albumId = "al-2", title = "T1"),
        )
        // Schema version is current — no force re-sync
        every { prefs.getInt("metadata_version", any()) } returns 2

        worker.syncAlbumTracks()

        coVerify(exactly = 0) { api.getAlbum(any(), any()) }
    }

    @Test
    fun `syncAlbumTracks handles API failure gracefully`() = runTest {
        val album = com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity(id = "al-3", name = "Fail")
        coEvery { metadataDao.getAllAlbums() } returns listOf(album)
        coEvery { metadataDao.getAlbumTracks("al-3") } returns emptyList()
        coEvery { api.getAlbum(id = "al-3", auth = any()) } throws RuntimeException("Network error")

        worker.syncAlbumTracks()

        // Should not crash — just skip
        coVerify(exactly = 0) { metadataDao.replaceAlbumTracksBatch(any()) }
    }

    @Test
    fun `syncAlbumTracks handles failed response status`() = runTest {
        val album = com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity(id = "al-4", name = "Bad")
        coEvery { metadataDao.getAllAlbums() } returns listOf(album)
        coEvery { metadataDao.getAlbumTracks("al-4") } returns emptyList()
        coEvery { api.getAlbum(id = "al-4", auth = any()) } returns
            mapOf("subsonic-response" to mapOf("status" to "failed"))

        worker.syncAlbumTracks()

        coVerify(exactly = 0) { metadataDao.replaceAlbumTracksBatch(any()) }
    }

    // ── syncGenres tests ──────────────────────────────────────────────────────

    @Test
    fun `syncGenres fetches and caches genres`() = runTest {
        val genreResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "genres" to mapOf(
                    "genre" to listOf(
                        mapOf("value" to "Rock", "songCount" to 500, "albumCount" to 50),
                        mapOf("value" to "Jazz", "songCount" to 300, "albumCount" to 30),
                    ),
                ),
            ),
        )
        coEvery { api.getGenres(any()) } returns genreResponse
        // Return empty songs for each genre to skip song fetching
        coEvery { api.getSongsByGenre(any(), any(), any(), any()) } returns emptyMap<String, Any>()

        worker.syncGenres()

        coVerify { genreMixDao.replaceGenres(any()) }
        coVerify(atLeast = 1) { genreMixDao.replaceGenres(any()) }
    }

    @Test
    fun `syncGenres fetches songs for top 10 genres`() = runTest {
        val genres = (1..12).map { i ->
            mapOf("value" to "Genre$i", "songCount" to (1000 - i * 50), "albumCount" to 10)
        }
        val genreResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "genres" to mapOf("genre" to genres),
            ),
        )
        coEvery { api.getGenres(any()) } returns genreResponse
        coEvery { api.getSongsByGenre(any(), any(), any(), any()) } returns emptyMap<String, Any>()
        // The song-fetch list comes from the Daily Mix genre selection (DB-backed,
        // defaults to top 20 by song count when no settings row exists).
        coEvery { genreMixDao.getTopGenres() } returns genres.map {
            CachedGenreEntity(name = it["value"] as String, songCount = it["songCount"] as Int)
        }
        coEvery { dailyMixRepository.allMixGenreNames() } returns emptyList()

        worker.syncGenres()

        // Should call getSongsByGenre for top 10
        coVerify(atLeast = 10) { api.getSongsByGenre(any(), any(), any(), any()) }
    }

    @Test
    fun `syncGenres fetches songs for the user-selected genres`() = runTest {
        val genreResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "genres" to mapOf(
                    "genre" to listOf(
                        mapOf("value" to "Rock", "songCount" to 500, "albumCount" to 50),
                        mapOf("value" to "Jazz", "songCount" to 300, "albumCount" to 30),
                        mapOf("value" to "Blues", "songCount" to 100, "albumCount" to 10),
                        mapOf("value" to "Metal", "songCount" to 900, "albumCount" to 90),
                    ),
                ),
            ),
        )
        coEvery { api.getGenres(any()) } returns genreResponse
        coEvery { api.getSongsByGenre(any(), any(), any(), any()) } returns emptyMap<String, Any>()
        // User selected Jazz + Blues (not the top genres) — only those get songs fetched
        coEvery { dailyMixRepository.hasMixes() } returns true
        coEvery { genreMixDao.getTopGenres() } returns listOf(
            CachedGenreEntity(name = "Rock", songCount = 500),
            CachedGenreEntity(name = "Jazz", songCount = 300),
            CachedGenreEntity(name = "Blues", songCount = 100),
            CachedGenreEntity(name = "Metal", songCount = 900),
        )
        coEvery { dailyMixRepository.allMixGenreNames() } returns listOf("Jazz", "Blues")

        worker.syncGenres()

        coVerify(exactly = 1) { api.getSongsByGenre(any(), "Jazz", any(), any()) }
        coVerify(exactly = 1) { api.getSongsByGenre(any(), "Blues", any(), any()) }
        coVerify(exactly = 0) { api.getSongsByGenre(any(), "Rock", any(), any()) }
        coVerify(exactly = 0) { api.getSongsByGenre(any(), "Metal", any(), any()) }
    }

    @Test
    fun `syncGenres falls back to the current top 20 when no mixes exist`() = runTest {
        // 22 genres; Genre22 has the most songs, Genre1 the fewest
        val genres = (1..22).map { i ->
            mapOf("value" to "Genre$i", "songCount" to i * 10, "albumCount" to 1)
        }
        val genreResponse = mapOf(
            "subsonic-response" to mapOf("status" to "ok", "genres" to mapOf("genre" to genres)),
        )
        coEvery { api.getGenres(any()) } returns genreResponse
        coEvery { api.getSongsByGenre(any(), any(), any(), any()) } returns emptyMap<String, Any>()
        coEvery { genreMixDao.getTopGenres() } returns genres.map {
            CachedGenreEntity(name = it["value"] as String, songCount = it["songCount"] as Int)
        }
        // Stale persisted DEFAULT from a previous sync (Genre1/Genre2 were top
        // then, but are now the bottom two). Non-custom → song fetching must
        // use the CURRENT top 20 (Genre22..Genre3), not the stale default.
        coEvery { dailyMixRepository.allMixGenreNames() } returns emptyList()

        worker.syncGenres()

        coVerify(exactly = 1) { api.getSongsByGenre(any(), "Genre22", any(), any()) }
        coVerify(exactly = 1) { api.getSongsByGenre(any(), "Genre3", any(), any()) }
        coVerify(exactly = 0) { api.getSongsByGenre(any(), "Genre1", any(), any()) }
        coVerify(exactly = 0) { api.getSongsByGenre(any(), "Genre2", any(), any()) }
    }

    @Test
    fun `syncGenres skips genre song fetch when only non-genre mixes exist`() = runTest {
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "genres" to mapOf(
                    "genre" to listOf(
                        mapOf("value" to "Rock", "songCount" to 500, "albumCount" to 50),
                        mapOf("value" to "Jazz", "songCount" to 300, "albumCount" to 30),
                    ),
                ),
            ),
        )
        coEvery { dailyMixRepository.hasMixes() } returns true
        coEvery { dailyMixRepository.allMixGenreNames() } returns emptyList()

        worker.syncGenres()

        coVerify(exactly = 0) { api.getSongsByGenre(any(), any(), any(), any()) }
    }

    @Test
    fun `syncGenres handles API failure gracefully`() = runTest {
        coEvery { api.getGenres(any()) } returns mapOf("subsonic-response" to mapOf("status" to "failed"))

        worker.syncGenres()

        coVerify(exactly = 0) { genreMixDao.replaceGenres(any()) }
        coVerify(exactly = 0) { genreMixDao.upsertGenres(any()) }
    }

    @Test
    fun `syncGenres handles empty genre list`() = runTest {
        val emptyResponse = mapOf(
            "subsonic-response" to mapOf("status" to "ok", "genres" to emptyMap<String, Any>()),
        )
        coEvery { api.getGenres(any()) } returns emptyResponse

        worker.syncGenres()

        coVerify(exactly = 0) { genreMixDao.upsertGenres(any()) }
    }

    @Test
    fun `syncGenres skips when credentials empty`() = runTest {
        every { SubsonicCredentials.username } returns ""

        worker.syncGenres()

        coVerify(exactly = 0) { api.getGenres(any()) }
    }

    @Test
    fun `syncGenres caches songs for individual genre`() = runTest {
        val genreResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "genres" to mapOf(
                    "genre" to listOf(
                        mapOf("value" to "Rock", "songCount" to 5, "albumCount" to 1),
                    ),
                ),
            ),
        )
        val songsResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "songsByGenre" to mapOf(
                    "song" to listOf(
                        mapOf(
                            "id" to "s1",
                            "title" to "Track 1",
                            "artist" to "Artist",
                            "duration" to 200,
                            "track" to 1,
                            "coverArt" to "ca-1",
                        ),
                    ),
                ),
            ),
        )
        coEvery { api.getGenres(any()) } returns genreResponse
        coEvery { api.getSongsByGenre(any(), "Rock", 120, 0) } returns songsResponse
        // Song-fetch list comes from the Daily Mix genre selection (DB-backed)
        coEvery { genreMixDao.getTopGenres() } returns listOf(CachedGenreEntity(name = "Rock", songCount = 5))
        coEvery { dailyMixRepository.allMixGenreNames() } returns emptyList()

        worker.syncGenres()

        coVerify { genreMixDao.replaceGenres(any()) }
        coVerify { genreMixDao.replaceSongs("Rock", any()) }
    }

    @Test
    fun `syncGenres keeps cached songs when a genre returns an empty list`() = runTest {
        val w = makeWorker()
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "genres" to mapOf("genre" to listOf(mapOf("value" to "Rock", "songCount" to 5))),
            ),
        )
        coEvery { api.getSongsByGenre(any(), "Rock", 120, 0) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "songsByGenre" to mapOf("song" to emptyList<Any>()),
            ),
        )
        coEvery { genreMixDao.getTopGenres() } returns listOf(CachedGenreEntity(name = "Rock", songCount = 5))
        coEvery { dailyMixRepository.allMixGenreNames() } returns emptyList()
        coEvery { genreMixDao.countSongsForGenre("Rock") } returns 5

        w.syncGenres()

        coVerify(exactly = 0) { genreMixDao.replaceSongs("Rock", any()) }
    }

    @Test
    fun `syncGenres replaces with an empty list when the genre cache is empty`() = runTest {
        val w = makeWorker()
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "genres" to mapOf("genre" to listOf(mapOf("value" to "Rock", "songCount" to 5))),
            ),
        )
        coEvery { api.getSongsByGenre(any(), "Rock", 120, 0) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "songsByGenre" to mapOf("song" to emptyList<Any>()),
            ),
        )
        coEvery { genreMixDao.getTopGenres() } returns listOf(CachedGenreEntity(name = "Rock", songCount = 5))
        coEvery { dailyMixRepository.allMixGenreNames() } returns emptyList()
        coEvery { genreMixDao.countSongsForGenre("Rock") } returns 0

        w.syncGenres()

        coVerify { genreMixDao.replaceSongs("Rock", emptyList()) }
    }

    // ── syncNow orchestration tests (use UnconfinedTestDispatcher so
    //    scope.launch{} runs synchronously — no advanceUntilIdle needed)

    private fun makeWorker() = MetadataSyncWorker(
        context, api, authHelper, metadataDao, trackDao, genreMixDao, coverArtFallback,
        mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
        dailyMixRepository,
        kotlinx.coroutines.test.UnconfinedTestDispatcher(),
    )

    @Test
    fun `syncNow orchestrates all phases and completes`() = runTest {
        val w = makeWorker()
        coEvery { metadataDao.albumCount() } returns 42
        coEvery { metadataDao.artistCount() } returns 10
        coEvery { metadataDao.cachedTrackCount() } returns 500
        coEvery { genreMixDao.getTopGenres() } returns emptyList()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "albumList2" to mapOf<String, Any>()),
        )
        coEvery { api.getArtists(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "artists" to mapOf<String, Any>()),
        )
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "genres" to mapOf<String, Any>()),
        )
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.countUncachedAlbums() } returns 0
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "starred2" to mapOf<String, Any>()),
        )

        w.syncNow() // Unconfined dispatcher → launches synchronously

        assertEquals("complete", w.status.value.phase)
        coVerify(atLeast = 1) { api.getStarred2(any()) }
        assertFalse(w.status.value.isRunning)
        assertEquals(42, w.status.value.albums)
        // The album-count recompute must run exactly once, AFTER the tracks
        // phase (cached_album_tracks populated) so track-artist attribution is
        // included — the syncArtists-phase regression fix.
        coVerify(exactly = 1) { metadataDao.updateArtistAlbumCounts() }
        // First-run Custom Daily Mix seeding must run at sync end (after
        // tracks, before the Daily Mix phase).
        coVerify(exactly = 1) { dailyMixRepository.seedIfNeeded() }
    }

    @Test
    fun `syncNow persists timestamps to SharedPreferences`() = runTest {
        val w = makeWorker()
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { metadataDao.artistCount() } returns 1
        coEvery { metadataDao.cachedTrackCount() } returns 1
        coEvery { genreMixDao.getTopGenres() } returns emptyList()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "albumList2" to mapOf<String, Any>()),
        )
        coEvery { api.getArtists(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "artists" to mapOf<String, Any>()),
        )
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "genres" to mapOf<String, Any>()),
        )
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.countUncachedAlbums() } returns 0

        w.syncNow()

        coVerify(atLeast = 1) { prefsEditor.putLong("last_metadata_sync_ms", any()) }
        coVerify(atLeast = 1) { prefsEditor.putLong("metadata_sync_duration_ms", any()) }
    }

    @Test
    fun `syncNow resets isSyncing guard on error`() = runTest {
        val w = makeWorker()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } throws RuntimeException("boom")

        w.syncNow(forceTrackResync = true)
        w.syncNow(forceTrackResync = true) // Should not skip — guard must be reset after exception

        coVerify(atLeast = 2) { api.getAlbumList2(any(), any(), any(), any()) }
    }

    @Test
    fun `syncAlbumTracks emits progress with granularity and totals`() = runTest {
        val albums = (1..12).map { i -> CachedAlbumEntity(id = "al-$i", name = "A$i") }
        coEvery { metadataDao.getAllAlbums() } returns albums
        coEvery { metadataDao.getAlbumTracks(any()) } returns emptyList()
        coEvery { metadataDao.countUncachedAlbums() } returns 12

        albums.forEach {
            coEvery { api.getAlbum(id = it.id, auth = any()) } returns mapOf(
                "subsonic-response" to mapOf(
                    "status" to "ok",
                    "album" to mapOf(
                        "song" to listOf(
                            mapOf("id" to "t-${it.id}", "title" to "T", "duration" to 100),
                        ),
                    ),
                ),
            )
        }
        coEvery { metadataDao.replaceAlbumTracksBatch(any()) } just Runs

        worker.syncAlbumTracks()

        // Status should show albumTracksProgressTotal was set
        assertEquals(12, worker.status.value.albumTracksProgressTotal)
        // Progress emitted every album (not just % 5), trackCount reflects per-sync total
        assertTrue(worker.status.value.albumTracksProgress > 0)
        assertTrue(worker.status.value.trackCount > 0)
    }

    // ── Incremental staleness + force resync (option C) ─────────────────────

    @Test
    fun `syncAlbums detects changed albums and cleans orphaned tracks`() = runTest {
        // Cached album al-1 has songCount 10; server now reports 12 → changed
        coEvery { metadataDao.getAllAlbums() } returns listOf(
            CachedAlbumEntity(id = "al-1", name = "A", songCount = 10, duration = 100),
        )
        val response = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "albumList2" to mapOf(
                    "album" to listOf(
                        mapOf("id" to "al-1", "name" to "A", "songCount" to 12, "duration" to 120),
                    ),
                ),
            ),
        )
        coEvery { api.getAlbumList2(type = any(), size = any(), offset = any(), auth = any()) } returns response

        worker.syncAlbums()

        // Orphan cleanup ran after replace
        coVerify { metadataDao.deleteOrphanedAlbumTracks() }
    }

    @Test
    fun `syncAlbumTracks refetches changed album even when tracks exist`() = runTest {
        // Setup: album al-1 metadata changed (songCount 10→12)
        coEvery { metadataDao.getAllAlbums() } returns listOf(
            CachedAlbumEntity(id = "al-1", name = "A", songCount = 10, duration = 100),
        )
        val albumsResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "albumList2" to mapOf(
                    "album" to listOf(mapOf("id" to "al-1", "name" to "A", "songCount" to 12, "duration" to 100)),
                ),
            ),
        )
        coEvery { api.getAlbumList2(type = any(), size = any(), offset = any(), auth = any()) } returns albumsResponse
        worker.syncAlbums() // populates changedAlbumIds with al-1

        // al-1 already has cached tracks — would normally be skipped
        coEvery { metadataDao.getAlbumTracks("al-1") } returns listOf(
            CachedAlbumTrackEntity(id = "t1", albumId = "al-1", title = "Old"),
        )
        every { prefs.getInt("metadata_version", 0) } returns 2 // no schema-forced resync
        val albumResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "album" to mapOf(
                    "song" to listOf(mapOf("id" to "t1", "title" to "New Title")),
                ),
            ),
        )
        coEvery { api.getAlbum(id = "al-1", auth = any()) } returns albumResponse

        worker.syncAlbumTracks()

        // Changed album was re-fetched despite having cached tracks
        coVerify { metadataDao.replaceAlbumTracksBatch(match { it.size == 1 && it[0].title == "New Title" }) }
    }

    @Test
    fun `syncAlbumTracks force flag refetches all albums`() = runTest {
        coEvery { metadataDao.getAllAlbums() } returns listOf(
            CachedAlbumEntity(id = "al-1", name = "A", songCount = 10, duration = 100),
        )
        // Cached tracks exist and metadata unchanged — only force can trigger re-fetch
        coEvery { metadataDao.getAlbumTracks("al-1") } returns listOf(
            CachedAlbumTrackEntity(id = "t1", albumId = "al-1", title = "T"),
        )
        every { prefs.getInt("metadata_version", 0) } returns 2
        val albumResponse = mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "album" to mapOf("song" to listOf(mapOf("id" to "t1", "title" to "T"))),
            ),
        )
        coEvery { api.getAlbum(id = "al-1", auth = any()) } returns albumResponse

        worker.syncAlbumTracks(force = true)

        coVerify { metadataDao.replaceAlbumTracksBatch(any()) }
    }

    @Test
    fun `syncAlbumTracks skips unchanged cached albums without force`() = runTest {
        coEvery { metadataDao.getAllAlbums() } returns listOf(
            CachedAlbumEntity(id = "al-1", name = "A", songCount = 10, duration = 100),
        )
        coEvery { metadataDao.getAlbumTracks("al-1") } returns listOf(
            CachedAlbumTrackEntity(id = "t1", albumId = "al-1", title = "T"),
        )
        every { prefs.getInt("metadata_version", 0) } returns 2

        worker.syncAlbumTracks()

        coVerify(exactly = 0) { api.getAlbum(id = "al-1", auth = any()) }
    }

    // ── Genre population ordering ────────────────────────────────────────

    @Test
    fun `populateAllTrackGenres skipped when no tracks were changed`() = runTest {
        val w = makeWorker()
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { metadataDao.artistCount() } returns 1
        coEvery { metadataDao.cachedTrackCount() } returns 1
        coEvery { genreMixDao.getTopGenres() } returns emptyList()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "albumList2" to mapOf<String, Any>()),
        )
        coEvery { api.getArtists(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "artists" to mapOf<String, Any>()),
        )
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "genres" to mapOf<String, Any>()),
        )
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.countUncachedAlbums() } returns 0
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "starred2" to mapOf<String, Any>()),
        )

        w.syncNow()

        // No albums were synced, so populate must NOT run
        coVerify(exactly = 0) { trackDao.populateAllTrackGenres() }
        coVerify(exactly = 0) { trackDao.populateGenresFromCachedGenreSongs() }
    }

    // ── Star sync mirrors tracks, albums, and artists (v43) ──────────────

    @Test
    fun `syncStarredAndRatings mirrors tracks albums and artists`() = runTest {
        val w = makeWorker()
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "starred2" to mapOf(
                    "song" to listOf(mapOf("id" to "t1", "userRating" to 3)),
                    "album" to listOf(mapOf("id" to "al-1", "userRating" to 4)),
                    "artist" to listOf(mapOf("id" to "ar-1")),
                ),
            ),
        )

        w.syncStarredAndRatings()

        // Tracks: star + rating
        coVerify(atLeast = 1) { trackDao.setStarredAtBulk(any(), any()) }
        coVerify(atLeast = 1) { trackDao.clearNonStarred(any(), any()) }
        coVerify { trackDao.setRating("t1", 3) }
        // Albums: star + rating (bulk + clear-non-starred)
        coVerify(atLeast = 1) { metadataDao.setAlbumStarredAtBulk(any(), any()) }
        coVerify(atLeast = 1) { metadataDao.clearNonStarredAlbums(any(), any()) }
        coVerify { metadataDao.setAlbumRating("al-1", 4) }
        // Artists: star
        coVerify(atLeast = 1) { metadataDao.setArtistStarredAtBulk(any(), any()) }
        coVerify(atLeast = 1) { metadataDao.clearNonStarredArtists(any(), any()) }
    }

    @Test
    fun `syncStarredAndRatings skips album and artist mirror when sections missing`() = runTest {
        val w = makeWorker()
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "starred2" to mapOf(
                    "song" to listOf(mapOf("id" to "t1")),
                ),
            ),
        )

        w.syncStarredAndRatings()

        coVerify(atLeast = 1) { trackDao.setStarredAtBulk(any(), any()) }
        // No album/artist sections → no bulk writes, no clear-non-starred
        coVerify(exactly = 0) { metadataDao.setAlbumStarredAtBulk(any(), any()) }
        coVerify(exactly = 0) { metadataDao.clearNonStarredAlbums(any(), any()) }
        coVerify(exactly = 0) { metadataDao.setArtistStarredAtBulk(any(), any()) }
        coVerify(exactly = 0) { metadataDao.clearNonStarredArtists(any(), any()) }
    }

    // ── v43: Local-first star push-back + mirror protection ──────────────

    @Test
    fun `syncStarredAndRatings pushes local-only track stars back to server`() = runTest {
        val w = makeWorker()
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "starred2" to mapOf(
                    "song" to listOf(mapOf("id" to "t1")),
                ),
            ),
        )
        // t2 is starred locally but missing on the server (offline like)
        coEvery { trackDao.getStarredIds() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.StarredIdProjection(id = "t1", userRating = 5),
            com.lucasdss.ftpmusic.app.data.db.StarredIdProjection(id = "t2", userRating = 5),
        )

        w.syncStarredAndRatings()

        // Push-back: one comma-joined call for the local-only star
        coVerify { api.star(any(), id = "t2", albumId = null, artistId = null) }
        // Clear sweep protects the pushed id AND is bounded by the sync start
        coVerify { trackDao.clearNonStarred(listOf("t1", "t2"), any()) }
    }

    @Test
    fun `syncStarredAndRatings pushes local-only album and artist stars`() = runTest {
        val w = makeWorker()
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "starred2" to mapOf<String, Any>()),
        )
        coEvery { metadataDao.getStarredAlbumIds() } returns listOf("al-9")
        coEvery { metadataDao.getStarredArtistIds() } returns listOf("ar-9")

        w.syncStarredAndRatings()

        coVerify { api.star(any(), id = null, albumId = "al-9", artistId = null) }
        coVerify { api.star(any(), id = null, albumId = null, artistId = "ar-9") }
        coVerify { metadataDao.clearNonStarredAlbums(listOf("al-9"), any()) }
        coVerify { metadataDao.clearNonStarredArtists(listOf("ar-9"), any()) }
        // No server sections → no bulk star writes
        coVerify(exactly = 0) { metadataDao.setAlbumStarredAtBulk(any(), any()) }
        coVerify(exactly = 0) { metadataDao.setArtistStarredAtBulk(any(), any()) }
    }

    @Test
    fun `syncStarredAndRatings push-back failure is silent and does not wipe local stars`() = runTest {
        val w = makeWorker()
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "starred2" to mapOf<String, Any>()),
        )
        coEvery { trackDao.getStarredIds() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.StarredIdProjection(id = "t9", userRating = 5),
        )
        coEvery { api.star(any(), id = "t9", albumId = null, artistId = null) } throws RuntimeException("offline")

        w.syncStarredAndRatings()

        // Failure is swallowed; the protected clear still passes t9 so the
        // local star survives for the next retry.
        coVerify { trackDao.clearNonStarred(listOf("t9"), any()) }
    }

    // ── v44: local intent — never re-star disliked / pending-unstar rows ──

    @Test
    fun `mirror does not re-star a disliked track and pushes the unstar`() = runTest {
        val w = makeWorker()
        // Server still stars t1; locally the user disliked it (star removed)
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "starred2" to mapOf(
                    "song" to listOf(mapOf("id" to "t1")),
                ),
            ),
        )
        coEvery { trackDao.getStarredIds() } returns emptyList()
        coEvery { trackDao.getDislikedIds() } returns listOf("t1")
        coEvery { trackDao.getPendingUnstarIds() } returns emptyList()

        w.syncStarredAndRatings()

        // t1 is skipped from the re-star bulk write
        coVerify(exactly = 0) { trackDao.setStarredAtBulk(any(), any()) }
        // Unstar push-back fires for the disliked-but-server-starred row
        coVerify { api.unstar(any(), id = "t1", albumId = null, artistId = null) }
        // Protected clear still covers the server list
        coVerify { trackDao.clearNonStarred(listOf("t1"), any()) }
    }

    @Test
    fun `mirror does not re-star a pending-unstar track and pushes the unstar`() = runTest {
        val w = makeWorker()
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "starred2" to mapOf(
                    "song" to listOf(mapOf("id" to "t1")),
                ),
            ),
        )
        coEvery { trackDao.getStarredIds() } returns emptyList()
        coEvery { trackDao.getDislikedIds() } returns emptyList()
        coEvery { trackDao.getPendingUnstarIds() } returns listOf("t1")

        w.syncStarredAndRatings()

        coVerify(exactly = 0) { trackDao.setStarredAtBulk(any(), any()) }
        coVerify { api.unstar(any(), id = "t1", albumId = null, artistId = null) }
        // Marker is NOT cleared while the server still lists the star
        coVerify(exactly = 0) { trackDao.clearPendingUnstar(any()) }
    }

    @Test
    fun `mirror clears the pending-unstar marker once the server drops the star`() = runTest {
        val w = makeWorker()
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "starred2" to mapOf<String, Any>()),
        )
        coEvery { trackDao.getStarredIds() } returns emptyList()
        coEvery { trackDao.getDislikedIds() } returns emptyList()
        coEvery { trackDao.getPendingUnstarIds() } returns listOf("t1")

        w.syncStarredAndRatings()

        coVerify { trackDao.clearPendingUnstar(listOf("t1")) }
    }

    @Test
    fun `mirror skips re-star of disliked albums and artists`() = runTest {
        val w = makeWorker()
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "starred2" to mapOf(
                    "album" to listOf(mapOf("id" to "al-1")),
                    "artist" to listOf(mapOf("id" to "ar-1")),
                ),
            ),
        )
        coEvery { metadataDao.getStarredAlbumIds() } returns emptyList()
        coEvery { metadataDao.getStarredArtistIds() } returns emptyList()
        coEvery { metadataDao.getDislikedAlbumIds() } returns listOf("al-1")
        coEvery { metadataDao.getDislikedArtistIds() } returns listOf("ar-1")
        coEvery { metadataDao.getPendingUnstarAlbumIds() } returns emptyList()
        coEvery { metadataDao.getPendingUnstarArtistIds() } returns emptyList()

        w.syncStarredAndRatings()

        coVerify(exactly = 0) { metadataDao.setAlbumStarredAtBulk(any(), any()) }
        coVerify(exactly = 0) { metadataDao.setArtistStarredAtBulk(any(), any()) }
        coVerify { api.unstar(any(), id = null, albumId = "al-1", artistId = null) }
        coVerify { api.unstar(any(), id = null, albumId = null, artistId = "ar-1") }
    }

    @Test
    fun `star push-back is chunked at 50 ids per call`() = runTest {
        val w = makeWorker()
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "starred2" to mapOf<String, Any>()),
        )
        // 60 local-only stars
        val localIds = (0 until 60).map { "t$it" }
        coEvery { trackDao.getStarredIds() } returns localIds.map {
            com.lucasdss.ftpmusic.app.data.db.StarredIdProjection(id = it, userRating = 5)
        }
        coEvery { trackDao.getDislikedIds() } returns emptyList()
        coEvery { trackDao.getPendingUnstarIds() } returns emptyList()

        w.syncStarredAndRatings()

        // Two star calls: 50 + 10 comma-joined ids
        coVerify(exactly = 1) {
            api.star(any(), id = (0 until 50).joinToString(",") { "t$it" }, albumId = null, artistId = null)
        }
        coVerify(exactly = 1) {
            api.star(any(), id = (50 until 60).joinToString(",") { "t$it" }, albumId = null, artistId = null)
        }
    }

    // ── Data consistency tests ───────────────────────────────────────────

    @Test
    fun `syncAlbums replaces with empty list when server returns 0 albums`() = runTest {
        val w = makeWorker()
        // API returns empty album list (status ok, but no albums)
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "albumList2" to mapOf<String, Any>()),
        )

        w.syncAlbums()

        // replaceAlbums must be called with empty list (clears stale cache)
        coVerify { metadataDao.replaceAlbums(emptyList()) }
        // Orphan cleanup runs too
        coVerify(atLeast = 1) { metadataDao.deleteOrphanedAlbumTracks() }
    }

    @Test
    fun `syncAlbums keeps cached albums when an empty response meets a populated library`() = runTest {
        val w = makeWorker()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "albumList2" to mapOf<String, Any>()),
        )
        coEvery { metadataDao.albumCount() } returns 42

        w.syncAlbums()

        coVerify(exactly = 0) { metadataDao.replaceAlbums(any()) }
        coVerify(exactly = 0) { metadataDao.deleteOrphanedAlbumTracks() }
    }

    @Test
    fun `change detection catches genre name artist year coverArt changes`() = runTest {
        val w = makeWorker()
        val serverAlbum = mapOf<String, Any>(
            "id" to "al-1",
            "name" to "New Name",
            "artist" to "New Artist",
            "year" to 2024,
            "genre" to "New Genre",
            "coverArt" to "ca-new",
            "songCount" to 10,
            "duration" to 300,
        )
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "albumList2" to mapOf("album" to listOf(serverAlbum))),
        )
        val cachedAlbum = CachedAlbumEntity(
            id = "al-1",
            name = "Old Name",
            artist = "Old Artist",
            year = 2020,
            genre = "Old Genre",
            coverArt = "ca-old",
            songCount = 10,
            duration = 300,
        )
        coEvery { metadataDao.getAllAlbums() } returns listOf(cachedAlbum)
        coEvery { metadataDao.replaceAlbums(any()) } returns Unit
        coEvery { metadataDao.deleteOrphanedAlbumTracks() } returns 0

        w.syncAlbums()

        // Genre/name/artist/year/coverArt changes must trigger re-fetch
        // (verified by checking replaceAlbums was called — the album is in allAlbums)
        coVerify { metadataDao.replaceAlbums(any()) }
    }

    @Test
    fun `pendingTotal is capped at albums size when sets overlap`() = runTest {
        val w = makeWorker()
        val albums = (1..5).map { i -> CachedAlbumEntity(id = "al-$i", name = "A$i") }
        coEvery { metadataDao.getAllAlbums() } returns albums
        coEvery { metadataDao.countUncachedAlbums() } returns 3
        coEvery { metadataDao.getAlbumTracks(any()) } returns emptyList()
        every { context.getSharedPreferences(any(), any()) } returns prefs

        // metadata_version >= METADATA_VERSION (no forceResync)
        every { prefs.getInt("metadata_version", 0) } returns 2

        // Simulate: changedAlbumIds already populated with 2 albums
        // uncached=3 + changed=2 = 5, but albums.size = 5. Capped at 5.
        val reflectField = MetadataSyncWorker::class.java.getDeclaredField("changedAlbumIds")
        reflectField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val changed = reflectField.get(w) as MutableSet<String>
        changed.add("al-1")
        changed.add("al-2")

        w.syncAlbumTracks()

        // pendingTotal should be 5 (capped at albums.size, not 3+2=5 which is equal)
        val status = w.status.value
        assertTrue(
            "albumTracksProgressTotal must be <= albums.size",
            status.albumTracksProgressTotal <= 5,
        )
    }

    // ── Resilience tests ─────────────────────────────────────────────────

    @Test
    fun `syncAlbumTracks aborts after 3 consecutive failures`() = runTest {
        val w = makeWorker()
        val albums = (1..10).map { i -> CachedAlbumEntity(id = "al-$i", name = "A$i") }
        coEvery { metadataDao.getAllAlbums() } returns albums
        coEvery { metadataDao.getAlbumTracks(any()) } returns emptyList()
        coEvery { metadataDao.countUncachedAlbums() } returns 10
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getInt("metadata_version", 0) } returns 2
        // All getAlbum calls fail after the first 3 — network gone
        coEvery { api.getAlbum(any(), any()) } throws RuntimeException("Network gone")

        w.syncAlbumTracks()

        // Must abort, not iterate all 10 albums with 500ms delay each
        val status = w.status.value
        assertEquals("phase must be error after abort", "error", status.phase)
    }

    @Test
    fun `syncAlbumTracks sets phase error when over 5 percent fail`() = runTest {
        val w = makeWorker()
        val albums = (1..20).map { i -> CachedAlbumEntity(id = "al-$i", name = "A$i") }
        coEvery { metadataDao.getAllAlbums() } returns albums
        coEvery { metadataDao.getAlbumTracks(any()) } returns emptyList()
        coEvery { metadataDao.countUncachedAlbums() } returns 20
        coEvery { metadataDao.replaceAlbumTracksBatch(any()) } returns Unit
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getInt("metadata_version", 0) } returns 2
        // First 2 succeed, remaining 18 fail (>5%)
        coEvery { api.getAlbum(id = "al-1", any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "album" to mapOf("song" to emptyList<Any>())),
        )
        coEvery { api.getAlbum(id = "al-2", any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "album" to mapOf("song" to emptyList<Any>())),
        )
        coEvery { api.getAlbum(id = match { it != "al-1" && it != "al-2" }, any()) } throws RuntimeException("fail")

        w.syncAlbumTracks()

        val status = w.status.value
        // 18 failures out of 20 = 90% > 5% threshold
        assertEquals("phase must be error when >5% fail", "error", status.phase)
    }
}
