package com.lucasdss.ftpmusic.app.data.search

/**
 * Union server + local search hits by id, then rank by query relevance.
 * Fixes SearchViewModel `ifEmpty` clobber when search3 returns a partial page.
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

    /** Lower score = better. 0 exact, 1 prefix, 2 contains, 3 other. */
    internal fun rankScore(foldedText: String, foldedQuery: String): Int = when {
        foldedText == foldedQuery -> 0
        foldedText.startsWith(foldedQuery) -> 1
        foldedText.contains(foldedQuery) -> 2
        else -> 3
    }
}
