package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Branch coverage for [FavoriteRepository.mirrorStar]: the offline-mode
 * short-circuit and the server-failure catch — the two branches the primary
 * playback-side test class does not reach.
 */
class FavoriteRepositoryBranchTest {

    private val api: SubsonicApi = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val radioFavoriteDao: RadioFavoriteDao = mockk(relaxed = true)

    private lateinit var repo: FavoriteRepository

    @Before
    fun setup() {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        every { offlineModeManager.isOfflineEnabled() } returns false
        coEvery { trackDao.isTrackStarred(any()) } returns true
        coEvery { trackDao.isTrackDisliked(any()) } returns true
        coEvery { metadataDao.isAlbumStarred(any()) } returns true
        coEvery { metadataDao.isAlbumDisliked(any()) } returns true
        coEvery { metadataDao.isArtistStarred(any()) } returns true
        coEvery { metadataDao.isArtistDisliked(any()) } returns true
        repo = FavoriteRepository(api, storage, offlineModeManager, trackDao, metadataDao, radioFavoriteDao)
    }

    @Test
    fun `starTrack skips server mirror when offline`() = runTest {
        every { offlineModeManager.isOfflineEnabled() } returns true
        repo.starTrack("t1")
        coVerify(exactly = 0) { api.star(any(), id = "t1") }
        coVerify { trackDao.setStarredAt("t1", any()) }
        coVerify { trackDao.setPendingUnstar("t1", null) }
    }

    @Test
    fun `server mirror failure is swallowed and local write kept`() = runTest {
        coEvery { api.star(any(), id = "t1") } throws RuntimeException("network")
        repo.starTrack("t1")
        // No exception propagates; local state was still written
        coVerify { trackDao.setStarredAt("t1", any()) }
    }

    @Test
    fun `likeTrack clears dislike then stars`() = runTest {
        repo.likeTrack("t1")
        coVerify { trackDao.setDisliked("t1", false, any()) }
        coVerify { trackDao.setStarredAt("t1", any()) }
        coVerify { api.star(any(), id = "t1") }
    }

    @Test
    fun `dislikeTrack clears star and mirrors unstar`() = runTest {
        repo.dislikeTrack("t1")
        coVerify { trackDao.clearStarAndMarkPendingUnstar("t1", any()) }
        coVerify { trackDao.setDisliked("t1", true, any()) }
        coVerify { api.unstar(any(), id = "t1") }
    }

    @Test
    fun `album and artist star flows mirror to server`() = runTest {
        repo.starAlbum("al-1")
        coVerify { metadataDao.ensureAlbumLedgerRow("al-1") }
        coVerify { api.star(any(), albumId = "al-1") }

        repo.starArtist("ar-1")
        coVerify { metadataDao.ensureArtistLedgerRow("ar-1") }
        coVerify { api.star(any(), artistId = "ar-1") }

        repo.dislikeAlbum("al-2")
        coVerify { api.unstar(any(), albumId = "al-2") }

        repo.dislikeArtist("ar-2")
        coVerify { api.unstar(any(), artistId = "ar-2") }
    }

    @Test
    fun `starTrack skips server when local write missed`() = runTest {
        coEvery { trackDao.isTrackStarred("t-miss") } returns false
        repo.starTrack("t-miss")
        coVerify { trackDao.ensureTrackRow("t-miss") }
        coVerify(exactly = 0) { api.star(any(), id = "t-miss") }
    }

    @Test
    fun `dislikeTrack skips server when local dislike miss`() = runTest {
        coEvery { trackDao.isTrackDisliked("t-miss") } returns false
        repo.dislikeTrack("t-miss")
        coVerify { trackDao.clearStarAndMarkPendingUnstar("t-miss", any()) }
        coVerify(exactly = 0) { api.unstar(any(), id = "t-miss") }
    }

    @Test
    fun `starAlbum and starArtist skip server when local write missed`() = runTest {
        coEvery { metadataDao.isAlbumStarred("al-miss") } returns false
        coEvery { metadataDao.isArtistStarred("ar-miss") } returns false
        repo.starAlbum("al-miss")
        repo.starArtist("ar-miss")
        coVerify(exactly = 0) { api.star(any(), albumId = "al-miss") }
        coVerify(exactly = 0) { api.star(any(), artistId = "ar-miss") }
    }

    @Test
    fun `dislikeAlbum and dislikeArtist skip server when local dislike miss`() = runTest {
        coEvery { metadataDao.isAlbumDisliked("al-miss") } returns false
        coEvery { metadataDao.isArtistDisliked("ar-miss") } returns false
        repo.dislikeAlbum("al-miss")
        repo.dislikeArtist("ar-miss")
        coVerify { metadataDao.clearAlbumStarAndMarkPendingUnstar("al-miss", any()) }
        coVerify { metadataDao.clearArtistStarAndMarkPendingUnstar("ar-miss", any()) }
        coVerify(exactly = 0) { api.unstar(any(), albumId = "al-miss") }
        coVerify(exactly = 0) { api.unstar(any(), artistId = "ar-miss") }
    }

    @Test
    fun `rateTrack writes local then mirrors setRating`() = runTest {
        repo.rateTrack("t1", 4)
        coVerify { trackDao.ensureTrackRow("t1") }
        coVerify { trackDao.setRating("t1", 4) }
        coVerify { api.setRating(any(), id = "t1", rating = 4) }
    }

    @Test
    fun `rateAlbum writes ledger rating then mirrors setRating`() = runTest {
        repo.rateAlbum("al-1", 3)
        coVerify { metadataDao.ensureAlbumLedgerRow("al-1") }
        coVerify { metadataDao.setAlbumRating("al-1", 3) }
        coVerify { api.setRating(any(), id = "al-1", rating = 3) }
    }

    @Test
    fun `rateTrack skips server when offline`() = runTest {
        every { offlineModeManager.isOfflineEnabled() } returns true
        repo.rateTrack("t1", 5)
        coVerify { trackDao.setRating("t1", 5) }
        coVerify(exactly = 0) { api.setRating(any(), id = any(), rating = any()) }
    }
}
