// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Clean-room W3C Content Security Policy Level 3 header parser and source-expression evaluator.
 *
 * Enforces key W3C CSP3 invariants:
 * - `;`-delimited directives; duplicate directive names obey first-directive-wins (§2.2.1) while
 *   recording duplicates for audit.
 * - Fetch directives (`script-src`, `style-src`, `img-src`, `connect-src`, `font-src`, `media-src`,
 *   `frame-src`, `object-src`, `worker-src`) fall back to `default-src`; non-fetch directives
 *   (`frame-ancestors`, `base-uri`, `form-action`) NEVER fall back to `default-src` (§6).
 * - `'none'` rejects all loads; `'self'` requires exact `(scheme, host, effectivePort)` match.
 * - `'strict-dynamic'` on `script-src` ignores host, scheme, and `'unsafe-inline'` expressions,
 *   admitting ONLY cryptographic `'nonce-...'` or `'sha256-...'` tokens (§6.1.1).
 * - Presence of any `'nonce-...'` or `'sha256-...'` expression disables `'unsafe-inline'` for
 *   inline execution per CSP2/CSP3 backward-compatibility rules.
 */
object CspHeader {
    const val SCOPE =
        "W3C CSP3 single-header parser + URL/inline directive evaluator (default-src fallback rules, 'none', 'self', host wildcards, nonces, sha256 hashes, 'strict-dynamic'); report-to/trusted-types/path-level matching out of scope."

    const val MAX_HEADER_CHARS = 16_384

    private val FETCH_DIRECTIVES = setOf(
        "child-src", "connect-src", "font-src", "frame-src", "img-src",
        "manifest-src", "media-src", "object-src", "script-src", "style-src", "worker-src",
    )

    data class Origin(val scheme: String, val host: String, val port: Int) {
        init {
            require(scheme.isNotEmpty() && host.isNotEmpty() && port in 1..65535) {
                "invalid origin ($scheme, $host, $port)"
            }
        }
    }

    data class Policy(
        val directives: Map<String, List<String>>,
        val duplicateDirectives: List<String>,
    )

    fun parse(headerValue: String): Policy {
        require(headerValue.length <= MAX_HEADER_CHARS) { "CSP header exceeds $MAX_HEADER_CHARS chars" }
        require(headerValue.none { it.code < 0x20 && it != '\t' || it.code == 0x7F }) {
            "CSP header contains control characters (CRLF injection refused)"
        }
        val map = LinkedHashMap<String, List<String>>()
        val duplicates = ArrayList<String>()

        for (rawSegment in headerValue.split(';')) {
            val trimmed = rawSegment.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(Regex("\\s+"))
            val name = parts[0].lowercase()
            require(name.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) {
                "invalid CSP directive name '$name'"
            }
            if (name in map) {
                duplicates.add(name)
                continue // W3C CSP3 §2.2.1: ignore subsequent duplicate directive
            }
            map[name] = parts.drop(1)
        }
        return Policy(map, duplicates)
    }

    fun parseOrigin(rawUrl: String): Origin {
        val trim = rawUrl.trim()
        val schemeIdx = trim.indexOf("://")
        require(schemeIdx > 0) { "URL '$rawUrl' lacks hierarchical scheme://" }
        val scheme = trim.substring(0, schemeIdx).lowercase()
        val rest = trim.substring(schemeIdx + 3)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        require('@' !in authority && authority.isNotEmpty()) { "invalid or credentialed authority in '$rawUrl'" }
        val colon = authority.lastIndexOf(':')
        val (host, port) = if (colon > 0 && !authority.endsWith(']')) {
            val p = authority.substring(colon + 1).toIntOrNull()
                ?: throw IllegalArgumentException("invalid port in '$rawUrl'")
            authority.substring(0, colon).lowercase() to p
        } else {
            authority.lowercase() to defaultPort(scheme)
        }
        return Origin(scheme, host, port)
    }

