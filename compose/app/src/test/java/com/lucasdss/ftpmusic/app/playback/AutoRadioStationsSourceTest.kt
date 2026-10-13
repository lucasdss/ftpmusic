package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao
import com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AutoRadioStationsSourceTest {

    @Before
    fun setup() {
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
        DynamicBaseUrl.url = ""
    }

    @After
    fun teardown() {
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
        DynamicBaseUrl.url = ""
    }

    @Test
    fun `parseStations handles list and single map`() {
        val listResponse = mapOf(
            "subsonic-response" to mapOf(
                "internetRadioStations" to mapOf(
                    "internetRadioStation" to listOf(
                        mapOf("id" to "1", "name" to "A", "streamUrl" to "https://a"),
                    ),
                ),
            ),
        )
        assertEquals(1, DefaultAutoRadioStationsSource.parseStations(listResponse).size)

        val single = mapOf(
            "subsonic-response" to mapOf(
                "internetRadioStations" to mapOf(
                    "internetRadioStation" to mapOf(
                        "id" to "2",
                        "name" to "B",
                        "streamUrl" to "https://b",
                    ),
                ),
            ),
        )
        assertEquals("2", DefaultAutoRadioStationsSource.parseStations(single).single().id)
    }

    @Test
    fun `parseStations coerces numeric ids`() {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "internetRadioStations" to mapOf(
                    "internetRadioStation" to mapOf(
                        "id" to 42,
                        "name" to "Numeric",
                        "streamUrl" to "https://num",
                    ),
                ),
            ),
        )
        assertEquals("42", DefaultAutoRadioStationsSource.parseStations(response).single().id)
        assertEquals("7", DefaultAutoRadioStationsSource.stationIdOf(7L))
    }

    @Test
    fun `falls back to bookmarks when offline`() = runBlocking {
        val favDao = mockk<RadioFavoriteDao>()
        coEvery { favDao.getAll() } returns listOf(
            RadioFavoriteEntity(
                stationId = "bm1",
                name = "Bookmarked",
                streamUrl = "https://bm.example",
            ),
        )
        val source = DefaultAutoRadioStationsSource(
            api = mockk(relaxed = true),
            authHelper = SubsonicAuthHelper(),
            radioFavoriteDao = favDao,
        )
        val stations = source.loadStations()
        assertEquals(1, stations.size)
        assertEquals("bm1", stations[0].id)
    }
}
