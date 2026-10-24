package dev.fusion.browser.model

/**
 * Strict-Transport-Security header parsing (RFC 6797 §6.1): directives
 * separated by `;`, case-insensitive names, `max-age` REQUIRED with a
 * non-negative integer of seconds, optional valueless
 * `includeSubDomains`; a directive name appearing twice makes the
 * header invalid (§6.1 "MUST NOT appear more than once"); unknown
 * directives are IGNORED per spec (recorded, not refused — the spec
 * mandates tolerance here, so refusal would be wrong).
 *
 * `preload` is recognized as the de-facto extension it is (not RFC
 * 6797), flagged separately so policy code can treat it as hint-only.
 *
 * Declared narrowings: header VALUE parsing only — no store, no clock,
 * no eviction (maxAge of 0 means "delete policy" to the CALLER).
 */
object HstsParse {
    const val SCOPE = "RFC 6797 value parsing; unknown directives tolerated per spec; no store or clock."

    data class Hsts(
        val maxAgeSeconds: Long, val includeSubDomains: Boolean,
        val preload: Boolean, val unknownDirectives: List<String>
    )

    fun parse(headerValue: String): Hsts {
        var maxAge: Long? = null
        var subs = false
        var preload = false
        val unknown = ArrayList<String>()
        val seen = HashSet<String>()
        for (raw in headerValue.split(';')) {
            val token = raw.trim()
            if (token.isEmpty()) continue
            val eq = token.indexOf('=')
            val name = (if (eq < 0) token else token.substring(0, eq)).trim().lowercase()
            require(seen.add(name)) { "directive '$name' appears more than once (RFC 6797 forbids)" }
            when (name) {
                "max-age" -> {
                    require(eq > 0) { "max-age needs a value" }
                    val v = token.substring(eq + 1).trim().trim('"')
                    require(v.isNotEmpty() && v.all { it.isDigit() }) { "max-age '$v' is not a delta-seconds" }
                    require(v.length <= 15) { "max-age implausibly long" }
                    maxAge = v.toLong()
                }
                "includesubdomains" -> {
                    require(eq < 0) { "includeSubDomains is valueless" }
                    subs = true
                }
                "preload" -> preload = true
                else -> unknown.add(name)
            }
        }
        require(maxAge != null) { "max-age is required (header without it MUST be ignored — refuse loudly here)" }
        return Hsts(maxAge, subs, preload, unknown)
    }

    fun isValid(headerValue: String): Boolean =
        try { parse(headerValue); true } catch (_: IllegalArgumentException) { false }
}
