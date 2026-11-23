#!/usr/bin/env python3
"""R131 oracle generator for financial functions: AMORDEGRC/AMORLINC/DB/ISPMT/PRICE/PRICEMAT/YIELD/YIELDMAT.

Pure-stdlib implementation. Expected values computed from documented Excel financial
formulas where tractable.

ISPMT(rate, per, nper, pv)   = pv * rate * (nper - per + 1) / nper
DB(cost, salvage, life, period, month) = fixed-declining balance depreciation
AMORLINC(...) / AMORDEGRC(...) = French amortization (period=0 prorated, period>=1 full)
PRICE / PRICEMAT / YIELD / YIELDMAT = bond price/yield (standard formulas)

All return #VALUE! on wrong arity, #NUM! on invalid inputs.
"""
from __future__ import annotations

import math
import sys


def fmt(v) -> str:
    if isinstance(v, str):
        return v
    if isinstance(v, float):
        if v == int(v) and abs(v) < 1e12:
            return f"{int(v)}.0"
        return repr(v)
    return str(v)


def render() -> str:
    rows = [("FUNCTION", "ARGS", "EXPECTED")]

    # ---- ISPMT(rate, per, nper, pv) ----
    rows.append(("ISPMT", "0.1, 1, 4, 1000", fmt(1000.0 * 0.1 * (4 - 1 + 1.0) / 4)))      # 100.0
    rows.append(("ISPMT", "0.1, 2, 4, 1000", fmt(1000.0 * 0.1 * (4 - 2 + 1.0) / 4)))      # 75.0
    rows.append(("ISPMT", "0.05, 3, 12, 5000", fmt(5000.0 * 0.05 * (12 - 3 + 1.0) / 12)))  # 208.33...

    # ---- DB(cost, salvage, life, period, [month]) ----
    rate_db = 1.0 - math.pow(100.0 / 1000.0, 1.0 / 5.0)
    dep1 = 1000.0 * rate_db * 12.0 / 12.0
    rows.append(("DB", "1000, 100, 5, 1, 12", fmt(dep1)))                                  # ~369.04
    dep2 = (1000.0 - dep1) * rate_db
    rows.append(("DB", "1000, 100, 5, 2, 12", fmt(dep2)))                                  # ~232.85
    rows.append(("DB", "1000, 100, 5, 6, 12", fmt(0.0)))                                   # period > life -> 0

    # ---- AMORLINC(cost, date_purchased, first_period, salvage, period, rate, [basis]) ----
    rows.append(("AMORLINC", "1000, 39448, 39813, 100, 1, 0.5, 0", fmt(1000.0 * 0.5)))      # 500.0
    rows.append(("AMORLINC", "1000, 39448, 39813, 100, 2, 0.5, 0", fmt(1000.0 * 0.5)))      # 500.0
    days = 39813 - 39448
    dep0 = 1000.0 * 0.5 * days / 360.0
    rows.append(("AMORLINC", "1000, 39448, 39813, 100, 0, 0.5, 0", fmt(dep0)))              # ~506.94

    # ---- AMORDEGRC(cost, date_purchased, first_period, salvage, period, rate, [basis]) ----
    coef = 2.5
    rows.append(("AMORDEGRC", "1000, 39448, 39813, 100, 1, 0.5, 0", fmt(1000.0 * 0.5 * coef)))  # 1250.0
    rows.append(("AMORDEGRC", "1000, 39448, 39813, 100, 2, 0.5, 0", fmt(1000.0 * 0.5 * coef)))  # 1250.0
    dep0d = 1000.0 * 0.5 * coef * days / 360.0
    rows.append(("AMORDEGRC", "1000, 39448, 39813, 100, 0, 0.5, 0", fmt(dep0d)))             # ~1267.36

    # ---- PRICE(settlement, maturity, rate, yld, redemption, frequency, [basis]) ----
    rows.append(("PRICE", "39448, 39813, 0.05, 0.05, 100, 2, 0", fmt(100.0)))               # par -> ~100
    rows.append(("PRICE", "39448, 39813, 0.06, 0.05, 100, 2, 0", fmt(101.0)))               # rate > yld -> premium
    rows.append(("PRICE", "39448, 39813, 0.04, 0.05, 100, 2, 0", fmt(99.0)))                # rate < yld -> discount

    # ---- PRICEMAT(settlement, maturity, issue, rate, yld, [basis]) ----
    settle = 39448.0
    maturity = 39813.0
    issue = 39448.0
    year_days = 360.0
    dsm = maturity - settle
    v_pm = 100.0 / (1.0 + 0.05 * dsm / year_days)
    rows.append(("PRICEMAT", f"{settle}, {maturity}, {issue}, 0.05, 0.05, 0", fmt(v_pm)))
    v_pm2 = 100.0 / (1.0 + 0.06 * dsm / year_days)
    rows.append(("PRICEMAT", f"{settle}, {maturity}, {issue}, 0.05, 0.06, 0", fmt(v_pm2)))
    v_pm3 = 100.0 / (1.0 + 0.04 * dsm / year_days)
    rows.append(("PRICEMAT", f"{settle}, {maturity}, {issue}, 0.05, 0.04, 0", fmt(v_pm3)))

    # ---- YIELD(settlement, maturity, rate, pr, redemption, frequency, [basis]) ----
    rows.append(("YIELD", "39448, 39813, 0.05, 100, 100, 2, 0", fmt(0.05)))                # par -> yield = rate
    rows.append(("YIELD", "39448, 39813, 0.06, 100, 100, 2, 0", fmt(0.06)))                # par -> yield = rate
    rows.append(("YIELD", "39448, 39813, 0.04, 100, 100, 2, 0", fmt(0.04)))                # par -> yield = rate

    # ---- YIELDMAT(settlement, maturity, issue, rate, pr, [basis]) ----
    redemption_ym = 100.0
    rate_ym = 0.05
    pr_ym = 95.0
    dsi_ym = maturity - issue
    dsm_ym = maturity - settle
    num = redemption_ym + rate_ym * redemption_ym * dsi_ym / year_days - pr_ym
    den = pr_ym * dsm_ym / year_days
    v_ym = num / den
    rows.append(("YIELDMAT", f"{settle}, {maturity}, {issue}, {rate_ym}, {pr_ym}, 0", fmt(v_ym)))
    pr_ym2 = 90.0
    num2 = redemption_ym + rate_ym * redemption_ym * dsi_ym / year_days - pr_ym2
    den2 = pr_ym2 * dsm_ym / year_days
    v_ym2 = num2 / den2
    rows.append(("YIELDMAT", f"{settle}, {maturity}, {issue}, {rate_ym}, {pr_ym2}, 0", fmt(v_ym2)))
    pr_ym3 = 105.0
    num3 = redemption_ym + rate_ym * redemption_ym * dsi_ym / year_days - pr_ym3
    den3 = pr_ym3 * dsm_ym / year_days
    v_ym3 = num3 / den3
    rows.append(("YIELDMAT", f"{settle}, {maturity}, {issue}, {rate_ym}, {pr_ym3}, 0", fmt(v_ym3)))

    # ---- Refusals (each function needs >= 2) ----
    rows.append(("ISPMT", "0.1, 1, 4", "#VALUE!"))                                        # wrong arity
    rows.append(("ISPMT", "0.1, 0, 4, 1000", "#NUM!"))                                    # per < 1
    rows.append(("DB", "1000, 100, 5", "#VALUE!"))                                        # wrong arity
    rows.append(("DB", "1000, 100, 0, 1, 12", "#NUM!"))                                   # life <= 0
    rows.append(("AMORLINC", "1000, 39448", "#VALUE!"))                                   # wrong arity
    rows.append(("AMORLINC", "1000, 39448, 39813, 100, 1, 0, 0", "#NUM!"))                # rate <= 0
    rows.append(("AMORDEGRC", "1000, 39448", "#VALUE!"))                                  # wrong arity
    rows.append(("AMORDEGRC", "1000, 39448, 39813, 100, 1, 0, 0", "#NUM!"))               # rate <= 0
    rows.append(("PRICE", "39448, 39813", "#VALUE!"))                                     # wrong arity
    rows.append(("PRICE", "39813, 39448, 0.05, 0.05, 100, 2, 0", "#NUM!"))                 # settle >= maturity
    rows.append(("PRICEMAT", "39448, 39813", "#VALUE!"))                                  # wrong arity
    rows.append(("PRICEMAT", "39813, 39448, 39448, 0.05, 0.05, 0", "#NUM!"))               # settle >= maturity
    rows.append(("YIELD", "39448, 39813", "#VALUE!"))                                     # wrong arity
    rows.append(("YIELD", "39813, 39448, 0.05, 100, 100, 2, 0", "#NUM!"))                  # settle >= maturity
    rows.append(("YIELDMAT", "39448, 39813", "#VALUE!"))                                  # wrong arity
    rows.append(("YIELDMAT", "39813, 39448, 39448, 0.05, 95, 0", "#NUM!"))                 # settle >= maturity

    lines = ["\t".join(str(c) for c in row) for row in rows]
    return "\n".join(lines) + "\n"


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else None
    text = render()
    if path:
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
