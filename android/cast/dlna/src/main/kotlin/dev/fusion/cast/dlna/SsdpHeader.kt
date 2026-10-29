// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

/**
 * Clean-room UPnP Device Architecture 1.1 SSDP HTTPMU envelope parser and private-LAN `LOCATION`
 * SSRF gate for `:cast:dlna` (hardens [DlnaClient] discovery responses).
 *
 * Counters UPnP CallStranger / metadata-SSRF / DNS-rebinding discovery attacks:
 * - Accepts ONLY `HTTP/1.1 200 OK` (M-SEARCH unicast reply) or `NOTIFY * HTTP/1.1` (ssdp:alive).
 * - Requires non-empty `USN` (`uuid:...`), `ST` or `NT`, and a valid `LOCATION` URL.
 * - Enforces that `LOCATION` uses plain `http://` over an explicit **numeric private-LAN IPv4
 *   address** (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`) or **IPv6 ULA** (`fc00::/7`),
 *   refusing `127.0.0.0/8` loopback, `169.254.0.0/16` cloud metadata (`169.254.169.254`),
 *   multicast, credentials (`user@host`), non-HTTP schemes, and DNS hostnames (DNS rebinding).
 */
object SsdpHeader {
    const val SCOPE =
        "UPnP 1.1 SSDP 200-OK / NOTIFY header parser with RFC 1918 / RFC 4193 numeric private-LAN HTTP LOCATION gate; IPv6 %zone link-local scopes and SOAP XML fetching live in DlnaClient."

    const val MAX_PACKET_CHARS = 8_192

    enum class Kind { SEARCH_RESPONSE, NOTIFY_ALIVE, NOTIFY_BYEBYE }

    data class ValidatedAnnouncement(
        val kind: Kind,
        val location: String?,
        val hostIp: String?,
        val port: Int?,
        val usn: String,
        val target: String,
        val maxAgeSeconds: Int?,
    )

    fun parseAndValidate(packetText: String): ValidatedAnnouncement {
        require(packetText.isNotEmpty() && packetText.length <= MAX_PACKET_CHARS) {
            "SSDP packet length ${packetText.length} outside 1..$MAX_PACKET_CHARS"
        }
        val lines = packetText.replace("\r\n", "\n").split('\n')
        val startLine = lines.first().trim()
        val isSearchResp = startLine.startsWith("HTTP/1.1 200", ignoreCase = true)
        val isNotify = startLine.equals("NOTIFY * HTTP/1.1", ignoreCase = true)
        require(isSearchResp || isNotify) { "unsupported SSDP start-line '$startLine'" }

        val headers = LinkedHashMap<String, String>()
        for (idx in 1 until lines.size) {
            val raw = lines[idx]
            if (raw.isEmpty()) break
            require(raw[0] != ' ' && raw[0] != '\t') { "obs-fold header continuation refused in SSDP" }
            val colon = raw.indexOf(':')
            require(colon > 0) { "malformed SSDP header line '$raw'" }
            val name = raw.substring(0, colon).trim().lowercase()
            val value = raw.substring(colon + 1).trim()
            require(value.none { it.code < 0x20 || it.code == 0x7F }) { "control char in SSDP header '$name'" }
            headers.putIfAbsent(name, value)
        }

        val usn = headers["usn"] ?: throw IllegalArgumentException("SSDP missing USN header")
        require(usn.lowercase().startsWith("uuid:")) { "SSDP USN '$usn' must start with uuid:" }

        val kind = if (isSearchResp) {
            Kind.SEARCH_RESPONSE
        } else {
            when (val nts = headers["nts"]?.lowercase()) {
                "ssdp:alive" -> Kind.NOTIFY_ALIVE
                "ssdp:byebye" -> Kind.NOTIFY_BYEBYE
                else -> throw IllegalArgumentException("unsupported SSDP NTS '$nts'")
            }
        }

        val target = (if (isSearchResp) headers["st"] else headers["nt"])
            ?: throw IllegalArgumentException("SSDP missing ST/NT target header")
        require(target.isNotEmpty()) { "empty SSDP ST/NT target" }

        val maxAge = headers["cache-control"]?.let(::parseMaxAge)

        if (kind == Kind.NOTIFY_BYEBYE) {
            return ValidatedAnnouncement(kind, null, null, null, usn, target, maxAge)
        }

        val loc = headers["location"] ?: throw IllegalArgumentException("SSDP missing LOCATION header")
        val (hostIp, port) = validatePrivateLanHttpLocation(loc)
        return ValidatedAnnouncement(kind, loc, hostIp, port, usn, target, maxAge)
    }

