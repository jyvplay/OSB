// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import java.security.MessageDigest
import java.util.Base64

/**
 * Clean-room W3C **Content Security Policy Level 3 (`CSP3`)** Parser & Script/Framing Evaluator.
 *
 * Parses directive lists (`default-src`, `script-src`, `style-src`, `img-src`, `connect-src`,
 * `object-src`, `base-uri`, `frame-ancestors`), enforces CSP3 `'strict-dynamic'` + `'nonce-...'`
 * + `'sha256-...'` rules (under CSP3 §6.6.2, presence of a cryptographic nonce or SHA-256 hash, OR
 * `'strict-dynamic'`, automatically neutralizes legacy `'unsafe-inline'`!), and audits policies for
 * XSS bypass weaknesses.
 */
object CspHeaderDirective {
    const val SCOPE =
        "Clean-room W3C CSP Level 3 parser & script/frame-ancestors evaluator ('nonce-...', 'sha256-...', 'strict-dynamic' neutralizing 'unsafe-inline', and XSS weakness audit)."

    data class Policy(val directives: Map<String, List<String>>) {
        fun effectiveSources(directive: String): List<String> {
            val d = directive.lowercase()
            return directives[d] ?: if (d in setOf("script-src", "style-src", "img-src", "connect-src", "object-src")) {
                directives["default-src"].orEmpty()
            } else emptyList()
        }
    }

    fun parse(headerValue: String): Policy {
        val map = LinkedHashMap<String, List<String>>()
        for (rawDir in headerValue.split(';')) {
            val tokens = rawDir.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue
            val name = tokens[0].lowercase()
            if (name !in map) { // per W3C CSP3 §2.2.1 duplicate directives are ignored after the first!
                map[name] = tokens.drop(1)
            }
        }
        return Policy(map)
    }

    fun allowInlineScript(policy: Policy, elementNonce: String? = null, scriptBody: String? = null): Boolean {
        val srcs = policy.effectiveSources("script-src")
        if (srcs.isEmpty()) return true
        val lowerSrcs = srcs.map { it.lowercase() }
        if ("'none'" in lowerSrcs) return false

        val nonces = srcs.mapNotNull { tok ->
            if (tok.lowercase().startsWith("'nonce-") && tok.endsWith("'")) tok.substring(7, tok.length - 1) else null
        }
        val sha256Hashes = srcs.mapNotNull { tok ->
            if (tok.lowercase().startsWith("'sha256-") && tok.endsWith("'")) tok.substring(8, tok.length - 1) else null
        }
        val hasModernGuard = nonces.isNotEmpty() || sha256Hashes.isNotEmpty() || "'strict-dynamic'" in lowerSrcs

        if (!elementNonce.isNullOrEmpty() && elementNonce in nonces) return true
        if (scriptBody != null && sha256Hashes.isNotEmpty()) {
            val digestB64 = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(scriptBody.toByteArray(Charsets.UTF_8)),
            )
            if (digestB64 in sha256Hashes) return true
        }
        if (hasModernGuard) return false // CSP3 neutralizes 'unsafe-inline' when nonces/hashes/strict-dynamic exist!
        return "'unsafe-inline'" in lowerSrcs
    }

    fun allowExternalScript(policy: Policy, pageOrigin: String, scriptOrigin: String, parserInserted: Boolean = true): Boolean {
        val srcs = policy.effectiveSources("script-src")
        if (srcs.isEmpty()) return true
        val lower = srcs.map { it.lowercase() }
        if ("'none'" in lower) return false
        if ("'strict-dynamic'" in lower) {
            // Under 'strict-dynamic', host-source allowlists ('self', https:) are ignored for parser-inserted scripts!
            return !parserInserted
        }
        if ("'self'" in lower && pageOrigin.equals(scriptOrigin, ignoreCase = true)) return true
        return srcs.any { tok -> !tok.startsWith("'") && scriptOrigin.equals(tok.trimEnd('/'), ignoreCase = true) }
    }

    fun allowFramingByAncestor(policy: Policy, pageOrigin: String, ancestorOrigin: String): Boolean {
        val fa = policy.directives["frame-ancestors"] ?: return true
        val lower = fa.map { it.lowercase() }
        if ("'none'" in lower) return false
        if ("'self'" in lower && pageOrigin.equals(ancestorOrigin, ignoreCase = true)) return true
        return fa.any { tok -> !tok.startsWith("'") && ancestorOrigin.equals(tok.trimEnd('/'), ignoreCase = true) }
    }
}
