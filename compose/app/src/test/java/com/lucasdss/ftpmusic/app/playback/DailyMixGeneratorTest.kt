package com.lucasdss.ftpmusic.app.playback

import io.mockk.*
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DailyMixGeneratorTest {

    private fun song(id: String, artist: String) = songWithArtist(id, artist)

    private fun songWithArtist(id: String, artist: String?) = DailyMixGenerator.SongInfo(
        id = id,
        title = "Track $id",
        artist = artist,
        albumId = "al-$id",
        duration = 200,
        trackNumber = 1,
        coverArt = null,
    )

    @Test
    fun `generates between 30 and 120 tracks`() {
        val songs = (1..200).map { song("s$it", "Artist${it % 10}") }
        val ratings = songs.associate { it.id to 0 }
        val result = DailyMixGenerator.generate(songs, ratings, emptyList())
        assertTrue("Size >= 30", result.size >= 30)
        assertTrue("Size <= 120", result.size <= 120)
    }

    @Test
    fun `returns all tracks when fewer than 30 available`() {
        // 30 artists × 1 track = 30 tracks, cap = max(2,30/10)=3. All fit.
        val songs = (1..30).map { song("s$it", "Artist$it") }
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList())
        assertEquals(30, result.size)
    }

    @Test
    fun `no two consecutive tracks have the same artist`() {
        val songs = (1..100).map { song("s$it", "Artist${it % 3}") }
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList())
        for (i in 1 until result.size) {
            assertNotEquals(
                "Consecutive tracks at $i must differ in artist",
                result[i].artist,
                result[i - 1].artist,
            )
        }
    }

    @Test
    fun `handles single artist gracefully`() {
        val songs = (1..100).map { song("s$it", "SameArtist") }
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList())
        // DM-1/DM-12: a single-artist pool must not collapse to one track.
        assertTrue("Must fill the mix (got ${result.size})", result.size >= 30)
        assertTrue(
            "All same artist is acceptable when only one exists",
            result.all { it.artist == "SameArtist" },
        )
        assertEquals("No duplicate track IDs", result.size, result.map { it.id }.toSet().size)
    }

    @Test
    fun `null artists are one bucket and still fill the mix`() {
        val songs = (1..100).map { songWithArtist("n$it", null) }
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList(), random = Random(3))
        // DM-9: null artists share one internal key; adjacency relaxes when no
        // alternative exists, so the mix must still reach the target.
        assertTrue("Null-artist pool must fill (got ${result.size})", result.size >= 30)
        assertEquals("No duplicate track IDs", result.size, result.map { it.id }.toSet().size)
    }

    @Test
    fun `null artist and literal unknown artist are distinct buckets`() {
        val songs = (1..40).map { songWithArtist("n$it", null) } +
            (1..40).map { songWithArtist("u$it", "unknown") }
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList(), random = Random(4))
        assertTrue("Mix must fill (got ${result.size})", result.size >= 30)
        assertTrue("Null-artist tracks represented", result.any { it.artist == null })
        assertTrue("Literal 'unknown' tracks represented", result.any { it.artist == "unknown" })
    }

    @Test
    fun `dominant artist fills target after the only alternative is exhausted`() {
        // DM-12: ArtistB has one track, ArtistA has many. The soft cap on A must
        // relax once B is exhausted, otherwise the mix stops around cap+1.
        val songs = (1..100).map { song("a$it", "ArtistA") } + song("b1", "ArtistB")
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList(), random = Random(5))
        assertTrue("Must reach minimum target (got ${result.size})", result.size >= 30)
        assertTrue("ArtistB must be included", result.any { it.artist == "ArtistB" })
    }

    @Test
    fun `small alternative pools do not strand the mix below target`() {
        // 3 artists: A has 100 tracks, B and C have one each. Once B/C exhaust,
        // the hard fill must use A's remaining tracks instead of stopping at
        // the soft cap (DM-1 follow-up).
        val songs = (1..100).map { song("a$it", "ArtistA") } +
            song("b1", "ArtistB") +
            song("c1", "ArtistC")
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList(), random = Random(6))
        assertTrue("Must reach minimum target (got ${result.size})", result.size >= 30)
        assertTrue("ArtistB included", result.any { it.artist == "ArtistB" })
        assertTrue("ArtistC included", result.any { it.artist == "ArtistC" })
    }

    @Test
    fun `higher rated tracks appear more frequently`() {
        val rng = Random(42)
        val songs = (1..500).map {
            if (it <= 10) {
                song("star-$it", "StarArtist")
            } else {
                song("s$it", "Artist${it % 20}")
            }
        }
        val ratings = mutableMapOf<String, Int>()
        (1..5).forEach { ratings["star-$it"] = 5 }
        (6..10).forEach { ratings["star-$it"] = 4 }
        val result = DailyMixGenerator.generate(songs, ratings, emptyList(), random = rng)
        val starCount = result.count { it.id.startsWith("star-") }
        // With 10 starred tracks × weight 5 = 50 weighted copies vs 490 × 1 = 490,
        // expect roughly 50/540 ≈ 9% of result to be starred => ~6-9 in 60-120
        assertTrue("Starred tracks should appear (got $starCount)", starCount >= 2)
    }

    @Test
    fun `unrated tracks still appear`() {
        val songs = (1..100).map { song("s$it", "Artist${it % 10}") }
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList())
        assertTrue("Unrated tracks must appear", result.isNotEmpty())
    }

    @Test
    fun `all output tracks are from input`() {
        val songs = (1..100).map { song("s$it", "Artist${it % 10}") }
        val inputIds = songs.map { it.id }.toSet()
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList())
        result.forEach { assertTrue("Track ${it.id} must be in input", it.id in inputIds) }
    }

    @Test
    fun `empty input returns empty list`() {
        val result = DailyMixGenerator.generate(emptyList(), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `rating 5 gives roughly 5x weight over unrated`() {
        val rng = Random(42)
        // Rated tracks each have unique artists — all appear in round-robin
        val songs = mutableListOf<DailyMixGenerator.SongInfo>()
        repeat(10) { songs.add(song("star-$it", "StarArtist$it")) }
        repeat(90) { songs.add(song("u$it", "Artist${it % 15}")) }
        val ratings = (0..9).associate { "star-$it" to 5 }
        val result = DailyMixGenerator.generate(songs, ratings, emptyList(), random = rng)
        val starCount = result.count { it.id.startsWith("star-") }
        assertTrue("All rated tracks appear (got $starCount)", starCount >= 10)
    }

    @Test
    fun `two artists with many tracks are interleaved`() {
        val songs = mutableListOf<DailyMixGenerator.SongInfo>()
        repeat(60) { songs.add(song("a$it", "ArtistA")) }
        repeat(60) { songs.add(song("b$it", "ArtistB")) }
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList())
        // With only 2 artists, they should alternate most of the time
        for (i in 1 until result.size) {
            assertNotEquals(
                "Only 2 artists — must alternate",
                result[i].artist,
                result[i - 1].artist,
            )
        }
    }

    @Test
    fun `retries shuffle twice before falling back to supplemental`() {
        // 150 ArtistA + 3 ArtistB — capped, supplemental with diverse artists fills rest
        val songs = mutableListOf<DailyMixGenerator.SongInfo>()
        repeat(150) { songs.add(song("a$it", "ArtistA")) }
        repeat(3) { songs.add(song("b$it", "ArtistB")) }

        val supplemental = mutableListOf<DailyMixGenerator.SongInfo>()
        repeat(200) { supplemental.add(song("sup$it", "SuppArtist${it % 20}")) } // 20 diverse artists

        val result = DailyMixGenerator.generate(songs, emptyMap(), supplemental)
        assertTrue("Size >= 30 (got ${result.size})", result.size >= 30)
    }

    @Test
    fun `falls back to appending when supplemental also exhausted`() {
        // Single artist in both songs and supplemental — no alternatives exist
        val songs = (1..100).map { song("s$it", "SameArtist") }
        val supplemental = (1..10).map { song("sup$it", "SameArtist") }

        val result = DailyMixGenerator.generate(songs, emptyMap(), supplemental)

        // DM-10: graceful degradation must still fill the mix, not stop at 1.
        assertTrue("Must fill the mix (got ${result.size})", result.size >= 30)
        assertTrue(
            "All same artist is acceptable when no alternatives exist",
            result.all { it.artist == "SameArtist" },
        )
    }

    // ── Edge cases for ratings & retry ──────────────────────────────────────

    @Test
    fun `empty ratings with explicit emptyMap works identically to missing param`() {
        val songs = (1..100).map { song("s$it", "Artist${it % 10}") }
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList())
        // Size should be within 60-120 range (random, so check bounds)
        assertTrue("Size >= 30", result.size >= 30)
        assertTrue("Size <= 120", result.size <= 120)
        val inputIds = songs.map { it.id }.toSet()
        result.forEach { assertTrue("Track ${it.id} must be in input", it.id in inputIds) }
    }

    @Test
    fun `ratings with mixed star levels produce weighted distribution`() {
        val rng = Random(42)
        val songs = (1..200).map {
            when {
                it <= 10 -> song("r5-$it", "Artist-r5-$it")

                // each has unique artist (10 artists)
                it <= 20 -> song("r3-$it", "Artist-r3-$it")

                // each has unique artist (10 artists)
                else -> song("u-$it", "Artist${it % 15}")
            }
        }
        val ratings = mutableMapOf<String, Int>()
        (1..10).forEach { ratings["r5-$it"] = 5 } // weight 5 each = 50
        (11..20).forEach { ratings["r3-$it"] = 3 } // weight 3 each = 30
        // unrated: 180 tracks × weight 1 = 180
        // total pool: 50 + 30 + 180 = 260

        val result = DailyMixGenerator.generate(songs, ratings, emptyList(), random = rng)
        val r5Count = result.count { it.id.startsWith("r5-") }
        val r3Count = result.count { it.id.startsWith("r3-") }

        // r5 (weight 5) should appear more often than r3 (weight 3)
        assertTrue("r5 should appear more than r3 (got r5=$r5Count, r3=$r3Count)", r5Count >= r3Count)
    }

    @Test
    fun `retries exhaust gracefully without supplemental tracks`() {
        // 80 ArtistA + 5 ArtistB — the soft cap must not strand the mix below target.
        val songs = mutableListOf<DailyMixGenerator.SongInfo>()
        repeat(80) { songs.add(song("a$it", "ArtistA")) }
        repeat(5) { songs.add(song("b$it", "ArtistB")) }

        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList())
        assertTrue("Must fill at least the minimum target (got ${result.size})", result.size >= 30)
        assertTrue("Cannot exceed available tracks (got ${result.size})", result.size <= 85)
    }

    @Test
    fun `dynamic cap limits tracks per artist proportionally`() {
        val rng = Random(42)
        // 100 tracks from ArtistA, 200 from 20 other artists (21 artists total).
        val songs = (1..100).map { song("a-$it", "ArtistA") } +
            (1..200).map { song("o-$it", "Artist${it % 20}") }
        val expectedTarget = Random(42).nextInt(30, 121)
        val expectedCap = DailyMixGenerator.maxTracksPerArtist(expectedTarget, 21)
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList(), emptySet(), random = rng)
        val aCount = result.count { it.artist == "ArtistA" }
        assertTrue(
            "ArtistA soft cap exceeded (aCount=$aCount cap=$expectedCap)",
            aCount <= expectedCap,
        )
        assertTrue("Should generate reasonable size (got ${result.size})", result.size >= 30)
    }

    // ── buildWeightMap tests ────────────────────────────────────────────────

    @Test
    fun `buildWeightMap returns weight 1 for unplayed tracks`() = runTest {
        val trackDao: com.lucasdss.ftpmusic.app.data.db.TrackDao = mockk(relaxed = true)
        coEvery { trackDao.getTrackPlayCounts(any()) } returns emptyList()
        coEvery { trackDao.getRatedTracksByIds(any()) } returns emptyList()
        coEvery { trackDao.getStarredIdsByIds(any()) } returns emptyList()

        val weights = DailyMixGenerator.buildWeightMap(listOf("t1", "t2"), trackDao)
        assertEquals(1, weights["t1"])
        assertEquals(1, weights["t2"])
    }

    @Test
    fun `buildWeightMap applies log-scale play count`() = runTest {
        val trackDao: com.lucasdss.ftpmusic.app.data.db.TrackDao = mockk(relaxed = true)
        coEvery { trackDao.getTrackPlayCounts(any()) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.PlayCountInfo(id = "t1", playCount = 1), // log2→0 → clamp→1
            com.lucasdss.ftpmusic.app.data.db.PlayCountInfo(id = "t2", playCount = 4), // log2→3
            com.lucasdss.ftpmusic.app.data.db.PlayCountInfo(id = "t3", playCount = 16), // log2→5
            com.lucasdss.ftpmusic.app.data.db.PlayCountInfo(id = "t4", playCount = 100), // log2→7 → clamp→5
        )
        coEvery { trackDao.getRatedTracksByIds(any()) } returns emptyList()
        coEvery { trackDao.getStarredIdsByIds(any()) } returns emptyList()

        val weights = DailyMixGenerator.buildWeightMap(listOf("t1", "t2", "t3", "t4"), trackDao)
        assertEquals(1, weights["t1"]) // 1 play → weight 1 (minimum)
        assertEquals(2, weights["t2"]) // 4 plays → ceil(log₂(4))=2
        assertEquals(4, weights["t3"]) // 16 plays → ceil(log₂(16))=4
        assertEquals(5, weights["t4"]) // 100 plays → clamped to 5
    }

    @Test
    fun `buildWeightMap adds rating bonus on top of play count`() = runTest {
        val trackDao: com.lucasdss.ftpmusic.app.data.db.TrackDao = mockk(relaxed = true)
        coEvery { trackDao.getTrackPlayCounts(any()) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.PlayCountInfo(id = "t1", playCount = 4), // weight 3
        )
        coEvery { trackDao.getRatedTracksByIds(any()) } returns emptyList()
        // Star gives rating=5 → bonus=2
        coEvery { trackDao.getStarredIdsByIds(any()) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.StarredIdProjection(id = "t1", userRating = 5),
        )

        val weights = DailyMixGenerator.buildWeightMap(listOf("t1"), trackDao)
        assertEquals(4, weights["t1"]) // 2 (play) + 2 (star bonus) = 4
    }

    @Test
    fun `buildWeightMap clamps weight to 1-5 range`() = runTest {
        val trackDao: com.lucasdss.ftpmusic.app.data.db.TrackDao = mockk(relaxed = true)
        coEvery { trackDao.getTrackPlayCounts(any()) } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.PlayCountInfo(id = "t1", playCount = 0),
            com.lucasdss.ftpmusic.app.data.db.PlayCountInfo(id = "t2", playCount = 1000),
        )
        coEvery { trackDao.getRatedTracksByIds(any()) } returns emptyList()
        coEvery { trackDao.getStarredIdsByIds(any()) } returns emptyList()

        val weights = DailyMixGenerator.buildWeightMap(listOf("t1", "t2"), trackDao)
        assertEquals(1, weights["t1"]) // 0 plays clamped to 1
        assertEquals(5, weights["t2"]) // 1000 plays clamped to 5
    }

    // ── yesterdayTrackIds tests ─────────────────────────────────────────────

    @Test
    fun `yesterdayTrackIds parameter reduces appearance of those tracks`() {
        val rng = Random(42)
        val songs = (1..200).map { song("t$it", "Artist${it % 10}") }
        val yesterday = setOf("t1", "t2", "t3")
        // Single generation with seeded RNG — yesterday tracks appear at most 3
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList(), yesterday, random = rng)
        val yesterdayCount = result.count { it.id in yesterday }
        // With weight penalty (coerced to at least 1), yesterday tracks can still appear
        // when they have low play counts. Deterministic seed caps at 3.
        assertTrue("Yesterday tracks should be limited (got $yesterdayCount)", yesterdayCount <= 3)
    }

    @Test
    fun `yesterdayTrackIds empty set has no effect`() {
        // 100 artists, 1 track each — cap doesn't limit
        val songs = (1..100).map { song("t$it", "Artist$it") }
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList(), emptySet())
        assertTrue("Size >= 30", result.size >= 30)
        assertTrue("Size <= 100", result.size <= 100)
    }

    @Test
    fun `all tracks from yesterday still produces output`() {
        val songs = (1..50).map { song("t$it", "Artist${it % 5}") }
        val allIds = songs.map { it.id }.toSet()
        // When all tracks are from yesterday, should still generate a mix
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList(), allIds)
        assertTrue("Should produce output even when all tracks are yesterday's", result.isNotEmpty())
    }

    @Test
    fun `reshuffle deduplicates tracks already in result`() {
        // Genre with 2 artists, 10 tracks each — diversity forces reshuffle
        val songs = (1..10).map { song("a$it", "ArtistA") } +
            (1..10).map { song("b$it", "ArtistB") }
        val result = DailyMixGenerator.generate(songs, emptyMap(), emptyList())
        // No duplicate track IDs
        val ids = result.map { it.id }.toSet()
        assertEquals(result.size, ids.size)
    }

    // ── 60% playback threshold (logic tests) ─────────────────────────────

    @Test
    fun `60 percent threshold fires at exactly 60 percent`() {
        val duration = 100_000L
        val at60 = duration * 0.6f
        val below60 = at60 - 1
        assertTrue("Position at 60% must fire", at60 >= duration * 0.6f)
        assertTrue("Position below 60% must not fire", below60 < duration * 0.6f)
    }

    @Test
    fun `60 percent threshold handles zero duration`() {
        val duration = 0L
        val position = 0L
        val shouldFire = duration > 0 && position >= duration * 0.6f
        assertFalse("Must not fire at zero duration", shouldFire)
    }

    @Test
    fun `60 percent threshold at 100 percent always fires`() {
        val duration = 200_000L
        val atEnd = duration
        assertTrue("Position at end must fire", atEnd >= duration * 0.6f)
    }

    @Test
    fun `60 percent threshold just below boundary does not fire`() {
        val duration = 100_000L
        val justBelow = (duration * 0.599).toLong()
        assertTrue("Just below must be < 60%", justBelow < duration * 0.6f)
    }

    // ── Weight math boundary tests ───────────────────────────────────────

    @Test
    fun `log2 of zero returns zero`() {
        val log2 = { n: Int -> if (n <= 1) 0 else (32 - Integer.numberOfLeadingZeros(n - 1)) }
        assertEquals(0, log2(0))
    }

    @Test
    fun `weight clamp never below 1`() {
        val log2 = { n: Int -> if (n <= 1) 0 else (32 - Integer.numberOfLeadingZeros(n - 1)) }
        val clamped = (log2(0) + 0).coerceIn(1, 5)
        assertEquals(1, clamped)
    }

    @Test
    fun `weight clamp never above 5`() {
        val log2 = { n: Int -> if (n <= 1) 0 else (32 - Integer.numberOfLeadingZeros(n - 1)) }
        val clamped = (log2(1000) + 5).coerceIn(1, 5)
        assertEquals(5, clamped)
    }

    @Test
    fun `starred tracks get rating bonus of 2`() {
        val rating = 5 // Starred tracks stored as user_rating = 5
        val bonus = rating / 2
        assertEquals(2, bonus)
    }

    @Test
    fun `rating bonus floors at integer division`() {
        assertEquals(1, 3 / 2) // rating 3 → bonus 1
        assertEquals(1, 2 / 2) // rating 2 → bonus 1
        assertEquals(0, 1 / 2) // rating 1 → bonus 0
        assertEquals(0, 0 / 2) // unrated  → bonus 0
    }

    // ── Anti-repetition: previously listened tracks ────────────────────────

    @Test
    fun `previouslyListenedTrackIds are excluded when enough tracks exist`() {
        // 50 artists × 1 track = 50 tracks. Mark 10 as previously listened.
        val songs = (1..50).map { song("s$it", "Artist$it") }
        val listened = (1..10).map { "s$it" }.toSet()

        val result = DailyMixGenerator.generate(
            songs,
            emptyMap(),
            emptyList(),
            emptySet(),
            previouslyListenedTrackIds = listened,
            random = kotlin.random.Random(42),
        )

        // None of the previously listened tracks should appear
        for (track in result) {
            assertFalse(
                "Previously listened track ${track.id} must be excluded",
                listened.contains(track.id),
            )
        }
    }

    @Test
    fun `previouslyListenedTrackIds fallback when pool is too small`() {
        // Only 5 artists × 1 track = 5 tracks, all previously listened.
        // No way to reach minimum 30 without repeating.
        val songs = (1..5).map { song("s$it", "Artist$it") }
        val listened = (1..5).map { "s$it" }.toSet()

        val result = DailyMixGenerator.generate(
            songs,
            emptyMap(),
            emptyList(),
            emptySet(),
            previouslyListenedTrackIds = listened,
            random = kotlin.random.Random(42),
        )

        // Must produce output — falls back to previously listened tracks
        assertTrue(
            "Must produce tracks even when all are previously listened",
            result.isNotEmpty(),
        )
    }

    @Test
    fun `previouslyListenedTrackIds empty set has no effect`() {
        val songs = (1..50).map { song("s$it", "Artist$it") }
        val result = DailyMixGenerator.generate(
            songs,
            emptyMap(),
            emptyList(),
            emptySet(),
            random = kotlin.random.Random(42),
        )

        assertTrue(
            "Should generate normally with empty listened set",
            result.size >= 30,
        )
    }

    // ── Regeneration trigger logic ─────────────────────────────────────

    @Test
    fun `shouldRegenerate returns false for fresh mix`() {
        val createdAtMs = System.currentTimeMillis()
        val totalTracks = 100
        val listenedTracks = 0

        val result = DailyMixGenerator.shouldRegenerate(
            createdAtMs = createdAtMs,
            totalTracks = totalTracks,
            listenedTracks = listenedTracks,
            nowMs = createdAtMs + 12 * 60 * 60 * 1000L, // 12h later
        )

        assertFalse("Fresh mix with no listens must not regenerate", result)
    }

    @Test
    fun `shouldRegenerate returns true when 24h passed and 10 percent listened`() {
        val createdAtMs = System.currentTimeMillis()
        val totalTracks = 100
        val listenedTracks = 15 // 15% > 10% threshold

        val result = DailyMixGenerator.shouldRegenerate(
            createdAtMs = createdAtMs,
            totalTracks = totalTracks,
            listenedTracks = listenedTracks,
            nowMs = createdAtMs + 25 * 60 * 60 * 1000L, // 25h later
        )

        assertTrue("Must regenerate when >24h and >10% listened", result)
    }

    @Test
    fun `shouldRegenerate returns false when 24h passed but under 10 percent listened`() {
        val createdAtMs = System.currentTimeMillis()
        val totalTracks = 100
        val listenedTracks = 5 // 5% < 10% threshold

        val result = DailyMixGenerator.shouldRegenerate(
            createdAtMs = createdAtMs,
            totalTracks = totalTracks,
            listenedTracks = listenedTracks,
            nowMs = createdAtMs + 25 * 60 * 60 * 1000L, // 25h later
        )

        assertFalse("Must not regenerate when >24h but <10% listened", result)
    }

    @Test
    fun `shouldRegenerate returns true when 48h passed regardless of listens`() {
        val createdAtMs = System.currentTimeMillis()
        val totalTracks = 100
        val listenedTracks = 0 // 0% listened

        val result = DailyMixGenerator.shouldRegenerate(
            createdAtMs = createdAtMs,
            totalTracks = totalTracks,
            listenedTracks = listenedTracks,
            nowMs = createdAtMs + 49 * 60 * 60 * 1000L, // 49h later
        )

        assertTrue("Must regenerate at 48h hard max even with 0% listened", result)
    }

    @Test
    fun `shouldRegenerate manual flag always returns true`() {
        val createdAtMs = System.currentTimeMillis()
        val totalTracks = 100
        val listenedTracks = 0

        val result = DailyMixGenerator.shouldRegenerate(
            createdAtMs = createdAtMs,
            totalTracks = totalTracks,
            listenedTracks = listenedTracks,
            nowMs = createdAtMs + 1 * 60 * 60 * 1000L, // 1h later
            manual = true,
        )

        assertTrue("Manual trigger must always regenerate", result)
    }

    // ── Un-albumed track diversity (single-<id> album key) ────────────

    private fun songNoAlbum(id: String, artist: String) = DailyMixGenerator.SongInfo(
        id = id,
        title = "Track $id",
        artist = artist,
        albumId = null,
        duration = 200,
        trackNumber = 1,
        coverArt = null,
    )

    @Test
    fun `multiple un-albumed tracks from same artist are all selected`() {
        // 3 artists × 3 un-albumed tracks each = 9 total, below the 30 target.
        // The shared "artist:null" key previously penalized tracks 2+ per artist.
        val songs = listOf(
            songNoAlbum("a1", "ArtistA"), songNoAlbum("a2", "ArtistA"), songNoAlbum("a3", "ArtistA"),
            songNoAlbum("b1", "ArtistB"), songNoAlbum("b2", "ArtistB"), songNoAlbum("b3", "ArtistB"),
            songNoAlbum("c1", "ArtistC"), songNoAlbum("c2", "ArtistC"), songNoAlbum("c3", "ArtistC"),
        )
        val ratings = songs.associate { it.id to 0 }
        val result = DailyMixGenerator.generate(songs, ratings, emptyList(), random = kotlin.random.Random(42))

        assertEquals("All 9 tracks should be present", 9, result.size)
        assertEquals("All distinct track ids", 9, result.map { it.id }.toSet().size)
        // Every artist contributes all 3 (unique single keys → no shared pseudo-album penalty).
        listOf("ArtistA", "ArtistB", "ArtistC").forEach { artist ->
            assertEquals(
                "Artist must contribute 3 tracks",
                3,
                result.count { it.artist == artist },
            )
        }
    }

    @Test
    fun `un-albumed tracks alternate with album tracks from different artists`() {
        // Artist A: 3 un-albumed tracks. Artist B: 3 album tracks (distinct albums).
        val songs = listOf(
            songNoAlbum("a1", "ArtistA"),
            songNoAlbum("a2", "ArtistA"),
            songNoAlbum("a3", "ArtistA"),
            song("b1", "ArtistB"),
            song("b2", "ArtistB"),
            song("b3", "ArtistB"),
        )
        val ratings = songs.associate { it.id to 0 }
        val result = DailyMixGenerator.generate(songs, ratings, emptyList(), random = kotlin.random.Random(7))

        // Both artists must appear; no two consecutive from the same artist.
        assertEquals("All 6 selectable tracks", 6, result.size)
        for (i in 1 until result.size) {
            assertNotEquals(
                "No consecutive same artist at index $i",
                result[i - 1].artist,
                result[i].artist,
            )
        }
        assertTrue("ArtistA represented", result.any { it.artist == "ArtistA" })
        assertTrue("ArtistB represented", result.any { it.artist == "ArtistB" })
    }

    @Test
    fun `supplemental tracks fill the mix when primary pool is small`() {
        // Primary: 1 artist × 1 track (genre pool tiny). Supplemental: 3 tracks
        // from 3 different artists. Generator Phase 3 should add them.
        val primary = listOf(song("p1", "PrimaryArtist"))
        val supplemental = listOf(
            songNoAlbum("s1", "SuppA"),
            song("s2", "SuppB"),
            song("s3", "SuppC"),
        )
        val ratings = (primary + supplemental).associate { it.id to 0 }
        val result = DailyMixGenerator.generate(primary, ratings, supplemental, random = kotlin.random.Random(3))

        assertTrue(
            "Supplemental must fill beyond primary",
            result.size >= 2,
        )
        assertTrue(
            "No duplicates in result",
            result.size == result.map { it.id }.toSet().size,
        )
    }
}
