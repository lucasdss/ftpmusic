package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackWaveformDao
import com.lucasdss.ftpmusic.app.data.db.TrackWaveformEntity
import com.lucasdss.ftpmusic.app.data.waveform.WaveformAssetLoader
import com.lucasdss.ftpmusic.app.data.waveform.WaveformAssetSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformRepositoryTest {

    private val dao: TrackWaveformDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)

    private fun barsJson(value: Float = 0.5f): String =
        // Deliberately 100 values — tests that old 100-value Room caches
        // remain valid under the loader's MIN..MAX range parser.
        (0 until 100).joinToString(",", "[", "]") { value.toString() }

    private fun fakeSource(
        dirs: Map<String, List<String>> = emptyMap(),
        files: Map<String, String> = emptyMap(),
    ): WaveformAssetSource = object : WaveformAssetSource {
        override fun list(path: String): List<String>? = dirs[path]
        override fun open(path: String): InputStream? = files[path]?.let { ByteArrayInputStream(it.toByteArray()) }
    }

    private fun repo(loader: WaveformAssetLoader) = WaveformRepository(dao, trackDao, loader)

    // ── Room cache ─────────────────────────────────────────────────────────

    @Test fun `getOrGenerate returns cached bars on hit`() = runTest {
        coEvery { dao.get("t1") } returns TrackWaveformEntity("t1", barsJson(0.5f), 1000)

        val bars = repo(WaveformAssetLoader(fakeSource())).getOrGenerate("t1")

        assertEquals(100, bars.size)
        assertTrue(bars.all { it == 0.5f })
        coVerify(exactly = 0) { trackDao.getGenre(any()) }
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test fun `getOrGenerate regenerates on corrupt cached JSON`() = runTest {
        coEvery { dao.get("t1") } returns TrackWaveformEntity("t1", "not json", 1000)

        val bars = repo(WaveformAssetLoader(fakeSource())).getOrGenerate("t1")

        assertEquals(100, bars.size)
        coVerify { dao.deleteByTrackIds(listOf("t1")) }
        coVerify { dao.upsert(any()) }
    }

    @Test fun `getOrGenerate regenerates on wrong-length cached JSON`() = runTest {
        coEvery { dao.get("t1") } returns TrackWaveformEntity("t1", "[0.5,0.3]", 1000)

        val bars = repo(WaveformAssetLoader(fakeSource())).getOrGenerate("t1")

        assertEquals(100, bars.size)
        coVerify { dao.deleteByTrackIds(listOf("t1")) }
    }

    // ── Asset loading by genre ─────────────────────────────────────────────

    @Test fun `getOrGenerate loads random asset from exact genre match`() = runTest {
        coEvery { dao.get("t1") } returns null
        coEvery { trackDao.getGenre("t1") } returns "Rock"
        val source = fakeSource(
            dirs = mapOf("waveform" to listOf("Jazz", "Rock"), "waveform/Rock" to listOf("11922.json")),
            files = mapOf("waveform/Rock/11922.json" to barsJson(0.3f)),
        )

        val bars = repo(WaveformAssetLoader(source)).getOrGenerate("t1")

        assertEquals(100, bars.size)
        assertTrue(bars.all { it == 0.3f })
        coVerify { dao.upsert(match { it.trackId == "t1" && it.barsJson == barsJson(0.3f) }) }
    }

    @Test fun `getOrGenerate falls back to closest genre via fuzzy match`() = runTest {
        coEvery { dao.get("t1") } returns null
        coEvery { trackDao.getGenre("t1") } returns "Rock"
        val source = fakeSource(
            dirs = mapOf(
                "waveform" to listOf("Rock and Roll"),
                "waveform/Rock and Roll" to listOf("27881.json"),
            ),
            files = mapOf("waveform/Rock and Roll/27881.json" to barsJson(0.7f)),
        )

        val bars = repo(WaveformAssetLoader(source)).getOrGenerate("t1")

        assertTrue(bars.all { it == 0.7f })
    }

    @Test fun `getOrGenerate uses random genre when track genre is null`() = runTest {
        coEvery { dao.get("t1") } returns null
        coEvery { trackDao.getGenre("t1") } returns null
        val source = fakeSource(
            dirs = mapOf(
                "waveform" to listOf("Blues", "Jazz"),
                "waveform/Blues" to listOf("b.json"),
                "waveform/Jazz" to listOf("j.json"),
            ),
            files = mapOf(
                "waveform/Blues/b.json" to barsJson(0.2f),
                "waveform/Jazz/j.json" to barsJson(0.9f),
            ),
        )

        val bars = repo(WaveformAssetLoader(source)).getOrGenerate("t1")

        assertEquals(100, bars.size)
        assertTrue("expected a genre asset bar value", bars.all { it == 0.2f || it == 0.9f })
    }

    // ── Fallback to synthetic generation ───────────────────────────────────

    @Test fun `getOrGenerate falls back to synthetic bars when no assets`() = runTest {
        coEvery { dao.get("t1") } returns null

        val bars = repo(WaveformAssetLoader(fakeSource())).getOrGenerate("t1")

        assertEquals(100, bars.size)
        bars.forEach { assertTrue("bar $it in range", it in 0.04f..1.0f) }
    }

    @Test fun `same trackId produces identical fallback bars`() = runTest {
        coEvery { dao.get(any()) } returns null
        val loader = WaveformAssetLoader(fakeSource())

        val bars1 = repo(loader).getOrGenerate("same-track")
        val bars2 = repo(loader).getOrGenerate("same-track")

        assertEquals(bars1, bars2)
    }

    @Test fun `different trackIds produce different fallback bars`() = runTest {
        coEvery { dao.get(any()) } returns null
        val loader = WaveformAssetLoader(fakeSource())

        val bars1 = repo(loader).getOrGenerate("11111111-aaaa-bbbb-cccc-000000000001")
        val bars2 = repo(loader).getOrGenerate("22222222-aaaa-bbbb-cccc-000000000002")

        assertNotEquals(bars1, bars2)
    }

    // ── Failure tolerance ──────────────────────────────────────────────────

    @Test fun `getOrGenerate tolerates track dao failure`() = runTest {
        coEvery { dao.get("t1") } returns null
        coEvery { trackDao.getGenre(any()) } throws RuntimeException("db down")
        val source = fakeSource(
            dirs = mapOf("waveform" to listOf("Rock"), "waveform/Rock" to listOf("a.json")),
            files = mapOf("waveform/Rock/a.json" to barsJson(0.4f)),
        )

        val bars = repo(WaveformAssetLoader(source)).getOrGenerate("t1")

        assertEquals(100, bars.size)
        assertTrue(bars.all { it == 0.4f })
    }

    @Test fun `getOrGenerate degrades to synthetic when room fails`() = runTest {
        coEvery { dao.get("t1") } throws RuntimeException("db broken")

        val bars = repo(WaveformAssetLoader(fakeSource())).getOrGenerate("t1")

        assertEquals(100, bars.size)
        bars.forEach { assertTrue(it in 0.04f..1.0f) }
    }

    @Test fun `getOrGenerate rethrows cancellation from asset loader`() = runTest {
        coEvery { dao.get("t1") } returns null
        val loader = mockk<WaveformAssetLoader>()
        every { loader.parseBars(any()) } returns null
        every { loader.availableGenres() } throws CancellationException("cancelled")

        var caught: Throwable? = null
        try {
            repo(loader).getOrGenerate("t1")
        } catch (e: CancellationException) {
            caught = e
        }

        assertNotNull("CancellationException must propagate, not be swallowed", caught)
    }

    @Test fun `getOrGenerate tolerates loader failure`() = runTest {
        coEvery { dao.get("t1") } returns null
        val loader = mockk<WaveformAssetLoader>()
        every { loader.parseBars(any()) } returns null
        every { loader.availableGenres() } throws IllegalStateException("assets broken")

        val bars = repo(loader).getOrGenerate("t1")

        assertEquals(100, bars.size)
        bars.forEach { assertTrue(it in 0.04f..1.0f) }
    }

    @Test fun `getOrGenerate falls back when genre folder yields no bars`() = runTest {
        coEvery { dao.get("t1") } returns null
        coEvery { trackDao.getGenre("t1") } returns "Rock"
        val source = fakeSource(
            dirs = mapOf("waveform" to listOf("Rock"), "waveform/Rock" to listOf("a.json")),
        )

        val bars = repo(WaveformAssetLoader(source)).getOrGenerate("t1")

        assertEquals(100, bars.size)
        bars.forEach { assertTrue(it in 0.04f..1.0f) }
    }

    // ── deleteByTrackIds ───────────────────────────────────────────────────

    @Test fun `deleteByTrackIds does nothing on empty list`() = runTest {
        repo(WaveformAssetLoader(fakeSource())).deleteByTrackIds(emptyList())
        coVerify(exactly = 0) { dao.deleteByTrackIds(any()) }
    }

    @Test fun `deleteByTrackIds delegates to DAO`() = runTest {
        repo(WaveformAssetLoader(fakeSource())).deleteByTrackIds(listOf("t1", "t2"))
        coVerify { dao.deleteByTrackIds(listOf("t1", "t2")) }
    }

    @Test fun `deleteByTrackIds chunks large lists`() = runTest {
        val many = (1..1200).map { "t$it" }
        repo(WaveformAssetLoader(fakeSource())).deleteByTrackIds(many)

        coVerify { dao.deleteByTrackIds(match { it.size <= 500 }) }
    }
}
