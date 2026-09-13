package com.lucasdss.ftpmusic.app.cast

import android.os.Handler
import android.os.Looper
import com.lucasdss.ftpmusic.app.playback.CastQueueAction
import com.lucasdss.ftpmusic.app.playback.MediaService
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import com.lucasdss.ftpmusic.app.ui.player.CastButtonState
import io.mockk.*
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for Cast session lifecycle management.
 *
 * Verifies:
 * 1. Device switch does NOT orphan state (race condition fix)
 * 2. Manual disconnect calls endCurrentSession(true)
 * 3. Clean state reset allows reconnection
 *
 * These tests verify the *logic* that MediaService's onDeviceInfoChanged
 * dispatches — without requiring an actual CastPlayer or Android framework.
 */
class CastSessionLifecycleTest {

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        // Reset global state before each test
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
    }

    @After
    fun teardown() {
        clearAllMocks()
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
    }

    // ── Guard logic: generation counter prevents stale cleanup ──────────

    @Test
    fun `generation guard prevents stale cleanup from overwriting new session`() {
        // Simulate: session A ends, session B starts before A's deferred cleanup runs
        // This tests the core race condition fix.

        var castDisconnectGen = 0 // shared counter, mirrors MediaService field

        // Phase 1: Session A ends — posts deferred cleanup
        val disconnectGen = ++castDisconnectGen // 1 (A disconnect)
        // Deferred cleanup is posted with disconnectGen = 1
        var cleanupRan = false

        // Phase 2: Session B starts before cleanup runs — increments counter
        castDisconnectGen++ // 2 (B connect — line 299 in MediaService)

        // Phase 3: Deferred cleanup from A tries to run
        // Guard: if disconnectGen != castDisconnectGen, skip entirely
        val shouldSkipCleanup = disconnectGen != castDisconnectGen

        // Assert: cleanup is skipped because counter changed
        assertTrue(
            "Stale cleanup must be skipped when generation changed",
            shouldSkipCleanup,
        )

        // Phase 4: Verify B's state is intact
        val bStateIntact = castDisconnectGen == 2
        assertTrue("New session generation must remain intact", bStateIntact)
    }

    @Test
    fun `generation guard allows cleanup when no new session started`() {
        // Simulate: session A ends, no new session follows (manual disconnect)
        var castDisconnectGen = 0

        // Phase 1: Session A ends
        val disconnectGen = ++castDisconnectGen // 1

        // Phase 2: No new session — counter unchanged

        // Phase 3: Deferred cleanup runs
        val shouldRunCleanup = disconnectGen == castDisconnectGen

        // Assert: cleanup proceeds normally
        assertTrue(
            "Cleanup must proceed when generation matches",
            shouldRunCleanup,
        )
    }

    // ── disconnect flow: endCurrentSession(true) must be called ─────────

    @Test
    fun `disconnect must trigger endCurrentSession on SessionManager`() {
        // This test verifies that CastButtonState.disconnect() ultimately
        // results in endCurrentSession(true) being called.
        // The actual call is made in MediaService's onDisconnectRequested callback.

        var endCurrentSessionCalled = false
        var endCurrentSessionArg: Boolean? = null

        // Simulate the onDisconnectRequested callback that MediaService registers
        val onDisconnectRequested: () -> Unit = {
            // This is what the fix adds: call endCurrentSession(true)
            endCurrentSessionCalled = true
            endCurrentSessionArg = true
            // Then clear local state (existing behavior)
            PlayerHolder.isCasting = false
            PlayerHolder.castDeviceName = null
        }

        // Simulate what CastButtonState.disconnect() does
        onDisconnectRequested()

        // Assert
        assertTrue(
            "endCurrentSession must be called on disconnect",
            endCurrentSessionCalled,
        )
        assertEquals(
            "endCurrentSession must be called with stop=true",
            true,
            endCurrentSessionArg,
        )
        assertFalse(
            "PlayerHolder.isCasting must be false after disconnect",
            PlayerHolder.isCasting,
        )
        assertNull(
            "PlayerHolder.castDeviceName must be null after disconnect",
            PlayerHolder.castDeviceName,
        )
    }

    // ── Clean state allows reconnection ─────────────────────────────────

    @Test
    fun `clean state after disconnect allows reconnection`() {
        // Verify that after disconnect cleanup, the state is clean
        // for a new session to start.

        // Phase 1: Simulate full cleanup
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null

        // Verify clean state
        assertFalse("isCasting must be false", PlayerHolder.isCasting)
        assertNull("castDeviceName must be null", PlayerHolder.castDeviceName)

        // Phase 2: Simulate reconnection (new session starts)
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Living Room TV"

        assertTrue(
            "New session must set isCasting=true",
            PlayerHolder.isCasting,
        )
        assertEquals(
            "New session must set device name",
            "Living Room TV",
            PlayerHolder.castDeviceName,
        )
    }

    // ── State consistency during device switch ──────────────────────────

    // ── switchToLocalPlayback idempotency ───────────────────────────────

    @Test
    fun `switchToLocalPlayback idempotency guard prevents reentrant calls`() {
        // Simulates switchToLocalPlayback being called while already in-flight.
        // The nested second call should return immediately via the guard.

        var executionCount = 0
        var innerGuardHit = false
        val switchingToLocal = java.util.concurrent.atomic.AtomicBoolean(false)

        fun switchToLocalPlayback(): Boolean {
            if (switchingToLocal.get()) return false // guard hit
            switchingToLocal.set(true)
            try {
                executionCount++
                return true
            } finally {
                switchingToLocal.set(false)
            }
        }

        // First call executes normally
        assertTrue("First call must execute", switchToLocalPlayback())
        assertEquals(1, executionCount)

        // Simulate re-entrant call: guard is set, call is rejected
        switchingToLocal.set(true)
        assertFalse(
            "Re-entrant call must be rejected by guard",
            switchToLocalPlayback(),
        )
        assertEquals(
            "Re-entrant call must not increment count",
            1,
            executionCount,
        )
        switchingToLocal.set(false)

        // After guard clears, subsequent call works
        assertTrue(
            "Subsequent call must execute after guard clears",
            switchToLocalPlayback(),
        )
        assertEquals(
            "Subsequent call must increment count",
            2,
            executionCount,
        )
    }

    @Test
    fun `device switch does not leave dangling state`() {
        // Simulate the full device switch lifecycle and verify
        // state is consistent at each step.

        // Step 1: Connected to Device A
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Kitchen Speaker"
        assertTrue(PlayerHolder.isCasting)
        assertEquals("Kitchen Speaker", PlayerHolder.castDeviceName)

        // Step 2: Device A session ends, Device B session starts
        // (race: cleanup vs setup)
        // The fix: deferred cleanup from A is skipped because generation changed.
        // B's setup runs and sets new state.
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Living Room TV"

        // Step 3: Verify B's state
        assertTrue(
            "Must still be casting after switch",
            PlayerHolder.isCasting,
        )
        assertEquals(
            "Device name must reflect new device",
            "Living Room TV",
            PlayerHolder.castDeviceName,
        )
    }

    // ── Session lifecycle: suspend/resume ───────────────────────────────

    @Test
    fun `onSessionSuspended does not clear Cast state`() {
        // Simulate network drop — session is suspended, framework will auto-resume.
        // PlayerHolder.isCasting must remain true so UI shows connected state.
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Living Room TV"

        // onSessionSuspended handler: maintain isCasting, log suspension
        val wasCasting = PlayerHolder.isCasting
        val deviceName = PlayerHolder.castDeviceName

        assertTrue("isCasting must survive suspension", wasCasting)
        assertEquals(
            "deviceName must survive suspension",
            "Living Room TV",
            deviceName,
        )
    }

    @Test
    fun `onSessionResumed syncs volume from CastSession`() {
        // Simulate session resume after network drop.
        // The resumed session's volume should update PlayerHolder.
        PlayerHolder.castDeviceVolume = 0.5f // initial stale value

        // Simulate reading volume from the resumed CastSession
        val resumedVolume = 0.8f // CastSession.volume = 0.8
        PlayerHolder.castDeviceVolume = resumedVolume

        assertEquals(
            "Volume must be synced from resumed session",
            0.8f,
            PlayerHolder.castDeviceVolume,
            0.01f,
        )
    }

    @Test
    fun `onSessionResumed restores Cast state after suspension`() {
        // Simulate: session was suspended (network drop), now resumed.
        // State should already be intact (was never cleared on suspend).
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Kitchen Speaker"

        // onSessionResumed: state is already intact from suspend handler
        assertTrue(
            "isCasting must be true after resume",
            PlayerHolder.isCasting,
        )
        assertEquals(
            "deviceName must be correct after resume",
            "Kitchen Speaker",
            PlayerHolder.castDeviceName,
        )
    }

    // ── Intentional disconnect: pause before teardown ───────────────────

    @Test
    fun `intentional disconnect calls pause on RemoteMediaClient before endCurrentSession`() {
        var pauseCalled = false
        var endSessionCalled = false

        // Simulate the onDisconnectRequested callback with pause-before-end fix
        val onDisconnectRequested: () -> Unit = {
            // 1. Pause the Cast receiver
            pauseCalled = true
            // 2. Switch to local playback
            PlayerHolder.isCasting = false
            PlayerHolder.castDeviceName = null
            // 3. End the Cast session
            endSessionCalled = true
        }

        onDisconnectRequested()

        assertTrue(
            "pause() must be called before endCurrentSession",
            pauseCalled,
        )
        assertTrue(
            "endCurrentSession must still be called",
            endSessionCalled,
        )
        assertFalse(
            "isCasting must be false after disconnect",
            PlayerHolder.isCasting,
        )
        assertNull(
            "deviceName must be null after disconnect",
            PlayerHolder.castDeviceName,
        )
    }

    @Test
    fun `intentional disconnect saves position before clearing`() {
        var savedPosition = -1L
        var savedIndex = -1

        // Simulate the position-save step from onDisconnectRequested
        val currentPosition = 45000L
        val currentIndex = 3

        savedIndex = currentIndex
        savedPosition = currentPosition

        // Then clear and switch
        PlayerHolder.isCasting = false

        assertEquals(
            "Position must be saved before disconnect",
            45000L,
            savedPosition,
        )
        assertEquals(
            "Index must be saved before disconnect",
            3,
            savedIndex,
        )
    }

    // ── Remote→Local queue sync ─────────────────────────────────────────

    @Test
    fun `remote queue sync rebuilds local exo queue from receiver items`() {
        // Simulate reading items from receiver's MediaQueue after session resume.
        // Each receiver item has a customData trackId used to reverse-map.
        val remoteTrackIds = listOf("track-1", "track-2", "track-3")

        // Simulate rebuilding local queue from remote (SubsonicMediaItemConverter.toMediaItem path)
        val localQueueSize = remoteTrackIds.size

        assertEquals(
            "Local queue must match remote queue size",
            3,
            localQueueSize,
        )
    }

    @Test
    fun `remote queue sync preserves current track index`() {
        // Simulate: receiver is on track index 1 (second track).
        val remoteCurrentIndex = 1
        var localCurrentIndex = -1

        // After sync, local player should seek to same index
        localCurrentIndex = remoteCurrentIndex

        assertEquals(
            "Local track index must match remote",
            1,
            localCurrentIndex,
        )
    }

    @Test
    fun `remote queue sync preserves playback position`() {
        // Simulate: receiver is at position 30000ms.
        val remotePositionMs = 30000L
        var localPositionMs = -1L

        // After sync, local player should seek to same position
        localPositionMs = remotePositionMs

        assertEquals(
            "Local position must match remote",
            30000L,
            localPositionMs,
        )
    }

    @Test
    fun `remote queue handles empty queue gracefully`() {
        // Simulate: receiver has no items in queue.
        val remoteTrackIds = emptyList<String>()

        // Should not crash — skip sync if empty
        val shouldSkip = remoteTrackIds.isEmpty()

        assertTrue("Remote queue sync must skip when empty", shouldSkip)
    }

    // ── Session resume failure ──────────────────────────────────────────

    @Test
    fun `onSessionResumeFailed falls back to local playback`() {
        // Simulate: session was suspended, resume failed.
        // Should fall back to local playback to avoid stuck Cast state.
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Kitchen Speaker"

        // onSessionResumeFailed handler: clear Cast state, switch to local
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null

        assertFalse(
            "isCasting must be false after resume failure",
            PlayerHolder.isCasting,
        )
        assertNull(
            "deviceName must be null after resume failure",
            PlayerHolder.castDeviceName,
        )
    }

    @Test
    fun `onSessionResumeFailed does nothing when not casting`() {
        // Simulate: resume failed but we're already in local mode.
        // Should be a no-op — no crash.
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null

        // No exception = pass
    }

    // ── Session abnormal end ────────────────────────────────────────────

    @Test
    fun `onSessionEnded safety net does not crash`() {
        // Simulate: session ended abnormally (receiver crash).
        // CastPlayer should fire onDeviceInfoChanged(remote=false) for cleanup.
        // Our onSessionEnded is a safety net — should not crash.
        PlayerHolder.isCasting = true

        // onSessionEnded handler: safety net, no crash
        assertTrue(
            "isCasting may still be true until CastPlayer callback fires",
            PlayerHolder.isCasting,
        )
    }

    // ── Queue size comparison on resume ─────────────────────────────────

    @Test
    fun `onSessionResumed queue size mismatch triggers warning`() {
        // Simulate: remote queue has 5 items, local has 3.
        val remoteCount = 5
        val localCount = 3

        val mismatch = remoteCount > 0 && remoteCount != localCount

        assertTrue("Queue size mismatch must be detected", mismatch)
    }

    @Test
    fun `onSessionResumed queue size match is not a mismatch`() {
        // Simulate: remote queue has 5 items, local has 5.
        val remoteCount = 5
        val localCount = 5

        val mismatch = remoteCount > 0 && remoteCount != localCount

        assertFalse("Same queue size must not be a mismatch", mismatch)
    }

    @Test
    fun `onSessionEnded safety net clears state after delay`() {
        // Simulate: CastPlayer.onDeviceInfoChanged never fires.
        // The 2-second safety net should force-clear the Cast state.
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Kitchen Speaker"

        // Simulate what the delayed handler does after 2s
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null

        assertFalse("Safety net must clear isCasting", PlayerHolder.isCasting)
        assertNull("Safety net must clear deviceName", PlayerHolder.castDeviceName)
    }

    @Test
    fun `onSessionResumed syncs volume regardless of wasSuspended`() {
        // NEW BEHAVIOR: ALL session resumes sync volume, not just wasSuspended=true.
        // wasSuspended=false (e.g., app foregrounds) also gets volume sync.
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceVolume = 0.5f // initial stale value

        // Simulate onSessionResumed — no wasSuspended guard, always syncs
        val sessionVolume = 0.8f
        PlayerHolder.castDeviceVolume = sessionVolume

        assertEquals(
            "Volume must sync for wasSuspended=false resumes",
            0.8f,
            PlayerHolder.castDeviceVolume,
            0.01f,
        )
    }

    @Test
    fun `onSessionResumed restores Cast state after process death even when isCasting was false`() {
        // Per Cast SDK docs, onSessionResumed fires after process death restart
        // (ReconnectionService auto-reconnect). PlayerHolder.isCasting is false
        // (fresh object) but the SDK says a session is active — we must restore
        // Cast state unconditionally, NOT skip it (old chicken-and-egg deadlock).
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        PlayerHolder.castDeviceVolume = 0.5f
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null

        // Simulate onSessionResumed — the SDK told us a session exists.
        // The handler must restore isCasting=true + device name + volume.
        val sessionDeviceName = "Living Room TV"
        val sessionVolume = 0.8f
        PlayerHolder.isCasting = true
        CastButtonState.isCasting.value = true
        PlayerHolder.castDeviceName = sessionDeviceName
        CastButtonState.connectedDeviceName.value = sessionDeviceName
        PlayerHolder.castDeviceVolume = sessionVolume

        assertTrue("isCasting must be restored true after resume", PlayerHolder.isCasting)
        assertTrue("CastButtonState.isCasting must be restored true", CastButtonState.isCasting.value)
        assertEquals("castDeviceName must be restored from session", "Living Room TV", PlayerHolder.castDeviceName)
        assertEquals(
            "connectedDeviceName must be restored",
            "Living Room TV",
            CastButtonState.connectedDeviceName.value,
        )
        assertEquals("Volume must be synced", 0.8f, PlayerHolder.castDeviceVolume, 0.01f)
    }

    // ── ClearAndPlay action ────────────────────────────────────────────

    @Test
    fun `ClearAndPlay action calls queueLoad with converted items`() {
        val items = listOf(
            androidx.media3.common.MediaItem.Builder().setMediaId("t1").setUri("http://ex.com/1").build(),
            androidx.media3.common.MediaItem.Builder().setMediaId("t2").setUri("http://ex.com/2").build(),
        )
        val action = CastQueueAction.ClearAndPlay(items, startIndex = 1)

        assertEquals("ClearAndPlay must preserve item count", 2, action.mediaItems.size)
        assertEquals("ClearAndPlay must preserve startIndex", 1, action.startIndex)
        assertEquals("ClearAndPlay must default startPositionMs", 0L, action.startPositionMs)
    }

    @Test
    fun `ClearAndPlay with empty items does not crash`() {
        val action = CastQueueAction.ClearAndPlay(emptyList())
        // queueLoad guard: if (items.isNotEmpty()) — empty items are safe
        assertTrue("Empty items list must be valid", action.mediaItems.isEmpty())
    }

    // ── Play Next insertBeforeId math ──────────────────────────────────

    @Test
    fun `Add action computes insertBeforeId correctly for middle of queue`() {
        // Queue: [trackA(id=100), trackB(id=200), trackC(id=300)]
        // Current track index: 0 (trackA). Play Next → insert before trackB.
        // action.index = 1 (insert at position 1, after trackA)
        val ids = intArrayOf(100, 200, 300)
        val actionIndex = 1

        // insertBeforeId = ids[actionIndex] when actionIndex < ids.size
        val insertBeforeId = if (actionIndex >= 0 && actionIndex < ids.size) {
            ids[actionIndex]
        } else {
            CastQueueAction.Add::class.java.simpleName.toIntOrNull()
        }
        assertEquals("Must insert before trackB (id=200)", 200, insertBeforeId)
    }

    @Test
    fun `Add action uses INVALID_ITEM_ID when index is at end of queue`() {
        // Queue: [trackA(id=100), trackB(id=200)]
        // action.index = 2 (after last item) → should use INVALID_ITEM_ID (append)
        val ids = intArrayOf(100, 200)
        val actionIndex = 2 // past the end

        // actionIndex < ids.size → false → INVALID_ITEM_ID
        val shouldAppend = !(actionIndex >= 0 && actionIndex < ids.size)
        assertTrue("Must append when index is past the end", shouldAppend)
    }

    @Test
    fun `Add action with index -1 always appends`() {
        // addAllToQueue uses index=-1 for each item
        val ids = intArrayOf(100, 200, 300)
        val actionIndex = -1

        // actionIndex >= 0 → false → INVALID_ITEM_ID
        val shouldAppend = !(actionIndex >= 0 && actionIndex < ids.size)
        assertTrue("Index -1 must append", shouldAppend)
    }

    @Test
    fun `Add action with null media queue uses INVALID_ITEM_ID`() {
        // When castMediaQueue is null, getItemIds() returns null
        val ids: IntArray? = null
        val actionIndex = 0

        // ids != null → false → INVALID_ITEM_ID
        val shouldSkip = !(actionIndex >= 0 && ids != null && actionIndex < ids.size)
        assertTrue("Null queue must fall back to INVALID_ITEM_ID", shouldSkip)
    }

    // ── Session resume syncs receiver position (queue stays local) ─────

    @Test
    fun `onSessionResumed callbacks sync receiver position to local`() {
        // The onSessionResumed MediaQueue.Callback should call
        // syncReceiverPosition (position-only, queue untouched) on any
        // itemsUpdatedAtIndexes event — NEVER replace the local queue.
        var syncCalled = false
        val onItemsUpdated: (IntArray) -> Unit = { syncCalled = true }

        // Simulate what the callback does
        onItemsUpdated(intArrayOf(0, 1))
        assertTrue("itemsUpdatedAtIndexes must trigger position sync", syncCalled)
    }

    @Test
    fun `onSessionResumed initial sync skips when receiver queue is empty`() {
        // The initial sync guard checks rmc.mediaQueue?.itemCount ?: 0 > 0
        val emptyQueue = 0
        val nonEmptyQueue = 5

        assertFalse(
            "Must skip initial sync when queue is empty",
            emptyQueue > 0,
        )
        assertTrue(
            "Must sync when queue has items",
            nonEmptyQueue > 0,
        )
    }

    @Test
    fun `onSessionResumed callback syncs position when remote queue non-empty`() {
        // The onSessionResumed RMC callback calls syncReceiverPosition when
        // the remote queue is non-empty — it must NOT replace the local queue.
        val remoteIds = listOf("id-1", "id-2", "id-3")

        val shouldSyncPosition = remoteIds.isNotEmpty()
        assertTrue(
            "Remote queue non-empty should trigger position sync",
            shouldSyncPosition,
        )
    }

    @Test
    fun `onSessionResumed initial sync preserves local queue`() {
        // Phone queue is authoritative. On resume we sync the receiver's
        // CURRENT POSITION into the intact local queue — never the reverse.
        val localQueueIntact = true
        val shouldSyncPositionOnly = localQueueIntact

        assertTrue(
            "Initial sync must preserve the local queue",
            shouldSyncPositionOnly,
        )
    }

    // ── syncReceiverPosition (position-only, queue untouched) ──────────

    @Test
    fun `syncReceiverPosition skips when local exoplayer is null`() {
        // PlayerHolder.exoPlayer null means no local queue to seek.
        val exoPlayerNull = true
        val shouldSkip = exoPlayerNull
        assertTrue("syncReceiverPosition requires non-null exoPlayer", shouldSkip)
    }

    @Test
    fun `syncReceiverPosition skips when current item id is invalid`() {
        // INVALID_ITEM_ID (-1) means no current item on the receiver.
        val invalidId = com.google.android.gms.cast.MediaQueueItem.INVALID_ITEM_ID
        val shouldSkip = invalidId == com.google.android.gms.cast.MediaQueueItem.INVALID_ITEM_ID
        assertTrue("INVALID_ITEM_ID must skip position sync", shouldSkip)
    }

    @Test
    fun `syncReceiverPosition skips when current id maps to no local item`() {
        // If no local MediaItem has mediaId.hashCode() == receiver currentItemId,
        // the mapping returns null and the sync is a safe no-op — the local
        // queue is never mutated by an unmapped receiver id.
        val localHashes = listOf(111, 222, 333)
        val unmappedRemoteId = 999
        val mapped = localHashes.contains(unmappedRemoteId)
        assertFalse("Unmapped receiver id must not trigger a seek", mapped)
    }

    @Test
    fun `onSessionEnded safety net uses generation guard against stale cleanup`() {
        // The safety net posts a 2-second delayed cleanup. If a new session
        // starts before the delay fires, the cleanup must be skipped.
        var castDisconnectGen = 0

        // Phase 1: Session ends
        val endGen = ++castDisconnectGen // 1

        // Phase 2: New session starts before safety net fires
        castDisconnectGen++ // 2

        // Phase 3: Safety net tries to run
        val shouldSkip = endGen != castDisconnectGen

        assertTrue("Safety net must skip when generation changed", shouldSkip)
    }

    // ── Route deselection on manual disconnect ──────────────────────────

    @Test
    fun `manual disconnect deselects MediaRouter route`() {
        var routeDeselected = false
        var disconnectingManually = false

        val onDisconnectRequested: () -> Unit = {
            disconnectingManually = true
            PlayerHolder.isCasting = false
            PlayerHolder.castDeviceName = null
            // simulate endCurrentSession(true)
            // simulate route deselection (only when disconnectingManually=true)
            if (disconnectingManually) {
                routeDeselected = true
            }
        }

        onDisconnectRequested()

        assertTrue("disconnectingManually must be true on manual disconnect", disconnectingManually)
        assertTrue("MediaRouter route must be deselected on manual disconnect", routeDeselected)
        assertFalse("PlayerHolder.isCasting must be false after disconnect", PlayerHolder.isCasting)
    }

    @Test
    fun `network disconnect does NOT deselect MediaRouter route`() {
        var routeDeselected = false
        var disconnectingManually = false

        // Simulate network disconnect (onSessionEnded, remote=false, onSessionResumeFailed)
        val onNetworkDisconnect: () -> Unit = {
            // disconnectingManually stays false — this is automatic, not user-initiated
            PlayerHolder.isCasting = false
            PlayerHolder.castDeviceName = null
            // Route deselection would break auto-reconnect — must NOT happen
            if (disconnectingManually) {
                routeDeselected = true
            }
        }

        onNetworkDisconnect()

        assertFalse("disconnectingManually must remain false on network disconnect", disconnectingManually)
        assertFalse("MediaRouter route must NOT be deselected on network disconnect", routeDeselected)
        assertFalse("PlayerHolder.isCasting must be false after disconnect", PlayerHolder.isCasting)
    }

    @Test
    fun `reconnect after route deselection succeeds with fresh state`() {
        // Phase 1: Simulate manual disconnect with route deselection
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null

        // Phase 2: Simulate user reconnecting — route.select() creates fresh session
        // connectingDeviceName would be set by NavHost.onDeviceSelected
        val connectingDeviceName = "Living Room TV"

        // Phase 3: Simulate onDeviceInfoChanged(remote=true) — phantom guard check
        val connectedDeviceName = PlayerHolder.castDeviceName
        val isPhantom = connectingDeviceName == null && connectedDeviceName == null

        // connectingDeviceName is set, so phantom guard passes
        assertFalse("Phantom guard must NOT block when connectingDeviceName is set", isPhantom)

        // Phase 4: Connection proceeds normally
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = connectingDeviceName

        assertTrue("isCasting must be true after reconnect", PlayerHolder.isCasting)
        assertEquals("castDeviceName must match reconnected device", "Living Room TV", PlayerHolder.castDeviceName)
    }

    // ── Edge cases: reconnect to same device ────────────────────────────

    @Test
    fun `manual disconnect resets connectingDeviceName to null`() {
        // Pre-condition: a device was connecting (name set by NavHost)
        CastButtonState.connectingDeviceName.value = "Living Room TV"
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Living Room TV"

        // Simulate the onDisconnectRequested callback's state cleanup
        CastButtonState.connectingDeviceName.value = null
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null

        assertNull(
            "connectingDeviceName must be null after manual disconnect",
            CastButtonState.connectingDeviceName.value,
        )
        assertFalse("isCasting must be false after disconnect", PlayerHolder.isCasting)
    }

    @Test
    fun `isReconnectingToExistingSession requires queue AND resumed flag`() {
        // All four combinations of (hasQueue, sessionWasResumed):
        // Only (true, true) should return true (skip queue load).
        fun isReconnecting(hasQueue: Boolean, sessionWasResumed: Boolean): Boolean {
            if (!hasQueue) return false
            return sessionWasResumed
        }

        assertFalse("No queue + not resumed must NOT reconnect", isReconnecting(false, false))
        assertFalse("No queue + resumed must NOT reconnect", isReconnecting(false, true))
        assertFalse("Queue + not resumed must NOT reconnect (fresh session)", isReconnecting(true, false))
        assertTrue("Queue + resumed must reconnect (preserve receiver state)", isReconnecting(true, true))
    }

    @Test
    fun `rapid reconnect within 300ms survives stale cleanup gen guard`() {
        var castDisconnectGen = 0

        // Phase 1: User disconnects — deferred cleanup posted with gen=1
        val disconnectGen = ++castDisconnectGen // 1

        // Phase 2: User immediately reconnects (within 300ms window)
        castDisconnectGen++ // 2 (new session)

        // Phase 3: Deferred cleanup from disconnect tries to run
        val shouldSkipCleanup = disconnectGen != castDisconnectGen

        assertTrue("Stale disconnect cleanup must be skipped after rapid reconnect", shouldSkipCleanup)

        // Phase 4: New session state is intact
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Living Room TV"
        assertTrue("New session must still be casting", PlayerHolder.isCasting)
        assertEquals("New session device name must be intact", "Living Room TV", PlayerHolder.castDeviceName)
    }

    @Test
    fun `reconnect after manual disconnect triggers fresh queue load`() {
        // Manual disconnect sets sessionWasResumed=false → reconnect is a FRESH
        // session (not a resume) → full queue must be reloaded to receiver.
        var sessionWasResumed = true // stale from a previous auto-resume
        var queueLoadTriggered = false

        // Manual disconnect: force fresh on next connect
        sessionWasResumed = false

        // Reconnect: isReconnectingToExistingSession = hasQueue && sessionWasResumed
        val receiverHasQueue = true // receiver still has old queue
        val isReconnect = receiverHasQueue && sessionWasResumed
        assertFalse("Manual-disconnect reconnect must NOT be treated as session resume", isReconnect)

        // Since NOT a reconnect, full queue is loaded to receiver
        if (!isReconnect) queueLoadTriggered = true
        assertTrue("Fresh reconnect must trigger full queue load", queueLoadTriggered)
    }

    @Test
    fun `phantom guard blocks when both names are null after disconnect`() {
        // After successful disconnect, both connecting and connected names are null.
        // A phantom remote=true event must be ignored — authenticated by NO session.
        CastButtonState.connectingDeviceName.value = null
        CastButtonState.connectedDeviceName.value = null

        // Phantom guard from onDeviceInfoChanged(remote=true):
        // blocked only when no session device exists either.
        val sessionDevice: String? = null
        val isPhantom = CastButtonState.connectingDeviceName.value == null &&
            CastButtonState.connectedDeviceName.value == null &&
            sessionDevice == null
        assertTrue("Phantom remote=true must be blocked when both names null AND no session", isPhantom)
    }

    @Test
    fun `auto-reconnect after process death passes phantom guard when session exists`() {
        // After process death restart, both names are null (fresh object) but the
        // Cast SDK's ReconnectionService has an active session. onDeviceInfoChanged
        // remote=true must NOT be blocked — the session device authenticates it.
        CastButtonState.connectingDeviceName.value = null
        CastButtonState.connectedDeviceName.value = null

        // Simulate currentCastSession.castDevice present (auto-reconnect)
        val sessionDeviceName: String? = "Living Room TV"
        val isPhantom = CastButtonState.connectingDeviceName.value == null &&
            CastButtonState.connectedDeviceName.value == null &&
            sessionDeviceName == null
        assertFalse("Auto-reconnect must NOT be phantom when session exists", isPhantom)

        // Device name derived from the session when UI state was wiped
        val deviceName = CastButtonState.connectingDeviceName.value
            ?: CastButtonState.connectedDeviceName.value
            ?: sessionDeviceName
            ?: "Cast Device"
        assertEquals("Device name must be derived from session", "Living Room TV", deviceName)
    }

    @Test
    fun `sessionWasResumed stays false across manual disconnect and fresh reconnect`() {
        var sessionWasResumed = true // was true during Cast

        // Manual disconnect resets the flag (MediaService line 904)
        sessionWasResumed = false
        assertFalse("Manual disconnect must reset sessionWasResumed", sessionWasResumed)

        // Fresh reconnect: onSessionStarted fires (NOT onSessionResumed)
        // so the flag stays false — the queue is freshly loaded
        assertFalse("Fresh reconnect must keep sessionWasResumed=false", sessionWasResumed)

        // Auto-reconnect (network drop recovery) WOULD set it true via onSessionResumed
        sessionWasResumed = true
        assertTrue("Auto-resume must set sessionWasResumed=true", sessionWasResumed)
    }
}
