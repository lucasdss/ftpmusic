package com.lucasdss.ftpmusic.app.ui.settings

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileStatsMathTest {

    @Test
    fun `week bounds are calendar Monday start UTC`() {
        // 2023-11-15 is Wednesday → Monday 2023-11-13
        val now = LocalDate.of(2023, 11, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val (start, end) = ProfileStatsMath.periodBounds(StatsPeriod.WEEK, now, ZoneOffset.UTC)
        val expected = LocalDate.of(2023, 11, 13).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expected, start)
        assertEquals(now + 1, end)
    }

    @Test
    fun `month bounds start at day 1 UTC`() {
        // 2023-11-15 00:00 UTC
        val now = LocalDate.of(2023, 11, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val (start, end) = ProfileStatsMath.periodBounds(StatsPeriod.MONTH, now, ZoneOffset.UTC)
        val expectedStart = LocalDate.of(2023, 11, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expectedStart, start)
        assertEquals(now + 1, end)
    }

    @Test
    fun `year bounds start Jan 1`() {
        val now = LocalDate.of(2024, 6, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val (start, _) = ProfileStatsMath.periodBounds(StatsPeriod.YEAR, now, ZoneOffset.UTC)
        val expected = LocalDate.of(2024, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expected, start)
    }

    @Test
    fun `all time has null start`() {
        val (start, end) = ProfileStatsMath.periodBounds(StatsPeriod.ALL_TIME, 1000L)
        assertEquals(null, start)
        assertEquals(1001L, end)
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val today = LocalDate.of(2024, 5, 10)
        val days = listOf("2024-05-10", "2024-05-09", "2024-05-08", "2024-05-05")
        assertEquals(3, ProfileStatsMath.computeStreakDays(days, today))
    }

    @Test
    fun `streak allows yesterday start if today empty`() {
        val today = LocalDate.of(2024, 5, 10)
        val days = listOf("2024-05-09", "2024-05-08")
        assertEquals(2, ProfileStatsMath.computeStreakDays(days, today))
    }

    @Test
    fun `streak zero when gap before today`() {
        val today = LocalDate.of(2024, 5, 10)
        assertEquals(0, ProfileStatsMath.computeStreakDays(listOf("2024-05-07"), today))
        assertEquals(0, ProfileStatsMath.computeStreakDays(emptyList(), today))
    }

    @Test
    fun `formatListeningMinutes`() {
        assertEquals("0m", ProfileStatsMath.formatListeningMinutes(0))
        assertEquals("45m", ProfileStatsMath.formatListeningMinutes(45))
        assertEquals("2h", ProfileStatsMath.formatListeningMinutes(120))
        assertEquals("1h 30m", ProfileStatsMath.formatListeningMinutes(90))
    }

    @Test
    fun `fillDailySeries zero fills week`() {
        // Wednesday 2023-11-15 → Mon–Wed with one day having data
        val now = LocalDate.of(2023, 11, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val series = ProfileStatsMath.fillDailySeries(
            StatsPeriod.WEEK,
            mapOf("2023-11-14" to 120L),
            nowMs = now,
            zone = ZoneOffset.UTC,
        )
        assertEquals(3, series.size)
        assertEquals(0L, series[0].seconds) // Mon
        assertEquals(120L, series[1].seconds) // Tue
        assertEquals(0L, series[2].seconds) // Wed
    }

    @Test
    fun `fillMonthlySeries year has months through now`() {
        val now = LocalDate.of(2024, 3, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val series = ProfileStatsMath.fillMonthlySeries(
            StatsPeriod.YEAR,
            mapOf("2024-02" to 600L),
            nowMs = now,
            zone = ZoneOffset.UTC,
        )
        assertEquals(3, series.size) // Jan–Mar
        assertEquals(0L, series[0].seconds)
        assertEquals(600L, series[1].seconds)
        assertEquals(0L, series[2].seconds)
    }
}
