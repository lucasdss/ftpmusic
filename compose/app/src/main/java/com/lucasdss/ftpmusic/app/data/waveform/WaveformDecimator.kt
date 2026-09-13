package com.lucasdss.ftpmusic.app.data.waveform

/**
 * Decimates stored waveform bars down to a target on-screen bar count.
 *
 * Uses the same 0.65·mean + 0.35·max bucket blend as the asset generator so
 * percussive transients survive downscaling. Never upsamples — the target is
 * coerced to `[1, bars.size]`, so a coarse cache (e.g. old 100-value rows) on
 * a wide screen renders as-is instead of fabricating detail. Inputs are
 * guaranteed by [WaveformAssetLoader.parseBars] to be finite and in [0,1];
 * the output is a convex combination and therefore also stays in [0,1].
 */
object WaveformDecimator {

    fun decimate(bars: List<Float>, targetCount: Int): List<Float> {
        if (bars.isEmpty()) return emptyList()
        val n = targetCount.coerceIn(1, bars.size)
        if (n >= bars.size) return bars

        val result = ArrayList<Float>(n)
        val bucket = bars.size.toDouble() / n
        for (i in 0 until n) {
            val lo = (i * bucket).toInt()
            val hi = maxOf(lo + 1, ((i + 1) * bucket).toInt()).coerceAtMost(bars.size)
            var sum = 0.0
            var max = 0f
            for (j in lo until hi) {
                val v = bars[j]
                sum += v
                if (v > max) max = v
            }
            val mean = (sum / (hi - lo)).toFloat()
            result.add(mean * 0.65f + max * 0.35f)
        }
        return result
    }
}
