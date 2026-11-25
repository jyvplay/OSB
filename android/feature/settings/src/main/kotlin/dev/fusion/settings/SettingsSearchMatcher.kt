// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Bounded settings search matcher with title/summary scoring.
 */
class SettingsSearchMatcher private constructor() {
    companion object {
        const val MAX_QUERY_LEN = 128

        fun score(query: String, title: String, summary: String): Int {
            require(query.length <= MAX_QUERY_LEN) { "query too long" }
            val q = query.trim().lowercase()
            if (q.isEmpty()) return 0
            val t = title.lowercase()
            val s = summary.lowercase()
            var pts = 0
            if (t == q) pts += 100
            else if (t.startsWith(q)) pts += 60
            else if (t.contains(q)) pts += 30
            if (s.contains(q)) pts += 10
            return pts
        }
    }
}
