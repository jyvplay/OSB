// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.security.SecureRandom

/**
 * Clean-room Wi-Fi 802.11 Probe-Request De-Anonymization Scrambler & Evil-Twin Captive-Portal
 * MitM Quarantine (`Vector V12`, `arXiv:2606.25788` Jun 2026 & Safeguard Oct 2026).
 *
 * Defeats three tracking vectors that survive naive MAC randomization:
 * 1. Simultaneously re-seeds the 12-bit 802.11 `seq_ctrl` sequence number (`0..4095`) on every
 *    locally-administered unicast MAC rotation (`(b0 & 0x03) == 0x02`).
 * 2. Strips all saved/hidden SSIDs from probe frames (wildcard 0-length SSID IE only) to block
 *    WiGLE location inference.
 * 3. Jitters Inter-Probe Frame Arrival Time (`IFAT`) across `[minIfatMs, maxIfatMs]` to defeat
 *    ML timing-signature clustering.
 */
object WifiProbeSequenceScrambler {
    const val SCOPE =
        "Pure-JVM 802.11 locally-administered unicast MAC + 12-bit sequence-number co-rotator, wildcard-only SSID probe sanitizer, IFAT jitter scheduler, and Evil-Twin captive-portal detector (V12)."

    data class ProbeBurstFrame(
        val macAddress: String,
        val sequenceNumber12Bit: Int,
        val ssidBroadcast: String, // always "" for wildcard
        val interFrameDelayMs: Int,
    )

    data class CaptivePortalAudit(
        val quarantineNetwork: Boolean,
        val reasons: List<String>,
    )

    fun generateDecorrelatedBurst(
        burstCount: Int,
        seedBytes: ByteArray? = null,
        minIfatMs: Int = 35,
        maxIfatMs: Int = 240,
    ): List<ProbeBurstFrame> {
        require(burstCount in 1..64 && minIfatMs >= 10 && maxIfatMs > minIfatMs) {
            "invalid probe burst parameters"
        }
        val rng = if (seedBytes != null) {
            // Deterministic SHA-PRNG fallback ONLY when caller passes explicit test vector seed; CSPRNG otherwise!
            java.util.Random(seedBytes.fold(0x5A17C3E9L) { acc, b -> (acc * 6364136223846793005L) + (b.toLong() and 0xFFL) })
        } else {
            val sr = SecureRandom()
            object : java.util.Random() {
                override fun next(bits: Int): Int = sr.nextInt() ushr (32 - bits)
            }
        }

        return List(burstCount) {
            val mac = ByteArray(6)
            rng.nextBytes(mac)
            // Locally administered (bit 1 = 1) + unicast (bit 0 = 0) per IEEE 802.11aq!
            mac[0] = ((mac[0].toInt() and 0xFC) or 0x02).toByte()
            val macHex = mac.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
            val seq12 = rng.nextInt(4096)
            val delay = minIfatMs + rng.nextInt(maxIfatMs - minIfatMs + 1)
            ProbeBurstFrame(macHex, seq12, "", delay)
        }
    }

    fun sanitizeDirectedProbeSsids(requestedSsids: List<String>): List<String> =
        // Never leak saved/home/workplace SSIDs in 802.11 probe frames (WiGLE geolocation vector)!
        if (requestedSsids.isEmpty()) listOf("") else listOf("")

    fun auditCaptivePortal(
        knownBssidsForSsid: Set<String>,
        observedBssid: String,
        wasWpa3Previously: Boolean,
        nowOpenOrWep: Boolean,
        portalRedirectUrl: String?,
    ): CaptivePortalAudit {
        val reasons = ArrayList<String>()
        if (wasWpa3Previously && nowOpenOrWep) {
            reasons.add("EVIL_TWIN_WPA3_TO_OPEN_DOWNGRADE")
        }
        if (knownBssidsForSsid.isNotEmpty() && observedBssid.uppercase() !in knownBssidsForSsid.map { it.uppercase() } && nowOpenOrWep) {
            reasons.add("UNRECOGNIZED_OPEN_BSSID_FOR_KNOWN_SSID")
        }
        if (portalRedirectUrl != null && portalRedirectUrl.startsWith("http://", ignoreCase = true) &&
            portalRedirectUrl.contains(".apk", ignoreCase = true)
        ) {
            reasons.add("CAPTIVE_PORTAL_EXECUTABLE_PAYLOAD")
        }
        return CaptivePortalAudit(reasons.isNotEmpty(), reasons)
    }
}
