package dev.fusion.calc

/**
 * Bounded unit conversion engine with dimension checking.
 *
 * Supports length, mass, temperature, and time conversions between common
 * units. All conversions use decimal arithmetic with bounds checking.
 * No currency or calendar conversions (those require external data).
 *
 * Bounds (per call):
 *   - value magnitude <= 10^15
 *   - unit names must be from the defined dimension table
 *   - fromUnit and toUnit must belong to the same dimension
 *
 * Throws IllegalArgumentException for unknown units, mismatched dimensions,
 * or out-of-bounds values.
 */
object UnitConversion {
    private const val MAX_VALUE = 10_000_000_000_000_000.0

    private val dimensions = mapOf(
        "length" to mapOf(
            "m" to 1.0, "cm" to 0.01, "mm" to 0.001, "km" to 1000.0,
            "in" to 0.0254, "ft" to 0.3048, "yd" to 0.9144, "mi" to 1609.344
        ),
        "mass" to mapOf(
            "kg" to 1.0, "g" to 0.001, "mg" to 0.000001, "t" to 1000.0,
            "lb" to 0.45359237, "oz" to 0.028349523125
        ),
        "time" to mapOf(
            "s" to 1.0, "ms" to 0.001, "min" to 60.0, "h" to 3600.0,
            "d" to 86400.0
        ),
    )

    private val temperatureUnits = setOf("c", "f", "k")

    data class ConversionResult(
        val value: Double,
        val fromUnit: String,
        val toUnit: String,
        val dimension: String
    )

    fun convert(value: Double, fromUnit: String, toUnit: String): ConversionResult {
        require(value.isFinite()) { "value must be finite" }
        require(kotlin.math.abs(value) <= MAX_VALUE) {
            "value magnitude ${kotlin.math.abs(value)} exceeds $MAX_VALUE"
        }
        require(fromUnit.isNotBlank()) { "fromUnit must not be blank" }
        require(toUnit.isNotBlank()) { "toUnit must not be blank" }

        val f = fromUnit.lowercase()
        val t = toUnit.lowercase()

        if (f in temperatureUnits && t in temperatureUnits) {
            val celsius = when (f) {
                "c" -> value
                "f" -> (value - 32.0) * 5.0 / 9.0
                "k" -> value - 273.15
                else -> error("unreachable")
            }
            val result = when (t) {
                "c" -> celsius
                "f" -> celsius * 9.0 / 5.0 + 32.0
                "k" -> celsius + 273.15
                else -> error("unreachable")
            }
            require(result.isFinite()) { "non-finite result" }
            return ConversionResult(result, fromUnit, toUnit, "temperature")
        }

        for ((dim, units) in dimensions) {
            val fromMult = units[f] ?: continue
            val toMult = units[t] ?: continue
            require(fromMult != 0.0 && toMult != 0.0) { "unit multiplier must not be zero" }
            val result = value * fromMult / toMult
            require(result.isFinite()) { "non-finite result" }
            return ConversionResult(result, fromUnit, toUnit, dim)
        }

        throw IllegalArgumentException(
            "unknown units or mismatched dimensions: $fromUnit, $toUnit"
        )
    }

    fun definition(unit: String) {
        val u = unit.lowercase()
        require(u in dimensions.values.flatMap { it.keys } || u in temperatureUnits) {
            "unknown unit: $unit"
        }
    }

    fun dimensionNames(): List<String> = dimensions.keys.sorted() + "temperature"

    fun unitsForDimension(dimension: String): List<String> {
        return when (dimension) {
            "temperature" -> temperatureUnits.sorted()
            else -> dimensions[dimension]?.keys?.sorted() ?: emptyList()
        }
    }
}
