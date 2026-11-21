#!/usr/bin/env python3
"""Deterministic oracle corpus for R122 `CHISQ.TEST` / `F.TEST` / `Z.TEST` / `T.TEST` / `SKEW.P`.

Renders the committed test fixture `format/ooxml/src/test/resources/r123/stat_test_corpus.tsv`.
Every expected value is recomputed here from the documented definitions independently of the
Kotlin product under test -- this fixture is an oracle, not a transcript of the implementation.

Pure `math` only (no numpy/scipy available in this sandbox; same constraint as the R120 controls).
"""
from __future__ import annotations

import math

SQRT_2PI = math.sqrt(2.0 * math.pi)
SQRT_2 = math.sqrt(2.0)


def regGammaLowerP(a: float, x: float) -> float:
    """Regularized lower incomplete gamma P(a, x) via series expansion."""
    if x <= 0.0:
        return 0.0
    gln = math.lgamma(a)
    term = 1.0 / a
    total = term
    for n in range(1, 500):
        term *= x / (a + n)
        total += term
        if abs(term) <= abs(total) * 1e-15:
            break
    return math.exp(-x + a * math.log(x) - gln) * total


def chisqDistRT(x: float, df: float) -> float:
    """CHISQ.DIST.RT(x, df) = 1 - P(df/2, x/2)."""
    return (1.0 - regGammaLowerP(df / 2.0, x / 2.0)).__abs__() if False else (1.0 - regGammaLowerP(df / 2.0, x / 2.0))


def incompleteBeta(a: float, b: float, x: float) -> float:
    """Regularized incomplete beta I_x(a, b) via continued fraction (Numerical Recipes)."""
    if x <= 0.0:
        return 0.0
    if x >= 1.0:
        return 1.0
    # betacf: continued fraction for incomplete beta
    # I_x(a,b) = x^a * (1-x)^b / (a * B(a,b)) * betacf(a,b,x)
    # where betacf is evaluated via modified Lentz's method
    from math import lgamma, exp, log

    lbeta = lgamma(a) + lgamma(b) - lgamma(a + b)
    bt = exp(lgamma(a + b) - lgamma(a) - lgamma(b) + a * math.log(x) + b * math.log(1.0 - x))

    # Modified Lentz's method for the continued fraction
    qab = a + b
    qap = a + 1.0
    qam = a - 1.0
    c = 1.0
    d = 1.0 - qab * x / qap
    if abs(d) < 1e-30:
        d = 1e-30
    d = 1.0 / d
    h = d
    for m in range(1, 500):
        m2 = 2 * m
        aa = m * (b - m) * x / ((qam + m2) * (a + m2))
        d = 1.0 + aa * d
        if abs(d) < 1e-30:
            d = 1e-30
        c = 1.0 + aa / c
        if abs(c) < 1e-30:
            c = 1e-30
        d = 1.0 / d
        h *= d * c
        aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
        d = 1.0 + aa * d
        if abs(d) < 1e-30:
            d = 1e-30
        c = 1.0 + aa / c
        if abs(c) < 1e-30:
            c = 1e-30
        d = 1.0 / d
        delta = d * c
        h *= delta
        if abs(delta - 1.0) < 1e-14:
            break
    return bt * h / a


def fDistRT(x: float, d1: float, d2: float) -> float:
    """F.DIST.RT(x, d1, d2) = 1 - I(d1/2, d2/2, d1*x/(d1*x+d2))."""
    u = (d1 * x) / (d1 * x + d2)
    return 1.0 - incompleteBeta(d1 / 2.0, d2 / 2.0, u)


def distT(t: float, df: float) -> float:
    """T.DIST.RT(t, df) = 0.5 * I(df/2, 0.5, df/(df+t^2))."""
    u = df / (df + t * t)
    return 0.5 * incompleteBeta(df / 2.0, 0.5, u)


def phiCdf(z: float) -> float:
    """NORM.S.DIST(z, TRUE) = standard normal CDF."""
    return 0.5 * (1.0 + math.erf(z / SQRT_2))


