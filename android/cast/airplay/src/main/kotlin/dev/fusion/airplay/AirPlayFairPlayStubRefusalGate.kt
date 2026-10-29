package dev.fusion.airplay

/**
 * Clean-room AirPlay FairPlay (`fp-setup`) DRM Refusal & Open Profile Downgrade Gate (Apache-2.0).
 *
 * Enforces legal & clean-room hygiene (`CA-04` / `AP-05`): proprietary FairPlay key-exchange blobs
 * (`FPLY` magic) are explicitly refused with HTTP `451` / `501` status and an auditable clean-room
 * negotiation downgrade to unencumbered Curve25519/Ed25519 + ChaCha20-Poly1305 (`Pair-Setup` transient/PIN).
 */
object AirPlayFairPlayStubRefusalGate {
    const val SCOPE =
        "Clean-room FairPlay (FPLY / fp-setup) DRM refusal gate + Curve25519/ChaCha20-Poly1305 unencumbered profile negotiator"

    enum class HandshakeOutcome { ACCEPTED_OPEN_PAIR_VERIFY, REFUSED_PROPRIETARY_FAIRPLAY_DRM, MALFORMED_RTSP_REQUEST }

    data class RtspNegotiationDecision(
        val outcome: HandshakeOutcome,
        val httpStatusCode: Int,
        val statusReason: String,
        val fallbackAuthProfile: String,
    )

    fun inspectRtspSetupRequest(method: String, path: String, payload: ByteArray): RtspNegotiationDecision {
        if (method.isBlank() || path.isBlank()) {
            return RtspNegotiationDecision(HandshakeOutcome.MALFORMED_RTSP_REQUEST, 400, "Bad Request", "NONE")
        }
        val hasFplyMagic = payload.size >= 4 &&
            payload[0] == 'F'.code.toByte() &&
            payload[1] == 'P'.code.toByte() &&
            payload[2] == 'L'.code.toByte() &&
            payload[3] == 'Y'.code.toByte()
        if (path.endsWith("/fp-setup", ignoreCase = true) || hasFplyMagic) {
            return RtspNegotiationDecision(
                outcome = HandshakeOutcome.REFUSED_PROPRIETARY_FAIRPLAY_DRM,
                httpStatusCode = 501,
                statusReason = "Not Implemented: Proprietary FairPlay DRM refused by clean-room policy",
                fallbackAuthProfile = "HAP-Curve25519-ChaCha20Poly1305-ALAC",
            )
        }
        return RtspNegotiationDecision(
            outcome = HandshakeOutcome.ACCEPTED_OPEN_PAIR_VERIFY,
            httpStatusCode = 200,
            statusReason = "OK",
            fallbackAuthProfile = "HAP-Curve25519-ChaCha20Poly1305-ALAC",
        )
    }
}
