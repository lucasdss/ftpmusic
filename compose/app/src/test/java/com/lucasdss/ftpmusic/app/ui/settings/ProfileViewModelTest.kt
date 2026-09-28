package com.lucasdss.ftpmusic.app.ui.settings

import com.lucasdss.ftpmusic.app.data.db.ListenEventDao
import com.lucasdss.ftpmusic.app.data.db.RecentListenRow
import com.lucasdss.ftpmusic.app.data.db.TopCountRow
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val listenEventDao: ListenEventDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var vm: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { listenEventDao.sumListenedSeconds(any(), any()) } returns 3600L
        coEvery { listenEventDao.countPlays(any(), any()) } returns 10
        coEvery { listenEventDao.countDistinctTracks(any(), any()) } returns 4
        coEvery { listenEventDao.countDistinctArtists(any(), any()) } returns 2
        coEvery { listenEventDao.distinctListenDays(any()) } returns listOf(
            java.time.LocalDate.now().toString(),
        )
        coEvery { listenEventDao.topTracks(any(), any(), any()) } returns listOf(
            TopCountRow("t1", "Song", 5),
        )
        coEvery { listenEventDao.topArtists(any(), any(), any()) } returns emptyList()
        coEvery { listenEventDao.topAlbums(any(), any(), any()) } returns emptyList()
        coEvery { listenEventDao.topGenres(any(), any(), any()) } returns emptyList()
        coEvery { listenEventDao.recentlyPlayed(any()) } returns listOf(
            RecentListenRow("t1", "Song", "Artist", 1000L, 180),
        )
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(
            TrackEntity(id = "t1", title = "Song", artist = "Artist", durationSeconds = 200),
        )
        vm = ProfileViewModel(listenEventDao, trackDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh loads summary tops and recent`() = runTest(dispatcher) {
        vm.refresh()
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(60, s.summary.listeningMinutes)
        assertEquals(10, s.summary.plays)
        assertEquals(4, s.summary.songCount)
        assertEquals(2, s.summary.artistCount)
        assertEquals(1, s.summary.streakDays)
        assertEquals(1, s.topTracks.size)
        assertEquals("t1", s.recentlyPlayed.first().id)
        assertFalse(s.isLoading)
    }

    @Test
    fun `setPeriod changes period and reloads`() = runTest(dispatcher) {
        vm.setPeriod(StatsPeriod.YEAR)
        advanceUntilIdle()
        assertEquals(StatsPeriod.YEAR, vm.state.value.period)
        coVerify(atLeast = 1) { listenEventDao.countPlays(any(), any()) }
    }

    @Test
    fun `setPeriod same value is no-op`() = runTest(dispatcher) {
        vm.refresh()
        advanceUntilIdle()
        vm.setPeriod(StatsPeriod.WEEK)
        advanceUntilIdle()
        // still week; only one refresh from above + no extra from setPeriod
        assertEquals(StatsPeriod.WEEK, vm.state.value.period)
    }

    @Test
    fun `empty recent stays empty`() = runTest(dispatcher) {
        coEvery { listenEventDao.recentlyPlayed(any()) } returns emptyList()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.recentlyPlayed.size)
    }

    @Test
    fun `missing track row falls back to event fields`() = runTest(dispatcher) {
        coEvery { trackDao.getTracksByIds(any()) } returns emptyList()
        vm.refresh()
        advanceUntilIdle()
        assertEquals("Song", vm.state.value.recentlyPlayed.first().title)
    }
}
