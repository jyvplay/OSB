// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

import java.net.URI

/**
 * Clean-room W3C `Permissions-Policy` HTTP Header Parser & Origin Delegation Gate
 * (`https://www.w3.org/TR/permissions-policy/`, RFC 8941 Structured Fields subset).
 *
 * Hardens Fusion Browser & Privacy Mode against WebUSB (`usb`), WebHID (`hid`), WebBluetooth
 * (`bluetooth`), `camera`, `microphone`, and `geolocation` abuse:
 * - Unknown or omitted high-risk hardware capabilities (`usb`, `hid`, `bluetooth`, `serial`)
 *   default to `DENY_ALL` (`=()`), blocking browser-originated hardware keylogger/implant probing!
 * - Evaluates `=()`, `=*`, `=(self)`, and explicit quoted HTTPS origin allowlists
 *   `=(self "https://trusted.example")`, intersecting parent-frame and iframe `allow` policies.
 */
object PermissionsPolicyHeader {
    const val SCOPE =
        "W3C Permissions-Policy structured dictionary parser ((), *, self, quoted HTTPS origins) + hardware-capability default-deny evaluator; RFC 8941 boolean/decimal parameters out of scope."

    const val MAX_HEADER_CHARS = 8_192

    private val HARDWARE_DEFAULT_DENY = setOf(
        "usb", "hid", "bluetooth", "serial", "midi", "camera", "microphone", "geolocation", "payment",
    )

    sealed interface AllowList {
        data object None : AllowList
        data object All : AllowList
        data class Origins(val includeSelf: Boolean, val explicitOrigins: Set<String>) : AllowList
    }

    class Policy internal constructor(private val directives: Map<String, AllowList>) {
        fun isAllowed(feature: String, documentOrigin: String, targetOrigin: String = documentOrigin): Boolean {
            val f = feature.trim().lowercase()
            val normDoc = normalizeOrigin(documentOrigin)
            val normTarget = normalizeOrigin(targetOrigin)
            val rule = directives[f] ?: if (f in HARDWARE_DEFAULT_DENY) AllowList.None else AllowList.Origins(true, emptySet())
            return when (rule) {
                AllowList.None -> false
                AllowList.All -> true
                is AllowList.Origins -> {
                    (rule.includeSelf && normTarget == normDoc) || normTarget in rule.explicitOrigins
                }
            }
        }

        /** Strict privacy lockdown preset (`usb=(), hid=(), bluetooth=(), camera=(), microphone=(), geolocation=()`). */
        fun intersectWithChild(child: Policy, parentOrigin: String, childOrigin: String): Boolean {
            for (feat in HARDWARE_DEFAULT_DENY) {
                if (child.isAllowed(feat, childOrigin, childOrigin) && !this.isAllowed(feat, parentOrigin, childOrigin)) {
                    return false
                }
            }
            return true
        }
    }

    fun parse(headerValue: String): Policy {
        require(headerValue.length <= MAX_HEADER_CHARS) { "Permissions-Policy exceeds $MAX_HEADER_CHARS chars" }
        if (headerValue.isBlank()) return Policy(emptyMap())
        val map = LinkedHashMap<String, AllowList>()
        for (rawMember in splitTopLevelCommas(headerValue)) {
            val member = rawMember.trim()
            if (member.isEmpty()) continue
            val eq = member.indexOf('=')
            require(eq > 0) { "malformed Permissions-Policy directive '$member'" }
            val feature = member.substring(0, eq).trim().lowercase()
            require(feature.matches(Regex("[a-z][a-z0-9-]*"))) { "invalid feature token '$feature'" }
            val rhs = member.substring(eq + 1).trim()
            val allow = when {
                rhs == "*" -> AllowList.All
                rhs == "()" -> AllowList.None
                rhs == "self" -> AllowList.Origins(includeSelf = true, explicitOrigins = emptySet())
                rhs.startsWith("(") && rhs.endsWith(")") -> parseInnerList(rhs.substring(1, rhs.length - 1))
                else -> throw IllegalArgumentException("unsupported allowlist syntax '$rhs' for '$feature'")
            }
            map[feature] = allow
        }
        return Policy(map)
    }

    fun strictTailsPreset(): Policy = parse(
        "usb=(), hid=(), bluetooth=(), serial=(), camera=(), microphone=(), geolocation=(), payment=()",
    )

    private fun parseInnerList(inner: String): AllowList {
        val trimmed = inner.trim()
        if (trimmed.isEmpty()) return AllowList.None
        var selfAllowed = false
        val origins = LinkedHashSet<String>()
        var i = 0
        while (i < trimmed.length) {
            while (i < trimmed.length && trimmed[i].isWhitespace()) i++
            if (i >= trimmed.length) break
            if (trimmed[i] == '"') {
                val end = trimmed.indexOf('"', i + 1)
                require(end > i) { "unclosed quoted origin in Permissions-Policy" }
                val rawOrigin = trimmed.substring(i + 1, end)
                val norm = normalizeOrigin(rawOrigin)
                require(norm.startsWith("https://")) {
                    "Permissions-Policy explicit origin '$rawOrigin' must use https://"
                }
                origins.add(norm)
                i = end + 1
            } else {
                val start = i
                while (i < trimmed.length && !trimmed[i].isWhitespace()) i++
                val tok = trimmed.substring(start, i)
                when (tok) {
                    "self" -> selfAllowed = true
                    "*" -> return AllowList.All
                    else -> throw IllegalArgumentException("unexpected inner-list token '$tok'")
                }
            }
        }
        return if (!selfAllowed && origins.isEmpty()) AllowList.None else AllowList.Origins(selfAllowed, origins)
    }

    private fun splitTopLevelCommas(s: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var inQuotes = false
        var start = 0
        for (i in s.indices) {
            when (s[i]) {
                '"' -> inQuotes = !inQuotes
                '(' -> if (!inQuotes) depth++
                ')' -> if (!inQuotes) {
                    require(depth > 0) { "unmatched ')' in Permissions-Policy" }
                    depth--
                }
                ',' -> if (!inQuotes && depth == 0) {
                    out.add(s.substring(start, i))
                    start = i + 1
                }
            }
        }
        require(!inQuotes && depth == 0) { "unbalanced quotes or parentheses in Permissions-Policy" }
        out.add(s.substring(start))
        return out
    }

    fun normalizeOrigin(raw: String): String {
        val u = URI(raw.trim())
        val scheme = u.scheme?.lowercase() ?: throw IllegalArgumentException("missing scheme in origin '$raw'")
        val host = u.host?.lowercase() ?: throw IllegalArgumentException("missing host in origin '$raw'")
        val defaultPort = when (scheme) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
        val portPart = if (u.port == -1 || u.port == defaultPort) "" else ":${u.port}"
        return "$scheme://$host$portPart"
    }
}
