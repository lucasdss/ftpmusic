package com.lucasdss.ftpmusic.app.data.cache

/**
 * Minimal ID3-tagged payload that passes [AudioCacheValidation.looksLikeAudio].
 * Pads to at least [AudioCacheValidation.MIN_CACHED_AUDIO_BYTES].
 */
fun fakeAudioBytes(
    size: Int = AudioCacheValidation.MIN_CACHED_AUDIO_BYTES.toInt(),
    fill: Byte = 0x7F,
): ByteArray {
    val n = size.coerceAtLeast(AudioCacheValidation.MIN_CACHED_AUDIO_BYTES.toInt())
    val bytes = ByteArray(n) { fill }
    bytes[0] = 'I'.code.toByte()
    bytes[1] = 'D'.code.toByte()
    bytes[2] = '3'.code.toByte()
    bytes[3] = 0x03
    return bytes
}

/** Subsonic-style JSON error body (~182B) observed in device diagnostics. */
fun subsonicErrorJsonBytes(): ByteArray {
    val json =
        "{" +
            "\"subsonic-response\":{" +
            "\"status\":\"failed\",\"version\":\"1.16.1\",\"type\":\"navidrome\"," +
            "\"error\":{\"code\":70,\"message\":\"Song not found\"}" +
            "}" +
            "}"
    return json.toByteArray()
}
