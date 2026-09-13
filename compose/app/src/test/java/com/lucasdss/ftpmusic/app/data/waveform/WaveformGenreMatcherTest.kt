package com.lucasdss.ftpmusic.app.data.waveform

import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformGenreMatcherTest {

    // Mirrors the real bundled asset folders (includes both Hip Hop spellings).
    private val genres = listOf(
        "Alternative", "Blues", "Classical", "Electronic", "Hip Hop", "Hip-Hop",
        "Jazz", "Metal", "Pop", "R&B", "Rock", "Rock and Roll", "Soundtrack",
    )

    // ── Exact matching ─────────────────────────────────────────────────────

    @Test fun `exact match is case-insensitive`() {
        assertEquals("Rock", WaveformGenreMatcher.match("ROCK", genres))
    }

    @Test fun `exact match ignores hyphen vs space`() {
        assertEquals("Hip Hop", WaveformGenreMatcher.match("Hip-Hop", genres))
    }

    @Test fun `exact match handles ampersand genre`() {
        assertEquals("R&B", WaveformGenreMatcher.match("R&B", genres))
    }

    @Test fun `exact match trims whitespace`() {
        assertEquals("Jazz", WaveformGenreMatcher.match("  Jazz  ", genres))
    }

    @Test fun `multi-value genre tries each part`() {
        assertEquals("Rock", WaveformGenreMatcher.match("Unknown Tag, Rock", genres))
    }

    // ── Alias map ──────────────────────────────────────────────────────────

    @Test fun `alias maps EDM to Electronic`() {
        assertEquals("Electronic", WaveformGenreMatcher.match("EDM", genres))
    }

    @Test fun `alias maps rnb to R&B`() {
        assertEquals("R&B", WaveformGenreMatcher.match("RnB", genres))
    }

    // ── Fuzzy (closest genre) matching ─────────────────────────────────────

    @Test fun `fuzzy match finds genre whose tokens contain the whole genre`() {
        val folders = listOf("Rock and Roll", "Blues")
        assertEquals("Rock and Roll", WaveformGenreMatcher.match("Rock", folders))
    }

    @Test fun `fuzzy match accepts partial token overlap at threshold`() {
        assertEquals("Classical", WaveformGenreMatcher.match("Classical Piano", genres))
    }

    @Test fun `fuzzy prefers genre fully contained in track genre`() {
        assertEquals("Metal", WaveformGenreMatcher.match("Symphonic Metal", listOf("Metal", "Blues")))
    }

    @Test fun `fuzzy tie-break is alphabetical`() {
        val result = WaveformGenreMatcher.match(
            "Symphonic Metal",
            listOf("Progressive Metal", "Industrial Metal", "Classical"),
        )
        assertEquals("Industrial Metal", result)
    }

    @Test fun `no token overlap returns null`() {
        assertNull(WaveformGenreMatcher.match("Experimental Noise", genres))
    }

    // ── Null / empty inputs ────────────────────────────────────────────────

    @Test fun `null genre returns null`() {
        assertNull(WaveformGenreMatcher.match(null, genres))
    }

    @Test fun `blank genre returns null`() {
        assertNull(WaveformGenreMatcher.match("   ", genres))
    }

    @Test fun `empty available genres returns null`() {
        assertNull(WaveformGenreMatcher.match("Rock", emptyList()))
    }

    // ── Random fallback ────────────────────────────────────────────────────

    @Test fun `pickRandom returns only members of the set`() {
        val rnd = Random(42)
        val picks = (0 until 50).map { WaveformGenreMatcher.pickRandom(genres, rnd) }
        assertTrue(picks.all { it in genres })
        assertTrue("expected variety across 50 picks", picks.distinct().size > 1)
    }

    @Test fun `pickRandom returns null on empty set`() {
        assertNull(WaveformGenreMatcher.pickRandom(emptyList(), Random(1)))
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    @Test fun `normalize strips punctuation and case`() {
        assertEquals("rb", WaveformGenreMatcher.normalize("R&B"))
        assertEquals("hiphop", WaveformGenreMatcher.normalize("Hip-Hop"))
    }

    @Test fun `normalize is locale-independent`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")) // Turkish: 'I'→'ı' with default locale
            assertEquals("indie", WaveformGenreMatcher.normalize("INDIE"))
            assertEquals(
                "Indie Rock",
                WaveformGenreMatcher.match("INDIE ROCK", listOf("Indie Rock", "Blues")),
            )
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `similarity is symmetric`() {
        val a = listOf("rock", "and", "roll")
        val b = listOf("rock", "blues")
        assertEquals(
            WaveformGenreMatcher.similarity(a, b),
            WaveformGenreMatcher.similarity(b, a),
        )
    }
}
