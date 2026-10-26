package dev.fusion.browser.model

/**
 * Accept-Language parsing and best-match (RFC 9110 q-values: weights
 * 0..1 with up to three decimals, default 1; q=0 means NOT
 * acceptable; ties keep header order). Matching uses the basic
 * prefix rule (`en` matches `en-US`) — full RFC 4647 lookup is named,
 * not smuggled.
 *
 * PRIVACY NOTE in scope: the header itself is a fingerprinting
 * vector; this kernel PARSES for the request-policy layer, which may
 * well decide to send less.
 */
object AcceptLang {
    const val SCOPE = "q-value parse + basic-prefix best match; RFC 4647 lookup named; header is a fingerprint vector."

    data class Pref(val tag: String, val q: Double)

    fun parse(header: String): List<Pref> {
        require(header.isNotBlank()) { "empty header" }
        val out = ArrayList<Pref>()
        for (raw in header.split(',')) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            val semi = part.indexOf(';')
            val tag = (if (semi < 0) part else part.substring(0, semi)).trim()
            require(tag == "*" || Regex("[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*").matches(tag)) { "tag '" + tag + "' malformed" }
            var q = 1.0
            if (semi >= 0) {
                val m = Regex("\\s*q=([01](\\.\\d{0,3})?)\\s*").matchEntire(part.substring(semi + 1))
                    ?: throw IllegalArgumentException("bad q parameter in '" + part + "'")
                q = m.groupValues[1].toDouble()
                require(q <= 1.0) { "q above 1" }
            }
            out.add(Pref(tag.lowercase(), q))
        }
        require(out.isNotEmpty()) { "no language tags" }
        return out.sortedByDescending { it.q }   // stable: ties keep header order
    }

    /** Best available tag, or null when nothing acceptable. */
    fun bestMatch(header: String, available: List<String>): String? {
        val prefs = parse(header)
        val avail = available.map { it.lowercase() }
        for (p in prefs) {
            if (p.q == 0.0) continue
            if (p.tag == "*") return available.firstOrNull()
            avail.indexOfFirst { it == p.tag || it.startsWith(p.tag + "-") }
                .let { if (it >= 0) return available[it] }
        }
        return null
    }
}
