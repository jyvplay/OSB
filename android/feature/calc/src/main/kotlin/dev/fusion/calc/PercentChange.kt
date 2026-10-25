package dev.fusion.calc

/**
 * Percent-change arithmetic with the zero-base refusal the Calculator
 * surface deliberately excludes from its expression engine ("no
 * implied percentages" is Calculator's contract; THIS kernel is the
 * explicit, separate operation).
 *
 * change(a,b) = (b-a)/a * 100 over POSITIVE bases only (signed-base
 * conventions contradict each other AND break the apply() inverse —
 * the restriction is declared, not hidden); basis points are exactly
 * 100x the percent figure; zero/negative bases REFUSE.
 */
object PercentChange {
    const val SCOPE = "Explicit percent/bp change with zero-base refusal; Calculator's engine stays percent-free."

    fun percent(from: Double, to: Double): Double {
        require(from > 0.0) { "positive bases only (zero undefined; negative-base conventions conflict) — refused" }
        require(from.isFinite() && to.isFinite()) { "non-finite input" }
        return (to - from) / from * 100.0
    }

    fun basisPoints(from: Double, to: Double): Double = percent(from, to) * 100.0

    fun apply(base: Double, percent: Double): Double {
        require(base > 0.0 && base.isFinite() && percent.isFinite()) { "positive finite base required" }
        return base * (1.0 + percent / 100.0)
    }

    /** percent() and apply() are inverse within tolerance; executed in tests, stated here. */
    fun roundTripError(from: Double, to: Double): Double {
        val p = percent(from, to)
        return kotlin.math.abs(apply(from, p) - to)
    }
}
