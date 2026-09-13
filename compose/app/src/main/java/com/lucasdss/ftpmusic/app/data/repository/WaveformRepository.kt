package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackWaveformDao
import com.lucasdss.ftpmusic.app.data.db.TrackWaveformEntity
import com.lucasdss.ftpmusic.app.data.waveform.WaveformAssetLoader
import com.lucasdss.ftpmusic.app.data.waveform.WaveformGenreMatcher
import java.lang.StringBuilder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*
import kotlin.random.Random
import kotlinx.coroutines.CancellationException

/**
 * Provides the 100-bar waveform used by the Now Playing scrubber.
 *
 * Source priority:
 *  1. Room cache (`track_waveforms`) — fast path, stable per track.
 *  2. Bundled per-genre waveform assets: a random example from the matching
 *     genre folder, the closest genre when there is no exact match, or a
 *     random genre/file as a last resort.
 *  3. Deterministic synthetic fallback so the UI never breaks.
 */
@Singleton
class WaveformRepository @Inject constructor(
    private val dao: TrackWaveformDao,
    private val trackDao: TrackDao,
    private val assetLoader: WaveformAssetLoader,
) {
    companion object {
        const val BAR_COUNT = 100
        private const val MIN_AMP = 0.04f
    }

    suspend fun getOrGenerate(trackId: String): List<Float> = try {
        val cached = dao.get(trackId)
        if (cached != null) {
            assetLoader.parseBars(cached.barsJson)?.let { return it }
            dao.deleteByTrackIds(listOf(trackId))
        }
        val bars = loadFromAssets(trackId) ?: generateBars(trackId)
        dao.upsert(TrackWaveformEntity(trackId, serializeBars(bars)))
        bars
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Never let a DB/IO failure break the scrubber — degrade to synthetic.
        generateBars(trackId)
    }

    /**
     * Genre-matched asset bars, or null when no asset is usable so the caller
     * falls back to synthetic generation. Never throws (cancellation aside).
     */
    private suspend fun loadFromAssets(trackId: String): List<Float>? {
        val genre = resolveGenre(trackId)
        val available = try {
            assetLoader.availableGenres()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (available.isEmpty()) return null
        val folder = WaveformGenreMatcher.match(genre, available)
            ?: WaveformGenreMatcher.pickRandom(available, Random.Default)
            ?: return null
        return try {
            assetLoader.loadRandomBars(folder)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveGenre(trackId: String): String? = try {
        trackDao.getGenre(trackId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    suspend fun deleteByTrackIds(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        trackIds.chunked(500).forEach { chunk ->
            dao.deleteByTrackIds(chunk)
        }
    }

    private fun serializeBars(bars: List<Float>): String {
        val sb = StringBuilder("[")
        bars.forEachIndexed { i, f ->
            if (i > 0) sb.append(",")
            sb.append(f)
        }
        sb.append("]")
        return sb.toString()
    }

    /**
     * Deterministic pseudo-random waveform generation seeded by track ID —
     * final fallback only (no bundled asset usable). Same track ID always
     * produces the same bars.
     */
    private fun generateBars(trackId: String): List<Float> {
        val seed = trackId.hashCode().toLong().absoluteValue
        val s1 = seed * 9301 + 49297
        val s2 = seed * 1664525 + 1013904223

        return (0 until BAR_COUNT).map { i ->
            val t = i.toFloat() / BAR_COUNT
            val lo = abs(sin(s1 * 0.00001f + t * 6.3f))
            val mid = abs(sin(s2 * 0.000007f + t * 19.7f + 1.2f))
            val hi = abs(sin(s1 * 0.000013f + t * 41.1f + 2.9f))
            val raw = lo * 0.45f + mid * 0.35f + hi * 0.20f
            val env = 0.35f + 0.65f * sin(t * PI.toFloat()).pow(0.55f)
            max(MIN_AMP, min(1f, raw * env))
        }
    }
}
