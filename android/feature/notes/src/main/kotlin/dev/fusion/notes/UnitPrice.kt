package dev.fusion.notes

/**
 * Unit-price normalization for shopping notes (stated contract): a
 * price for a quantity in one of a fixed unit table normalizes to
 * price per BASE unit (g for mass, ml for volume, piece for count),
 * so two offers compare only within the same dimension — comparing
 * grams against milliliters REFUSES instead of pretending density.
 *
 * Money is integer MINOR units (cents) in; per-base prices are
 * Double only at the comparison layer, never stored.
 *
 * Declared narrowings: the seven-unit table below (declared data);
 * no currencies (amounts are dimensionless minor units — mixing
 * currencies is the caller's refusal to make).
 */
object UnitPrice {
    const val SCOPE = "Fixed unit table to base g/ml/piece; cross-dimension comparison refuses; no currency handling."

    enum class Dim { MASS, VOLUME, COUNT }

    data class Unit(val name: String, val dim: Dim, val toBase: Double)

    val UNITS = listOf(
        Unit("g", Dim.MASS, 1.0), Unit("kg", Dim.MASS, 1000.0),
        Unit("ml", Dim.VOLUME, 1.0), Unit("l", Dim.VOLUME, 1000.0), Unit("cl", Dim.VOLUME, 10.0),
        Unit("pc", Dim.COUNT, 1.0), Unit("dozen", Dim.COUNT, 12.0)
    ).associateBy { it.name }

    data class Offer(val minorUnits: Long, val quantity: Double, val unit: Unit) {
        init {
            require(minorUnits >= 0) { "negative price" }
            require(quantity > 0 && quantity.isFinite()) { "quantity $quantity" }
        }
        val perBase: Double get() = minorUnits / (quantity * unit.toBase)
    }

    fun offer(minorUnits: Long, quantity: Double, unitName: String): Offer {
        val u = UNITS[unitName] ?: throw IllegalArgumentException(
            "unit '$unitName' outside the declared table (${UNITS.keys.sorted()})"
        )
        return Offer(minorUnits, quantity, u)
    }

    /** Negative when a is cheaper per base unit; refuses across dimensions. */
    fun compare(a: Offer, b: Offer): Int {
        require(a.unit.dim == b.unit.dim) {
            "comparing ${a.unit.dim} against ${b.unit.dim} needs a density the note does not have — refused"
        }
        return a.perBase.compareTo(b.perBase)
    }

    fun cheapest(offers: List<Offer>): Offer {
        require(offers.isNotEmpty()) { "no offers" }
        offers.drop(1).forEach { require(it.unit.dim == offers[0].unit.dim) { "mixed dimensions in list" } }
        return offers.minByOrNull { it.perBase }!!
    }
}
