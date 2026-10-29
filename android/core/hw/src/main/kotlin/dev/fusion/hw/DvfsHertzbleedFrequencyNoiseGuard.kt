// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Vector `#32` — `Hertzbleed`-class DVFS frequency-throttling side-channel guard.
 *
 * `Hertzbleed` (2022) and follow-ups observe that dynamic voltage/frequency scaling makes execution time
 * a function of the *data* being processed, turning `cpufreq` telemetry (or timing alone) into a secret
 * oracle. A defender cannot remove DVFS, so the guard instead:
 * 1. detects statistically improbable frequency dips that correlate with cryptographic work windows
 *    (Pearson `r` over the per-window mean frequency, plus a step-transition entropy check);
 * 2. enforces **constant-time jitter windows**: every crypto batch is padded to a fixed quantum so a
 *    single fast/slow batch is not observable;
 * 3. redacts raw `cpufreq`/`time_in_state` telemetry from untrusted callers while a secret is in scope.
 */
object DvfsHertzbleedFrequencyNoiseGuard {
    enum class Alert { NONE, FREQUENCY_DIP_CORRELATION, LOW_TRANSITION_ENTROPY, JITTER_INSUFFICIENT, TELEMETRY_REDACTED }

    data class Sample(val windowMs: Long, val khz: Int, val cryptoWorkUnits: Int)

    data class Report(
        val alert: Alert,
        val pearsonR: Double,
        val transitionEntropyBits: Double,
        val dipCount: Int,
        val meanKhz: Double,
        val recommendation: String,
    )

    fun pearson(xs: DoubleArray, ys: DoubleArray): Double {
        require(xs.size == ys.size && xs.size >= 3) { "Pearson needs >= 3 paired samples" }
        val mx = xs.average(); val my = ys.average()
        var num = 0.0; var dx = 0.0; var dy = 0.0
        for (i in xs.indices) {
            val a = xs[i] - mx
            val b = ys[i] - my
            num += a * b; dx += a * a; dy += b * b
        }
        if (dx <= 1e-18 || dy <= 1e-18) return 0.0
        return num / sqrt(dx * dy)
    }

    /** Shannon entropy (bits) of the coarse step-direction distribution; near `0` means a data-dependent cliff. */
    fun transitionEntropyBits(samples: List<Sample>): Double {
        if (samples.size < 3) return Double.NaN
        val counts = IntArray(3) // 0: down, 1: flat, 2: up
        for (i in 1 until samples.size) {
            val delta = samples[i].khz - samples[i - 1].khz
            when {
                delta < -1_000 -> counts[0]++
                delta > 1_000 -> counts[2]++
                else -> counts[1]++
            }
        }
        val total = counts.sum().toDouble()
        var h = 0.0
        for (c in counts) {
            if (c == 0) continue
            val p = c / total
            h -= p * (kotlin.math.ln(p) / kotlin.math.ln(2.0))
        }
        return h
    }

    fun analyse(samples: List<Sample>, dipThresholdKhz: Int = 200_000): Report {
        if (samples.size < 3) {
            return Report(Alert.NONE, 0.0, Double.NaN, 0, samples.map { it.khz.toDouble() }.average(), "insufficient telemetry")
        }
        val freq = DoubleArray(samples.size) { samples[it].khz.toDouble() }
        val work = DoubleArray(samples.size) { samples[it].cryptoWorkUnits.toDouble() }
        val r = pearson(freq, work)
        val entropy = transitionEntropyBits(samples)
        val dips = samples.count { it.khz < dipThresholdKhz }
        return when {
            r < -0.6 -> Report(
                Alert.FREQUENCY_DIP_CORRELATION, r, entropy, dips, freq.average(),
                "frequency dips correlate with crypto work (r=$r): enable fixed-quantum jitter windows",
            )
            entropy < 0.35 -> Report(
                Alert.LOW_TRANSITION_ENTROPY, r, entropy, dips, freq.average(),
                "step-transition entropy ${"%.3f".format(entropy)} bits is a data-dependent cliff",
            )
            else -> Report(Alert.NONE, r, entropy, dips, freq.average(), "DVFS behaviour statistically unremarkable")
        }
    }

    /** Pads a crypto batch to a fixed quantum so per-batch duration is not observable. */
    fun constantTimeBatch(
        workUnitsPerItem: IntArray,
        quantumUnits: Int = 4_096,
        rngSeed: Long = 0x5DEECE66DL,
    ): Pair<Int, Long> {
        require(workUnitsPerItem.isNotEmpty() && quantumUnits > 0) { "invalid batch" }
        var used = 0
        var rng = rngSeed
        for (w in workUnitsPerItem) {
            require(w in 0..quantumUnits) { "work unit $w exceeds the quantum" }
            used += w
            // Deterministic jitter (xorshift) keeps total work a multiple of the quantum without leaking w.
            rng = rng xor (rng shl 13)
            rng = rng xor (rng ushr 7)
            rng = rng xor (rng shl 17)
        }
        val target = ((used + quantumUnits - 1) / quantumUnits) * quantumUnits
        val padding = target - used
        return padding to (abs(rng) % maxOf(1, quantumUnits).toLong())
    }

    /** Redacts raw frequency counters from logs unless the caller holds the diagnostics capability. */
    fun redactTelemetry(lines: List<String>, diagnosticsGranted: Boolean): List<String> {
        if (diagnosticsGranted) return lines
        val pattern = Regex("(cpu\\d+\\s+(scaling_cur_freq|time_in_state|cpuinfo_cur_freq)\\s*:?\\s*)\\d+")
        return lines.map { line ->
            if (pattern.containsMatchIn(line)) {
                pattern.replace(line) { m -> m.groupValues[1] + "[redacted]" }
            } else line
        }
    }
}
