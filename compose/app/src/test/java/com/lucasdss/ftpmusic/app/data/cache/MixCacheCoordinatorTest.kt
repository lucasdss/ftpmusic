package com.lucasdss.ftpmusic.app.data.cache

import com.lucasdss.ftpmusic.app.data.db.CustomMixCacheTrackEntity
import com.lucasdss.ftpmusic.app.data.db.CustomMixDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MixCacheCoordinatorTest {

    private val customMixDao: CustomMixDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val cacheService: CacheService = mockk(relaxed = true)
    private val downloadManager: DownloadManager = mockk(relaxed = true)
    private val authHelper: SubsonicAuthHelper = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val coordinator = MixCacheCoordinator(
        customMixDao,
        trackDao,
        cacheService,
        downloadManager,
        authHelper,
        storage,
    )

    private fun mix(id: Long = 1L, autoCache: Boolean = true) = CustomMixEntity(
        id = id,
        name = "Mix $id",
        sourceKind = "genres",
        genresJson = "Rock",
        autoCache = autoCache,
    )

    private fun track(id: String, downloaded: Boolean = false) =
        TrackEntity(id = id, title = "T $id", isDownloaded = downloaded)

    private fun stubCredentials() {
        com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url = "https://s"
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "u"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "p"
        every { authHelper.buildStreamUrl(any(), any(), any(), any()) } returns "https://s/stream"
    }

    @org.junit.After
    fun tearDown() {
        com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url = ""
    }

    @Test
    fun `onGenerated is a no-op when auto-cache is off`() = runTest {
        coordinator.onGenerated(mix(autoCache = false), listOf("t1"))

        coVerify(exactly = 0) { customMixDao.upsertCacheTracks(any()) }
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `onGenerated records ownership and enqueues missing tracks`() = runTest {
        stubCredentials()
        every { cacheService.isStoredInCache("t1") } returns false
        every { cacheService.isStoredInCache("t2") } returns true
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns emptyList()

        coordinator.onGenerated(mix(), listOf("t1", "t2"))

        coVerify {
            customMixDao.upsertCacheTracks(
                match { rows: List<CustomMixCacheTrackEntity> -> rows.map { it.trackId } == listOf("t1", "t2") },
            )
        }
        coVerify { downloadManager.enqueue("t1", any(), priority = 2) }
        coVerify(exactly = 0) { downloadManager.enqueue("t2", any(), any()) }
    }

    @Test
    fun `onGenerated prunes ownership rows whose files are gone`() = runTest {
        stubCredentials()
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns listOf("stale")
        every { cacheService.isStoredInCache("stale") } returns false
        every { cacheService.isStoredInCache("t1") } returns true

        coordinator.onGenerated(mix(), listOf("t1"))

        coVerify { customMixDao.deleteCacheTracks(1L, listOf("stale")) }
    }

    @Test
    fun `onSourceChanged evicts removed exclusives and keeps shared and downloads`() = runTest {
        stubCredentials()
        coEvery { customMixDao.getById(1L) } returns mix()
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns listOf("removed", "shared", "download")
        coEvery { customMixDao.getTrackIdsOwnedByOtherMixes(1L) } returns listOf("shared")
        coEvery { trackDao.getTrack("removed") } returns track("removed")
        coEvery { trackDao.getTrack("shared") } returns track("shared")
        coEvery { trackDao.getTrack("download") } returns track("download", downloaded = true)
        every { cacheService.isStoredInCache(any()) } returns false

        coordinator.onSourceChanged(1L, listOf("new"))

        coVerify { cacheService.removeCached("removed") }
        coVerify(exactly = 0) { cacheService.removeCached("shared") }
        coVerify(exactly = 0) { cacheService.removeCached("download") }
        coVerify { customMixDao.deleteAllCacheTracks(1L) }
        coVerify { downloadManager.enqueue("new", any(), priority = 2) }
    }

    @Test
    fun `onSourceChanged with auto-cache off evicts and clears ownership`() = runTest {
        coEvery { customMixDao.getById(1L) } returns mix(autoCache = false)
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns listOf("t1")
        coEvery { customMixDao.getTrackIdsOwnedByOtherMixes(1L) } returns emptyList()
        coEvery { trackDao.getTrack("t1") } returns track("t1")

        coordinator.onSourceChanged(1L, listOf("t2"))

        coVerify { cacheService.removeCached("t1") }
        coVerify { customMixDao.deleteAllCacheTracks(1L) }
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `onSourceChanged ignores unknown mixes`() = runTest {
        coEvery { customMixDao.getById(1L) } returns null

        coordinator.onSourceChanged(1L, listOf("t1"))

        coVerify(exactly = 0) { customMixDao.deleteAllCacheTracks(any()) }
    }

    @Test
    fun `onDisabled evicts exclusives and clears ownership`() = runTest {
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns listOf("t1")
        coEvery { customMixDao.getTrackIdsOwnedByOtherMixes(1L) } returns emptyList()
        coEvery { trackDao.getTrack("t1") } returns track("t1")

        coordinator.onDisabled(1L)

        coVerify { cacheService.removeCached("t1") }
        coVerify { customMixDao.deleteAllCacheTracks(1L) }
    }

    @Test
    fun `onDeleted evicts exclusives`() = runTest {
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns listOf("t1")
        coEvery { customMixDao.getTrackIdsOwnedByOtherMixes(1L) } returns emptyList()
        coEvery { trackDao.getTrack("t1") } returns track("t1")

        coordinator.onDeleted(1L)

        coVerify { cacheService.removeCached("t1") }
        coVerify { customMixDao.deleteAllCacheTracks(1L) }
    }

    @Test
    fun `enqueue is a no-op without server credentials`() = runTest {
        com.lucasdss.ftpmusic.app.di.DynamicBaseUrl.url = ""
        every { cacheService.isStoredInCache("t1") } returns false
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns emptyList()

        coordinator.onGenerated(mix(), listOf("t1"))

        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `onGenerated still records ownership when all tracks are cached`() = runTest {
        stubCredentials()
        every { cacheService.isStoredInCache("t1") } returns true
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns emptyList()

        coordinator.onGenerated(mix(), listOf("t1"))

        coVerify { customMixDao.upsertCacheTracks(any()) }
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `onGenerated ignores an empty tracklist`() = runTest {
        coordinator.onGenerated(mix(), emptyList())

        coVerify(exactly = 0) { customMixDao.upsertCacheTracks(any()) }
        coVerify(exactly = 0) { customMixDao.getOwnedTrackIds(any()) }
    }

    @Test
    fun `onSourceChanged with an empty desired set clears ownership without enqueueing`() = runTest {
        stubCredentials()
        coEvery { customMixDao.getById(1L) } returns mix()
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns emptyList()

        coordinator.onSourceChanged(1L, emptyList())

        coVerify { customMixDao.deleteAllCacheTracks(1L) }
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `enqueue is skipped when username or password is missing`() = runTest {
        every { storage.get(SecureStorage.KEY_URL) } returns "https://s"
        every { storage.get(SecureStorage.KEY_USERNAME) } returns null
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "p"
        every { cacheService.isStoredInCache("t1") } returns false
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns emptyList()

        coordinator.onGenerated(mix(), listOf("t1"))

        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `evict skips removal when the owned set is empty`() = runTest {
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns emptyList()

        coordinator.onDeleted(1L)

        coVerify(exactly = 0) { cacheService.removeCached(any()) }
    }

    @Test
    fun `deleting one mix keeps tracks owned by another mix`() = runTest {
        coEvery { customMixDao.getOwnedTrackIds(1L) } returns listOf("shared")
        coEvery { customMixDao.getTrackIdsOwnedByOtherMixes(1L) } returns listOf("shared")
        coEvery { trackDao.getTrack("shared") } returns track("shared")

        coordinator.onDeleted(1L)

        coVerify(exactly = 0) { cacheService.removeCached("shared") }
        coVerify { customMixDao.deleteAllCacheTracks(1L) }
    }
}