def chisqTest(actual: list[list[float]], expected: list[list[float]]) -> float:
    """CHISQ.TEST: p-value of chi-square test of independence."""
    rows = len(actual)
    cols = len(actual[0])
    chi2 = 0.0
    for i in range(rows):
        for j in range(cols):
            e = expected[i][j]
            if e <= 0.0:
                return float('nan')
            d = actual[i][j] - e
            chi2 += d * d / e
    df = (rows - 1) * (cols - 1)
    return chisqDistRT(chi2, df)


def fTest(xs: list[float], ys: list[float]) -> float:
    """F.TEST: two-sample F test for equal variances."""
    nx, ny = len(xs), len(ys)
    if nx < 2 or ny < 2:
        return float('nan')
    mx = sum(xs) / nx
    my = sum(ys) / ny
    vx = sum((x - mx) ** 2 for x in xs) / (nx - 1)
    vy = sum((y - my) ** 2 for y in ys) / (ny - 1)
    if vx == 0.0 or vy == 0.0:
        return float('nan')
    if vx >= vy:
        largerDf, smallerDf = nx - 1, ny - 1
        largerVar, smallerVar = vx, vy
    else:
        largerDf, smallerDf = ny - 1, nx - 1
        largerVar, smallerVar = vy, vx
    f = largerVar / smallerVar
    return (2.0 * fDistRT(f, largerDf, smallerDf)).__abs__() if False else min(1.0, 2.0 * fDistRT(f, largerDf, smallerDf))


def zTest(xs: list[float], mu: float, sigma: float | None) -> float:
    """Z.TEST: one-sample Z test p-value."""
    n = len(xs)
    if n < 1:
        return float('nan')
    mean = sum(xs) / n
    if sigma is not None:
        s = sigma
    else:
        s = math.sqrt(sum((x - mean) ** 2 for x in xs) / n)
    if s == 0.0:
        return float('nan')
    z = (mean - mu) / (s / math.sqrt(n))
    return 1.0 - phiCdf(z)


def tTest(xs: list[float], ys: list[float], tails: float, type_: float) -> float:
    """T.TEST: Student's t test p-value."""
    nx, ny = len(xs), len(ys)
    if tails not in (1.0, 2.0) or type_ not in (1.0, 2.0, 3.0):
        return float('nan')
    if type_ == 1.0:
        if nx != ny:
            return float('nan')
        md = sum(xs[i] - ys[i] for i in range(nx)) / nx
        ss = sum((xs[i] - ys[i] - md) ** 2 for i in range(nx))
        sdDiff = math.sqrt(ss / (nx - 1))
        if sdDiff == 0.0:
            return float('nan')
        tval = md / (sdDiff / math.sqrt(nx))
        df = nx - 1
    else:
        mx = sum(xs) / nx
        my = sum(ys) / ny
        vx = sum((x - mx) ** 2 for x in xs) / (nx - 1)
        vy = sum((y - my) ** 2 for y in ys) / (ny - 1)
        if type_ == 2.0:
            sp2 = ((nx - 1) * vx + (ny - 1) * vy) / (nx + ny - 2)
            if sp2 == 0.0:
                return float('nan')
            tval = (mx - my) / math.sqrt(sp2 * (1.0 / nx + 1.0 / ny))
            df = nx + ny - 2
        else:
            if vx == 0.0 or vy == 0.0:
                return float('nan')
            num = vx / nx + vy / ny
            tval = (mx - my) / math.sqrt(num)
            df = num * num / (
                (vx / nx) ** 2 / (nx - 1) + (vy / ny) ** 2 / (ny - 1)
            )
    if math.isnan(tval) or df <= 0.0:
        return float('nan')
    rt = distT(abs(tval), df)
    return min(1.0, 2.0 * rt) if tails == 2.0 else rt


