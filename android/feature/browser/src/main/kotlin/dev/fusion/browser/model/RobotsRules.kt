// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/** One User-agent group with its Allow/Disallow path prefixes. */
data class RobotsGroup(val agents: List<String>, val allow: List<String>, val disallow: List<String>)

/**
 * robots.txt subset (RFC 9309 shape): User-agent groups with Allow/Disallow
 * path prefixes. Matching: exact case-insensitive UA wins over `*` groups
 * (RFC product-token prefix matching is NOT implemented — a versioned UA
 * like `Googlebot/2.1` falls back to `*`); longest prefix match wins;
 * Allow beats Disallow on ties; no match allows. Empty Disallow values
 * allow-all (ignored). Comments (`#`) and unknown fields are skipped.
 * Caps: 10k lines, 256 groups, 4k rules. Caller passes the path-query
 * string; matching is pure prefix work on it.
 */
object RobotsRules {
    const val MAX_LINES = 10_000
    const val MAX_GROUPS = 256
    const val MAX_RULES = 4_096

    fun parse(text: String): List<RobotsGroup> {
        val lines = text.split('\n')
        require(lines.size <= MAX_LINES) { "over $MAX_LINES lines" }
        val groups = mutableListOf<RobotsGroup>()
        var agents = mutableListOf<String>()
        var allow = mutableListOf<String>()
        var disallow = mutableListOf<String>()
        var inRules = false
        fun flush() {
            if (agents.isNotEmpty()) {
                require(groups.size < MAX_GROUPS) { "over $MAX_GROUPS groups" }
                groups += RobotsGroup(agents.toList(), allow.toList(), disallow.toList())
            }
            agents = mutableListOf(); allow = mutableListOf(); disallow = mutableListOf()
            inRules = false
        }
        for (raw in lines) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val field = line.substringBefore(':').trim().lowercase()
            val value = line.substringAfter(':', "").trim()
            if (':' !in line) continue
            when (field) {
                "user-agent" -> {
                    if (inRules) flush()
                    if (value.isNotEmpty()) agents += value.lowercase()
                }
                "allow", "disallow" -> {
                    if (agents.isEmpty()) continue
                    inRules = true
                    if (value.isEmpty()) continue
                    require(allow.size + disallow.size < MAX_RULES) { "over $MAX_RULES rules" }
                    if (field == "allow") allow += value else disallow += value
                }
                else -> Unit
            }
        }
        flush()
        return groups
    }

    /** True when [userAgent] may fetch [pathQuery] under [groups]. */
    fun allowed(groups: List<RobotsGroup>, userAgent: String, pathQuery: String): Boolean {
        val ua = userAgent.lowercase()
        val exact = groups.filter { g -> g.agents.any { it == ua } }
        val pool = (if (exact.isNotEmpty()) exact else groups.filter { "*" in it.agents })
            .flatMap { g -> g.allow.map { it to true } + g.disallow.map { it to false } }
            .filter { (prefix, _) -> pathQuery.startsWith(prefix) }
        if (pool.isEmpty()) return true
        val longest = pool.maxOf { it.first.length }
        val winners = pool.filter { it.first.length == longest }
        return winners.any { it.second }
    }
}
