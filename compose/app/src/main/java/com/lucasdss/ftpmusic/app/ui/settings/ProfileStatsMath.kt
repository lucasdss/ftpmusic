package com.lucasdss.ftpmusic.app.ui.settings

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class StatsPeriod {
    WEEK,
    MONTH,
    YEAR,
    ALL_TIME,
}

/**
 * Pure period / streak math for Profile metrics (ADR-0046 / 0047).
 */
object ProfileStatsMath {
    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Returns `[startMs, endMs)` bounds. Null start = unbounded (All time).
     * Week = calendar ISO week (Monday 00:00 device TZ → now).
     */
    fun periodBounds(
        period: StatsPeriod,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<Long?, Long> {
        val endMs = nowMs + 1
        val zdt = Instant.ofEpochMilli(nowMs).atZone(zone)
        return when (period) {
            StatsPeriod.WEEK -> {
                val monday = zdt.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
                monday to endMs
            }

            StatsPeriod.MONTH -> {
                val start = zdt.toLocalDate().withDayOfMonth(1)
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                start to endMs
            }

            StatsPeriod.YEAR -> {
                val start = zdt.toLocalDate().withDayOfYear(1)
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                start to endMs
            }

            StatsPeriod.ALL_TIME -> null to endMs
        }
    }

    /**
     * Consecutive local calendar days with ≥1 listen, ending at [today]
     * (or yesterday if today empty — still counts as active streak through yesterday).
     */
    fun computeStreakDays(daysNewestFirst: List<String>, today: LocalDate = LocalDate.now()): Int {
        if (daysNewestFirst.isEmpty()) return 0
        val daySet = daysNewestFirst.toSet()
        val start = when {
            daySet.contains(today.format(dayFmt)) -> today
            daySet.contains(today.minusDays(1).format(dayFmt)) -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        var cursor = start
        while (daySet.contains(cursor.format(dayFmt))) {
            streak++
            cursor = cursor.minusDays(1)
            if (streak > 400) break
        }
        return streak
    }

    fun formatListeningMinutes(minutes: Int): String = when {
        minutes < 1 -> "0m"

        minutes < 60 -> "${minutes}m"

        else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "${h}h" else "${h}h ${m}m"
        }
    }

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
}
