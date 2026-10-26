package dev.fusion.media

/**
 * Tempo arithmetic for the media surface: BPM ↔ milliseconds-per-beat
 * (60000/bpm), and tap-tempo estimation from inter-tap intervals via
 * the MEDIAN (robust to one mis-tap, unlike the mean — that choice is
 * the point and is tested).
 *
 * Declared narrowings: declared musical range 20..400 BPM (outside is
 * sensor noise, refused); no audio analysis — taps are caller input.
 */
object Bpm {
    const val SCOPE = "BPM<->ms + median tap estimation in 20..400 only; no audio analysis."

    fun msPerBeat(bpm: Double): Double {
        require(bpm in 20.0..400.0) { "bpm $bpm outside 20..400" }
        return 60000.0 / bpm
    }

    fun fromMs(ms: Double): Double {
        require(ms > 0 && ms.isFinite()) { "interval $ms" }
        val bpm = 60000.0 / ms
        require(bpm in 20.0..400.0) { "interval $ms maps to $bpm bpm, outside 20..400" }
        return bpm
    }

    fun median(values: DoubleArray): Double {
        require(values.isNotEmpty()) { "no samples" }
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /** Taps are timestamps ms; needs >= 3 taps (>= 2 intervals). */
    fun fromTaps(tapTimesMs: LongArray): Double {
        require(tapTimesMs.size >= 3) { "need at least three taps" }
        val intervals = DoubleArray(tapTimesMs.size - 1)
        for (i in 1 until tapTimesMs.size) {
            val d = tapTimesMs[i] - tapTimesMs[i - 1]
            require(d > 0) { "taps must strictly increase" }
            intervals[i - 1] = d.toDouble()
        }
        return fromMs(median(intervals))
    }
}
