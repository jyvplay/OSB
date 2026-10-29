// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Clean-room Privacy Dashboard Aggregator (`SC-05`) scoring Fusion Shell's 14-vector Tails-Light &
 * local hardware origin-defense posture in real time.
 */
object PrivacyPostureDashboardEngine {
    const val SCOPE =
        "Pure-JVM 14-vector Privacy Dashboard posture scorer, active exposure summarizer, and remediation planner (SC-05)."

    data class VectorSignal(
        val vectorId: String,
        val title: String,
        val active: Boolean,
        val weight: Int,
        val remediationHint: String,
    )

    data class DashboardSnapshot(
        val score0To100: Int,
        val grade: String,
        val activeVectors: Int,
        val totalVectors: Int,
        val openRemediationSteps: List<String>,
    )

    fun defaultFourteenVectors(disabledIds: Set<String> = emptySet()): List<VectorSignal> {
        val specs = listOf(
            Triple("V1", "Tor Prop 351 Per-Origin Stream Isolation", 8),
            Triple("V2", "RFC 9458 OHTTP + RFC 9292 Padded BHTTP", 8),
            Triple("V3", "RFC 9849 TLS ECH + Tails 7.15.1 CA Blocklist", 8),
            Triple("V4", "RFC 8828 Mode 4 WebRTC Proxy-Only SDP", 7),
            Triple("V5", "ChoiceJacking USB AOAP/HID Quarantine", 8),
            Triple("V6", "Pixnapping Cross-UID Blur Surface Shield", 8),
            Triple("V7", "SonarSnoop/CoAtNet Acoustic & IMU Clamp", 7),
            Triple("V8", "Power/DVFS Side-Channel Jitter", 6),
            Triple("V9", "CSPRNG PIN + Trojan-Source/Clipboard Guard", 7),
            Triple("V10", "Tor WebTunnel/Snowflake + Conflux Multi-Path", 7),
            Triple("V11", "Bluetooth BLUFFS Mode 4 Level 4 16B Guard", 7),
            Triple("V12", "Wi-Fi 802.11 MAC+SeqNo+IFAT Probe Scrambler", 7),
            Triple("V13", "NFC HCE <=12ms Relay Distance-Bounding", 6),
            Triple("V14", "Recursive mat2 Metadata + Tempest Dither", 6),
        )
        return specs.map { (id, title, w) ->
            VectorSignal(id, title, id !in disabledIds, w, "Enable $id ($title) in Privacy Shield")
        }
    }

    fun evaluate(signals: List<VectorSignal>): DashboardSnapshot {
        require(signals.isNotEmpty()) { "signals must be non-empty" }
        val totalW = signals.sumOf { it.weight }.coerceAtLeast(1)
        val earnedW = signals.filter { it.active }.sumOf { it.weight }
        val score = ((earnedW * 100.0) / totalW).toInt().coerceIn(0, 100)
        val grade = when {
            score >= 95 -> "TAILS_HARDENED"
            score >= 80 -> "GUARDED"
            score >= 60 -> "DEGRADED"
            else -> "EXPOSED"
        }
        val remediations = signals.filterNot { it.active }.map { it.remediationHint }
        return DashboardSnapshot(score, grade, signals.count { it.active }, signals.size, remediations)
    }
}
