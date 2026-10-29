// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Clean-room WASI Preview 1/2 Socket Capability Firewall (`WasiSocketCapabilityGate`).
 *
 * Enforces capability-based outbound network isolation for Wasm plugins:
 * - Default-deny (`ERRNO_NOTCAPABLE = 76` per WASI `errno` table) when no socket capability token exists.
 * - Unconditionally blocks loopback (`127.0.0.0/8`, `::1`), RFC 1918 LAN (`10/8`, `172.16/12`,
 *   `192.168/16`), link-local cloud metadata (`169.254.169.254`), and multicast (`224.0.0.0/4`)
 *   to prevent SSRF / local-service pivoting from inside Wasm plugins!
 * - Enforces explicit `(host, port)` allowlists and cumulative egress byte budgets.
 */
class WasiSocketCapabilityGate(
    val allowedEndpoints: Set<Pair<String, Int>> = emptySet(),
    val maxEgressBytes: Long = 1_048_576L,
) {
    companion object {
        const val ERRNO_SUCCESS = 0
        const val ERRNO_ACCES = 2
        const val ERRNO_FBIG = 22
        const val ERRNO_NOTCAPABLE = 76
    }

    var egressBytesUsed: Long = 0L
        private set

    data class ConnectDecision(val wasiErrno: Int, val permitted: Boolean, val reason: String)

    fun checkConnect(host: String, port: Int): ConnectDecision {
        val normHost = host.trim().lowercase().removeSurrounding("[", "]")
        if (port !in 1..65_535) {
            return ConnectDecision(ERRNO_ACCES, false, "invalid TCP/UDP port $port")
        }
        if (isForbiddenLocalOrPrivateAddress(normHost)) {
            return ConnectDecision(
                ERRNO_NOTCAPABLE,
                false,
                "SSRF guard: WASI plugin forbidden from loopback/LAN/metadata address '$normHost'",
            )
        }
        if ((normHost to port) !in allowedEndpoints) {
            return ConnectDecision(
                ERRNO_NOTCAPABLE,
                false,
                "endpoint '$normHost:$port' not granted in WASI socket capability set",
            )
        }
        return ConnectDecision(ERRNO_SUCCESS, true, "capability verified")
    }

    fun recordSend(byteCount: Int): ConnectDecision {
        require(byteCount >= 0) { "negative send byteCount" }
        if (egressBytesUsed + byteCount > maxEgressBytes) {
            return ConnectDecision(
                ERRNO_FBIG,
                false,
                "WASI socket egress quota exceeded (${egressBytesUsed + byteCount} > $maxEgressBytes)",
            )
        }
        egressBytesUsed += byteCount
        return ConnectDecision(ERRNO_SUCCESS, true, "sent $byteCount bytes")
    }

    fun isForbiddenLocalOrPrivateAddress(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host == "::1" || host == "::") {
            return true
        }
        if (':' in host) {
            return host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("ff")
        }
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return a == 0 ||
                a == 10 ||
                a == 127 ||
                (a == 100 && b in 64..127) ||
                (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                a >= 224
        }
        return false
    }
}
