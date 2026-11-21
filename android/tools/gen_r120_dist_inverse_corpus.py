#!/usr/bin/env python3
"""Generate the R120 distribution-inverse corpus: `function<TAB>args<TAB>expected`.

R120 adds the distribution functions the engine was missing — `T.DIST`, `T.DIST.2T`, `T.DIST.RT`, `F.DIST`,
`T.INV`, `T.INV.2T`, `CHISQ.INV`, `CHISQ.INV.RT`, `F.INV`, `F.INV.RT`, `BETA.INV`, `GAMMA.INV` and
`GAMMALN.PRECISE`. Before this round every one of those names returned `#NAME?` from a formula.

The expected column is produced here from the documented definitions (Microsoft's function reference for each
name; the regularized incomplete beta and gamma functions for the CDFs) and is *independently* recomputed in
`tools/test_r120_dist_inverse.py` through numerical integration of the probability density functions
(Simpson's rule) plus bisection — a genuinely different algorithm from the continued fractions used here.
Two implementations agreeing is evidence; one implementation writing its own fixture is a transcript.

`args` is a comma-separated list of numeric literals (no ranges: these functions take scalars). An expected
value starting with `#` is an error literal.

Deterministic and version-stable: no `sum()` on floats (Python 3.12 changed it to compensated summation),
no random module, no clock, no environment. Re-running the generator must reproduce the committed file byte
for byte, and `tools/check_r120_dist_inverse.py` fails if it does not.
"""
from __future__ import annotations

import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / 'format/ooxml/src/test/resources/r120/dist_inverse_corpus.tsv'
HEADER = 'function\targs\texpected'

NAMES = (
    'GAMMALN.PRECISE', 'T.DIST', 'T.DIST.2T', 'T.DIST.RT', 'F.DIST',
    'T.INV', 'T.INV.2T', 'CHISQ.INV', 'CHISQ.INV.RT', 'F.INV', 'F.INV.RT',
    'BETA.INV', 'GAMMA.INV',
)


def naive_sum(values) -> float:
    """Left-to-right float summation, matching Kotlin's `sum()` and the engine's kernels.

    Python 3.12 changed `sum()` for floats to Neumaier (compensated) summation, which differs from naive
    summation in the last ULP. The corpus must render identically on every Python version, so the generator
    never calls `sum()` on floats.
    """
    total = 0.0
    for value in values:
        total += value
    return total


def betacf(a: float, b: float, x: float) -> float:
    maxit, eps, fpmin = 200, 3e-14, 1e-300
    qab, qap, qam = a + b, a + 1.0, a - 1.0
    c = 1.0
    d = 1.0 - qab * x / qap
    if abs(d) < fpmin:
        d = fpmin
    d = 1.0 / d
    h = d
    for m in range(1, maxit + 1):
        m2 = 2 * m
        aa = m * (b - m) * x / ((qam + m2) * (a + m2))
        d = 1.0 + aa * d
        if abs(d) < fpmin:
            d = fpmin
        c = 1.0 + aa / c
        if abs(c) < fpmin:
            c = fpmin
        d = 1.0 / d
        h *= d * c
        aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
        d = 1.0 + aa * d
        if abs(d) < fpmin:
            d = fpmin
        c = 1.0 + aa / c
        if abs(c) < fpmin:
            c = fpmin
        d = 1.0 / d
        dl = d * c
        h *= dl
        if abs(dl - 1.0) < eps:
            break
    return h


def incbeta(a: float, b: float, x: float) -> float:
    """Regularized incomplete beta I_x(a, b)."""
    if x <= 0.0:
        return 0.0
    if x >= 1.0:
        return 1.0
    ln_beta = math.lgamma(a) + math.lgamma(b) - math.lgamma(a + b)
    front = math.exp(a * math.log(x) + b * math.log(1.0 - x) - ln_beta)
    if x < (a + 1.0) / (a + b + 2.0):
        return front * betacf(a, b, x) / a
    return 1.0 - front * betacf(b, a, 1.0 - x) / b


