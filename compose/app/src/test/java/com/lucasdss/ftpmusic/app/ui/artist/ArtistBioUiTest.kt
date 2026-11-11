package com.lucasdss.ftpmusic.app.ui.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistBioUiTest {
    @Test
    fun `teaser max lines stays compact never Int MAX_VALUE`() {
        assertEquals(3, ArtistBioUi.TEASER_MAX_LINES)
        assertTrue(ArtistBioUi.TEASER_MAX_LINES < 10)
        assertTrue(ArtistBioUi.TEASER_MAX_LINES != Int.MAX_VALUE)
    }

    @Test
    fun `read more hidden for short bio`() {
        assertFalse(ArtistBioUi.shouldShowReadMore("Short bio."))
        assertFalse(ArtistBioUi.shouldShowReadMore("x".repeat(ArtistBioUi.READ_MORE_LENGTH_THRESHOLD)))
    }

    @Test
    fun `read more shown when longer than threshold`() {
        assertTrue(
            ArtistBioUi.shouldShowReadMore(
                "x".repeat(ArtistBioUi.READ_MORE_LENGTH_THRESHOLD + 1),
            ),
        )
    }
}
