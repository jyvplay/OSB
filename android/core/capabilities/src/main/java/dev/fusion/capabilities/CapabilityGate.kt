// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Caller-supplied device facts. Null means unmeasured — never a fabricated
 * zero — and any requirement touching an unknown fact is denied with an
 * "unknown" reason. Mapping from [CapabilityReport] is caller duty.
 */
data class DeviceCaps(
    val sdk: Int,
    val ramGb: Double?,
    val lowRam: Boolean?,
    val encoders: List<String>?,
    val freeform: Boolean?,
)

/** Enablement verdict with a human-readable reason. Reasons are stable text. */
data class GateDecision(val enabled: Boolean, val reason: String)

/**
 * Pure feature-enablement decisions over explicit facts. Deny-by-default:
 * every unsatisfied or unknown requirement denies. No probing here — the
 * Android probe owns measurement, this owns the decision table.
 */
object CapabilityGate {
    fun decide(
        caps: DeviceCaps,
        minSdk: Int,
        needsEncoder: String? = null,
        needsRamGb: Double? = null,
        needsFreeform: Boolean = false,
    ): GateDecision {
        require(minSdk in 21..40) { "bad minSdk" }
        if (caps.sdk < minSdk) return GateDecision(false, "sdk ${caps.sdk} < $minSdk")
        if (needsEncoder != null) {
            val enc = caps.encoders
                ?: return GateDecision(false, "encoder list unknown")
            if (enc.none { it.equals(needsEncoder, ignoreCase = true) }) {
                return GateDecision(false, "no $needsEncoder encoder")
            }
        }
        if (needsRamGb != null) {
            require(needsRamGb > 0) { "bad needsRamGb" }
            val ram = caps.ramGb
                ?: return GateDecision(false, "ram unknown")
            if (ram < needsRamGb) return GateDecision(false, "ram $ram < $needsRamGb")
        }
        if (needsFreeform && caps.freeform != true) {
            return GateDecision(caps.freeform == true, if (caps.freeform == null) "freeform unknown" else "freeform absent")
        }
        return GateDecision(true, "all requirements met")
    }
}