def gammainc_reg(a: float, x: float) -> float:
    """Regularized lower incomplete gamma P(a, x)."""
    if x <= 0.0:
        return 0.0
    if x < a + 1.0:
        ap = a
        s = 1.0 / a
        d = s
        for _ in range(200):
            ap += 1.0
            d *= x / ap
            s += d
            if abs(d) < abs(s) * 1e-14:
                return s * math.exp(-x + a * math.log(x) - math.lgamma(a))
        return s * math.exp(-x + a * math.log(x) - math.lgamma(a))
    b = x + 1.0 - a
    c = 1.0 / 1e-30
    d = 1.0 / b
    h = d
    for i in range(1, 201):
        an = -i * (i - a)
        b += 2.0
        d = an * d + b
        if abs(d) < 1e-30:
            d = 1e-30
        c = b + an / c
        if abs(c) < 1e-30:
            c = 1e-30
        d = 1.0 / d
        dl = d * c
        h *= dl
        if abs(dl - 1.0) < 1e-14:
            break
    return 1.0 - math.exp(-x + a * math.log(x) - math.lgamma(a)) * h


def tdist_2t(x: float, df: float) -> float:
    return incbeta(df / 2.0, 0.5, df / (df + x * x))


def tdist_rt(x: float, df: float) -> float:
    ib = incbeta(df / 2.0, 0.5, df / (df + x * x))
    return 0.5 * ib if x >= 0.0 else 1.0 - 0.5 * ib


def chisq_dist(x: float, df: float) -> float:
    return gammainc_reg(df / 2.0, x / 2.0)


def fdist_rt(x: float, d1: float, d2: float) -> float:
    u = (d1 * x) / (d1 * x + d2)
    return 1.0 - incbeta(d1 / 2.0, d2 / 2.0, u)


def invert_cdf(cdf, p: float, hi_start: float) -> float:
    """Bisects a monotone increasing CDF for the quantile at p, expanding hi until it brackets p."""
    hi = hi_start
    guard = 0
    while cdf(hi) < p and guard < 200:
        hi *= 2.0
        guard += 1
        if hi > 1e15:
            break
    lo = 0.0
    for _ in range(100):
        mid = 0.5 * (lo + hi)
        if cdf(mid) < p:
            lo = mid
        else:
            hi = mid
    return 0.5 * (lo + hi)


def t_inv(p: float, df: float) -> float:
    lo, hi = 0.0, 1e6
    for _ in range(80):
        mid = 0.5 * (lo + hi)
        if tdist_2t(mid, df) > p:
            lo = mid
        else:
            hi = mid
    return 0.5 * (lo + hi)


def expected(name: str, args: list[float]) -> float | str:
    """The documented value of `name(args)`, or the error literal the spreadsheet returns."""
    if name == 'GAMMALN.PRECISE':
        (x,) = args
        if x <= 0.0:
            return '#NUM!'
        return math.lgamma(x)
    if name == 'T.DIST':
        x, df, tails = args
        df = math.floor(df)
        if df < 1.0 or tails not in (1.0, 2.0):
            return '#NUM!'
        if tails == 2.0:
            if x < 0.0:
                return '#NUM!'
            return tdist_2t(x, df)
        return tdist_rt(x, df)
    if name == 'T.DIST.2T':
        x, df = args
        df = math.floor(df)
        if df < 1.0 or x < 0.0:
            return '#NUM!'
        return tdist_2t(x, df)
    if name == 'T.DIST.RT':
        x, df = args
        df = math.floor(df)
        if df < 1.0:
            return '#NUM!'
        return tdist_rt(x, df)
    if name == 'F.DIST':
        x, d1, d2 = args
        d1, d2 = math.floor(d1), math.floor(d2)
        if x < 0.0 or d1 < 1.0 or d2 < 1.0:
            return '#NUM!'
        return fdist_rt(x, d1, d2)
    if name in ('T.INV', 'T.INV.2T'):
        p, df = args
        df = math.floor(df)
        if p <= 0.0 or p >= 1.0 or df < 1.0:
            return '#NUM!'
        return t_inv(p, df)
    if name == 'CHISQ.INV':
        p, df = args
        df = math.floor(df)
        if p <= 0.0 or p >= 1.0 or df < 1.0:
            return '#NUM!'
        half_k = df / 2.0
        return invert_cdf(lambda v: gammainc_reg(half_k, v / 2.0), p, 1.0)
    if name == 'CHISQ.INV.RT':
        p, df = args
        df = math.floor(df)
        if p <= 0.0 or p >= 1.0 or df < 1.0:
            return '#NUM!'
        half_k = df / 2.0
        return invert_cdf(lambda v: gammainc_reg(half_k, v / 2.0), 1.0 - p, 1.0)
    if name == 'F.INV':
        p, d1, d2 = args
        d1, d2 = math.floor(d1), math.floor(d2)
        if p <= 0.0 or p >= 1.0 or d1 < 1.0 or d2 < 1.0:
            return '#NUM!'
        # Left-tailed: I_u(d1/2, d2/2) == p, with u = d1*x/(d1*x+d2).
        cdf = lambda v: incbeta(d1 / 2.0, d2 / 2.0, (d1 * v) / (d1 * v + d2))
        return invert_cdf(cdf, p, 1.0)
    if name == 'F.INV.RT':
        p, d1, d2 = args
        d1, d2 = math.floor(d1), math.floor(d2)
        if p <= 0.0 or p >= 1.0 or d1 < 1.0 or d2 < 1.0:
            return '#NUM!'
        # Right-tailed: 1 - I_u(d1/2, d2/2) == p is I_u == 1 - p, with u = d1*x/(d1*x+d2).
        cdf = lambda v: incbeta(d1 / 2.0, d2 / 2.0, (d1 * v) / (d1 * v + d2))
        return invert_cdf(cdf, 1.0 - p, 1.0)
    if name == 'BETA.INV':
        p, alpha, beta = args
        if p <= 0.0 or p >= 1.0 or alpha <= 0.0 or beta <= 0.0:
            return '#NUM!'
        return invert_cdf(lambda v: incbeta(alpha, beta, v), p, 1.0)
    if name == 'GAMMA.INV':
        p, alpha, beta = args
        if p <= 0.0 or p >= 1.0 or alpha <= 0.0 or beta <= 0.0:
            return '#NUM!'
        return invert_cdf(lambda v: gammainc_reg(alpha, v / beta), p, max(1.0, alpha * beta))
    raise AssertionError(name)


