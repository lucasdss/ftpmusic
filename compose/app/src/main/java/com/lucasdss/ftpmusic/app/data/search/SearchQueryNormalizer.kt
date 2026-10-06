package com.lucasdss.ftpmusic.app.data.search

import java.text.Normalizer

/**
 * Shared search query helpers: LIKE escaping + light Unicode fold for ranking.
 * DAO queries still use Room LIKE; callers must pass [escapeLike] results.
 */
object SearchQueryNormalizer {

    /** Escape SQLite LIKE metacharacters so user input is literal. */
    fun escapeLike(raw: String): String =
        buildString(raw.length + 4) {
            for (ch in raw) {
                when (ch) {
                    '\\', '%', '_' -> {
                        append('\\')
                        append(ch)
                    }
                    else -> append(ch)
                }
            }
        }

    /**
     * Fold for ranking / equality: NFD strip combining marks, lowercase.
     * Not applied inside SQL today (collation); used by [SearchResultMerger].
     */
    fun fold(raw: String): String {
        val nfd = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
        return buildString(nfd.length) {
            for (ch in nfd) {
                if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) {
                    append(ch.lowercaseChar())
                }
            }
        }
    }
}
