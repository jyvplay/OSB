// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

/**
 * Clean-room UnifiedPush (`RFC 8030` WebPush without Google Play Services, `PR-10`), GSM-7 / UCS-2
 * Multipart SMS Segment Planner (`MS-06`), and Per-Workspace VPN / Tor / Direct Route Policy Gate
 * (`PR-03`, `PR-07s`).
 */
object UnifiedPushSmsFallbackRouter {
    const val SCOPE =
        "Pure-JVM UnifiedPush distributor endpoint validator (PR-10), GSM-7/UCS-2 concatenated SMS UDH segmenter (MS-06), and per-workspace VPN/Tor/SOCKS5 fail-closed route policy gate (PR-03, PR-07s)."

    private val GSM7_BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private val GSM7_EXT = "^{}\\[~]|€"

    enum class NetworkPolicy { DIRECT_ALLOWED, REQUIRE_SYSTEM_VPN, REQUIRE_ORBOT_SOCKS5 }

    data class SmsSegmentPlan(
        val encoding: String, // "GSM-7" or "UCS-2"
        val septetOrCodeUnitCount: Int,
        val partsCount: Int,
        val parts: List<String>,
    )

    fun planSmsSegments(text: String): SmsSegmentPlan {
        require(text.isNotEmpty()) { "empty SMS body" }
        var septets = 0
        var gsmCompatible = true
        for (ch in text) {
            when {
                ch in GSM7_BASIC -> septets += 1
                ch in GSM7_EXT -> septets += 2
                else -> { gsmCompatible = false; break }
            }
        }
        return if (gsmCompatible) {
            if (septets <= 160) SmsSegmentPlan("GSM-7", septets, 1, listOf(text))
            else {
                val chunks = text.chunked(153) // 153 septets per UDH multipart frame
                SmsSegmentPlan("GSM-7", septets, chunks.size, chunks)
            }
        } else {
            val units = text.length
            if (units <= 70) SmsSegmentPlan("UCS-2", units, 1, listOf(text))
            else {
                val chunks = text.chunked(67) // 67 UTF-16 code units per UDH multipart frame
                SmsSegmentPlan("UCS-2", units, chunks.size, chunks)
            }
        }
    }

    fun selectPushEndpoint(
        installedUnifiedPushDistributors: List<String>,
        preferredDistributor: String?,
        httpsPushGatewayUrl: String,
    ): Pair<String, String> {
        require(httpsPushGatewayUrl.startsWith("https://")) { "UnifiedPush endpoint must use HTTPS" }
        require(installedUnifiedPushDistributors.none { "com.google.android.gms" in it }) {
            "proprietary Play Services FCM distributor rejected in clean-room UnifiedPush router"
        }
        require(installedUnifiedPushDistributors.isNotEmpty()) { "no UnifiedPush distributor available" }
        val chosen = if (preferredDistributor != null && preferredDistributor in installedUnifiedPushDistributors) {
            preferredDistributor
        } else {
            installedUnifiedPushDistributors.sorted().first()
        }
        return chosen to httpsPushGatewayUrl
    }

    fun checkRouteGate(
        policy: NetworkPolicy,
        vpnInterfaceUp: Boolean,
        orbotSocks5Reachable: Boolean,
    ): Pair<Boolean, String> = when (policy) {
        NetworkPolicy.DIRECT_ALLOWED -> true to "DIRECT_ROUTE_OK"
        NetworkPolicy.REQUIRE_SYSTEM_VPN ->
            if (vpnInterfaceUp) true to "VPN_TUNNEL_VERIFIED"
            else false to "FAIL_CLOSED_VPN_REQUIRED"
        NetworkPolicy.REQUIRE_ORBOT_SOCKS5 ->
            if (orbotSocks5Reachable) true to "ORBOT_SOCKS5_9050_VERIFIED"
            else false to "FAIL_CLOSED_TOR_SOCKS5_UNREACHABLE"
    }
}
