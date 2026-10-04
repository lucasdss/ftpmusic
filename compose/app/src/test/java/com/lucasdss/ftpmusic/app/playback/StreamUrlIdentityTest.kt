package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamUrlIdentityTest {

    @Test
    fun `streamUrlTrackId reads id query param`() {
        assertEquals(
            "track-42",
            streamUrlTrackId("https://server/rest/stream?id=track-42&u=user&t=abc"),
        )
    }

    @Test
    fun `streamUrlTrackId returns null when id absent`() {
        assertNull(streamUrlTrackId("https://server/radio/stream.mp3"))
    }

    @Test
    fun `alignStreamUrlToTrackId is no-op when ids match`() {
        val url = "https://server/rest/stream?id=animal-id&u=user"
        assertEquals(url, alignStreamUrlToTrackId("animal-id", url))
    }

    @Test
    fun `alignStreamUrlToTrackId rewrites mismatched TAKE IT id under ANIMAL mediaId`() {
        val url = "https://server/rest/stream?id=take-it-id&u=user&t=tok"
        assertEquals(
            "https://server/rest/stream?id=animal-id&u=user&t=tok",
            alignStreamUrlToTrackId("animal-id", url),
        )
    }

    @Test
    fun `alignStreamUrlToTrackId leaves urls without id untouched`() {
        val url = "http://local-audio"
        assertEquals(url, alignStreamUrlToTrackId("animal-id", url))
    }
}
