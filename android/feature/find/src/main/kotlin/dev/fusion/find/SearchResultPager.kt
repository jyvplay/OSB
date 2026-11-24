// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * Bounded search result pager. Paginates search result IDs into pages of
 bounded size, supporting page navigation (next/previous) and total count.
 Result IDs are bounded in length; pages are bounded in count; page size
 is bounded. Does not perform actual search — the search engine supplies
 the IDs.
 *
 * Declared narrowings: at most [MAX_RESULTS] result IDs; each ID at most
 * [MAX_ID_LEN] chars matching [ID_RE]; page size in 1..[MAX_PAGE_SIZE];
 * at most [MAX_PAGES] pages; zero-based page indexing.
 */
class SearchResultPager private constructor(
    private val results: List<String>,
    private val pageSize: Int
) {
    companion object {
        const val MAX_RESULTS = 10_000
        const val MAX_ID_LEN = 256
        const val MAX_PAGE_SIZE = 100
        const val MAX_PAGES = 10_000
        private val ID_RE = Regex("[a-zA-Z0-9_-]{1,64}")

        fun create(results: List<String>, pageSize: Int): SearchResultPager {
            require(results.size <= MAX_RESULTS) { "too many results: $MAX_RESULTS" }
            for (id in results) {
                require(ID_RE.matches(id)) { "invalid result id: $id" }
                require(id.length <= MAX_ID_LEN) { "result id too long" }
            }
            require(pageSize >= 1 && pageSize <= MAX_PAGE_SIZE) { "page size out of bounds" }
            val totalPages = (results.size + pageSize - 1) / pageSize.coerceAtLeast(1)
            require(totalPages <= MAX_PAGES) { "too many pages: $MAX_PAGES" }
            return SearchResultPager(results, pageSize)
        }
    }

    fun totalResults(): Int = results.size
    fun totalPages(): Int = if (results.isEmpty()) 0 else (results.size + pageSize - 1) / pageSize

    fun page(pageIndex: Int): List<String> {
        require(pageIndex >= 0 && pageIndex < totalPages()) { "page index out of bounds" }
        val start = pageIndex * pageSize
        val end = (start + pageSize).coerceAtMost(results.size)
        return results.subList(start, end)
    }

    fun nextPage(pageIndex: Int): Int? {
        if (pageIndex + 1 < totalPages()) return pageIndex + 1
        return null
    }

    fun previousPage(pageIndex: Int): Int? {
        if (pageIndex > 0) return pageIndex - 1
        return null
    }

    fun hasPage(pageIndex: Int): Boolean = pageIndex >= 0 && pageIndex < totalPages()

    fun pageSize(): Int = pageSize

    private fun Int.coerceAtMost(max: Int): Int = if (this > max) max else this
    private fun Int.coerceAtLeast(min: Int): Int = if (this < min) min else this
}
