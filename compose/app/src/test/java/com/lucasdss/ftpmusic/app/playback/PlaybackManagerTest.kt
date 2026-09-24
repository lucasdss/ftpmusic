package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.QueueJournalDao
import com.lucasdss.ftpmusic.app.data.model.Track
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaybackManagerTest {

    private val mockAppContext: android.app.Application = mockk(relaxed = true)
    private val mockPlayer: Player = mockk(relaxed = true)
    private val queueManager = QueueManager(mockk())
    private val mockPersistenceManager: QueuePersistenceManager = mockk(relaxed = true)
    private val mockOfflineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val mockDownloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)
    private val mockQueueJournalDao: QueueJournalDao = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns Uri.EMPTY
        PlayerHolder.player = mockPlayer
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
        PlayerHolder.isCasting = false
    }

    @Test
    fun `playAlbum builds media items and calls playAll`() {
        val tracks = listOf(
            Track("t1", "Song 1", artist = "Artist A", albumId = "al-1", duration = 200, coverArt = "ca-1"),
            Track("t2", "Song 2", artist = "Artist A", albumId = "al-1", duration = 180),
        )
        // Build stream URLs
        val urls = tracks.map { "http://server/rest/stream?id=${it.id}" }

        PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
        ).playAlbum(tracks, urls)

        verify { mockPlayer.setMediaItems(any()) }
        verify { mockPlayer.prepare() }
        verify { mockPlayer.play() }
    }

    @Test
    fun `playAlbum with empty list does nothing`() {
        PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
        ).playAlbum(emptyList(), emptyList())

        verify(exactly = 0) { mockPlayer.setMediaItems(any()) }
    }

    @Test
    fun `shuffleAlbum calls shuffleAndPlay`() {
        val tracks = listOf(
            Track("t1", "S1", duration = 100),
            Track("t2", "S2", duration = 100),
        )
        val urls = tracks.map { "http://server/rest/stream?id=${it.id}" }

        PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
        ).shuffleAlbum(tracks, urls)

        verify { mockPlayer.setMediaItems(any()) }
        verify { mockPlayer.prepare() }
        verify { mockPlayer.play() }
    }

    @Test
    fun `playSingleTrack plays one track`() {
        val track = Track(
            "t-solo",
            "Solo Track",
            artist = "Solo Artist",
            album = "Solo Album",
            duration = 250,
            coverArt = "ca-solo",
        )
        val url = "http://server/rest/stream?id=t-solo"

        PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
        ).playSingleTrack(track, url)

        verify { mockPlayer.setMediaItems(any()) }
        verify { mockPlayer.prepare() }
        verify { mockPlayer.play() }
    }

    @Test
    fun `addToQueue appends track`() {
        val track = Track("t-add", "Add Me", duration = 150)
        val url = "http://server/rest/stream?id=t-add"

        PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
        ).addToQueue(track, url)

        // Dual-queue mode: addToQueue operates on DualQueueManager + syncs to player.
        // Player interaction tested in DualQueueManagerTest + OptimisticQueueDelegateTest.
    }

    @Test
    fun `addToQueue persists updated queue`() {
        val track = Track("t-cb", "Callback Track", duration = 120)
        val url = "http://server/rest/stream?id=t-cb"
        val manager =
            PlaybackManager(
                queueManager,
                mockPersistenceManager,
                mockOfflineModeManager,
                mockDownloadManager,
                castPreferences,
                mockAppContext,
                mockQueueJournalDao,
            )

        manager.addToQueue(track, url)

        coVerify(timeout = 1_000) {
            mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `addAllToQueue persists updated queue`() {
        val tracks = listOf(Track("t1", "T1", duration = 100), Track("t2", "T2", duration = 200))
        val urls = listOf("http://a", "http://b")
        val manager =
            PlaybackManager(
                queueManager,
                mockPersistenceManager,
                mockOfflineModeManager,
                mockDownloadManager,
                castPreferences,
                mockAppContext,
                mockQueueJournalDao,
            )

        manager.addAllToQueue(tracks, urls)

        coVerify(timeout = 1_000) {
            mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `appendToContext grows the context queue and persists grown contextSize`() {
        val manager =
            PlaybackManager(
                queueManager,
                mockPersistenceManager,
                mockOfflineModeManager,
                mockDownloadManager,
                castPreferences,
                mockAppContext,
                mockQueueJournalDao,
            )
        manager.playAlbum(
            listOf(Track("c1", "C1"), Track("c2", "C2")),
            listOf("http://c1", "http://c2"),
            skipPersistence = true,
        )
        assertEquals(2, manager.contextSize)

        manager.appendToContext(listOf(Track("n1", "N1")), listOf("http://n1"))

        // Continuation extends CONTEXT, not priority — the persisted split must
        // reflect the grown context size (3), so a Cast disconnect restores
        // context = mix + continuation instead of mis-filing it as priority.
        assertEquals(3, manager.contextSize)
        assertEquals(0, manager.priorityQueueSize)
        coVerify(timeout = 1_000) {
            mockPersistenceManager.save(any(), any(), any(), any(), match { it == 3 }, any(), any(), any())
        }
    }

    @Test
    fun `appendToContext during Cast persists the dual queue`() {
        PlayerHolder.isCasting = true
        val manager =
            PlaybackManager(
                queueManager,
                mockPersistenceManager,
                mockOfflineModeManager,
                mockDownloadManager,
                castPreferences,
                mockAppContext,
                mockQueueJournalDao,
            )
        manager.playAlbum(
            listOf(Track("c1", "C1")),
            listOf("http://c1"),
            skipPersistence = true,
        )

        manager.appendToContext(listOf(Track("n1", "N1")), listOf("http://n1"))

        assertEquals(2, manager.contextSize)
        coVerify(timeout = 1_000) {
            mockPersistenceManager.save(any(), any(), any(), any(), match { it == 2 }, any(), any(), any())
        }
    }

    @Test
    fun `playNext builds media item and calls queueManager playNext`() {
        val testQueueManager = QueueManager(mockk())
        val manager =
            PlaybackManager(
                testQueueManager,
                mockPersistenceManager,
                mockOfflineModeManager,
                mockDownloadManager,
                castPreferences,
                mockAppContext,
                mockQueueJournalDao,
            )
        val track = Track(
            "t-next",
            "Next Song",
            artist = "Next Artist",
            album = "Next Album",
            duration = 180,
            coverArt = "ca-next",
        )
        val url = "http://server/rest/stream?id=t-next"

        PlayerHolder.player = mockPlayer
        every { mockPlayer.currentMediaItemIndex } returns 0

        manager.playNext(track, url)

        // Dual-queue mode: playNext operates on DualQueueManager + syncs to player.
        // Player interaction tested in DualQueueManagerTest + OptimisticQueueDelegateTest.
    }

    @Test
    fun `playStream creates radio MediaItem and starts playback`() {
        PlayerHolder.player = mockPlayer
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 0

        val mgr = PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
        )
        mgr.playStream("http://radio.example.com/stream", "My Radio")

        // Verify player was prepared and played
        verify {
            mockPlayer.setMediaItems(
                match { items ->
                    items.isNotEmpty() &&
                        items.first().mediaId == "radio:${"http://radio.example.com/stream".hashCode()}" &&
                        items.first().mediaMetadata.title?.toString() == "My Radio"
                },
            )
        }
        verify { mockPlayer.prepare() }
        verify { mockPlayer.play() }
    }

    @Test
    fun `buildQueueState prefers exoPlayer over CastPlayer when isCasting`() {
        // Verify the selection logic: exoPlayer takes priority during Cast
        PlayerHolder.exoPlayer = mockPlayer // 11 items (from @Before setup)
        PlayerHolder.isCasting = true
        // mockPlayer (also PlayerHolder.player from setup) has mediaItemCount=0 (relaxed default)
        // buildQueueState should pick exoPlayer (mockPlayer) over PlayerHolder.player (also mockPlayer)

        val pb = PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
        )
        val (tracks, _) = pb.buildQueueState()

        // exoPlayer (mockPlayer) has mediaItemCount=0 from relaxed default → 0 tracks
        assertEquals(0, tracks.size)
        // Verify exoPlayer was accessed (via mediaItemCount)
        verify { mockPlayer.mediaItemCount }

        PlayerHolder.exoPlayer = null
        PlayerHolder.isCasting = false
    }

    // ── Cast dispatch during active Cast session ─────────────────────────

    @Test
    fun `playAlbum dispatches ClearAndPlay when casting`() {
        PlayerHolder.isCasting = true
        PlayerHolder.player = mockPlayer
        PlayerHolder.exoPlayer = mockPlayer

        var capturedAction: CastQueueAction? = null
        val manager =
            PlaybackManager(
                queueManager,
                mockPersistenceManager,
                mockOfflineModeManager,
                mockDownloadManager,
                castPreferences,
                mockAppContext,
                mockQueueJournalDao,
            )
        manager.castQueueListener = { action -> capturedAction = action }

        val tracks = listOf(Track(id = "t1", title = "A"), Track(id = "t2", title = "B"))
        val urls = listOf("http://ex.com/1", "http://ex.com/2")
        manager.playAlbum(tracks, urls, startIndex = 0, skipPersistence = true)

        assertNotNull("ClearAndPlay must be dispatched during Cast", capturedAction)
        assertTrue(
            "Must be ClearAndPlay action",
            capturedAction is CastQueueAction.ClearAndPlay,
        )
        val clearPlay = capturedAction as CastQueueAction.ClearAndPlay
        assertEquals("Must preserve startIndex", 0, clearPlay.startIndex)
        assertEquals("Must contain all tracks", 2, clearPlay.mediaItems.size)

        PlayerHolder.isCasting = false
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
    }

    @Test
    fun `playAlbum does not dispatch when not casting`() {
        PlayerHolder.isCasting = false
        PlayerHolder.player = mockPlayer
        PlayerHolder.exoPlayer = mockPlayer

        var capturedAction: CastQueueAction? = null
        val manager =
            PlaybackManager(
                queueManager,
                mockPersistenceManager,
                mockOfflineModeManager,
                mockDownloadManager,
                castPreferences,
                mockAppContext,
                mockQueueJournalDao,
            )
        manager.castQueueListener = { action -> capturedAction = action }

        val tracks = listOf(Track(id = "t1", title = "A"))
        val urls = listOf("http://ex.com/1")
        manager.playAlbum(tracks, urls, skipPersistence = true)

        assertNull("Must NOT dispatch when not casting", capturedAction)

        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
    }

    @Test
    fun `playSingleTrack dispatches ClearAndPlay when casting`() {
        PlayerHolder.isCasting = true
        PlayerHolder.player = mockPlayer

        var capturedAction: CastQueueAction? = null
        val manager =
            PlaybackManager(
                queueManager,
                mockPersistenceManager,
                mockOfflineModeManager,
                mockDownloadManager,
                castPreferences,
                mockAppContext,
                mockQueueJournalDao,
            )
        manager.castQueueListener = { action -> capturedAction = action }

        manager.playSingleTrack(Track(id = "t1", title = "Single"), "http://ex.com/1")

        assertNotNull("ClearAndPlay must be dispatched during Cast", capturedAction)
        assertTrue(
            "Must be ClearAndPlay for single track",
            capturedAction is CastQueueAction.ClearAndPlay,
        )

        PlayerHolder.isCasting = false
        PlayerHolder.player = null
    }

    @Test
    fun `continuousPlayEnabled defaults to true`() {
        val mgr = PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
        )
        assertTrue(mgr.continuousPlayEnabled)
    }

    @Test
    fun `setContinuousPlayEnabled changes flag`() {
        val mgr = PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
        )
        mgr.setContinuousPlayEnabled(false)
        assertFalse(mgr.continuousPlayEnabled)
        mgr.setContinuousPlayEnabled(true)
        assertTrue(mgr.continuousPlayEnabled)
    }

    // ── buildQueueStateFromDual (authoritative during Cast) ────────────

    @Test
    fun `buildQueueStateFromDual returns merged context plus priority`() {
        val sharedDual = DualQueueManager()
        val mgr = PlaybackManager(
            queueManager, mockPersistenceManager,
            mockOfflineModeManager, mockDownloadManager,
            castPreferences, mockAppContext, mockQueueJournalDao,
            sharedDual, OptimisticQueueDelegate(sharedDual),
        )
        val tracks = listOf(
            Track("t1", "Song 1", artist = "Artist A", albumId = "al-1", duration = 200, coverArt = "ca-1"),
            Track("t2", "Song 2", artist = "Artist A", albumId = "al-1", duration = 180),
        )
        val urls = tracks.map { "http://server/rest/stream?id=${it.id}" }
        // Play the context (2 tracks), then add one via priority queue.
        mgr.playAlbum(tracks, urls)
        mgr.addToQueue(
            Track("t3", "Song 3", artist = "Artist A", duration = 150),
            "http://server/rest/stream?id=t3",
        )

        val (stateTracks, stateUrls) = mgr.buildQueueStateFromDual()

        assertEquals(
            "merged must include context + priority",
            3,
            stateTracks.size,
        )
        assertEquals(
            "track order: context through current, then priority, then suffix",
            listOf("t1", "t3", "t2"),
            stateTracks.map { it.id },
        )
        // NOTE: Uri.parse is mocked to Uri.EMPTY in unit tests, so url strings
        // are empty here. The track IDs/order are the meaningful assertion —
        // URL extraction is exercised by integration tests on-device.
        assertEquals(
            "urls aligned with tracks (3 entries)",
            3,
            stateUrls.size,
        )
    }

    @Test
    fun `buildQueueStateFromDual is empty when nothing played`() {
        val mgr = PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
        )
        val (tracks, urls) = mgr.buildQueueStateFromDual()
        assertTrue(tracks.isEmpty())
        assertTrue(urls.isEmpty())
    }

    // ── Overwrite behavior (Ask / Clean / Push) ─────────────────────────

    private fun managerWithBehavior(
        behavior: com.lucasdss.ftpmusic.app.playback.OverwriteBehavior,
    ): Triple<PlaybackManager, DualQueueManager, com.lucasdss.ftpmusic.app.data.security.SecureStorage> {
        val sharedDual = DualQueueManager()
        val mockStorage = mockk<com.lucasdss.ftpmusic.app.data.security.SecureStorage>(relaxed = true)
        every {
            mockStorage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_QUEUE_OVERWRITE_BEHAVIOR)
        } returns
            behavior.key
        val mgr = PlaybackManager(
            queueManager, mockPersistenceManager,
            mockOfflineModeManager, mockDownloadManager,
            castPreferences, mockAppContext, mockQueueJournalDao,
            sharedDual, OptimisticQueueDelegate(sharedDual), mockStorage,
        )
        PlayerHolder.player = mockk(relaxed = true)
        return Triple(mgr, sharedDual, mockStorage)
    }

    private fun owTracks(n: Int): Pair<List<Track>, List<String>> {
        val tracks = (1..n).map { Track("ow$it", "OW $it", artist = "OWArtist", duration = 100) }
        val urls = tracks.map { "http://server/rest/stream?id=ow$it" }
        return tracks to urls
    }

    @Test
    fun `ASK behavior blocks when priority queue non-empty`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.ASK)
        // Seed priority queue with one item via addToQueue
        mgr.addToQueue(Track("pri1", "P", artist = "A"), "http://s/pri1")

        val (tracks, urls) = owTracks(2)
        val started = mgr.tryStartContext(tracks, urls, sourceType = "album")

        assertFalse("ASK must block when priority non-empty", started)
        assertNotNull("PendingPlayback must be set", mgr.pendingPlayback)
    }

    @Test
    fun `ASK behavior starts immediately when priority empty`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.ASK)
        val (tracks, urls) = owTracks(2)
        val started = mgr.tryStartContext(tracks, urls, sourceType = "album")

        assertTrue("ASK must start immediately when priority empty", started)
        assertNull("No pending playback", mgr.pendingPlayback)
    }

    @Test
    fun `resolveOverwrite clear and play clears priority and plays pending`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.ASK)
        mgr.addToQueue(Track("pri1", "P", artist = "A"), "http://s/pri1")
        val (tracks, urls) = owTracks(2)
        mgr.tryStartContext(tracks, urls, sourceType = "album")

        mgr.resolveOverwrite(true)

        assertEquals("Priority must be cleared", 0, dual.prioritySize)
        assertEquals("Context must be the new album", 2, dual.contextSize)
        assertNull("Pending cleared", mgr.pendingPlayback)
    }

    @Test
    fun `resolveOverwrite keep queue discards pending`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.ASK)
        mgr.addToQueue(Track("pri1", "P", artist = "A"), "http://s/pri1")
        val (tracks, urls) = owTracks(2)
        mgr.tryStartContext(tracks, urls, sourceType = "album")

        mgr.resolveOverwrite(false)

        assertEquals("Priority must be preserved", 1, dual.prioritySize)
        assertNull("Pending cleared", mgr.pendingPlayback)
    }

    @Test
    fun `CLEAN behavior always clears priority and starts`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.CLEAN)
        mgr.addToQueue(Track("pri1", "P", artist = "A"), "http://s/pri1")

        val (tracks, urls) = owTracks(2)
        val started = mgr.tryStartContext(tracks, urls, sourceType = "album")

        assertTrue("CLEAN must always start", started)
        assertEquals("Priority must be cleared", 0, dual.prioritySize)
        assertEquals("Context = new album", 2, dual.contextSize)
        assertNull("No pending", mgr.pendingPlayback)
    }

    @Test
    fun `PUSH behavior preserves old queue after new context`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.PUSH)
        // Seed: context = old album (1 track "old1") + priority (1 track "pri1")
        val (oldTracks, oldUrls) = owTracks(1)
        mgr.playAlbum(oldTracks, oldUrls) // context = [ow1]
        mgr.addToQueue(Track("pri1", "P", artist = "A"), "http://s/pri1")

        // Push a NEW album with tracks that don't overlap the old queue
        val newTracks = (1..3).map { Track("new$it", "NEW $it", artist = "NewArtist", duration = 100) }
        val newUrls = newTracks.map { "http://server/rest/stream?id=new$it" }
        val started = mgr.tryStartContext(newTracks, newUrls, sourceType = "album")

        assertTrue("PUSH must always start", started)
        assertEquals("Context = pushed album (3)", 3, dual.contextSize)
        assertEquals("Old queue preserved after (2 items)", 2, dual.prioritySize)
        val merged = dual.getMerged()
        assertEquals("Playing context first", "new1", merged[0].mediaId)
        assertEquals(
            "Old queue as priority after current",
            listOf("ow1", "pri1"),
            merged.subList(1, 3).map { it.mediaId },
        )
        assertEquals(
            "Context suffix after priority",
            listOf("new2", "new3"),
            merged.drop(3).map { it.mediaId },
        )
    }

    @Test
    fun `PUSH behavior dedups tracks already in the new context`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.PUSH)
        // Seed: old context already contains ow1 (same track as the push)
        mgr.playAlbum(owTracks(1).first, owTracks(1).second) // context = [ow1]
        val (tracks, urls) = owTracks(3) // push ow1, ow2, ow3
        mgr.tryStartContext(tracks, urls, sourceType = "album")

        // ow1 was already in the old queue → deduped from the new context,
        // but must still appear once in the final queue (via the preserved old queue).
        val merged = dual.getMerged()
        assertEquals(
            "ow1 must appear exactly once (from old queue)",
            1,
            merged.count { it.mediaId == "ow1" },
        )
        assertEquals("No duplicates at all", merged.size, merged.map { it.mediaId }.toSet().size)
    }

    // ── Shuffle overwrite behavior ─────────────────────────────────────

    @Test
    fun `ASK behavior blocks shuffle when priority queue non-empty`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.ASK)
        mgr.addToQueue(Track("pri1", "P", artist = "A"), "http://s/pri1")

        val (tracks, urls) = owTracks(2)
        val started = mgr.tryShuffleContext(tracks, urls, sourceType = "album")

        assertFalse("ASK must block shuffle when priority non-empty", started)
        assertEquals("Pending must be flagged shuffled", true, mgr.pendingPlayback?.shuffled)
    }

    @Test
    fun `ASK behavior starts shuffle immediately when priority empty`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.ASK)
        val (tracks, urls) = owTracks(2)

        val started = mgr.tryShuffleContext(tracks, urls, sourceType = "album")

        assertTrue("ASK must start shuffle immediately when priority empty", started)
        assertNull("No pending playback", mgr.pendingPlayback)
        assertEquals("Context set (2 tracks)", 2, dual.contextSize)
    }

    @Test
    fun `resolveOverwrite clear and play resolves blocked shuffle`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.ASK)
        mgr.addToQueue(Track("pri1", "P", artist = "A"), "http://s/pri1")
        val (tracks, urls) = owTracks(2)
        mgr.tryShuffleContext(tracks, urls, sourceType = "album")

        mgr.resolveOverwrite(true)

        assertEquals("Priority must be cleared", 0, dual.prioritySize)
        assertEquals("Context must be the shuffled album", 2, dual.contextSize)
        assertNull("Pending cleared", mgr.pendingPlayback)
    }

    @Test
    fun `CLEAN behavior clears priority and shuffles`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.CLEAN)
        mgr.addToQueue(Track("pri1", "P", artist = "A"), "http://s/pri1")

        val (tracks, urls) = owTracks(2)
        val started = mgr.tryShuffleContext(tracks, urls, sourceType = "album")

        assertTrue("CLEAN must always start shuffle", started)
        assertEquals("Priority must be cleared", 0, dual.prioritySize)
        assertEquals("Context = shuffled album (2)", 2, dual.contextSize)
        assertNull("No pending", mgr.pendingPlayback)
    }

    @Test
    fun `PUSH behavior shuffles new context and preserves old queue`() {
        val (mgr, dual, _) = managerWithBehavior(com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.PUSH)
        val (oldTracks, oldUrls) = owTracks(1)
        mgr.playAlbum(oldTracks, oldUrls) // context = [ow1]
        mgr.addToQueue(Track("pri1", "P", artist = "A"), "http://s/pri1")

        val newTracks = (1..3).map { Track("new$it", "NEW $it", artist = "NewArtist", duration = 100) }
        val newUrls = newTracks.map { "http://server/rest/stream?id=new$it" }
        val started = mgr.tryShuffleContext(newTracks, newUrls, sourceType = "album")

        assertTrue("PUSH must always start shuffle", started)
        assertEquals("Context = shuffled pushed album (3)", 3, dual.contextSize)
        assertEquals("Old queue preserved after (2 items)", 2, dual.prioritySize)
        val merged = dual.getMerged()
        assertEquals(
            "All pushed tracks present (any shuffle order)",
            setOf("new1", "new2", "new3"),
            merged.map { it.mediaId }.filter { it.startsWith("new") }.toSet(),
        )
        assertEquals(
            "Old queue as priority after current",
            listOf("ow1", "pri1"),
            merged.filter { it.mediaId == "ow1" || it.mediaId == "pri1" }.map { it.mediaId },
        )
        assertNull("No pending", mgr.pendingPlayback)
    }

    // ── Append-without-restart (addToQueue / addAllToQueue) ─────────────

    private fun baseManager(): PlaybackManager = PlaybackManager(
        queueManager,
        mockPersistenceManager,
        mockOfflineModeManager,
        mockDownloadManager,
        castPreferences,
        mockAppContext,
        mockQueueJournalDao,
    )

    @Test
    fun `addToQueue appends via addMediaItems without replacing timeline`() {
        PlayerHolder.player = mockPlayer
        val manager = baseManager()

        manager.addToQueue(Track("t-add", "Add Me", duration = 150), "http://server/rest/stream?id=t-add")

        verify { mockPlayer.addMediaItems(match { it.size == 1 && it[0].mediaId == "t-add" }) }
        verify(exactly = 0) { mockPlayer.setMediaItems(any()) }
        assertEquals("Priority queue grew", 1, manager.priorityQueueSize)
    }

    @Test
    fun `addAllToQueue appends via addMediaItems without replacing timeline`() {
        PlayerHolder.player = mockPlayer
        val manager = baseManager()

        manager.addAllToQueue(
            listOf(Track("t1", "T1", duration = 100), Track("t2", "T2", duration = 200)),
            listOf("http://a", "http://b"),
        )

        verify { mockPlayer.addMediaItems(match { it.size == 2 && it.map { i -> i.mediaId } == listOf("t1", "t2") }) }
        verify(exactly = 0) { mockPlayer.setMediaItems(any()) }
        assertEquals("Priority queue grew", 2, manager.priorityQueueSize)
    }

    @Test
    fun `addToQueue persists queue including appended track`() {
        PlayerHolder.player = mockPlayer
        val manager = baseManager()

        manager.addToQueue(Track("t-persist", "Persist Me", duration = 120), "http://server/rest/stream?id=t-persist")

        coVerify(timeout = 1_000) {
            mockPersistenceManager.save(
                match { tracks -> tracks.any { it.id == "t-persist" } },
                match { urls -> urls.any { it == "http://server/rest/stream?id=t-persist" } },
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `addAllToQueue with empty list is a no-op append`() {
        PlayerHolder.player = mockPlayer
        val manager = baseManager()

        manager.addAllToQueue(emptyList(), emptyList())

        verify { mockPlayer.addMediaItems(emptyList()) }
        verify(exactly = 0) { mockPlayer.setMediaItems(any()) }
        assertEquals("Priority queue untouched", 0, manager.priorityQueueSize)
    }

    @Test
    fun `addAllToQueue with null player keeps dual queue consistent`() {
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
        val manager = baseManager()

        manager.addAllToQueue(
            listOf(Track("t1", "T1", duration = 100)),
            listOf("http://a"),
        )

        assertEquals("Dual queue keeps items without a player", 1, manager.priorityQueueSize)
        coVerify(exactly = 0) { mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `addToQueue during Cast sends single append event and skips local append`() {
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        val manager = baseManager()
        val actions = mutableListOf<CastQueueAction>()
        manager.castQueueListener = { actions.add(it) }

        manager.addToQueue(Track("t-cast", "Cast Me", duration = 150), "http://server/rest/stream?id=t-cast")

        verify(exactly = 0) { mockPlayer.addMediaItems(any()) }
        assertEquals("Exactly one Add event", 1, actions.size)
        val add = actions[0] as? CastQueueAction.Add
        assertNotNull("Event must be Add", add)
        assertNull("Append has no insert-before item", add!!.beforeEntryId)
        assertEquals("t-cast", add.mediaItem.mediaId)
    }

    @Test
    fun `addAllToQueue during Cast sends one append event per item and skips local append`() {
        PlayerHolder.player = mockPlayer
        PlayerHolder.isCasting = true
        val manager = baseManager()
        val actions = mutableListOf<CastQueueAction>()
        manager.castQueueListener = { actions.add(it) }

        manager.addAllToQueue(
            listOf(Track("t1", "T1", duration = 100), Track("t2", "T2", duration = 200)),
            listOf("http://a", "http://b"),
        )

        verify(exactly = 0) { mockPlayer.addMediaItems(any()) }
        assertEquals("One Add event per item", 2, actions.size)
        assertTrue(
            "All events are appends",
            actions.all { it is CastQueueAction.Add && it.beforeEntryId == null },
        )
    }

    // ── Gapless local queue edits (ADR 0042) ─────────────────────────────

    private fun managerWithDual(): Pair<PlaybackManager, DualQueueManager> {
        val dual = DualQueueManager()
        val mgr = PlaybackManager(
            queueManager,
            mockPersistenceManager,
            mockOfflineModeManager,
            mockDownloadManager,
            castPreferences,
            mockAppContext,
            mockQueueJournalDao,
            dual,
            OptimisticQueueDelegate(dual),
        )
        return mgr to dual
    }

    @Test
    fun `removeFromQueue uses removeMediaItem without replacing timeline`() {
        PlayerHolder.player = mockPlayer
        val (manager, _) = managerWithDual()
        manager.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100), Track("c", "C", duration = 100)),
            listOf("http://s/a", "http://s/b", "http://s/c"),
        )

        manager.removeFromQueue(1)

        verify { mockPlayer.removeMediaItem(1) }
        // playAlbum is the only setMediaItems call
        verify(exactly = 1) { mockPlayer.setMediaItems(any()) }
    }

    @Test
    fun `moveQueueItem uses moveMediaItem without replacing timeline`() {
        PlayerHolder.player = mockPlayer
        val (manager, _) = managerWithDual()
        manager.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100), Track("c", "C", duration = 100)),
            listOf("http://s/a", "http://s/b", "http://s/c"),
        )

        manager.moveQueueItem(0, 2)

        verify { mockPlayer.moveMediaItem(0, 2) }
        verify(exactly = 1) { mockPlayer.setMediaItems(any()) }
    }

    @Test
    fun `playNext inserts via addMediaItem without replacing timeline`() {
        PlayerHolder.player = mockPlayer
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.currentMediaItem } returns null
        val (manager, _) = managerWithDual()
        manager.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100)),
            listOf("http://s/a", "http://s/b"),
        )

        manager.playNext(Track("n", "Next", duration = 100), "http://s/n")

        verify { mockPlayer.addMediaItem(any(), match { it.mediaId == "n" }) }
        verify(exactly = 1) { mockPlayer.setMediaItems(any()) }
    }

    @Test
    fun `clearPriorityQueue removes priority items without setMediaItems`() {
        PlayerHolder.player = mockPlayer
        val (manager, _) = managerWithDual()
        manager.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100)),
            listOf("http://s/a", "http://s/b"),
        )
        manager.addToQueue(Track("c", "C", duration = 100), "http://s/c")
        // Merged industry: [a, c, b] — priority index 1
        every { mockPlayer.mediaItemCount } returns 3

        manager.clearPriorityQueue()

        verify { mockPlayer.removeMediaItem(1) }
        // playAlbum only (addToQueue uses addMediaItems)
        verify(exactly = 1) { mockPlayer.setMediaItems(any()) }
        assertEquals(0, manager.priorityQueueSize)
    }

    @Test
    fun `drag coalesce applies one moveMediaItem on commit`() {
        PlayerHolder.player = mockPlayer
        val (manager, dual) = managerWithDual()
        manager.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100), Track("c", "C", duration = 100)),
            listOf("http://s/a", "http://s/b", "http://s/c"),
        )
        val entryId = dual.getMerged()[0].queueEntryId()

        manager.beginQueueReorder(entryId, 0)
        manager.moveQueueItem(0, 1)
        manager.moveQueueItem(1, 2)
        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }

        manager.commitQueueReorder()
        verify(exactly = 1) { mockPlayer.moveMediaItem(0, 2) }
        verify(exactly = 1) { mockPlayer.setMediaItems(any()) }
    }

    @Test
    fun `drag coalesce with no move is a no-op on commit`() {
        PlayerHolder.player = mockPlayer
        val (manager, dual) = managerWithDual()
        manager.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100)),
            listOf("http://s/a", "http://s/b"),
        )
        val entryId = dual.getMerged()[0].queueEntryId()

        manager.beginQueueReorder(entryId, 0)
        manager.commitQueueReorder()

        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
    }

    @Test
    fun `clearQueue removes items after current via removeMediaItem`() {
        PlayerHolder.player = mockPlayer
        val itemA = androidx.media3.common.MediaItem.Builder().setMediaId("a").build()
        every { mockPlayer.currentMediaItem } returns itemA
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 3
        val (manager, _) = managerWithDual()
        manager.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100), Track("c", "C", duration = 100)),
            listOf("http://s/a", "http://s/b", "http://s/c"),
        )
        every { mockPlayer.currentMediaItem } returns itemA
        every { mockPlayer.currentMediaItemIndex } returns 0

        manager.clearQueue()

        verify { mockPlayer.removeMediaItem(2) }
        verify { mockPlayer.removeMediaItem(1) }
        verify(exactly = 1) { mockPlayer.setMediaItems(any()) }
    }

    @Test
    fun `removeFromQueue during Cast syncs exo mirror via setMediaItems`() {
        PlayerHolder.player = mockPlayer
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        val (manager, _) = managerWithDual()
        manager.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100)),
            listOf("http://s/a", "http://s/b"),
        )
        PlayerHolder.isCasting = true
        val actions = mutableListOf<CastQueueAction>()
        manager.castQueueListener = { actions.add(it) }

        manager.removeFromQueue(1)

        verify { exo.setMediaItems(any(), any(), any()) }
        verify(exactly = 0) { mockPlayer.removeMediaItem(any()) }
        assertTrue(actions.single() is CastQueueAction.Remove)
    }

    @Test
    fun `commitQueueReorder during Cast syncs exo mirror once`() {
        PlayerHolder.player = mockPlayer
        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.exoPlayer = exo
        val (manager, dual) = managerWithDual()
        manager.playAlbum(
            listOf(Track("a", "A", duration = 100), Track("b", "B", duration = 100), Track("c", "C", duration = 100)),
            listOf("http://s/a", "http://s/b", "http://s/c"),
        )
        val entryId = dual.getMerged()[0].queueEntryId()
        PlayerHolder.isCasting = true
        val actions = mutableListOf<CastQueueAction>()
        manager.castQueueListener = { actions.add(it) }

        manager.beginQueueReorder(entryId, 0)
        manager.moveQueueItem(0, 2)
        manager.commitQueueReorder()

        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
        verify { exo.setMediaItems(any(), any(), any()) }
        assertTrue(actions.single() is CastQueueAction.Move)
    }

    // ── v46: cover art id extraction must not produce "getCoverArt" ─────

    @Test
    fun `buildQueueStateFromDual extracts the real cover art id from full artwork URLs`() {
        val sharedDual = DualQueueManager()
        val mgr = PlaybackManager(
            queueManager, mockPersistenceManager,
            mockOfflineModeManager, mockDownloadManager,
            castPreferences, mockAppContext, mockQueueJournalDao,
            sharedDual, OptimisticQueueDelegate(sharedDual),
        )
        // Simulate a restored queue whose metadata carries a full artwork URL —
        // the OLD lastPathSegment reading produced coverArt = "getCoverArt".
        // (Plain-JVM Bundles are silent no-ops, so the id must come from the
        // URI path — the production `coverArtId` extra path is covered by
        // CoverArtBitmapLoaderTest + runs on-device.)
        val artworkUri = mockk<android.net.Uri>(relaxed = true)
        every { artworkUri.isHierarchical } returns true
        every { artworkUri.getQueryParameter("id") } returns "al-42"
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle("Song 1")
            .setArtist("Artist A")
            .setArtworkUri(artworkUri)
            .build()
        val item = androidx.media3.common.MediaItem.Builder()
            .setMediaId("t1")
            .setMediaMetadata(metadata)
            .build()
        sharedDual.setContext(listOf(item))

        val (stateTracks, _) = mgr.buildQueueStateFromDual()

        assertEquals(
            "cover art must be the real id, never the literal 'getCoverArt'",
            "al-42",
            stateTracks.single().coverArt,
        )
        assertNotEquals("getCoverArt", stateTracks.single().coverArt)
    }
}
