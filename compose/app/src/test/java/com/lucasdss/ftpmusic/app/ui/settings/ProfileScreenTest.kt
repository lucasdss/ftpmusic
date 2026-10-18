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
    fun `formatDuration formats seconds correctly`() {
        assertEquals("3:05", formatDuration(185))
    }

    @Test
    fun `formatDuration formats zero`() {
        assertEquals("0:00", formatDuration(0))
    }
}
