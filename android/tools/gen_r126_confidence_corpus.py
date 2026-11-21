#!/usr/bin/env python3
"""R126 oracle generator for `CONFIDENCE` (legacy alias for CONFIDENCE.NORM).

Pure-stdlib implementation. Expected values are computed from the documented definition:
CONFIDENCE(alpha, standard_dev, size) = NORM.S.INV(1 - alpha/2) * standard_dev / sqrt(size).

The corpus is the source of truth for both `tools/check_r126_confidence.py` and
`R126ConfidenceTest.kt`.
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


# ---------------- CONFIDENCE ----------------

def confidence(alpha: float, std_dev: float, size: float) -> float:
    """CONFIDENCE(alpha, standard_dev, size) = NORM.S.INV(1 - alpha/2) * std_dev / sqrt(size)."""
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

    # ---- CONFIDENCE(alpha, standard_dev, size) ----
    conf_cases = [
        (0.05, 1.0, 100),     # 1.96 * 1 / 10 = 0.195996...
        (0.05, 2.0, 100),     # 0.391992...
        (0.01, 1.0, 100),     # 2.576 * 0.1 = 0.257582...
        (0.10, 1.0, 100),     # 1.645 * 0.1 = 0.164485...
        (0.05, 1.5, 50),      # different std_dev and size
        (0.10, 1.0, 50),      # different size
        (0.05, 1.0, 10),      # small sample
        (0.05, 5.0, 200),     # large std_dev, large sample
    ]
    for alpha, sd, size in conf_cases:
        rows.append(("CONFIDENCE", f"{alpha},{sd},{size}", fmt(confidence(alpha, sd, size))))

    # ---- Domain refusals ----
    rows.append(("CONFIDENCE", "0,1,100", "#NUM!"))      # alpha <= 0
    rows.append(("CONFIDENCE", "1,1,100", "#NUM!"))      # alpha >= 1
    rows.append(("CONFIDENCE", "0.05,-1,100", "#NUM!"))  # std_dev <= 0
    rows.append(("CONFIDENCE", "0.05,1,0", "#NUM!"))     # size < 1
    rows.append(("CONFIDENCE", "0.05,1,-5", "#NUM!"))    # size < 1

    # ---- Wrong arity refusals ----
    rows.append(("CONFIDENCE", "0.05,1", "#VALUE!"))     # only 2 args
    rows.append(("CONFIDENCE", "0.05,1,100,1", "#VALUE!"))  # 4 args
    rows.append(("CONFIDENCE", "", "#VALUE!"))           # 0 args

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
