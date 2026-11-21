#!/usr/bin/env python3
"""Deterministic oracle corpus for R122 `PHI` / `DELTA` / `GESTEP`.

Renders the committed test fixture `format/ooxml/src/test/resources/r122/phi_corpus.tsv`.
Every expected value is recomputed here from the documented definitions (standard normal PDF
for PHI, equality test for DELTA, threshold test for GESTEP) independently of the Kotlin
product under test -- this fixture is an oracle, not a transcript of the implementation.

Pure `math` only (no numpy/scipy available in this sandbox; same constraint as the R120 controls).
"""
from __future__ import annotations

import math

SQRT_2PI = math.sqrt(2.0 * math.pi)


def phi(x: float) -> float:
    """Standard normal PDF: PHI(x) = exp(-x^2/2) / sqrt(2*pi)."""
    return math.exp(-(x * x) / 2.0) / SQRT_2PI


def delta(a: float, b: float) -> float:
    """DELTA(a, b) = 1 if a == b else 0."""
    return 1.0 if a == b else 0.0


def gestep(x: float, step: float = 0.0) -> float:
    """GESTEP(x, [step]) = 1 if x >= step else 0."""
    return 1.0 if x >= step else 0.0


# Refusal expectations.
NUM = "#NUM!"
VALUE = "#VALUE!"


def render() -> str:
    lines = ["function\targs\texpected"]

    # PHI rows: dense sampling across the domain
    xs = [-5.0, -4.0, -3.5, -3.0, -2.5, -2.0, -1.5, -1.0, -0.75, -0.5, -0.25,
          0.0, 0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0]
    for x in xs:
        lines.append(f"PHI\t{x}\t{phi(x)!r}")
    # PHI refusals (wrong arity, numeric-only args so the fixture stays well-formed)
    for args, expected in (("1,2", VALUE), ("0,0,0", VALUE), ("1,2,3,4", VALUE), ("1,2,3,4,5", VALUE), ("10,20,30", VALUE)):
        lines.append(f"PHI\t{args}\t{expected}")

    # DELTA rows: equal and unequal pairs
    pairs = [
        (0.0, 0.0), (1.0, 1.0), (-1.0, -1.0), (2.5, 2.5), (-2.5, -2.5),
        (0.0, 1.0), (1.0, 0.0), (-1.0, 1.0), (2.0, 3.0), (-2.0, -3.0),
        (0.0, -0.0), (100.0, 100.0), (-100.0, -100.0), (0.5, 0.5), (0.5, -0.5),
        (3.14159, 3.14159), (2.71828, 2.71828), (1e-10, 1e-10), (-1e-10, -1e-10),
        (0.0, 0.0001), (1000.0, 1000.1),
    ]
    for a, b in pairs:
        lines.append(f"DELTA\t{a},{b}\t{delta(a, b)!r}")
    # DELTA refusals (numeric-only args)
    for args, expected in (("1", VALUE), ("1,2,3", VALUE), ("1,2,3,4", VALUE), ("1,2,3,4,5", VALUE), ("10,20", VALUE)):
        lines.append(f"DELTA\t{args}\t{expected}")

    # GESTEP rows: grid of x vs step
    gx = [-5.0, -3.0, -2.0, -1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0]
    gsteps = [-5.0, -2.0, -1.0, 0.0, 1.0, 2.0, 5.0]
    for x in gx:
        for step in gsteps:
            lines.append(f"GESTEP\t{x},{step}\t{gestep(x, step)!r}")
    # GESTEP single-arg (step defaults to 0)
    for x in gx:
        lines.append(f"GESTEP\t{x}\t{gestep(x)!r}")
    # GESTEP refusals (wrong arity, numeric-only args)
    for args, expected in (("1,2,3", VALUE), ("1,2,3,4", VALUE), ("1,2,3,4,5", VALUE), ("10,20,30", VALUE), ("50,60,70", VALUE)):
        lines.append(f"GESTEP\t{args}\t{expected}")

    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    print(render(), end="")
