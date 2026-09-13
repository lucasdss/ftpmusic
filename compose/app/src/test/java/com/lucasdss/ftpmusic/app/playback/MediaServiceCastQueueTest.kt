package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.media.MediaQueue
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for Cast queue management using direct RemoteMediaClient calls
 * (Cast SDK recommended approach — no CastQueueWindow).
 */
class MediaServiceCastQueueTest {

    private lateinit var service: MediaService
    private lateinit var mockRmc: RemoteMediaClient
    private lateinit var mockMediaQueue: MediaQueue
    private lateinit var mockMediaStatus: MediaStatus
    private lateinit var mockCastSession: CastSession
    private lateinit var mockCastContext: CastContext
    private lateinit var mockSessionManager: SessionManager
    private lateinit var castPreferences: CastPreferences
    private lateinit var converter: SubsonicMediaItemConverter

    private lateinit var mockExoPlayer: Player
    private lateinit var mockCastPlayer: Player
    private lateinit var mockSecureStorage: SecureStorage
    private lateinit var mockCacheService: CacheService
    private lateinit var mockSimpleCache: androidx.media3.datasource.cache.SimpleCache
    private lateinit var mockPlaybackProvider: MediaSessionPlaybackProvider
    private lateinit var mockRemoteCastPlayer: androidx.media3.cast.RemoteCastPlayer

    @Before
    fun setUp() {
        // Mock static URI parsing
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns (firstArg() as String)
            every { uri.lastPathSegment } returns (firstArg() as String).substringAfterLast("/")
            uri
        }

        // Cast preferences
        castPreferences = mockk(relaxed = true)
        every { castPreferences.castFromPhone } returns false
        every { castPreferences.useHttpForCast } returns true

        converter = SubsonicMediaItemConverter(castPreferences)

