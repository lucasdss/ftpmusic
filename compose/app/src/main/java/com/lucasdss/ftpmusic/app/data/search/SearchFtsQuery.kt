package com.lucasdss.ftpmusic.app.data.search

/**
 * Build FTS MATCH query from user text (prefix tokens).
 */
object SearchFtsQuery {
    fun toMatchQuery(raw: String): String {
        val folded = SearchQueryNormalizer.fold(raw)
        val tokens = folded.split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]+"), "") }
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) {
            val single = folded.replace(Regex("[^\\p{L}\\p{N}]+"), "")
            // No alnum → empty (caller skips MATCH; no noisy sentinel "a")
            return if (single.isEmpty()) "" else "$single*"
        }
        return tokens.joinToString(" ") { "$it*" }
    }
}
