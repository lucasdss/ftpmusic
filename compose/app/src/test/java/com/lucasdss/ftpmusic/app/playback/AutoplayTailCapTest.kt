package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.QueueJournalDao
import com.lucasdss.ftpmusic.app.data.model.Track
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** CP-7: autoplay tail cap, prune, current-row protection, and Cast emission. */
class AutoplayTailCapTest {

    private val mockAppContext: android.app.Application = mockk(relaxed = true)
    private val mockPlayer: Player = mockk(relaxed = true)
    private val queueManager = QueueManager(mockk())
    private val mockPersistenceManager: QueuePersistenceManager = mockk(relaxed = true)
    private val mockOfflineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val mockDownloadManager: DownloadManager = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)
    private val mockQueueJournalDao: QueueJournalDao = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns Uri.EMPTY
        PlayerHolder.player = mockPlayer
        every { mockPlayer.mediaItemCount } returns 0
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.currentPosition } returns 0L
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
        PlayerHolder.isCasting = false
    }

    private fun manager(): PlaybackManager = PlaybackManager(
        queueManager,
        mockPersistenceManager,
        mockOfflineModeManager,
        mockDownloadManager,
        castPreferences,
        mockAppContext,
        mockQueueJournalDao,
    )

    private fun burst(prefix: String, count: Int): Pair<List<Track>, List<String>> {
        val tracks = (0 until count).map { Track("$prefix-$it", "Title $prefix-$it") }
        return tracks to tracks.map { "http://s/${it.id}" }
    }

    private fun appendBurst(mgr: PlaybackManager, prefix: String, count: Int = 10) {
        val (tracks, urls) = burst(prefix, count)
        mgr.appendToContext(tracks, urls, asAutoplay = true)
    }

    // ── pure helper ──────────────────────────────────────────────────────

    @Test
    fun `eviction plan evicts oldest autoplay rows only`() {
        val merged = listOf(
            item("ctx", autoplay = false),
            item("ap1", autoplay = true),
            item("ap2", autoplay = true),
            item("ap3", autoplay = true),
            item("ctx2", autoplay = false),
        )

        val evicted =
            autoplayEvictionIndices(merged, incoming = 2, protectedEntryId = 0, protectedMediaId = null, cap = 3)

        assertEquals(listOf(1, 2), evicted)
    }

    @Test
    fun `eviction plan never evicts protected entry id`() {
        val merged = listOf(
            item("ap1", autoplay = true, entryId = 11),
            item("ap2", autoplay = true, entryId = 12),
            item("ap3", autoplay = true, entryId = 13),
        )

        val evicted =
            autoplayEvictionIndices(merged, incoming = 1, protectedEntryId = 11, protectedMediaId = "ap1", cap = 3)

        assertEquals(listOf(1), evicted)
    }

    @Test
    fun `eviction plan falls back to media id for legacy rows`() {
        val merged = listOf(
            item("ap1", autoplay = true),
            item("ap2", autoplay = true),
            item("ap3", autoplay = true),
        )

        val evicted =
            autoplayEvictionIndices(merged, incoming = 1, protectedEntryId = 0, protectedMediaId = "ap1", cap = 3)

        assertEquals(listOf(1), evicted)
    }

    @Test
    fun `eviction plan prefers protected index over a duplicate media id`() {
        val merged = listOf(
            item("dup", autoplay = true),
            item("dup", autoplay = true),
            item("ap3", autoplay = true),
        )

        val evicted = autoplayEvictionIndices(
            merged,
            incoming = 1,
            protectedEntryId = 0,
            protectedMediaId = "dup",
            protectedIndex = 1,
            cap = 3,
        )

        assertEquals(listOf(0), evicted)
    }

    @Test
    fun `eviction plan is a no-op at or below the cap`() {
        val merged = List(50) { item("ap$it", autoplay = true) }

        assertTrue(
            autoplayEvictionIndices(merged, incoming = 0, protectedEntryId = 0, protectedMediaId = null).isEmpty(),
        )
        assertTrue(
            autoplayEvictionIndices(
                merged,
                incoming = 0,
                protectedEntryId = 0,
                protectedMediaId = null,
                cap = 50,
            ).isEmpty(),
        )
    }

    // ── append integration ───────────────────────────────────────────────

    @Test
    fun `five bursts stay at the cap and evict the oldest burst`() {
        val mgr = manager()
        mgr.playAlbum(listOf(Track("base", "Base")), listOf("http://base"), skipPersistence = true)

        repeat(5) { appendBurst(mgr, "b$it") }
        assertEquals(50, mgr.isAutoplayFlags().count { it })

        // A sixth burst must evict the 10 oldest autoplay rows, not grow to 60.
        appendBurst(mgr, "b5")

        assertEquals(50, mgr.isAutoplayFlags().count { it })
        assertEquals(51, mgr.dualQueueSize)
        val mediaIds = mgr.currentQueueMediaIds()
        assertTrue(mediaIds.contains("base"))
        assertTrue(mediaIds.containsAll((0 until 10).map { "b5-$it" }))
        assertFalse(mediaIds.containsAll((0 until 10).map { "b0-$it" }))
    }

    @Test
    fun `non autoplay rows are never evicted`() {
        val mgr = manager()
        mgr.playAlbum(
            listOf(Track("base", "Base"), Track("base2", "Base 2")),
            listOf("http://base", "http://base2"),
            skipPersistence = true,
        )
        mgr.playNext(Track("priority", "Priority"), "http://priority")
        repeat(5) { appendBurst(mgr, "b$it") }

        appendBurst(mgr, "b5")

        val mediaIds = mgr.currentQueueMediaIds()
        assertTrue(mediaIds.containsAll(listOf("base", "base2", "priority")))
        assertEquals(50, mgr.isAutoplayFlags().count { it })
    }

    @Test
    fun `currently playing autoplay row survives overflow`() {
        val mgr = manager()
        mgr.playAlbum(listOf(Track("base", "Base")), listOf("http://base"), skipPersistence = true)
        repeat(5) { appendBurst(mgr, "b$it") }

        // Jump back into the first burst (merged index 1..10 are autoplay).
        val protectedEntryId = mgr.entryIds()[5]
        val protectedMediaId = mgr.mergedMediaItems()[5].mediaId
        every { mockPlayer.currentMediaItem } returns
            item(protectedMediaId, autoplay = true, entryId = protectedEntryId)
        every { mockPlayer.currentMediaItemIndex } returns 5

        appendBurst(mgr, "b5")

        assertTrue(mgr.entryIds().contains(protectedEntryId))
        assertEquals(50, mgr.isAutoplayFlags().count { it })
    }

    @Test
    fun `casting emits AddAll then one RemoveAll and never ClearAndPlay`() {
        val mgr = manager()
        mgr.playAlbum(listOf(Track("base", "Base")), listOf("http://base"), skipPersistence = true)
        repeat(5) { appendBurst(mgr, "b$it") }

        val exo = mockk<Player>(relaxed = true)
        PlayerHolder.isCasting = true
        PlayerHolder.exoPlayer = exo
        every { mockPlayer.currentMediaItem } returns item("base", autoplay = false, entryId = mgr.entryIds().first())
        every { mockPlayer.currentPosition } returns 1_234L
        val actions = mutableListOf<CastQueueAction>()
        mgr.castQueueListener = { actions.add(it) }

        appendBurst(mgr, "b5")

        assertEquals(2, actions.size)
        val remove = actions[0] as CastQueueAction.RemoveAll
        assertEquals(10, remove.entryIds.size)
        val add = actions[1] as CastQueueAction.AddAll
        assertEquals(10, add.mediaItems.size)
        assertTrue(actions.none { it is CastQueueAction.ClearAndPlay })
    }

    @Test
    fun `casting below the cap emits only AddAll`() {
        val mgr = manager()
        mgr.playAlbum(listOf(Track("base", "Base")), listOf("http://base"), skipPersistence = true)
        PlayerHolder.isCasting = true
        PlayerHolder.exoPlayer = mockk(relaxed = true)
        every { mockPlayer.currentMediaItem } returns item("base", autoplay = false, entryId = mgr.entryIds().first())
        val actions = mutableListOf<CastQueueAction>()
        mgr.castQueueListener = { actions.add(it) }

        appendBurst(mgr, "b0")

        assertEquals(1, actions.size)
        assertTrue(actions.single() is CastQueueAction.AddAll)
    }

    @Test
    fun `local append over the cap removes the evicted player rows too`() {
        val mgr = manager()
        mgr.playAlbum(listOf(Track("base", "Base")), listOf("http://base"), skipPersistence = true)
        repeat(5) { appendBurst(mgr, "b$it") }
        every { mockPlayer.mediaItemCount } returns 51

        appendBurst(mgr, "b5")

        verify(atLeast = 10) { mockPlayer.removeMediaItem(any()) }
        verify { mockPlayer.addMediaItems(match { it.size == 10 }) }
    }

    private fun item(id: String, autoplay: Boolean, entryId: Int = 0): MediaItem {
        var built = MediaItem.Builder().setMediaId(id).setUri("http://$id").build()
        if (entryId > 0) built = built.withQueueEntryId(entryId)
        if (autoplay) built = built.withAutoplay(true)
        return built
    }
}
