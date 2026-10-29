// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import kotlin.math.abs

/**
 * Vector `#34` — IEEE 802.11bf / 802.11mc Wi-Fi Channel-State-Information (CSI) keystroke sensing mitigator.
 *
 * Fine-grained CSI (subcarrier amplitude + phase) and Fine Timing Measurement (`FTM`, 802.11mc/az) allow
 * an off-device receiver to infer finger motion above the screen. The defender cannot control the air, but
 * it can: detect high-rate `FTM`/NDP sounding bursts during sensitive focus, suppress unauthenticated FTM
 * responders, and randomise on-screen key feedback cadence so the physical keystroke timing is not
 * phase-locked to a predictable UI animation.
 */
object WifiCsiKeystrokeSensingMitigator {
    enum class Finding { NONE, FTM_BURST_DURING_SECRET, UNAUTHENTICATED_FTM_RESPONDER, CSI_SOUNDING_CADENCE_LOCK, KEY_CADENCE_LEAK }

    data class FtmExchange(val peerMac: String, val authenticated: Boolean, val burstFrames: Int, val startMs: Long, val endMs: Long)

    data class CsiSounding(val timestampMs: Long, val subcarriers: Int, val ndp: Boolean)

    data class Assessment(
        val findings: List<Finding>,
        val suppressedResponders: List<String>,
        val randomisedKeyDelayMs: Int,
        val allowFtmLinks: Boolean,
        val notes: List<String>,
    )

    fun assess(
        exchanges: List<FtmExchange>,
        soundings: List<CsiSounding>,
        secretFieldFocused: Boolean,
        uiCadenceMs: Int,
        jitterSeed: Long = java.lang.Long.parseUnsignedLong("9e3779b97f4a7c15", 16),
    ): Assessment {
        val findings = ArrayList<Finding>()
        val notes = ArrayList<String>()
        val suppressed = ArrayList<String>()

        for (e in exchanges) {
            if (e.burstFrames >= 8 && secretFieldFocused) findings.add(Finding.FTM_BURST_DURING_SECRET)
            if (!e.authenticated && e.burstFrames >= 4) {
                findings.add(Finding.UNAUTHENTICATED_FTM_RESPONDER)
                suppressed.add(e.peerMac)
            }
            if (e.endMs < e.startMs) notes.add("FTM exchange from ${e.peerMac} has an inverted time window")
        }

        // Cadence lock: >= 6 NDP soundings inside 500 ms with sub-2 ms spacing regularity.
        val ndp = soundings.filter { it.ndp }.sortedBy { it.timestampMs }
        var lockDetected = false
        if (ndp.size >= 6) {
            for (i in 0..ndp.size - 6) {
                val window = ndp.subList(i, i + 6)
                if (window.last().timestampMs - window.first().timestampMs <= 500) {
                    val gaps = window.zipWithNext { a, b -> (b.timestampMs - a.timestampMs).toDouble() }
                    val mean = gaps.average()
                    val spread = gaps.maxOf { abs(it - mean) }
                    if (mean < 2.0 && spread < 0.35) {
                        lockDetected = true
                        break
                    }
                }
            }
        }
        if (lockDetected) findings.add(Finding.CSI_SOUNDING_CADENCE_LOCK)

        // Deterministic xorshift jitter so key feedback is not phase-locked to a fixed cadence.
        var rng = jitterSeed
        rng = rng xor (rng shl 13); rng = rng xor (rng ushr 7); rng = rng xor (rng shl 17)
        val jitter = (abs(rng) % 31L).toInt()
        if (secretFieldFocused && (lockDetected || findings.contains(Finding.FTM_BURST_DURING_SECRET))) {
            findings.add(Finding.KEY_CADENCE_LEAK)
            notes.add("randomised key feedback by ${jitter}ms (base ${uiCadenceMs}ms)")
        }

        val allowFtm = !secretFieldFocused && suppressed.isEmpty()
        if (!allowFtm) notes.add("FTM links disabled while a secret field is focused or a rogue responder was seen")
        return Assessment(
            findings = findings.distinct(),
            suppressedResponders = suppressed.distinct().sorted(),
            randomisedKeyDelayMs = if (findings.contains(Finding.KEY_CADENCE_LEAK)) jitter else 0,
            allowFtmLinks = allowFtm,
            notes = notes,
        )
    }
}
