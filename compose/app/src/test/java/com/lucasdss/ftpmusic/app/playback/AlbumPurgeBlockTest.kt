package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumPurgeBlockTest {

    @Test
    fun `a clean album may be purged`() {
        assertEquals(
            AlbumPurgeBlock.NONE,
            albumPurgeBlock(
                downloadedTracks = 0,
                starred = false,
                disliked = false,
                pendingUnstar = false,
            ),
        )
    }

    @Test
    fun `any pinned download blocks the purge`() {
        assertEquals(
            AlbumPurgeBlock.DOWNLOADED,
            albumPurgeBlock(
                downloadedTracks = 1,
                starred = false,
                disliked = false,
                pendingUnstar = false,
            ),
        )
    }

    @Test
    fun `download wins over user intent in the reason`() {
        assertEquals(
            AlbumPurgeBlock.DOWNLOADED,
            albumPurgeBlock(
                downloadedTracks = 2,
                starred = true,
                disliked = true,
                pendingUnstar = true,
            ),
        )
    }

    @Test
    fun `star disliked and pending unstar each block the purge`() {
        assertEquals(
            AlbumPurgeBlock.USER_INTENT,
            albumPurgeBlock(0, starred = true, disliked = false, pendingUnstar = false),
        )
        assertEquals(
            AlbumPurgeBlock.USER_INTENT,
            albumPurgeBlock(0, starred = false, disliked = true, pendingUnstar = false),
        )
        assertEquals(
            AlbumPurgeBlock.USER_INTENT,
            albumPurgeBlock(0, starred = false, disliked = false, pendingUnstar = true),
        )
    }
}
