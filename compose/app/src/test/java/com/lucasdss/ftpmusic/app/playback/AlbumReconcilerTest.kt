package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog
import com.lucasdss.ftpmusic.app.data.repository.AlbumPresence
import com.lucasdss.ftpmusic.app.data.repository.AlbumRepository
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * N11-3: the reconciler only purges on an authenticated `code=70`, and only
 * when every local-intent guard (download / star / dislike / pending unstar)
 * allows it.
 */
class AlbumReconcilerTest {

    private val albumRepository: AlbumRepository = mockk()
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val dualQueue: DualQueueManager = mockk(relaxed = true)
    private val playbackManager: PlaybackManager = mockk(relaxed = true)

    private fun reconciler() = AlbumReconciler(
        albumRepository = albumRepository,
        trackDao = trackDao,
        metadataDao = metadataDao,
        dualQueue = dualQueue,
        playbackManager = playbackManager,
    )

    @Before
    fun setUp() {
        SubsonicCredentials.username = "user"
        SubsonicCredentials.password = "pass"
        DiagnosticLog.clear()
        coEvery { metadataDao.getAlbumTracks(any()) } returns emptyList()
        coEvery { metadataDao.deleteAlbumsByIds(any()) } just runs
        coEvery { metadataDao.deleteOrphanedAlbumTracks() } returns 0
        coEvery { metadataDao.pruneAlbumLedger() } just runs
        coEvery { trackDao.reconcileSearchCorpusAgainstCatalog() } returns 0
        every { playbackManager.persistCurrentQueue() } just runs
    }

    @After
    fun tearDown() {
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
        DiagnosticLog.clear()
    }

    private fun albumIsAbsent() {
        coEvery { albumRepository.albumPresence(any(), any(), any()) } returns AlbumPresence.ABSENT
    }

    @Test
    fun `blank album id is a no-op`() = runTest {
        assertFalse(reconciler().reconcileAfterConfirmedMissing(""))
        coVerify(exactly = 0) { albumRepository.albumPresence(any(), any(), any()) }
    }

    @Test
    fun `missing credentials is a no-op`() = runTest {
        SubsonicCredentials.username = ""
        assertFalse(reconciler().reconcileAfterConfirmedMissing("a"))
        coVerify(exactly = 0) { albumRepository.albumPresence(any(), any(), any()) }
    }

    @Test
    fun `present album is never purged`() = runTest {
        coEvery { albumRepository.albumPresence(any(), any(), any()) } returns AlbumPresence.PRESENT
        assertFalse(reconciler().reconcileAfterConfirmedMissing("a"))
        coVerify(exactly = 0) { metadataDao.deleteAlbumsByIds(any()) }
    }

    @Test
    fun `unknown presence is never purged`() = runTest {
        coEvery { albumRepository.albumPresence(any(), any(), any()) } returns AlbumPresence.UNKNOWN
        assertFalse(reconciler().reconcileAfterConfirmedMissing("a"))
        coVerify(exactly = 0) { metadataDao.deleteAlbumsByIds(any()) }
    }

    @Test
    fun `a verification exception is never purged`() = runTest {
        coEvery { albumRepository.albumPresence(any(), any(), any()) } throws RuntimeException("boom")
        assertFalse(reconciler().reconcileAfterConfirmedMissing("a"))
        coVerify(exactly = 0) { metadataDao.deleteAlbumsByIds(any()) }
    }

    @Test
    fun `a pinned download blocks the purge`() = runTest {
        albumIsAbsent()
        coEvery { trackDao.countDownloadedTracksInAlbum("a") } returns 1
        assertFalse(reconciler().reconcileAfterConfirmedMissing("a"))
        coVerify(exactly = 0) { metadataDao.deleteAlbumsByIds(any()) }
    }

    @Test
    fun `starred disliked and pending unstar each block the purge`() = runTest {
        listOf(
            Triple(true, false, false),
            Triple(false, true, false),
            Triple(false, false, true),
        ).forEach { (starred, disliked, pending) ->
            val localRepo: AlbumRepository = mockk()
            coEvery { localRepo.albumPresence(any(), any(), any()) } returns AlbumPresence.ABSENT
            val localMetadata: CachedMetadataDao = mockk(relaxed = true)
            coEvery { localMetadata.isAlbumStarred(any()) } returns starred
            coEvery { localMetadata.isAlbumDisliked(any()) } returns disliked
            coEvery { localMetadata.isAlbumPendingUnstar(any()) } returns pending
            val r = AlbumReconciler(
                albumRepository = localRepo,
                trackDao = trackDao,
                metadataDao = localMetadata,
                dualQueue = dualQueue,
                playbackManager = playbackManager,
            )
            assertFalse(
                "starred=$starred disliked=$disliked pending=$pending",
                r.reconcileAfterConfirmedMissing("a"),
            )
            coVerify(exactly = 0) { localMetadata.deleteAlbumsByIds(any()) }
        }
    }

    @Test
    fun `a clean absent album is purged and its queue entries removed`() = runTest {
        albumIsAbsent()
        coEvery { trackDao.countDownloadedTracksInAlbum("a") } returns 0
        coEvery { metadataDao.isAlbumStarred("a") } returns false
        coEvery { metadataDao.isAlbumDisliked("a") } returns false
        coEvery { metadataDao.isAlbumPendingUnstar("a") } returns false
        coEvery { trackDao.countCachedTracksInAlbum("a") } returns 0
        coEvery { metadataDao.deleteOrphanedAlbumTracks() } returns 3
        coEvery { trackDao.reconcileSearchCorpusAgainstCatalog() } returns 2
        every { dualQueue.removeAllByAlbumId("a") } returns 4

        assertTrue(reconciler().reconcileAfterConfirmedMissing("a"))

        coVerify(exactly = 1) { metadataDao.deleteAlbumsByIds(listOf("a")) }
        coVerify(exactly = 1) { metadataDao.deleteOrphanedAlbumTracks() }
        coVerify(exactly = 1) { metadataDao.pruneAlbumLedger() }
        coVerify(exactly = 1) { trackDao.reconcileSearchCorpusAgainstCatalog() }
        verify(exactly = 1) { dualQueue.removeAllByAlbumId("a") }
        verify(exactly = 1) { playbackManager.persistCurrentQueue() }
    }

    @Test
    fun `no queue entries means the queue is not persisted`() = runTest {
        albumIsAbsent()
        coEvery { trackDao.countDownloadedTracksInAlbum("a") } returns 0
        coEvery { metadataDao.isAlbumStarred("a") } returns false
        coEvery { metadataDao.isAlbumDisliked("a") } returns false
        coEvery { metadataDao.isAlbumPendingUnstar("a") } returns false
        every { dualQueue.removeAllByAlbumId("a") } returns 0

        assertTrue(reconciler().reconcileAfterConfirmedMissing("a"))
        verify(exactly = 0) { playbackManager.persistCurrentQueue() }
    }

    @Test
    fun `one verification per cooldown window`() = runTest {
        albumIsAbsent()
        coEvery { trackDao.countDownloadedTracksInAlbum("a") } returns 0
        coEvery { metadataDao.isAlbumStarred("a") } returns false
        coEvery { metadataDao.isAlbumDisliked("a") } returns false
        coEvery { metadataDao.isAlbumPendingUnstar("a") } returns false
        val r = reconciler()
        assertTrue(r.reconcileAfterConfirmedMissing("a"))
        assertFalse(r.reconcileAfterConfirmedMissing("a"))
        coVerify(exactly = 1) { albumRepository.albumPresence(any(), any(), any()) }
    }
}
