package com.lucasdss.ftpmusic.app.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.ThumbRating
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.media3.common.util.UnstableApi
class MediaSessionCallbackReactionTest {

    private fun callback(reaction: CurrentTrackReactionController): MediaSessionCallback = MediaSessionCallback(
        trackDao = mockk(relaxed = true),
        metadataDao = mockk<CachedMetadataDao>(relaxed = true),
        authHelper = SubsonicAuthHelper(),
        api = mockk<SubsonicApi>(relaxed = true),
        reactionController = reaction,
        scope = CoroutineScope(Dispatchers.Unconfined),
        mainDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `onCustomCommand like toggles via controller`() {
        val fav = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "T", starredAt = null)
        val reaction = CurrentTrackReactionController(fav, trackDao)
        val cb = callback(reaction)

        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } returns MediaItem.Builder().setMediaId("t1").build()
        every { player.currentMediaItemIndex } returns 0
        val session = mockk<MediaSession>(relaxed = true)
        every { session.player } returns player

        val result = cb.onCustomCommand(
            session,
            mockk(relaxed = true),
            MediaSessionReactionCommands.LIKE_COMMAND,
            Bundle.EMPTY,
        ).get()
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
        coVerify { fav.likeTrack("t1") }
        assertEquals(true, reaction.reactions.value?.isStarred)
        // Surface refresh is owned by MediaService.wireReactionSurfaceUpdates.
        verify(exactly = 0) { session.setMediaButtonPreferences(any()) }
    }

    @Test
    fun `onCustomCommand with no media id returns invalid state`() {
        val reaction = CurrentTrackReactionController(mockk(relaxed = true), mockk(relaxed = true))
        val cb = callback(reaction)
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } returns null
        val session = mockk<MediaSession>(relaxed = true)
        every { session.player } returns player

        val result = cb.onCustomCommand(
            session,
            mockk(relaxed = true),
            MediaSessionReactionCommands.DISLIKE_COMMAND,
            Bundle.EMPTY,
        ).get()
        assertEquals(SessionResult.RESULT_ERROR_INVALID_STATE, result.resultCode)
    }

    @Test
    fun `onSetRating thumbs up likes track`() {
        val fav = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "T", starredAt = null)
        val reaction = CurrentTrackReactionController(fav, trackDao)
        val cb = callback(reaction)
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } returns MediaItem.Builder().setMediaId("t1").build()
        every { player.currentMediaItemIndex } returns 0
        val session = mockk<MediaSession>(relaxed = true)
        every { session.player } returns player

        val result = cb.onSetRating(session, mockk(relaxed = true), ThumbRating(true)).get()
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
        coVerify { fav.likeTrack("t1") }
    }
}
