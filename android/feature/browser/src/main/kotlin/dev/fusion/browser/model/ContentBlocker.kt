// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

import java.util.Collections
import java.util.Locale

/**
 * Bounded content-block compiler for two list formats only:
 * hosts lines (`0.0.0.0 host` / `127.0.0.1 host`, `#` comments) and an adblock-style
 * subset (`||domain^` block, `@@||domain^` exception). Anything else is REFUSED with a
 * line number — this is an explicit subset, not an adblock-syntax implementation.
 * Decision order: exception wins, then block, else allow. Matching is
 * case-insensitive with trailing dots stripped. Lists are user-supplied data, never a
 * built-in verdict about the web.
 */
object ContentBlocker {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_HOSTS = 100_000

    data class Compiled(val blocked: Set<String>, val exceptions: Set<String>) {
        init {
            require(blocked.size + exceptions.size in 1..MAX_HOSTS)
            require(blocked.intersect(exceptions).isEmpty()) { "A domain cannot be both blocked and excepted" }
        }
        private fun matches(host: String, domain: String) = host == domain || host.endsWith(".$domain")
        /** Exception wins over block; unknown hosts allowed. This is filtering, not safety proof. */
        fun decide(rawHost: String): Boolean {
            val host = rawHost.trim().trimEnd('.').lowercase(Locale.ROOT)
            require(host.isNotEmpty() && host.length <= 253) { "Bad host" }
            if (exceptions.any { matches(host, it) }) return true
            if (blocked.any { matches(host, it) }) return false
            return true
        }
    }

    private fun cleanDomain(token: String, line: Int): String {
        val host = token.trim().trimEnd('.').lowercase(Locale.ROOT)
        require(host.length in 4..253 && '.' in host && host.split('.').size >= 2 &&
            host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) { "Bad hostname at line $line" }
        require(!host.first().isDigit() || host.any { it.isLetter() }) { "Bare IPs are not host rules (line $line)" }
        return host
    }

    fun compile(text: String): Compiled {
        require(text.length <= MAX_BYTES) { "Blocklist exceeds $MAX_BYTES bytes" }
        val blocked = LinkedHashSet<String>()
        val exceptions = LinkedHashSet<String>()
        text.lines().forEachIndexed { n, raw ->
            val lineNo = n + 1
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            require(line.length <= 512) { "Line $lineNo too long" }
            require(blocked.size + exceptions.size < MAX_HOSTS) { "More than $MAX_HOSTS hosts" }
            if (line.startsWith("@@||") && line.endsWith("^")) {
                exceptions += cleanDomain(line.removePrefix("@@||").removeSuffix("^"), lineNo)
            } else if (line.startsWith("||") && line.endsWith("^")) {
                blocked += cleanDomain(line.removePrefix("||").removeSuffix("^"), lineNo)
            } else {
                val parts = line.split(Regex("\\s+"))
                require(parts.size == 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) { "Unsupported syntax at line $lineNo" }
                require(parts[1].lowercase(Locale.ROOT) != "localhost") { "localhost must never be blocked (line $lineNo)" }
                blocked += cleanDomain(parts[1], lineNo)
            }
        }
        return Compiled(Collections.unmodifiableSet(blocked), Collections.unmodifiableSet(exceptions))
    }
}
