// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Clean-room NFC Host Card Emulation (`HCE`) Relay Attack Distance-Bounding & Tap-Skimming Gate
 * (`Vector V13`, `NDSS 2025-703` & ISO/IEC 14443-4 proximity timing bounds).
 */
object NfcRelayDistanceBoundingGate {
    const val SCOPE =
        "Pure-JVM ISO-14443 NFC HCE relay-attack distance-bounding gate enforcing <= 12.0 ms physical APDU round-trip latency, single-use 128-bit challenge nonces, and screen-unlocked foreground policy (V13)."

    const val MAX_GENUINE_PROXIMITY_RTT_MS = 12.0

    data class ApduExchangeSample(
        val challengeNonceHex32: String,
        val measuredRttMs: Double,
        val screenUnlocked: Boolean,
        val fusionInForeground: Boolean,
        val rfFieldDropCountInTx: Int,
    )

    data class BoundingDecision(
        val apduPermitted: Boolean,
        val lockSessionImmediately: Boolean,
        val violationCodes: List<String>,
    )

    class ProximitySessionVerifier {
        private val consumedNonces = HashSet<String>()
        private var locked = false

        fun verifyExchange(sample: ApduExchangeSample): BoundingDecision {
            val violations = ArrayList<String>()
            if (locked) violations.add("SESSION_ALREADY_LOCKED_BY_PRIOR_ANOMALY")
            if (!sample.screenUnlocked) violations.add("SCREEN_LOCKED_TAP_SKIMMING_BLOCKED")
            if (!sample.fusionInForeground) violations.add("BACKGROUND_HCE_RELAY_BLOCKED")
            if (!sample.measuredRttMs.isFinite() || sample.measuredRttMs <= 0.0) {
                violations.add("INVALID_RTT_TELEMETRY")
            } else if (sample.measuredRttMs > MAX_GENUINE_PROXIMITY_RTT_MS) {
                violations.add("RELAY_DISTANCE_BOUND_EXCEEDED_${"%.1f".format(sample.measuredRttMs)}MS")
            }
            if (sample.rfFieldDropCountInTx > 1) {
                violations.add("SUSPICIOUS_RF_FIELD_TEARING")
            }
            val nonce = sample.challengeNonceHex32.trim().uppercase()
            if (nonce.length != 32 || !nonce.all { it in '0'..'9' || it in 'A'..'F' }) {
                violations.add("MALFORMED_128BIT_CHALLENGE_NONCE")
            } else if (!consumedNonces.add(nonce)) {
                violations.add("REPLAYED_APDU_CHALLENGE_NONCE")
            }

            if (violations.isNotEmpty()) locked = true
            return BoundingDecision(violations.isEmpty(), locked, violations)
        }
    }
}
