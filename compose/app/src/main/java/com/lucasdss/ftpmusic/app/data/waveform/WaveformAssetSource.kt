package com.lucasdss.ftpmusic.app.data.waveform

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over the bundled waveform assets so [WaveformAssetLoader] is
 * pure-JVM testable (unit tests use an in-memory fake).
 */
interface WaveformAssetSource {
    /** Lists entries under [path]; null when the path does not exist. */
    fun list(path: String): List<String>?

    /** Opens [path] for reading; null when missing or unreadable. */
    fun open(path: String): InputStream?
}

/** Production impl backed by [android.content.res.AssetManager]. */
@Singleton
class AndroidWaveformAssetSource @Inject constructor(@ApplicationContext private val context: Context) :
    WaveformAssetSource {

    override fun list(path: String): List<String>? = try {
        context.assets.list(path)?.toList()?.takeIf { it.isNotEmpty() }
    } catch (_: IOException) {
        null
    }

    override fun open(path: String): InputStream? = try {
        context.assets.open(path)
    } catch (_: IOException) {
        null
    }
}
