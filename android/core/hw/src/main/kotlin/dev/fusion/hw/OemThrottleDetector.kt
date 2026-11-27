// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * R146 — OEM sustained-throttle detector and guidance classifier
 * (owner:v2.0 "OEM throttle detector and guidance"; mechanism-distinct from
 * [BatteryThermalThrottleLevel], which classifies a single temperature
 * reading and never looks at clocks or time).
 *
 * Input is a bounded series of samples
 * `epochMs:currentKHz:maxKHz:tempTenthsC`. A sample is *capped* when the
 * running frequency is below 70% of the advertised maximum. Consecutive
 * capped samples whose timestamps are no more than [MAX_GAP_MS] apart form
 * an episode; an episode only counts once it spans at least
 * [MIN_EPISODE_MS]. Guidance escalates with duration and temperature:
 *  - `LIGHT`   : episode >= 30 s, no sample >= 400 tenths C during it;
 *  - `SEVERE`  : episode >= 300 s or any sample >= 450 tenths C;
 *  - `CRITICAL`: any sample >= 480 tenths C during the episode.
 * Episodes are reported `startMs:endMs:level` in input order; overlapping or
 * unsorted input refuses instead of being silently repaired.
 *
 * Declared narrowings: 1..[MAX_SAMPLES] samples; timestamps strictly
 * increasing with gaps <= 30 minutes (a larger gap breaks the contract —
 * the caller must start a new series); frequency 1..[MAX_FREQ_KHZ];
 * temperature -400..1200 tenths C; ratio uses integer math
 * (`current * 10 < max * 7`).
 */
object OemThrottleDetector {
    const val SCOPE =
        "Sustained sub-70%-clock episode detection with thermal escalation guidance; reads samples it is given, never probes hardware."

    const val MAX_SAMPLES = 4096
    const val MAX_GAP_MS = 30L * 60_000L
    const val MIN_EPISODE_MS = 30_000L
    const val SEVERE_MS = 300_000L
    const val MAX_FREQ_KHZ = 10_000_000

    data class Sample(val epochMs: Long, val currentKHz: Long, val maxKHz: Long, val tempTenthsC: Int)

    fun parse(series: String): List<Sample> {
        require(series.isNotEmpty()) { "empty sample series" }
        val tokens = series.split(',')
        require(tokens.size in 1..MAX_SAMPLES) { "sample count out of 1..$MAX_SAMPLES" }
        val samples = tokens.map { token ->
            val p = token.split(':')
            require(p.size == 4) { "sample must be epochMs:current:max:temp" }
            val t = p[0].toLongOrNull() ?: throw IllegalArgumentException("epoch not an int")
            val cur = p[1].toLongOrNull() ?: throw IllegalArgumentException("current freq not an int")
            val max = p[2].toLongOrNull() ?: throw IllegalArgumentException("max freq not an int")
            val temp = p[3].toIntOrNull() ?: throw IllegalArgumentException("temp not an int")
            require(t >= 0) { "epoch must be >= 0" }
            require(cur in 1..MAX_FREQ_KHZ && max in 1..MAX_FREQ_KHZ) { "frequency out of range" }
            require(temp in -400..1200) { "temperature out of physical bounds" }
            Sample(t, cur, max, temp)
        }
        for (i in 1 until samples.size) {
            require(samples[i].epochMs > samples[i - 1].epochMs) { "timestamps must be strictly increasing" }
            require(samples[i].epochMs - samples[i - 1].epochMs <= MAX_GAP_MS) { "sample gap over 30 minutes" }
        }
        return samples
    }

    fun detect(series: String): String {
        val samples = parse(series)
        val capped = samples.map { it.currentKHz * 10 < it.maxKHz * 7L }
        val out = ArrayList<String>()
        var i = 0
        while (i < samples.size) {
            if (!capped[i]) { i++; continue }
            val start = i
            var end = i
            while (end + 1 < samples.size &&
                capped[end + 1] &&
                samples[end + 1].epochMs - samples[end].epochMs <= MAX_GAP_MS
            ) {
                end++
            }
            val startMs = samples[start].epochMs
            val endMs = samples[end].epochMs
            val span = endMs - startMs
            if (span >= MIN_EPISODE_MS) {
                val maxTemp = (start..end).maxOf { samples[it].tempTenthsC }
                val level = when {
                    maxTemp >= 480 -> "CRITICAL"
                    maxTemp >= 450 -> "SEVERE"
                    span >= SEVERE_MS -> "SEVERE"
                    else -> "LIGHT"
                }
                out.add("$startMs:$endMs:$level")
            }
            i = end + 1
        }
        return if (out.isEmpty()) "none" else out.joinToString(",")
    }
}
