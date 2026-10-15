package com.lucasdss.ftpmusic.app.data.search

/**
 * Artist display-name fold for library membership + Desired identity keys.
 * Folds curly/smart apostrophes so "Guns N' Roses" matches "Guns N’ Roses".
 */
object ArtistNameNormalizer {

    /** Delimiter for [search_aliases] written by MetadataEnrichWorker. */
    const val ALIAS_DELIMITER = '|'

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
     * True when [needle] equals one whole alias entry after [normalize].
     *
     * Pipe-delimited ([ALIAS_DELIMITER]) entries are preferred (writer uses
     * [joinAliases]). Legacy space-joined blobs without `|` only match when
     * the **entire** string equals the needle — never a token subsequence —
     * so tribute supersets ("Australian Pink Floyd Show") cannot match
     * "Pink Floyd". Re-enrich rewrites aliases with `|`.
     */
    fun aliasMatches(aliases: String?, needle: String): Boolean {
        val n = normalize(needle)
        if (n.isEmpty()) return false
        val raw = aliases.orEmpty()
        if (raw.isBlank()) return false
        val entries = if (raw.indexOf(ALIAS_DELIMITER) >= 0) {
            raw.split(ALIAS_DELIMITER)
        } else {
            listOf(raw)
        }
        return entries.any { normalize(it) == n }
    }

    /** Join MB alias list for persistence (pipe-delimited). */
    fun joinAliases(aliases: Iterable<String>): String = aliases.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        .joinToString(ALIAS_DELIMITER.toString())
}
