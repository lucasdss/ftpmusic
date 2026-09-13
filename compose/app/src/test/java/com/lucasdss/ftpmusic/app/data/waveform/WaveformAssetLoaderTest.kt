package com.lucasdss.ftpmusic.app.data.waveform

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformAssetLoaderTest {

    private fun barsJson(count: Int = WaveformAssetLoader.ASSET_BAR_COUNT, value: Float = 0.5f): String =
        (0 until count).joinToString(",", "[", "]") { value.toString() }

    private fun loader(source: WaveformAssetSource): WaveformAssetLoader = WaveformAssetLoader(source)

    // ── availableGenres ────────────────────────────────────────────────────

    @Test fun `availableGenres returns sorted non-blank folders`() {
        val source = FakeSource(
            dirs = mapOf("waveform" to listOf("Rock", "", "Blues", " ")),
        )
        assertEquals(listOf("Blues", "Rock"), loader(source).availableGenres())
    }

    @Test fun `availableGenres is empty when root dir missing`() {
        assertEquals(emptyList<String>(), loader(FakeSource()).availableGenres())
    }

    // ── loadRandomBars happy path ──────────────────────────────────────────

    @Test fun `loadRandomBars returns parsed bars from the genre folder`() {
        val json = barsJson()
        val source = FakeSource(
            dirs = mapOf("waveform/Rock" to listOf("11922.json")),
            files = mapOf("waveform/Rock/11922.json" to json),
        )
        val bars = loader(source).loadRandomBars("Rock")
        assertNotNull(bars)
        assertEquals(WaveformAssetLoader.ASSET_BAR_COUNT, bars!!.size)
        assertTrue(bars.all { it == 0.5f })
    }

    @Test fun `loadRandomBars picks randomly among files with seeded random`() {
        val source = FakeSource(
            dirs = mapOf("waveform/Rock" to listOf("a.json", "b.json")),
            files = mapOf(
                "waveform/Rock/a.json" to barsJson(value = 0.2f),
                "waveform/Rock/b.json" to barsJson(value = 0.8f),
            ),
        )
        val random = Random(7)
        val expectedIndex = random.nextInt(2) // same draw order as the loader
        val expected = if (expectedIndex == 0) 0.2f else 0.8f
        val bars = loader(source).loadRandomBars("Rock", Random(7))
        assertNotNull(bars)
        assertTrue(bars!!.all { it == expected })
    }

    // ── loadRandomBars failure paths ───────────────────────────────────────

    @Test fun `loadRandomBars returns null when genre dir missing`() {
        assertNull(loader(FakeSource()).loadRandomBars("Rock"))
    }

    @Test fun `loadRandomBars returns null when genre dir empty`() {
        val source = FakeSource(dirs = mapOf("waveform/Rock" to emptyList()))
        assertNull(loader(source).loadRandomBars("Rock"))
    }

    @Test fun `loadRandomBars returns null when file unreadable`() {
        val source = FakeSource(dirs = mapOf("waveform/Rock" to listOf("a.json")))
        assertNull(loader(source).loadRandomBars("Rock"))
    }

    @Test fun `loadRandomBars returns null on corrupt json`() {
        val source = FakeSource(
            dirs = mapOf("waveform/Rock" to listOf("a.json")),
            files = mapOf("waveform/Rock/a.json" to "{invalid"),
        )
        assertNull(loader(source).loadRandomBars("Rock"))
    }

    @Test fun `loadRandomBars ignores non-json files`() {
        val source = FakeSource(
            dirs = mapOf("waveform/Rock" to listOf("notes.txt", "a.json")),
            files = mapOf("waveform/Rock/a.json" to barsJson()),
        )
        assertEquals(WaveformAssetLoader.ASSET_BAR_COUNT, loader(source).loadRandomBars("Rock")!!.size)
    }

    // ── parseBars strictness ───────────────────────────────────────────────

    @Test fun `parseBars rejects counts below MIN or above MAX`() {
        val belowMin = (0 until WaveformAssetLoader.MIN_BARS - 1).joinToString(",", "[", "]") { "0.5" }
        val aboveMax = (0 until WaveformAssetLoader.MAX_BARS + 1).joinToString(",", "[", "]") { "0.5" }
        assertNull(WaveformAssetLoader(FakeSource()).parseBars(belowMin))
        assertNull(WaveformAssetLoader(FakeSource()).parseBars(aboveMax))
    }

    @Test fun `parseBars accepts old 100-value caches`() {
        val oldCache = barsJson(count = 100)
        val bars = WaveformAssetLoader(FakeSource()).parseBars(oldCache)
        assertNotNull(bars)
        assertEquals(100, bars!!.size)
        assertTrue(bars.all { it == 0.5f })
    }

    @Test fun `parseBars accepts boundaries MIN and MAX`() {
        val atMin = (0 until WaveformAssetLoader.MIN_BARS).joinToString(",", "[", "]") { "0.5" }
        val atMax = (0 until WaveformAssetLoader.MAX_BARS).joinToString(",", "[", "]") { "0.5" }
        assertEquals(WaveformAssetLoader.MIN_BARS, WaveformAssetLoader(FakeSource()).parseBars(atMin)!!.size)
        assertEquals(WaveformAssetLoader.MAX_BARS, WaveformAssetLoader(FakeSource()).parseBars(atMax)!!.size)
    }

    @Test fun `parseBars rejects non-array json`() {
        assertNull(WaveformAssetLoader(FakeSource()).parseBars("{}"))
        assertNull(WaveformAssetLoader(FakeSource()).parseBars("not json"))
        assertNull(WaveformAssetLoader(FakeSource()).parseBars("[]"))
    }

    @Test fun `parseBars rejects non-finite and out-of-range values`() {
        fun withBadValue(bad: String): String {
            val good = (0 until 99).joinToString(",") { "0.5" }
            return "[$bad,$good]"
        }
        assertNull(WaveformAssetLoader(FakeSource()).parseBars(withBadValue("NaN")))
        assertNull(WaveformAssetLoader(FakeSource()).parseBars(withBadValue("1.5")))
        assertNull(WaveformAssetLoader(FakeSource()).parseBars(withBadValue("-0.1")))
    }

    @Test fun `parseBars accepts real generated asset format`() {
        val json = javaClass.classLoader
            ?.getResourceAsStream("waveform-fixture/Rock_11922.json")
            ?.bufferedReader()
            ?.use { it.readText() }
        assertNotNull("fixture missing", json)
        val bars = WaveformAssetLoader(FakeSource()).parseBars(json!!)
        assertNotNull(bars)
        assertEquals(WaveformAssetLoader.ASSET_BAR_COUNT, bars!!.size)
        assertTrue(bars.all { it in 0f..1f })
    }

    // ── In-memory fake source ──────────────────────────────────────────────

    private class FakeSource(
        private val dirs: Map<String, List<String>> = emptyMap(),
        private val files: Map<String, String> = emptyMap(),
    ) : WaveformAssetSource {
        override fun list(path: String): List<String>? = dirs[path]
        override fun open(path: String): InputStream? = files[path]?.let { ByteArrayInputStream(it.toByteArray()) }
    }
}
