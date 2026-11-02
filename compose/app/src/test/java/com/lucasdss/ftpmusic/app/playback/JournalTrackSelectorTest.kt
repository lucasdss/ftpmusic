package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.QueueJournalEntity
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class JournalTrackSelectorTest {

    private fun entry(trackIdsJson: String, sourceType: String = "album", sourceId: String = "src") =
        QueueJournalEntity(
            sourceType = sourceType,
            sourceId = sourceId,
            trackIdsJson = trackIdsJson,
            sourceName = "Test",
        )

    private fun entity(
        id: String,
        artistId: String? = null,
        albumId: String? = null,
        genre: String? = null,
        playCount: Int = 0,
        userRating: Int? = null,
        starredAt: Long? = null,
        lastPlayedAt: Long? = null,
        cachedFilePath: String? = null,
        isDisliked: Boolean = false,
    ): TrackEntity = TrackEntity(
        id = id,
        title = id,
        artistId = artistId,
        albumId = albumId,
        genre = genre,
        playCount = playCount,
        userRating = userRating,
        starredAt = starredAt,
        lastPlayedAt = lastPlayedAt,
        cachedFilePath = cachedFilePath,
        isDisliked = isDisliked,
    )

    private fun jsonIds(prefix: String, count: Int): String =
        (0 until count).joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$prefix$it\"" }

    /** Zero-variance random → weighted selection picks the highest score first. */
    private object ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    // ── Empty / null inputs ───────────────────────────────────────────────

    @Test
    fun `empty entries returns empty list`() {
        val result = JournalTrackSelector.select(emptyList(), emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty currentTrackIds returns candidates`() {
        val entries = listOf(entry("""["t1","t2","t3"]"""))
        val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 5)
        assertTrue(result.isNotEmpty())
        assertTrue(result.size <= 5)
    }

    // ── Exclusion ──────────────────────────────────────────────────────────

    @Test
    fun `excludes tracks already in currentTrackIds`() {
        val entries = listOf(entry("""["t1","t2","t3"]"""))
        val result = JournalTrackSelector.select(entries, setOf("t1", "t2", "t3"), maxTracks = 3)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `partially excludes when some overlap`() {
        val entries = listOf(entry("""["t1","t2","t3","t4"]"""))
        val result = JournalTrackSelector.select(entries, setOf("t1", "t2"), maxTracks = 3)
        assertTrue(result.none { it == "t1" || it == "t2" })
        assertTrue(result.isNotEmpty())
    }

    // ── Max cap ────────────────────────────────────────────────────────────

    @Test
    fun `respects maxTracks`() {
        val entries = List(10) { i -> entry("""["t${i}a","t${i}b","t${i}c"]""") }
        val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 5)
        assertEquals(5, result.size)
    }

    @Test
    fun `returns fewer than max when pool is small`() {
        val entries = listOf(entry("""["t1","t2"]"""))
        val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 10)
        assertEquals(2, result.size)
    }

    // ── Dedup ──────────────────────────────────────────────────────────────

    @Test
    fun `returns distinct track IDs`() {
        // Same IDs in multiple entries to test distinct filtering
        val entries = listOf(
            entry("""["a","b","c"]"""),
            entry("""["b","c","d"]"""),
            entry("""["a","b","c"]"""),
        )
        repeat(10) {
            val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 4)
            assertEquals(result.size, result.distinct().size)
        }
    }

    // ── Weighting: newer entries get more weight ───────────────────────────

    @Test
    fun `more recent entries have higher weight`() {
        val entries = listOf(
            entry("""["r1","r2"]""").copy(position = 99), // newest: highest recency weight
            entry("""["m1","m2"]""").copy(position = 99), // middle
            entry("""["o1","o2"]""").copy(position = 99), // oldest
        )
        val result = JournalTrackSelector.select(
            parsed = JournalTrackSelector.parse(entries),
            hydrated = emptyMap(),
            maxTracks = 3,
            random = ZeroRandom,
        )
        assertEquals(listOf("r1", "r2", "m1"), result)
    }

    // ── Malformed JSON ─────────────────────────────────────────────────────

    @Test
    fun `malformed JSON is skipped`() {
        val entries = listOf(
            entry("not-json-at-all"),
            entry("""["valid1","valid2"]"""),
        )
        val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 5)
        // valid1/valid2 should appear; malformed entry is skipped
        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.startsWith("valid") })
    }

    @Test
    fun `all malformed JSON returns empty`() {
        val entries = listOf(entry("garbage"), entry("{bad}"), entry(""))
        val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 5)
        assertTrue(result.isEmpty())
    }

    // ── Full journal cap and bounded hydration ─────────────────────────────

    @Test
    fun `uses the full configured entry cap instead of a hardcoded 50`() {
        val entries = List(60) { i -> entry("""["t$i"]""") }
        val parsed = JournalTrackSelector.parse(entries)
        assertEquals(60, parsed.candidates.size)
        val result = JournalTrackSelector.select(parsed, emptyMap(), maxTracks = 60, random = ZeroRandom)
        assertEquals(60, result.size)
    }

    @Test
    fun `honors a smaller configured entry cap`() {
        // 100 entries, cap 10 → only the 10 newest are eligible.
        val entries = List(100) { i -> entry("""["t$i"]""") }
        val parsed = JournalTrackSelector.parse(entries, entryCap = 10)
        val result = JournalTrackSelector.select(parsed, emptyMap(), maxTracks = 100, random = ZeroRandom)
        assertEquals(10, result.size)
        assertTrue(result.all { it.removePrefix("t").toInt() < 10 })
    }

    @Test
    fun `bounds the hydration pool and reports truncation`() {
        val entries = listOf(entry(jsonIds("t", 100)))
        val parsed = JournalTrackSelector.parse(entries, poolCap = 7)
        assertEquals(7, parsed.candidates.size)
        assertTrue(parsed.truncated)
    }

    @Test
    fun `does not report truncation when the pool fits`() {
        val entries = listOf(entry(jsonIds("t", 5)))
        val parsed = JournalTrackSelector.parse(entries, poolCap = 7)
        assertEquals(5, parsed.candidates.size)
        assertFalse(parsed.truncated)
    }

    @Test
    fun `a 100-track entry can surface more than five unique tracks`() {
        val ids = (0 until 100).map { "t$it" }
        val entries = listOf(entry(jsonIds("t", 100)))
        val hydrated = ids.associateWith { entity(it) }
        val result = JournalTrackSelector.select(
            parsed = JournalTrackSelector.parse(entries),
            hydrated = hydrated,
            maxTracks = 10,
            random = ZeroRandom,
        )
        assertEquals(10, result.size)
        assertEquals(result.size, result.distinct().size)
    }

    // ── Position continuity (CP-9) ─────────────────────────────────────────

    @Test
    fun `prefers tracks after the current index in the matching entry`() {
        val ids = (0 until 100).map { "t$it" }
        val entries = listOf(entry(jsonIds("t", 100)).copy(position = 40))
        val hydrated = ids.associateWith { entity(it) }
        val result = JournalTrackSelector.select(
            parsed = JournalTrackSelector.parse(entries),
            hydrated = hydrated,
            signals = JournalTrackSelector.Signals(
                currentTrackId = "t50",
                excludedIds = setOf("t50"),
            ),
            maxTracks = 3,
            random = ZeroRandom,
        )
        assertEquals(listOf("t51", "t52", "t53"), result)
    }

    @Test
    fun `uses the saved position within a single entry`() {
        val ids = (0 until 100).map { "t$it" }
        val entries = listOf(entry(jsonIds("t", 100)).copy(position = 40))
        val hydrated = ids.associateWith { entity(it) }
        val result = JournalTrackSelector.select(
            parsed = JournalTrackSelector.parse(entries),
            hydrated = hydrated,
            maxTracks = 3,
            random = ZeroRandom,
        )
        assertEquals(listOf("t41", "t42", "t43"), result)
    }

    @Test
    fun `uses the saved position when the current track is not in the journal`() {
        val newer = entry(jsonIds("a", 100)).copy(sourceId = "al-new", position = 40)
        val older = entry(jsonIds("b", 100)).copy(sourceId = "al-old", position = 2)
        val hydrated = (0 until 100).associate { i ->
            "a$i" to entity("a$i")
        } + (0 until 100).associate { i ->
            "b$i" to entity("b$i")
        }
        val result = JournalTrackSelector.select(
            parsed = JournalTrackSelector.parse(listOf(newer, older)),
            hydrated = hydrated,
            maxTracks = 3,
            random = ZeroRandom,
        )
        assertEquals(listOf("a41", "a42", "a43"), result)
    }

    // ── Taste and recency signals ──────────────────────────────────────────

    @Test
    fun `prefers higher rated and more played tracks`() {
        val entries = listOf(entry("""["low","high"]"""))
        val hydrated = mapOf(
            "low" to entity("low", playCount = 0, userRating = 1),
            "high" to entity("high", playCount = 20, userRating = 5),
        )
        val result = JournalTrackSelector.select(
            parsed = JournalTrackSelector.parse(entries),
            hydrated = hydrated,
            maxTracks = 1,
            random = ZeroRandom,
        )
        assertEquals(listOf("high"), result)
    }

    @Test
    fun `down-ranks recently played tracks`() {
        val now = 1_700_000_000_000L
        val entries = listOf(entry("""["recent","stale"]"""))
        val hydrated = mapOf(
            "recent" to entity("recent", lastPlayedAt = now - 60 * 60 * 1000L),
            "stale" to entity("stale", lastPlayedAt = now - 30L * 24 * 60 * 60 * 1000L),
        )
        val result = JournalTrackSelector.select(
            parsed = JournalTrackSelector.parse(entries),
            hydrated = hydrated,
            signals = JournalTrackSelector.Signals(nowMs = now),
            maxTracks = 1,
            random = ZeroRandom,
        )
        assertEquals(listOf("stale"), result)
    }

    @Test
    fun `prefers the current artist and album`() {
        val entries = listOf(entry("""["other","same","flagged"]"""))
        val hydrated = mapOf(
            "other" to entity("other", artistId = "ar-other", albumId = "al-other"),
            "same" to entity("same", artistId = "ar-1", albumId = "al-1", genre = "Rock"),
            "flagged" to entity("flagged", artistId = "ar-1", albumId = "al-1", genre = "Rock", isDisliked = true),
        )
        val result = JournalTrackSelector.select(
            parsed = JournalTrackSelector.parse(entries),
            hydrated = hydrated,
            signals = JournalTrackSelector.Signals(
                currentArtistId = "ar-1",
                currentAlbumId = "al-1",
                currentGenre = "Rock",
            ),
            maxTracks = 1,
            random = ZeroRandom,
        )
        assertEquals(listOf("same"), result)
    }

    @Test
    fun `excludes track and album artist dislike ledgers`() {
        val entries = listOf(entry("""["ok","bad-track","bad-album","bad-artist"]"""))
        val hydrated = mapOf(
            "ok" to entity("ok"),
            "bad-track" to entity("bad-track", isDisliked = true),
            "bad-album" to entity("bad-album", albumId = "al-bad"),
            "bad-artist" to entity("bad-artist", artistId = "ar-bad"),
        )
        val result = JournalTrackSelector.select(
            parsed = JournalTrackSelector.parse(entries),
            hydrated = hydrated,
            signals = JournalTrackSelector.Signals(
                dislikedAlbumIds = setOf("al-bad"),
                dislikedArtistIds = setOf("ar-bad"),
            ),
            maxTracks = 4,
            random = ZeroRandom,
        )
        assertEquals(listOf("ok"), result)
    }

    @Test
    fun `soft diversity never underfills`() {
        val ids = (0 until 10).map { "a$it" } + listOf("b0", "b1")
        val entries = listOf(entry(ids.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }))
        val hydrated = (0 until 10).associate { i ->
            "a$i" to entity("a$i", artistId = "ar-a")
        } + mapOf(
            "b0" to entity("b0", artistId = "ar-b"),
            "b1" to entity("b1", artistId = "ar-b"),
        )
        val result = JournalTrackSelector.select(
            parsed = JournalTrackSelector.parse(entries),
            hydrated = hydrated,
            maxTracks = 5,
            random = ZeroRandom,
        )
        assertEquals(5, result.size)
        assertTrue("should include the second artist", "b0" in result && "b1" in result)
    }

    @Test
    fun `deterministic for a fixed seed`() {
        val entries = List(5) { i -> entry("""["a$i","b$i","c$i"]""") }
        val ids = entries.flatMap { entry ->
            listOf("a", "b", "c").map { prefix -> prefix + entries.indexOf(entry) }
        }
        val hydrated = ids.associateWith { id ->
            entity(id, playCount = id.last().digitToInt() * 5, userRating = id.last().digitToInt())
        }
        val parsed = JournalTrackSelector.parse(entries)
        val first = JournalTrackSelector.select(parsed, hydrated, maxTracks = 5, random = Random(42))
        val second = JournalTrackSelector.select(parsed, hydrated, maxTracks = 5, random = Random(42))
        assertEquals(first, second)
    }

    // ── Shuffle produces different results for different seeds ─────────────

    @Test
    fun `different seeds can produce different selections`() {
        val entries = List(5) { i -> entry("""["a$i","b$i","c$i"]""") }
        val hydrated = entries.flatMap { entry ->
            listOf("a", "b", "c").map { prefix -> prefix + entries.indexOf(entry) }
        }.associateWith { entity(it) }
        val parsed = JournalTrackSelector.parse(entries)
        val first = JournalTrackSelector.select(parsed, hydrated, maxTracks = 5, random = Random(1))
        val second = JournalTrackSelector.select(parsed, hydrated, maxTracks = 5, random = Random(2))
        assertNotEquals(first, second)
    }

    // ── Null JSON ──────────────────────────────────────────────────────────

    @Test
    fun `null JSON in entry is handled`() {
        val entries = listOf(entry("null"))
        val result = JournalTrackSelector.select(entries, emptySet())
        assertTrue(result.isEmpty())
    }
}
