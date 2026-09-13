package com.lucasdss.ftpmusic.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {

    @Test
    fun `stripHtmlTags removes br tags`() {
        assertEquals("Line one\nLine two", stripHtmlTags("Line one<br>Line two"))
    }

    @Test
    fun `stripHtmlTags removes anchor tags`() {
        assertEquals("Click here", stripHtmlTags("<a href=\"#\">Click here</a>"))
    }

    @Test
    fun `stripHtmlTags decodes HTML entities`() {
        assertEquals("\"Hello\" & 'World'", stripHtmlTags("&quot;Hello&quot; &amp; &#39;World&#39;"))
    }

    @Test
    fun `stripHtmlTags handles plain text unchanged`() {
        assertEquals("No tags here", stripHtmlTags("No tags here"))
    }

    @Test
    fun `stripHtmlTags handles complex lyrics markup`() {
        val input = "<div class=\"lyric\"><b>Verse 1:</b><br>I'm singing<br><i>loudly</i></div>"
        assertEquals("Verse 1:\nI'm singing\nloudly", stripHtmlTags(input))
    }

    @Test
    fun `stripHtmlTags trims whitespace`() {
        assertEquals("text", stripHtmlTags("  <br>  text  <br>  "))
    }

    // ── cleanLyricText timestamp stripping ──────────────────────────

    @Test
    fun `cleanLyricText strips LRC timestamps from display text`() {
        val input = "[00:15.23]Hello world"
        assertEquals("Hello world", cleanLyricText(input))
    }

    @Test
    fun `cleanLyricText strips multiple LRC timestamps`() {
        val input = "[00:15.23]Line one[00:32.50]Line two"
        assertEquals("Line oneLine two", cleanLyricText(input))
    }

    @Test
    fun `cleanLyricText handles 3-digit milliseconds`() {
        val input = "[01:02.003]Verse"
        assertEquals("Verse", cleanLyricText(input))
    }

    @Test
    fun `cleanLyricText does not strip escaped backslash-n`() {
        val input = "Line one\\nLine two"
        assertEquals("Line one\nLine two", cleanLyricText(input))
    }

    @Test
    fun `cleanLyricText preserves text without timestamps`() {
        val input = "Plain lyrics with no timestamps"
        assertEquals("Plain lyrics with no timestamps", cleanLyricText(input))
    }

    @Test
    fun `cleanLyricText strips HTML and timestamps together`() {
        val input = "<b>[00:10.50]Chorus line</b><br>[00:20.00]Next"
        assertEquals("Chorus line\nNext", cleanLyricText(input))
    }

    // ── parseLrcText unit tests ──────────────────────────────────────

    @Test
    fun `parseLrcText extracts single timestamped line`() {
        val input = "[00:15.23]Hello world"
        val result = parseLrcText(input)
        assertEquals(1, result.size)
        // 2-digit = centiseconds: 23 → 230ms. 15*1000 + 230 = 15230
        assertEquals(15_230L, result[0].timeMs)
        assertEquals("Hello world", result[0].text)
    }

    @Test
    fun `parseLrcText extracts multiple timestamped lines`() {
        val input = "[00:15.23]First line\n[00:32.50]Second line\n[01:02.00]Third line"
        val result = parseLrcText(input)
        assertEquals(3, result.size)
        // 2-digit ms → centiseconds → ×10 for ms
        assertEquals(15_230L, result[0].timeMs)
        assertEquals("First line", result[0].text)
        assertEquals(32_500L, result[1].timeMs)
        assertEquals("Second line", result[1].text)
        assertEquals(62_000L, result[2].timeMs)
        assertEquals("Third line", result[2].text)
    }

    @Test
    fun `parseLrcText handles 3-digit milliseconds`() {
        val input = "[01:30.123]Three-digit ms"
        val result = parseLrcText(input)
        assertEquals(1, result.size)
        assertEquals(90_123L, result[0].timeMs)
        assertEquals("Three-digit ms", result[0].text)
    }

    @Test
    fun `parseLrcText handles 2-digit milliseconds`() {
        val input = "[02:45.50]Two-digit ms"
        val result = parseLrcText(input)
        assertEquals(1, result.size)
        assertEquals(165_500L, result[0].timeMs)
        assertEquals("Two-digit ms", result[0].text)
    }

    @Test
    fun `parseLrcText returns empty list for text without timestamps`() {
        val input = "Plain text\nNo timestamps here"
        val result = parseLrcText(input)
        assertEquals(0, result.size)
    }

    @Test
    fun `parseLrcText strips HTML from extracted lyric text`() {
        val input = "[00:05.00]<b>Bold lyric</b>"
        val result = parseLrcText(input)
        assertEquals(1, result.size)
        assertEquals(5_000L, result[0].timeMs)
        assertEquals("Bold lyric", result[0].text)
    }

    @Test
    fun `parseLrcText handles 1-digit milliseconds`() {
        val input = "[00:05.2]One-digit ms"
        val result = parseLrcText(input)
        assertEquals(1, result.size)
        // 1-digit = deciseconds: 2 → 200ms. 5*1000 + 200 = 5200
        assertEquals(5_200L, result[0].timeMs)
        assertEquals("One-digit ms", result[0].text)
    }

    @Test
    fun `parseLrcText handles no milliseconds`() {
        val input = "[00:15]No milliseconds"
        val result = parseLrcText(input)
        assertEquals(1, result.size)
        assertEquals(15_000L, result[0].timeMs)
        assertEquals("No milliseconds", result[0].text)
    }

    @Test
    fun `parseLrcText mixed ms formats`() {
        val input = "[00:05.100]3-digit\n[00:10.50]2-digit\n[00:15.2]1-digit\n[00:20]none"
        val result = parseLrcText(input)
        assertEquals(4, result.size)
        assertEquals(5_100L, result[0].timeMs) // 3-digit: 100ms
        assertEquals(10_500L, result[1].timeMs) // 2-digit: 50 → 500ms
        assertEquals(15_200L, result[2].timeMs) // 1-digit: 2 → 200ms
        assertEquals(20_000L, result[3].timeMs) // none: 0ms
    }

    @Test
    fun `parseLrcText handles empty input`() {
        assertEquals(0, parseLrcText("").size)
        assertEquals(0, parseLrcText("   ").size)
    }

    @Test
    fun `parseLrcText preserves untimed intro text before first timestamp`() {
        val input = "Intro stanza without timestamp\n[00:05.00]First timed line"
        val result = parseLrcText(input)
        assertEquals(2, result.size)
        assertEquals(0L, result[0].timeMs)
        assertEquals("Intro stanza without timestamp", result[0].text)
        assertEquals(5_000L, result[1].timeMs)
        assertEquals("First timed line", result[1].text)
    }

    @Test
    fun `parseLrcText skips pre-text that is just whitespace or metadata`() {
        // Only whitespace before timestamp — not lyrics
        val input = "   \n[00:05.00]Timed line"
        val result = parseLrcText(input)
        assertEquals(1, result.size)
        assertEquals(5_000L, result[0].timeMs)
    }

    @Test
    fun `parseLrcText handles multi-line untimed intro`() {
        val input = "First verse\nSecond verse\n[00:10.00]Chorus"
        val result = parseLrcText(input)
        assertEquals(2, result.size)
        assertEquals(0L, result[0].timeMs)
        assertTrue(result[0].text.contains("First verse"))
        assertTrue(result[0].text.contains("Second verse"))
    }

    @Test
    fun `parseLrcText no duplication when first line is at time zero`() {
        val input = "[00:00.00]Already at zero"
        val result = parseLrcText(input)
        // Should have exactly 1 line — the timed line at 0ms
        assertEquals(1, result.size)
        assertEquals(0L, result[0].timeMs)
        assertEquals("Already at zero", result[0].text)
    }

    @Test
    fun `parseLrcText sort order preserved as-is`() {
        val input = "[00:30.00]Middle\n[00:10.00]Early\n[00:50.00]Late"
        val result = parseLrcText(input)
        assertEquals(3, result.size)
        assertEquals(30_000L, result[0].timeMs) // first in source = first in result
        assertEquals(10_000L, result[1].timeMs)
        assertEquals(50_000L, result[2].timeMs)
    }

    // ── Integration: raw structured lines → LRC fallback ─────────────

    @Test
    fun `LRC fallback recovers timestamps from raw structured lines`() {
        // Simulate what NavHost does when API returns lines with start=0 and
        // LRC timestamps embedded in value strings.
        val rawLines = listOf(
            Pair(0L, "[00:15.23]First line"),
            Pair(0L, "[00:32.50]Second line"),
            Pair(0L, "[01:02.00]Third line"),
        )

        // Step 1: build lyricLines with cleaned text (will have all timeMs=0)
        val cleaned = rawLines.map { (start, value) ->
            LyricLine(start, cleanLyricText(value))
        }
        // All timeMs should be 0
        assert(cleaned.all { it.timeMs == 0L }) { "All starts are 0 before LRC fallback" }

        // Step 2: LRC fallback — use raw values, NOT cleaned text
        val combinedRaw = rawLines.joinToString("\n") { it.second }
        val lrcParsed = parseLrcText(combinedRaw)
        assert(lrcParsed.isNotEmpty()) { "LRC fallback should produce synced lines" }

        // Step 3: verify synced lines have correct timeMs (2-digit = centiseconds)
        assertEquals(3, lrcParsed.size)
        assertEquals(15_230L, lrcParsed[0].timeMs)
        assertEquals("First line", lrcParsed[0].text)
        assertEquals(32_500L, lrcParsed[1].timeMs)
        assertEquals("Second line", lrcParsed[1].text)
        assertEquals(62_000L, lrcParsed[2].timeMs)
        assertEquals("Third line", lrcParsed[2].text)
    }

    @Test
    fun `LRC fallback with HTML in values`() {
        // Server may return HTML inside LRC-embedded values
        val rawLines = listOf(
            Pair(0L, "[00:05.00]<b>Bold</b> lyric"),
            Pair(0L, "[00:10.00]Normal lyric<br>"),
        )
        val combinedRaw = rawLines.joinToString("\n") { it.second }
        val result = parseLrcText(combinedRaw)
        assertEquals(2, result.size)
        assertEquals(5_000L, result[0].timeMs)
        assertEquals("Bold lyric", result[0].text)
        assertEquals(10_000L, result[1].timeMs)
        assertEquals("Normal lyric", result[1].text)
    }

    @Test
    fun `cleanLyricText applied to structured line with non-zero start`() {
        // When server sends proper start times, cleanLyricText should strip
        // any embedded [mm:ss.xx] cruft but timeMs comes from start field
        val line = LyricLine(15_230L, cleanLyricText("[00:15.23]Hello"))
        assertEquals(15_230L, line.timeMs)
        assertEquals("Hello", line.text)
    }

    @Test
    fun `position tracking works with parsed LRC lines`() {
        val lines = listOf(
            LyricLine(0L, "Intro"),
            LyricLine(10_000L, "Verse 1"),
            LyricLine(30_000L, "Chorus"),
            LyricLine(60_000L, "Verse 2"),
            LyricLine(90_000L, "Outro"),
        )

        // activeIndex = last line where timeMs <= positionMs
        fun activeAt(pos: Long) = lines.indexOfLast { it.timeMs <= pos }

        assertEquals(0, activeAt(0)) // 0 <= 0 → intro
        assertEquals(0, activeAt(5_000)) // 0 ≤ 5000 → intro
        assertEquals(1, activeAt(10_000)) // 10000 ≤ 10000 → verse 1
        assertEquals(1, activeAt(25_000)) // 25000 ≤ 30000? No, 25000 > 10000? yes, but 25000 < 30000 →
        // indexOfLast: 10000≤25000✓, 30000≤25000✗ → verse 1
        assertEquals(2, activeAt(30_000)) // chorus
        assertEquals(4, activeAt(120_000)) // outro
    }

    // ── P4: binary search replaces the O(n) indexOfLast scan ────────────

    private val sampleLines = listOf(
        LyricLine(0L, "Intro"),
        LyricLine(10_000L, "Verse 1"),
        LyricLine(30_000L, "Chorus"),
        LyricLine(60_000L, "Verse 2"),
        LyricLine(90_000L, "Outro"),
    )

    @Test
    fun `binary search matches indexOfLast across positions`() {
        for (pos in longArrayOf(0, 5_000, 10_000, 25_000, 30_000, 59_999, 60_000, 89_999, 90_000, 120_000)) {
            val expected = sampleLines.indexOfLast { it.timeMs <= pos }
            assertEquals("mismatch at pos=$pos", expected, lastLyricIndexBefore(sampleLines, pos))
        }
    }

    @Test
    fun `binary search returns -1 before the first timestamp`() {
        assertEquals(-1, lastLyricIndexBefore(listOf(LyricLine(5_000L, "A")), 4_999))
    }

    @Test
    fun `binary search handles empty and single-line lists`() {
        assertEquals(-1, lastLyricIndexBefore(emptyList(), 100_000))
        assertEquals(0, lastLyricIndexBefore(listOf(LyricLine(7_000L, "Only")), 7_000))
        assertEquals(-1, lastLyricIndexBefore(listOf(LyricLine(7_000L, "Only")), 6_999))
    }

    @Test
    fun `binary search works on sorted unsorted input`() {
        val unsorted = listOf(
            LyricLine(30_000L, "Chorus"),
            LyricLine(0L, "Intro"),
            LyricLine(10_000L, "Verse 1"),
        )
        val sorted = unsorted.sortedBy { it.timeMs }
        assertEquals(2, lastLyricIndexBefore(sorted, 120_000))
        assertEquals(1, lastLyricIndexBefore(sorted, 15_000))
        assertEquals(0, lastLyricIndexBefore(sorted, 5_000))
    }
}
