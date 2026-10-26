package dev.fusion.privacy

/**
 * IP-address log redaction (prefix-keeping anonymization as practiced by
 * major log pipelines: keep /24 for IPv4 and /48 for IPv6, zero the
 * host-identifying remainder).
 *
 * IPv4 parsing is strict: exactly four dot-separated decimal octets
 * 0..255, no leading zeros (inet_aton's octal ambiguity is refused).
 * IPv6 parsing handles `::` compression, 1..4 hex digits per group, and
 * refuses mixed-notation (embedded IPv4) as a declared narrowing.
 *
 * Honesty: prefix-keeping redaction reduces identifiability; it is NOT
 * anonymity — a /24 still narrows to one network. The scope string says
 * so; callers surface that wording, not a stronger one.
 *
 * Declared narrowings: no CIDR math beyond the two fixed prefixes, no
 * zone indices (%eth0), no reverse DNS.
 */
object IpRedact {
    const val SCOPE = "Keep /24 (v4) and /48 (v6), zero the rest; reduces identifiability, is NOT anonymity."

    fun parseV4(s: String): IntArray {
        val parts = s.split('.')
        require(parts.size == 4) { "IPv4 needs 4 octets, got ${parts.size}" }
        return IntArray(4) { i ->
            val p = parts[i]
            require(p.isNotEmpty() && p.length <= 3 && p.all { it.isDigit() }) { "octet '$p' malformed" }
            require(!(p.length > 1 && p[0] == '0')) { "leading zero in '$p' refused (octal ambiguity)" }
            val v = p.toInt()
            require(v in 0..255) { "octet $v outside 0..255" }
            v
        }
    }

    /** 8 groups of 16 bits, `::` expanded; mixed IPv4 notation refused. */
    fun parseV6(s: String): IntArray {
        require(!s.contains('.')) { "embedded-IPv4 notation refused by declared narrowing" }
        require(!s.contains('%')) { "zone index refused" }
        val halves = s.split("::")
        require(halves.size <= 2) { "more than one '::'" }
        fun side(part: String): List<Int> =
            if (part.isEmpty()) emptyList() else part.split(':').map { g ->
                require(g.isNotEmpty() && g.length <= 4 && g.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                    "group '$g' malformed"
                }
                g.toInt(16)
            }
        val left = side(halves[0])
        val right = if (halves.size == 2) side(halves[1]) else emptyList()
        val missing = 8 - left.size - right.size
        if (halves.size == 2) require(missing >= 1) { "'::' must compress at least one group" }
        else require(missing == 0) { "IPv6 needs 8 groups, got ${left.size}" }
        return (left + List(if (halves.size == 2) missing else 0) { 0 } + right).toIntArray()
    }

    fun redactV4(s: String): String {
        val o = parseV4(s)
        return "${o[0]}.${o[1]}.${o[2]}.0"
    }

    fun redactV6(s: String): String {
        val g = parseV6(s)
        return "%x:%x:%x::".format(g[0], g[1], g[2])
    }

    fun redact(s: String): String = if (s.contains(':')) redactV6(s) else redactV4(s)

    fun isPrivateV4(s: String): Boolean {
        val o = parseV4(s)
        return o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168)
    }
}
