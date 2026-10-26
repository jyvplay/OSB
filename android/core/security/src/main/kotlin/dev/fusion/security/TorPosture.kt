// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Honest Tor/Orbot readiness observations. This kernel NEVER claims traffic is anonymized:
 * it records tri-state observations (observed / absent / unknown) and builds disclosures
 * that name exactly what was not proven. Routing traffic through Tor requires the separate
 * Orbot app plus explicit user VPN consent; an SDK check or a proxy port is not a circuit.
 *
 * Unknown must propagate: any UNKNOWN input keeps the verdict UNKNOWN, never "safe".
 */
object TorPosture {
    enum class Observation { OBSERVED, ABSENT, UNKNOWN }

    data class Posture(
        val orbotInstalled: Observation = Observation.UNKNOWN,
        val vpnConsentGranted: Observation = Observation.UNKNOWN,
        val torBootstrapObserved: Observation = Observation.UNKNOWN,
    ) {
        /** Only an observed bootstrap through a consented route counts as tunneled. Nothing else upgrades. */
        fun tunneled(): Observation = when {
            torBootstrapObserved == Observation.OBSERVED && vpnConsentGranted == Observation.OBSERVED -> Observation.OBSERVED
            torBootstrapObserved == Observation.ABSENT || vpnConsentGranted == Observation.ABSENT || orbotInstalled == Observation.ABSENT -> Observation.ABSENT
            else -> Observation.UNKNOWN
        }

        fun disclosure(): String = buildString {
            append("Tor posture: Orbot installed=${orbotInstalled.name.lowercase()}, ")
            append("VPN consent=${vpnConsentGranted.name.lowercase()}, ")
            append("bootstrap observed=${torBootstrapObserved.name.lowercase()}. ")
            when (tunneled()) {
                Observation.OBSERVED -> append("A Tor bootstrap was observed through the consented route at check time. " +
                    "This does not prove every app packet used Tor, nor safety against endpoint, browser-fingerprint, " +
                    "timing-correlation or malicious-exit observation.")
                Observation.ABSENT -> append("Traffic is NOT routed through Tor. Do not treat this session as anonymous.")
                Observation.UNKNOWN -> append("Routing is UNKNOWN: absence of evidence is not evidence of anonymity. " +
                    "Treat this session as non-anonymous until a bootstrap is actually observed.")
            }
        }
    }

    /** Merges two independent posture readings. Disagreement or any unknown stays non-observed. */
    fun merge(a: Posture, b: Posture): Posture {
        fun join(x: Observation, y: Observation) = if (x == y) x else Observation.UNKNOWN
        return Posture(join(a.orbotInstalled, b.orbotInstalled), join(a.vpnConsentGranted, b.vpnConsentGranted),
            join(a.torBootstrapObserved, b.torBootstrapObserved))
    }
}
