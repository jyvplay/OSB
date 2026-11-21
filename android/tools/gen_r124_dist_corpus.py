#!/usr/bin/env python3
"""R124 oracle generator for NORM.S.DIST / NORM.S.INV / BINOM.DIST / POISSON.DIST / EXPON.DIST / CONFIDENCE.NORM.

Pure-stdlib implementation. Expected values are computed from the documented definitions
using only `math`, mirroring the Kotlin kernels. The corpus is the source of truth for
both `tools/check_r124_dist.py` and `R124DistTest.kt`.
"""
from __future__ import annotations

import math
import sys


# ---------------- erfApprox mirror (matches Kotlin erfApprox, A&S 7.1.26) ----------------

def erf_approx(x: float) -> float:
    if x == 0.0:
        return 0.0
    sign = 1.0 if x >= 0.0 else -1.0
    ax = abs(x)
    t = 1.0 / (1.0 + 0.3275911 * ax)
    poly = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t
    return sign * (1.0 - poly * math.exp(-ax * ax))


# ---------------- NORM.S.DIST ----------------

def norm_s_dist(x: float, cumulative: bool) -> float:
    if cumulative:
        return 0.5 * (1.0 + erf_approx(x / math.sqrt(2.0)))
    return math.exp(-(x * x) / 2.0) / math.sqrt(2.0 * math.pi)


# ---------------- NORM.S.INV (Acklam's algorithm) ----------------

