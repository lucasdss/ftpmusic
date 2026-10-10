package com.lucasdss.ftpmusic.app.ui.library

/**
 * Library Albums alpha browse paging (ADR-0107 / Pass 6).
 * Not a total catalog cap — near-end loadMore appends further pages.
 */
object LibraryPaging {
    const val ALPHA_PAGE_SIZE: Int = 60

    /** True when [pageCount] looks like a full page (more rows may exist). */
    fun hasMore(pageCount: Int, pageSize: Int = ALPHA_PAGE_SIZE): Boolean = pageCount >= pageSize
}
