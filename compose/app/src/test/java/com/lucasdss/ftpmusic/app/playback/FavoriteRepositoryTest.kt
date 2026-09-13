package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Real FavoriteRepository tests (not mocked): like == star on Navidrome,
 * dislike is local-only, and both are mutually exclusive.
 */
class FavoriteRepositoryTest {

    private fun repo(
        api: SubsonicApi = mockk(relaxed = true),
        storage: SecureStorage? = null,
        trackDao: TrackDao = mockk(relaxed = true),
        metadataDao: com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao = mockk(relaxed = true),
        radioFavoriteDao: com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao = mockk(relaxed = true),
    ): FavoriteRepository {
        val s = storage ?: mockk<SecureStorage>(relaxed = true).also { m ->
            every { m.get(SecureStorage.KEY_USERNAME) } returns "user"
            every { m.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        }
        return FavoriteRepository(
            api,
            s,
            mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
            trackDao,
            metadataDao,
            radioFavoriteDao,
        )
    }

    @Test
    fun `likeTrack clears dislike then stars`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        val r = repo(api = api, trackDao = trackDao)

        r.likeTrack("t1")

        // Local-first: dislike cleared and star written locally BEFORE the server call
        coVerifyOrder {
            trackDao.setDisliked("t1", false)
            trackDao.setStarredAt("t1", any())
            api.star(any(), id = "t1")
        }
    }

