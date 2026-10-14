package com.lucasdss.ftpmusic.app.data.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeezerServiceTest {

    private val service = DeezerService()

    @Test
    fun `parseHits maps artist data`() {
        val body = """
            {"data":[{"id":27,"name":"Daft Punk","type":"artist","picture_medium":"https://cdn/a.jpg"}],"total":1}
        """.trimIndent()
        val hits = service.parseHits(body) { obj ->
            DeezerService.ArtistHit(
                id = obj.optLong("id").toString(),
                name = obj.optString("name"),
                imageUrl = service.optImageUrl(obj, "picture_medium", "picture"),
            ).takeIf { it.name.isNotBlank() }
        }
        assertEquals(1, hits.size)
        assertEquals("27", hits[0].id)
        assertEquals("Daft Punk", hits[0].name)
        assertEquals("https://cdn/a.jpg", hits[0].imageUrl)
    }

    @Test(expected = DeezerService.QuotaException::class)
    fun `parseHits throws on body quota code 4`() {
        val body = """{"error":{"type":"QuotaException","message":"Quota","code":4}}"""
        service.parseHits(body) { null }
    }

    @Test
    fun `parseHits returns empty on other body error`() {
        val body = """{"error":{"type":"DataException","message":"x","code":1}}"""
        val hits = service.parseHits(body) { obj ->
            DeezerService.ArtistHit(obj.optLong("id").toString(), obj.optString("name"))
        }
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `applyQuotaBackoff increments counter and delays`() = runTest {
        val before = service.quotaBackoffInvocations
        val job = backgroundScope.launch { service.applyQuotaBackoff() }
        advanceTimeBy(DeezerService.QUOTA_BACKOFF_MS)
        job.join()
        assertEquals(before + 1, service.quotaBackoffInvocations)
    }
}
