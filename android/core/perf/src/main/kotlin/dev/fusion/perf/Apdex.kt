package dev.fusion.perf

/**
 * Apdex scoring (the published Apdex formula: score = (satisfied +
 * tolerating/2) / total, with satisfied <= T and tolerating <= 4T)
 * and the standard rating bands (>=0.94 excellent, >=0.85 good,
 * >=0.70 fair, >=0.50 poor, else unacceptable).
 *
 * Declared narrowings: the caller supplies T (choosing T is product
 * judgment, not math); empty sample sets refuse; the 4T multiplier is
 * the spec's, not configurable (a configurable multiplier is a
 * different metric wearing Apdex's name).
 */
object Apdex {
    const val SCOPE = "Published formula + bands; T is caller judgment; the 4T line is fixed by the spec."

    enum class Rating { EXCELLENT, GOOD, FAIR, POOR, UNACCEPTABLE }

    fun score(samplesMs: LongArray, thresholdMs: Long): Double {
        require(samplesMs.isNotEmpty()) { "no samples" }
        require(thresholdMs > 0) { "threshold $thresholdMs" }
        var sat = 0; var tol = 0
        for (s in samplesMs) {
            require(s >= 0) { "negative latency $s" }
            if (s <= thresholdMs) sat++ else if (s <= 4 * thresholdMs) tol++
        }
        return (sat + tol / 2.0) / samplesMs.size
    }

    fun rating(score: Double): Rating {
        require(score in 0.0..1.0) { "score $score outside 0..1" }
        return when {
            score >= 0.94 -> Rating.EXCELLENT
            score >= 0.85 -> Rating.GOOD
            score >= 0.70 -> Rating.FAIR
            score >= 0.50 -> Rating.POOR
            else -> Rating.UNACCEPTABLE
        }
    }
}
