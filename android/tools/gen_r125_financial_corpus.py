#!/usr/bin/env python3
"""R125 oracle generator for XNPV / XIRR / ACCRINT / ACCRINTM financial functions.

Pure-stdlib implementation. Expected values are computed from the documented definitions
using only `math` and `datetime`. The corpus is the source of truth for both
`tools/check_r125_financial.py` and `R125FinancialTest.kt`.
"""
from __future__ import annotations

import math
import sys
from datetime import date, timedelta


# ---------------- Day count basis ----------------

def days_between(d1: date, d2: date, basis: int) -> float:
    """Day count between two dates per Excel basis convention."""
    if basis in (0, 4):
        # 30/360 (US/NASD or European)
        y1, m1, d1d = d1.year, d1.month, d1.day
        y2, m2, d2d = d2.year, d2.month, d2.day
        dd1 = 30 if (basis == 0 and d1d == 31) else d1d
        dd2 = 30 if (basis == 0 and d2d == 31 and dd1 == 30) else d2d
        ee1 = 30 if (basis == 4 and d1d == 31) else d1d
        ee2 = 30 if (basis == 4 and d2d == 31) else d2d
        return float(360 * (y2 - y1) + 30 * (m2 - m1) + (ee2 - ee1))
    elif basis == 1:
        return ChronoUnitDays(d1, d2) / 365.25  # Actual/Actual (approximation)
    elif basis == 2:
        return ChronoUnitDays(d1, d2) / 360.0   # Actual/360
    elif basis == 3:
        return ChronoUnitDays(d1, d2) / 365.0   # Actual/365
    else:
        return ChronoUnitDays(d1, d2) / 365.0


def ChronoUnitDays(d1: date, d2: date) -> float:
    return float((d2 - d1).days)


# ---------------- XNPV ----------------

def xnpv(rate: float, values: list, dates: list) -> float:
    """XNPV = sum(values[i] / (1 + rate)^((dates[i] - dates[0]) / 365))"""
    if not values or not dates or len(values) != len(dates):
        return float('nan')
    if rate <= -1.0:
        return float('nan')
    d0 = dates[0]
    total = 0.0
    for v, d in zip(values, dates):
        days = (d - d0).days
        total += v / math.pow(1.0 + rate, days / 365.0)
    return total


# ---------------- XIRR ----------------

def xirr(values: list, dates: list, guess: float = 0.1) -> float:
    """XIRR: rate such that XNPV(rate, values, dates) == 0."""
    if not values or not dates or len(values) != len(dates) or len(values) < 2:
        return float('nan')
    has_pos = any(v > 0 for v in values)
    has_neg = any(v < 0 for v in values)
    if not has_pos or not has_neg:
        return float('nan')

    rate = guess if guess > -1.0 else 0.1
    # Newton-Raphson
    for _ in range(100):
        npv = xnpv(rate, values, dates)
        if abs(npv) < 1e-12:
            return rate
        d0 = dates[0]
        deriv = 0.0
        for v, d in zip(values, dates):
            days = (d - d0).days
            deriv -= v * days / (365.0 * math.pow(1.0 + rate, days / 365.0 + 1.0))
        if deriv == 0.0:
            break
        new_rate = rate - npv / deriv
        if new_rate <= -1.0 or math.isnan(new_rate) or not math.isfinite(new_rate):
            break
        rate = new_rate
    # Bisection fallback
    lo, hi = -0.999999, 1e6
    for _ in range(200):
        mid = (lo + hi) / 2.0
        npv = xnpv(mid, values, dates)
        if abs(npv) < 1e-12:
            return mid
        npv_lo = xnpv(lo, values, dates)
        if npv_lo * npv < 0:
            hi = mid
        else:
            lo = mid
    return float('nan')


# ---------------- ACCRINT ----------------

