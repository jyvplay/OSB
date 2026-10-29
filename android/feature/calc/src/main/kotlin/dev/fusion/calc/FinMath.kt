// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import kotlin.math.abs
import kotlin.math.pow

/**
 * Clean-room Time-Value-of-Money (TVM) & cash-flow financial math kernel for `:feature:calc`
 * (`PMT`, `PV`, `FV`, `NPV`, `CAGR`), following the standard SpreadsheetML / OASIS OpenFormula
 * annuity identity:
 *
 *   `PV * (1 + r)^n + PMT * (1 + r * type) * (((1 + r)^n - 1) / r) + FV = 0`
 *
 * Handles the removable `r == 0` singularity analytically (`PV + PMT * n + FV = 0`) so 0% APR
 * schedules never divide by zero or lose precision.
 */
object FinMath {
    const val SCOPE =
        "Closed-form TVM annuity functions (PMT, PV, FV with type=0|1 and exact r=0 branch), periodic NPV, and CAGR; iterative IRR/XIRR root-finding and day-count bond conventions out of scope."

    const val MAX_CASH_FLOWS = 10_000

    fun pmt(rate: Double, nper: Double, pv: Double, fv: Double = 0.0, type: Int = 0): Double {
        validateCommon(rate, nper, type, pv, fv)
        if (abs(rate) < 1e-15) {
            return checkFinite(-(pv + fv) / nper)
        }
        val compound = (1.0 + rate).pow(nper)
        val denom = (1.0 + rate * type) * (compound - 1.0) / rate
        require(denom != 0.0 && denom.isFinite()) { "degenerate PMT denominator" }
        return checkFinite(-(pv * compound + fv) / denom)
    }

    fun pv(rate: Double, nper: Double, pmt: Double, fv: Double = 0.0, type: Int = 0): Double {
        validateCommon(rate, nper, type, pmt, fv)
        if (abs(rate) < 1e-15) {
            return checkFinite(-(pmt * nper + fv))
        }
        val compound = (1.0 + rate).pow(nper)
        val annuity = pmt * (1.0 + rate * type) * ((compound - 1.0) / rate)
        return checkFinite(-(annuity + fv) / compound)
    }

    fun fv(rate: Double, nper: Double, pmt: Double, pv: Double = 0.0, type: Int = 0): Double {
        validateCommon(rate, nper, type, pmt, pv)
        if (abs(rate) < 1e-15) {
            return checkFinite(-(pv + pmt * nper))
        }
        val compound = (1.0 + rate).pow(nper)
        val annuity = pmt * (1.0 + rate * type) * ((compound - 1.0) / rate)
        return checkFinite(-(pv * compound + annuity))
    }

    /**
     * Spreadsheet `NPV(rate, cf_1, ..., cf_k)` = `sum_{t=1..k} cf_t / (1 + rate)^t`.
     * (As in Excel/Calc, a `t=0` initial outlay `cf_0` is added outside `npv` by the caller.)
     */
    fun npv(rate: Double, cashFlows: List<Double>): Double {
        require(rate.isFinite() && rate > -1.0) { "discount rate $rate must be finite and > -1" }
        require(cashFlows.isNotEmpty() && cashFlows.size <= MAX_CASH_FLOWS) {
            "cashFlows size ${cashFlows.size} outside 1..$MAX_CASH_FLOWS"
        }
        var total = 0.0
        var discount = 1.0
        val step = 1.0 + rate
        for (cf in cashFlows) {
            require(cf.isFinite()) { "non-finite cash flow $cf" }
            discount *= step
            total += cf / discount
        }
        return checkFinite(total)
    }

    fun cagr(beginValue: Double, endValue: Double, periods: Double): Double {
        require(beginValue.isFinite() && beginValue > 0.0) { "beginValue must be > 0" }
        require(endValue.isFinite() && endValue > 0.0) { "endValue must be > 0" }
        require(periods.isFinite() && periods > 0.0) { "periods must be > 0" }
        return checkFinite((endValue / beginValue).pow(1.0 / periods) - 1.0)
    }

    private fun validateCommon(rate: Double, nper: Double, type: Int, a: Double, b: Double) {
        require(rate.isFinite() && rate > -1.0) { "rate $rate must be finite and > -1.0" }
        require(nper.isFinite() && nper > 0.0) { "nper $nper must be finite and > 0" }
        require(type == 0 || type == 1) { "annuity timing type must be 0 (end) or 1 (beginning), got $type" }
        require(a.isFinite() && b.isFinite()) { "non-finite monetary argument" }
    }

    private fun checkFinite(v: Double): Double {
        require(v.isFinite()) { "TVM calculation overflowed to $v" }
        return if (v == 0.0) 0.0 else v
    }
}
