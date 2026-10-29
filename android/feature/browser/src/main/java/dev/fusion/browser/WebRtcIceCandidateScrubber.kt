// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import java.security.SecureRandom
import java.util.UUID

/**
 * Clean-room IETF `RFC 8828` (*WebRTC IP Address Handling Requirements*) SDP ICE candidate
 * scrubber & `.local` mDNS ephemeral hostname masker for `:feature:browser`.
 *
 * Implements all four `RFC 8828` §5.2 modes:
 * - `MODE_1_ENUMERATE_ALL`: Discloses all candidates (requires explicit user consent).
 * - `MODE_2_DEFAULT_ROUTE_AND_LOCAL`: Masks private `typ host` LAN IPs with ephemeral RFC 4122
 *   UUIDv4 `.local` mDNS hostnames, while keeping `srflx`/`relay`.
 * - `MODE_3_DEFAULT_ROUTE_ONLY`: Drops all `typ host` candidates completely (even `.local`);
 *   keeps only STUN `srflx` and TURN `relay`.
 * - `MODE_4_DISABLE_NON_PROXIED_UDP` (**default in Tails-Light / Incognito**): Strips ALL non-proxied
 *   UDP `host`, `srflx`, and `prflx` candidates that bypass SOCKS5/Tor/OHTTP proxies to leak the
 *   user's real ISP IP, retaining only `typ relay` TCP/TLS TURN candidates!
 */
object WebRtcIceCandidateScrubber {
    enum class IpHandlingMode {
        MODE_1_ENUMERATE_ALL,
        MODE_2_DEFAULT_ROUTE_AND_LOCAL,
        MODE_3_DEFAULT_ROUTE_ONLY,
        MODE_4_DISABLE_NON_PROXIED_UDP,
    }

    data class ScrubResult(
        val scrubbedSdp: String,
        val keptCandidates: Int,
        val droppedCandidates: Int,
        val mdnsMaskedCandidates: Int,
    )

    private val csprng = SecureRandom()

    fun mintEphemeralMdnsHost(): String {
        val bytes = ByteArray(16)
        csprng.nextBytes(bytes)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        var msb = 0L
        var lsb = 0L
        for (i in 0..7) msb = (msb shl 8) or (bytes[i].toLong() and 0xFFL)
        for (i in 8..15) lsb = (lsb shl 8) or (bytes[i].toLong() and 0xFFL)
        return "${UUID(msb, lsb)}.local"
    }

    fun isPrivateOrLinkLocalIp(address: String): Boolean {
        val clean = address.trim().lowercase()
        if (clean.endsWith(".local")) return false
        if (':' in clean) {
            return clean == "::1" || clean.startsWith("fe80:") || clean.startsWith("fc") || clean.startsWith("fd")
        }
        val parts = clean.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val a = octets[0]; val b = octets[1]
        return a == 10 ||
            a == 127 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127)
    }

    fun scrubSdp(
        sdp: String,
        mode: IpHandlingMode = IpHandlingMode.MODE_4_DISABLE_NON_PROXIED_UDP,
    ): ScrubResult {
        val outLines = ArrayList<String>()
        val mdnsCache = HashMap<String, String>()
        var kept = 0
        var dropped = 0
        var masked = 0

        for (rawLine in sdp.replace("\r\n", "\n").split('\n')) {
            if (rawLine.startsWith("c=IN IP4 ") || rawLine.startsWith("c=IN IP6 ")) {
                // Prevent c= connection line from leaking raw ISP/LAN IP in Mode 3 / Mode 4!
                if (mode == IpHandlingMode.MODE_3_DEFAULT_ROUTE_ONLY ||
                    mode == IpHandlingMode.MODE_4_DISABLE_NON_PROXIED_UDP
                ) {
                    outLines.add("c=IN IP4 0.0.0.0")
                    continue
                }
            }
            if (!rawLine.startsWith("a=candidate:")) {
                outLines.add(rawLine)
                continue
            }

            // RFC 8839 format: a=candidate:<foundation> <comp-id> <transport> <priority> <conn-addr> <port> typ <type> ...
            val tokens = rawLine.trim().split(Regex("\\s+")).toMutableList()
            val typIdx = tokens.indexOf("typ")
            if (tokens.size < 8 || typIdx < 0 || typIdx + 1 >= tokens.size) {
                dropped++
                continue
            }
            val transport = tokens[2].lowercase()
            val addr = tokens[4]
            val candType = tokens[typIdx + 1].lowercase()

            when (mode) {
                IpHandlingMode.MODE_1_ENUMERATE_ALL -> {
                    outLines.add(rawLine)
                    kept++
                }
                IpHandlingMode.MODE_2_DEFAULT_ROUTE_AND_LOCAL -> {
                    if (candType == "host" && isPrivateOrLinkLocalIp(addr)) {
                        tokens[4] = mdnsCache.getOrPut(addr) { mintEphemeralMdnsHost() }
                        masked++
                    }
                    // Strip raddr private IP leak on srflx/prflx if present
                    redactRelatedAddress(tokens)
                    outLines.add(tokens.joinToString(" "))
                    kept++
                }
                IpHandlingMode.MODE_3_DEFAULT_ROUTE_ONLY -> {
                    if (candType == "host") {
                        dropped++
                    } else {
                        redactRelatedAddress(tokens)
                        outLines.add(tokens.joinToString(" "))
                        kept++
                    }
                }
                IpHandlingMode.MODE_4_DISABLE_NON_PROXIED_UDP -> {
                    // Only proxied TCP/TLS relay candidates survive; all raw UDP / host / srflx / prflx dropped!
                    if (candType == "relay" && (transport == "tcp" || transport == "tls")) {
                        redactRelatedAddress(tokens)
                        outLines.add(tokens.joinToString(" "))
                        kept++
                    } else {
                        dropped++
                    }
                }
            }
        }
        return ScrubResult(outLines.joinToString("\r\n"), kept, dropped, masked)
    }

    private fun redactRelatedAddress(tokens: MutableList<String>) {
        val raddrIdx = tokens.indexOf("raddr")
        if (raddrIdx >= 0 && raddrIdx + 1 < tokens.size) {
            tokens[raddrIdx + 1] = "0.0.0.0"
        }
        val rportIdx = tokens.indexOf("rport")
        if (rportIdx >= 0 && rportIdx + 1 < tokens.size) {
            tokens[rportIdx + 1] = "0"
        }
    }
}
