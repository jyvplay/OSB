package dev.fusion.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Bounded decimal exact arithmetic engine.
 *
 * Provides add, subtract, multiply, divide, and power operations on
 * decimal strings using BigDecimal with 34 significant digits and
 * half-even rounding. All operations validate inputs and bounds.
 *
 * Bounds (per call):
 *   - operand strings must be valid decimal literals
 *   - result precision bounded to 34 significant digits
 *   - result adjusted exponent bounded to [-1000, 1000]
 *   - power exponent bounded to [0, 64]
 *   - divide by zero refused
 *
 * Throws IllegalArgumentException for invalid inputs or bound violations.
 */
object DecimalMath {
    private const val PRECISION = 34
    private const val MIN_EXPONENT = -1000
    private const val MAX_EXPONENT = 1000
    private const val MAX_POWER = 64
    private val CONTEXT = MathContext(PRECISION, RoundingMode.HALF_EVEN)

    data class DecimalResult(
        val value: String,
        val scale: Int,
        val precision: Int
    )

    private fun parseDecimal(text: String): BigDecimal {
        require(text.length <= 512) { "decimal literal too long" }
        require(text.isNotBlank()) { "empty decimal literal" }
        require(text.matches(Regex("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?"))) {
            "invalid decimal literal: $text"
        }
        return BigDecimal(text).also {
            require(it.scale() in -2048..2048) { "decimal scale out of bounds" }
            checkBounds(it)
        }
    }

    private fun checkBounds(value: BigDecimal) {
        // BigDecimal represents finite numbers only; NaN/Infinity literals refuse in parsing.
        val adjustedExponent = value.precision().toLong() - value.scale() - 1
        if (value.signum() != 0) {
            require(adjustedExponent in MIN_EXPONENT..MAX_EXPONENT) {
                "adjusted exponent $adjustedExponent outside [$MIN_EXPONENT, $MAX_EXPONENT]"
            }
        }
    }

    private fun result(value: BigDecimal): DecimalResult {
        checkBounds(value)
        val normalized = value.stripTrailingZeros()
        return DecimalResult(
            value = if (normalized.signum() == 0) "0" else normalized.toPlainString(),
            scale = normalized.scale(),
            precision = normalized.precision()
        )
    }

    fun add(a: String, b: String): DecimalResult {
        val x = parseDecimal(a)
        val y = parseDecimal(b)
        return result(x.add(y, CONTEXT))
    }

    fun subtract(a: String, b: String): DecimalResult {
        val x = parseDecimal(a)
        val y = parseDecimal(b)
        return result(x.subtract(y, CONTEXT))
    }

    fun multiply(a: String, b: String): DecimalResult {
        val x = parseDecimal(a)
        val y = parseDecimal(b)
        return result(x.multiply(y, CONTEXT))
    }

    fun divide(a: String, b: String): DecimalResult {
        val x = parseDecimal(a)
        val y = parseDecimal(b)
        require(y.signum() != 0) { "division by zero" }
        return result(x.divide(y, CONTEXT))
    }

    fun power(base: String, exponent: Int): DecimalResult {
        require(exponent in 0..MAX_POWER) { "power exponent $exponent outside [0, $MAX_POWER]" }
        val x = parseDecimal(base)
        if (exponent == 0) return result(BigDecimal.ONE)
        var acc = x
        var e = exponent
        while (e > 1) {
            acc = acc.multiply(x, CONTEXT)
            checkBounds(acc)
            e--
        }
        return result(acc)
    }

    fun negate(a: String): DecimalResult {
        val x = parseDecimal(a)
        return result(x.negate(CONTEXT))
    }

    fun abs(a: String): DecimalResult {
        val x = parseDecimal(a)
        return result(x.abs(CONTEXT))
    }

    fun isValidDecimal(text: String): Boolean {
        return try {
            parseDecimal(text)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
