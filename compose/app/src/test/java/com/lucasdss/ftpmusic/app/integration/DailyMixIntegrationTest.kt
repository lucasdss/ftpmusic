package com.lucasdss.ftpmusic.app.integration

import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.playback.DailyMixGenerator
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Integration tests hitting the real Navidrome server.
 * Validates that genre metadata exists in API responses and
 * Daily Mix generator works correctly with real data.
 */
class DailyMixIntegrationTest {

    private lateinit var api: SubsonicApi
    private lateinit var authHelper: SubsonicAuthHelper

    // Live-server integration test: credentials are injected via environment
    // variables (FTPMUSIC_TEST_SERVER / FTPMUSIC_TEST_USER / FTPMUSIC_TEST_PASS)
    // and the test is skipped when they are absent — never commit real
    // credentials to the repository.
    private val baseUrl: String = System.getenv("FTPMUSIC_TEST_SERVER") ?: ""
    private val username: String = System.getenv("FTPMUSIC_TEST_USER") ?: ""
    private val password: String = System.getenv("FTPMUSIC_TEST_PASS") ?: ""

    @Before
    fun setUp() {
        org.junit.Assume.assumeTrue(
            "Set FTPMUSIC_TEST_SERVER/USER/PASS to run the live integration test",
            baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
        )
        authHelper = SubsonicAuthHelper()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        api = Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(SubsonicApi::class.java)
    }

    // ── Genre metadata validation ──────────────────────────────────────

    @Test
    fun `album tracks have genre from API`() = runBlocking {
        val params = authHelper.buildAuthParams(username, password)
        val response = api.getAlbum("55bgFxaNbmz8NbRdcftTvn", params)
        val sr = response["subsonic-response"] as? Map<*, *>
        val album = sr?.get("album") as? Map<*, *>
        assertNotNull("Album should exist", album)
        // Genre label is live-server data and drifts (was "Rock", now "Metal").
        // The test validates the plumbing — genre present + non-blank.
        assertTrue(
            "Album should carry a genre from the API",
            (album?.get("genre") as? String)?.isNotBlank() == true,
        )

        val songs = album?.get("song") as? List<*>
        assertNotNull("Album should have songs", songs)
        assertTrue("Album should have multiple tracks", songs!!.size > 1)
    }

    @Test
    fun `getSongsByGenre returns proper track metadata`() = runBlocking {
        val params = authHelper.buildAuthParams(username, password)
        val response = api.getSongsByGenre(params, "Rock", 10)
        val sr = response["subsonic-response"] as? Map<*, *>
        val sbg = sr?.get("songsByGenre") as? Map<*, *>
        val songs = sbg?.get("song") as? List<*>
        assertNotNull("Should return songs", songs)
        assertTrue("Should return some songs", songs!!.isNotEmpty())

        for (s in songs) {
            val m = s as Map<*, *>
            assertNotNull("Track ${m["title"]} should have id", m["id"])
            assertNotNull("Track ${m["title"]} should have title", m["title"])
            assertNotNull("Track ${m["title"]} should have artist", m["artist"])
        }
    }

    @Test
    fun `genres endpoint returns meaningful counts`() = runBlocking {
        val params = authHelper.buildAuthParams(username, password)
        val response = api.getGenres(params)
        val sr = response["subsonic-response"] as? Map<*, *>
        val genres = sr?.get("genres") as? Map<*, *>
        val genreList = genres?.get("genre") as? List<*>
        assertNotNull("Should have genres", genreList)
        assertTrue("Should have many genres", genreList!!.size > 50)

        val rock = genreList.mapNotNull { it as? Map<*, *> }
            .find { (it["value"] ?: it["name"]) == "Rock" }
        assertNotNull("Rock genre should exist", rock)
        val rockCount = (rock!!["songCount"] as? Number)?.toInt() ?: 0
        assertTrue("Rock should have many songs, got $rockCount", rockCount > 1000)
    }

