#!/usr/bin/env python3
"""Deterministic oracle corpus for R121 `BESSELK` / `BESSELY`.

Renders the committed test fixture `format/ooxml/src/test/resources/r121/bessel_corpus.tsv`.
Every expected value is recomputed here from the documented definitions (convergent series for
x <= 10, DLMF asymptotic beyond), independently of the Kotlin product under test -- this fixture
is an oracle, not a transcript of the implementation.

Pure `math` only (no numpy/scipy available in this sandbox; same constraint as the R120 controls).
"""
from __future__ import annotations

import math

GAMMA = 0.577215664901532860606512090082402431
XOVER = 10.0


def harmonic(m: int) -> float:
    return sum(1.0 / i for i in range(1, m + 1)) if m > 0 else 0.0


def besseli_series(x: float, n: int) -> float:
    half = x / 2.0
    term = 1.0
    for k in range(1, n + 1):
        term *= half / k
    total = term
    half_sq = half * half
    for m in range(1, 400):
        term *= half_sq / (m * (m + n))
        total += term
        if abs(term) <= abs(total) * 1e-17:
            break
    return total


def besselj_series(x: float, n: int) -> float:
    half = x / 2.0
    term = 1.0
    for k in range(1, n + 1):
        term *= half / k
    total = term
    half_sq = half * half
    for m in range(1, 400):
        term *= -half_sq / (m * (m + n))
        total += term
        if abs(term) <= abs(total) * 1e-17:
            break
    return total


def bessk0(x: float) -> float:
    i0 = besseli_series(x, 0)
    total = -(math.log(x / 2.0) + GAMMA) * i0
    x2o4 = (x / 2.0) ** 2
    h = 1.0
    kfact = 1.0
    k2fact = 1.0
    p = x2o4
    total += h * p / k2fact  # k = 1
    for k in range(2, 400):
        h += 1.0 / k
        kfact *= k
        k2fact = kfact * kfact
        p *= x2o4
        term = h * p / k2fact
        total += term
        if abs(term) <= abs(total) * 1e-17:
            break
    return total


def bessk1(x: float) -> float:
    i0 = besseli_series(x, 0)
    i1 = besseli_series(x, 1)
    x2o4 = (x / 2.0) ** 2
    sp = 0.0
    p = x / 2.0  # (x/2)^(2k-1), k = 1
    kfact = 1.0
    k2fact = 1.0
    h = 1.0
    for k in range(1, 400):
        if k > 1:
            h += 1.0 / k
            kfact *= k
            k2fact = kfact * kfact
            p *= x2o4
        term = h * k * p / k2fact
        sp += term
        if abs(term) <= abs(sp) * 1e-17:
            break
    return (1.0 / x) * i0 + (math.log(x / 2.0) + GAMMA) * i1 - sp


def bessk_series(x: float, n: int) -> float:
    if n == 0:
        return bessk0(x)
    if n == 1:
        return bessk1(x)
    km2 = bessk0(x)
    km1 = bessk1(x)
    for i in range(1, n):
        km2, km1 = km1, km2 + (2.0 * i / x) * km1
    return km1


def bessk_asymp(x: float, n: int) -> float:
    pref = math.sqrt(math.pi / (2.0 * x)) * math.exp(-x)
    total = 1.0  # the k = 0 term
    term = 1.0
    best = abs(term)
    for k in range(1, 600):
        term *= (4.0 * n * n - (2.0 * k - 1.0) ** 2) / (8.0 * k * x)
        total += term
        if abs(term) > best and k > 3:
            break
        best = abs(term)
    return pref * total


def bessk(x: float, n: int) -> float:
    if x <= 0.0:
        raise ValueError("domain")
    if n < 0:
        raise ValueError("order")
    return bessk_series(x, n) if x <= XOVER else bessk_asymp(x, n)


def bessy_series(x: float, n: int) -> float:
    half = x / 2.0
    pi = math.pi
    fact = [1.0] * (n + 1)
    for i in range(1, n + 1):
        fact[i] = fact[i - 1] * i
    total = 0.0
    for k in range(0, n):
        total -= (fact[n - k - 1] / fact[k]) * (half ** (2 * k - n)) / pi
    total += (2.0 / pi) * math.log(half) * besselj_series(x, n)
    p = half ** n
    kfact = 1.0
    nkfact = fact[n]
    sign = 1.0
    for k in range(0, 400):
        if k > 0:
            p *= half * half
            kfact *= k
            nkfact *= (n + k)
            sign = -sign
        coeff = sign * p / (kfact * nkfact)
        bracket = harmonic(k) + harmonic(n + k) - 2.0 * GAMMA
        total -= coeff * bracket / pi
        if abs(coeff * bracket) <= abs(total) * 1e-16:
            break
    return total


def bessy_asymp(x: float, n: int) -> float:
    w = x - n * math.pi / 2.0 - math.pi / 4.0
    pref = math.sqrt(2.0 / (math.pi * x))
    se = 1.0  # the k = 0 term
    so = 0.0
    term = 1.0
    for k in range(1, 600):
        term *= (4.0 * n * n - (2.0 * k - 1.0) ** 2) / (8.0 * k * x)
        s = 1.0 if (k // 2) % 2 == 0 else -1.0
        if k % 2 == 0:
            se += s * term
        else:
            so += s * term
        if abs(term) < 1e-18 and k > 4:
            break
    return pref * (math.sin(w) * se - math.cos(w) * so)


def bessy(x: float, n: int) -> float:
    if x <= 0.0:
        raise ValueError("domain")
    if n < 0:
        raise ValueError("order")
    return bessy_series(x, n) if x <= XOVER else bessy_asymp(x, n)


# Refusal expectations.
NUM = "#NUM!"
VALUE = "#VALUE!"


def render() -> str:
    lines = ["function\targs\texpected"]
    xs = [0.1, 0.2, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 7.0, 10.0]
    ns = [0, 1, 2, 3, 5]
    for name, fn in (("BESSELK", bessk), ("BESSELY", bessy)):
        for x in xs:
            for n in ns:
                try:
                    value = fn(x, n)
                except ValueError:
                    value = NUM
                lines.append(
                    f"{name}\t{x},{n}\t{value if isinstance(value, str) else repr(value)}"
                )
        # refusals (all-numeric args so the fixture stays well-formed)
        for args, expected in (
            ("0,0", NUM),
            ("-1,0", NUM),
            ("1,-1", NUM),
            ("0,-1", NUM),
            ("1", VALUE),
            ("1,0,0", VALUE),
        ):
            lines.append(f"{name}\t{args}\t{expected}")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    print(render(), end="")