    fun allowsUrl(
        policy: Policy,
        directive: String,
        documentOrigin: Origin,
        targetUrl: String,
        nonce: String? = null,
    ): Boolean {
        val dir = directive.lowercase()
        val sources = effectiveSources(policy, dir) ?: return true
        if (sources.isEmpty() || sources.any { it.equals("'none'", ignoreCase = true) }) return false

        val cleanTarget = targetUrl.trim()
        val targetScheme = cleanTarget.substringBefore(':').lowercase()
        if (targetScheme in setOf("javascript", "vbscript")) return false

        if (nonce != null && nonce.isNotEmpty()) {
            val want = "'nonce-$nonce'"
            if (sources.any { it == want }) return true
        }

        val strictDynamic = dir == "script-src" && sources.any { it.equals("'strict-dynamic'", ignoreCase = true) }
        if (strictDynamic) {
            return false // 'strict-dynamic' ignores host, scheme, and 'self' sources without a valid nonce
        }

        if (targetScheme in setOf("data", "blob")) {
            return sources.any { it.equals("$targetScheme:", ignoreCase = true) }
        }

        val target = runCatching { parseOrigin(cleanTarget) }.getOrNull() ?: return false
        for (src in sources) {
            val lower = src.lowercase()
            when {
                lower == "*" -> {
                    if (target.scheme in setOf("http", "https", "ws", "wss")) return true
                }
                lower == "'self'" -> {
                    if (target == documentOrigin) return true
                }
                lower.endsWith(":") && !lower.startsWith("'") -> {
                    if (lower.dropLast(1) == target.scheme) return true
                }
                !lower.startsWith("'") -> {
                    if (matchesHostSource(lower, target, documentOrigin)) return true
                }
            }
        }
        return false
    }

    fun allowsInline(
        policy: Policy,
        directive: String,
        nonce: String? = null,
        sha256Base64: String? = null,
    ): Boolean {
        val dir = directive.lowercase()
        val sources = effectiveSources(policy, dir) ?: return true
        if (sources.isEmpty() || sources.any { it.equals("'none'", ignoreCase = true) }) return false

        if (nonce != null && nonce.isNotEmpty() && sources.any { it == "'nonce-$nonce'" }) {
            return true
        }
        if (sha256Base64 != null && sha256Base64.isNotEmpty() &&
            sources.any { it.equals("'sha256-$sha256Base64'", ignoreCase = false) ||
                (it.lowercase().startsWith("'sha256-") && it.drop(8).dropLast(1) == sha256Base64) }
        ) {
            return true
        }

        val hasCryptoOrStrict = sources.any {
            val l = it.lowercase()
            l == "'strict-dynamic'" || l.startsWith("'nonce-") || l.startsWith("'sha256-") ||
                l.startsWith("'sha384-") || l.startsWith("'sha512-")
        }
        if (hasCryptoOrStrict) return false

        return sources.any { it.equals("'unsafe-inline'", ignoreCase = true) }
    }

    private fun effectiveSources(policy: Policy, directive: String): List<String>? {
        policy.directives[directive]?.let { return it }
        return if (directive in FETCH_DIRECTIVES) policy.directives["default-src"] else null
    }

    private fun matchesHostSource(expr: String, target: Origin, docOrigin: Origin): Boolean {
        var rem = expr
        val reqScheme = if ("://" in rem) {
            val s = rem.substringBefore("://")
            rem = rem.substringAfter("://")
            s
        } else null
        rem = rem.substringBefore('/') // strip optional path
        val colon = rem.lastIndexOf(':')
        val (hostPat, portPat) = if (colon > 0) {
            rem.substring(0, colon) to rem.substring(colon + 1)
        } else {
            rem to null
        }

        if (reqScheme != null && reqScheme != target.scheme) return false
        if (reqScheme == null && docOrigin.scheme == "https" && target.scheme == "http") return false

        if (portPat != null && portPat != "*") {
            val p = portPat.toIntOrNull() ?: return false
            if (p != target.port) return false
        } else if (portPat == null) {
            if (target.port != defaultPort(target.scheme)) return false
        }

        return if (hostPat.startsWith("*.")) {
            val suffix = hostPat.substring(1) // ".example.com"
            target.host.length > suffix.length && target.host.endsWith(suffix)
        } else {
            target.host == hostPat
        }
    }

    private fun defaultPort(scheme: String): Int = when (scheme) {
        "http", "ws" -> 80
        "https", "wss" -> 443
        else -> 443
    }
}