def skewP(nums: list[float]) -> float:
    """SKEW.P: population skewness."""
    n = len(nums)
    if n < 3:
        return float('nan')
    mean = sum(nums) / n
    ss = sum((x - mean) ** 2 for x in nums)
    sigma = math.sqrt(ss / n)
    if sigma == 0.0:
        return float('nan')
    m3 = sum(((x - mean) / sigma) ** 3 for x in nums)
    return (n / ((n - 1) * (n - 2))) * m3


# Refusal expectations.
NUM = "#NUM!"
VALUE = "#VALUE!"


def render() -> str:
    lines = ["function\targs\texpected"]

    # CHISQ.TEST rows: 2x2 contingency tables using array literal syntax {a,b;c,d}
    chisq_cases = [
        ([[2, 1], [1, 2]], [[1.5, 1.5], [1.5, 1.5]]),
        ([[5, 3], [2, 4]], [[3.5, 3.5], [3.5, 3.5]]),
        ([[10, 5], [5, 10]], [[7.5, 7.5], [7.5, 7.5]]),
        ([[1, 2], [3, 4]], [[2, 2], [2, 2]]),
        ([[20, 10], [5, 15]], [[12.5, 12.5], [12.5, 12.5]]),
        ([[3, 7], [5, 5]], [[4, 6], [4, 6]]),
        ([[15, 25], [35, 25]], [[25, 25], [25, 25]]),
        ([[8, 2], [4, 6]], [[6, 4], [6, 4]]),
        ([[30, 10], [20, 40]], [[25, 25], [25, 25]]),
        ([[100, 50], [75, 75]], [[87.5, 87.5], [87.5, 87.5]]),
        ([[4, 4, 4], [8, 8, 8]], [[6, 6, 6], [6, 6, 6]]),
        ([[1, 3], [2, 4]], [[1.5, 3.5], [1.5, 3.5]]),
        ([[7, 1], [3, 5]], [[5, 3], [5, 3]]),
        ([[50, 30], [20, 40]], [[35, 35], [35, 35]]),
        ([[2, 8], [6, 4]], [[4, 6], [4, 6]]),
    ]
    for actual, expected in chisq_cases:
        a_str = "{" + ";".join(",".join(str(v) for v in row) for row in actual) + "}"
        e_str = "{" + ";".join(",".join(str(v) for v in row) for row in expected) + "}"
        args = a_str + "," + e_str
        try:
            p = chisqTest(actual, expected)
            if math.isnan(p):
                lines.append(f"CHISQ.TEST\t{args}\t{NUM}")
            else:
                lines.append(f"CHISQ.TEST\t{args}\t{p!r}")
        except Exception:
            lines.append(f"CHISQ.TEST\t{args}\t{NUM}")

    # CHISQ.TEST refusals (wrong arity, numeric args only)
    lines.append(f"CHISQ.TEST\t1\t{VALUE}")
    lines.append(f"CHISQ.TEST\t1,2,3\t{VALUE}")
    lines.append(f"CHISQ.TEST\t1,2,3,4\t{VALUE}")
    lines.append(f"CHISQ.TEST\t1,2,3,4,5\t{VALUE}")
    lines.append(f"CHISQ.TEST\t1,2,3,4,5,6\t{VALUE}")

    # F.TEST rows: array literals {x1,x2,...}
    f_cases = [
        ([1, 2, 3, 4, 5], [2, 4, 6, 8, 10]),
        ([1, 1, 1, 1, 2], [3, 3, 3, 3, 4]),
        ([5, 5, 5, 5, 6], [1, 1, 1, 1, 2]),
        ([10, 20, 30], [1, 2, 3, 4, 5]),
        ([1, 2, 3], [10, 20, 30, 40, 50]),
        ([2, 4, 6, 8, 10], [1, 3, 5, 7, 9]),
        ([1, 2, 3, 4, 5, 6], [2, 4, 6, 8, 10, 12]),
        ([5, 5, 5, 5, 5], [1, 2, 3, 4, 5]),
        ([10, 10, 10, 10], [2, 4, 6, 8]),
        ([3, 3, 3, 4, 5], [1, 1, 1, 1, 2]),
        ([7, 7, 7, 7, 8], [1, 1, 1, 2, 3]),
        ([100, 200, 300], [10, 20, 30]),
        ([1, 1, 2, 2, 3], [4, 4, 5, 5, 6]),
        ([2, 2, 2, 3, 4], [1, 1, 1, 2, 3]),
        ([8, 8, 8, 8, 9], [2, 2, 2, 3, 4]),
    ]
    for xs, ys in f_cases:
        x_str = "{" + ",".join(str(v) for v in xs) + "}"
        y_str = "{" + ",".join(str(v) for v in ys) + "}"
        args = x_str + "," + y_str
        try:
            p = fTest(xs, ys)
            if math.isnan(p):
                lines.append(f"F.TEST\t{args}\t{NUM}")
            else:
                lines.append(f"F.TEST\t{args}\t{p!r}")
        except Exception:
            lines.append(f"F.TEST\t{args}\t{NUM}")

    # F.TEST refusals (wrong arity, numeric args only)
    lines.append(f"F.TEST\t1\t{VALUE}")
    lines.append(f"F.TEST\t1,2,3\t{VALUE}")
    lines.append(f"F.TEST\t1,2,3,4\t{VALUE}")
    lines.append(f"F.TEST\t1,2,3,4,5\t{VALUE}")
    lines.append(f"F.TEST\t1,2,3,4,5,6\t{VALUE}")

    # Z.TEST rows: array literal {x1,x2,...}, x, [sigma]
    z_cases = [
        ([1, 2, 3, 4, 5], 3.0, None),
        ([1, 2, 3, 4, 5], 3.0, 2.0),
        ([10, 20, 30], 20.0, None),
        ([5, 5, 5, 5, 5], 5.0, None),  # sigma=0 -> #NUM!
        ([1, 2, 3, 4, 5], 2.5, None),
        ([2, 4, 6, 8, 10], 6.0, None),
        ([1, 2, 3, 4, 5], 0.0, None),
        ([10, 20, 30, 40], 25.0, None),
        ([5, 10, 15], 10.0, 5.0),
        ([1, 1, 1, 1, 10], 5.0, None),
        ([2, 4, 6, 8], 5.0, None),
        ([100, 200, 300], 200.0, None),
        ([1, 3, 5, 7, 9], 5.0, None),
        ([0, 0, 0, 0, 10], 5.0, None),
        ([3, 3, 3, 3, 3], 3.0, None),
    ]
    for xs, mu, sigma in z_cases:
        x_str = "{" + ",".join(str(v) for v in xs) + "}"
        sigma_str = "" if sigma is None else f",{sigma}"
        args = x_str + f",{mu}" + sigma_str
        try:
            p = zTest(xs, mu, sigma)
            if math.isnan(p):
                lines.append(f"Z.TEST\t{args}\t{NUM}")
            else:
                lines.append(f"Z.TEST\t{args}\t{p!r}")
        except Exception:
            lines.append(f"Z.TEST\t{args}\t{NUM}")

    # Z.TEST refusals (wrong arity, numeric args only)
    lines.append(f"Z.TEST\t1\t{VALUE}")
    lines.append(f"Z.TEST\t1,2,3,4\t{VALUE}")
    lines.append(f"Z.TEST\t1,2,3,4,5\t{VALUE}")
    lines.append(f"Z.TEST\t1,2,3,4,5,6\t{VALUE}")
    lines.append(f"Z.TEST\t1,2,3,4,5,6,7\t{VALUE}")

    # T.TEST rows: array literals {x1,x2,...},{y1,y2,...},tails,type
    t_cases = [
        ([1, 2, 3, 4, 5], [2, 4, 6, 8, 10], 2.0, 2.0),
        ([1, 2, 3, 4, 5], [2, 4, 6, 8, 10], 1.0, 2.0),
        ([1, 2, 3, 4, 5], [2, 4, 6, 8, 10], 2.0, 3.0),
        ([1, 2, 3], [4, 5, 6], 2.0, 1.0),  # paired
        ([1, 2, 3, 4], [5, 6, 7], 2.0, 2.0),
        ([2, 4, 6, 8, 10], [1, 3, 5, 7, 9], 2.0, 2.0),
        ([1, 2, 3, 4, 5, 6], [2, 4, 6, 8, 10, 12], 2.0, 2.0),
        ([5, 5, 5, 5, 5], [1, 2, 3, 4, 5], 2.0, 2.0),
        ([10, 10, 10, 10], [2, 4, 6, 8], 2.0, 2.0),
        ([3, 3, 3, 4, 5], [1, 1, 1, 1, 2], 2.0, 2.0),
        ([7, 7, 7, 7, 8], [1, 1, 1, 2, 3], 2.0, 2.0),
        ([100, 200, 300], [10, 20, 30], 2.0, 2.0),
        ([1, 1, 2, 2, 3], [4, 4, 5, 5, 6], 2.0, 2.0),
        ([2, 2, 2, 3, 4], [1, 1, 1, 2, 3], 2.0, 2.0),
        ([1, 2, 3, 4, 5], [1, 2, 3, 4, 5], 2.0, 2.0),
    ]
    for xs, ys, tails, type_ in t_cases:
        x_str = "{" + ",".join(str(v) for v in xs) + "}"
        y_str = "{" + ",".join(str(v) for v in ys) + "}"
        args = x_str + "," + y_str + f",{tails},{type_}"
        try:
            p = tTest(xs, ys, tails, type_)
            if math.isnan(p):
                lines.append(f"T.TEST\t{args}\t{NUM}")
            else:
                lines.append(f"T.TEST\t{args}\t{p!r}")
        except Exception:
            lines.append(f"T.TEST\t{args}\t{NUM}")

    # T.TEST refusals (wrong arity, numeric args only)
    lines.append(f"T.TEST\t1\t{VALUE}")
    lines.append(f"T.TEST\t1,2\t{VALUE}")
    lines.append(f"T.TEST\t1,2,3\t{VALUE}")
    lines.append(f"T.TEST\t1,2,3,4\t{VALUE}")
    lines.append(f"T.TEST\t1,2,3,4,5\t{VALUE}")
    lines.append(f"T.TEST\t1,2,3,4,5,6\t{VALUE}")

    # SKEW.P rows
    skew_cases = [
        [1, 2, 3, 4, 5],
        [1, 1, 1, 2, 3],
        [5, 4, 3, 2, 1],
        [1, 2, 3],
        [1, 1, 2, 3, 5, 8],
        [2, 4, 6, 8, 10],
        [1, 2, 3, 4, 5, 6],
        [5, 5, 5, 5, 6],
        [10, 10, 10, 10, 20],
        [3, 3, 3, 4, 5],
        [7, 7, 7, 7, 8],
        [100, 200, 300],
        [1, 1, 2, 2, 3],
        [2, 2, 2, 3, 4],
        [9, 8, 7, 6, 5],
    ]
    for nums in skew_cases:
        args = ",".join(str(v) for v in nums)
        try:
            p = skewP(nums)
            if math.isnan(p):
                lines.append(f"SKEW.P\t{args}\t{NUM}")
            else:
                lines.append(f"SKEW.P\t{args}\t{p!r}")
        except Exception:
            lines.append(f"SKEW.P\t{args}\t{NUM}")

    # SKEW.P refusals
    lines.append(f"SKEW.P\t1\t{NUM}")  # n < 3 -> #NUM!
    lines.append(f"SKEW.P\t1,2\t{NUM}")  # n < 3 -> #NUM!
    lines.append(f"SKEW.P\t5,5,5\t{NUM}")  # sigma=0 -> #NUM!
    lines.append(f"SKEW.P\t0,0,0\t{NUM}")  # sigma=0 -> #NUM!
    lines.append(f"SKEW.P\t-1,-1,-1\t{NUM}")  # sigma=0 -> #NUM!

    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    print(render(), end="")
