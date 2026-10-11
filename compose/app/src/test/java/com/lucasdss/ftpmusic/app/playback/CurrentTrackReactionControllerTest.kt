package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentTrackReactionControllerTest {

    @Test
    fun `load reads starred and disliked from Room`() = runTest {
        val trackDao = mockk<TrackDao>()
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "T",
            starredAt = 1L,
            isDisliked = false,
            userRating = 4,
        )
        val controller = CurrentTrackReactionController(mockk(relaxed = true), trackDao)
        val snap = controller.load("t1")
        assertTrue(snap.isStarred)
        assertFalse(snap.isDisliked)
        assertEquals(4, snap.trackRating)
        assertEquals("t1", controller.reactions.value?.trackId)
    }

    @Test
    fun `toggleLike likes when not starred`() = runTest {
        val fav = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "T", starredAt = null)
        val controller = CurrentTrackReactionController(fav, trackDao)
        val snap = controller.toggleLike("t1", wasStarred = false)
        coVerify { fav.likeTrack("t1") }
        assertTrue(snap.isStarred)
        assertFalse(snap.isDisliked)
    }

    @Test
    fun `toggleLike unlikes when starred`() = runTest {
        val fav = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "T", starredAt = 1L)
        val controller = CurrentTrackReactionController(fav, trackDao)
        val snap = controller.toggleLike("t1", wasStarred = true)
        coVerify { fav.unlikeTrack("t1") }
        assertFalse(snap.isStarred)
    }

    @Test
    fun `toggleDislike dislikes when not disliked`() = runTest {
        val fav = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(
            id = "t1",
            title = "T",
            starredAt = 1L,
            isDisliked = false,
        )
        val controller = CurrentTrackReactionController(fav, trackDao)
        val snap = controller.toggleDislike("t1", wasDisliked = false)
        coVerify { fav.dislikeTrack("t1") }
        assertTrue(snap.isDisliked)
        assertFalse(snap.isStarred)
    }

    @Test
    fun `toggleDislike clears when already disliked`() = runTest {
        val fav = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "T", isDisliked = true)
        val controller = CurrentTrackReactionController(fav, trackDao)
        val snap = controller.toggleDislike("t1", wasDisliked = true)
        coVerify { fav.clearDislikeTrack("t1") }
        assertFalse(snap.isDisliked)
    }

    @Test
    fun `publish and clear update StateFlow`() {
        val controller = CurrentTrackReactionController(mockk(relaxed = true), mockk(relaxed = true))
        controller.publish(
            CurrentTrackReactionController.Snapshot("x", isStarred = true, isDisliked = false),
        )
        assertEquals("x", controller.reactions.value?.trackId)
        controller.clear()
        assertNull(controller.reactions.value)
    }

    @Test
    fun `toggleLike with wasStarred does not emit intermediate Room snapshot`() = runTest {
        val fav = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        // Room still says not starred; optimistic wasStarred=true (unlike path).
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "T", starredAt = null)
        val controller = CurrentTrackReactionController(fav, trackDao)
        controller.publish(
            CurrentTrackReactionController.Snapshot("t1", isStarred = true, isDisliked = false),
        )
        val emissions = mutableListOf<CurrentTrackReactionController.Snapshot?>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.reactions.collect { emissions.add(it) }
        }
        // Drop the initial collect of optimistic publish.
        emissions.clear()
        val snap = controller.toggleLike("t1", wasStarred = true)
        assertFalse(snap.isStarred)
        coVerify { fav.unlikeTrack("t1") }
        // Only one post-toggle emission — never Room's starred=false mid-flight.
        assertEquals(1, emissions.size)
        assertFalse(emissions.single()!!.isStarred)
        collectJob.cancel()
    }

    @Test
    fun `session toggleLike updates shared reactions for NP sync`() = runTest {
        val fav = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("t1") } returns TrackEntity(id = "t1", title = "T", starredAt = null)
        val controller = CurrentTrackReactionController(fav, trackDao)
        // Simulate NP seeded state, then session/shade toggle.
        controller.publish(
            CurrentTrackReactionController.Snapshot("t1", isStarred = false, isDisliked = false),
        )
        val fromSession = controller.toggleLike("t1")
        assertTrue(fromSession.isStarred)
        assertTrue(controller.reactions.value!!.isStarred)
        assertEquals("t1", controller.reactions.value!!.trackId)
    }

    @Test
    fun `clear then load replaces prior track reaction`() = runTest {
        val trackDao = mockk<TrackDao>()
        coEvery { trackDao.getTrack("t2") } returns TrackEntity(
            id = "t2",
            title = "T2",
            starredAt = null,
            isDisliked = true,
        )
        val controller = CurrentTrackReactionController(mockk(relaxed = true), trackDao)
        controller.publish(
            CurrentTrackReactionController.Snapshot("t1", isStarred = true, isDisliked = false),
        )
        controller.clear()
        assertNull(controller.reactions.value)
        val snap = controller.load("t2")
        assertEquals("t2", snap.trackId)
        assertFalse(snap.isStarred)
        assertTrue(snap.isDisliked)
    }
}
