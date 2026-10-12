package com.lucasdss.ftpmusic.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListenBrainzServiceTest {

    private val service = ListenBrainzService()

    @Test
    fun `parsePopularityTotal reads total_listen_count`() {
        val body = """{"total_listen_count":12345}"""
        assertEquals(12345L, service.parsePopularityTotal(body))
    }

    @Test
    fun `parsePopularityTotal reads nested payload`() {
        val body = """{"payload":{"listen_count":99}}"""
        assertEquals(99L, service.parsePopularityTotal(body))
    }

    @Test
    fun `parsePopularityTotal returns null when missing`() {
        assertNull(service.parsePopularityTotal("{}"))
        assertNull(service.parsePopularityTotal(""))
    }
}
