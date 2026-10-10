package com.lucasdss.ftpmusic.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRecentCacheTest {

    @Test
    fun `maxTtlMinutes tracks sync interval hours`() {
        assertEquals(60, HomeRecentCache.maxTtlMinutes(1))
        assertEquals(720, HomeRecentCache.maxTtlMinutes(12))
        assertEquals(1440, HomeRecentCache.maxTtlMinutes(24))
        assertEquals(60, HomeRecentCache.maxTtlMinutes(0))
        assertEquals(1440, HomeRecentCache.maxTtlMinutes(99))
    }

    @Test
    fun `clampTtlMinutes respects sync interval ceiling`() {
        assertEquals(5, HomeRecentCache.clampTtlMinutes(5, 12))
        assertEquals(1, HomeRecentCache.clampTtlMinutes(0, 12))
        assertEquals(60, HomeRecentCache.clampTtlMinutes(999, 1))
        assertEquals(720, HomeRecentCache.clampTtlMinutes(1000, 12))
    }

    @Test
    fun `isStale when never fetched or past ttl`() {
        assertTrue(HomeRecentCache.isStale(0L, 5, nowMs = 1_000_000L))
        val fetched = 1_000_000L
        assertFalse(HomeRecentCache.isStale(fetched, 5, nowMs = fetched + 4 * 60_000L))
        assertTrue(HomeRecentCache.isStale(fetched, 5, nowMs = fetched + 5 * 60_000L))
    }

    @Test
    fun `formatTtlLabel`() {
        assertEquals("5m", HomeRecentCache.formatTtlLabel(5))
        assertEquals("1h", HomeRecentCache.formatTtlLabel(60))
        assertEquals("1h 30m", HomeRecentCache.formatTtlLabel(90))
    }
}
