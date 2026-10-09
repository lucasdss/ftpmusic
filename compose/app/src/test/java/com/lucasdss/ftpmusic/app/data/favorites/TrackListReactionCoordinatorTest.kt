package com.lucasdss.ftpmusic.app.data.favorites

import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackListReactionCoordinatorTest {

    private val favoriteRepository: FavoriteRepository = mockk(relaxed = true)

    @Test
    fun `setsFromEntities maps starred and disliked`() {
        val coordinator = TrackListReactionCoordinator(favoriteRepository)
        val sets = coordinator.setsFromEntities(
            listOf(
                TrackEntity(id = "a", title = "A", starredAt = 1L),
                TrackEntity(id = "b", title = "B", isDisliked = true),
                TrackEntity(id = "c", title = "C"),
            ),
        )
        assertEquals(setOf("a"), sets.liked)
        assertEquals(setOf("b"), sets.disliked)
    }

    @Test
    fun `toggleLike optimistic then persists`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = TrackListReactionCoordinator(favoriteRepository)
        var published = TrackListReactionCoordinator.Sets()
        coordinator.toggleLike(
            trackId = "t1",
            current = TrackListReactionCoordinator.Sets(),
            scope = this,
            publish = { published = it },
        )
        assertTrue("t1" in published.liked)
        coVerify { favoriteRepository.likeTrack("t1") }
    }

    @Test
    fun `toggleLike rollback on failure`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { favoriteRepository.likeTrack("t1") } throws RuntimeException("offline")
        val coordinator = TrackListReactionCoordinator(favoriteRepository)
        var published = TrackListReactionCoordinator.Sets(liked = setOf("keep"))
        val before = published
        coordinator.toggleLike("t1", before, this) { published = it }
        assertFalse("t1" in published.liked)
        assertEquals(before, published)
    }

    @Test
    fun `toggleDislike clears like`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = TrackListReactionCoordinator(favoriteRepository)
        var published = TrackListReactionCoordinator.Sets(liked = setOf("t1"))
        coordinator.toggleDislike(
            trackId = "t1",
            current = published,
            scope = this,
            publish = { published = it },
        )
        assertTrue("t1" in published.disliked)
        assertFalse("t1" in published.liked)
        coVerify { favoriteRepository.dislikeTrack("t1") }
    }
}