    @Test
    fun `likeTrack keeps local star when server call fails (local-first)`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.star(any(), id = "t1") } throws RuntimeException("offline")
        val trackDao = mockk<TrackDao>(relaxed = true)
        val r = repo(api = api, trackDao = trackDao)

        r.likeTrack("t1")

        // Local write persists even though the server mirror failed
        coVerify { trackDao.setStarredAt("t1", any()) }
        coVerify { trackDao.setDisliked("t1", false) }
    }

    // ── v44: pending-unstar markers (local intent vs server mirror) ──────

    @Test
    fun `unstarTrack records the pending-unstar marker`() = runTest {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val r = repo(trackDao = trackDao)

        r.unstarTrack("t1")

        coVerifyOrder {
            trackDao.setStarredAt("t1", null)
            trackDao.setPendingUnstar("t1", any())
        }
    }

    @Test
    fun `starTrack clears any pending-unstar marker`() = runTest {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val r = repo(trackDao = trackDao)

        r.starTrack("t1")

        coVerify { trackDao.setPendingUnstar("t1", null) }
    }

    @Test
    fun `dislikeTrack records the pending-unstar marker`() = runTest {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val r = repo(trackDao = trackDao)

        r.dislikeTrack("t1")

        coVerify { trackDao.setPendingUnstar("t1", any()) }
    }

    @Test
    fun `album and artist star and unstar manage pending markers`() = runTest {
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(metadataDao = metadataDao)

        r.unstarAlbum("al-1")
        r.starAlbum("al-1")
        r.unstarArtist("ar-1")
        r.starArtist("ar-1")

        coVerify { metadataDao.setAlbumPendingUnstar("al-1", any()) }
        coVerify { metadataDao.setAlbumPendingUnstar("al-1", null) }
        coVerify { metadataDao.setArtistPendingUnstar("ar-1", any()) }
        coVerify { metadataDao.setArtistPendingUnstar("ar-1", null) }
    }

    @Test
    fun `album and artist dislike record pending markers`() = runTest {
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(metadataDao = metadataDao)

        r.dislikeAlbum("al-1")
        r.dislikeArtist("ar-1")

        coVerify { metadataDao.setAlbumPendingUnstar("al-1", any()) }
        coVerify { metadataDao.setArtistPendingUnstar("ar-1", any()) }
    }

    @Test
    fun `unlikeTrack unstars`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        val r = repo(api = api, trackDao = trackDao)

        r.unlikeTrack("t1")

        coVerify { api.unstar(any(), id = "t1") }
        coVerify { trackDao.setStarredAt("t1", null) }
    }

    @Test
    fun `dislikeTrack clears star locally even when server unstar fails`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.unstar(any(), id = "t1") } throws RuntimeException("offline")
        val trackDao = mockk<TrackDao>(relaxed = true)
        val r = repo(api = api, trackDao = trackDao)

        r.dislikeTrack("t1")

        coVerify { trackDao.setStarredAt("t1", null) }
        coVerify { trackDao.setDisliked("t1", true) }
    }

    @Test
    fun `dislikeTrack clears star and sets dislike`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        val r = repo(api = api, trackDao = trackDao)

        r.dislikeTrack("t1")

        coVerify { api.unstar(any(), id = "t1") }
        coVerify { trackDao.setStarredAt("t1", null) }
        coVerify { trackDao.setDisliked("t1", true) }
    }

    @Test
    fun `clearDislikeTrack clears the local flag`() = runTest {
        val trackDao = mockk<TrackDao>(relaxed = true)
        val r = repo(trackDao = trackDao)

        r.clearDislikeTrack("t1")

        coVerify { trackDao.setDisliked("t1", false) }
        coVerify(exactly = 0) { trackDao.setStarredAt(any(), any()) }
    }

    @Test
    fun `starTrack stars server and records starredAt`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        val r = repo(api = api, trackDao = trackDao)

        r.starTrack("t1")

        coVerify { api.star(any(), id = "t1") }
        coVerify { trackDao.setStarredAt("t1", any()) }
    }

    @Test
    fun `unstarTrack unstars server and clears starredAt`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        val r = repo(api = api, trackDao = trackDao)

        r.unstarTrack("t1")

        coVerify { api.unstar(any(), id = "t1") }
        coVerify { trackDao.setStarredAt("t1", null) }
    }

    @Test
    fun `album and artist star variants delegate to api`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val r = repo(api = api)

        r.starAlbum("al-1")
        r.unstarAlbum("al-1")
        r.starArtist("ar-1")
        r.unstarArtist("ar-1")

        coVerify { api.star(any(), albumId = "al-1") }
        coVerify { api.unstar(any(), albumId = "al-1") }
        coVerify { api.star(any(), artistId = "ar-1") }
        coVerify { api.unstar(any(), artistId = "ar-1") }
    }

    @Test
    fun `getStarred delegates to api`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val r = repo(api = api)

        r.getStarred()

        coVerify { api.getStarred2(any()) }
    }

    // ── v43: Album / artist likes (server star + local ledger) ───────────

    @Test
    fun `likeAlbum clears dislike then stars server and ledger`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(api = api, metadataDao = metadataDao)

        r.likeAlbum("al-1")

        // Local-first: clear dislike → ensure ledger row → local star → server
        coVerifyOrder {
            metadataDao.setAlbumDisliked("al-1", false)
            metadataDao.ensureAlbumLedgerRow("al-1")
            metadataDao.setAlbumStarredAt("al-1", any())
            api.star(any(), albumId = "al-1")
        }
    }

    @Test
    fun `likeAlbum keeps local star when server call fails (local-first)`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.star(any(), albumId = "al-1") } throws RuntimeException("offline")
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(api = api, metadataDao = metadataDao)

        r.likeAlbum("al-1")

        coVerify { metadataDao.ensureAlbumLedgerRow("al-1") }
        coVerify { metadataDao.setAlbumStarredAt("al-1", any()) }
        coVerify { metadataDao.setAlbumDisliked("al-1", false) }
    }

    @Test
    fun `dislikeAlbum ensures the ledger row before writing`() = runTest {
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(metadataDao = metadataDao)

        r.dislikeAlbum("al-1")

        coVerifyOrder {
            metadataDao.ensureAlbumLedgerRow("al-1")
            metadataDao.setAlbumStarredAt("al-1", null)
            metadataDao.setAlbumDisliked("al-1", true)
        }
    }

    @Test
    fun `likeArtist ensures the ledger row before writing`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(api = api, metadataDao = metadataDao)

        r.likeArtist("ar-1")

        // Local-first: clear dislike → ensure ledger row → local star → server
        coVerifyOrder {
            metadataDao.setArtistDisliked("ar-1", false)
            metadataDao.ensureArtistLedgerRow("ar-1")
            metadataDao.setArtistStarredAt("ar-1", any())
            api.star(any(), artistId = "ar-1")
        }
    }

    @Test
    fun `dislikeArtist keeps local state when server unstar fails`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.unstar(any(), artistId = "ar-1") } throws RuntimeException("offline")
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(api = api, metadataDao = metadataDao)

        r.dislikeArtist("ar-1")

        coVerify { metadataDao.ensureArtistLedgerRow("ar-1") }
        coVerify { metadataDao.setArtistDisliked("ar-1", true) }
        coVerify { metadataDao.setArtistStarredAt("ar-1", null) }
    }

    @Test
    fun `unlikeAlbum unstars server and clears ledger star`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(api = api, metadataDao = metadataDao)

        r.unlikeAlbum("al-1")

        coVerify { api.unstar(any(), albumId = "al-1") }
        coVerify { metadataDao.setAlbumStarredAt("al-1", null) }
    }

    @Test
    fun `dislikeAlbum clears star locally even when server unstar fails`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.unstar(any(), albumId = "al-1") } throws RuntimeException("offline")
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(api = api, metadataDao = metadataDao)

        r.dislikeAlbum("al-1")

        coVerify { metadataDao.setAlbumStarredAt("al-1", null) }
        coVerify { metadataDao.setAlbumDisliked("al-1", true) }
    }

    @Test
    fun `clearDislikeAlbum clears the local flag only`() = runTest {
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(metadataDao = metadataDao)

        r.clearDislikeAlbum("al-1")

        coVerify { metadataDao.setAlbumDisliked("al-1", false) }
        coVerify(exactly = 0) { metadataDao.setAlbumStarredAt(any(), any()) }
    }

    @Test
    fun `likeArtist clears dislike then stars server and ledger`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(api = api, metadataDao = metadataDao)

        r.likeArtist("ar-1")

        // Local-first: clear dislike → ensure ledger row → local star → server
        coVerifyOrder {
            metadataDao.setArtistDisliked("ar-1", false)
            metadataDao.ensureArtistLedgerRow("ar-1")
            metadataDao.setArtistStarredAt("ar-1", any())
            api.star(any(), artistId = "ar-1")
        }
    }

    @Test
    fun `unlikeArtist unstars server and clears ledger star`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(api = api, metadataDao = metadataDao)

        r.unlikeArtist("ar-1")

        coVerify { api.unstar(any(), artistId = "ar-1") }
        coVerify { metadataDao.setArtistStarredAt("ar-1", null) }
    }

    @Test
    fun `dislikeArtist clears star locally even when server unstar fails`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.unstar(any(), artistId = "ar-1") } throws RuntimeException("offline")
        val metadataDao = mockk<com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao>(relaxed = true)
        val r = repo(api = api, metadataDao = metadataDao)

        r.dislikeArtist("ar-1")

        coVerify { metadataDao.setArtistStarredAt("ar-1", null) }
        coVerify { metadataDao.setArtistDisliked("ar-1", true) }
    }

    // ── v43: Radio bookmarks (local-only Room persistence) ───────────────

    @Test
    fun `bookmarkRadio upserts a local favorite row`() = runTest {
        val radioFavoriteDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        val r = repo(radioFavoriteDao = radioFavoriteDao)

        r.bookmarkRadio("st-1", "Retro Wave", "https://stream.example/retro", "https://home.example")

        coVerify {
            radioFavoriteDao.upsert(
                match {
                    it.stationId == "st-1" && it.name == "Retro Wave" &&
                        it.streamUrl == "https://stream.example/retro" &&
                        it.homePageUrl == "https://home.example"
                },
            )
        }
    }

    @Test
    fun `unbookmarkRadio deletes the local favorite row`() = runTest {
        val api = mockk<SubsonicApi>(relaxed = true)
        val radioFavoriteDao = mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true)
        val r = repo(api = api, radioFavoriteDao = radioFavoriteDao)

        r.unbookmarkRadio("st-1")

        coVerify { radioFavoriteDao.delete("st-1") }
        coVerify(exactly = 0) { api.unstar(any(), albumId = any()) }
    }
}
