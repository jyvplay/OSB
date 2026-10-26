// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Reduced fraction with a positive denominator. */
data class Fraction(val num: Long, val den: Long) {
    init {
        require(den > 0) { "non-positive denominator" }
        require(gcd(num, den) == 1L) { "not reduced" }
    }
    companion object {
        private tailrec fun gcd(a: Long, b: Long): Long =
            if (b == 0L) kotlin.math.abs(a) else gcd(b, a % b)
        fun of(num: Long, den: Long): Fraction {
            require(den != 0L) { "zero denominator" }
            val g = gcd(num, den)
            val n = num / g
            val d = den / g
            return if (d < 0) Fraction(-n, -d) else Fraction(n, d)
        }
    }
}

/**
 * Best rational approximation with a bounded denominator via continued
 * fractions (convergents, semiconvergent at the bound, at most 64 steps).
 * Exact for rationals whose reduced denominator fits. Overflow is
 * impossible by construction: denominators are checked before numerators
 * are formed, and huge partial quotients shortcut to the semiconvergent.
 * Caps: denominator 1..1M, |value| <= 1e12. Double-to-BigDecimal
 * conversion is caller duty.
 */
object FractionApprox {
    const val MAX_DEN = 1_000_000
    private val MAX_ABS = BigDecimal.TEN.pow(12)
    private val MC = MathContext(34, RoundingMode.HALF_EVEN)

    fun approx(value: BigDecimal, maxDen: Int): Fraction {
        require(maxDen in 1..MAX_DEN) { "maxDen $maxDen outside 1..$MAX_DEN" }
        require(value.abs() <= MAX_ABS) { "value over 1e12" }
        if (value.signum() == 0) return Fraction(0, 1)
        val neg = value.signum() < 0
        val target = value.abs()
        var x = target
        var p0 = 0L
        var p1 = 1L
        var q0 = 1L
        var q1 = 0L
        repeat(64) {
            val aDec = x.setScale(0, RoundingMode.FLOOR)
            // Later steps (q1 >= 1) with a huge quotient must shortcut: the
            // denominator would exceed maxDen and a.toLong() could overflow.
            if (q1 >= 1 && aDec > BigDecimal(maxDen.toLong())) {
                return signed(semi(p0, p1, q0, q1, maxDen, target), neg)
            }
            val a = aDec.toLong()
            // Denominator first: only a fitting q admits its numerator, so
            // p = a*p1+p0 cannot overflow (p/q ~= x <= 1e12, q <= maxDen).
            val q = Math.addExact(Math.multiplyExact(a, q1), q0)
            if (q > maxDen) return signed(semi(p0, p1, q0, q1, maxDen, target), neg)
            val p = Math.addExact(Math.multiplyExact(a, p1), p0)
            p0 = p1; p1 = p; q0 = q1; q1 = q
            val frac = x.subtract(BigDecimal(a))
            if (frac.signum() == 0) return signed(Fraction.of(p, q), neg)
            x = BigDecimal.ONE.divide(frac, MC)
        }
        return signed(Fraction.of(p1, q1), neg)
    }

    private fun semi(p0: Long, p1: Long, q0: Long, q1: Long, maxDen: Int, x: BigDecimal): Fraction {
        // Closest of the full-k semiconvergent and the previous convergent
        // (the k+1 semiconvergent always exceeds maxDen, so it is not one).
        // Ties prefer the previous convergent. k = 0 is valid (yields p0/q0).
        val k = (maxDen - q0) / q1
        val c1 = Fraction.of(k * p1 + p0, k * q1 + q0)
        val prev = Fraction.of(p1, q1)
        return if (err(c1, x) < err(prev, x)) c1 else prev
    }

    private fun err(f: Fraction, x: BigDecimal): BigDecimal =
        BigDecimal(f.num).divide(BigDecimal(f.den), MC).subtract(x).abs()

    private fun signed(f: Fraction, neg: Boolean): Fraction =
        if (neg) Fraction.of(-f.num, f.den) else f
}
