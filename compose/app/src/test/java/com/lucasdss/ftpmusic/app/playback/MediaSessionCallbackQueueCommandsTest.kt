package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.media3.common.util.UnstableApi
class MediaSessionCallbackQueueCommandsTest {

    @Before
    fun setup() {
        SubsonicCredentials.username = "user"
        SubsonicCredentials.password = "pass"
        DynamicBaseUrl.url = "https://music.example.com"
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns (firstArg() as String)
            uri
        }
        every { Uri.encode(any()) } answers { firstArg() }
        every { Uri.decode(any()) } answers { firstArg() }
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
        DynamicBaseUrl.url = ""
    }

    private fun callback(trackDao: TrackDao, playbackManager: PlaybackManager): MediaSessionCallback =
        MediaSessionCallback(
            trackDao = trackDao,
            metadataDao = mockk<CachedMetadataDao>(relaxed = true),
            authHelper = SubsonicAuthHelper(),
            api = mockk<SubsonicApi>(relaxed = true),
            playlistDao = mockk(relaxed = true),
            playbackManager = playbackManager,
            scope = CoroutineScope(Dispatchers.Unconfined),
            mainDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun `onConnect advertises queue commands`() {
        val cb = callback(mockk(relaxed = true), mockk(relaxed = true))
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } returns null
        val session = mockk<MediaSession>(relaxed = true)
        every { session.player } returns player
        val result = cb.onConnect(session, mockk(relaxed = true))
        assertTrue(
            result.availableSessionCommands.contains(MediaSessionQueueCommands.PLAY_NEXT_COMMAND),
        )
        assertTrue(
            result.availableSessionCommands.contains(MediaSessionQueueCommands.ADD_TO_QUEUE_COMMAND),
        )
    }

    @Test
    fun `play next calls PlaybackManager and starts when empty`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "Song")
        val pm = mockk<PlaybackManager>(relaxed = true)
        val cb = callback(trackDao, pm)
        val player = mockk<Player>(relaxed = true)
        every { player.mediaItemCount } returnsMany listOf(0, 1)
        every { player.currentMediaItem } returns null
        val session = mockk<MediaSession>(relaxed = true)
        every { session.player } returns player
        val args = Bundle().apply { putString(MediaConstants.EXTRA_KEY_MEDIA_ID, "t1") }

        val result = cb.onCustomCommand(
            session,
            mockk(relaxed = true),
            MediaSessionQueueCommands.PLAY_NEXT_COMMAND,
            args,
        ).get()
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
        verify {
            pm.playNext(
                match<Track> { it.id == "t1" },
                any(),
            )
        }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `add to queue rejects radio media id`() {
        val pm = mockk<PlaybackManager>(relaxed = true)
        val cb = callback(mockk(relaxed = true), pm)
        val player = mockk<Player>(relaxed = true)
        val session = mockk<MediaSession>(relaxed = true)
        every { session.player } returns player
        val args = Bundle().apply {
            putString(MediaConstants.EXTRA_KEY_MEDIA_ID, AutoBrowseIds.radio("s1"))
        }
        val result = cb.onCustomCommand(
            session,
            mockk(relaxed = true),
            MediaSessionQueueCommands.ADD_TO_QUEUE_COMMAND,
            args,
        ).get()
        assertEquals(SessionResult.RESULT_ERROR_NOT_SUPPORTED, result.resultCode)
        verify(exactly = 0) { pm.addToQueue(any(), any()) }
    }

    @Test
    fun `missing room track returns bad value`() {
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("missing") } returns null
        val pm = mockk<PlaybackManager>(relaxed = true)
        val cb = callback(trackDao, pm)
        val session = mockk<MediaSession>(relaxed = true)
        every { session.player } returns mockk(relaxed = true)
        val args = Bundle().apply { putString(MediaConstants.EXTRA_KEY_MEDIA_ID, "missing") }
        val result = cb.onCustomCommand(
            session,
            mockk(relaxed = true),
            MediaSessionQueueCommands.PLAY_NEXT_COMMAND,
            args,
        ).get()
        assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, result.resultCode)
    }

    @Test
    fun `onSetMediaItems queue track returns dual timeline at entry`() {
        val pm = mockk<PlaybackManager>(relaxed = true)
        val merged = listOf(
            MediaItem.Builder().setMediaId("a").build().withQueueEntryId(10),
            MediaItem.Builder().setMediaId("b").build().withQueueEntryId(11),
        )
        every { pm.mergedMediaItems() } returns merged
        every { pm.indexOfQueueEntry(11) } returns 1
        val cb = callback(mockk(relaxed = true), pm)
        val session = mockk<MediaSession>(relaxed = true)
        val result = cb.onSetMediaItems(
            session,
            mockk(relaxed = true),
            listOf(MediaItem.Builder().setMediaId(AutoBrowseIds.queueTrack(11)).build()),
            0,
            0L,
        ).get()
        assertEquals(1, result.startIndex)
        assertEquals(listOf("a", "b"), result.mediaItems.map { it.mediaId })
    }

    @Test
    fun `onSetMediaItems stale queue track fails without empty timeline`() {
        val pm = mockk<PlaybackManager>(relaxed = true)
        every { pm.indexOfQueueEntry(99) } returns -1
        val cb = callback(mockk(relaxed = true), pm)
        val session = mockk<MediaSession>(relaxed = true)
        try {
            cb.onSetMediaItems(
                session,
                mockk(relaxed = true),
                listOf(MediaItem.Builder().setMediaId(AutoBrowseIds.queueTrack(99)).build()),
                0,
                0L,
            ).get()
            org.junit.Assert.fail("expected failure for stale queue entry")
        } catch (_: Exception) {
            // Failed future — Media3 must not setMediaItems([]).
        }
        verify(exactly = 0) { pm.playQueueItem(any()) }
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
