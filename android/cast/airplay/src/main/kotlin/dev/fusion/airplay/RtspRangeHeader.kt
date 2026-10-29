// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

import java.util.Locale

/**
 * Clean-room RFC 2326 / Wi-Fi Display (`Miracast`) RTSP `Range`, `Session`, and `Transport`
 * header parser & canonical serializer (`RtspRangeHeader`).
 */
object RtspRangeHeader {
    const val SCOPE =
        "RFC 2326 / Wi-Fi Display RTSP Range (npt=sec & HH:MM:SS), Session (id;timeout), and Transport (RTP/AVP unicast client_port) codec; RTP jitter buffer out of scope."

    data class NptRange(val startSec: Double?, val endSec: Double?, val isNowStart: Boolean = false) {
        init {
            if (!isNowStart) {
                require(startSec != null && startSec >= 0.0 && startSec.isFinite()) { "Invalid NPT start: $startSec" }
                if (endSec != null) require(endSec >= startSec && endSec.isFinite()) { "NPT end ($endSec) < start ($startSec)" }
            }
        }

        fun formatHeaderValue(): String = if (isNowStart) {
            "npt=now-"
        } else {
            val s = String.format(Locale.US, "%.3f", startSec!!)
            val e = endSec?.let { String.format(Locale.US, "%.3f", it) } ?: ""
            "npt=$s-$e"
        }
    }

    data class SessionHeader(val sessionId: String, val timeoutSec: Int = 60) {
        init {
            require(sessionId.matches(Regex("^[A-Za-z0-9_.+-]{1,64}$"))) { "Invalid RTSP Session ID: $sessionId" }
            require(timeoutSec in 5..3600) { "RTSP Session timeout out of bounds: $timeoutSec" }
        }

        fun formatHeaderValue(): String = "$sessionId;timeout=$timeoutSec"
    }

    data class TransportSpec(
        val protocol: String,
        val unicast: Boolean,
        val clientRtpPort: Int,
        val clientRtcpPort: Int,
    ) {
        init {
            require(clientRtpPort in 1024..65534 && clientRtpPort % 2 == 0) {
                "RTP port must be an even unprivileged UDP port (1024..65534): $clientRtpPort"
            }
            require(clientRtcpPort == clientRtpPort + 1) {
                "RTCP port ($clientRtcpPort) must equal RTP port + 1 (${clientRtpPort + 1})"
            }
        }

        fun formatHeaderValue(): String =
            "$protocol;${if (unicast) "unicast" else "multicast"};client_port=$clientRtpPort-$clientRtcpPort"
    }

    fun parseRange(rawValue: String): NptRange {
        val v = rawValue.trim()
        require(v.startsWith("npt=", ignoreCase = true)) { "Unsupported RTSP Range unit: $rawValue" }
        val body = v.substring(4).trim()
        val dash = body.indexOf('-')
        require(dash > 0) { "Malformed RTSP npt= range: $rawValue" }
        val lhs = body.substring(0, dash).trim()
        val rhs = body.substring(dash + 1).trim()
        if (lhs.equals("now", ignoreCase = true)) {
            require(rhs.isEmpty()) { "npt=now- must have open end" }
            return NptRange(null, null, isNowStart = true)
        }
        val start = parseNptTime(lhs)
        val end = if (rhs.isEmpty()) null else parseNptTime(rhs)
        return NptRange(start, end)
    }

    fun parseSession(rawValue: String): SessionHeader {
        val parts = rawValue.split(';').map { it.trim() }
        val id = parts.first()
        var timeout = 60
        for (p in parts.drop(1)) {
            if (p.startsWith("timeout=", ignoreCase = true)) {
                timeout = p.substring(8).trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid RTSP timeout parameter: $p")
            }
        }
        return SessionHeader(id, timeout)
    }

    fun parseTransport(rawValue: String): TransportSpec {
        val parts = rawValue.split(';').map { it.trim() }
        val proto = parts.firstOrNull() ?: error("Empty Transport header")
        require(proto.startsWith("RTP/AVP")) { "Unsupported RTSP transport protocol: $proto" }
        val unicast = parts.none { it.equals("multicast", ignoreCase = true) }
        val portParam = parts.firstOrNull { it.startsWith("client_port=", ignoreCase = true) }
            ?: throw IllegalArgumentException("Missing client_port= in RTSP Transport")
        val portRange = portParam.substringAfter('=').split('-')
        val rtp = portRange[0].toInt()
        val rtcp = if (portRange.size > 1) portRange[1].toInt() else rtp + 1
        return TransportSpec(proto, unicast, rtp, rtcp)
    }

    private fun parseNptTime(s: String): Double = if (s.contains(':')) {
        val p = s.split(':')
        require(p.size == 3) { "Invalid HH:MM:SS NPT timestamp: $s" }
        p[0].toLong() * 3600.0 + p[1].toLong() * 60.0 + p[2].toDouble()
    } else {
        s.toDouble()
    }
}
