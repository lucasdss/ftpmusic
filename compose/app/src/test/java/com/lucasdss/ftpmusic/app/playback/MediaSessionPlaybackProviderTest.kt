package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MediaSessionPlaybackProviderTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var provider: MediaSessionPlaybackProvider

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        provider =
            MediaSessionPlaybackProvider(
                com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager(io.mockk.mockk(relaxed = true)),
            )
    }

    @After
    fun teardown() {
        PlayerHolder.player = null
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        PlayerHolder.castVolume = 0f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.pendingCastVolumeTimestamp = 0L
        PlayerHolder.castDeviceMuted = false
        provider.castStateSource = null
        provider.disconnect()
        Dispatchers.resetMain()
    }

    // -- fromPlayer mapping --

    @Test
    fun `fromPlayer reads player metadata`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        val mediaItem = MediaItem.Builder()
            .setMediaId("t1")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Test")
                    .setArtist("Artist")
                    .setAlbumTitle("Album")
                    .build(),
            )
            .build()
        every { mockPlayer.currentMediaItem } returns mediaItem
        every { mockPlayer.currentMediaItemIndex } returns 2
        every { mockPlayer.mediaItemCount } returns 3
        every { mockPlayer.isPlaying } returns true
        every { mockPlayer.currentPosition } returns 5000L
        every { mockPlayer.duration } returns 300000L
        every { mockPlayer.repeatMode } returns Player.REPEAT_MODE_ALL
        every { mockPlayer.shuffleModeEnabled } returns true
        every { mockPlayer.volume } returns 0.8f
        every { mockPlayer.playbackParameters } returns PlaybackParameters(1.25f)

        val state = PlaybackState.fromPlayer(mockPlayer)
        assertEquals("Test", state.title)
        assertEquals("Artist", state.artist)
        assertEquals("Album", state.album)
        assertNull(state.coverArtId)
        assertEquals(true, state.isPlaying)
        assertEquals(5000L, state.position)
        assertEquals(300000L, state.duration)
        assertEquals(Player.REPEAT_MODE_ALL, state.repeatMode)
        assertEquals(true, state.shuffleModeEnabled)
        assertEquals(0.8f, state.volume)
        assertEquals(1.25f, state.playbackSpeed)
        assertEquals("t1", state.currentTrackId)
        assertEquals(2, state.trackIndex)
        assertEquals(3, state.queueSize)
        assertNull(state.artistId)
        assertNull(state.albumId)
        assertEquals("music", state.mediaType)
    }

    @Test
    fun `fromPlayer reads isCasting and castDeviceName from PlayerHolder`() {
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "TV"
        try {
            val mockPlayer = mockk<Player>(relaxed = true)
            every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT

            val state = PlaybackState.fromPlayer(mockPlayer)
            assertEquals(true, state.isCasting)
            assertEquals("TV", state.castDeviceName)
        } finally {
            PlayerHolder.isCasting = false
            PlayerHolder.castDeviceName = null
        }
    }

    @Test
    fun `fromPlayer reads next track preview`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        val item1 = MediaItem.Builder()
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Track1")
                    .setArtist("Artist1")
                    .build(),
            )
            .build()
        val item2 = MediaItem.Builder()
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Track2")
                    .setArtist("Artist2")
                    .build(),
            )
            .build()
        every { mockPlayer.currentMediaItem } returns item1
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 2
        every { mockPlayer.getMediaItemAt(1) } returns item2
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT

        val state = PlaybackState.fromPlayer(mockPlayer)
        assertEquals("Track2", state.nextTrackTitle)
        assertEquals("Artist2", state.nextTrackArtist)
    }

    @Test
    fun `fromPlayer handles null currentMediaItem`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT

        val state = PlaybackState.fromPlayer(mockPlayer)
        assertNull(state.title)
        assertNull(state.artist)
    }

    @Test
    fun `fromPlayer falls back to prevState for non-Player fields`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT

        val prevState = PlaybackState(
            sleepTimerEndMs = 5000L,
            isStarred = true,
            isOffline = true,
            downloadedTrackIds = setOf("t1"),
        )
        val state = PlaybackState.fromPlayer(mockPlayer, prevState)
        assertEquals(5000L, state.sleepTimerEndMs)
        assertEquals(true, state.isStarred)
        assertEquals(true, state.isOffline)
        assertEquals(setOf("t1"), state.downloadedTrackIds)
    }

    @Test
    fun `fromPlayer maps extras albumId and artistId`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        val mediaItem = MediaItem.Builder()
            .setMediaId("t1")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Song")
                    .setArtist("Artist")
                    .build(),
            )
            .build()
        every { mockPlayer.currentMediaItem } returns mediaItem
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 1
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT

        val state = PlaybackState.fromPlayer(mockPlayer)
        assertNull(state.artistId)
        assertNull(state.albumId)
    }

    // -- Control delegation --

    /** The dispatch path requires PlayerHolder.player to be attached — while no
     *  player is wired a control is queued + a re-arm requested instead of being
     *  dispatched. Wire a relaxed player for the duration of [block] so these
     *  tests keep exercising the wired dispatch contract. */
    private fun <T> withWiredPlayer(block: () -> T): T {
        PlayerHolder.player = mockk<Player>(relaxed = true)
        try {
            return block()
        } finally {
            PlayerHolder.player = null
        }
    }

    @Test
    fun `playPause sends PLAY_PAUSE`() {
        val captured = mutableListOf<PlaybackControl>()
        provider.setControlCallback { captured.add(it) }

        withWiredPlayer { provider.playPause() }
        assertEquals(PlaybackControl.PLAY_PAUSE, captured.single())
    }

    @Test
    fun `skipNext sends SKIP_NEXT`() {
        val captured = mutableListOf<PlaybackControl>()
        provider.setControlCallback { captured.add(it) }

        withWiredPlayer { provider.skipNext() }
        assertEquals(PlaybackControl.SKIP_NEXT, captured.single())
    }

    @Test
    fun `skipPrev sends SKIP_PREV`() {
        val captured = mutableListOf<PlaybackControl>()
        provider.setControlCallback { captured.add(it) }

        withWiredPlayer { provider.skipPrev() }
        assertEquals(PlaybackControl.SKIP_PREV, captured.single())
    }

    @Test
    fun `seekTo sends SEEK_TO with correct fraction`() {
        val captured = mutableListOf<PlaybackControl>()
        provider.setControlCallback { captured.add(it) }

        withWiredPlayer { provider.seekTo(0.75f) }
        assertEquals(PlaybackControl.SEEK_TO(0.75f), captured.single())
    }

    @Test
    fun `toggleRepeat sends REPEAT_TOGGLE`() {
        val captured = mutableListOf<PlaybackControl>()
        provider.setControlCallback { captured.add(it) }

        withWiredPlayer { provider.toggleRepeat() }
        assertEquals(PlaybackControl.REPEAT_TOGGLE, captured.single())
    }

    @Test
    fun `toggleShuffle sends SHUFFLE_TOGGLE`() {
        val captured = mutableListOf<PlaybackControl>()
        provider.setControlCallback { captured.add(it) }

        withWiredPlayer { provider.toggleShuffle() }
        assertEquals(PlaybackControl.SHUFFLE_TOGGLE, captured.single())
    }

    @Test
    fun `toggleSpeed sends SPEED_TOGGLE`() {
        val captured = mutableListOf<PlaybackControl>()
        provider.setControlCallback { captured.add(it) }

        withWiredPlayer { provider.toggleSpeed() }
        assertEquals(PlaybackControl.SPEED_TOGGLE, captured.single())
    }

    @Test
    fun `setVolume sends SET_VOLUME with correct value`() {
        val captured = mutableListOf<PlaybackControl>()
        provider.setControlCallback { captured.add(it) }

        withWiredPlayer { provider.setVolume(0.5f) }
        assertEquals(PlaybackControl.SET_VOLUME(0.5f), captured.single())
    }

    // -- No callback set -- no crash --

    @Test
    fun `control methods do not crash when no callback is set`() {
        provider.playPause()
        provider.skipNext()
        provider.skipPrev()
        provider.seekTo(0.5f)
        provider.toggleRepeat()
        provider.toggleShuffle()
        provider.toggleSpeed()
        provider.setVolume(0.8f)
        // No assertion needed -- test passes if no exception thrown
    }

    // -- PlaybackControl equality --

    @Test
    fun `PlaybackControl data objects are equal`() {
        assertEquals(PlaybackControl.PLAY_PAUSE, PlaybackControl.PLAY_PAUSE)
        assertEquals(PlaybackControl.SKIP_NEXT, PlaybackControl.SKIP_NEXT)
        assertEquals(PlaybackControl.SKIP_PREV, PlaybackControl.SKIP_PREV)
        assertEquals(PlaybackControl.REPEAT_TOGGLE, PlaybackControl.REPEAT_TOGGLE)
        assertEquals(PlaybackControl.SHUFFLE_TOGGLE, PlaybackControl.SHUFFLE_TOGGLE)
        assertEquals(PlaybackControl.SPEED_TOGGLE, PlaybackControl.SPEED_TOGGLE)
    }

    @Test
    fun `PlaybackControl data classes are equal by value`() {
        assertEquals(PlaybackControl.SEEK_TO(0.5f), PlaybackControl.SEEK_TO(0.5f))
    }

    @Test
    fun `PlaybackControl data classes differ by value`() {
        assertNotEquals(PlaybackControl.SEEK_TO(0.3f), PlaybackControl.SEEK_TO(0.7f))
    }

    @Test
    fun `SET_VOLUME is equal by value`() {
        assertEquals(PlaybackControl.SET_VOLUME(0.5f), PlaybackControl.SET_VOLUME(0.5f))
    }

    // -- Position poller --

    // -- Timeline-driven refresh (addMediaItems path) --

    private fun connectAndCaptureListener(player: Player): Player.Listener {
        val slot = io.mockk.CapturingSlot<Player.Listener>()
        every { player.addListener(capture(slot)) } returns Unit
        PlayerHolder.player = player
        provider.connect()
        return slot.captured
    }

    @Test
    fun `onTimelineChanged refreshes queueSize after append`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.mediaItemCount } returns 12
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        val listener = connectAndCaptureListener(mockPlayer)

        listener.onTimelineChanged(mockk(relaxed = true), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)

        assertEquals("queueSize must reflect appended items", 12, provider.playbackState.value.queueSize)
        PlayerHolder.player = null
        provider.disconnect()
    }

    @Test
    fun `onTimelineChanged with null player is a no-op`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.mediaItemCount } returns 12
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        val listener = connectAndCaptureListener(mockPlayer)
        val before = provider.playbackState.value

        PlayerHolder.player = null
        listener.onTimelineChanged(mockk(relaxed = true), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)

        assertEquals("State must be untouched when player is null", before, provider.playbackState.value)
        provider.disconnect()
    }

    @Test
    fun `onTimelineChanged preserves non-player fields from previous state`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.mediaItemCount } returns 5
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        val listener = connectAndCaptureListener(mockPlayer)

        provider.updateExtraState(
            isStarred = true,
            sleepTimerEndMs = 7000L,
            downloadedTrackIds = setOf("t1", "t2"),
        )
        listener.onTimelineChanged(mockk(relaxed = true), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)

        val state = provider.playbackState.value
        assertEquals(5, state.queueSize)
        assertEquals(true, state.isStarred)
        assertEquals(7000L, state.sleepTimerEndMs)
        assertEquals(setOf("t1", "t2"), state.downloadedTrackIds)
        PlayerHolder.player = null
        provider.disconnect()
    }

    @Test
    fun `onTimelineChanged suppresses repeated events with unchanged queue size`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.mediaItemCount } returns 7
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        val listener = connectAndCaptureListener(mockPlayer)

        listener.onTimelineChanged(mockk(relaxed = true), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        val afterFirst = provider.playbackState.value

        // Track transition: same queue size, different current item — must NOT emit
        every { mockPlayer.currentMediaItem } returns mockk(relaxed = true)
        listener.onTimelineChanged(mockk(relaxed = true), Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)

        assertSame("Same-size timeline event must not re-emit", afterFirst, provider.playbackState.value)
        PlayerHolder.player = null
        provider.disconnect()
    }

    @Test
    fun `onTimelineChanged emits when queue size grows after append`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.mediaItemCount } returns 7
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        val listener = connectAndCaptureListener(mockPlayer)
        listener.onTimelineChanged(mockk(relaxed = true), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        assertEquals(7, provider.playbackState.value.queueSize)

        // addMediaItems: size grows 7 → 12
        every { mockPlayer.mediaItemCount } returns 12
        listener.onTimelineChanged(mockk(relaxed = true), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)

        assertEquals("Append must refresh queueSize", 12, provider.playbackState.value.queueSize)
        PlayerHolder.player = null
        provider.disconnect()
    }

    @Test
    fun `onTimelineChanged emits when queue size shrinks after removal`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.mediaItemCount } returns 10
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        val listener = connectAndCaptureListener(mockPlayer)
        listener.onTimelineChanged(mockk(relaxed = true), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        assertEquals(10, provider.playbackState.value.queueSize)

        every { mockPlayer.mediaItemCount } returns 4
        listener.onTimelineChanged(mockk(relaxed = true), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)

        assertEquals("Removal must refresh queueSize", 4, provider.playbackState.value.queueSize)
        PlayerHolder.player = null
        provider.disconnect()
    }

    // -- Cast receiver-state mirror --

    @Test
    fun `fromCastState maps receiver fields and local metadata`() {
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Soundbar"
        PlayerHolder.castVolume = 0.7f
        PlayerHolder.castDeviceMuted = false
        // NOTE: Bundle extras are no-ops under plain-JVM tests (isReturnDefaultValues),
        // so duration comes via MediaMetadata.durationMs here.
        val item = MediaItem.Builder()
            .setMediaId("t42")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Cast Song")
                    .setArtist("Cast Artist")
                    .setAlbumTitle("Cast Album")
                    .setDurationMs(250000L)
                    .build(),
            )
            .build()
        val next = MediaItem.Builder()
            .setMediaId("t43")
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle("Next").setArtist("NArtist").build(),
            )
            .build()
        val prev = PlaybackState(isStarred = true, sleepTimerEndMs = 7000L, trackRating = 3)

        val state = PlaybackState.fromCastState(
            item,
            next,
            prev,
            isPlaying = true,
            positionMs = 98765L,
            index = 3,
            queueSize = 5,
        )

        assertEquals("Cast Song", state.title)
        assertEquals("Cast Artist", state.artist)
        assertEquals("Cast Album", state.album)
        assertTrue(state.isPlaying)
        assertEquals(98765L, state.position)
        assertEquals(250000L, state.duration)
        assertEquals("t42", state.currentTrackId)
        assertEquals(3, state.trackIndex)
        assertEquals(5, state.queueSize)
        assertEquals("Next", state.nextTrackTitle)
        assertEquals("NArtist", state.nextTrackArtist)
        assertEquals("music", state.mediaType)
        assertTrue(state.isStarred)
        assertEquals(7000L, state.sleepTimerEndMs)
        assertEquals(3, state.trackRating)
        assertTrue(state.isCasting)
        assertEquals("Soundbar", state.castDeviceName)
        assertEquals(0.7f, state.volume, 0.001f)
    }

    @Test
    fun `cast state source drives state while casting`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.isPlaying } returns false
        every { mockPlayer.currentPosition } returns 20L
        every { mockPlayer.duration } returns 0L
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        provider.castStateSource = {
            PlaybackState(
                title = "Receiver Track",
                artist = "R",
                isPlaying = true,
                position = 555000L,
                duration = 600000L,
                isCasting = true,
            )
        }

        provider.connect()
        testDispatcher.scheduler.advanceTimeBy(300)

        val state = provider.playbackState.value
        assertEquals("Receiver Track", state.title)
        assertEquals(555000L, state.position)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `cast state source null falls back to player position patch`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.isPlaying } returns false
        every { mockPlayer.currentPosition } returns 20L
        every { mockPlayer.duration } returns 0L
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        provider.castStateSource = { null }

        provider.connect()
        testDispatcher.scheduler.advanceTimeBy(300)

        assertEquals(20L, provider.playbackState.value.position)
        assertNull(provider.playbackState.value.title)
    }

    // -- volume expiry / lifecycle branches --

    @Test
    fun `expireStalePendingVolume clears pending after timeout`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = 0.4f
        PlayerHolder.pendingCastVolumeTimestamp = System.currentTimeMillis() - 5000L

        provider.expireStalePendingVolume(mockPlayer)

        assertNull("Stale pending volume must be cleared", PlayerHolder.pendingCastVolume)
        PlayerHolder.pendingCastVolumeTimestamp = 0L
    }

    @Test
    fun `expireStalePendingVolume keeps fresh pending volume`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = 0.4f
        PlayerHolder.pendingCastVolumeTimestamp = System.currentTimeMillis()

        provider.expireStalePendingVolume(mockPlayer)

        assertEquals("Fresh pending volume must survive", 0.4f, PlayerHolder.pendingCastVolume!!, 0.001f)
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.pendingCastVolumeTimestamp = 0L
    }

    @Test
    fun `expireStalePendingVolume no-ops when nothing pending`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        PlayerHolder.player = mockPlayer
        PlayerHolder.pendingCastVolume = null

        provider.expireStalePendingVolume(mockPlayer)
        // no-op must not throw
    }

    @Test
    fun `connect is idempotent and onPlayerSwitched re-registers the listener`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        PlayerHolder.player = mockPlayer

        provider.onPlayerSwitched()
        provider.connect()
        provider.connect() // second connect must be a no-op
        // onPlayerSwitched while connected re-registers on the current player
        // (covers the removeListener-on-non-null branch)
        provider.onPlayerSwitched()

        val listenerSlot = io.mockk.CapturingSlot<Player.Listener>()
        every { mockPlayer.addListener(capture(listenerSlot)) } returns Unit
        PlayerHolder.player = mockPlayer
        provider.disconnect()
        provider.connect()
        assertNotNull("Listener must be registered on connect", listenerSlot.captured)
    }

    @Test
    fun `onPositionDiscontinuity emits fresh state when player attached`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 0
        every { mockPlayer.isPlaying } returns false
        every { mockPlayer.currentPosition } returns 0L
        every { mockPlayer.duration } returns 0L
        val listener = connectAndCaptureListener(mockPlayer)

        listener.onPositionDiscontinuity(
            mockk(relaxed = true),
            mockk(relaxed = true),
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
        )

        assertNull("Discontinuity must not crash with attached player", provider.playbackState.value.title)
        provider.disconnect()
    }

    @Test
    fun `onDeviceVolumeChanged keeps pending when confirmation differs`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        every { mockPlayer.volume } returns 1.0f
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 0
        every { mockPlayer.isPlaying } returns false
        every { mockPlayer.currentPosition } returns 0L
        every { mockPlayer.duration } returns 0L
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = 0.5f
        PlayerHolder.pendingCastVolumeTimestamp = System.currentTimeMillis()
        val listener = connectAndCaptureListener(mockPlayer)

        listener.onDeviceVolumeChanged(75, false) // 0.75 vs pending 0.5 → ±2% mismatch

        assertEquals("Mismatched ramp value must not clear pending", 0.5f, PlayerHolder.pendingCastVolume!!, 0.001f)
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.pendingCastVolumeTimestamp = 0L
        PlayerHolder.isCasting = false
        provider.disconnect()
    }

    @Test
    fun `onDeviceVolumeChanged no-ops when volume unchanged or player null`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        every { mockPlayer.volume } returns 1.0f
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 0
        every { mockPlayer.isPlaying } returns false
        every { mockPlayer.currentPosition } returns 0L
        every { mockPlayer.duration } returns 0L
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        PlayerHolder.castVolume = 0f // initial state falls back to castDeviceVolume = 0.5
        val listener = connectAndCaptureListener(mockPlayer)
        val before = provider.playbackState.value

        listener.onDeviceVolumeChanged(50, false) // confirmed 0.5 == state volume 0.5 → no change

        assertEquals("Unchanged volume must not emit", before, provider.playbackState.value)

        PlayerHolder.player = null
        listener.onDeviceVolumeChanged(60, false) // player null → no-op

        assertEquals("Null player must not emit", before, provider.playbackState.value)
        PlayerHolder.isCasting = false
        provider.disconnect()
    }

    @Test
    fun `fromPlayer casting volume falls back to device volume when castVolume is zero`() {
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.castVolume = 0f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.castDeviceMuted = true
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT

        val state = PlaybackState.fromPlayer(mockPlayer)

        assertEquals(0.5f, state.volume, 0.001f)
        assertTrue(state.muted)
        PlayerHolder.castVolume = 0f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.castDeviceMuted = false
        PlayerHolder.isCasting = false
    }

    @Test
    fun `fromPlayer casting uses pending volume over confirmed`() {
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = 0.3f
        PlayerHolder.castVolume = 0.9f
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT

        val state = PlaybackState.fromPlayer(mockPlayer)

        assertEquals(0.3f, state.volume, 0.001f)
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.castVolume = 0f
        PlayerHolder.isCasting = false
    }

    @Test
    fun `poller full-refreshes on track change when playing locally`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.isPlaying } returns true
        every { mockPlayer.currentPosition } returns 5000L
        every { mockPlayer.duration } returns 300000L
        every { mockPlayer.currentMediaItem } returns MediaItem.Builder()
            .setMediaId("t99")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Live Track").build())
            .build()
        every { mockPlayer.currentMediaItemIndex } returns 1
        every { mockPlayer.mediaItemCount } returns 4
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = false
        provider.castStateSource = null

        provider.connect()
        testDispatcher.scheduler.advanceTimeBy(300)

        val state = provider.playbackState.value
        assertEquals("Live Track", state.title)
        assertEquals(1, state.trackIndex)
        PlayerHolder.player = null
        provider.disconnect()
    }

    @Test
    fun `onDeviceVolumeChanged confirms pending volume and updates state`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        every { mockPlayer.volume } returns 1.0f
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.currentPosition } returns 0L
        every { mockPlayer.duration } returns 0L
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 0
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = 0.5f
        PlayerHolder.pendingCastVolumeTimestamp = System.currentTimeMillis()
        val listener = connectAndCaptureListener(mockPlayer)

        listener.onDeviceVolumeChanged(50, false)

        assertNull("Confirmed volume must clear pending", PlayerHolder.pendingCastVolume)
        PlayerHolder.pendingCastVolumeTimestamp = 0L
        PlayerHolder.isCasting = false
        provider.disconnect()
    }

    @Test
    fun `onDeviceVolumeChanged zero does not clobber seeded volume`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        every { mockPlayer.volume } returns 1.0f
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 0
        every { mockPlayer.isPlaying } returns false
        every { mockPlayer.currentPosition } returns 0L
        every { mockPlayer.duration } returns 0L
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.castVolume = 0.4f
        PlayerHolder.castDeviceVolume = 0.4f
        val listener = connectAndCaptureListener(mockPlayer)

        listener.onDeviceVolumeChanged(0, false)

        assertEquals("Untrusted 0 must not wipe seed", 0.4f, PlayerHolder.castVolume, 0.001f)
        assertEquals("Untrusted 0 must not wipe device seed", 0.4f, PlayerHolder.castDeviceVolume, 0.001f)
        assertEquals(
            "fromPlayer must keep seeded volume",
            0.4f,
            PlaybackState.fromPlayer(mockPlayer).volume,
            0.001f,
        )
        PlayerHolder.isCasting = false
        provider.disconnect()
    }

    @Test
    fun `onDeviceVolumeChanged zero applies when muted`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        every { mockPlayer.volume } returns 1.0f
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 0
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.castVolume = 0.4f
        PlayerHolder.castDeviceVolume = 0.4f
        val listener = connectAndCaptureListener(mockPlayer)

        listener.onDeviceVolumeChanged(0, true)

        assertEquals(0f, PlayerHolder.castVolume, 0.001f)
        assertEquals(0f, PlayerHolder.castDeviceVolume, 0.001f)
        assertTrue(PlayerHolder.castDeviceMuted)
        PlayerHolder.isCasting = false
        provider.disconnect()
    }

    @Test
    fun `onDeviceVolumeChanged zero applies when pending is zero`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
        every { mockPlayer.volume } returns 1.0f
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 0
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = 0f
        PlayerHolder.pendingCastVolumeTimestamp = System.currentTimeMillis()
        PlayerHolder.castVolume = 0.4f
        PlayerHolder.castDeviceVolume = 0.4f
        val listener = connectAndCaptureListener(mockPlayer)

        listener.onDeviceVolumeChanged(0, false)

        assertEquals(0f, PlayerHolder.castVolume, 0.001f)
        assertNull("User-intent 0 confirm must clear pending", PlayerHolder.pendingCastVolume)
        PlayerHolder.isCasting = false
        provider.disconnect()
    }

    // ── P1: high-frequency position mirror ──────────────────────────────

    @Test
    fun `positionMs mirrors the published player position on connect`() {
        val player = mockk<Player>(relaxed = true)
        every { player.currentPosition } returns 42_000L
        every { player.currentMediaItem } returns null
        PlayerHolder.player = player

        provider.connect()

        assertEquals(42_000L, provider.positionMs.value)
        provider.disconnect()
    }

    @Test
    fun `positionMs updates on the lightweight poller patch`() {
        val player = mockk<Player>(relaxed = true)
        every { player.currentPosition } returns 10_000L
        every { player.currentMediaItem } returns null
        every { player.isPlaying } returns true
        PlayerHolder.player = player

        provider.connect()
        every { player.currentPosition } returns 12_500L
        testDispatcher.scheduler.advanceTimeBy(250)

        assertEquals("poller tick must mirror position (P1)", 12_500L, provider.positionMs.value)
        provider.disconnect()
    }

    // ── Dead-pipeline control recovery (idle-stopped service) ──────────────

    @Test
    fun `control with no player requests re-arm and does NOT dispatch`() {
        val captured = mutableListOf<PlaybackControl>()
        var rearmRequests = 0
        provider.setControlCallback { captured.add(it) }
        provider.onPlaybackUnavailable = { rearmRequests++ }
        provider.connect() // no PlayerHolder.player — pipeline down

        provider.playPause()
        provider.skipNext()

        assertTrue("no dispatch while unwired", captured.isEmpty())
        assertEquals("each dropped control must request a re-arm", 2, rearmRequests)
    }

    @Test
    fun `pending control replays after player attaches via connect`() {
        val captured = mutableListOf<PlaybackControl>()
        var rearmRequests = 0
        provider.setControlCallback { captured.add(it) }
        provider.onPlaybackUnavailable = { rearmRequests++ }
        // Provider not connected yet (torn down with the old service instance).
        // A control arrives in the dead window → queued + re-arm requested.
        provider.playPause()
        assertTrue(captured.isEmpty())
        assertEquals(1, rearmRequests)

        // Service (re)start attaches a Player → connect() wires + replays once.
        PlayerHolder.player = mockk<Player>(relaxed = true)
        provider.connect()

        assertEquals("pending control must replay exactly once", listOf(PlaybackControl.PLAY_PAUSE), captured)
        assertEquals("replay must not re-request a re-arm", 1, rearmRequests)
        assertTrue(provider.playerWired.value)
    }

    @Test
    fun `pending control replays after onPlayerSwitched`() {
        val captured = mutableListOf<PlaybackControl>()
        var rearmRequests = 0
        provider.setControlCallback { captured.add(it) }
        provider.onPlaybackUnavailable = { rearmRequests++ }
        provider.connect() // no player yet

        provider.toggleShuffle()
        assertTrue(captured.isEmpty())
        assertEquals(1, rearmRequests)

        // The player appears mid-lifecycle (cast switch / service re-create).
        PlayerHolder.player = mockk<Player>(relaxed = true)
        provider.onPlayerSwitched()

        assertEquals(listOf(PlaybackControl.SHUFFLE_TOGGLE), captured)
        // The replayed control must not fire a second re-arm.
        assertEquals(1, rearmRequests)
    }

    @Test
    fun `poller wires playerWired when a player appears`() {
        provider.connect() // no player yet
        assertFalse("not wired while player absent", provider.playerWired.value)

        PlayerHolder.player = mockk<Player>(relaxed = true)
        testDispatcher.scheduler.advanceTimeBy(600) // null-branch probes every 500ms

        assertTrue("poller must wire a player that appears", provider.playerWired.value)
        provider.disconnect()
        assertFalse("disconnect must unwire", provider.playerWired.value)
    }

    @Test
    fun `disconnect unwires and clears pending control`() {
        val captured = mutableListOf<PlaybackControl>()
        var rearmRequests = 0
        provider.setControlCallback { captured.add(it) }
        provider.onPlaybackUnavailable = { rearmRequests++ }
        provider.connect()

        // Queue a control while unwired (player nulled by a service teardown).
        PlayerHolder.player = null
        provider.toggleSpeed()
        assertEquals(1, rearmRequests)
        assertTrue(captured.isEmpty())

        // Teardown finishes: disconnect must forget the queued control.
        provider.disconnect()
        PlayerHolder.player = mockk<Player>(relaxed = true)
        provider.connect()

        assertTrue("pending must be dropped by disconnect", captured.isEmpty())
        assertTrue(provider.playerWired.value)
    }
}