def norm_s_inv(p: float) -> float:
    if p <= 0.0 or p >= 1.0:
        return float('nan')
    a = [-39.69683028665376, 220.9460984245205, -275.9285104469687,
         138.3577518672690, -30.66479806614716, 2.506628277459239]
    b = [-54.47609879822406, 161.5858368580409, -155.6989798598866,
         66.80131188771972, -13.28068155288572]
    c = [-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838,
         -2.549732539343734, 4.374664141464968, 2.938163982698783]
    d = [7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996,
         3.754408661907416]
    plow = 0.02425
    phigh = 1.0 - plow
    if p < plow:
        q = math.sqrt(-2.0 * math.log(p))
        num = (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
        den = ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
        return num / den
    if p <= phigh:
        q = p - 0.5
        r = q * q
        num = (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q
        den = ((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0
        return num / den
    q = math.sqrt(-2.0 * math.log(1.0 - p))
    num = (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
    den = ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
    return -num / den


# ---------------- BINOM.DIST ----------------

def binom_dist(k: float, n: float, p: float, cumulative: bool) -> float:
    if k < 0.0 or k > n or p < 0.0 or p > 1.0:
        return float('nan')
    ki = int(k)
    ni = int(n)
    if not cumulative:
        # PMF via lgamma
        log_p = math.lgamma(ni + 1) - math.lgamma(ki + 1) - math.lgamma(ni - ki + 1)
        if p > 0.0 and ki > 0:
            log_p += ki * math.log(p)
        if (1.0 - p) > 0.0 and (ni - ki) > 0:
            log_p += (ni - ki) * math.log(1.0 - p)
        if p == 0.0 and ki > 0:
            return float('nan')
        if p == 1.0 and (ni - ki) > 0:
            return float('nan')
        return math.exp(log_p)
    # CDF: direct sum of PMFs (reliable for test-sized n)
    total = 0.0
    for i in range(ki + 1):
        total += binom_dist(float(i), float(ni), p, False)
    return min(total, 1.0)


# ---------------- POISSON.DIST ----------------

def poisson_dist(x: float, mean: float, cumulative: bool) -> float:
    if x < 0.0 or mean < 0.0:
        return float('nan')
    xi = int(x)
    if not cumulative:
        if mean == 0.0:
            return 1.0 if xi == 0 else 0.0
        log_p = -mean
        if xi > 0 and mean > 0.0:
            log_p += xi * math.log(mean)
        for i in range(2, xi + 1):
            log_p -= math.log(i)
        return math.exp(log_p)
    if mean == 0.0:
        return 1.0
    # CDF: P(X <= k) = Q(k+1, mean) = 1 - P(k+1, mean) = 1 - regGammaLowerP(k+1, mean)
    return 1.0 - reg_gamma_lower_p(xi + 1.0, mean)


# ---------------- regGammaLowerP mirror (series + continued fraction) ----------------

def reg_gamma_lower_p(a: float, x: float) -> float:
    if x < 0.0 or a <= 0.0:
        return float('nan')
    if x == 0.0:
        return 0.0
    if x < a + 1.0:
        # Series: gamma(a,x) = x^a * e^-x * sum x^k / (a*(a+1)*...*(a+k))
        ap = a
        total = 1.0 / a
        delta = 1.0 / a
        for k in range(1, 200):
            ap += 1.0
            delta *= x / ap
            total += delta
            if abs(delta) < abs(total) * 1e-15:
                break
        return total * math.exp(-x + a * math.log(x) - math.lgamma(a))
    else:
        # Continued fraction for upper, then P = 1 - Q
        return 1.0 - reg_gamma_upper_cf(a, x)


def reg_gamma_upper_cf(a: float, x: float) -> float:
    # Modified Lentz algorithm for the upper incomplete gamma continued fraction.
    b = x + 1.0 - a
    c = 1.0 / 1.0e-30
    d = 1.0 / b
    h = d
    for i in range(1, 200):
        an = -i * (i - a)
        b += 2.0
        d = an * d + b
        if abs(d) < 1.0e-30:
            d = 1.0e-30
        c = b + an / c
        if abs(c) < 1.0e-30:
            c = 1.0e-30
        d = 1.0 / d
        delta = d * c
        h *= delta
        if abs(delta - 1.0) < 1.0e-14:
            break
    return math.exp(-x + a * math.log(x) - math.lgamma(a)) * h


# ---------------- EXPON.DIST ----------------

def expon_dist(x: float, lam: float, cumulative: bool) -> float:
    if x < 0.0 or lam <= 0.0:
        return float('nan')
    if cumulative:
        return 1.0 - math.exp(-lam * x)
    return lam * math.exp(-lam * x)


# ---------------- CONFIDENCE.NORM ----------------

def confidence_norm(alpha: float, std_dev: float, size: float) -> float:
    if alpha <= 0.0 or alpha >= 1.0:
        return float('nan')
    if std_dev <= 0.0:
        return float('nan')
    if size < 1.0:
        return float('nan')
    return norm_s_inv(1.0 - alpha / 2.0) * std_dev / math.sqrt(size)


# ---------------- corpus rendering ----------------

def fmt(v: float) -> str:
    if v != v:  # NaN
        return "#NUM!"
    if v == float('inf') or v == float('-inf'):
        return "#NUM!"
    return repr(v)


def render() -> str:
    rows = [("FUNCTION", "ARGS", "EXPECTED")]

    # ---- NORM.S.DIST(x, cumulative) ----
    for x, cum in [(0.0, True), (0.0, False), (1.0, True), (-1.0, True),
                    (1.96, True), (-1.96, True), (2.0, True), (-2.0, True),
                    (3.0, True), (0.5, False)]:
        rows.append(("NORM.S.DIST", f"{x},{'TRUE' if cum else 'FALSE'}",
                     fmt(norm_s_dist(x, cum))))
    rows.append(("NORM.S.DIST", "", "#VALUE!"))              # no args
    rows.append(("NORM.S.DIST", "0,2,3,4", "#VALUE!"))       # too many args

    # ---- NORM.S.INV(probability) ----
    for p in [0.5, 0.8413447460685429, 0.025, 0.975, 0.99865, 0.15865]:
        rows.append(("NORM.S.INV", f"{p}", fmt(norm_s_inv(p))))
    rows.append(("NORM.S.INV", "0", "#NUM!"))
    rows.append(("NORM.S.INV", "1", "#NUM!"))
    rows.append(("NORM.S.INV", "-0.5", "#NUM!"))

    # ---- BINOM.DIST(k, n, p, cumulative) ----
    for k, n, p, cum in [(0, 10, 0.5, False), (5, 10, 0.5, False), (5, 10, 0.5, True),
                          (0, 10, 0.3, False), (3, 10, 0.3, False), (3, 10, 0.3, True),
                          (10, 10, 0.5, True), (0, 10, 0.0, False), (0, 10, 1.0, False)]:
        rows.append(("BINOM.DIST", f"{k},{n},{p},{'TRUE' if cum else 'FALSE'}",
                     fmt(binom_dist(k, n, p, cum))))
    rows.append(("BINOM.DIST", "-1,10,0.5,TRUE", "#NUM!"))
    rows.append(("BINOM.DIST", "11,10,0.5,TRUE", "#NUM!"))
    rows.append(("BINOM.DIST", "5,10,1.5,TRUE", "#NUM!"))

    # ---- POISSON.DIST(x, mean, cumulative) ----
    for x, mean, cum in [(0, 1.0, False), (1, 1.0, False), (2, 1.0, False),
                          (0, 1.0, True), (2, 1.0, True), (5, 3.0, True),
                          (0, 5.0, True), (10, 5.0, True)]:
        rows.append(("POISSON.DIST", f"{x},{mean},{'TRUE' if cum else 'FALSE'}",
                     fmt(poisson_dist(x, mean, cum))))
    rows.append(("POISSON.DIST", "-1,5,TRUE", "#NUM!"))
    rows.append(("POISSON.DIST", "5,-1,TRUE", "#NUM!"))

    # ---- EXPON.DIST(x, lambda, cumulative) ----
    for x, lam, cum in [(0.0, 1.0, False), (1.0, 1.0, False),
                         (0.0, 1.0, True), (1.0, 1.0, True), (2.0, 0.5, True)]:
        rows.append(("EXPON.DIST", f"{x},{lam},{'TRUE' if cum else 'FALSE'}",
                     fmt(expon_dist(x, lam, cum))))
    rows.append(("EXPON.DIST", "-1,1,TRUE", "#NUM!"))
    rows.append(("EXPON.DIST", "1,0,TRUE", "#NUM!"))
    rows.append(("EXPON.DIST", "1,-0.5,TRUE", "#NUM!"))

    # ---- CONFIDENCE.NORM(alpha, standard_dev, size) ----
    for alpha, sd, size in [(0.05, 1.0, 100), (0.05, 2.0, 100),
                             (0.01, 1.0, 100), (0.10, 1.0, 100),
                             (0.05, 1.5, 50), (0.10, 1.0, 50)]:
        rows.append(("CONFIDENCE.NORM", f"{alpha},{sd},{size}",
                     fmt(confidence_norm(alpha, sd, size))))
    rows.append(("CONFIDENCE.NORM", "0,1,100", "#NUM!"))
    rows.append(("CONFIDENCE.NORM", "1,1,100", "#NUM!"))
    rows.append(("CONFIDENCE.NORM", "0.05,-1,100", "#NUM!"))
    rows.append(("CONFIDENCE.NORM", "0.05,1,0", "#NUM!"))

    # ---- Wrong arity refusals ----
    rows.append(("NORM.S.DIST", "0", "#VALUE!"))
    rows.append(("NORM.S.DIST", "0,TRUE,1", "#VALUE!"))
    rows.append(("NORM.S.INV", "", "#VALUE!"))
    rows.append(("NORM.S.INV", "0.5,0.3", "#VALUE!"))
    rows.append(("BINOM.DIST", "5,10,0.5", "#VALUE!"))
    rows.append(("BINOM.DIST", "5,10,0.5,TRUE,1", "#VALUE!"))
    rows.append(("POISSON.DIST", "0,1", "#VALUE!"))
    rows.append(("POISSON.DIST", "0,1,TRUE,1", "#VALUE!"))
    rows.append(("EXPON.DIST", "1,1", "#VALUE!"))
    rows.append(("EXPON.DIST", "1,1,TRUE,1", "#VALUE!"))
    rows.append(("CONFIDENCE.NORM", "0.05,1", "#VALUE!"))
    rows.append(("CONFIDENCE.NORM", "0.05,1,100,1", "#VALUE!"))

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
