package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.content.SharedPreferences

/**
 * Home "Recently Added" snapshot (ADR-0109): ordered top-10 album ids + fetch time
 * in [MetadataSyncWorker.PREFS_NAME]. TTL max = Sync Interval minutes.
 */
object HomeRecentCache {
    const val PREF_HOME_RECENT_IDS = "home_recent_album_ids"
    const val PREF_HOME_RECENT_FETCHED_MS = "home_recent_fetched_ms"
    const val DEFAULT_TTL_MINUTES = 5
    const val MIN_TTL_MINUTES = 1
    const val HOME_RECENT_LIMIT = 10

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(MetadataSyncWorker.PREFS_NAME, Context.MODE_PRIVATE)

    fun maxTtlMinutes(syncIntervalHours: Int): Int =
        syncIntervalHours.coerceIn(1, 24) * 60

    fun clampTtlMinutes(ttlMinutes: Int, syncIntervalHours: Int): Int =
        ttlMinutes.coerceIn(MIN_TTL_MINUTES, maxTtlMinutes(syncIntervalHours))

    fun readIds(prefs: SharedPreferences): List<String> =
        prefs.getString(PREF_HOME_RECENT_IDS, "")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun readFetchedMs(prefs: SharedPreferences): Long =
        prefs.getLong(PREF_HOME_RECENT_FETCHED_MS, 0L)

    fun writeSnapshot(prefs: SharedPreferences, orderedIds: List<String>, fetchedAtMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(
                PREF_HOME_RECENT_IDS,
                orderedIds.take(HOME_RECENT_LIMIT).joinToString(","),
            )
            .putLong(PREF_HOME_RECENT_FETCHED_MS, fetchedAtMs)
            .apply()
    }

    fun isStale(fetchedAtMs: Long, ttlMinutes: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (fetchedAtMs <= 0L) return true
        return nowMs - fetchedAtMs >= ttlMinutes.toLong() * 60_000L
    }

    fun formatTtlLabel(ttlMinutes: Int): String = when {
        ttlMinutes < 60 -> "${ttlMinutes}m"
        ttlMinutes % 60 == 0 -> "${ttlMinutes / 60}h"
        else -> "${ttlMinutes / 60}h ${ttlMinutes % 60}m"
    }
}
