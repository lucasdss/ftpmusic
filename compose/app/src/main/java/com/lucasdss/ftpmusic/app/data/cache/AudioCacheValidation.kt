package com.lucasdss.ftpmusic.app.data.cache

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Shared validity checks for on-disk / in-flight audio cache payloads.
 *
 * Cache trust was previously `length() > 0`, which accepts Subsonic/Navidrome
 * HTTP 200 API error documents (~182B JSON/XML) forever — ExoPlayer then hits
 * Source error and auto-skip loops. Mirror [CoverArtFiles] for audio.
 *
 * Hybrid heal (ADR-0110): [PayloadVerdict.POISON] is deleted; [PayloadVerdict.UNKNOWN]
 * (exotic codecs without matched magic) stays on disk with pin intact.
 */
object AudioCacheValidation {

    /** Above observed poison cluster (182/304/379); below any real track. */
    const val MIN_CACHED_AUDIO_BYTES: Long = 4L * 1024L

    private val ASCII = Charset.forName("US-ASCII")

    /** Classification for cache heal / offline jump decisions. */
    enum class PayloadVerdict {
        /** Known audio magic, min size, not an API error body. */
        PLAYABLE,

        /** Undersize or Subsonic/Navidrome error document — safe to delete. */
        POISON,

        /** Large enough, not an error body, but magic unrecognized — keep pin. */
        UNKNOWN,
    }

    /** True when [file] exists, meets min size, is not an API error doc, and has audio magic. */
    fun looksLikeAudio(file: File): Boolean = classify(file) == PayloadVerdict.PLAYABLE

    fun classify(file: File): PayloadVerdict {
        if (!file.exists() || !file.isFile) return PayloadVerdict.POISON
        val size = file.length()
        if (size < MIN_CACHED_AUDIO_BYTES) return PayloadVerdict.POISON
        return try {
            val header = ByteArray(512.coerceAtMost(size.toInt()))
            RandomAccessFile(file, "r").use { raf ->
                val read = raf.read(header)
                if (read < 4) return PayloadVerdict.POISON
                val bytes = if (read == header.size) header else header.copyOf(read)
                classifyBytes(bytes, size)
            }
        } catch (_: Exception) {
            PayloadVerdict.UNKNOWN
        }
    }

    /** Validate a prefix already buffered in memory (download peek / tests). */
    fun looksLikeAudioBytes(header: ByteArray, totalSize: Long = header.size.toLong()): Boolean =
        classifyBytes(header, totalSize) == PayloadVerdict.PLAYABLE

    fun classifyBytes(header: ByteArray, totalSize: Long = header.size.toLong()): PayloadVerdict {
        if (totalSize < MIN_CACHED_AUDIO_BYTES) return PayloadVerdict.POISON
        if (header.size < 4) return PayloadVerdict.POISON
        if (looksLikeSubsonicError(header)) return PayloadVerdict.POISON
        return if (hasAudioMagic(header)) PayloadVerdict.PLAYABLE else PayloadVerdict.UNKNOWN
    }

    /** Subsonic/Navidrome error JSON/XML returned with HTTP 200 on /rest/stream. */
    fun looksLikeSubsonicError(header: ByteArray): Boolean {
        if (header.isEmpty()) return false
        val sample = String(header, 0, header.size.coerceAtMost(512), ASCII)
            .trimStart()
            .lowercase()
        if (sample.isEmpty()) return false
        if (sample.contains("subsonic-response") ||
            (sample.startsWith("<?xml") && sample.contains("subsonic"))
        ) {
            return true
        }
        return sample.startsWith("{") &&
            sample.contains("\"status\"") &&
            (sample.contains("\"failed\"") || sample.contains("\"error\""))
    }

    fun hasAudioMagic(header: ByteArray): Boolean {
        if (header.size < 4) return false
        val four = String(header, 0, 4, ASCII)
        if (four.startsWith("ID3") || four == "fLaC" || four == "OggS") return true
        // WavPack
        if (four == "wvpk") return true
        // AIFF / AIFF-C — FORM….AIFF or FORM….AIFC
        if (header.size >= 12 && four == "FORM") {
            val formType = String(header, 8, 4, ASCII)
            if (formType == "AIFF" || formType == "AIFC") return true
        }
        // MPEG frame sync (MP3 without ID3)
        if (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0) return true
        // RIFF / WAVE
        if (header.size >= 12 && four == "RIFF" &&
            String(header, 8, 4, ASCII) == "WAVE"
        ) {
            return true
        }
        // ISO BMFF (M4A / MP4) — "ftyp" at offset 4
        return header.size >= 8 && String(header, 4, 4, ASCII) == "ftyp"
    }

    /**
     * Soft Content-Type gate for download responses. Unknown/empty/octet-stream
     * pass through (magic check is authoritative). Explicit non-audio types fail
     * except common m4a/flac server mislabels.
     */
    fun isAcceptableStreamContentType(contentType: String?): Boolean {
        if (contentType.isNullOrBlank()) return true
        val ct = contentType.substringBefore(';').trim().lowercase()
        if (ct.startsWith("audio/")) return true
        if (ct == "application/octet-stream" || ct == "binary/octet-stream") return true
        if (ct == "application/ogg") return true
        // Servers often label M4A / ALAC as mp4 video/application
        if (ct == "video/mp4" || ct == "application/mp4") return true
        if (ct == "application/x-flac") return true
        return false
    }
}
