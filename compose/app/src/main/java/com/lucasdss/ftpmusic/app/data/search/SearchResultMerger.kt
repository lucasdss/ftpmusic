package com.lucasdss.ftpmusic.app.data.search

/**
 * Union server + local search hits by id, then rank by query relevance.
 * Phase-6: composite lexical + popularity tie-break (FTS4 kept; no BM25).
 * Lower primary score = better.
 */
object SearchResultMerger {

    fun <T> unionById(
        server: List<T>,
        local: List<T>,
        idOf: (T) -> String,
    ): List<T> {
        if (server.isEmpty()) return local
        if (local.isEmpty()) return server
        val seen = LinkedHashSet<String>()
        val out = ArrayList<T>(server.size + local.size)
        for (item in server) {
            val id = idOf(item)
            if (seen.add(id)) out.add(item)
        }
        for (item in local) {
            val id = idOf(item)
            if (seen.add(id)) out.add(item)
        }
        return out
    }

    fun <T> rankByQuery(
        items: List<T>,
        query: String,
        textOf: (T) -> String,
    ): List<T> {
        val q = SearchQueryNormalizer.fold(query)
        if (q.isEmpty() || items.size <= 1) return items
        return items.sortedWith(
            compareBy<T> { rankScore(SearchQueryNormalizer.fold(textOf(it)), q) }
                .thenBy { SearchQueryNormalizer.fold(textOf(it)) },
        )
    }

    /**
     * Rank by best match across multiple text fields (title / artist / album).
     * Lower composite score wins. Optional [popularityOf] is a descending tie-break
     * (higher play count / recency wins when lexical tier ties).
     */
    fun <T> rankByFields(
        items: List<T>,
        query: String,
        fieldsOf: (T) -> List<String?>,
        popularityOf: ((T) -> Long)? = null,
    ): List<T> {
        val q = SearchQueryNormalizer.fold(query)
        if (q.isEmpty() || items.size <= 1) return items
        return items.sortedWith(
            compareBy<T> { item ->
                fieldsOf(item)
                    .mapNotNull { it?.takeIf { s -> s.isNotBlank() } }
                    .minOfOrNull { rankScore(SearchQueryNormalizer.fold(it), q) }
                    ?: 3
            }.thenByDescending { item ->
                popularityOf?.invoke(item) ?: 0L
            }.thenBy {
                fieldsOf(it).firstOrNull { f -> !f.isNullOrBlank() }
                    ?.let { SearchQueryNormalizer.fold(it) }
                    .orEmpty()
            },
        )
    }

    /** Combine play_count + recency into a single descending popularity key. */
    fun trackPopularity(playCount: Int, lastPlayedAt: Long?): Long {
        val plays = playCount.coerceAtLeast(0).toLong()
        val recency = (lastPlayedAt ?: 0L) / 1_000_000L // coarse ms→bucket
        return plays * 1_000_000L + recency
    }

    /** True when folded name equals folded query (exact entity). */
    fun isExactName(name: String?, query: String): Boolean {
        val q = SearchQueryNormalizer.fold(query)
        if (q.isEmpty()) return false
        return SearchQueryNormalizer.fold(name.orEmpty()) == q
    }

    /** Lower score = better. 0 exact, 1 prefix, 2 contains, 3 other. */
    internal fun rankScore(foldedText: String, foldedQuery: String): Int = when {
        foldedText == foldedQuery -> 0
        foldedText.startsWith(foldedQuery) -> 1
        foldedText.contains(foldedQuery) -> 2
        else -> 3
    }
}
