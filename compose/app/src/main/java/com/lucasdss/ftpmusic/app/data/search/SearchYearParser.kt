package com.lucasdss.ftpmusic.app.data.search

/**
 * Parse year / decade tokens from a search query (ADR Phase-3 WS-I).
 * Examples: "1994", "90s", "1990s", "pink floyd 70s"
 */
data class SearchYearConstraint(val exactYear: Int? = null, val minYear: Int? = null, val maxYear: Int? = null) {
    val isActive: Boolean get() = exactYear != null || (minYear != null && maxYear != null)

    fun matches(year: Int?): Boolean {
        if (!isActive) return true
        if (year == null) return false
        exactYear?.let { return year == it }
        val min = minYear ?: return true
        val max = maxYear ?: return true
        return year in min..max
    }
}

data class ParsedSearchQuery(
    /** Remaining free-text after stripping year/decade tokens. */
    val text: String,
    val year: SearchYearConstraint = SearchYearConstraint(),
)

object SearchYearParser {
    private val exactYear = Regex("""\b(19\d{2}|20\d{2})\b""")
    private val decadeShort = Regex("""\b([6-9]0)s\b""", RegexOption.IGNORE_CASE)
    private val decadeLong = Regex("""\b((?:19|20)\d0)s\b""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ParsedSearchQuery {
        var remaining = raw.trim()
        if (remaining.isEmpty()) return ParsedSearchQuery("")

        decadeLong.find(remaining)?.let { m ->
            val start = m.groupValues[1].toInt()
            remaining = remaining.removeRange(m.range).replace(Regex("\\s+"), " ").trim()
            return ParsedSearchQuery(remaining, SearchYearConstraint(minYear = start, maxYear = start + 9))
        }
        decadeShort.find(remaining)?.let { m ->
            val decade = m.groupValues[1].toInt() // 70, 80, 90, 60
            val start = 1900 + decade
            remaining = remaining.removeRange(m.range).replace(Regex("\\s+"), " ").trim()
            return ParsedSearchQuery(remaining, SearchYearConstraint(minYear = start, maxYear = start + 9))
        }
        exactYear.find(remaining)?.let { m ->
            val y = m.groupValues[1].toInt()
            remaining = remaining.removeRange(m.range).replace(Regex("\\s+"), " ").trim()
            return ParsedSearchQuery(remaining, SearchYearConstraint(exactYear = y))
        }
        return ParsedSearchQuery(remaining)
    }
}
