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

    /**
     * 3-state headline formatter (ADR-0128): no listens → "0m", sub-minute
     * listen → "<1m", else hour/minute. Unlike [formatListeningMinutes], this
     * takes raw seconds so an empty Profile and a sub-minute listen are
     * distinguishable.
     */
    fun formatListeningTime(seconds: Long): String = when {
        seconds <= 0L -> "0m"

        seconds < 60L -> "<1m"

        else -> formatListeningMinutes((seconds / 60L).toInt())
    }

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)

    data class ActivityPoint(val label: String, val seconds: Long)

    /**
     * Zero-fill daily buckets from period start (or [fallbackStart]) through today.
     * [raw] keys are yyyy-MM-dd.
     */
    fun fillDailySeries(
        period: StatsPeriod,
        raw: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ActivityPoint> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val start = when (period) {
            StatsPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            StatsPeriod.MONTH -> today.withDayOfMonth(1)
            StatsPeriod.YEAR, StatsPeriod.ALL_TIME -> today.minusDays(29)
        }
        val points = mutableListOf<ActivityPoint>()
        var cursor = start
        while (!cursor.isAfter(today)) {
            val key = cursor.format(dayFmt)
            val label = when (period) {
                StatsPeriod.WEEK -> cursor.dayOfWeek.name.take(3).lowercase()
                    .replaceFirstChar { it.uppercase() }

                else -> cursor.dayOfMonth.toString()
            }
            points += ActivityPoint(label = label, seconds = raw[key] ?: 0L)
            cursor = cursor.plusDays(1)
        }
        return points
    }

    /**
     * Zero-fill monthly buckets for calendar year (or last 12 months for All time).
     * [raw] keys are yyyy-MM.
     */
    fun fillMonthlySeries(
        period: StatsPeriod,
        raw: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ActivityPoint> {
        val zdt = Instant.ofEpochMilli(nowMs).atZone(zone)
        val endMonth = zdt.toLocalDate().withDayOfMonth(1)
        val startMonth = when (period) {
            StatsPeriod.YEAR -> endMonth.withDayOfYear(1)
            StatsPeriod.ALL_TIME -> endMonth.minusMonths(11)
            else -> endMonth
        }
        val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM")
        val labelFmt = DateTimeFormatter.ofPattern("MMM")
        val points = mutableListOf<ActivityPoint>()
        var cursor = startMonth
        while (!cursor.isAfter(endMonth)) {
            val key = cursor.format(monthFmt)
            points += ActivityPoint(
                label = cursor.format(labelFmt),
                seconds = raw[key] ?: 0L,
            )
            cursor = cursor.plusMonths(1)
        }
        return points
    }
}
