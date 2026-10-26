// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

import java.net.URI
import java.util.Collections
import java.util.Locale

/**
 * Navigation hardening kernels shared by every browser-engine option (system WebView,
 * GeckoView, Servo). This is the policy layer — URL canonicalization, bounded allow/block
 * matching, private-session flags — not a rendering engine. Engine loading, process
 * isolation and content rendering remain separate gates with their own evidence.
 */
object SafeNavigation {
    const val MAX_URL = 2048
    const val MAX_RULES = 1024

    data class Canonical(val url: String, val host: String, val scheme: String)

    /** Strict canonicalization: https/http only, no credentials, no exotic ports, ASCII-only. */
    fun canonicalize(text: String): Canonical {
        require(text.length in 1..MAX_URL && text.all { it.code in 0x21..0x7e } && '\\' !in text) { "Use a bounded ASCII URL" }
        val uri = URI(text)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "https" || scheme == "http") { "Only http/https navigation" }
        require(uri.userInfo == null && !uri.isOpaque) { "Credentials and opaque URIs refused" }
        val host = requireNotNull(uri.host).lowercase(Locale.ROOT)
        require(host.isNotEmpty() && host.length <= 253 && uri.rawAuthority.equals(
            if (uri.port == -1) host else "$host:${uri.port}", true)) { "Encoded hosts and odd authorities refused" }
        if (uri.port != -1) require(uri.port in 1..65535 && uri.port != 80 && uri.port != 443) { "Default/exotic ports refused" }
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return Canonical("$scheme://$host$port$path$query", host, scheme)
    }

    /** Host rules: "example.com" matches exactly; ".example.com" matches the domain and subdomains. */
    data class RuleSet(val allow: Set<String>, val block: Set<String>) {
        init {
            require(allow.size + block.size in 1..MAX_RULES)
            (allow + block).forEach { rule ->
                val bare = rule.removePrefix(".")
                require(rule.length in 1..253 && bare.split('.').size >= 2 &&
                    bare.all { it.isLetterOrDigit() || it == '.' || it == '-' }) { "Bad host rule: $rule" }
                require(rule == rule.lowercase(Locale.ROOT)) { "Rules must be lowercase: $rule" }
            }
            require(allow.intersect(block).isEmpty()) { "A host cannot be both allowed and blocked" }
        }
        private fun matches(host: String, rule: String) = if (rule.startsWith(".")) host == rule.drop(1) || host.endsWith(rule) else host == rule
        /** Block wins over allow; unknown hosts are denied when an allowlist is non-empty (default-deny). */
        fun decide(host: String): Boolean {
            val h = host.lowercase(Locale.ROOT)
            if (block.any { matches(h, it) }) return false
            if (allow.isEmpty()) return true
            return allow.any { matches(h, it) }
        }
    }

    /** Private-session flags: data-model defaults, not a renderer proof. */
    data class PrivateSession(val persistCookies: Boolean = false, val persistStorage: Boolean = false,
                              val persistHistory: Boolean = false, val thirdPartyCookies: Boolean = false) {
        init { require(!persistCookies && !persistStorage && !persistHistory && !thirdPartyCookies) { "Private sessions retain nothing" } }
        fun disclosure() = "Private session: no cookies, site storage or history are retained by policy. " +
            "This does not hide your IP address, defeat fingerprinting, or make the session anonymous."
    }

    /** Sorted unique hosts from a history export; bounded, for the explicit review UI. */
    fun historyHosts(entries: List<String>): List<String> {
        require(entries.size <= 4096)
        return Collections.unmodifiableList(entries.map { canonicalize(it).host }.distinct().sorted())
    }
}