def accrint(issue: date, first_interest: date, settlement: date, rate: float,
            par: float, frequency: float, basis: int) -> float:
    """Accrued interest for periodic interest security."""
    if rate < 0.0 or par <= 0.0:
        return float('nan')
    if frequency not in (1.0, 2.0, 4.0):
        return float('nan')
    if issue > settlement or first_interest > settlement:
        return float('nan')
    days = days_between(issue, settlement, basis)
    freq_days = {1.0: 365.0, 2.0: 182.5, 4.0: 91.25}[frequency]
    return par * rate * days / freq_days


# ---------------- ACCRINTM ----------------

def accrintm(issue: date, settlement: date, rate: float, par: float, basis: int) -> float:
    """Accrued interest at maturity."""
    if rate < 0.0 or par <= 0.0:
        return float('nan')
    if issue > settlement:
        return float('nan')
    days = days_between(issue, settlement, basis)
    return par * rate * days / 365.0


# ---------------- corpus rendering ----------------

def fmt(v: float) -> str:
    if v != v:  # NaN
        return "#NUM!"
    if v == float('inf') or v == float('-inf'):
        return "#NUM!"
    return repr(v)


def render() -> str:
    rows = [("FUNCTION", "ARGS", "EXPECTED")]

    # ---- XNPV(rate, values, dates) ----
    # values = [-1000, 200, 300, 400, 500], dates = [2024-01-01, 2024-03-01, 2024-05-01, 2024-07-01, 2024-09-01], rate = 0.1
    v1 = [-1000.0, 200.0, 300.0, 400.0, 500.0]
    d1 = [date(2024, 1, 1), date(2024, 3, 1), date(2024, 5, 1), date(2024, 7, 1), date(2024, 9, 1)]
    rows.append(("XNPV", f"0.1,{serialize_vector(v1)},{serialize_dates(d1)}", fmt(xnpv(0.1, v1, d1))))
    rows.append(("XNPV", f"0.05,{serialize_vector(v1)},{serialize_dates(d1)}", fmt(xnpv(0.05, v1, d1))))
    rows.append(("XNPV", f"0.0,{serialize_vector(v1)},{serialize_dates(d1)}", fmt(xnpv(0.0, v1, d1))))  # rate=0 -> undiscounted sum
    rows.append(("XNPV", f"-1.1,{serialize_vector(v1)},{serialize_dates(d1)}", "#NUM!"))  # rate <= -1

    # ---- XIRR(values, dates, [guess]) ----
    v2 = [-1000.0, 200.0, 300.0, 400.0, 500.0]
    d2 = [date(2024, 1, 1), date(2024, 3, 1), date(2024, 5, 1), date(2024, 7, 1), date(2024, 9, 1)]
    irr_result = xirr(v2, d2, 0.1)
    rows.append(("XIRR", f"{serialize_vector(v2)},{serialize_dates(d2)},0.1", fmt(irr_result)))
    # Verify: XNPV at IRR should be ~0
    if not math.isnan(irr_result):
        check_npv = xnpv(irr_result, v2, d2)
        rows.append(("XIRR", f"{serialize_vector(v2)},{serialize_dates(d2)}", fmt(irr_result)))
    # Another XIRR case with different cash flows
    v3 = [-5000.0, 1000.0, 2000.0, 3000.0]
    d3 = [date(2023, 1, 1), date(2023, 7, 1), date(2024, 1, 1), date(2024, 7, 1)]
    irr3 = xirr(v3, d3, 0.1)
    rows.append(("XIRR", f"{serialize_vector(v3)},{serialize_dates(d3)},0.1", fmt(irr3)))
    rows.append(("XIRR", f"{serialize_vector([100.0, 200.0])},{serialize_dates([date(2024,1,1), date(2024,6,1)])}", "#NUM!"))  # all positive
    rows.append(("XIRR", f"{serialize_vector([-100.0, -200.0])},{serialize_dates([date(2024,1,1), date(2024,6,1)])}", "#NUM!"))  # all negative

    # ---- ACCRINT(issue, first_interest, settlement, rate, par, frequency, [basis], [calc_method]) ----
    issue = date(2024, 1, 1)
    first_int = date(2024, 3, 1)
    settlement = date(2024, 6, 15)
    rows.append(("ACCRINT", f"{serialize_date(issue)},{serialize_date(first_int)},{serialize_date(settlement)},0.05,1000,2,0", fmt(accrint(issue, first_int, settlement, 0.05, 1000.0, 2.0, 0))))
    rows.append(("ACCRINT", f"{serialize_date(issue)},{serialize_date(first_int)},{serialize_date(settlement)},0.05,1000,2,1", fmt(accrint(issue, first_int, settlement, 0.05, 1000.0, 2.0, 1))))
    rows.append(("ACCRINT", f"{serialize_date(issue)},{serialize_date(first_int)},{serialize_date(settlement)},0.05,1000,2,4", fmt(accrint(issue, first_int, settlement, 0.05, 1000.0, 2.0, 4))))
    rows.append(("ACCRINT", f"{serialize_date(issue)},{serialize_date(first_int)},{serialize_date(settlement)},-0.05,1000,2,0", "#NUM!"))  # negative rate
    rows.append(("ACCRINT", f"{serialize_date(settlement)},{serialize_date(first_int)},{serialize_date(issue)},0.05,1000,2,0", "#NUM!"))  # issue after settlement

    # ---- ACCRINTM(issue, settlement, rate, par, [basis]) ----
    issue_m = date(2024, 1, 1)
    settlement_m = date(2024, 12, 31)
    rows.append(("ACCRINTM", f"{serialize_date(issue_m)},{serialize_date(settlement_m)},0.05,10000,0", fmt(accrintm(issue_m, settlement_m, 0.05, 10000.0, 0))))
    rows.append(("ACCRINTM", f"{serialize_date(issue_m)},{serialize_date(settlement_m)},0.05,10000,3", fmt(accrintm(issue_m, settlement_m, 0.05, 10000.0, 3))))
    rows.append(("ACCRINTM", f"{serialize_date(issue_m)},{serialize_date(settlement_m)},-0.05,10000,0", "#NUM!"))  # negative rate
    rows.append(("ACCRINTM", f"{serialize_date(settlement_m)},{serialize_date(issue_m)},0.05,10000,0", "#NUM!"))  # issue after settlement

    # ---- Wrong arity refusals ----
    rows.append(("XNPV", "0.1", "#VALUE!"))           # too few args
    rows.append(("XNPV", f"0.1,{serialize_vector([-1000.0, 200.0])},{serialize_dates([date(2024,1,1)])}", "#VALUE!"))  # size mismatch
    rows.append(("XIRR", "{-1000,200}", "#VALUE!"))   # too few args
    rows.append(("ACCRINT", f"{serialize_date(issue)},{serialize_date(first_int)},{serialize_date(settlement)},0.05,1000", "#VALUE!"))  # missing frequency
    rows.append(("ACCRINTM", f"{serialize_date(issue_m)},{serialize_date(settlement_m)},0.05,10000", fmt(accrintm(issue_m, settlement_m, 0.05, 10000.0, 0))))  # valid with default basis

    lines = ["\t".join(str(c) for c in row) for row in rows]
    return "\n".join(lines) + "\n"


def serialize_vector(values: list) -> str:
    """Serialize a 1D vector as Excel array literal {val1,val2,...}."""
    return "{" + ",".join(str(v) for v in values) + "}"


def serialize_dates(dates: list) -> str:
    """Serialize dates as Excel array literal of serial numbers."""
    epoch = date(1899, 12, 30)
    return "{" + ",".join(str((d - epoch).days) for d in dates) + "}"


def serialize_date(d: date) -> str:
    """Serialize a single date as Excel serial number."""
    epoch = date(1899, 12, 30)
    return str((d - epoch).days)


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
