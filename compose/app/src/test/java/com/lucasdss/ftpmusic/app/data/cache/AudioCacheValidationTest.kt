package com.lucasdss.ftpmusic.app.data.cache

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioCacheValidationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun file(bytes: ByteArray): File {
        val f = tempFolder.newFile()
        f.writeBytes(bytes)
        return f
    }

    private fun padded(magic: ByteArray, size: Int = AudioCacheValidation.MIN_CACHED_AUDIO_BYTES.toInt()): ByteArray {
        val out = ByteArray(size)
        magic.copyInto(out)
        return out
    }

    @Test
    fun `rejects tiny subsonic json error bodies`() {
        val poison = subsonicErrorJsonBytes()
        assertTrue(poison.size < AudioCacheValidation.MIN_CACHED_AUDIO_BYTES)
        assertTrue(AudioCacheValidation.looksLikeSubsonicError(poison))
        assertFalse(AudioCacheValidation.looksLikeAudio(file(poison)))
    }

    @Test
    fun `rejects subsonic xml error even when padded past min size`() {
        val xml =
            (
                """<?xml version="1.0"?>""" +
                    """<subsonic-response status="failed" version="1.16.1">""" +
                    """<error code="70" message="x"/></subsonic-response>"""
                ).toByteArray()
        val padded = xml + ByteArray(5000) { 0 }
        assertTrue(AudioCacheValidation.looksLikeSubsonicError(padded.copyOf(512)))
        assertFalse(AudioCacheValidation.looksLikeAudioBytes(padded, padded.size.toLong()))
    }

    @Test
    fun `rejects json failed status without needing xml`() {
        val json = """{"status":"failed","error":{"code":40}}""".toByteArray()
        assertTrue(AudioCacheValidation.looksLikeSubsonicError(json))
        assertFalse(
            AudioCacheValidation.looksLikeAudioBytes(
                padded(json),
                AudioCacheValidation.MIN_CACHED_AUDIO_BYTES,
            ),
        )
    }

    @Test
    fun `rejects below min size even with mp3 frame sync`() {
        val tiny = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
        assertFalse(AudioCacheValidation.looksLikeAudioBytes(tiny, tiny.size.toLong()))
    }

    @Test
    fun `rejects missing empty and short headers`() {
        assertFalse(AudioCacheValidation.looksLikeAudio(File(tempFolder.root, "missing.bin")))
        assertFalse(AudioCacheValidation.looksLikeAudio(file(ByteArray(0))))
        assertFalse(AudioCacheValidation.looksLikeAudioBytes(byteArrayOf(1, 2, 3), 5000))
        assertFalse(AudioCacheValidation.looksLikeAudio(file(ByteArray(100) { 0 })))
    }

    @Test
    fun `rejects random padded payload without magic`() {
        assertFalse(AudioCacheValidation.looksLikeAudio(file(ByteArray(5000) { 0x11 })))
    }

    @Test
    fun `accepts id3 mpeg flac ogg wave and ftyp magic`() {
        assertTrue(AudioCacheValidation.looksLikeAudio(file(fakeAudioBytes())))
        assertTrue(
            AudioCacheValidation.looksLikeAudio(
                file(padded(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00))),
            ),
        )
        assertTrue(
            AudioCacheValidation.looksLikeAudio(
                file(
                    padded(
                        byteArrayOf(
                            'f'.code.toByte(),
                            'L'.code.toByte(),
                            'a'.code.toByte(),
                            'C'.code.toByte(),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(
            AudioCacheValidation.looksLikeAudio(
                file(
                    padded(
                        byteArrayOf(
                            'O'.code.toByte(),
                            'g'.code.toByte(),
                            'g'.code.toByte(),
                            'S'.code.toByte(),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(
            AudioCacheValidation.looksLikeAudio(
                file(
                    padded(
                        byteArrayOf(
                            'R'.code.toByte(),
                            'I'.code.toByte(),
                            'F'.code.toByte(),
                            'F'.code.toByte(),
                            0, 0, 0, 0,
                            'W'.code.toByte(),
                            'A'.code.toByte(),
                            'V'.code.toByte(),
                            'E'.code.toByte(),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(
            AudioCacheValidation.looksLikeAudio(
                file(
                    padded(
                        byteArrayOf(
                            0, 0, 0, 0,
                            'f'.code.toByte(),
                            't'.code.toByte(),
                            'y'.code.toByte(),
                            'p'.code.toByte(),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `riff without wave is not audio`() {
        val riffOnly = padded(
            byteArrayOf(
                'R'.code.toByte(),
                'I'.code.toByte(),
                'F'.code.toByte(),
                'F'.code.toByte(),
                0, 0, 0, 0,
                'A'.code.toByte(),
                'V'.code.toByte(),
                'I'.code.toByte(),
                ' '.code.toByte(),
            ),
        )
        assertFalse(AudioCacheValidation.looksLikeAudioBytes(riffOnly, riffOnly.size.toLong()))
    }

    @Test
    fun `content type gate rejects json and xml accepts audio and octet-stream`() {
        assertFalse(AudioCacheValidation.isAcceptableStreamContentType("application/json"))
        assertFalse(AudioCacheValidation.isAcceptableStreamContentType("text/xml; charset=utf-8"))
        assertFalse(AudioCacheValidation.isAcceptableStreamContentType("text/html"))
        assertTrue(AudioCacheValidation.isAcceptableStreamContentType("audio/mpeg"))
        assertTrue(AudioCacheValidation.isAcceptableStreamContentType("audio/flac; codecs=flac"))
        assertTrue(AudioCacheValidation.isAcceptableStreamContentType("application/octet-stream"))
        assertTrue(AudioCacheValidation.isAcceptableStreamContentType("binary/octet-stream"))
        assertTrue(AudioCacheValidation.isAcceptableStreamContentType(null))
        assertTrue(AudioCacheValidation.isAcceptableStreamContentType(""))
        assertTrue(AudioCacheValidation.isAcceptableStreamContentType("application/ogg"))
        assertTrue(AudioCacheValidation.isAcceptableStreamContentType("   "))
    }

    @Test
    fun `subsonic error sniff ignores empty and non matching ascii`() {
        assertFalse(AudioCacheValidation.looksLikeSubsonicError(ByteArray(0)))
        assertFalse(AudioCacheValidation.looksLikeSubsonicError("hello world".toByteArray()))
        assertFalse(AudioCacheValidation.looksLikeSubsonicError("""{"status":"ok"}""".toByteArray()))
        assertTrue(AudioCacheValidation.looksLikeSubsonicError("""{"subsonic-response":{}}""".toByteArray()))
        assertTrue(
            AudioCacheValidation.looksLikeSubsonicError(
                """  <?xml version="1.0"?><root xmlns="subsonic"></root>""".toByteArray(),
            ),
        )
        assertTrue(
            AudioCacheValidation.looksLikeSubsonicError(
                """{"status":"ok","error":{"code":0}}""".toByteArray(),
            ),
        )
    }

    @Test
    fun `looksLikeAudio false when path is a directory`() {
        assertFalse(AudioCacheValidation.looksLikeAudio(tempFolder.root))
    }

    @Test
    fun `mpeg sync requires 0xE0 mask on second byte`() {
        val bad = padded(byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00))
        assertFalse(AudioCacheValidation.hasAudioMagic(bad))
        val good = padded(byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0x00, 0x00))
        assertTrue(AudioCacheValidation.hasAudioMagic(good))
    }
}
