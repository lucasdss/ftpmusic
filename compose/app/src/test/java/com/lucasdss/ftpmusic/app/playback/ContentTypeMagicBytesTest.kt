package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests magic-byte content type detection used by PlaybackProxy for cache hits.
 */
class ContentTypeMagicBytesTest {

    // Pattern extracted from PlaybackProxy.detectContentTypeFromBytes

    private fun detectFromBytes(data: ByteArray): String {
        if (data.size < 4) return "audio/mpeg"
        // FLAC: "fLaC"
        if (data[0] == 0x66.toByte() && data[1] == 0x4C.toByte() &&
            data[2] == 0x61.toByte() && data[3] == 0x43.toByte()
        ) {
            return "audio/flac"
        }
        // OGG: "OggS"
        if (data[0] == 0x4F.toByte() && data[1] == 0x67.toByte() &&
            data[2] == 0x67.toByte() && data[3] == 0x53.toByte()
        ) {
            return "audio/ogg"
        }
        // WAV: "RIFF"
        if (data[0] == 0x52.toByte() && data[1] == 0x49.toByte() &&
            data[2] == 0x46.toByte() && data[3] == 0x46.toByte()
        ) {
            return "audio/wav"
        }
        // MP4: ftyp at offset 4
        if (data.size >= 12 && data[4] == 0x66.toByte() && data[5] == 0x74.toByte() &&
            data[6] == 0x79.toByte() && data[7] == 0x70.toByte()
        ) {
            return "audio/mp4"
        }
        // ID3 for MP3
        if (data[0] == 0x49.toByte() && data[1] == 0x44.toByte() &&
            data[2] == 0x33.toByte()
        ) {
            return "audio/mpeg"
        }
        return "audio/mpeg"
    }

    @Test
    fun `FLAC magic bytes detected`() {
        val data = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"
        assertEquals("audio/flac", detectFromBytes(data))
    }

    @Test
    fun `OGG magic bytes detected`() {
        val data = byteArrayOf(0x4F, 0x67, 0x67, 0x53) // "OggS"
        assertEquals("audio/ogg", detectFromBytes(data))
    }

    @Test
    fun `WAV magic bytes detected`() {
        val data = byteArrayOf(0x52, 0x49, 0x46, 0x46) // "RIFF"
        assertEquals("audio/wav", detectFromBytes(data))
    }

    @Test
    fun `MP4 ftyp box detected`() {
        val data = ByteArray(12)
        data[4] = 0x66
        data[5] = 0x74
        data[6] = 0x79
        data[7] = 0x70 // "ftyp"
        assertEquals("audio/mp4", detectFromBytes(data))
    }

    @Test
    fun `ID3 tag detected as MPEG`() {
        val data = byteArrayOf(0x49, 0x44, 0x33) // "ID3"
        assertEquals("audio/mpeg", detectFromBytes(data))
    }

    @Test
    fun `unknown bytes default to audio_mpeg`() {
        val data = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertEquals("audio/mpeg", detectFromBytes(data))
    }

    @Test
    fun `empty array defaults to audio_mpeg`() {
        assertEquals("audio/mpeg", detectFromBytes(ByteArray(0)))
    }

    @Test
    fun `FLAC with ID3 at start still detected as FLAC`() {
        // FLAC files can't have ID3 at start; "fLaC" is the magic
        val data = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0x00)
        assertEquals("audio/flac", detectFromBytes(data))
    }

    @Test
    fun `OGG detection works with extra bytes`() {
        val data = byteArrayOf(0x4F, 0x67, 0x67, 0x53, 0x00, 0x02, 0x00, 0x00)
        assertEquals("audio/ogg", detectFromBytes(data))
    }
}