# Probe grids. Probabilities span the interior; degrees of freedom span small to moderate; shape
# parameters include the boundary alpha/beta below 1 (singular densities) and above 1.
GAMMALN_XS = [0.1, 0.25, 0.5, 0.75, 0.9, 1.0, 1.1, 1.5, 2.0, 2.5, 3.0, 4.5, 5.0, 6.0, 7.3, 8.0, 10.0, 12.0, 15.0, 20.0, 30.0]
TDIST_XS = [-3.0, -2.0, -1.0, -0.5, 0.0, 0.5, 1.0, 2.0, 3.0]
TDIST_DFS = [1.0, 5.0, 10.0, 30.0]
FDIST_XS = [0.0, 0.5, 1.0, 2.0, 5.0]
FDIST_DFS = [(5.0, 10.0), (1.0, 1.0), (10.0, 20.0), (3.0, 15.0)]
PROBS = [0.001, 0.01, 0.05, 0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99, 0.999]
INV_DFS = [1.0, 5.0, 10.0, 30.0]
F_DFS = [(5.0, 10.0), (1.0, 1.0), (10.0, 20.0), (3.0, 15.0)]
BETA_SHAPES = [(2.0, 3.0), (1.0, 1.0), (5.0, 2.0), (0.5, 0.5), (10.0, 10.0)]
GAMMA_SHAPES = [(2.0, 3.0), (1.0, 1.0), (5.0, 2.0), (0.5, 2.0), (10.0, 1.0)]


