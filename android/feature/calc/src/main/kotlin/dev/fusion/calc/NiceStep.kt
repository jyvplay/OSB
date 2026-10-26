package dev.fusion.calc

/**
 * 1-2-5 axis-step selection for the calc/graphing surface (the
 * classic "nice numbers" algorithm family: pick the smallest step of
 * the form {1,2,5}×10^k producing AT MOST [maxTicks] intervals over
 * the range).
 *
 * Executed properties: tick count never exceeds maxTicks; the step
 * divides evenly into decimal grid lines; the chosen step is minimal
 * among the 1-2-5 family satisfying the bound.
 *
 * Declared narrowings: decimal 1-2-5 family only (time axes use
 * 15/30/60 ladders — a different kernel); degenerate ranges refuse;
 * calc's expression engine is untouched (Calculator owns evaluation).
 */
object NiceStep {
    const val SCOPE = "Decimal 1-2-5 step selection only; time ladders and expression evaluation live elsewhere."

    fun step(min: Double, max: Double, maxTicks: Int): Double {
        require(min.isFinite() && max.isFinite() && max > min) { "degenerate range [$min,$max]" }
        require(maxTicks >= 2) { "need at least two ticks" }
        val span = max - min
        val rough = span / maxTicks
        var mag = Math.pow(10.0, Math.floor(Math.log10(rough)))
        while (true) {
            for (m in doubleArrayOf(1.0, 2.0, 5.0)) {
                val s = m * mag
                if (Math.ceil(max / s) - Math.floor(min / s) <= maxTicks) return s
            }
            mag *= 10.0
            require(mag.isFinite()) { "unreachable: step search diverged" }
        }
    }

    /** Grid line positions: multiples of the chosen step covering the range. */
    fun ticks(min: Double, max: Double, maxTicks: Int): List<Double> {
        val s = step(min, max, maxTicks)
        val first = Math.floor(min / s)
        val last = Math.ceil(max / s)
        return (first.toLong()..last.toLong()).map { it * s }
    }
}
