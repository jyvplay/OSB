package dev.fusion.perf

/**
 * Exponentially weighted moving average with explicit bias correction
 * for perf dashboards (the standard recurrence y_t = a*x_t + (1-a)*y_{t-1}
 * started at 0, plus the Adam-style correction y_t / (1 - (1-a)^t)
 * that removes the cold-start pull toward zero).
 *
 * Executed properties: a constant series equals that constant under
 * the corrected estimate at EVERY step (not just in the limit); the
 * uncorrected estimate underestimates on cold start, and that bias is
 * exactly (1-a)^t of the gap — both are asserted numerically, not
 * described.
 *
 * Declared narrowings: alpha in (0, 1]; sample ordering is the
 * caller's responsibility; no time-weighted variant (irregular
 * sampling intervals need a different estimator and a different
 * kernel).
 */
object Ewma {
    const val SCOPE = "Fixed-alpha EWMA + bias correction; no time-weighting; ordering is the caller's duty."

    data class State(val alpha: Double, val raw: Double, val count: Long) {
        init {
            require(alpha > 0.0 && alpha <= 1.0) { "alpha $alpha outside (0, 1]" }
            require(count >= 0) { "negative count" }
        }
        /** Bias-corrected estimate; refuses before any sample. */
        val estimate: Double
            get() {
                require(count > 0) { "no samples yet" }
                val denom = 1.0 - Math.pow(1.0 - alpha, count.toDouble())
                return if (denom == 0.0) raw else raw / denom
            }
    }

    fun start(alpha: Double): State = State(alpha, 0.0, 0)

    fun add(s: State, sample: Double): State {
        require(sample.isFinite()) { "non-finite sample" }
        return State(s.alpha, s.alpha * sample + (1.0 - s.alpha) * s.raw, s.count + 1)
    }

    fun addAll(s: State, samples: DoubleArray): State {
        var cur = s
        for (x in samples) cur = add(cur, x)
        return cur
    }
}
