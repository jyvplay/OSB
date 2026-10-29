// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.net.URI

/**
 * Clean-room Tails `6.18`–`7.x` (`2025–2026`) Tor Pluggable Transport (`WebTunnel`, `Snowflake`,
 * `obfs4`) & `Conflux` dual-guard circuit configuration builder (`Vector V10`).
 */
object TorPluggableTransportConfig {
    const val SCOPE =
        "Pure-JVM Tor pluggable transport bridge validator & torrc generator (WebTunnel HTTPS/uTLS, Snowflake domain-fronted WebRTC, obfs4 IAT, Conflux dual-leg scheduling) (V10)."

    enum class TransportKind { OBFS4, WEBTUNNEL, SNOWFLAKE }

    data class BridgeDescriptor(
        val kind: TransportKind,
        val endpointOrUrl: String,
        val fingerprintHex40: String,
        val params: Map<String, String> = emptyMap(),
    )

    data class ConfluxLegMetrics(
        val legId: String,
        val guardFingerprint: String,
        val rttMs: Double,
        val cwndCells: Int,
    )

    data class TorTransportPlan(
        val transport: TransportKind,
        val torrcLines: List<String>,
        val confluxPrimaryLeg: String?,
        val primaryLegShareRatio: Double,
    )

    private val HEX40_RE = Regex("^[0-9A-Fa-f]{40}$")

    fun buildConfig(
        bridges: List<BridgeDescriptor>,
        confluxLegs: List<ConfluxLegMetrics> = emptyList(),
    ): TorTransportPlan {
        require(bridges.isNotEmpty()) { "at least one bridge descriptor required" }
        val kind = bridges.first().kind
        require(bridges.all { it.kind == kind }) { "mixed transport kinds in single profile refused" }

        val lines = ArrayList<String>()
        lines.add("UseBridges 1")
        lines.add("ClientOnly 1")
        lines.add("SafeLogging 1")
        when (kind) {
            TransportKind.WEBTUNNEL ->
                lines.add("ClientTransportPlugin webtunnel exec /usr/bin/webtunnel-client")
            TransportKind.SNOWFLAKE ->
                lines.add("ClientTransportPlugin snowflake exec /usr/bin/snowflake-client")
            TransportKind.OBFS4 ->
                lines.add("ClientTransportPlugin obfs4 exec /usr/bin/obfs4proxy")
        }

        for (b in bridges) {
            require(HEX40_RE.matches(b.fingerprintHex40)) {
                "invalid 40-hex relay fingerprint '${b.fingerprintHex40}'"
            }
            val fp = b.fingerprintHex40.uppercase()
            when (b.kind) {
                TransportKind.WEBTUNNEL -> {
                    val u = URI(b.endpointOrUrl)
                    require(u.scheme?.lowercase() == "https" && !u.host.isNullOrBlank()) {
                        "WebTunnel bridge requires https:// URL, got '${b.endpointOrUrl}'"
                    }
                    val ver = b.params["ver"] ?: "0.0.2"
                    lines.add("Bridge webtunnel 192.0.2.3:1 $fp url=${b.endpointOrUrl} ver=$ver")
                }
                TransportKind.SNOWFLAKE -> {
                    val front = b.params["front"]?.takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("Snowflake bridge requires domain-fronting 'front' host")
                    val ice = b.params["ice"]?.takeIf { it.startsWith("stun:") }
                        ?: throw IllegalArgumentException("Snowflake bridge requires stun: 'ice' endpoint")
                    lines.add("Bridge snowflake 192.0.2.4:80 $fp url=${b.endpointOrUrl} front=$front ice=$ice utls-imitate=hellorandomizedalpn")
                }
                TransportKind.OBFS4 -> {
                    val cert = b.params["cert"]?.takeIf { it.length >= 24 }
                        ?: throw IllegalArgumentException("obfs4 bridge requires base64 'cert'")
                    val iat = b.params["iat-mode"] ?: "1"
                    require(iat in setOf("0", "1", "2")) { "invalid obfs4 iat-mode '$iat'" }
                    lines.add("Bridge obfs4 ${b.endpointOrUrl} $fp cert=$cert iat-mode=$iat")
                }
            }
        }

        if (confluxLegs.size >= 2) {
            require(confluxLegs.map { it.guardFingerprint.uppercase() }.distinct().size == confluxLegs.size) {
                "Conflux legs must traverse distinct entry guards"
            }
            lines.add("ConfluxEnabled 1")
            lines.add("ConfluxClientUX throughput")
            val sorted = confluxLegs.sortedBy { it.rttMs }
            val w0 = sorted[0].cwndCells / sorted[0].rttMs.coerceAtLeast(1.0)
            val w1 = sorted[1].cwndCells / sorted[1].rttMs.coerceAtLeast(1.0)
            val share = (w0 / (w0 + w1).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
            return TorTransportPlan(kind, lines, sorted[0].legId, share)
        }
        return TorTransportPlan(kind, lines, null, 1.0)
    }
}
