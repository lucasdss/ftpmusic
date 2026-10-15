package com.lucasdss.ftpmusic.app.data.search

/**
 * Artist display-name fold for library membership + Desired identity keys.
 * Folds curly/smart apostrophes so "Guns N' Roses" matches "Guns N’ Roses".
 */
object ArtistNameNormalizer {

    fun normalize(value: String): String = value.trim()
        .replace('\u2019', '\'') // ’
        .replace('\u2018', '\'') // ‘
        .replace('\u02BC', '\'') // ʼ modifier letter apostrophe
        .replace('\uFF07', '\'') // fullwidth apostrophe
        .replace('`', '\'')
        .replace('\u00B4', '\'') // ´
        .lowercase()
        .replace(Regex("\\s+"), " ")

    /**
     * True when [needle] appears as a whole-token sequence inside space-joined
     * [aliases] (after [normalize]). Avoids `%guns%` matching tribute bands
     * when the SQL LIKE is only a coarse prefilter.
     */
    fun aliasMatches(aliases: String?, needle: String): Boolean {
        val n = normalize(needle)
        if (n.isEmpty()) return false
        val a = normalize(aliases.orEmpty())
        if (a.isEmpty()) return false
        if (a == n) return true
        return Regex("(^|\\s)${Regex.escape(n)}(\\s|$)").containsMatchIn(a)
    }
}