    fun isPrivateLanIpLiteral(host: String): Boolean =
        if (host.startsWith("[") && host.endsWith("]")) {
            isIpv6Ula(host.substring(1, host.length - 1))
        } else {
            isRfc1918Ipv4(host)
        }

    private fun validatePrivateLanHttpLocation(url: String): Pair<String, Int> {
        require(url.startsWith("http://", ignoreCase = true)) {
            "SSDP LOCATION '$url' must use plain http://"
        }
        val afterScheme = url.substring(7)
        val slash = afterScheme.indexOf('/').let { if (it < 0) afterScheme.length else it }
        require('?' !in afterScheme.substring(0, slash) && '#' !in afterScheme) {
            "malformed authority or fragment in SSDP LOCATION '$url'"
        }
        val authority = afterScheme.substring(0, slash)
        require(authority.isNotEmpty() && '@' !in authority) {
            "empty or credentialed authority refused in SSDP LOCATION '$url'"
        }

        val (host, port) = if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            require(close > 1) { "unclosed IPv6 literal in '$url'" }
            val h = authority.substring(0, close + 1)
            val rem = authority.substring(close + 1)
            val p = if (rem.isEmpty()) 80 else {
                require(rem.startsWith(":")) { "garbage after IPv6 literal in '$url'" }
                rem.substring(1).toIntOrNull() ?: throw IllegalArgumentException("bad port in '$url'")
            }
            h to p
        } else {
            val colon = authority.indexOf(':')
            if (colon < 0) authority to 80 else {
                val p = authority.substring(colon + 1).toIntOrNull()
                    ?: throw IllegalArgumentException("bad port in '$url'")
                authority.substring(0, colon) to p
            }
        }
        require(port in 1..65535) { "SSDP LOCATION port $port outside 1..65535" }
        require(isPrivateLanIpLiteral(host)) {
            "SSDP LOCATION host '$host' refused (must be RFC 1918 IPv4 or RFC 4193 IPv6 ULA literal)"
        }
        return host.removeSurrounding("[", "]") to port
    }

    private fun isRfc1918Ipv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = IntArray(4)
        for (i in 0..3) {
            val s = parts[i]
            if (s.isEmpty() || s.length > 3 || (s.length > 1 && s[0] == '0') || !s.all { it.isDigit() }) return false
            val v = s.toInt()
            if (v !in 0..255) return false
            octets[i] = v
        }
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    private fun isIpv6Ula(addr: String): Boolean {
        if ('%' in addr || addr.length < 4) return false
        if (!addr.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' }) return false
        val firstGroup = addr.substringBefore(':')
        if (firstGroup.length != 4) return false
        val word = firstGroup.toIntOrNull(16) ?: return false
        return (word and 0xFE00) == 0xFC00 // fc00::/7 Unique Local Unicast
    }

    private fun parseMaxAge(cc: String): Int? {
        for (part in cc.split(',')) {
            val t = part.trim()
            if (t.startsWith("max-age=", ignoreCase = true)) {
                val v = t.substringAfter('=').trim().toIntOrNull()
                    ?: throw IllegalArgumentException("invalid SSDP max-age in '$cc'")
                require(v in 0..864_000) { "SSDP max-age $v out of bounds" }
                return v
            }
        }
        return null
    }
}
