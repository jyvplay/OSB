// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.util.Collections

/**
 * Fail-closed Tor-only firewall PROPOSAL generator (nftables) for the laptop/host
 * companion workflow, modeled on Tails' enforcement design: default-drop output, TCP
 * redirected to Tor's TransPort, DNS to Tor's DNSPort, UDP/IPv6/RFC1918-DNS dropped,
 * loopback Tor ports reachable.
 *
 * READ-ONLY by construction: this returns review text, never touches a firewall, and
 * REFUSES to generate unless the caller asserts explicit root authority on a trusted
 * machine ([authorityAttested]). Applying firewall rules is a privileged host action
 * with its own physical validation; a proposal is not enforcement, and Tor routing is
 * not anonymity by itself (endpoint, fingerprint and correlation risks remain).
 */
object FirewallPolicy {
    data class TorPorts(val transPort: Int = 9040, val dnsPort: Int = 5353, val socksPort: Int = 9050) {
        init {
            require(transPort in 1024..65535 && dnsPort in 1024..65535 && socksPort in 1024..65535)
            require(setOf(transPort, dnsPort, socksPort).size == 3) { "Tor ports must be distinct" }
        }
    }

    data class Proposal(val ruleset: String, val invariants: List<String>) {
        val text: String get() = ruleset
    }

    const val VIRT_NET = "127.192.0.0/10"

    fun generate(ports: TorPorts = TorPorts(), authorityAttested: Boolean): Proposal {
        if (!authorityAttested) throw IllegalStateException(
            "Refused: firewall proposals need explicit attested root authority on a trusted machine. " +
                "Review every rule, apply manually, and verify with leak tests; this tool applies nothing.")
        val ruleset = """
            |# REVIEW PROPOSAL ONLY — Tor-only fail-closed nftables sketch. Applies nothing by itself.
            |# Model: Tails-style enforcement. Verify on YOUR host; leaks are your responsibility.
            |table inet toronly {
            |  chain output {
            |    type filter hook output priority filter; policy drop;
            |    ct state established,related accept
            |    iifname "lo" accept
            |    tcp dport { ${ports.transPort}, ${ports.dnsPort}, ${ports.socksPort} } ip daddr 127.0.0.1 accept
            |    udp dport 53 ip daddr != 127.0.0.1 drop
            |    udp dport 53 ip daddr { 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 } drop
            |    meta protocol != ip drop
            |  }
            |  chain prerouting {
            |    type nat hook output priority dstnat; policy accept;
            |    ip daddr $VIRT_NET tcp dport != ${ports.transPort} redirect to :${ports.transPort}
            |    ip protocol tcp tcp dport != ${ports.transPort} redirect to :${ports.transPort}
            |    udp dport 53 redirect to :${ports.dnsPort}
            |  }
            |}
            |# Required host facts (NOT established by this text): Tor running with TransPort/DNSPort/SocksPort,
            |# no other inet tables accept first, IPv6 disabled or dropped, no captive-portal helpers punching holes.
        """.trimMargin()
        val invariants = listOf(
            "policy drop" to (ruleset.contains("policy drop;")),
            "established,related accept" to ruleset.contains("ct state established,related accept"),
            "transPort redirect ${ports.transPort}" to ruleset.contains("redirect to :${ports.transPort}"),
            "dnsPort redirect ${ports.dnsPort}" to ruleset.contains("redirect to :${ports.dnsPort}"),
            "loopback Tor ports reachable" to ruleset.contains("tcp dport { ${ports.transPort}, ${ports.dnsPort}, ${ports.socksPort} }"),
            "non-TCP dropped" to ruleset.contains("meta protocol != ip drop"),
            "RFC1918 DNS dropped" to ruleset.contains("192.168.0.0/16 } drop"),
            "virtual automap net $VIRT_NET" to ruleset.contains(VIRT_NET),
            "REVIEW PROPOSAL banner" to ruleset.contains("REVIEW PROPOSAL ONLY"),
            "no blanket accept" to !ruleset.lines().any { it.trim() == "accept" },
        )
        val failed = invariants.filterNot { it.second }.map { it.first }
        require(failed.isEmpty()) { "Proposal invariant broken: $failed" }
        return Proposal(ruleset, Collections.unmodifiableList(invariants.map { it.first }))
    }
}
