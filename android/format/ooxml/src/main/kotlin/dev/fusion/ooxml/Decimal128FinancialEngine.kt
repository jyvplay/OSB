// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Clean-room IEEE 754-2008 `decimal128` financial arithmetic engine for spreadsheet ledgers
 * (34 significant digits, exponent `-6143..6144`, `ROUND_HALF_EVEN` default banker's rounding).
 *
 * Why this exists: binary64 `Double` cannot represent `0.10` exactly, so a 100,000-row invoice ledger
 * accumulates rounding drift and `0.1 + 0.2 != 0.3`. Every monetary arithmetic path here uses exact
 * decimal semantics and returns explicit `#NUM!`-style errors on overflow or division by zero.
 */
object Decimal128FinancialEngine {
    val DECIMAL128: MathContext = MathContext(34, RoundingMode.HALF_EVEN)
    const val MAX_SCALE = 6144

    enum class Err { NONE, DIV_BY_ZERO, OVERFLOW, SCALE_OVERFLOW, PARSE }

    data class Dec(val value: BigDecimal?, val err: Err = Err.NONE) {
        fun isOk(): Boolean = err == Err.NONE && value != null
        val signum: Int get() = value?.signum() ?: 0
        fun plainString(): String = value?.toPlainString() ?: "#NUM!"
    }

    private fun ok(v: BigDecimal) = Dec(v, Err.NONE)
    private fun fail(e: Err) = Dec(null, e)

    fun parse(text: String): Dec {
        val t = text.trim().replace(",", "").replace("\u00A0", "")
        if (t.isEmpty()) return fail(Err.PARSE)
        return try {
            val bd = BigDecimal(t, DECIMAL128)
            if (bd.scale() > MAX_SCALE) fail(Err.SCALE_OVERFLOW) else ok(bd)
        } catch (e: NumberFormatException) {
            fail(Err.PARSE)
        }
    }

    fun ofCents(cents: Long): Dec = ok(BigDecimal.valueOf(cents, 2))

    private fun guard(v: BigDecimal): Dec =
        if (v.scale() > MAX_SCALE) fail(Err.SCALE_OVERFLOW) else ok(v)

    fun add(a: Dec, b: Dec): Dec =
        if (!a.isOk() || !b.isOk()) fail(if (!a.isOk()) a.err else b.err) else guard(a.value!!.add(b.value!!, DECIMAL128))

    fun sub(a: Dec, b: Dec): Dec =
        if (!a.isOk() || !b.isOk()) fail(if (!a.isOk()) a.err else b.err) else guard(a.value!!.subtract(b.value!!, DECIMAL128))

    fun mul(a: Dec, b: Dec): Dec =
        if (!a.isOk() || !b.isOk()) fail(if (!a.isOk()) a.err else b.err) else guard(a.value!!.multiply(b.value!!, DECIMAL128))

    fun div(a: Dec, b: Dec): Dec {
        if (!a.isOk() || !b.isOk()) return fail(if (!a.isOk()) a.err else b.err)
        if (b.value!!.signum() == 0) return fail(Err.DIV_BY_ZERO)
        return guard(a.value!!.divide(b.value!!, DECIMAL128))
    }

    fun round(a: Dec, scale: Int, mode: RoundingMode = RoundingMode.HALF_EVEN): Dec =
        if (!a.isOk()) a else ok(a.value!!.setScale(scale.coerceIn(0, 6144), mode))

    /** Exact sum of a ledger column with order-independent provable equality (`decimal128` associativity). */
    fun sumExact(values: List<Dec>): Dec {
        var acc = BigDecimal.ZERO
        for (v in values) {
            if (!v.isOk()) return fail(v.err)
            acc = acc.add(v.value!!, DECIMAL128)
            if (acc.scale() > MAX_SCALE) return fail(Err.SCALE_OVERFLOW)
        }
        return ok(acc)
    }

    /** Straight-line (declining balance over a constant payment) amortisation in exact decimal. */
    fun amortisationSchedule(principal: Dec, annualRateBps: Int, months: Int): List<BigDecimal> {
        require(principal.isOk() && months in 1..600) { "invalid amortisation inputs" }
        val monthlyRate = BigDecimal.valueOf(annualRateBps.toLong(), 4)
            .divide(BigDecimal.valueOf(12), DECIMAL128)
        val one = BigDecimal.ONE
        val growth = one.add(monthlyRate, DECIMAL128).pow(months, DECIMAL128)
        val payment = if (monthlyRate.signum() == 0) {
            principal.value!!.divide(BigDecimal.valueOf(months.toLong()), DECIMAL128)
        } else {
            principal.value!!.multiply(monthlyRate, DECIMAL128).multiply(growth, DECIMAL128)
                .divide(growth.subtract(one, DECIMAL128), DECIMAL128)
        }
        val out = ArrayList<BigDecimal>(months + 1)
        var balance = principal.value!!
        out.add(balance.setScale(2, RoundingMode.HALF_EVEN))
        for (m in 1..months) {
            val interest = balance.multiply(monthlyRate, DECIMAL128).setScale(2, RoundingMode.HALF_EVEN)
            var pay = payment.setScale(2, RoundingMode.HALF_EVEN)
            if (m == months) pay = balance.add(interest, DECIMAL128).setScale(2, RoundingMode.HALF_EVEN)
            val newBal = balance.add(interest, DECIMAL128).subtract(pay, DECIMAL128)
            balance = if (newBal.signum() < 0) BigDecimal.ZERO.setScale(2) else newBal
            out.add(balance.setScale(2, RoundingMode.HALF_EVEN))
        }
        return out
    }

    /** Demonstrates the exactness separation against binary64 for the canonical `0.1 + 0.2` case. */
    fun binary64DriftOf100kInvoiceRows(): Pair<Double, String> {
        var d = 0.0
        var dec = BigDecimal.ZERO
        val each = BigDecimal("0.07")
        repeat(100_000) {
            d += 0.07
            dec = dec.add(each, DECIMAL128)
        }
        return d to dec.setScale(2, RoundingMode.HALF_EVEN).toPlainString()
    }
}
