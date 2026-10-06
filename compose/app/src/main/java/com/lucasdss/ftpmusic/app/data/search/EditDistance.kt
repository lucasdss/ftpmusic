package com.lucasdss.ftpmusic.app.data.search

/**
 * Levenshtein edit distance for soft typo fallback (Phase-3 WS-J).
 */
object EditDistance {
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in a.indices) {
            cur[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                cur[j + 1] = minOf(cur[j] + 1, prev[j + 1] + 1, prev[j] + cost)
            }
            for (j in prev.indices) prev[j] = cur[j]
        }
        return prev[b.length]
    }

    /** True when folded strings are within [max] edits. */
    fun within(a: String, b: String, max: Int = 1): Boolean {
        if (kotlin.math.abs(a.length - b.length) > max) return false
        return distance(a, b) <= max
    }
}
