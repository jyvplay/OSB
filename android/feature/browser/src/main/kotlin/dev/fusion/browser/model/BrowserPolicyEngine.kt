// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Production-grade Content-Security-Policy (CSP Level 3) parser & evaluator.
 * Supports directive parsing, `default-src` fallback, `'none'`, `'self'`,
 * scheme sources (`https:`), and wildcard host matching (`*.example.com`).
 */
class BrowserPolicyEngine private constructor(
    private val directives: Map<String, List<String>>
) {
    companion object {
        const val MAX_HEADER_LEN = 16_384

        fun parse(cspHeader: String): BrowserPolicyEngine {
            require(cspHeader.length <= MAX_HEADER_LEN) { "CSP header too long" }
            val map = LinkedHashMap<String, List<String>>()
            for (rawDir in cspHeader.split(';')) {
                val trimmed = rawDir.trim()
                if (trimmed.isEmpty()) continue
                val tokens = trimmed.split(Regex("\\s+"))
                val name = tokens[0].lowercase()
                if (name !in map) {
                    map[name] = tokens.drop(1)
                }
            }
            return BrowserPolicyEngine(map)
        }
    }

    fun effectiveSources(directive: String): List<String>? {
        val d = directive.lowercase()
        return directives[d] ?: directives["default-src"]
    }

    fun isAllowed(
        directive: String,
        targetScheme: String,
        targetHost: String,
        selfScheme: String,
        selfHost: String
    ): Boolean {
        val sources = effectiveSources(directive) ?: return true
        if (sources.isEmpty() || sources.any { it.equals("'none'", ignoreCase = true) }) {
            return false
        }
        val tScheme = targetScheme.lowercase().trimEnd(':')
        val tHost = targetHost.lowercase()
        val sScheme = selfScheme.lowercase().trimEnd(':')
        val sHost = selfHost.lowercase()

        for (src in sources) {
            val s = src.lowercase()
            when {
                s == "*" -> return true
                s == "'self'" -> if (tScheme == sScheme && tHost == sHost) return true
                s.endsWith(":") -> if (tScheme == s.trimEnd(':')) return true
                s.startsWith("*.") -> {
                    val suffix = s.substring(1) // ".example.com"
                    if (tHost.endsWith(suffix) && tHost.length > suffix.length) return true
                }
                else -> {
                    val clean = s.substringAfter("://").substringBefore('/')
                    if (tHost == clean) return true
                }
            }
        }
        return false
    }
}
