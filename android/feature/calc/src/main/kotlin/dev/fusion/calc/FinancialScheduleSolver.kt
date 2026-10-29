// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * Clean-room **Loan/Bond Amortization Schedule Generator & Irregular-Date `XNPV` / `XIRR` Solver**.
 *
 * Complements `FormulaEval`'s regular-period `PMT`/`PV`/`FV`/`NPV`/`IRR` with:
 * - Period-by-period amortization tables (payment, interest component, principal component, ending
 *   balance converging cleanly to `0.0`);
 * - Irregular-date `XNPV` and Newton-Raphson `XIRR` (`365.0`-day year basis per Excel `XIRR`);
 * - Exact financial day-count fractions (`US_30_360`, `ACT_360`, `ACT_365`).
 */
object FinancialScheduleSolver {
    const val SCOPE =
        "Clean-room loan amortization schedule generator, irregular-date XNPV/XIRR Newton-Raphson solver, and US_30_360 / ACT_360 / ACT_365 day-count fraction calculator."

    enum class DayCountBasis { US_30_360, ACT_360, ACT_365 }

    data class AmortizationRow(
        val period: Int,
        val payment: Double,
        val interestPaid: Double,
        val principalPaid: Double,
        val endingBalance: Double,
    )

    data class DatedCashflow(val date: LocalDate, val amount: Double)

    fun buildAmortizationSchedule(principal: Double, annualRate: Double, periodsPerYear: Int, totalPeriods: Int): List<AmortizationRow> {
        require(principal > 0.0 && annualRate >= 0.0 && periodsPerYear >= 1 && totalPeriods in 1..1200) {
            "invalid amortization parameters"
        }
        val r = annualRate / periodsPerYear
        val pmt = if (r == 0.0) principal / totalPeriods else {
            val f = (1.0 + r).pow(totalPeriods)
            principal * (r * f) / (f - 1.0)
        }
        var bal = principal
        return List(totalPeriods) { idx ->
            val k = idx + 1
            val interest = bal * r
            val principalPart = if (k == totalPeriods) bal else (pmt - interest)
            val actualPayment = if (k == totalPeriods) bal + interest else pmt
            bal = (bal - principalPart).coerceAtLeast(0.0)
            AmortizationRow(k, actualPayment, interest, principalPart, bal)
        }
    }

    fun xnpv(rate: Double, flows: List<DatedCashflow>): Double {
        require(flows.isNotEmpty() && rate > -1.0) { "XNPV requires non-empty flows and rate > -1" }
        val d0 = flows.first().date
        return flows.sumOf { cf ->
            val days = ChronoUnit.DAYS.between(d0, cf.date).toDouble()
            cf.amount / (1.0 + rate).pow(days / 365.0)
        }
    }

    fun xirr(flows: List<DatedCashflow>, guess: Double = 0.1): Double {
        require(flows.size >= 2 && flows.any { it.amount > 0.0 } && flows.any { it.amount < 0.0 }) {
            "XIRR requires >= 2 cashflows with both positive and negative amounts"
        }
        val d0 = flows.first().date
        var r = guess
        repeat(100) {
            var f = 0.0
            var df = 0.0
            for (cf in flows) {
                val yr = ChronoUnit.DAYS.between(d0, cf.date).toDouble() / 365.0
                f += cf.amount / (1.0 + r).pow(yr)
                if (yr != 0.0) {
                    df -= (yr * cf.amount) / (1.0 + r).pow(yr + 1.0)
                }
            }
            if (abs(f) < 1e-10) return r
            require(abs(df) >= 1e-15) { "XIRR derivative vanished" }
            val next = r - f / df
            r = if (!next.isFinite() || next <= -0.999999) (r - 0.999999) / 2.0 else next
        }
        throw IllegalArgumentException("XIRR failed to converge within 100 Newton-Raphson iterations")
    }

    fun yearFraction(start: LocalDate, end: LocalDate, basis: DayCountBasis): Double {
        require(!end.isBefore(start)) { "end date before start date" }
        return when (basis) {
            DayCountBasis.ACT_360 -> ChronoUnit.DAYS.between(start, end) / 360.0
            DayCountBasis.ACT_365 -> ChronoUnit.DAYS.between(start, end) / 365.0
            DayCountBasis.US_30_360 -> {
                val d1 = min(start.dayOfMonth, 30)
                val d2 = if (d1 == 30 && end.dayOfMonth == 31) 30 else min(end.dayOfMonth, 30)
                val days360 = 360 * (end.year - start.year) + 30 * (end.monthValue - start.monthValue) + (d2 - d1)
                days360 / 360.0
            }
        }
    }
}