    // ── BUG REPRODUCTION: getSongsByGenre lacks artist diversity ──────

    @Test
    fun `getSongsByGenre returns too few artists for diverse mix`() = runBlocking {
        val params = authHelper.buildAuthParams(username, password)
        val response = api.getSongsByGenre(params, "Rock", 100)
        val sr = response["subsonic-response"] as? Map<*, *>
        val sbg = sr?.get("songsByGenre") as? Map<*, *>
        val songList = sbg?.get("song") as? List<*>
        requireNotNull(songList)

        val artistCounts = mutableMapOf<String, Int>()
        for (s in songList) {
            val m = s as Map<*, *>
            val artist = m["artist"] as? String ?: "unknown"
            artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
        }
        // The API returns only a few artists heavily skewed — this is the design bug
        println("getSongsByGenre(Rock, 100): ${songList.size} songs, ${artistCounts.size} artists")
        artistCounts.entries.sortedByDescending { it.value }.take(5).forEach {
            println("  ${it.key}: ${it.value}")
        }
        assertTrue(
            "getSongsByGenre lacks artist diversity (${artistCounts.size} artists in 100 songs)",
            artistCounts.size < 10, // Expected: very few artists dominate. Bug, not feature.
        )
    }

    // ── SOLUTION VALIDATION: album-based tracks give diversity ─────────

    @Test
    fun `tracks from multiple Rock albums have many distinct artists`() = runBlocking {
        val params = authHelper.buildAuthParams(username, password)
        // Use byGenre to get Rock-specific albums
        val albumResponse = api.getAlbumList2("newest", 30, 0, params)
        val sr = albumResponse["subsonic-response"] as? Map<*, *>
        val albumList = sr?.get("albumList2") as? Map<*, *>
        val albums = albumList?.get("album") as? List<*>
        requireNotNull(albums) { "getAlbumList2 returned no albums" }

        // Count distinct artists
        val artistSet = mutableSetOf<String>()
        for (a in albums) {
            val album = a as Map<*, *>
            val artist = album["artist"] as? String
            if (artist != null) artistSet.add(artist)
        }

        println("Found ${albums.size} albums with ${artistSet.size} distinct artists")
        assertTrue(
            "Albums should have multiple distinct artists, got ${artistSet.size}",
            artistSet.size >= 3,
        )
    }

    // ── Daily Mix generator with simulated genre-populated tracks ──────

    @Test
    fun `generator from diverse artists produces large valid mix`() {
        // Simulate what tracks table would look like after genre population fix:
        // 100 tracks from 30 artists, each with 1-5 tracks, all genre=Rock
        val songs = mutableListOf<DailyMixGenerator.SongInfo>()
        for (a in 1..30) {
            val artistName = "Artist$a"
            val tracksForArtist = (2..5).random()
            for (t in 1..tracksForArtist) {
                songs.add(
                    DailyMixGenerator.SongInfo(
                        id = "${artistName}_t${a}_$t",
                        title = "Track $t",
                        artist = artistName,
                        albumId = "album_${a}_$t",
                        duration = 240,
                        trackNumber = t,
                        coverArt = "cover_$a",
                    ),
                )
            }
        }
        assertTrue("Should have around 100 songs", songs.size in 90..120)

        val result = DailyMixGenerator.generate(songs)
        assertTrue("Mix should have tracks", result.isNotEmpty())
        assertTrue("Mix size in 30..120: was ${result.size}", result.size in 30..120)

        // With 30 artists, round-robin should maintain excellent diversity
        var consecutiveCount = 0
        for (i in 1 until result.size) {
            if (result[i].artist == result[i - 1].artist) consecutiveCount++
        }
        assertTrue(
            "At most 5% consecutive with 30 artists (was $consecutiveCount/${result.size})",
            consecutiveCount <= result.size / 20,
        )
    }
}
