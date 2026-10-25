package dev.fusion.airplay

/**
 * RTSP Transport header parsing (RFC 2326 §12.39 subset the AirPlay
 * SETUP exchange uses): `proto/profile[;param[=value]]*` — e.g.
 * `RTP/AVP;unicast;client_port=3056-3057`.
 *
 * Rules: protocol RTP, profile AVP (optionally /UDP or /TCP lower
 * transport); unicast and multicast are mutually exclusive;
 * client_port/server_port take `lo-hi` ranges with hi >= lo (the
 * RTP/RTCP pair convention is hi == lo+1, SURFACED but not forced —
 * single-port setups exist).
 *
 * Declared narrowings: the parameter subset above; unknown parameters
 * are kept verbatim (RTSP peers extend here; dropping them breaks
 * interop) — the INTAKE refusal philosophy applies to malformed
 * values, not unknown names (same split as HstsParse).
 */
object RtspTransport {
    const val SCOPE = "RTP/AVP transport-spec subset; unknown params kept verbatim; malformed values refuse."

    data class PortRange(val lo: Int, val hi: Int) {
        init {
            require(lo in 1..65535 && hi in lo..65535) { "port range $lo-$hi" }
        }
        val isRtpRtcpPair: Boolean get() = hi == lo + 1
    }

    data class Transport(
        val lowerTransport: String, val unicast: Boolean, val multicast: Boolean,
        val clientPort: PortRange?, val serverPort: PortRange?, val extras: Map<String, String?>
    )

    private fun range(v: String): PortRange {
        val m = Regex("(\\d{1,5})-(\\d{1,5})").matchEntire(v)
            ?: Regex("(\\d{1,5})").matchEntire(v)?.let { return PortRange(it.groupValues[1].toInt(), it.groupValues[1].toInt()) }
            ?: throw IllegalArgumentException("port range '$v' malformed")
        return PortRange(m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }

    fun parse(header: String): Transport {
        val parts = header.split(';')
        val spec = parts[0].trim().split('/')
        require(spec.size in 2..3 && spec[0] == "RTP" && spec[1] == "AVP") { "transport spec '${parts[0]}' unsupported" }
        val lower = if (spec.size == 3) spec[2] else "UDP"
        require(lower == "UDP" || lower == "TCP") { "lower transport '$lower'" }
        var uni = false; var multi = false
        var cp: PortRange? = null; var sp: PortRange? = null
        val extras = LinkedHashMap<String, String?>()
        for (raw in parts.drop(1)) {
            val p = raw.trim()
            if (p.isEmpty()) continue
            val eq = p.indexOf('=')
            val name = (if (eq < 0) p else p.substring(0, eq)).lowercase()
            val value = if (eq < 0) null else p.substring(eq + 1)
            when (name) {
                "unicast" -> uni = true
                "multicast" -> multi = true
                "client_port" -> cp = range(value ?: throw IllegalArgumentException("client_port needs a value"))
                "server_port" -> sp = range(value ?: throw IllegalArgumentException("server_port needs a value"))
                else -> extras[name] = value
            }
        }
        require(!(uni && multi)) { "unicast and multicast are mutually exclusive" }
        return Transport(lower, uni, multi, cp, sp, extras)
    }
}
