package dev.fusion.calc

import java.math.BigDecimal

enum class Dimension { LENGTH, MASS, TEMPERATURE, DURATION, DATA, SPEED, AREA, VOLUME }
/** Base quantity = (value + offset) × numerator / denominator. Rational scale avoids early rounding. */
data class UnitDefinition(val id: String, val label: String, val dimension: Dimension,
                          val numerator: BigDecimal, val denominator: BigDecimal = BigDecimal.ONE,
                          val offset: BigDecimal = BigDecimal.ZERO)
object Units {
    private fun unit(id: String, label: String, dimension: Dimension, n: String, d: String="1", offset: String="0") =
        UnitDefinition(id,label,dimension,BigDecimal(n),BigDecimal(d),BigDecimal(offset))
    val definitions: List<UnitDefinition> = listOf(
        unit("m","metre",Dimension.LENGTH,"1"), unit("km","kilometre",Dimension.LENGTH,"1000"),
        unit("cm","centimetre",Dimension.LENGTH,"0.01"), unit("mm","millimetre",Dimension.LENGTH,"0.001"),
        unit("in","international inch",Dimension.LENGTH,"0.0254"), unit("ft","international foot",Dimension.LENGTH,"0.3048"),
        unit("yd","international yard",Dimension.LENGTH,"0.9144"), unit("mi","international mile",Dimension.LENGTH,"1609.344"),
        unit("kg","kilogram",Dimension.MASS,"1"), unit("g","gram",Dimension.MASS,"0.001"), unit("mg","milligram",Dimension.MASS,"0.000001"),
        unit("lb","avoirdupois pound",Dimension.MASS,"0.45359237"), unit("oz","avoirdupois ounce",Dimension.MASS,"0.45359237","16"),
        unit("K","kelvin",Dimension.TEMPERATURE,"1"), unit("C","degree Celsius",Dimension.TEMPERATURE,"1",offset="273.15"),
        unit("F","degree Fahrenheit",Dimension.TEMPERATURE,"5","9","459.67"),
        unit("s","second",Dimension.DURATION,"1"), unit("min","minute",Dimension.DURATION,"60"),
        unit("h","hour",Dimension.DURATION,"3600"), unit("day","24-hour duration (not calendar day)",Dimension.DURATION,"86400"),
        unit("B","byte",Dimension.DATA,"1"), unit("bit","bit",Dimension.DATA,"1","8"),
        unit("kB","kilobyte (1000 B)",Dimension.DATA,"1000"), unit("MB","megabyte (1000² B)",Dimension.DATA,"1000000"),
        unit("GB","gigabyte (1000³ B)",Dimension.DATA,"1000000000"), unit("KiB","kibibyte (1024 B)",Dimension.DATA,"1024"),
        unit("MiB","mebibyte (1024² B)",Dimension.DATA,"1048576"), unit("GiB","gibibyte (1024³ B)",Dimension.DATA,"1073741824"),
        unit("m/s","metres per second",Dimension.SPEED,"1"), unit("km/h","kilometres per hour",Dimension.SPEED,"5","18"),
        unit("mph","miles per hour",Dimension.SPEED,"1609.344","3600"),
        unit("m2","square metre",Dimension.AREA,"1"), unit("ft2","square international foot",Dimension.AREA,"0.09290304"),
        unit("ha","hectare",Dimension.AREA,"10000"),
        unit("m3","cubic metre",Dimension.VOLUME,"1"), unit("L","litre",Dimension.VOLUME,"0.001"), unit("mL","millilitre",Dimension.VOLUME,"0.000001")
    )
    fun definition(id: String): UnitDefinition {
        if(id.length !in 1..16) throw CalculationException("Invalid unit identifier length")
        return definitions.find { it.id==id } ?: throw CalculationException("Unknown unit: $id")
    }
    fun convert(value: BigDecimal, from: String, to: String): BigDecimal {
        if(value.precision()>34) throw CalculationException("Unit amounts support at most 34 significant digits")
        Calculator.bounded(value)
        val a=definition(from); val b=definition(to)
        if(a.dimension!=b.dimension) throw CalculationException("Cannot convert ${a.dimension.name.lowercase()} to ${b.dimension.name.lowercase()}.")
        val baseNumerator=value.add(a.offset).multiply(a.numerator)
        if(a.dimension==Dimension.TEMPERATURE && baseNumerator.signum()<0) throw CalculationException("Temperature is below absolute zero.")
        val numerator=baseNumerator.multiply(b.denominator)
        val denominator=a.denominator.multiply(b.numerator)
        // Combine the affine offset before the only division/rounding step. Dividing first
        // loses significant digits through cancellation (counterexample: 0 F -> C).
        val adjusted=numerator.subtract(b.offset.multiply(denominator))
        return Calculator.bounded(adjusted.divide(denominator,Calculator.context))
    }
}
