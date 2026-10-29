// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * Clean-room `_googlecast._tcp` DNS-SD TXT property & capability bitmask decoder plus
 * Trust-On-First-Use (TOFU) SPKI SHA-256 certificate pin continuity gate for `:cast:castv2`.
 *
 * Addresses the PKI gap documented in [CastV2Client]: Chromecast receivers present self-signed
 * device TLS certificates on `:8009`, so [verifyTofuPin] binds each receiver's advertised `id`
 * to its observed TLS SPKI SHA-256 digest on first pairing and refuses silent certificate
 * substitution on subsequent connections (`Outcome.PIN_MISMATCH_MITM_REFUSED`).
 */
object CastTxtProps {
    const val SCOPE =
        "_googlecast._tcp DNS-SD property/capability bitmask decoder and TOFU SPKI SHA-256 pin continuity verifier; mDNS multicast socket listener and Cast hardware ICA signature chain out of scope."

    const val CAP_VIDEO_OUT = 1 shl 0
    const val CAP_VIDEO_IN = 1 shl 1
    const val CAP_AUDIO_OUT = 1 shl 2
    const val CAP_AUDIO_IN = 1 shl 3
    const val CAP_DEV_MODE = 1 shl 4
    const val CAP_MULTIZONE_GROUP = 1 shl 5

    enum class Capability {
        VIDEO_OUT, VIDEO_IN, AUDIO_OUT, AUDIO_IN, DEV_MODE, MULTIZONE_GROUP
    }

    data class ReceiverProps(
        val deviceId: String,
        val friendlyName: String,
        val modelName: String,
        val capabilityMask: Int,
        val capabilities: Set<Capability>,
        val receiverState: Int,
        val protocolVersion: Int,
    )

    enum class TofuOutcome {
        FIRST_SEEN_PINNED,
        MATCHED_TRUSTED,
        PIN_MISMATCH_MITM_REFUSED,
    }

    data class TofuDecision(
        val outcome: TofuOutcome,
        val trusted: Boolean,
        val updatedPins: Map<String, String>,
    )

    fun parseProps(txt: Map<String, String>): ReceiverProps {
        val rawId = txt["id"]?.trim() ?: throw IllegalArgumentException("Cast TXT missing 'id'")
        val normId = rawId.replace("-", "").lowercase()
        require(normId.length == 32 && normId.all { it in '0'..'9' || it in 'a'..'f' }) {
            "invalid Cast receiver id '$rawId'"
        }
        val fn = txt["fn"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Cast TXT missing 'fn' friendly name")
        require(fn.length <= 128 && fn.none { it.code < 0x20 || it.code == 0x7F }) {
            "invalid Cast friendly name"
        }
        val md = txt["md"]?.trim()?.ifEmpty { "Unknown Cast" } ?: "Unknown Cast"
        require(md.length <= 128 && md.none { it.code < 0x20 || it.code == 0x7F }) {
            "invalid Cast model name"
        }
        val ca = (txt["ca"]?.trim() ?: "0").toIntOrNull()
            ?: throw IllegalArgumentException("invalid Cast 'ca' bitmask '${txt["ca"]}'")
        require(ca >= 0) { "negative Cast 'ca' bitmask $ca" }
        val st = (txt["st"]?.trim() ?: "0").toIntOrNull()
            ?: throw IllegalArgumentException("invalid Cast 'st' state '${txt["st"]}'")
        require(st in 0..2) { "Cast 'st' state $st outside 0..2" }
        val ve = (txt["ve"]?.trim() ?: "02").toIntOrNull()
            ?: throw IllegalArgumentException("invalid Cast 've' version '${txt["ve"]}'")
        require(ve in 1..99) { "Cast 've' version $ve outside 1..99" }

        return ReceiverProps(
            deviceId = normId,
            friendlyName = fn,
            modelName = md,
            capabilityMask = ca,
            capabilities = decodeCapabilities(ca),
            receiverState = st,
            protocolVersion = ve,
        )
    }

    fun decodeCapabilities(mask: Int): Set<Capability> {
        require(mask >= 0) { "negative capability mask $mask" }
        val out = LinkedHashSet<Capability>()
        if ((mask and CAP_VIDEO_OUT) != 0) out.add(Capability.VIDEO_OUT)
        if ((mask and CAP_VIDEO_IN) != 0) out.add(Capability.VIDEO_IN)
        if ((mask and CAP_AUDIO_OUT) != 0) out.add(Capability.AUDIO_OUT)
        if ((mask and CAP_AUDIO_IN) != 0) out.add(Capability.AUDIO_IN)
        if ((mask and CAP_DEV_MODE) != 0) out.add(Capability.DEV_MODE)
        if ((mask and CAP_MULTIZONE_GROUP) != 0) out.add(Capability.MULTIZONE_GROUP)
        return out
    }

    fun verifyTofuPin(
        knownPinsByDeviceId: Map<String, String>,
        deviceId: String,
        observedSpkiSha256Hex: String,
    ): TofuDecision {
        val normDev = deviceId.trim().replace("-", "").lowercase()
        require(normDev.length == 32 && normDev.all { it in '0'..'9' || it in 'a'..'f' }) {
            "invalid deviceId '$deviceId'"
        }
        val normPin = observedSpkiSha256Hex.trim().lowercase()
        require(normPin.length == 64 && normPin.all { it in '0'..'9' || it in 'a'..'f' }) {
            "expected 64-char SHA-256 hex SPKI pin, got '$observedSpkiSha256Hex'"
        }

        val existing = knownPinsByDeviceId[normDev]?.lowercase()
        return when {
            existing == null -> {
                val next = LinkedHashMap(knownPinsByDeviceId)
                next[normDev] = normPin
                TofuDecision(TofuOutcome.FIRST_SEEN_PINNED, trusted = true, updatedPins = next)
            }
            existing == normPin -> TofuDecision(
                TofuOutcome.MATCHED_TRUSTED,
                trusted = true,
                updatedPins = knownPinsByDeviceId,
            )
            else -> TofuDecision(
                TofuOutcome.PIN_MISMATCH_MITM_REFUSED,
                trusted = false,
                updatedPins = knownPinsByDeviceId,
            )
        }
    }
}
