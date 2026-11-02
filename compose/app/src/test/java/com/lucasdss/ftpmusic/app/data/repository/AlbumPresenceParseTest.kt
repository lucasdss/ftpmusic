package com.lucasdss.ftpmusic.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * N11-3: only an explicit Subsonic `status=failed, error.code=70` may purge an
 * album. Everything else — auth failures, other codes, a malformed body, a
 * numeric code sent as a string — is UNKNOWN and must be a no-op.
 */
class AlbumPresenceParseTest {

    private fun response(inner: Map<String, Any?>): Map<String, Any> = mapOf("subsonic-response" to inner)

    @Test
    fun `ok with an album is present`() {
        assertEquals(
            AlbumPresence.PRESENT,
            parseAlbumPresence(
                response(mapOf("status" to "ok", "album" to mapOf("id" to "a"))),
            ),
        )
    }

    @Test
    fun `ok without an album is unknown`() {
        assertEquals(
            AlbumPresence.UNKNOWN,
            parseAlbumPresence(response(mapOf("status" to "ok"))),
        )
    }

    @Test
    fun `failed with code 70 is absent`() {
        assertEquals(
            AlbumPresence.ABSENT,
            parseAlbumPresence(
                response(
                    mapOf(
                        "status" to "failed",
                        "error" to mapOf("code" to 70, "message" to "not found"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `failed with any other code is unknown`() {
        listOf(0, 10, 40, 41, 50, 70_000).forEach { code ->
            assertEquals(
                "code=$code",
                AlbumPresence.UNKNOWN,
                parseAlbumPresence(
                    response(mapOf("status" to "failed", "error" to mapOf("code" to code))),
                ),
            )
        }
    }

    @Test
    fun `failed without an error object is unknown`() {
        assertEquals(
            AlbumPresence.UNKNOWN,
            parseAlbumPresence(response(mapOf("status" to "failed"))),
        )
    }

    @Test
    fun `a string code is not trusted`() {
        assertEquals(
            AlbumPresence.UNKNOWN,
            parseAlbumPresence(
                response(mapOf("status" to "failed", "error" to mapOf("code" to "70"))),
            ),
        )
    }

    @Test
    fun `missing or unknown status is unknown`() {
        assertEquals(AlbumPresence.UNKNOWN, parseAlbumPresence(emptyMap()))
        assertEquals(AlbumPresence.UNKNOWN, parseAlbumPresence(response(emptyMap())))
        assertEquals(
            AlbumPresence.UNKNOWN,
            parseAlbumPresence(response(mapOf("status" to "weird"))),
        )
    }

    @Test
    fun `non-map subsonic response is unknown`() {
        assertEquals(
            AlbumPresence.UNKNOWN,
            parseAlbumPresence(mapOf("subsonic-response" to "oops")),
        )
    }
}
