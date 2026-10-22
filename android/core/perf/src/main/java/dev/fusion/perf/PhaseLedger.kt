// SPDX-License-Identifier: Apache-2.0
package dev.fusion.perf

/**
 * Named-phase duration ledger for startup/flow measurement. Durations are
 * caller-supplied milliseconds — no clock is read here, so results are
 * deterministic and host-testable. Duplicate phase names are refused
 * (measure once, record once). Caps: 64 phases, 1h per phase.
 */
object PhaseLedger {
    const val MAX_PHASES = 64
    const val MAX_MS = 3_600_000L
    private val NAME = Regex("[a-z0-9_]{1,32}")

    fun record(phases: Map<String, Long>, name: String, ms: Long): Map<String, Long> {
        require(NAME.matches(name)) { "bad phase name $name" }
        require(ms in 0..MAX_MS) { "ms $ms outside 0..$MAX_MS" }
        require(name !in phases) { "duplicate phase $name" }
        require(phases.size < MAX_PHASES) { "over $MAX_PHASES phases" }
        return phases + (name to ms)
    }

    fun total(phases: Map<String, Long>): Long = phases.values.sum()

    /** Share of [name] in 0.0..1.0; empty ledger yields 0.0 for any name. */
    fun percent(phases: Map<String, Long>, name: String): Double {
        val total = total(phases)
        if (total == 0L) return 0.0
        return (phases[name] ?: 0L).toDouble() / total
    }

    /** Slowest phase name, ties broken alphabetically; null when empty. */
    fun slowest(phases: Map<String, Long>): String? =
        phases.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key
}
