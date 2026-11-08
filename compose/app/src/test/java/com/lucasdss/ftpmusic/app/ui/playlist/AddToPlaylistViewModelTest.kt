package com.lucasdss.ftpmusic.app.ui.playlist

import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddToPlaylistViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var playlistDao: PlaylistDao
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var viewModel: AddToPlaylistViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        playlistDao = mockk(relaxed = true)
        playlistRepository = mockk(relaxed = true)
        viewModel = AddToPlaylistViewModel(playlistDao, playlistRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPlaylists maps Room rows only`() = runTest {
        coEvery { playlistDao.getAll() } returns listOf(
            PlaylistEntity(id = "p1", name = "Chill", trackCount = 3),
            PlaylistEntity(id = "p2", name = "Focus", trackCount = 0),
        )
        viewModel.loadPlaylists()
        advanceUntilIdle()
        assertEquals(listOf("p1", "p2"), viewModel.playlists.value.map { it.id })
        assertTrue(viewModel.playlists.value.none { it.name.contains("Daily", ignoreCase = true) })
    }

    @Test
    fun `addTracks no-ops on blank or empty`() = runTest {
        viewModel.addTracks("", listOf("t1"))
        viewModel.addTracks("p1", emptyList())
        advanceUntilIdle()
        coVerify(exactly = 0) { playlistRepository.addToPlaylist(any(), any()) }
    }

    @Test
    fun `addTracks delegates to repository and toasts`() = runTest {
        coEvery { playlistDao.getAll() } returns listOf(
            PlaylistEntity(id = "p1", name = "Chill", trackCount = 1),
        )
        viewModel.loadPlaylists()
        advanceUntilIdle()
        coEvery { playlistRepository.addToPlaylist("p1", listOf("t1")) } returns 1
        viewModel.addTracks("p1", listOf("t1"))
        advanceUntilIdle()
        coVerify(exactly = 1) { playlistRepository.addToPlaylist("p1", listOf("t1")) }
        assertEquals("Added to Chill", viewModel.toast.value)
    }

    @Test
    fun `addTracks toasts Already when repo adds zero`() = runTest {
        coEvery { playlistDao.getAll() } returns listOf(
            PlaylistEntity(id = "p1", name = "Chill", trackCount = 1),
        )
        viewModel.loadPlaylists()
        advanceUntilIdle()
        coEvery { playlistRepository.addToPlaylist("p1", listOf("t1")) } returns 0
        viewModel.addTracks("p1", listOf("t1"))
        advanceUntilIdle()
        assertEquals("Already in playlist", viewModel.toast.value)
    }
}
