// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Bounded browser history deduplicator keeping latest visit per URL.
 */
class BrowserHistoryDeduplicator private constructor() {
    companion object {
        data class Visit(val url: String, val timestampMs: Long)

        fun deduplicateLatest(visits: List<Visit>): List<Visit> {
            val latest = LinkedHashMap<String, Visit>()
            for (v in visits) {
                val prev = latest[v.url]
                if (prev == null || v.timestampMs >= prev.timestampMs) {
                    latest[v.url] = v
                }
            }
            return latest.values.sortedByDescending { it.timestampMs }
        }
    }
}
