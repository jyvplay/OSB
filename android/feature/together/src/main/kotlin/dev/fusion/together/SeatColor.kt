package dev.fusion.together

/**
 * Deterministic participant hue assignment via golden-angle stepping
 * (hue_i = i * 137.50776405003785 mod 360 — the irrational rotation
 * that keeps ANY prefix of seats maximally spread; the same phi
 * family the design system's beauty gate uses).
 *
 * Returns HUES ONLY — saturation/lightness and RGB conversion belong
 * to the a11y HslColor kernel; sessions seed by join index so every
 * peer computes identical colors with no negotiation.
 *
 * Declared narrowings: hue geometry only; color-vision-deficiency
 * palettes are a curated-data concern, named not solved.
 */
object SeatColor {
    const val SCOPE = "Golden-angle hue sequence only; S/L and RGB live in HslColor; CVD palettes out of scope."
    const val GOLDEN_ANGLE = 137.50776405003785

    fun hue(index: Int): Double {
        require(index >= 0) { "negative seat index" }
        return (index * GOLDEN_ANGLE) % 360.0
    }

    fun hues(count: Int): List<Double> {
        require(count in 1..1000) { "count $count outside 1..1000" }
        return (0 until count).map { hue(it) }
    }

    /** Smallest circular distance between any two of the first [count] hues. */
    fun minSeparation(count: Int): Double {
        val h = hues(count).sorted()
        var best = 360.0 - h.last() + h.first()
        for (i in 1 until h.size) best = minOf(best, h[i] - h[i - 1])
        return best
    }
}
