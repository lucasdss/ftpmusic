package com.lucasdss.ftpmusic.app.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests MimeTypeResolver: suffix mapping, contentType priority,
 * and null/blank/unknown input handling.
 */
class MimeTypeResolverTest {

    // ── Known suffix mappings ────────────────────────────────────────────

    @Test
    fun `mp3 suffix maps to audio_mpeg`() {
        assertEquals("audio/mpeg", MimeTypeResolver.resolve(null, "mp3"))
    }

    @Test
    fun `ogg suffix maps to audio_ogg`() {
        assertEquals("audio/ogg", MimeTypeResolver.resolve(null, "ogg"))
    }

    @Test
    fun `opus suffix maps to audio_ogg with opus codecs`() {
        assertEquals("audio/ogg;codecs=opus", MimeTypeResolver.resolve(null, "opus"))
    }

    @Test
    fun `flac suffix maps to audio_flac`() {
        assertEquals("audio/flac", MimeTypeResolver.resolve(null, "flac"))
    }

    @Test
    fun `aac suffix maps to audio_aac`() {
        assertEquals("audio/aac", MimeTypeResolver.resolve(null, "aac"))
    }

    @Test
    fun `m4a suffix maps to audio_mp4`() {
        assertEquals("audio/mp4", MimeTypeResolver.resolve(null, "m4a"))
    }

    @Test
    fun `mp4 suffix maps to audio_mp4`() {
        assertEquals("audio/mp4", MimeTypeResolver.resolve(null, "mp4"))
    }

    @Test
    fun `wav suffix maps to audio_wav`() {
        assertEquals("audio/wav", MimeTypeResolver.resolve(null, "wav"))
    }

    @Test
    fun `wma suffix maps to audio_x_ms_wma`() {
        assertEquals("audio/x-ms-wma", MimeTypeResolver.resolve(null, "wma"))
    }

    @Test
    fun `aiff suffix maps to audio_aiff`() {
        assertEquals("audio/aiff", MimeTypeResolver.resolve(null, "aiff"))
    }

    @Test
    fun `aif suffix maps to audio_aiff`() {
        assertEquals("audio/aiff", MimeTypeResolver.resolve(null, "aif"))
    }

    @Test
    fun `webm suffix maps to audio_webm`() {
        assertEquals("audio/webm", MimeTypeResolver.resolve(null, "webm"))
    }

    @Test
    fun `dsf suffix maps to audio_dsf`() {
        assertEquals("audio/dsf", MimeTypeResolver.resolve(null, "dsf"))
    }

    @Test
    fun `dff suffix maps to audio_dff`() {
        assertEquals("audio/dff", MimeTypeResolver.resolve(null, "dff"))
    }

    @Test
    fun `wv suffix maps to audio_wavpack`() {
        assertEquals("audio/wavpack", MimeTypeResolver.resolve(null, "wv"))
    }

    // ── Case insensitivity ───────────────────────────────────────────────

    @Test
    fun `suffix is case insensitive`() {
        assertEquals("audio/mpeg", MimeTypeResolver.resolve(null, "MP3"))
        assertEquals("audio/flac", MimeTypeResolver.resolve(null, "FLAC"))
        assertEquals("audio/ogg", MimeTypeResolver.resolve(null, "OGG"))
    }

    @Test
    fun `suffix with leading dot is handled`() {
        assertEquals("audio/mpeg", MimeTypeResolver.resolve(null, ".mp3"))
        assertEquals("audio/flac", MimeTypeResolver.resolve(null, ".flac"))
    }

    @Test
    fun `suffix with whitespace is trimmed`() {
        assertEquals("audio/mpeg", MimeTypeResolver.resolve(null, "  mp3  "))
        assertEquals("audio/flac", MimeTypeResolver.resolve(null, " flac "))
    }

    // ── ContentType takes priority over suffix ───────────────────────────

    @Test
    fun `contentType takes priority over suffix`() {
        // Even though suffix is "mp3", contentType should win
        assertEquals("audio/flac", MimeTypeResolver.resolve("audio/flac", "mp3"))
    }

    @Test
    fun `contentType with opus codecs takes priority`() {
        assertEquals("audio/ogg;codecs=opus", MimeTypeResolver.resolve("audio/ogg;codecs=opus", "flac"))
    }

    @Test
    fun `contentType is trimmed`() {
        assertEquals("audio/mpeg", MimeTypeResolver.resolve("  audio/mpeg  ", "flac"))
    }

    @Test
    fun `contentType wins even if suffix is null`() {
        assertEquals("audio/aac", MimeTypeResolver.resolve("audio/aac", null))
    }

    // ── Null / blank / unknown inputs ────────────────────────────────────

    @Test
    fun `both null returns null`() {
        assertNull(MimeTypeResolver.resolve(null, null))
    }

    @Test
    fun `blank contentType and null suffix returns null`() {
        assertNull(MimeTypeResolver.resolve("", null))
        assertNull(MimeTypeResolver.resolve("   ", null))
    }

    @Test
    fun `unknown suffix returns null`() {
        assertNull(MimeTypeResolver.resolve(null, "xyz"))
        assertNull(MimeTypeResolver.resolve(null, "unknown"))
    }

    @Test
    fun `blank suffix returns null when no contentType`() {
        assertNull(MimeTypeResolver.resolve(null, ""))
        assertNull(MimeTypeResolver.resolve(null, "   "))
    }

    @Test
    fun `null contentType with blank suffix returns null`() {
        assertNull(MimeTypeResolver.resolve(null, ""))
    }

    // ── Edge cases ───────────────────────────────────────────────────────

    @Test
    fun `all known suffixes resolve correctly`() {
        val expected = mapOf(
            "mp3" to "audio/mpeg",
            "ogg" to "audio/ogg",
            "opus" to "audio/ogg;codecs=opus",
            "flac" to "audio/flac",
            "aac" to "audio/aac",
            "m4a" to "audio/mp4",
            "mp4" to "audio/mp4",
            "wav" to "audio/wav",
            "wma" to "audio/x-ms-wma",
            "aiff" to "audio/aiff",
            "aif" to "audio/aiff",
            "webm" to "audio/webm",
            "dsf" to "audio/dsf",
            "dff" to "audio/dff",
            "wv" to "audio/wavpack",
        )
        expected.forEach { (suffix, mimeType) ->
            assertEquals(
                "Suffix '$suffix' should map to '$mimeType'",
                mimeType,
                MimeTypeResolver.resolve(null, suffix),
            )
        }
    }

    @Test
    fun `resolve never returns blank string`() {
        // White-box: verify the resolver never returns empty/blank
        val results = listOf(
            MimeTypeResolver.resolve(null, null),
            MimeTypeResolver.resolve(null, ""),
            MimeTypeResolver.resolve(null, "   "),
            MimeTypeResolver.resolve(null, "unknown"),
            MimeTypeResolver.resolve("", null),
            MimeTypeResolver.resolve("   ", null),
            MimeTypeResolver.resolve("", "unknown"),
        )
        results.forEach { result ->
            if (result != null) {
                assertTrue("Result should not be blank: '$result'", result.isNotBlank())
            }
        }
    }
}
