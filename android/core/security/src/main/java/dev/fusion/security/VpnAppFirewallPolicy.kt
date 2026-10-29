// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Clean-room Pro Local `VpnService` Per-App UID & DNS Leak-Prevention Rule Compiler (`SC-04`).
 *
 * Enforces fail-closed local packet filtering rules (zero cloud VPN / zero external proxy server
 * owned by Fusion):
 * - Per-UID egress modes (`ALLOW_DIRECT`, `TUNNEL_ONLY_TOR_OR_OHTTP`, `BLOCK_ALL_NETWORK`, `WIFI_ONLY`).
 * - Unconditional DNS leak prevention in `STRICT_ANONYMITY` mode: blocks cleartext UDP/TCP port `53`,
 *   unpinned DoT port `853`, and LAN multicast discovery (`224.0.0.0/4`, `239.255.255.250:1900`,
 *   `5353` mDNS) outside the designated local tunnel resolver!
 */
object VpnAppFirewallPolicy {
    enum class AppRule { ALLOW_DIRECT, TUNNEL_ONLY, WIFI_ONLY, BLOCK_ALL }
    enum class TransportProto { TCP, UDP, ICMP }

    data class PacketMeta(
        val appUid: Int,
        val protocol: TransportProto,
        val dstAddress: String,
        val dstPort: Int,
        val onMeteredCellular: Boolean,
        val routedViaTrustedTunnel: Boolean,
    )

    enum class Verdict { FORWARD, DROP_APP_BLOCKED, DROP_METERED_RESTRICTED, DROP_DNS_LEAK, DROP_MULTICAST_LEAK, DROP_UNTUNNELED_EGRESS }

    data class CompiledFirewall(
        val strictAnonymityMode: Boolean,
        val defaultRule: AppRule,
        val uidRules: Map<Int, AppRule>,
        val trustedResolverEndpoint: Pair<String, Int> = "127.0.0.1" to 9053,
    ) {
        fun inspectPacket(pkt: PacketMeta): Verdict {
            require(pkt.appUid >= 0 && pkt.dstPort in 0..65_535) { "invalid PacketMeta" }
            val rule = uidRules[pkt.appUid] ?: defaultRule
            if (rule == AppRule.BLOCK_ALL) return Verdict.DROP_APP_BLOCKED
            if (rule == AppRule.WIFI_ONLY && pkt.onMeteredCellular) return Verdict.DROP_METERED_RESTRICTED

            val mustTunnel = strictAnonymityMode || rule == AppRule.TUNNEL_ONLY
            if (mustTunnel) {
                if (isMulticastOrBroadcast(pkt.dstAddress) || pkt.dstPort == 5353 || pkt.dstPort == 1900) {
                    return Verdict.DROP_MULTICAST_LEAK
                }
                if ((pkt.dstPort == 53 || pkt.dstPort == 853) &&
                    (pkt.dstAddress to pkt.dstPort) != trustedResolverEndpoint
                ) {
                    return Verdict.DROP_DNS_LEAK
                }
                if (!pkt.routedViaTrustedTunnel && (pkt.dstAddress to pkt.dstPort) != trustedResolverEndpoint) {
                    return Verdict.DROP_UNTUNNELED_EGRESS
                }
            }
            return Verdict.FORWARD
        }

        private fun isMulticastOrBroadcast(addr: String): Boolean {
            val clean = addr.trim().lowercase()
            if (clean == "255.255.255.255" || clean.startsWith("ff")) return true
            val parts = clean.split('.')
            if (parts.size == 4) {
                val first = parts[0].toIntOrNull() ?: return false
                if (first in 224..239) return true
            }
            return false
        }
    }
}