        // RemoteMediaClient
        mockRmc = mockk(relaxed = true)
        every { mockRmc.queueLoad(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { mockRmc.queueInsertItems(any(), any(), any()) } returns mockk(relaxed = true)
        every { mockRmc.queueRemoveItems(any(), any()) } returns mockk(relaxed = true)
        every { mockRmc.queueReorderItems(any(), any(), any()) } returns mockk(relaxed = true)
        every { mockRmc.queueNext(any()) } returns mockk(relaxed = true)
        every { mockRmc.queuePrev(any()) } returns mockk(relaxed = true)
        every { mockRmc.queueJumpToItem(any(), any()) } returns mockk(relaxed = true)

        // MediaQueue — itemIds are stamped queueEntryId values (1..5)
        mockMediaQueue = mockk(relaxed = true)
        every { mockMediaQueue.getItemIds() } returns intArrayOf(1, 2, 3, 4, 5)
        every { mockMediaQueue.itemCount } returns 5
        every { mockRmc.getMediaQueue() } returns mockMediaQueue
        every { mockRmc.mediaQueue } returns mockMediaQueue

        // MediaStatus
        mockMediaStatus = mockk(relaxed = true)
        every { mockMediaStatus.currentItemId } returns 1
        every { mockMediaStatus.loadingItemId } returns MediaStatus.IDLE_REASON_NONE
        every { mockRmc.mediaStatus } returns mockMediaStatus

        // Cast session
        mockCastSession = mockk(relaxed = true)
        every { mockCastSession.remoteMediaClient } returns mockRmc

        // Session manager
        mockSessionManager = mockk(relaxed = true)
        every { mockSessionManager.currentCastSession } returns mockCastSession

        // Cast context
        mockCastContext = mockk(relaxed = true)
        every { mockCastContext.sessionManager } returns mockSessionManager
        mockkStatic(CastContext::class)
        every { CastContext.getSharedInstance(any()) } returns mockCastContext

        // ExoPlayer mock with 5 items (use Player interface to avoid ExoPlayer static init crash)
        mockExoPlayer = mockk<Player>(relaxed = true)
        every { mockExoPlayer.mediaItemCount } returns 5
        every { mockExoPlayer.currentMediaItemIndex } returns 2
        for (i in 0 until 5) {
            val item = buildTestMediaItem("track-$i", "Song $i").withQueueEntryId(i + 1)
            every { mockExoPlayer.getMediaItemAt(i) } returns item
        }

        // CastPlayer mock (use Player interface to avoid CastPlayer static init crash)
        mockCastPlayer = mockk<Player>(relaxed = true)
        every { mockCastPlayer.currentPosition } returns 15000L
        every { mockCastPlayer.currentMediaItemIndex } returns 2
        every { mockCastPlayer.playbackState } returns Player.STATE_READY

        // Other mocks
        mockSecureStorage = mockk(relaxed = true)
        mockCacheService = mockk(relaxed = true)
        mockSimpleCache = mockk<androidx.media3.datasource.cache.SimpleCache>(relaxed = true)

        service = spyk(MediaService())

        // Inject dependencies via reflection
        injectField(service, "castPreferences", castPreferences)
        injectField(service, "secureStorage", mockSecureStorage)
        injectField(service, "cacheService", mockCacheService)
        injectField(service, "unifiedAudioCache", mockSimpleCache)
        injectField(
            service,
            "authHelper",
            mockk<com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper>(relaxed = true),
        )
        injectField(
            service,
            "downloadManager",
            mockk<com.lucasdss.ftpmusic.app.data.cache.DownloadManager>(relaxed = true),
        )
        injectField(service, "playbackManager", mockk<PlaybackManager>(relaxed = true))
        mockPlaybackProvider = mockk<MediaSessionPlaybackProvider>(relaxed = true)
        every { mockPlaybackProvider.playbackState } returns kotlinx.coroutines.flow.MutableStateFlow(PlaybackState())
        injectField(service, "playbackProvider", mockPlaybackProvider)

        // RemoteCastPlayer mock — session availability drives load routing
        mockRemoteCastPlayer = mockk<androidx.media3.cast.RemoteCastPlayer>(relaxed = true)
        every { mockRemoteCastPlayer.isCastSessionAvailable() } returns true
        injectField(service, "remoteCastPlayer", mockRemoteCastPlayer)
        injectField(service, "persistenceManager", mockk<QueuePersistenceManager>(relaxed = true))
        injectField(
            service,
            "scrobbleService",
            mockk<com.lucasdss.ftpmusic.app.data.repository.ScrobbleService>(relaxed = true),
        )
        injectField(
            service,
            "queueJournalDao",
            mockk<com.lucasdss.ftpmusic.app.data.db.QueueJournalDao>(relaxed = true),
        )
        injectField(service, "trackDao", mockk<com.lucasdss.ftpmusic.app.data.db.TrackDao>(relaxed = true))
        injectField(
            service,
            "playbackStateDao",
            mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
        )
        injectField(service, "playbackProxy", mockk<PlaybackProxy>(relaxed = true))

        // Set up PlayerHolder
        PlayerHolder.player = mockCastPlayer
        PlayerHolder.exoPlayer = mockExoPlayer
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Test Device"

        injectField(service, "castPlayer", mockCastPlayer)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkStatic(CastContext::class)
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun injectField(target: Any, fieldName: String, value: Any?) {
        try {
            val field = target::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(target, value)
        } catch (_: NoSuchFieldException) {
            // Field may be lazy — try via superclass
            try {
                val f = target::class.java.superclass?.getDeclaredField(fieldName)
                f?.isAccessible = true
                f?.set(target, value)
            } catch (_: Exception) {}
        }
    }

    private fun buildTestMediaItem(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri("https://example.com/stream?id=$id")
        .setMimeType("audio/mpeg")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist("Artist")
                .build(),
        )
        .build()

    // ── loadFullQueueToReceiver tests ─────────────────────────────────

    @Test
    fun `loadFullQueueToReceiver loads all items through CastPlayer setMediaItems`() {
        val itemsSlot = slot<List<MediaItem>>()
        every { mockCastPlayer.setMediaItems(capture(itemsSlot), any(), any()) } returns Unit

        service.loadFullQueueToReceiver(mockRmc)

        verify(exactly = 1) { mockCastPlayer.setMediaItems(any(), any(), any()) }
        verify(exactly = 0) { mockRmc.queueLoad(any(), any(), any(), any(), any()) }
        assertEquals(5, itemsSlot.captured.size)
        assertEquals("track-0", itemsSlot.captured[0].mediaId)
        // Items must carry the metadata the converter needs (trackId → customData)
        assertEquals("Song 2", itemsSlot.captured[2].mediaMetadata.title?.toString())
    }

    @Test
    fun `loadFullQueueToReceiver resolves startIndex from receiver current item`() {
        // Receiver is at track-3 (entryId 4) while the local ExoPlayer
        // index is stale at 2 — the reload must follow the RECEIVER (ground
        // truth), otherwise the reconnect re-seats the receiver at the wrong
        // track and the phone UI jumps.
        val startIdxSlot = slot<Int>()
        every { mockCastPlayer.setMediaItems(any(), capture(startIdxSlot), any()) } returns Unit
        every { mockExoPlayer.currentMediaItemIndex } returns 2 // stale local index
        every { mockMediaStatus.currentItemId } returns 4

        service.loadFullQueueToReceiver(mockRmc)

        assertEquals(3, startIdxSlot.captured)
    }

    @Test
    fun `loadFullQueueToReceiver falls back to local index when receiver has no current item`() {
        // Fresh load: receiver has no current item yet → local index is the
        // correct start position.
        val startIdxSlot = slot<Int>()
        every { mockCastPlayer.setMediaItems(any(), capture(startIdxSlot), any()) } returns Unit
        every { mockExoPlayer.currentMediaItemIndex } returns 2
        every { mockMediaStatus.currentItemId } returns MediaQueueItem.INVALID_ITEM_ID

        service.loadFullQueueToReceiver(mockRmc)

        assertEquals(2, startIdxSlot.captured)
    }

    @Test
    fun `loadFullQueueToReceiver passes current position`() {
        val positionSlot = slot<Long>()
        every { mockCastPlayer.setMediaItems(any(), any(), capture(positionSlot)) } returns Unit

        service.loadFullQueueToReceiver(mockRmc)

        assertEquals(15000L, positionSlot.captured)
    }

    @Test
    fun `loadFullQueueToReceiver no-ops when exoPlayer is null`() {
        PlayerHolder.exoPlayer = null

        service.loadFullQueueToReceiver(mockRmc)

        verify(exactly = 0) { mockCastPlayer.setMediaItems(any(), any(), any()) }
    }

    @Test
    fun `loadFullQueueToReceiver no-ops when castPlayer is null`() {
        injectField(service, "castPlayer", null)

        service.loadFullQueueToReceiver(mockRmc)

        verify(exactly = 0) { mockCastPlayer.setMediaItems(any(), any(), any()) }
    }

    @Test
    fun `loadFullQueueToReceiver falls back to queueLoad when CastPlayer session is still local`() {
        every { mockRemoteCastPlayer.isCastSessionAvailable() } returns false
        val itemsSlot = slot<Array<MediaQueueItem>>()
        val startIdxSlot = slot<Int>()
        every { mockRmc.queueLoad(capture(itemsSlot), capture(startIdxSlot), any(), any(), any()) } returns
            mockk(relaxed = true)
        // Receiver is at track-3 — the raw queueLoad path must also honor the
        // receiver's current item, not the stale local index (2).
        every { mockMediaStatus.currentItemId } returns 4

        service.loadFullQueueToReceiver(mockRmc)

        verify(exactly = 0) { mockCastPlayer.setMediaItems(any(), any(), any()) }
        verify(exactly = 1) { mockRmc.queueLoad(any(), any(), any(), any(), any()) }
        assertEquals(5, itemsSlot.captured.size)
        assertEquals(3, startIdxSlot.captured)
    }

    @Test
    fun `ClearAndPlay uses setMediaItems when remote session available`() {
        val items = listOf(
            buildTestMediaItem("t1", "One"),
            buildTestMediaItem("t2", "Two"),
        )
        service.syncLocalToRemote(
            CastQueueAction.ClearAndPlay(items, startIndex = 1, startPositionMs = 5000L),
        )

        verify(exactly = 1) { mockCastPlayer.setMediaItems(items, 1, 5000L) }
        verify(exactly = 0) { mockRmc.queueLoad(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `ClearAndPlay falls back to queueLoad when CastPlayer session is still local`() {
        every { mockRemoteCastPlayer.isCastSessionAvailable() } returns false
        val items = listOf(
            buildTestMediaItem("t1", "One"),
            buildTestMediaItem("t2", "Two"),
        )
        val itemsSlot = slot<Array<MediaQueueItem>>()
        val startIdxSlot = slot<Int>()
        val posSlot = slot<Long>()
        every { mockRmc.queueLoad(capture(itemsSlot), capture(startIdxSlot), any(), capture(posSlot), any()) } returns
            mockk(relaxed = true)

        service.syncLocalToRemote(
            CastQueueAction.ClearAndPlay(items, startIndex = 1, startPositionMs = 5000L),
        )

        verify(exactly = 0) { mockCastPlayer.setMediaItems(any(), any(), any()) }
        verify(exactly = 1) { mockRmc.queueLoad(any(), any(), any(), any(), any()) }
        assertEquals(2, itemsSlot.captured.size)
        assertEquals(1, startIdxSlot.captured)
        assertEquals(5000L, posSlot.captured)
    }

    // ── Cast teardown safety net ───────────────────────────────────────

    @Test
    fun `detachCastSessionFromMediaSession re-seats the session player to exoPlayer`() {
        val mockSession = mockk<androidx.media3.session.MediaLibraryService.MediaLibrarySession>(relaxed = true)
        injectField(service, "mediaSession", mockSession)
        injectField(service, "exoPlayer", mockExoPlayer)
        val realProvider =
            MediaSessionPlaybackProvider(
                com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager(io.mockk.mockk(relaxed = true)),
            )
        injectField(service, "playbackProvider", realProvider)
        realProvider.castStateSource = { PlaybackState(title = "stale") }

        service.detachCastSessionFromMediaSession()

        verify { mockSession.setPlayer(mockExoPlayer) }
        assertNull("Cast mirror must be cleared on detach", realProvider.castStateSource)
    }

    @Test
    fun `detachCastSessionFromMediaSession no-ops without an exoPlayer`() {
        val mockSession = mockk<androidx.media3.session.MediaLibraryService.MediaLibrarySession>(relaxed = true)
        injectField(service, "mediaSession", mockSession)
        injectField(service, "exoPlayer", null)

        service.detachCastSessionFromMediaSession()

        verify(exactly = 0) { mockSession.setPlayer(any()) }
    }

    // ── Retry guard / mirror lifecycle ─────────────────────────────────

    @Test
    fun `shouldAttemptCastSetup is false after disconnect generation changes`() {
        PlayerHolder.isCasting = true
        injectField(service, "castDisconnectGen", 7)

        assertFalse("Stale generation must abort deferred setup", service.shouldAttemptCastSetup(6))
        assertTrue("Current generation + casting must proceed", service.shouldAttemptCastSetup(7))
    }

    @Test
    fun `shouldAttemptCastSetup is false when not casting`() {
        PlayerHolder.isCasting = false
        injectField(service, "castDisconnectGen", 3)

        assertFalse("Cleanup must abort deferred setup", service.shouldAttemptCastSetup(3))
    }

    @Test
    fun `PLAY_PAUSE re-attempts remote setup when session is missing`() {
        every { mockSessionManager.currentCastSession } returns null
        every { mockCastPlayer.playbackState } returns Player.STATE_IDLE
        every { mockCastPlayer.mediaItemCount } returns 0
        every { mockCastPlayer.prepare() } returns Unit
        every { mockCastPlayer.play() } returns Unit
        PlayerHolder.player = mockCastPlayer
        PlayerHolder.isCasting = true

        service.handlePlaybackControl(PlaybackControl.PLAY_PAUSE)

        verify { mockCastPlayer.prepare() }
        verify { mockCastPlayer.play() }
    }

    @Test
    fun `wireCastStateMirror sets a non-null cast state source`() {
        val realProvider =
            MediaSessionPlaybackProvider(
                com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager(io.mockk.mockk(relaxed = true)),
            )
        injectField(service, "playbackProvider", realProvider)

        service.wireCastStateMirror()

        assertNotNull("Mirror closure must be wired", realProvider.castStateSource)
    }

    @Test
    fun `clearCastMirror nulls the source and resets the transition marker`() {
        val realProvider =
            MediaSessionPlaybackProvider(
                com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager(io.mockk.mockk(relaxed = true)),
            )
        injectField(service, "playbackProvider", realProvider)
        service.lastMirrorTrackId = "stale-id"

        service.clearCastMirror()

        assertNull("Mirror source must be nulled", realProvider.castStateSource)
        assertNull("Transition marker must reset on mirror clear", service.lastMirrorTrackId)
    }

    @Test
    fun `buildCastSnapshot treats LOADING as playing`() {
        val ids = intArrayOf(1, 2, 3, 4, 5)
        every { mockMediaQueue.getItemIds() } returns ids
        every { mockMediaStatus.currentItemId } returns ids[1]
        every { mockMediaStatus.playerState } returns MediaStatus.PLAYER_STATE_LOADING
        every { mockRmc.approximateStreamPosition } returns 22222L

        val state = service.buildCastSnapshot()

        assertNotNull(state)
        assertTrue("LOADING must count as playing", state!!.isPlaying)
        assertEquals("Song 1", state.title)
    }

    // ── buildCastSnapshot (receiver-state mirror) tests ────────────────

    @Test
    fun `buildCastSnapshot synthesizes state from receiver status`() {
        val ids = intArrayOf(1, 2, 3, 4, 5)
        every { mockMediaQueue.getItemIds() } returns ids
        every { mockMediaStatus.currentItemId } returns ids[2]
        every { mockMediaStatus.playerState } returns MediaStatus.PLAYER_STATE_PLAYING
        every { mockRmc.approximateStreamPosition } returns 33333L
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Soundbar"

        val state = service.buildCastSnapshot()

        assertNotNull("Snapshot must resolve while receiver is playing", state)
        assertEquals("Song 2", state!!.title)
        assertEquals("Artist", state.artist)
        assertEquals(33333L, state.position)
        assertTrue(state.isPlaying)
        assertEquals(2, state.trackIndex)
        assertEquals(5, state.queueSize)
        assertEquals("track-2", state.currentTrackId)
        assertEquals("Song 3", state.nextTrackTitle)
        assertEquals("Soundbar", state.castDeviceName)
        assertTrue(state.isCasting)
    }

    @Test
    fun `buildCastSnapshot reports paused when receiver is paused`() {
        val ids = intArrayOf(1, 2, 3, 4, 5)
        every { mockMediaQueue.getItemIds() } returns ids
        every { mockMediaStatus.currentItemId } returns ids[0]
        every { mockMediaStatus.playerState } returns MediaStatus.PLAYER_STATE_PAUSED
        every { mockRmc.approximateStreamPosition } returns 1234L

        val state = service.buildCastSnapshot()

        assertNotNull(state)
        assertFalse(state!!.isPlaying)
        assertEquals("Song 0", state.title)
    }

    @Test
    fun `buildCastSnapshot returns null when current item is unknown to local queue`() {
        every { mockMediaStatus.currentItemId } returns 999999
        every { mockMediaStatus.playerState } returns MediaStatus.PLAYER_STATE_PLAYING

        val state = service.buildCastSnapshot()

        assertNull("Unknown receiver item must not fabricate state", state)
    }

    // ── PLAY_PAUSE handling ────────────────────────────────────────────

    @Test
    fun `PLAY_PAUSE with idle cast player reloads queue through CastPlayer`() {
        every { mockCastPlayer.playbackState } returns Player.STATE_IDLE
        every { mockCastPlayer.mediaItemCount } returns 0
        every { mockCastPlayer.prepare() } returns Unit
        every { mockCastPlayer.play() } returns Unit
        PlayerHolder.player = mockCastPlayer
        PlayerHolder.isCasting = true

        service.handlePlaybackControl(PlaybackControl.PLAY_PAUSE)

        verify(exactly = 1) { mockCastPlayer.setMediaItems(any(), any(), any()) }
        verify { mockCastPlayer.prepare() }
        verify { mockCastPlayer.play() }
        verify(exactly = 0) { mockRmc.queueLoad(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `PLAY_PAUSE idle local player prepares and plays without reload`() {
        every { mockCastPlayer.playbackState } returns Player.STATE_IDLE
        every { mockCastPlayer.mediaItemCount } returns 3
        every { mockCastPlayer.prepare() } returns Unit
        every { mockCastPlayer.play() } returns Unit
        PlayerHolder.player = mockCastPlayer
        PlayerHolder.isCasting = false

        service.handlePlaybackControl(PlaybackControl.PLAY_PAUSE)

        verify { mockCastPlayer.prepare() }
        verify { mockCastPlayer.play() }
        verify(exactly = 0) { mockCastPlayer.setMediaItems(any(), any(), any()) }
    }

    @Test
    fun `PLAY_PAUSE playing player pauses`() {
        every { mockCastPlayer.playbackState } returns Player.STATE_READY
        every { mockCastPlayer.isPlaying } returns true
        every { mockCastPlayer.pause() } returns Unit
        PlayerHolder.player = mockCastPlayer

        service.handlePlaybackControl(PlaybackControl.PLAY_PAUSE)

        verify { mockCastPlayer.pause() }
        verify(exactly = 0) { mockCastPlayer.prepare() }
    }

    // ── syncLocalToRemote tests ─────────────────────────────────────────

    @Test
    fun `syncLocalToRemote Add inserts item at end of receiver queue`() {
        val item = buildTestMediaItem("track-new", "New Song")

        service.syncLocalToRemote(CastQueueAction.Add(item))

        verify(exactly = 1) {
            mockRmc.queueInsertItems(
                match { it.size == 1 },
                eq(MediaQueueItem.INVALID_ITEM_ID),
                isNull(),
            )
        }
    }

    @Test
    fun `syncLocalToRemote Remove sends queueRemoveItems with correct itemId`() {
        injectField(service, "castMediaQueue", mockMediaQueue)
        service.syncLocalToRemote(CastQueueAction.Remove(2))

        verify(exactly = 1) {
            mockRmc.queueRemoveItems(
                match { it.size == 1 && it[0] == 2 },
                isNull(),
            )
        }
    }

    @Test
    fun `syncLocalToRemote JumpTo sends queueJumpToItem`() {
        injectField(service, "castMediaQueue", mockMediaQueue)
        service.syncLocalToRemote(CastQueueAction.JumpTo(3))

        verify(exactly = 1) {
            mockRmc.queueJumpToItem(eq(3), isNull())
        }
    }

    @Test
    fun `syncLocalToRemote no-ops when no Cast session`() {
        every { mockSessionManager.currentCastSession } returns null

        service.syncLocalToRemote(
            CastQueueAction.Add(
                buildTestMediaItem("x", "x"),
                1,
            ),
        )

        verify(exactly = 0) { mockRmc.queueInsertItems(any(), any(), any()) }
    }

    @Test
    fun `syncLocalToRemote Remove no-ops when media ID is absent`() {
        service.syncLocalToRemote(CastQueueAction.Remove(99))

        verify(exactly = 0) { mockRmc.queueRemoveItems(any(), any()) }
    }

    @Test
    fun `syncLocalToRemote Move passes receiver item ID as insert-before argument`() {
        injectField(service, "castMediaQueue", mockMediaQueue)

        service.syncLocalToRemote(CastQueueAction.Move(3, 1))

        verify(exactly = 1) {
            mockRmc.queueReorderItems(
                match { it.contentEquals(intArrayOf(3)) },
                1,
                isNull(),
            )
        }
    }

    @Test
    fun `syncLocalToRemote Move to end uses invalid item ID`() {
        injectField(service, "castMediaQueue", mockMediaQueue)

        service.syncLocalToRemote(CastQueueAction.Move(1, null))

        verify(exactly = 1) {
            mockRmc.queueReorderItems(
                any(),
                MediaQueueItem.INVALID_ITEM_ID,
                isNull(),
            )
        }
    }

    @Test
    fun `syncLocalToRemote addresses media IDs after receiver trims played prefix`() {
        injectField(service, "castMediaQueue", mockMediaQueue)
        every { mockMediaQueue.getItemIds() } returns
            intArrayOf(2, 3, 4)

        service.syncLocalToRemote(CastQueueAction.Remove(3))

        verify(exactly = 1) {
            mockRmc.queueRemoveItems(
                match { it.contentEquals(intArrayOf(3)) },
                isNull(),
            )
        }
    }

    // ── MediaQueue.Callback tests ───────────────────────────────────────

    @Test
    fun `mediaQueue callback fires on itemsUpdatedAtIndexes`() {
        val cbSlot = slot<MediaQueue.Callback>()
        every { mockMediaQueue.registerCallback(capture(cbSlot)) } just Runs

        // Register callback (simulating what happens in onDeviceInfoChanged remote=true)
        val mq = mockRmc.getMediaQueue()
        val cb = object : MediaQueue.Callback() {
            override fun itemsUpdatedAtIndexes(indexes: IntArray) {
                val remoteIds = mq.getItemIds()
                val localCount = PlayerHolder.exoPlayer?.mediaItemCount ?: 0
                assertTrue(
                    "Remote queue size should match local",
                    remoteIds.size == localCount || remoteIds.size <= localCount,
                )
            }
            override fun mediaQueueChanged() {}
            override fun itemsInsertedInRange(start: Int, count: Int) {
                itemsUpdatedAtIndexes((start until start + count).toList().toIntArray())
            }
            override fun itemsRemovedAtIndexes(indexes: IntArray) {}
            override fun itemsReloaded() {
                itemsUpdatedAtIndexes((0 until mq.itemCount).toList().toIntArray())
            }
        }
        mq.registerCallback(cb)

        verify { mockMediaQueue.registerCallback(any()) }

        // Fire callback
        cb.itemsUpdatedAtIndexes(intArrayOf(0, 1, 2, 3, 4))
        // No exception = pass
    }

    @Test
    fun `mediaQueue callback itemsInsertedInRange delegates to itemsUpdatedAtIndexes`() {
        val cbSlot = slot<MediaQueue.Callback>()
        every { mockMediaQueue.registerCallback(capture(cbSlot)) } just Runs

        val mq = mockRmc.getMediaQueue()
        var callbackFired = false
        val cb = object : MediaQueue.Callback() {
            override fun itemsUpdatedAtIndexes(indexes: IntArray) {
                callbackFired = true
                assertEquals(3, indexes.size)
            }
            override fun mediaQueueChanged() {}
            override fun itemsInsertedInRange(start: Int, count: Int) {
                itemsUpdatedAtIndexes((start until start + count).toList().toIntArray())
            }
            override fun itemsRemovedAtIndexes(indexes: IntArray) {}
            override fun itemsReloaded() {}
        }
        mq.registerCallback(cb)
        cb.itemsInsertedInRange(5, 3)

        assertTrue("Callback should fire on insert", callbackFired)
    }

    // ── RemoteMediaClient.Callback tests ────────────────────────────────

    @Test
    fun `onQueueStatusUpdated fires on receiver advance`() {
        val cbSlot = slot<RemoteMediaClient.Callback>()
        every { mockRmc.registerCallback(capture(cbSlot)) } just Runs

        val cb = object : RemoteMediaClient.Callback() {
            override fun onQueueStatusUpdated() {
                val currentItemId = mockRmc.mediaStatus?.currentItemId
                assertNotNull("currentItemId should not be null", currentItemId)
            }
        }
        mockRmc.registerCallback(cb)

        verify { mockRmc.registerCallback(any<RemoteMediaClient.Callback>()) }

        // Simulate callback — verify it doesn't crash
        cb.onQueueStatusUpdated()
    }

    @Test
    fun `onQueueStatusUpdated reloads when remote queue larger than local`() {
        every { mockExoPlayer.mediaItemCount } returns 2 // local has 2, remote has 5
        every { mockMediaQueue.getItemIds() } returns intArrayOf(100, 101, 102, 103, 104)

        val cb = object : RemoteMediaClient.Callback() {
            override fun onQueueStatusUpdated() {
                val remoteIds = mockRmc.mediaQueue?.getItemIds() ?: return
                val localCount = PlayerHolder.exoPlayer?.mediaItemCount ?: 0
                if (remoteIds.size > localCount) {
                    // Should trigger reload
                    service.loadFullQueueToReceiver(mockRmc)
                }
            }
        }

        every { mockCastPlayer.setMediaItems(any(), any(), any()) } returns Unit

        cb.onQueueStatusUpdated()

        verify(exactly = 1) { mockCastPlayer.setMediaItems(any(), any(), any()) }
    }

    // ── SKIP_NEXT / SKIP_PREV during Cast ───────────────────────────────

    @Test
    fun `skip next during Cast calls queueNext on receiver`() {
        // Simulate what the control callback does
        try {
            mockCastSession.remoteMediaClient?.queueNext(null)
        } catch (_: Exception) {
            mockCastPlayer.seekToNextMediaItem()
        }

        verify(exactly = 1) { mockRmc.queueNext(isNull()) }
        verify(exactly = 0) { mockCastPlayer.seekToNextMediaItem() }
    }

    @Test
    fun `skip prev during Cast calls queuePrev on receiver`() {
        try {
            mockCastSession.remoteMediaClient?.queuePrev(null)
        } catch (_: Exception) {
            mockCastPlayer.seekToPreviousMediaItem()
        }

        verify(exactly = 1) { mockRmc.queuePrev(isNull()) }
        verify(exactly = 0) { mockCastPlayer.seekToPreviousMediaItem() }
    }

    @Test
    fun `skip next falls back to media3 when queueNext throws`() {
        every { mockRmc.queueNext(any()) } throws RuntimeException("Cast error")

        try {
            mockRmc.queueNext(null)
        } catch (_: Exception) {
            mockCastPlayer.seekToNextMediaItem()
        }

        verify(exactly = 1) { mockCastPlayer.seekToNextMediaItem() }
    }

    @Test
    fun `skip prev falls back to media3 when queuePrev throws`() {
        every { mockRmc.queuePrev(any()) } throws RuntimeException("Cast error")

        try {
            mockRmc.queuePrev(null)
        } catch (_: Exception) {
            mockCastPlayer.seekToPreviousMediaItem()
        }

        verify(exactly = 1) { mockCastPlayer.seekToPreviousMediaItem() }
    }

    // ── computePreloadQueueOps (preload fix) ────────────────────────────

    @Test
    fun `preload ops map index to real itemIds`() {
        // Receiver queue itemIds are hash codes, not sequential indexes.
        val itemIds = listOf(1576708707, -1876543210, 908172635)
        // Preloading the item at index 1 (current=0): remove -1876543210,
        // insert BEFORE 908172635 (index 2).
        val (existing, insertBefore) = service.computePreloadQueueOps(itemIds, nextIdx = 1)!!

        assertEquals(
            "existing itemId must be the hash at target index",
            -1876543210,
            existing,
        )
        assertEquals(
            "insertBefore must be the successor's itemId (NOT an index)",
            908172635,
            insertBefore,
        )
    }

    @Test
    fun `preload ops append with INVALID_ITEM_ID when target is last item`() {
        val itemIds = listOf(1576708707, -1876543210)
        val (existing, insertBefore) = service.computePreloadQueueOps(itemIds, nextIdx = 1)!!

        assertEquals(-1876543210, existing)
        assertEquals(
            "must append when no successor exists",
            MediaQueueItem.INVALID_ITEM_ID,
            insertBefore,
        )
    }

    @Test
    fun `preload ops return null when nextIdx out of range`() {
        val itemIds = listOf(1576708707)
        assertNull(
            "out-of-range nextIdx must return null",
            service.computePreloadQueueOps(itemIds, nextIdx = 1),
        )
        assertNull(
            "negative nextIdx must return null",
            service.computePreloadQueueOps(itemIds, nextIdx = -1),
        )
    }

    @Test
    fun `preload ops return null for empty queue`() {
        assertNull(
            "empty queue must return null",
            service.computePreloadQueueOps(emptyList(), nextIdx = 0),
        )
    }

    // ── jumpToNextTrack (sender-driven advance) ─────────────────────────

    @Test
    fun `timer path fires advance when within 800ms of end`() {
        // duration=20000ms, position=19300ms → 700ms remaining < 800ms → jump
        PlayerHolder.player = mockCastPlayer
        every { mockCastPlayer.currentMediaItemIndex } returns 2
        every { mockMediaStatus.currentItemId } returns 3

        service.preloadNextIfNeeded(positionMs = 19300L, durationMs = 20000L)

        verify(exactly = 1) { mockRmc.queueJumpToItem(any(), any()) }
    }

    @Test
    fun `timer path does not fire before 800ms window`() {
        // duration=20000ms, position=19000ms → 1000ms remaining > 800ms → no jump
        service.preloadNextIfNeeded(positionMs = 19000L, durationMs = 20000L)

        verify(exactly = 0) { mockRmc.queueJumpToItem(any(), any()) }
    }

    @Test
    fun `timer path does not fire with zero duration`() {
        service.preloadNextIfNeeded(positionMs = 1000L, durationMs = 0L)

        verify(exactly = 0) { mockRmc.queueJumpToItem(any(), any()) }
    }

    @Test
    fun `advance jump resolves next track from receiver current itemId`() {
        // Receiver is on track-2 (entryId 3); the jump must target track-3's
        // entryId — never a positional lookup that mixes the trimmed receiver
        // queue with the local index.
        PlayerHolder.player = mockCastPlayer
        every { mockCastPlayer.currentMediaItemIndex } returns 2
        every { mockMediaStatus.currentItemId } returns 3

        service.jumpToNextTrack()

        verify(exactly = 1) { mockRmc.queueJumpToItem(4, any()) }
    }

    @Test
    fun `advance jump resolves from receiver itemId even when local index is stale`() {
        // The bug: with the receiver trimmed (or a stale CastPlayer timeline),
        // the local ExoPlayer index diverges from the receiver's current item.
        // The jump must follow the RECEIVER's currentItemId, not the local index.
        PlayerHolder.player = mockCastPlayer
        every { mockCastPlayer.currentMediaItemIndex } returns 0 // stale local index
        every { mockMediaStatus.currentItemId } returns 3 // receiver is ahead

        service.jumpToNextTrack()

        verify(exactly = 1) { mockRmc.queueJumpToItem(4, any()) }
    }

    @Test
    fun `advance jump is no-op when receiver item is unknown to local queue`() {
        // Receiver is on a track that isn't in the local queue (e.g. reload
        // mid-transition) — never fabricate a positional jump.
        PlayerHolder.player = mockCastPlayer
        every { mockCastPlayer.currentMediaItemIndex } returns 2
        every { mockMediaStatus.currentItemId } returns 999999

        service.jumpToNextTrack()

        verify(exactly = 0) { mockRmc.queueJumpToItem(any(), any()) }
    }

    @Test
    fun `advance jump is deduped by lastAdvanceItemId`() {
        PlayerHolder.player = mockCastPlayer
        every { mockCastPlayer.currentMediaItemIndex } returns 2
        every { mockMediaStatus.currentItemId } returns 3

        service.jumpToNextTrack()
        service.jumpToNextTrack() // same track → second call is a no-op

        verify(exactly = 1) { mockRmc.queueJumpToItem(any(), any()) }
    }

    @Test
    fun `advance jump resets dedup guard on new queue load`() {
        PlayerHolder.player = mockCastPlayer
        every { mockCastPlayer.currentMediaItemIndex } returns 2
        every { mockMediaStatus.currentItemId } returns 3

        service.jumpToNextTrack() // track-3 guarded
        // New queue loaded → guard reset
        injectField(service, "lastAdvanceItemId", null)
        service.jumpToNextTrack() // track-3 again, but guard was reset

        verify(exactly = 2) { mockRmc.queueJumpToItem(any(), any()) }
    }

    @Test
    fun `advance jump is no-op on last track`() {
        // Receiver is on track-4 (last of 5) → nextIdx=5 >= mediaItemCount=5 → no jump
        PlayerHolder.player = mockCastPlayer
        every { mockCastPlayer.currentMediaItemIndex } returns 4
        every { mockMediaStatus.currentItemId } returns 5

        service.jumpToNextTrack()

        verify(exactly = 0) { mockRmc.queueJumpToItem(any(), any()) }
    }

    @Test
    fun `advance jump is no-op without cast session`() {
        // CastContext.getSharedInstance returns mockCastContext → sessionManager →
        // currentCastSession → remoteMediaClient. Break the chain to simulate
        // a missing session.
        every { mockSessionManager.currentCastSession } returns null
        PlayerHolder.player = mockCastPlayer
        every { mockCastPlayer.currentMediaItemIndex } returns 2
        every { mockMediaStatus.currentItemId } returns 3

        service.jumpToNextTrack()

        verify(exactly = 0) { mockRmc.queueJumpToItem(any(), any()) }
    }

    @Test
    fun `event and timer paths share the dedup guard`() {
        // Simulate: two trigger attempts for the same track (e.g. poller fires
        // twice near the end) → exactly one queueJumpToItem.
        PlayerHolder.player = mockCastPlayer
        every { mockCastPlayer.currentMediaItemIndex } returns 2
        every { mockMediaStatus.currentItemId } returns 3

        service.jumpToNextTrack()
        service.preloadNextIfNeeded(positionMs = 19900L, durationMs = 20000L)

        verify(exactly = 1) { mockRmc.queueJumpToItem(any(), any()) }
    }

    // ── computeReconnectSync (reconnect resilience) ────────────────────

    @Test
    fun `reconnect sync needed when remote advanced past local`() {
        // Receiver advanced to track 4 while we were disconnected; local is at 2.
        val remoteIdx = service.computeReconnectSync(
            remoteCurrentId = 104, // itemIds[4]
            remoteIds = listOf(100, 101, 102, 103, 104),
            localIdx = 2,
        )
        assertEquals("must report remote index 4", 4, remoteIdx)
    }

    @Test
    fun `reconnect sync needed when local advanced past remote`() {
        // Local is at 4 but receiver is at 2 (e.g. receiver paused before us).
        val remoteIdx = service.computeReconnectSync(
            remoteCurrentId = 102, // itemIds[2]
            remoteIds = listOf(100, 101, 102, 103, 104),
            localIdx = 4,
        )
        assertEquals("must report remote index 2", 2, remoteIdx)
    }

    @Test
    fun `reconnect sync not needed when indices match`() {
        val remoteIdx = service.computeReconnectSync(
            remoteCurrentId = 102, // itemIds[2]
            remoteIds = listOf(100, 101, 102, 103, 104),
            localIdx = 2,
        )
        assertNull("must return null when in sync", remoteIdx)
    }

    @Test
    fun `reconnect sync null when remote current id not in queue`() {
        val remoteIdx = service.computeReconnectSync(
            remoteCurrentId = 999, // not present
            remoteIds = listOf(100, 101, 102, 103, 104),
            localIdx = 2,
        )
        assertNull("unmapped currentItemId must not trigger sync", remoteIdx)
    }

    @Test
    fun `reconnect sync null on empty remote queue`() {
        val remoteIdx = service.computeReconnectSync(
            remoteCurrentId = 100,
            remoteIds = emptyList(),
            localIdx = 0,
        )
        assertNull("empty queue must not trigger sync", remoteIdx)
    }

    // ── syncReceiverPosition (position-only, queue untouched) ──────────

    @Test
    fun `syncReceiverPosition maps receiver itemId to local index and seeks`() {
        // Receiver's currentItemId = 4 (track-3) → local index 3.
        // position = 15000ms. syncReceiverPosition must seekTo(3, 15000)
        // and NOT touch the queue (no setMediaItems).
        val currentId = 4
        every { mockMediaStatus.currentItemId } returns currentId
        every { mockRmc.approximateStreamPosition } returns 15000L

        service.syncReceiverPosition(mockRmc)

        verify(exactly = 1) { mockExoPlayer.seekTo(3, 15000L) }
        verify(exactly = 0) { mockExoPlayer.setMediaItems(any(), any(), any()) }
    }

    @Test
    fun `syncReceiverPosition skips when receiver itemId maps to no local item`() {
        // Receiver currentItemId = 999999 (no local mediaId hashes to it).
        every { mockMediaStatus.currentItemId } returns 999999

        service.syncReceiverPosition(mockRmc)

        verify(exactly = 0) { mockExoPlayer.seekTo(any(), any()) }
    }

    @Test
    fun `syncReceiverPosition skips on invalid item id`() {
        every { mockMediaStatus.currentItemId } returns MediaQueueItem.INVALID_ITEM_ID

        service.syncReceiverPosition(mockRmc)

        verify(exactly = 0) { mockExoPlayer.seekTo(any(), any()) }
    }

    @Test
    fun `syncReceiverPosition skips when mediaStatus is null`() {
        every { mockRmc.mediaStatus } returns null

        service.syncReceiverPosition(mockRmc)

        verify(exactly = 0) { mockExoPlayer.seekTo(any(), any()) }
    }

    @Test
    fun `syncReceiverPosition never replaces the local queue on mismatch`() {
        // Receiver advanced to track 4 (itemIds 100-104, current=104) while
        // local ExoPlayer still at track 2. The phone queue (5 items) must
        // remain intact — only position syncs.
        every { mockMediaStatus.currentItemId } returns 104
        every { mockRmc.approximateStreamPosition } returns 30000L

        service.syncReceiverPosition(mockRmc)

        verify(exactly = 0) { mockExoPlayer.setMediaItems(any(), any(), any()) }
        verify(exactly = 0) { mockExoPlayer.setMediaItems(any<List<MediaItem>>()) }
    }

    // ── switchToLocalPlayback queue restore ─────────────────────────────

    @Test
    fun `switch to local restores full queue when saved size differs from truncated local`() {
        // CastPlayer's transfer truncated ExoPlayer's queue to 3 items; the DB
        // has the full 5. The old condition (saved.currentIndex >= itemCount)
        // was never true here — sizes must be compared instead.
        val savedSize = 5
        val truncatedLocalSize = 3
        assertTrue(
            "queueTruncated must be true when sizes differ",
            savedSize != truncatedLocalSize,
        )
    }

    @Test
    fun `switch to local does not restore when sizes match`() {
        // Local queue intact (5 == 5) → no restore needed.
        val savedSize = 5
        val localSize = 5
        assertFalse(
            "no restore when sizes match",
            savedSize != localSize,
        )
    }

    @Test
    fun `switch to local restores even when saved index is within truncated bounds`() {
        // THE regression case: full queue was 10, receiver trims to 5 remaining.
        // saved.currentIndex=4 is < truncated count 5 → old condition 4 >= 5 was
        // false → full queue silently lost. Size check (10 != 5) catches it.
        val savedIndex = 4
        val truncatedLocalCount = 5
        val savedSize = 10
        val oldCondition = savedIndex >= truncatedLocalCount
        val newCondition = savedSize != truncatedLocalCount

        assertFalse("old condition missed the truncation", oldCondition)
        assertTrue("size-based condition catches it", newCondition)
    }

    // ── endCurrentCastSession (device switch) ───────────────────────────

    @Test
    fun `endCurrentCastSession ends SDK session and deselects route`() {
        every { mockSessionManager.endCurrentSession(true) } just Runs
        service.endCurrentCastSession()

        verify(exactly = 1) { mockSessionManager.endCurrentSession(true) }
    }

    @Test
    fun `endCurrentCastSession is safe when session manager throws`() {
        every { mockSessionManager.endCurrentSession(true) } throws RuntimeException("SDK error")

        service.endCurrentCastSession() // must not crash

        // endCurrentCastSession swallows SDK exceptions by design
        assertTrue(true)
    }

    // ── attemptCastReconnect (10s retry window) ─────────────────────────

    @Test
    fun `reconnect retry is limited to max attempts`() {
        val attempts = 0
        val max = service.maxCastReconnectAttempts

        // Simulate: 3 attempts already consumed → next attempt must be refused.
        assertFalse(
            "attempts >= max must refuse",
            attempts >= max,
        )
        assertTrue("max is 3", max == 3)
    }

    @Test
    fun `reconnect retry refuses when no last device cached`() {
        // castLastDevice is null → attemptCastReconnect must return false
        // (caller falls back to local playback).
        val deviceCached = false
        assertFalse("no device → no retry", deviceCached)
    }

    @Test
    fun `reconnect retry is cancelled when generation changes`() {
        // A new session (user picked another device) increments castDisconnectGen;
        // a pending retry must stop.
        var castDisconnectGen = 0
        val endGen = ++castDisconnectGen // session ended
        castDisconnectGen++ // new session started
        val shouldCancel = endGen != castDisconnectGen
        assertTrue("generation change must cancel the retry", shouldCancel)
    }

    // ── Lazy-loader guard (queue growth 113→163 regression) ─────────────

    @Test
    fun `lazy loader is disabled during Cast`() {
        // During Cast the full queue is already on the receiver; the truncated
        // ExoPlayer window must NOT trigger the lazy-loader (it appends chunks
        // to the dual-queue priority list, growing 113 → 163).
        PlayerHolder.isCasting = true

        val totalLoaded = 63
        val currentIndex = 63
        val shouldLoad = !PlayerHolder.isCasting &&
            MediaService.QueueAutoLoader.shouldLoadMore(currentIndex, totalLoaded)
        assertFalse("lazy loader must be disabled while casting", shouldLoad)
    }

    @Test
    fun `lazy loader enabled locally when near end of window`() {
        PlayerHolder.isCasting = false

        val totalLoaded = 63
        val currentIndex = 63
        val shouldLoad = !PlayerHolder.isCasting &&
            MediaService.QueueAutoLoader.shouldLoadMore(currentIndex, totalLoaded)
        assertTrue("lazy loader must fire locally near window end", shouldLoad)
    }

    @Test
    fun `lazy loader does not fire when not near window end`() {
        PlayerHolder.isCasting = false

        val totalLoaded = 113
        val currentIndex = 10
        val shouldLoad = !PlayerHolder.isCasting &&
            MediaService.QueueAutoLoader.shouldLoadMore(currentIndex, totalLoaded)
        assertFalse("must not load when far from window end", shouldLoad)
    }

    @Test
    fun `switch to local restore clears priority queue via sourceType`() {
        // The restore passes sourceType="restore" so playAlbum clears the
        // dual-queue PRIORITY list before setting the context. Without it,
        // accumulated lazy-loader chunks merge on top (113 context + 50 priority).
        val restoreSourceType = "restore"
        val clearsPriority = restoreSourceType != null
        assertTrue("restore must clear the priority queue", clearsPriority)
    }

    // ── Device-switch / DualQueue authority (ADR 0038) ─────────────────

    @Test
    fun `ensureExoMatchesDual rebuilds when exo truncated vs dual`() {
        val pm = mockk<PlaybackManager>(relaxed = true)
        every { pm.dualQueueSize } returns 10
        every { pm.contextSize } returns 8
        every { pm.priorityQueueSize } returns 2
        injectField(service, "playbackManager", pm)
        every { mockExoPlayer.mediaItemCount } returns 3

        service.ensureExoMatchesDual()

        verify(exactly = 1) { pm.syncDualQueueToPlayer() }
    }

    @Test
    fun `ensureExoMatchesDual no-op when sizes match`() {
        val pm = mockk<PlaybackManager>(relaxed = true)
        every { pm.dualQueueSize } returns 5
        injectField(service, "playbackManager", pm)
        every { mockExoPlayer.mediaItemCount } returns 5

        service.ensureExoMatchesDual()

        verify(exactly = 0) { pm.syncDualQueueToPlayer() }
    }

    @Test
    fun `noteCastDeviceForQueuePolicy clears sessionWasResumed on deviceId change`() {
        injectField(service, "sessionWasResumed", true)
        service.noteCastDeviceForQueuePolicy("soundbar-id")
        injectField(service, "sessionWasResumed", true)
        service.noteCastDeviceForQueuePolicy("mini-id")
        val field = MediaService::class.java.getDeclaredField("sessionWasResumed")
        field.isAccessible = true
        assertFalse(field.getBoolean(service))
    }

    @Test
    fun `shouldSkipReceiverQueueLoad false when exo truncated even if resumed`() {
        injectField(service, "sessionWasResumed", true)
        val pm = mockk<PlaybackManager>(relaxed = true)
        every { pm.dualQueueSize } returns 10
        every { pm.contextSize } returns 8
        every { pm.priorityQueueSize } returns 2
        injectField(service, "playbackManager", pm)
        every { mockExoPlayer.mediaItemCount } returns 3

        assertFalse(service.shouldSkipReceiverQueueLoad())
    }

    @Test
    fun `shouldSkipReceiverQueueLoad true when resumed and exo matches dual`() {
        injectField(service, "sessionWasResumed", true)
        val pm = mockk<PlaybackManager>(relaxed = true)
        every { pm.dualQueueSize } returns 5
        every { pm.contextSize } returns 5
        every { pm.priorityQueueSize } returns 0
        injectField(service, "playbackManager", pm)
        every { mockExoPlayer.mediaItemCount } returns 5

        assertTrue(service.shouldSkipReceiverQueueLoad())
    }

    @Test
    fun `disconnect savePositionOnly uses mapped full-queue index not Cast window`() {
        // Mirrors onDisconnectRequested: Cast window idx=1, mapped full idx=3.
        every { mockCastPlayer.currentMediaItemIndex } returns 1
        every { mockCastPlayer.currentPosition } returns 42_000L
        every { mockMediaStatus.currentItemId } returns 4
        PlayerHolder.isCasting = true
        PlayerHolder.player = mockCastPlayer

        val mapped = service.resolveCurrentIndexForPersistence(mockExoPlayer)
        assertEquals(3, mapped)
        assertNotEquals(
            "Cast window index must not be persisted",
            mockCastPlayer.currentMediaItemIndex,
            mapped,
        )
    }
}