def rows() -> list[tuple[str, str, str]]:
    out: list[tuple[str, str, str]] = []

    def add(name: str, args: list[float]) -> None:
        value = expected(name, args)
        args_text = ','.join(repr(v) for v in args)
        out.append((name, args_text, value if isinstance(value, str) else repr(value)))

    for x in GAMMALN_XS:
        add('GAMMALN.PRECISE', [x])
    for x in (0.0, -1.0, -0.5, -2.0, -4.0):
        add('GAMMALN.PRECISE', [x])
    for x in TDIST_XS:
        for df in TDIST_DFS:
            for tails in (1.0, 2.0):
                add('T.DIST', [x, df, tails])
    for df in TDIST_DFS:
        add('T.DIST', [1.0, df, 3.0])
        add('T.DIST', [1.0, df, 0.0])
        add('T.DIST', [1.0, 0.0, 1.0])
    for x in [0.0, 0.5, 1.0, 2.0, 3.0]:
        for df in TDIST_DFS:
            add('T.DIST.2T', [x, df])
    add('T.DIST.2T', [-1.0, 10.0])
    add('T.DIST.2T', [-2.0, 5.0])
    add('T.DIST.2T', [-0.5, 10.0])
    add('T.DIST.2T', [1.0, 0.0])
    add('T.DIST.2T', [1.0, 0.5])
    for x in TDIST_XS:
        for df in TDIST_DFS:
            add('T.DIST.RT', [x, df])
    add('T.DIST.RT', [1.0, 0.0])
    add('T.DIST.RT', [2.0, 0.0])
    add('T.DIST.RT', [0.5, 0.5])
    add('T.DIST.RT', [3.0, 0.0])
    add('T.DIST.RT', [1.0, 0.5])
    for x in FDIST_XS:
        for d1, d2 in FDIST_DFS:
            add('F.DIST', [x, d1, d2])
    add('F.DIST', [-1.0, 5.0, 10.0])
    add('F.DIST', [1.0, 0.0, 10.0])
    add('F.DIST', [1.0, 5.0, 0.0])
    add('F.DIST', [-2.0, 5.0, 10.0])
    add('F.DIST', [3.0, 0.0, 10.0])
    for name in ('T.INV', 'T.INV.2T'):
        for p in PROBS:
            for df in INV_DFS:
                add(name, [p, df])
        add(name, [0.0, 10.0])
        add(name, [1.0, 10.0])
        add(name, [-0.1, 10.0])
        add(name, [1.1, 10.0])
        add(name, [0.5, 0.0])
    for name in ('CHISQ.INV', 'CHISQ.INV.RT'):
        for p in PROBS:
            for df in INV_DFS:
                add(name, [p, df])
        add(name, [0.0, 10.0])
        add(name, [1.0, 10.0])
        add(name, [0.5, 0.0])
        add(name, [-0.1, 10.0])
        add(name, [1.1, 10.0])
    for name in ('F.INV', 'F.INV.RT'):
        for p in PROBS:
            for d1, d2 in F_DFS:
                add(name, [p, d1, d2])
        add(name, [0.0, 5.0, 10.0])
        add(name, [1.0, 5.0, 10.0])
        add(name, [0.5, 0.0, 10.0])
        add(name, [0.5, 5.0, 0.0])
        add(name, [-0.1, 5.0, 10.0])
    for p in PROBS:
        for alpha, beta in BETA_SHAPES:
            add('BETA.INV', [p, alpha, beta])
    add('BETA.INV', [0.0, 2.0, 3.0])
    add('BETA.INV', [1.0, 2.0, 3.0])
    add('BETA.INV', [0.5, 0.0, 3.0])
    add('BETA.INV', [0.5, 2.0, 0.0])
    add('BETA.INV', [-0.1, 2.0, 3.0])
    for p in PROBS:
        for alpha, beta in GAMMA_SHAPES:
            add('GAMMA.INV', [p, alpha, beta])
    add('GAMMA.INV', [0.0, 2.0, 3.0])
    add('GAMMA.INV', [1.0, 2.0, 3.0])
    add('GAMMA.INV', [0.5, 0.0, 3.0])
    add('GAMMA.INV', [0.5, 2.0, 0.0])
    add('GAMMA.INV', [-0.1, 2.0, 3.0])
    # A corpus is a set of observations, not a log: the first occurrence of each row wins.
    unique: list[tuple[str, str, str]] = []
    seen: set[tuple[str, str]] = set()
    for row in out:
        if row[:2] in seen:
            continue
        seen.add(row[:2])
        unique.append(row)
    return unique


def render() -> str:
    lines = [HEADER]
    for name, args, expected_value in rows():
        lines.append('\t'.join((name, args, expected_value)))
    return '\n'.join(lines) + '\n'


def main() -> int:
    text = render()
    CORPUS.parent.mkdir(parents=True, exist_ok=True)
    CORPUS.write_text(text, encoding='utf-8')
    total = len(text.strip().splitlines()) - 1
    errors = sum(1 for line in text.splitlines()[1:] if line.split('\t')[2].startswith('#'))
    print(f'R120_DIST_INVERSE_CORPUS_WRITTEN rows={total} error_rows={errors} path={CORPUS.relative_to(ROOT)}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
