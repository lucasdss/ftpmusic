package com.lucasdss.ftpmusic.app.ui.settings

import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.ui.library.LibraryViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileScreenTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `formatListeningTime returns 0h for zero`() {
        assertEquals("0h", formatListeningTime(0))
    }

    @Test
    fun `formatListeningTime returns minutes for less than 60`() {
        assertEquals("45m", formatListeningTime(45))
    }

    @Test
    fun `formatListeningTime returns hours for 60 plus`() {
        assertEquals("2h", formatListeningTime(120))
    }

    @Test
    fun `formatListeningTime returns hours and minutes`() {
        assertEquals("2h 30m", formatListeningTime(150))
    }

    @Test
    fun `formatDuration formats seconds correctly`() {
        assertEquals("3:05", formatDuration(185))
    }

    @Test
    fun `formatDuration formats zero`() {
        assertEquals("0:00", formatDuration(0))
    }
}
